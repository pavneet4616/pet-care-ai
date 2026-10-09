import type { Env } from "../types.js";
import { getAccessToken } from "../billing/googlePlay.js";
import { sha256 } from "./crypto.js";
import { isValidId } from "./validation.js";

export interface IntegrityVerdict {
  requestDetails?: { requestPackageName?: string; requestHash?: string; timestampMillis?: string };
  appIntegrity?: { appRecognitionVerdict?: string; packageName?: string; certificateSha256Digest?: string[] };
  deviceIntegrity?: { deviceRecognitionVerdict?: string[] };
  accountDetails?: { appLicensingVerdict?: string };
}
export function validVerdict(verdict: IntegrityVerdict, hash: string, packageName: string, digests: string[], now=Date.now()): boolean {
  const time = Number(verdict.requestDetails?.timestampMillis);
  return Number.isFinite(time) && time <= now + 30_000 && now-time <= 120_000 &&
    verdict.requestDetails?.requestPackageName === packageName && verdict.requestDetails?.requestHash === hash &&
    verdict.appIntegrity?.packageName === packageName && verdict.appIntegrity?.appRecognitionVerdict === "PLAY_RECOGNIZED" &&
    Boolean(verdict.appIntegrity.certificateSha256Digest?.some(digest => digests.includes(digest))) &&
    Boolean(verdict.deviceIntegrity?.deviceRecognitionVerdict?.includes("MEETS_DEVICE_INTEGRITY")) &&
    verdict.accountDetails?.appLicensingVerdict === "LICENSED";
}
export async function requireIntegrity(request: Request, env: Env): Promise<Response | null> {
  // Only local tests may explicitly disable this. Shipped configuration requires it.
  if (env.INTEGRITY_REQUIRED === "false") return null;
  if (!env.GOOGLE_SERVICE_ACCOUNT_JSON || !env.PLAY_SIGNING_DIGESTS) return Response.json({error:"INTEGRITY_NOT_CONFIGURED"},{status:503});
  const integrityToken=request.headers.get("x-play-integrity");
  const secret=request.headers.get("x-installation-secret");
  if (!integrityToken || integrityToken.length > 16384 || !secret || !/^[A-Za-z0-9_-]{43}$/.test(secret)) return Response.json({error:"INTEGRITY_REQUIRED"},{status:401});
  try {
    const text = await request.clone().text();
    const body=JSON.parse(text) as {installationId?: unknown};
    if (typeof body.installationId !== "string" || !isValidId(body.installationId)) return Response.json({error:"INVALID_INSTALLATION"},{status:400});
    const hash = await sha256(`POST\n${new URL(request.url).pathname}\n${text}\n${secret}`);
    const sa=JSON.parse(env.GOOGLE_SERVICE_ACCOUNT_JSON);
    const access=await getAccessToken(sa,"https://www.googleapis.com/auth/playintegrity");
    const response=await fetch(`https://playintegrity.googleapis.com/v1/${env.PLAY_PACKAGE_NAME}:decodeIntegrityToken`,{
      method:"POST",headers:{authorization:`Bearer ${access}`,"content-type":"application/json"},body:JSON.stringify({integrity_token:integrityToken}),signal:AbortSignal.timeout(15_000),
    });
    if (!response.ok) return Response.json({error:"INTEGRITY_UNAVAILABLE"},{status:503});
    const {tokenPayloadExternal}=await response.json() as {tokenPayloadExternal: IntegrityVerdict};
    if (!tokenPayloadExternal || !validVerdict(tokenPayloadExternal,hash,env.PLAY_PACKAGE_NAME,env.PLAY_SIGNING_DIGESTS.split(",").map(v=>v.trim()))) return Response.json({error:"INTEGRITY_FAILED"},{status:403});
    const stub=env.AI_QUOTA.get(env.AI_QUOTA.idFromName(`install:${body.installationId}`));
    const auth=await stub.fetch("https://quota/authenticate",{method:"POST",body:JSON.stringify({secretHash:await sha256(secret)})});
    if (!auth.ok) return Response.json({error:"INSTALLATION_AUTH_FAILED"},{status:403});
    return null;
  } catch { return Response.json({error:"INTEGRITY_UNAVAILABLE"},{status:503}); }
}

/** Only this exact value enables the legacy path. Any other value keeps enforcement. */
export const LEGACY_INTEGRITY_MODE = "legacy_allowed";

/** versionCode of the first build that presents Play Integrity. Missing or invalid values disable legacy. */
export function legacyMinVersionCode(env: Env): number | null {
  const raw: string | undefined = env.INTEGRITY_MIN_VERSION_CODE;
  return raw && /^[1-9][0-9]{0,8}$/.test(raw) ? Number(raw) : null;
}

/**
 * Pre-Integrity builds send no attestation at all. A request may use the legacy path only when it
 * carries neither the Integrity token nor the installation secret and does not claim a versionCode
 * at or above the minimum. A request that presents either header is always fully verified, so a
 * failed verification can never fall back to legacy.
 */
export function isLegacyRequest(request: Request, env: Env): boolean {
  const mode: string | undefined = env.INTEGRITY_MODE;
  if (env.INTEGRITY_REQUIRED === "false" || mode !== LEGACY_INTEGRITY_MODE) return false;
  const minimum = legacyMinVersionCode(env);
  if (minimum === null) return false;
  if (request.headers.has("x-play-integrity") || request.headers.has("x-installation-secret")) return false;
  const claimed = request.headers.get("x-app-version-code");
  if (claimed === null) return true;
  return /^[0-9]{1,9}$/.test(claimed) && Number(claimed) < minimum;
}

/** Legacy quota buckets are keyed with a prefix that valid installation IDs cannot contain (isValidId rejects ":"). */
export function legacyBucketId(installationId: string): string {
  return `legacy:${installationId}`;
}
