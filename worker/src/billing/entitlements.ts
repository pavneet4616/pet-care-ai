import type { Tier } from "../types.js";

// Resolve effective tier. Server is the source of truth; the Android
// client only sends a hint (purchase token) which must be verified.
export function tierFromFlags(input: {
  hasActiveSubscription: boolean;
  hasLifetimePremium: boolean;
}): Tier {
  if (input.hasActiveSubscription) return "pro";
  if (input.hasLifetimePremium) return "lifetime";
  return "free";
}

export function quizLimitForTier(
  tier: Tier,
  limits: { free: number; lifetime: number; pro: number },
): number {
  return tier === "pro" ? limits.pro : tier === "lifetime" ? limits.lifetime : limits.free;
}

/** Carry a Durable Object entitlement into a new UTC day. */
export function carryTier(
  prev: { tier: string; proExpiry: number } | undefined,
  now = Date.now(),
): Tier {
  if (!prev) return "free";
  if (prev.tier === "lifetime") return "lifetime";
  if (prev.tier === "pro") {
    if (prev.proExpiry === 0 || prev.proExpiry > now) return "pro";
    return "free";
  }
  return "free";
}
