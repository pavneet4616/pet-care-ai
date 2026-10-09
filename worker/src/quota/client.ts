import type { Env, QuotaSnapshot, ReserveResult } from "../types.js";

export function quotaStub(env: Env, installationId: string): string {
  const id = env.AI_QUOTA.idFromName(`install:${installationId}`);
  return id.toString();
}

export async function callDo(env: Env, installationId: string, path: string, body?: unknown): Promise<Response> {
  const stub = env.AI_QUOTA.get(env.AI_QUOTA.idFromName(`install:${installationId}`));
  return stub.fetch(`https://quota${path}`, {
    method: body === undefined ? "GET" : "POST",
    headers: { "content-type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

export async function doReserve(env: Env, installationId: string, requestId: string): Promise<ReserveResult> {
  const res = await callDo(env, installationId, "/reserve", { requestId });
  return (await res.json()) as ReserveResult;
}

export async function doRelease(env: Env, installationId: string, requestId: string): Promise<void> {
  await callDo(env, installationId, "/release", { requestId });
}

export async function doConsumeBonus(
  env: Env,
  installationId: string,
  requestId: string,
): Promise<ReserveResult> {
  const res = await callDo(env, installationId, "/consume-bonus", { requestId });
  return (await res.json()) as ReserveResult;
}

export async function doStatus(env: Env, installationId: string): Promise<QuotaSnapshot> {
  const res = await callDo(env, installationId, "/status");
  const json = (await res.json()) as { quota: QuotaSnapshot };
  return json.quota;
}

export async function doSetEntitlement(
  env: Env,
  installationId: string,
  tier: "free" | "lifetime" | "pro",
  proExpiry?: number,
  source?: {tokenHash?: string; expectedTokenHash?: string; lifetime?: boolean},
): Promise<void> {
  const response=await callDo(env, installationId, "/set-entitlement", { tier, proExpiry, ...source });
  if (!response.ok) throw new Error("Entitlement persistence failed");
}

export async function doReport(
  env: Env,
  installationId: string,
  report: { requestId?: string; category: string; detail?: string; excerpt?: string },
): Promise<void> {
  await callDo(env, installationId, "/report", report);
}

export function quotaExhaustedBody(quota: QuotaSnapshot): Response {
  return Response.json(
    {
      ok: false,
      error: {
        code: "AI_DAILY_QUOTA_REACHED",
        message: "Today's free AI questions have been used.",
      },
      quota,
      upgrade: { available: true },
      rewardedEligible: quota.bonusRemaining > 0,
    },
    { status: 429 },
  );
}
