// Request validation: sizes, ids, abuse guardrails.

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export const MAX_MESSAGE_CHARS = 2000;
export const MAX_HISTORY_MESSAGES = 10;
export const MAX_CONTEXT_CHARS = 4000;

export function isValidId(value: unknown): value is string {
  return (
    typeof value === "string" &&
    value.length >= 8 &&
    value.length <= 128 &&
    (UUID_RE.test(value) || /^[A-Za-z0-9_-]+$/.test(value))
  );
}

export function sanitizeMessage(message: unknown): string | null {
  if (typeof message !== "string") return null;
  const trimmed = message.trim();
  if (trimmed.length === 0 || trimmed.length > MAX_MESSAGE_CHARS) return null;
  return trimmed;
}

// Minimal prompt-injection tripwires. These do not block the request;
// they are appended as a caution note for logging/downstream handling.
const INJECTION_PATTERNS = [
  /ignore (all )?previous instructions/i,
  /reveal (your )?system prompt/i,
  /you are now /i,
  /disregard (all )?prior/i,
];

export function detectInjectionAttempt(message: string): boolean {
  return INJECTION_PATTERNS.some((re) => re.test(message));
}
