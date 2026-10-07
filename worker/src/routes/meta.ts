import type { Env, ReportCategory } from "../types.js";
import { doReport } from "../quota/client.js";
import { isValidId } from "../security/validation.js";

const CATEGORIES: ReportCategory[] = ["unsafe", "incorrect", "offensive", "other"];

export async function handleAiStatus(_request: Request, env: Env): Promise<Response> {
  let override: Partial<{
    aiEnabled: boolean;
    freeLimit: number;
    lifetimeLimit: number;
    proLimit: number;
    rewardedBonus: number;
  }> = {};
  try {
    const raw = await env.CONFIG.get("ai_config");
    if (raw) override = JSON.parse(raw) as typeof override;
  } catch {
    // KV miss -> fall back to vars.
  }
  return Response.json({
    aiEnabled: override.aiEnabled ?? true,
    freeLimit: override.freeLimit ?? parseInt(env.FREE_DAILY ?? "5", 10),
    lifetimeLimit: override.lifetimeLimit ?? parseInt(env.LIFETIME_DAILY ?? "10", 10),
    proLimit: override.proLimit ?? parseInt(env.PRO_DAILY ?? "20", 10),
    rewardedBonus: override.rewardedBonus ?? parseInt(env.BONUS_DAILY ?? "1", 10),
    modelStatus: "healthy",
    model: env.MODEL,
  });
}

// In-app AI answer reporting (Play AI-generated-content policy).
// Stores only metadata + short excerpt, never the full history.
export async function handleReport(request: Request, env: Env): Promise<Response> {
  let body: {
    installationId?: string;
    requestId?: string;
    category?: string;
    detail?: string;
    messageExcerpt?: string;
  };
  try {
    body = (await request.json()) as typeof body;
  } catch {
    return Response.json({ error: "Invalid JSON body." }, { status: 400 });
  }
  if (!body.installationId || !isValidId(body.installationId)) {
    return Response.json({ error: "Invalid installationId." }, { status: 400 });
  }
  const category = CATEGORIES.includes(body.category as ReportCategory)
    ? (body.category as ReportCategory)
    : "other";
  await doReport(env, body.installationId, {
    requestId: body.requestId,
    category,
    detail: (body.detail ?? "").slice(0, 1000),
    excerpt: (body.messageExcerpt ?? "").slice(0, 1000),
  });
  return Response.json({ ok: true });
}
