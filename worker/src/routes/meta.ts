import type { Env, ReportCategory } from "../types.js";
import { registry } from "../security/RegistryDO.js";
import { requireIntegrity } from "../security/integrity.js";
import { isValidId } from "../security/validation.js";
import { readAiConfig, type AiConfig } from "../security/aiConfig.js";

const CATEGORIES: ReportCategory[] = ["unsafe", "incorrect", "offensive", "other"];

export async function handleAiStatus(_request: Request, env: Env): Promise<Response> {
  let override: AiConfig;
  try {
    override = await readAiConfig(env);
  } catch {
    return Response.json({ aiEnabled: false, modelStatus: "unavailable" }, { status: 503 });
  }
  return Response.json({
    aiEnabled: override.aiEnabled ?? true,
    freeLimit: override.freeLimit ?? parseInt(env.FREE_DAILY ?? "5", 10),
    lifetimeLimit: override.lifetimeLimit ?? parseInt(env.LIFETIME_DAILY ?? "10", 10),
    proLimit: override.proLimit ?? parseInt(env.PRO_DAILY ?? "20", 10),
    rewardedBonus: override.rewardedBonus ?? parseInt(env.BONUS_DAILY ?? "1", 10),
    modelStatus: "healthy",
    model: env.MODEL,
    integrityProjectNumber: env.PLAY_CLOUD_PROJECT_NUMBER ?? null,
  });
}

// In-app AI answer reporting (Play AI-generated-content policy).
// Stores only metadata + short excerpt, never the full history.
export async function handleReport(request: Request, env: Env): Promise<Response> {
  const failure=await requireIntegrity(request,env);
  if (failure) return failure;
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
  const stored=await registry(env,"/report", {
    id: crypto.randomUUID(), installationId: body.installationId,
    requestId: body.requestId,
    category,
    detail: (body.detail ?? "").slice(0, 1000),
    excerpt: (body.messageExcerpt ?? "").slice(0, 1000),
  });
  if (!stored.ok) return Response.json({error:"REPORT_UNAVAILABLE"},{status:503});
  return Response.json({ ok: true });
}
