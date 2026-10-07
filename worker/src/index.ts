import type { Env } from "./types.js";
import { AiQuotaDO } from "./quota/AiQuotaDO.js";
import { handleChat } from "./routes/chat.js";
import { handleRtdn, handleVerify } from "./routes/billing.js";
import { handleAiStatus, handleReport } from "./routes/meta.js";

export { AiQuotaDO };

const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET, POST, OPTIONS",
  "access-control-allow-headers": "content-type, x-request-id",
};

function withCors(res: Response): Response {
  const headers = new Headers(res.headers);
  for (const [k, v] of Object.entries(CORS)) headers.set(k, v);
  return new Response(res.body, { status: res.status, statusText: res.statusText, headers });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
    const url = new URL(request.url);

    try {
      if (url.pathname === "/v1/chat" && request.method === "POST") return withCors(await handleChat(request, env));
      if (url.pathname === "/v1/ai/status" && request.method === "GET")
        return withCors(await handleAiStatus(request, env));
      if (url.pathname === "/v1/chat/report" && request.method === "POST")
        return withCors(await handleReport(request, env));
      if (url.pathname === "/v1/billing/google/verify" && request.method === "POST")
        return withCors(await handleVerify(request, env));
      if (url.pathname === "/v1/billing/google/rtdn" && request.method === "POST")
        return withCors(await handleRtdn(request, env));
      if (url.pathname === "/" && request.method === "GET")
        return withCors(Response.json({ ok: true, service: "pet-companion-ai" }));
      return withCors(Response.json({ error: "not found" }, { status: 404 }));
    } catch (err) {
      console.error(JSON.stringify({ type: "unhandled", err: String(err) }));
      return withCors(Response.json({ error: "INTERNAL" }, { status: 500 }));
    }
  },
};
