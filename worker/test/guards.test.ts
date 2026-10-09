import { describe, expect, it, vi } from "vitest";
import worker from "../src/index.js";
import { handleChat } from "../src/routes/chat.js";
import { handleAiStatus } from "../src/routes/meta.js";
import {envVars} from "./helpers/vars.js";
import type { Env } from "../src/types.js";

function mockBinding<T extends object>(members: Partial<T>): T {
  return new Proxy(members, {
    get(target, property) {
      if (property in target) return Reflect.get(target, property);
      throw new Error(`Unexpected binding access: ${String(property)}`);
    },
  }) as T;
}

function guardedEnv(config: string | null, rateAllowed = true) {
  const aiRun = vi.fn();
  const quotaId = vi.fn();
  const getConfig = vi.fn().mockResolvedValue(config);
  const limit = vi.fn().mockResolvedValue({ success: rateAllowed });
  const env = {
    ...envVars,
    MODEL: "@cf/qwen/qwen3-30b-a3b-fp8",
    FREE_DAILY: "5", LIFETIME_DAILY: "10", PRO_DAILY: "20", BONUS_DAILY: "1",
    MAX_TOKENS: "500", PLAY_PACKAGE_NAME: "com.petcares.companionlites",
    AI_WEEKLY_SKU: "ai_weekly_pass", AI_MONTHLY_SKU: "ai_monthly_pass",
    CONFIG: mockBinding<KVNamespace>({ get: getConfig }),
    AI: mockBinding<Ai>({ run: aiRun }),
    AI_QUOTA: mockBinding<Env["AI_QUOTA"]>({ idFromName: quotaId }),
    SECURITY_REGISTRY: mockBinding<Env["SECURITY_REGISTRY"]>({}),
    API_RATE_LIMITER: { limit },
  } satisfies Env;
  return { env, aiRun, quotaId, getConfig, limit };
}

function request(body: string, headers: Record<string, string> = {}) {
  return new Request("https://example.com/v1/chat", {
    method: "POST", headers: { "content-type": "application/json", "cf-connecting-ip": "192.0.2.1", ...headers }, body,
  });
}

describe("AI cost guards", () => {
  it("checks the kill switch before any inference or quota reservation", async () => {
    const { env, aiRun, quotaId } = guardedEnv('{"aiEnabled":false}');
    const response = await handleChat(request("{}"), env);
    expect(response.status).toBe(503);
    expect(await response.json()).toEqual({ error: "AI_DISABLED" });
    expect(aiRun).not.toHaveBeenCalled();
    expect(quotaId).not.toHaveBeenCalled();
  });

  it("fails closed when config is corrupt or unavailable", async () => {
    const { env, getConfig, aiRun } = guardedEnv("invalid JSON");
    expect((await handleChat(request("{}"), env)).status).toBe(503);
    getConfig.mockRejectedValueOnce(new Error("KV unavailable"));
    expect((await handleChat(request("{}"), env)).status).toBe(503);
    expect(aiRun).not.toHaveBeenCalled();
    const status = await handleAiStatus(new Request("https://example.com/v1/ai/status"), env);
    expect(status.status).toBe(503);
    expect(await status.json()).toMatchObject({ aiEnabled: false });
  });

  it("rejects a rate-limited request before reading config or spending AI", async () => {
    const { env, aiRun, getConfig, limit } = guardedEnv(null, false);
    const response = await worker.fetch(request("{}"), env);
    expect(response.status).toBe(429);
    expect(response.headers.get("retry-after")).toBe("60");
    expect(limit).toHaveBeenCalledWith({ key: "192.0.2.1" });
    expect(getConfig).not.toHaveBeenCalled();
    expect(aiRun).not.toHaveBeenCalled();
  });

  it("bounds bodies even when content-length is absent", async () => {
    const { env, aiRun, getConfig } = guardedEnv(null);
    const response = await worker.fetch(request("x".repeat(32 * 1024 + 1)), env);
    expect(response.status).toBe(413);
    expect(aiRun).not.toHaveBeenCalled();
    expect(getConfig).not.toHaveBeenCalled();
  });

  it("does not publish wildcard browser access", async () => {
    const { env } = guardedEnv(null);
    const response = await worker.fetch(new Request("https://example.com/"), env);
    expect(response.headers.has("access-control-allow-origin")).toBe(false);
  });

  it("returns a client error for null and malformed history", async () => {
    const { env } = guardedEnv(null);
    const localEnv: Env = {...env,INTEGRITY_REQUIRED:"false"};
    expect((await handleChat(request("null"), localEnv)).status).toBe(400);
    expect((await handleChat(request('{"recentMessages":{}}'), localEnv)).status).toBe(400);
  });
});
