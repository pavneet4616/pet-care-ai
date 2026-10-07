// Google Play purchase verification via the Android Publisher API.
// Uses a service-account JSON stored in the GOOGLE_SERVICE_ACCOUNT_JSON secret.
// No npm deps: JWT RS256 signing is done with WebCrypto.

interface ServiceAccount {
  client_email: string;
  private_key: string;
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const b64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const raw = atob(b64);
  const buf = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) buf[i] = raw.charCodeAt(i);
  return buf.buffer;
}

function base64url(data: ArrayBuffer | string): string {
  const bytes =
    typeof data === "string" ? new TextEncoder().encode(data) : new Uint8Array(data);
  let s = "";
  bytes.forEach((b) => (s += String.fromCharCode(b)));
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function getAccessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = base64url(
    JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/androidpublisher",
      aud: "https://oauth2.googleapis.com/token",
      exp: now + 3600,
      iat: now,
    }),
  );
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claim}`));
  const assertion = `${header}.${claim}.${base64url(sig)}`;
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!res.ok) throw new Error(`oauth token failed: ${res.status}`);
  const json = (await res.json()) as { access_token: string };
  return json.access_token;
}

const ENTITLED_V2_STATES = new Set([
  "SUBSCRIPTION_STATE_ACTIVE",
  "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
  "SUBSCRIPTION_STATE_CANCELED",
]);

export type SubscriptionVerifyResult =
  | { status: "active"; expiryTimeMillis: number; autoRenewing?: boolean; productId?: string }
  | { status: "inactive"; expiryTimeMillis?: number }
  | { status: "error"; message: string };

/** Back-compat shape used by older call sites. */
export interface SubscriptionState {
  active: boolean;
  expiryTimeMillis?: number;
  autoRenewing?: boolean;
}

export function parseSubscriptionV2(
  json: {
    subscriptionState?: string;
    lineItems?: Array<{ productId?: string; expiryTime?: string }>;
  },
  now = Date.now(),
): SubscriptionVerifyResult {
  const productId = json.lineItems?.[0]?.productId;
  const expiryIso = json.lineItems?.[0]?.expiryTime;
  const expiryTimeMillis = expiryIso ? Date.parse(expiryIso) : 0;
  const state = json.subscriptionState ?? "";
  const entitledByState = ENTITLED_V2_STATES.has(state) && expiryTimeMillis > now;
  // Some test records omit subscriptionState but still have a future expiry.
  const entitledByExpiry = !state && expiryTimeMillis > now;
  if (entitledByState || entitledByExpiry) {
    return { status: "active", expiryTimeMillis, productId };
  }
  return { status: "inactive", expiryTimeMillis };
}

export function parseSubscriptionV1(
  json: {
    expiryTimeMillis?: string;
    autoRenewing?: boolean;
    paymentState?: number;
  },
  now = Date.now(),
): SubscriptionVerifyResult {
  const expiry = json.expiryTimeMillis ? parseInt(json.expiryTimeMillis, 10) : 0;
  // 1 = payment received, 2 = free trial. Missing paymentState happens on
  // some Play Billing v2 / license-tester records — trust a future expiry.
  const paid =
    json.paymentState === undefined || json.paymentState === 1 || json.paymentState === 2;
  if (paid && expiry > now) {
    return { status: "active", expiryTimeMillis: expiry, autoRenewing: json.autoRenewing };
  }
  return { status: "inactive", expiryTimeMillis: expiry };
}

function uniqueSkus(ids: Array<string | undefined>): string[] {
  const out: string[] = [];
  for (const id of ids) {
    if (id && !out.includes(id)) out.push(id);
  }
  return out;
}

export async function verifySubscription(
  serviceAccountJson: string,
  packageName: string,
  subscriptionId: string | undefined,
  purchaseToken: string,
  extraSkus: string[] = [],
): Promise<SubscriptionVerifyResult> {
  try {
    const sa = JSON.parse(serviceAccountJson) as ServiceAccount;
    const token = await getAccessToken(sa);
    const auth = { authorization: `Bearer ${token}` };

    // subscriptions v2 is SKU-agnostic (weekly vs monthly) and is the
    // correct API for Play Console base-plan subscriptions.
    const v2Url =
      `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}` +
      `/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;
    const v2 = await fetch(v2Url, { headers: auth });
    if (v2.ok) {
      return parseSubscriptionV2((await v2.json()) as Parameters<typeof parseSubscriptionV2>[0]);
    }
    if (v2.status === 401 || v2.status === 403) {
      console.warn(JSON.stringify({ type: "play_verify", api: "v2", status: v2.status }));
      return { status: "error", message: `play v2 ${v2.status}` };
    }

    const skus = uniqueSkus([subscriptionId, ...extraSkus]);
    let lastStatus = v2.status;
    for (const sku of skus) {
      const v1Url =
        `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}` +
        `/purchases/subscriptions/${sku}/tokens/${encodeURIComponent(purchaseToken)}`;
      const v1 = await fetch(v1Url, { headers: auth });
      lastStatus = v1.status;
      if (v1.ok) {
        return parseSubscriptionV1((await v1.json()) as Parameters<typeof parseSubscriptionV1>[0]);
      }
    }

    console.warn(JSON.stringify({ type: "play_verify", api: "v1", status: lastStatus, skuCount: skus.length }));
    if (lastStatus === 404) return { status: "inactive" };
    return { status: "error", message: `play ${lastStatus}` };
  } catch (err) {
    console.error(JSON.stringify({ type: "play_verify_error", err: String(err) }));
    return { status: "error", message: String(err) };
  }
}

export async function verifyOneTimeProduct(
  serviceAccountJson: string,
  packageName: string,
  productId: string,
  purchaseToken: string,
): Promise<boolean> {
  const sa = JSON.parse(serviceAccountJson) as ServiceAccount;
  const token = await getAccessToken(sa);
  const url =
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}` +
    `/purchases/products/${productId}/tokens/${encodeURIComponent(purchaseToken)}`;
  const res = await fetch(url, { headers: { authorization: `Bearer ${token}` } });
  if (!res.ok) return false;
  const json = (await res.json()) as { purchaseState?: number };
  return json.purchaseState === 0; // 0 = purchased
}
