import { DatabaseSync } from "node:sqlite";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import worker from "../src/index.js";
import { getAccessToken, verifySubscription } from "../src/billing/googlePlay.js";
import { AiQuotaDO } from "../src/quota/AiQuotaDO.js";
import { RegistryDO } from "../src/security/RegistryDO.js";
import { sha256 } from "../src/security/crypto.js";
import { envVars } from "./helpers/vars.js";
import type { Env } from "../src/types.js";

vi.mock("../src/billing/googlePlay.js", () => ({
  getAccessToken: vi.fn(async () => "access-token"),
  verifySubscription: vi.fn(),
  verifyOneTimeProduct: vi.fn(),
}));

const INSTALL = "123e4567-e89b-12d3-a456-426614174000";
const SECRET = "a".repeat(43);
const IP = "192.0.2.10";
const CHAT = "https://example.com/v1/chat";
const DIGEST = envVars.PLAY_SIGNING_DIGESTS;

interface Instance {
  db: DatabaseSync;
  fetch(request: Request): Promise<Response>;
}

// Runs the Durable Objects' real SQL against in-memory SQLite, as the existing quota and registry tests do.
function sqlStorage(db: DatabaseSync): SqlStorage {
  return {
    exec(query: string, ...bindings: SqlStorageValue[]) {
      const values = bindings.map((value) => (value instanceof ArrayBuffer ? new Uint8Array(value) : value));
      let rows: Record<string, unknown>[] = [];
      if (/^\s*(SELECT|PRAGMA)/i.test(query)) rows = db.prepare(query).all(...values);
      else if (bindings.length) db.prepare(query).run(...values);
      else db.exec(query);
      return { toArray: () => rows };
    },
  } as SqlStorage;
}

function quotaInstance(): Instance {
  const db = new DatabaseSync(":memory:");
  const quota = new AiQuotaDO({ storage: { sql: sqlStorage(db) } } as unknown as DurableObjectState, {
    FREE_DAILY: "5", LIFETIME_DAILY: "10", PRO_DAILY: "20", BONUS_DAILY: "1",
  });
  return { db, fetch: (request) => quota.fetch(request) };
}

function registryInstance(): Instance {
  const db = new DatabaseSync(":memory:");
  const registry = new RegistryDO({ storage: { sql: sqlStorage(db) } } as unknown as DurableObjectState);
  return { db, fetch: (request) => registry.fetch(request) };
}

// Durable Object namespace with one lazily created instance per name.
function durableNamespace(create: () => Instance) {
  const instances = new Map<string, Instance>();
  return {
    instances,
    idFromName: (name: string) => name,
    get(name: string) {
      let instance = instances.get(name);
      if (!instance) {
        instance = create();
        instances.set(name, instance);
      }
      const stub = instance;
      return { fetch: (url: string, init?: RequestInit) => stub.fetch(new Request(url, init)) };
    },
  };
}

function harness(patch: Record<string, string | undefined> = {}) {
  const quotas = durableNamespace(quotaInstance);
  const registry = durableNamespace(registryInstance);
  const aiRun = vi.fn(async () => ({ response: "Keep water fresh and watch appetite." }));
  const env = {
    ...envVars,
    INTEGRITY_MODE: "legacy_allowed",
    INTEGRITY_MIN_VERSION_CODE: "11",
    GOOGLE_SERVICE_ACCOUNT_JSON: JSON.stringify({ client_email: "sa@example.iam.gserviceaccount.com", private_key: "unused" }),
    CONFIG: { get: vi.fn(async () => null) },
    AI: { run: aiRun },
    AI_QUOTA: quotas,
    SECURITY_REGISTRY: registry,
    API_RATE_LIMITER: { limit: vi.fn(async () => ({ success: true })) },
    ...patch,
  } as unknown as Env;
  return { env, quotas, registry, aiRun };
}

// Any network call fails the test unless it is the Play Integrity decode request.
function networkWith(play?: () => Response) {
  return vi.fn(async (url: string): Promise<Response> => {
    if (play && url.startsWith("https://playintegrity.googleapis.com/")) return play();
    throw new Error(`Unexpected network call: ${url}`);
  });
}

function verdict(requestHash: string) {
  return {
    requestDetails: { requestPackageName: envVars.PLAY_PACKAGE_NAME, requestHash, timestampMillis: String(Date.now()) },
    appIntegrity: { packageName: envVars.PLAY_PACKAGE_NAME, appRecognitionVerdict: "PLAY_RECOGNIZED", certificateSha256Digest: [DIGEST] },
    deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY"] },
    accountDetails: { appLicensingVerdict: "LICENSED" },
  };
}

function body(overrides: Record<string, unknown> = {}): string {
  return JSON.stringify({
    requestId: crypto.randomUUID(),
    installationId: crypto.randomUUID(),
    message: "Is my dog due for a checkup?",
    stream: false,
    ...overrides,
  });
}

// Assertions check the parsed shape; this only types the read.
async function readJson(res: Response): Promise<any> {
  return res.json();
}

function post(url: string, text: string, headers: Record<string, string> = {}): Request {
  return new Request(url, {
    method: "POST",
    headers: { "content-type": "application/json", "cf-connecting-ip": IP, ...headers },
    body: text,
  });
}

let network: ReturnType<typeof networkWith>;

beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(console, "log").mockImplementation(() => {});
  network = networkWith();
  vi.stubGlobal("fetch", network);
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("integrity mode gate", () => {
  it("enforces when INTEGRITY_MODE is unset, rejecting tokenless and old-version chat before any AI or quota work", async () => {
    const h = harness({ INTEGRITY_MODE: undefined });
    const claims: Record<string, string>[] = [{}, { "x-app-version-code": "10" }];
    for (const headers of claims) {
      const res = await worker.fetch(post(CHAT, body(), headers), h.env);
      expect(res.status).toBe(401);
      expect(await res.json()).toEqual({ error: "INTEGRITY_REQUIRED" });
    }
    expect(h.aiRun).not.toHaveBeenCalled();
    expect(h.quotas.instances.size).toBe(0);
    expect(network).not.toHaveBeenCalled();
  });

  it.each(["enforce", "LEGACY_ALLOWED", "legacy", ""])("treats INTEGRITY_MODE=%j as enforce", async (mode) => {
    const h = harness({ INTEGRITY_MODE: mode });
    const res = await worker.fetch(post(CHAT, body(), { "x-app-version-code": "10" }), h.env);
    expect(res.status).toBe(401);
    expect(h.aiRun).not.toHaveBeenCalled();
  });

  it.each([undefined, "", "0", "abc", "-1", "1.5"])("keeps enforcing when INTEGRITY_MIN_VERSION_CODE is %j", async (minimum) => {
    const h = harness({ INTEGRITY_MIN_VERSION_CODE: minimum });
    const res = await worker.fetch(post(CHAT, body()), h.env);
    expect(res.status).toBe(401);
    expect(h.aiRun).not.toHaveBeenCalled();
  });

  it("serves an old-version tokenless chat on the free legacy bucket", async () => {
    const claims: Record<string, string>[] = [{}, { "x-app-version-code": "10" }];
    for (const headers of claims) {
      const h = harness();
      const res = await worker.fetch(post(CHAT, body({ installationId: INSTALL }), headers), h.env);
      expect(res.status).toBe(200);
      const json = await readJson(res);
      expect(json.reply).toContain("water");
      expect(json.quota).toMatchObject({ tier: "free", limit: 5, used: 1, bonusRemaining: 0, bonusReady: false });
      expect([...h.quotas.instances.keys()]).toEqual([`install:legacy:${INSTALL}`]);
      expect(h.aiRun).toHaveBeenCalledTimes(1);
      expect(network).not.toHaveBeenCalled();
    }
  });

  it.each<Record<string, string>>([
    { "x-app-version-code": "11" },
    { "x-app-version-code": "12" },
    { "x-app-version-code": "99999999" },
    { "x-app-version-code": "abc" },
    { "x-app-version-code": "" },
    { "x-app-version-code": "1.5" },
    { "x-app-version-code": "-1" },
  ])("rejects a tokenless chat claiming an unusable or current versionCode %j", async (headers) => {
    const h = harness();
    const res = await worker.fetch(post(CHAT, body(), headers), h.env);
    expect(res.status).toBe(401);
    expect(await res.json()).toEqual({ error: "INTEGRITY_REQUIRED" });
    expect(h.aiRun).not.toHaveBeenCalled();
    expect(h.quotas.instances.size).toBe(0);
  });

  it("rejects a presented but invalid Play verdict instead of falling back to legacy", async () => {
    const h = harness();
    network = networkWith(() => Response.json({ tokenPayloadExternal: { requestDetails: { requestHash: "wrong" } } }));
    vi.stubGlobal("fetch", network);
    const res = await worker.fetch(post(CHAT, body({ installationId: INSTALL }), {
      "x-play-integrity": "forged-token", "x-installation-secret": SECRET, "x-app-version-code": "10",
    }), h.env);
    expect(res.status).toBe(403);
    expect(await res.json()).toEqual({ error: "INTEGRITY_FAILED" });
    expect(network).toHaveBeenCalledWith(expect.stringMatching(/^https:\/\/playintegrity\.googleapis\.com\//), expect.anything());
    expect(h.aiRun).not.toHaveBeenCalled();
    expect(h.quotas.instances.size).toBe(0);
  });

  it("reports an unavailable Play decode as unavailable, never as legacy", async () => {
    const h = harness();
    network = networkWith(() => new Response("upstream failure", { status: 500 }));
    vi.stubGlobal("fetch", network);
    const res = await worker.fetch(post(CHAT, body(), { "x-play-integrity": "token", "x-installation-secret": SECRET }), h.env);
    expect(res.status).toBe(503);
    expect(await res.json()).toEqual({ error: "INTEGRITY_UNAVAILABLE" });
    expect(h.aiRun).not.toHaveBeenCalled();
  });

  it.each<{ name: string; headers: Record<string, string> }>([
    { name: "secret only", headers: { "x-installation-secret": SECRET } },
    { name: "token only", headers: { "x-play-integrity": "token" } },
    { name: "malformed secret", headers: { "x-play-integrity": "token", "x-installation-secret": "short" } },
    { name: "empty token header", headers: { "x-play-integrity": "", "x-installation-secret": SECRET } },
  ])("rejects an incomplete attestation ($name) without a legacy fallback", async ({ headers }) => {
    const h = harness();
    const res = await worker.fetch(post(CHAT, body({ installationId: INSTALL }), { ...headers, "x-app-version-code": "10" }), h.env);
    expect(res.status).toBe(401);
    expect(await res.json()).toEqual({ error: "INTEGRITY_REQUIRED" });
    expect(network).not.toHaveBeenCalled();
    expect(h.aiRun).not.toHaveBeenCalled();
  });

  it("still fully serves a verified request under legacy_allowed, on the attested bucket", async () => {
    const h = harness();
    const text = body({ installationId: INSTALL });
    const hash = await sha256(`POST\n/v1/chat\n${text}\n${SECRET}`);
    network = networkWith(() => Response.json({ tokenPayloadExternal: verdict(hash) }));
    vi.stubGlobal("fetch", network);
    const res = await worker.fetch(post(CHAT, text, { "x-play-integrity": "valid-token", "x-installation-secret": SECRET }), h.env);
    expect(res.status).toBe(200);
    expect((await readJson(res)).quota).toMatchObject({ tier: "free", used: 1 });
    expect([...h.quotas.instances.keys()]).toEqual([`install:${INSTALL}`]);
  });

  it("keeps the AI kill switch ahead of the legacy path", async () => {
    const h = harness();
    h.env.CONFIG = { get: vi.fn(async () => '{"aiEnabled":false}') } as unknown as KVNamespace;
    const res = await worker.fetch(post(CHAT, body()), h.env);
    expect(res.status).toBe(503);
    expect(await res.json()).toEqual({ error: "AI_DISABLED" });
    expect(h.aiRun).not.toHaveBeenCalled();
  });
});

describe("legacy restrictions", () => {
  it("never attaches a Play purchase for a legacy caller", async () => {
    const h = harness();
    const res = await worker.fetch(post(CHAT, body({
      installationId: INSTALL, purchaseToken: "play-receipt", subscriptionId: envVars.AI_MONTHLY_SKU,
    })), h.env);
    expect(res.status).toBe(200);
    expect(verifySubscription).not.toHaveBeenCalled();
    expect(getAccessToken).not.toHaveBeenCalled();
    expect(network).not.toHaveBeenCalled();
    // Only the legacy bucket was touched; the attested bucket and the receipt registry stayed untouched.
    expect([...h.quotas.instances.keys()]).toEqual([`install:legacy:${INSTALL}`]);
    expect(h.registry.instances.get("audit-registry-v1")!.db.prepare("SELECT COUNT(*) AS n FROM owners").get()).toEqual({ n: 0 });
  });

  it("never inherits an attested Pro entitlement", async () => {
    const h = harness();
    const attested = h.quotas.get(`install:${INSTALL}`);
    await attested.fetch("https://quota/set-entitlement", {
      method: "POST",
      body: JSON.stringify({ tier: "pro", proExpiry: Date.now() + 86_400_000, tokenHash: "sub-token" }),
    });
    const res = await worker.fetch(post(CHAT, body({ installationId: INSTALL })), h.env);
    expect(res.status).toBe(200);
    expect((await readJson(res)).quota).toMatchObject({ tier: "free", limit: 5 });
    expect((await readJson(await attested.fetch("https://quota/status"))).quota).toMatchObject({ tier: "pro", used: 0 });
  });

  it("never consumes attested rewarded credit, even when asked to", async () => {
    const h = harness();
    const attested = h.quotas.get(`install:${INSTALL}`);
    await attested.fetch("https://quota/grant-bonus", { method: "POST", body: JSON.stringify({ transactionId: "ad-1" }) });
    const res = await worker.fetch(post(`${CHAT}?bonus=1`, body({ installationId: INSTALL, useBonus: true })), h.env);
    expect(res.status).toBe(200);
    expect((await readJson(res)).quota).toMatchObject({ tier: "free", bonusRemaining: 0, bonusReady: false });
    // The attested reward grant is still unused and its bonus counter is untouched.
    const grants = h.quotas.instances.get(`install:${INSTALL}`)!.db.prepare("SELECT used_by FROM reward_grants").all();
    expect(grants).toEqual([{ used_by: null }]);
    expect((await readJson(await attested.fetch("https://quota/status"))).quota).toMatchObject({ bonusRemaining: 1, bonusReady: true });
  });

  it("gives legacy callers only the free allowance and never a reward prompt", async () => {
    const h = harness();
    for (let i = 0; i < 5; i++) {
      expect((await worker.fetch(post(CHAT, body({ installationId: INSTALL })), h.env)).status).toBe(200);
    }
    const exhausted = await worker.fetch(post(CHAT, body({ installationId: INSTALL })), h.env);
    expect(exhausted.status).toBe(429);
    expect(await exhausted.json()).toMatchObject({ error: { code: "AI_DAILY_QUOTA_REACHED" }, rewardedEligible: false });
    expect(h.aiRun).toHaveBeenCalledTimes(5);
  });

  it("applies separate legacy IP and global caps and releases the refused reservation", async () => {
    const h = harness({ LEGACY_IP_DAILY_LIMIT: "2", LEGACY_GLOBAL_DAILY_LIMIT: "1000" });
    for (let i = 0; i < 2; i++) expect((await worker.fetch(post(CHAT, body()), h.env)).status).toBe(200);
    const refused = await worker.fetch(post(CHAT, body({ installationId: INSTALL })), h.env);
    expect(refused.status).toBe(429);
    expect(refused.headers.get("retry-after")).toBe("3600");
    expect(await refused.json()).toEqual({ error: "RATE_LIMITED" });
    const bucket = (await readJson(await h.quotas.get(`install:legacy:${INSTALL}`).fetch("https://quota/status"))).quota;
    expect(bucket.used).toBe(0);
    expect(h.aiRun).toHaveBeenCalledTimes(2);
    const keys = h.registry.instances.get("audit-registry-v1")!.db.prepare("SELECT key FROM limits").all();
    expect(keys.length).toBeGreaterThan(0);
    expect(keys.every((row) => String(row.key).startsWith("legacy-"))).toBe(true);
  });

  it("stops legacy AI at the global legacy cap, and a zero cap disables it", async () => {
    const capped = harness({ LEGACY_GLOBAL_DAILY_LIMIT: "1" });
    expect((await worker.fetch(post(CHAT, body(), { "cf-connecting-ip": "198.51.100.1" }), capped.env)).status).toBe(200);
    expect((await worker.fetch(post(CHAT, body(), { "cf-connecting-ip": "198.51.100.2" }), capped.env)).status).toBe(429);

    const disabled = harness({ LEGACY_GLOBAL_DAILY_LIMIT: "0" });
    expect((await worker.fetch(post(CHAT, body()), disabled.env)).status).toBe(429);
    expect(disabled.aiRun).not.toHaveBeenCalled();
  });

  it("keeps purchase, report, deletion, admin, reward and RTDN endpoints closed to legacy callers", async () => {
    const h = harness();
    const attempts: Array<[Request, number, string]> = [
      [post("https://example.com/v1/billing/google/verify", body({ installationId: INSTALL, purchaseToken: "receipt" })), 401, "INTEGRITY_REQUIRED"],
      [post("https://example.com/v1/chat/report", body({ installationId: INSTALL, category: "other" })), 401, "INTEGRITY_REQUIRED"],
      [post("https://example.com/v1/data/delete", body({ installationId: INSTALL })), 401, "INTEGRITY_REQUIRED"],
      [post("https://example.com/v1/quota/status", body({ installationId: INSTALL })), 401, "UNAUTHORIZED"],
      [post("https://example.com/v1/admin/reports", body({ id: "report" })), 401, "UNAUTHORIZED"],
      [new Request("https://example.com/v1/admin/reports", { headers: { "cf-connecting-ip": IP } }), 401, "UNAUTHORIZED"],
      [new Request("https://example.com/v1/ads/reward", { headers: { "cf-connecting-ip": IP } }), 400, "INVALID_REWARD"],
      [post("https://example.com/v1/billing/google/rtdn", body({ message: { data: "" } })), 401, "UNAUTHORIZED"],
    ];
    for (const [request, status, error] of attempts) {
      const res = await worker.fetch(request, h.env);
      expect(res.status, request.url).toBe(status);
      expect(await res.json()).toEqual({ error });
    }
    expect(h.aiRun).not.toHaveBeenCalled();
    expect(h.quotas.instances.size).toBe(0);
    expect(verifySubscription).not.toHaveBeenCalled();
    expect(network).not.toHaveBeenCalled();
  });
});
