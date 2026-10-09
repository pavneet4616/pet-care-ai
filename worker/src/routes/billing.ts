import type { Env, Tier } from "../types.js";
import { verifyOneTimeProduct, verifySubscription, type SubscriptionVerifyResult } from "../billing/googlePlay.js";
import { doSetEntitlement, doStatus } from "../quota/client.js";
import { isValidId } from "../security/validation.js";
import { requireIntegrity } from "../security/integrity.js";
import { registry } from "../security/RegistryDO.js";
import { sha256 } from "../security/crypto.js";
import { authenticatePubSub } from "../security/pubsub.js";

function extraSkus(env: Env): string[] {
  return [env.AI_WEEKLY_SKU, env.AI_MONTHLY_SKU];
}

function playConfigured(env: Env): boolean {
  return Boolean(env.GOOGLE_SERVICE_ACCOUNT_JSON);
}

/**
 * Grant Pro when Google confirms an active sub. Never downgrade on API /
 * auth failures — a flaky Play call must not wipe a paying user's quota.
 * Revoke only when Google explicitly says the subscription is inactive.
 */
export async function applyPlayEntitlement(
  env: Env,
  installationId: string,
  purchaseToken: string,
  subscriptionId?: string,
): Promise<{ applied: boolean; tier?: Tier; error?: string }> {
  if (!env.GOOGLE_SERVICE_ACCOUNT_JSON) {
    return { applied: false, error: "billing not configured" };
  }
  const result: SubscriptionVerifyResult = await verifySubscription(
    env.GOOGLE_SERVICE_ACCOUNT_JSON,
    env.PLAY_PACKAGE_NAME,
    subscriptionId,
    purchaseToken,
    extraSkus(env),
  );
  if (result.status === "error") {
    return { applied: false, error: result.message };
  }
  if (result.status === "active") {
    const tokenHash=await sha256(purchaseToken);
    const bound=await registry(env,"/bind-token",{installationId,tokenHash,accountId:result.accountId,kind:"subscription"});
    if (!bound.ok) return {applied:false,error:(await bound.json() as {error:string}).error};
    await doSetEntitlement(env, installationId, "pro", result.expiryTimeMillis,{tokenHash});
    return { applied: true, tier: "pro" };
  }
  await doSetEntitlement(env, installationId, "free", 0, {expectedTokenHash:await sha256(purchaseToken)});
  return { applied: true, tier: "free" };
}

export async function handleVerify(request: Request, env: Env): Promise<Response> {
  const failure=await requireIntegrity(request,env);
  if (failure) return failure;
  let body: {
    installationId?: string;
    purchaseToken?: string;
    subscriptionId?: string;
    productId?: string;
    lifetimeProductId?: string;
    lifetimePurchaseToken?: string;
  };
  try {
    body = (await request.json()) as typeof body;
  } catch {
    return Response.json({ error: "Invalid JSON body." }, { status: 400 });
  }
  if (!body.installationId || !isValidId(body.installationId)) {
    return Response.json({ error: "Invalid installationId." }, { status: 400 });
  }
  if (!playConfigured(env)) {
    return Response.json({ error: "Billing not configured on server." }, { status: 503 });
  }

  const hasSubToken = Boolean(body.purchaseToken);
  const hasLifetime = Boolean(body.lifetimeProductId && body.lifetimePurchaseToken);

  let sub: SubscriptionVerifyResult | null = null;
  if (hasSubToken && body.purchaseToken) {
    sub = await verifySubscription(
      env.GOOGLE_SERVICE_ACCOUNT_JSON as string,
      env.PLAY_PACKAGE_NAME,
      body.subscriptionId,
      body.purchaseToken,
      extraSkus(env),
    );
  }

  let lifetime = false;
  if (hasLifetime) {
    // Historical lifetime ownership is restorable, but unrelated one-time products
    // must never grant the lifetime AI tier.
    if (body.lifetimeProductId !== "premium_unlock") {
      return Response.json({ error: "Unsupported lifetime product." }, { status: 400 });
    }
    lifetime = await verifyOneTimeProduct(
      env.GOOGLE_SERVICE_ACCOUNT_JSON as string,
      env.PLAY_PACKAGE_NAME,
      body.lifetimeProductId as string,
      body.lifetimePurchaseToken as string,
    );
  }

  // Play API / auth failure: leave the Durable Object untouched.
  if (sub?.status === "error") {
    const quota = await doStatus(env, body.installationId);
    return Response.json({
      verified: false,
      error: sub.message,
      tier: quota.tier,
      activeSub: false,
      lifetime: false,
      expiryTimeMillis: 0,
      quota,
    });
  }

  if (!hasSubToken && !hasLifetime) {
    const quota = await doStatus(env, body.installationId);
    return Response.json({
      verified: false,
      error: "missing purchase token",
      tier: quota.tier,
      activeSub: false,
      lifetime: false,
      expiryTimeMillis: 0,
      quota,
    });
  }

  const activeSub = sub?.status === "active";
  if (activeSub && sub?.status === "active" && body.purchaseToken) {
    const bound=await registry(env,"/bind-token",{installationId:body.installationId,tokenHash:await sha256(body.purchaseToken),accountId:sub.accountId,kind:"subscription"});
    if (!bound.ok) return bound;
  }
  if (lifetime && body.lifetimePurchaseToken) {
    const bound=await registry(env,"/bind-token",{installationId:body.installationId,tokenHash:await sha256(body.lifetimePurchaseToken),kind:"lifetime"});
    if (!bound.ok) return bound;
  }
  const expiry = sub && sub.status === "active" ? sub.expiryTimeMillis : 0;
  const tier: Tier = activeSub ? "pro" : lifetime ? "lifetime" : "free";
  const tokenHash = body.purchaseToken ? await sha256(body.purchaseToken) : undefined;
  await doSetEntitlement(env, body.installationId, tier, expiry,{
    tokenHash:activeSub ? tokenHash : undefined,
    expectedTokenHash:sub?.status === "inactive" ? tokenHash : undefined,
    lifetime:hasLifetime ? lifetime : undefined,
  });
  const quota = await doStatus(env, body.installationId);
  return Response.json({
    verified: true,
    tier,
    activeSub,
    lifetime,
    expiryTimeMillis: expiry,
    quota,
  });
}

// Real-time Developer Notifications (Pub/Sub push).
// Re-fetch canonical state for renewals, revocations and expiry; cancellation
// retains paid time. Transient storage/API errors request a Pub/Sub retry.
export async function handleRtdn(request: Request, env: Env): Promise<Response> {
  if (!await authenticatePubSub(request,env)) return Response.json({error:"UNAUTHORIZED"},{status:401});
  let body: { message?: { data?: string } };
  try {
    body = (await request.json()) as typeof body;
  } catch {
    return Response.json({ ok: false }, { status: 400 });
  }
  try {
    const raw = body.message?.data ?? "";
    const decoded = JSON.parse(atob(raw)) as {
      subscriptionNotification?: { notificationType?: number; purchaseToken?: string; subscriptionId?: string };
      packageName?: string;
      testNotification?: object;
    };
    const n = decoded.subscriptionNotification;
    if (decoded.packageName !== env.PLAY_PACKAGE_NAME) return Response.json({error:"WRONG_PACKAGE"},{status:400});
    if (decoded.testNotification) return Response.json({ok:true});
    if (!n?.purchaseToken || !env.GOOGLE_SERVICE_ACCOUNT_JSON) return Response.json({error:"INVALID_NOTIFICATION"},{status:400});
    const tokenHash=await sha256(n.purchaseToken);
    const response=await registry(env,"/owners",{tokenHash});
    if (!response.ok) return Response.json({error:"RETRY"},{status:503});
    const {installations}=await response.json() as {installations:Array<{installation_id:string;kind:string}>};
    // Re-fetch canonical Play state for every type. A canceled subscription stays entitled until
    // paid expiry; replayed/out-of-order events never roll back a newer purchase or renewal.
    const result=await verifySubscription(env.GOOGLE_SERVICE_ACCOUNT_JSON,env.PLAY_PACKAGE_NAME,n.subscriptionId,n.purchaseToken,extraSkus(env));
    if (result.status === "error") return Response.json({error:"RETRY"},{status:503});
    for (const {installation_id} of installations) {
      await doSetEntitlement(env,installation_id,result.status === "active" ? "pro" : "free",result.status === "active" ? result.expiryTimeMillis : 0,{expectedTokenHash:tokenHash});
    }
    return Response.json({ ok: true });
  } catch {
    return Response.json({ error: "RETRY" }, { status: 503 });
  }
}
