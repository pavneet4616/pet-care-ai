import type { Env } from "../types.js";

export interface AiConfig {
  aiEnabled?: boolean;
  freeLimit?: number;
  lifetimeLimit?: number;
  proLimit?: number;
  rewardedBonus?: number;
}

export async function readAiConfig(env: Env): Promise<AiConfig> {
  const raw = await env.CONFIG.get("ai_config");
  if (!raw) return {};
  const value: unknown = JSON.parse(raw);
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("Invalid AI config");
  const config = value as AiConfig;
  if (config.aiEnabled !== undefined && typeof config.aiEnabled !== "boolean") throw new Error("Invalid AI switch");
  return config;
}
