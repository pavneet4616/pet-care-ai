import type { ChatRequest, Env, QuotaSnapshot } from "../types.js";
import { buildMessages } from "../ai/prompt.js";
import {
  REPAIR_INSTRUCTION,
  detectUrgent,
  fallbackAnswer,
  parseAiAnswer,
  type AiAnswer,
} from "../ai/answer.js";
import {
  MAX_CONTEXT_CHARS,
  detectInjectionAttempt,
  isValidId,
  sanitizeMessage,
} from "../security/validation.js";
import { applyPlayEntitlement } from "./billing.js";
import { doConsumeBonus, doRelease, doReserve, doStatus, quotaExhaustedBody } from "../quota/client.js";
import { readAiConfig } from "../security/aiConfig.js";
import { isLegacyRequest, legacyBucketId, requireIntegrity } from "../security/integrity.js";
import { registry } from "../security/RegistryDO.js";
import { sha256 } from "../security/crypto.js";

function badRequest(message: string): Response {
  return Response.json({ error: message }, { status: 400 });
}

interface ModelOutput {
  response?: unknown;
  result?: { response?: unknown };
  choices?: Array<{
    text?: unknown;
    message?: { content?: unknown; reasoning_content?: unknown; reasoning?: unknown };
  }>;
}

/** Extract display text from Workers AI chat output (plain, OpenAI, or nested). */
function toText(value: unknown): string {
  if (typeof value === "string") return value;
  if (Array.isArray(value)) {
    return value
      .map((part) => {
        if (typeof part === "string") return part;
        if (part && typeof part === "object") {
          const text = (part as { text?: unknown }).text;
          if (typeof text === "string") return text;
        }
        return "";
      })
      .join("");
  }
  return "";
}

function extractText(out: ModelOutput): string {
  const msg = out.choices?.[0]?.message;
  const candidates = [
    out.response,
    msg?.content,
    out.choices?.[0]?.text,
    out.result?.response,
  ];
  for (const value of candidates) {
    const text = toText(value).trim();
    if (text.length > 0) return text;
  }
  return "";
}

/** Short BCP-47-ish fallback language from the client's hint. */
function langFallback(langHint: unknown): string {
  if (typeof langHint === "string" && /^[a-z]{2}(-[A-Za-z0-9]+)?$/.test(langHint.trim().slice(0, 12))) {
    return langHint.trim().slice(0, 12);
  }
  return "en";
}

// Legacy daily caps bound spend for unauthenticated traffic. Setting a cap to 0 disables legacy AI.
const LEGACY_DEFAULT_IP_LIMIT = 25;
const LEGACY_DEFAULT_GLOBAL_LIMIT = 500;

function dailyCap(raw: string | undefined, fallback: number): number {
  const value = Number.parseInt(raw ?? "", 10);
  return Number.isInteger(value) && value >= 0 ? value : fallback;
}

/** Separate from the authenticated caps. Any registry error or reached cap refuses the request. */
async function legacyCostGuardAllows(env: Env, ip: string | null): Promise<boolean> {
  try {
    const checks = [
      { key: `legacy-ip:${await sha256(ip ?? "")}`, limit: dailyCap(env.LEGACY_IP_DAILY_LIMIT, LEGACY_DEFAULT_IP_LIMIT) },
      { key: "legacy-global", limit: dailyCap(env.LEGACY_GLOBAL_DAILY_LIMIT, LEGACY_DEFAULT_GLOBAL_LIMIT) },
    ];
    for (const check of checks) {
      const response = await registry(env, "/cost-guard", check);
      if (!response.ok || (await response.json() as { allowed?: boolean }).allowed !== true) return false;
    }
    return true;
  } catch {
    return false;
  }
}

/** Legacy callers never see rewarded credit as available. */
function legacyQuotaView(quota: QuotaSnapshot): QuotaSnapshot {
  return { ...quota, bonusRemaining: 0, bonusReady: false };
}

export async function handleChat(request: Request, env: Env): Promise<Response> {
  // Enforce the emergency switch before any Play API, quota, or inference call.
  try {
    if ((await readAiConfig(env)).aiEnabled === false) {
      return Response.json({ error: "AI_DISABLED" }, { status: 503 });
    }
  } catch {
    return Response.json({ error: "AI_CONFIG_UNAVAILABLE" }, { status: 503 });
  }
  let body: ChatRequest;
  // Pre-Integrity builds get only the restricted legacy path. Any presented attestation is fully verified.
  const legacy = isLegacyRequest(request, env);
  const integrityFailure = legacy ? null : await requireIntegrity(request,env);
  if (integrityFailure) return integrityFailure;
  try {
    body = (await request.json()) as ChatRequest;
  } catch {
    return badRequest("Invalid JSON body.");
  }
  if (!body || typeof body !== "object" || Array.isArray(body)) return badRequest("Invalid request body.");
  if (body.recentMessages !== undefined && !Array.isArray(body.recentMessages)) return badRequest("Invalid history.");
  if (body.recentMessages?.some((m) => !m || typeof m !== "object")) return badRequest("Invalid history message.");

  if (!isValidId(body.requestId)) return badRequest("Invalid requestId.");
  if (!isValidId(body.installationId)) return badRequest("Invalid installationId.");
  const message = sanitizeMessage(body.message);
  if (!message) return badRequest("Message must be 1-2000 characters.");

  // Legacy requests use their own free-tier bucket, which never holds a purchase or bonus grant.
  const bucket = legacy ? legacyBucketId(body.installationId) : body.installationId;
  if (legacy) console.log(JSON.stringify({ type: "legacy_chat", requestId: body.requestId }));

  const history = (body.recentMessages ?? []).slice(-6).map((m) => ({
    role: m.role === "assistant" ? ("assistant" as const) : ("user" as const),
    content: String(m.content ?? "").slice(0, 500),
  }));

  let petContext = body.petContext;
  if (petContext) {
    // Trim oversized context for cost/latency/focus.
    const summary = JSON.stringify(petContext);
    if (summary.length > MAX_CONTEXT_CHARS && petContext.summary7d) {
      petContext = { ...petContext, summary7d: petContext.summary7d.slice(0, 800) };
    }
  }

  // Attach Pro from a live Play token BEFORE reserving, so a monthly
  // subscriber is never charged against the free 5-question cap. Legacy callers cannot attach purchases.
  if (!legacy && body.purchaseToken && env.GOOGLE_SERVICE_ACCOUNT_JSON) {
    const current = await doStatus(env, body.installationId);
    if (current.tier !== "pro" && current.tier !== "lifetime") {
      const attached = await applyPlayEntitlement(
        env,
        body.installationId,
        body.purchaseToken,
        body.subscriptionId,
      );
      if (!attached.applied) {
        console.warn(
          JSON.stringify({
            type: "chat_entitlement_skip",
            requestId: body.requestId,
            error: attached.error,
          }),
        );
      }
    }
  }

  // Legacy callers can never consume rewarded credit.
  const useBonus = !legacy && (new URL(request.url).searchParams.get("bonus") === "1" || (body as { useBonus?: boolean }).useBonus === true);
  const reservation = useBonus
    ? await doConsumeBonus(env, bucket, body.requestId)
    : await doReserve(env, bucket, body.requestId);
  if (legacy) reservation.quota = legacyQuotaView(reservation.quota);

  if (reservation.duplicate) {
    return Response.json(
      { requestId: body.requestId, duplicate: true, quota: reservation.quota },
      { status: 200 },
    );
  }
  if (!reservation.allowed) return reservation.reason === "REWARD_PENDING" ? Response.json({error:"REWARD_PENDING",quota:reservation.quota},{status:409}) : quotaExhaustedBody(reservation.quota);

  // Operational spend ceiling also bounds scripts rotating UUIDs on a genuine Play install.
  // These are abuse limits, separate from each customer's unchanged daily question allowance.
  const ip = request.headers.get("cf-connecting-ip");
  if (legacy) {
    // Own buckets, so legacy traffic can neither exhaust nor bypass the authenticated caps.
    if (!(await legacyCostGuardAllows(env, ip))) {
      await doRelease(env,bucket,body.requestId);
      return Response.json({error:"RATE_LIMITED"},{status:429,headers:{"retry-after":"3600"}});
    }
  } else if (env.INTEGRITY_REQUIRED !== "false") {
    const ipGuard=reservation.quota.tier === "free" ? await registry(env,"/cost-guard",{key:`ip:${await sha256(ip ?? "")}`,limit:50}) : null;
    const ipAllowed=ipGuard ? (await ipGuard.json() as {allowed:boolean}).allowed : true;
    const globalGuard=ipAllowed ? await registry(env,"/cost-guard",{key:"global",limit:2000}) : null;
    if (!ipAllowed || !globalGuard || !(await globalGuard.json() as {allowed:boolean}).allowed) {
      await doRelease(env,bucket,body.requestId);
      return Response.json({error:"RATE_LIMITED"},{status:429,headers:{"retry-after":"3600"}});
    }
  }

  if (detectInjectionAttempt(message)) {
    console.log(JSON.stringify({ type: "injection_tripwire", requestId: body.requestId }));
  }

  const messages = buildMessages(message, petContext, history, (body as { languageMode?: string }).languageMode, body.langHint);
  const maxTokens = parseInt(env.MAX_TOKENS ?? "500", 10);
  const model = env.MODEL ?? "";
  const isGlm = model.includes("glm");
  const isQwen = model.includes("qwen");
  const aiInput = {
    messages,
    ...(isGlm
      ? { max_completion_tokens: maxTokens, temperature: 0.3, reasoning_effort: "low" }
      : isQwen
        ? {
            max_tokens: maxTokens,
            temperature: 0.6,
            top_p: 0.8,
            top_k: 20,
            enable_thinking: false,
          }
        : { max_tokens: maxTokens, temperature: 0.3 }),
  };

  // Structured v1: non-streaming JSON contract, validated server-side.
  // Android renders native cards and never displays raw model output.
  if ((body as { answerFormat?: string }).answerFormat === "structured") {
    const fallbackLang = langFallback(body.langHint);
    const userContent = detectUrgent(message)
      ? `${message}\n\n[SAFETY: the user may describe an emergency. You MUST set urgency to URGENT and advise immediate veterinary care first.]`
      : message;
    const structuredMessages = buildMessages(
      userContent,
      petContext,
      history,
      (body as { languageMode?: string }).languageMode,
      body.langHint,
    );
    try {
      const started = Date.now();
      const out = (await env.AI.run(env.MODEL, { ...aiInput, messages: structuredMessages })) as ModelOutput;
      const firstText = extractText(out);
      let answer = parseAiAnswer(firstText, fallbackLang);
      let source = "parsed";
      let lastText = firstText;
      if (!answer) {
        const retry = (await env.AI.run(env.MODEL, {
          ...aiInput,
          messages: [...structuredMessages, { role: "user", content: REPAIR_INSTRUCTION }],
        })) as ModelOutput;
        lastText = extractText(retry);
        answer = parseAiAnswer(lastText, fallbackLang);
        source = "repair";
      }
      if (!answer) source = "fallback";
      if (!answer) {
        console.warn(
          JSON.stringify({
            type: "parse_fail",
            requestId: body.requestId,
            preview: lastText.slice(0, 500),
          }),
        );
      }
      console.log(
        JSON.stringify({
          type: "chat_timing",
          model: env.MODEL,
          ms: Date.now() - started,
          source,
          requestId: body.requestId,
        }),
      );
      const finalAnswer: AiAnswer = answer ?? fallbackAnswer(fallbackLang);
      return Response.json({
        ok: true,
        requestId: body.requestId,
        messageId: body.requestId,
        answer: finalAnswer,
        quota: reservation.quota,
      });
    } catch (err) {
      await doRelease(env, bucket, body.requestId);
      console.error(JSON.stringify({ type: "ai_error", requestId: body.requestId, err: String(err) }));
      return Response.json({ error: "AI_UNAVAILABLE" }, { status: 502 });
    }
  }

  try {
    if (body.stream === false) {
      const out = (await env.AI.run(env.MODEL, aiInput)) as ModelOutput;
      const reply = extractText(out);
      return Response.json({ requestId: body.requestId, reply, quota: reservation.quota });
    }

    // Streaming SSE (OpenAI delta format). Forwarded raw + final quota event.
    const stream = (await env.AI.run(env.MODEL, { ...aiInput, stream: true })) as unknown;

    if (!(stream instanceof ReadableStream)) {
      const reply = extractText(stream as ModelOutput);
      return Response.json({ requestId: body.requestId, reply, quota: reservation.quota });
    }

    const quotaEvent = `event: quota\ndata: ${JSON.stringify({
      requestId: body.requestId,
      quota: reservation.quota,
    })}\n\n`;
    const byteStream = stream as ReadableStream<Uint8Array>;
    const decoder = new TextDecoder();
    const withQuota = byteStream.pipeThrough(
      new TransformStream<Uint8Array, string>({
        transform(chunk, controller) {
          controller.enqueue(decoder.decode(chunk, { stream: true }));
        },
        flush(controller) {
          controller.enqueue(decoder.decode());
          controller.enqueue(quotaEvent);
        },
      }),
    );
    return new Response(withQuota.pipeThrough(new TextEncoderStream()), {
      headers: {
        "content-type": "text/event-stream",
        "cache-control": "no-cache",
        connection: "keep-alive",
        "x-request-id": body.requestId,
      },
    });
  } catch (err) {
    // Release the reservation so failures never burn quota.
    await doRelease(env, bucket, body.requestId);
    console.error(JSON.stringify({ type: "ai_error", requestId: body.requestId, err: String(err) }));
    return Response.json({ error: "AI_UNAVAILABLE" }, { status: 502 });
  }
}
