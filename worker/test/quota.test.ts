import { DatabaseSync } from "node:sqlite";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AiQuotaDO } from "../src/quota/AiQuotaDO.js";

// Execute the object's actual SQL against SQLite, including its upgrade path.
function createQuota(legacy = false) {
  const db = new DatabaseSync(":memory:");
  if (legacy) db.exec("CREATE TABLE processed_requests (request_id TEXT PRIMARY KEY, day TEXT NOT NULL)");
  const sql = {
    exec(query: string, ...bindings: SqlStorageValue[]) {
      const values = bindings.map((value) => value instanceof ArrayBuffer ? new Uint8Array(value) : value);
      let rows: Record<string, unknown>[] = [];
      if (/^\s*(SELECT|PRAGMA)/i.test(query)) {
        rows = db.prepare(query).all(...values);
      } else if (bindings.length) {
        db.prepare(query).run(...values);
      } else {
        db.exec(query);
      }
      return { toArray: () => rows };
    },
  };
  const state = { storage: { sql: sql as SqlStorage } } as DurableObjectState;
  const quota = new AiQuotaDO(state, { FREE_DAILY: "5", BONUS_DAILY: "1", PRO_DAILY: "20" });
  return {
    db,
    async call(path: string, body?: object) {
      const response = await quota.fetch(new Request(`https://quota${path}`, {
        method: body ? "POST" : "GET",
        ...(body ? { body: JSON.stringify(body), headers: { "content-type": "application/json" } } : {}),
      }));
      return response.json() as Promise<{ quota: { used: number; bonusRemaining: number; tier: string; limit: number }; duplicate?: boolean; allowed?: boolean }>;
    },
  };
}

describe("SQLite quota reservations", () => {
  beforeEach(() => { vi.useFakeTimers(); vi.setSystemTime(new Date("2026-10-07T23:59:00Z")); });
  afterEach(() => { vi.useRealTimers(); });

  it("requires a signed server reward grant before consuming a bonus", async () => {
    const {call,db}=createQuota();
    expect((await call("/consume-bonus",{requestId:"unearned"})).allowed).toBe(false);
    await call("/grant-bonus",{transactionId:"verified"});
    expect((await call("/consume-bonus",{requestId:"earned"})).allowed).toBe(true);
    expect((await call("/consume-bonus",{requestId:"second"})).allowed).toBe(false);
    db.close();
  });

  it("ignores an expired replaced token while a newer subscription is current", async () => {
    const {call,db}=createQuota();
    await call("/set-entitlement",{tier:"pro",proExpiry:Date.now()+10000,tokenHash:"new"});
    await call("/set-entitlement",{tier:"free",proExpiry:0,expectedTokenHash:"old"});
    expect((await call("/status")).quota.tier).toBe("pro");
    await call("/set-entitlement",{tier:"free",proExpiry:0,expectedTokenHash:"new"});
    expect((await call("/status")).quota.tier).toBe("free");
    db.close();
  });

  it("retains verified lifetime after subscription expiry and clears a confirmed refund", async () => {
    const {call,db}=createQuota();
    await call("/set-entitlement",{tier:"pro",proExpiry:Date.now()+10000,tokenHash:"sub",lifetime:true});
    await call("/set-entitlement",{tier:"free",expectedTokenHash:"sub"});
    expect((await call("/status")).quota.tier).toBe("lifetime");
    await call("/set-entitlement",{tier:"free",lifetime:false});
    expect((await call("/status")).quota.tier).toBe("free");
    db.close();
  });

  it("keeps lifetime when Pro automatically expires on the same day and at midnight", async () => {
    for (const expiry of [10_000, 120_000]) {
      vi.setSystemTime(new Date("2026-10-07T23:59:00Z"));
      const {call,db}=createQuota();
      await call("/set-entitlement",{tier:"pro",proExpiry:Date.now()+expiry,tokenHash:"sub",lifetime:true});
      vi.advanceTimersByTime(expiry);
      const status = (await call("/status")).quota;
      expect(status.tier).toBe("lifetime");
      expect(status.limit).toBe(10);
      db.close();
    }
  });

  it("refunds the bonus counter without refunding a regular answer", async () => {
    const { call, db } = createQuota();
    await call("/reserve", { requestId: "regular" });
    await call("/grant-bonus", {transactionId:"ad-1"});
    await call("/consume-bonus", { requestId: "bonus" });
    await call("/release", { requestId: "bonus" });
    const { quota } = await call("/status");
    expect(quota.used).toBe(1);
    expect(quota.bonusRemaining).toBe(1);
    db.close();
  });

  it("does not decrement twice or refund an unknown request", async () => {
    const { call, db } = createQuota();
    await call("/reserve", { requestId: "a" });
    await call("/reserve", { requestId: "b" });
    await call("/release", { requestId: "a" });
    await call("/release", { requestId: "a" });
    await call("/release", { requestId: "unknown" });
    expect((await call("/status")).quota.used).toBe(1);
    db.close();
  });

  it("refunds the original day when an AI call fails after midnight", async () => {
    const { call, db } = createQuota();
    await call("/reserve", { requestId: "yesterday" });
    vi.setSystemTime(new Date("2026-10-08T00:01:00Z"));
    await call("/reserve", { requestId: "today" });
    await call("/release", { requestId: "yesterday" });
    expect((await call("/status")).quota.used).toBe(1);
    expect(db.prepare("SELECT used FROM quota WHERE day = '2026-10-07'").get()?.used).toBe(0);
    db.close();
  });

  it("enforces paid expiry using the persisted SQL column", async () => {
    const { call, db } = createQuota();
    await call("/set-entitlement", { tier: "pro", proExpiry: Date.now() + 10_000 });
    expect((await call("/status")).quota.limit).toBe(20);
    vi.advanceTimersByTime(10_000);
    expect((await call("/status")).quota.tier).toBe("free");
    db.close();
  });

  it("carries a valid entitlement and bonus state across status reads", async () => {
    const { call, db } = createQuota();
    await call("/set-entitlement", { tier: "pro", proExpiry: Date.now() + 86_400_000 });
    await call("/grant-bonus", {transactionId:"ad-2"});
    await call("/consume-bonus", { requestId: "bonus" });
    expect((await call("/status")).quota.bonusRemaining).toBe(0);
    vi.setSystemTime(new Date("2026-10-08T01:00:00Z"));
    expect((await call("/status")).quota.tier).toBe("pro");
    db.close();
  });

  it("upgrades legacy objects and prunes old request IDs", async () => {
    const { call, db } = createQuota(true);
    db.prepare("INSERT INTO processed_requests (request_id, day) VALUES (?, ?)").run("stale", "2026-10-03");
    db.prepare("INSERT INTO processed_requests (request_id, day) VALUES (?, ?)").run("recent", "2026-10-06");
    await call("/status");
    expect(db.prepare("SELECT request_id FROM processed_requests").all()).toEqual([{ request_id: "recent" }]);
    await call("/reserve", { requestId: "a" });
    expect((await call("/reserve", { requestId: "a" })).duplicate).toBe(true);
    expect((await call("/status")).quota.used).toBe(1);
    db.close();
  });
});
