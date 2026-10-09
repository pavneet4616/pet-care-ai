import type { Env } from "./types.js";
import { AiQuotaDO } from "./quota/AiQuotaDO.js";
import { handleChat } from "./routes/chat.js";
import { handleRtdn, handleVerify } from "./routes/billing.js";
import { handleAiStatus, handleReport } from "./routes/meta.js";
import { handleReward } from "./routes/rewards.js";
import { handleDataDelete, handleQuotaStatus, handleAdminReports } from "./routes/data.js";

export { AiQuotaDO };
export { RegistryDO } from "./security/RegistryDO.js";

const MAX_BODY_BYTES = 32 * 1024;

async function boundedBody(request: Request): Promise<Request | Response> {
  const reader = request.body?.getReader();
  if (!reader) return request;
  const chunks: Uint8Array[] = [];
  let length = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    length += value.byteLength;
    if (length > MAX_BODY_BYTES) {
      await reader.cancel();
      return Response.json({ error: "REQUEST_TOO_LARGE" }, { status: 413 });
    }
    chunks.push(value);
  }
  const body = new Uint8Array(length);
  let offset = 0;
  for (const chunk of chunks) { body.set(chunk, offset); offset += chunk.byteLength; }
  return new Request(request.url, { method: request.method, headers: request.headers, body });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    try {
      if (request.method === "POST") {
        const bounded = await boundedBody(request);
        if (bounded instanceof Response) return bounded;
        request = bounded;
      }
      if (request.method === "POST" && ["/v1/chat", "/v1/billing/google/verify", "/v1/chat/report","/v1/data/delete","/v1/quota/status"].includes(url.pathname)) {
        // Coarse IP guard supplements per-install quotas; it does not authenticate an installation.
        const ip = request.headers.get("cf-connecting-ip");
        if (!ip) return Response.json({ error: "CLIENT_IP_REQUIRED" }, { status: 400 });
        const { success } = await env.API_RATE_LIMITER.limit({ key: ip });
        if (!success) return Response.json({ error: "RATE_LIMITED" }, { status: 429, headers: { "retry-after": "60" } });
      }
      if (url.pathname === "/v1/chat" && request.method === "POST") return await handleChat(request, env);
      if (url.pathname === "/v1/ads/reward" && request.method === "GET") return await handleReward(request,env);
      if (url.pathname === "/v1/data/delete" && request.method === "POST") return await handleDataDelete(request,env);
      if (url.pathname === "/v1/quota/status" && request.method === "POST") return await handleQuotaStatus(request,env);
      if (url.pathname === "/v1/admin/reports" && ["GET","POST"].includes(request.method)) return await handleAdminReports(request,env);
      if (url.pathname === "/v1/ai/status" && request.method === "GET")
        return await handleAiStatus(request, env);
      if (url.pathname === "/v1/chat/report" && request.method === "POST")
        return await handleReport(request, env);
      if (url.pathname === "/v1/billing/google/verify" && request.method === "POST")
        return await handleVerify(request, env);
      if (url.pathname === "/v1/billing/google/rtdn" && request.method === "POST")
        return await handleRtdn(request, env);
      if (url.pathname === "/" && request.method === "GET")
        return Response.json({ ok: true, service: "pet-companion-ai" });
      return Response.json({ error: "not found" }, { status: 404 });
    } catch (err) {
      console.error(JSON.stringify({ type: "unhandled" }));
      return Response.json({ error: "INTERNAL" }, { status: 500 });
    }
  },
};
