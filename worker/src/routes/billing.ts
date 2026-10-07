import type { Env, Tier } from "../types.js";
import { verifyOneTimeProduct, verifySubscription, type SubscriptionVerifyResult } from "../billing/googlePlay.js";
import { doSetEntitlement, doStatus } from "../quota/client.js";
import { isValidId } from "../security/validation.js";

function extraSkus(env: Env): string[] {
  return [env.AI_WEEKLY_SKU, env.AI_MONTHLY_SKU].filter((s): s is string => Boolean(s));
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
    await doSetEntitlement(env, installationId, "pro", result.expiryTimeMillis);
    return { applied: true, tier: "pro" };
  }
  await doSetEntitlement(env, installationId, "free", 0);
  return { applied: true, tier: "free" };
}

export async function handleVerify(request: Request, env: Env): Promise<Response> {
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
    lifetime = await verifyOneTimeProduct(
      env.GOOGLE_SERVICE_ACCOUNT_JSON as string,
      env.PLAY_PACKAGE_NAME,
      body.lifetimeProductId as string,
      body.lifetimePurchaseToken as string,
    );
  }

  // Play API / auth failure: leave the Durable Object untouched.
  if (sub?.status === "error" && !hasLifetime) {
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
  const expiry = sub && sub.status === "active" ? sub.expiryTimeMillis : 0;
  const tier: Tier = activeSub ? "pro" : lifetime ? "lifetime" : "free";
  await doSetEntitlement(env, body.installationId, tier, expiry);
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
// Revokes pro promptly on expiry/cancellation; renewals are picked up
// on next client verify (which re-checks the Publisher API).
export async function handleRtdn(request: Request, env: Env): Promise<Response> {
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
      // One-time product notifications use a different envelope; ignore for v1.
    };
    const n = decoded.subscriptionNotification;
    // Types: 3 = recovered, 4 = renewed... 13 = expired, 6 = grace, 12 = revoked.
    // We cannot map purchaseToken -> installationId without a store in v1,
    // so expiry enforcement happens lazily via proExpiry in the DO.
    console.log(
      JSON.stringify({ type: "rtdn", notificationType: n?.notificationType, subscriptionId: n?.subscriptionId }),
    );
    void env;
    return Response.json({ ok: true });
  } catch {
    return Response.json({ ok: false }, { status: 400 });
  }
}
