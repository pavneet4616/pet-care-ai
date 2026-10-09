import { DatabaseSync } from "node:sqlite";
import { generateKeyPairSync, sign } from "node:crypto";
import { afterEach, describe, expect, it, vi } from "vitest";
import { validVerdict } from "../src/security/integrity.js";
import { authenticatePubSub } from "../src/security/pubsub.js";
import { handleReward } from "../src/routes/rewards.js";
import { RegistryDO } from "../src/security/RegistryDO.js";
import { accountHash } from "../src/security/crypto.js";
import { envVars } from "./helpers/vars.js";
import type { Env } from "../src/types.js";

const installation="123e4567-e89b-12d3-a456-426614174000";
function env(patch: Partial<Env>={}):Env {
  const unexpected = new Proxy({}, {get(_t,key) { throw new Error(`Unexpected ${String(key)}`); }});
  return {...envVars,CONFIG:unexpected,AI:unexpected,API_RATE_LIMITER:unexpected,AI_QUOTA:unexpected,SECURITY_REGISTRY:unexpected,...patch} as Env;
}
afterEach(()=>vi.unstubAllGlobals());

describe("Play Integrity verdict policy",()=>{
  const packageName=envVars.PLAY_PACKAGE_NAME;
  const verdict=()=>({
    requestDetails:{requestPackageName:packageName,requestHash:"hash",timestampMillis:String(Date.now())},
    appIntegrity:{packageName,appRecognitionVerdict:"PLAY_RECOGNIZED",certificateSha256Digest:["digest"]},
    deviceIntegrity:{deviceRecognitionVerdict:["MEETS_DEVICE_INTEGRITY"]},accountDetails:{appLicensingVerdict:"LICENSED"},
  });
  it("accepts a fresh licensed app and certified device",()=>expect(validVerdict(verdict(),"hash",packageName,["digest"])).toBe(true));
  it("rejects wrong body, package, certificate, license, device and old tokens",()=>{
    expect(validVerdict(verdict(),"different",packageName,["digest"])).toBe(false);
    expect(validVerdict(verdict(),"hash","other",["digest"])).toBe(false);
    expect(validVerdict(verdict(),"hash",packageName,["other"])).toBe(false);
    expect(validVerdict({...verdict(),accountDetails:{appLicensingVerdict:"UNLICENSED"}},"hash",packageName,["digest"])).toBe(false);
    expect(validVerdict({...verdict(),deviceIntegrity:{deviceRecognitionVerdict:[]}},"hash",packageName,["digest"])).toBe(false);
    const old=verdict();old.requestDetails.timestampMillis=String(Date.now()-120_001);
    expect(validVerdict(old,"hash",packageName,["digest"])).toBe(false);
  });
});

describe("Pub/Sub JWT verification",()=>{
  const {privateKey,publicKey}=generateKeyPairSync("rsa",{modulusLength:2048});
  function request(patch:Record<string,unknown>={},tamper=false) {
    const b64=(value:object)=>Buffer.from(JSON.stringify(value)).toString("base64url");
    const header=b64({alg:"RS256",kid:"google-key"});
    const payload=b64({iss:"https://accounts.google.com",aud:envVars.RTDN_AUDIENCE,email:envVars.RTDN_SERVICE_ACCOUNT_EMAIL,email_verified:true,iat:Math.floor(Date.now()/1000),exp:Math.floor(Date.now()/1000)+3600,...patch});
    const signature=sign("sha256",Buffer.from(`${header}.${payload}${tamper ? "wrong" : ""}`),privateKey).toString("base64url");
    return new Request("https://example.com",{headers:{authorization:`Bearer ${header}.${payload}.${signature}`}});
  }
  function mockKeys() {
    vi.stubGlobal("fetch",vi.fn().mockResolvedValue(Response.json({keys:[{...publicKey.export({format:"jwk"}),kid:"google-key",alg:"RS256"}]})));
  }
  it("verifies a real RSA signature and expected service identity",async()=>{mockKeys();expect(await authenticatePubSub(request(),env())).toBe(true);});
  it.each([{aud:"other"},{email:"attacker@example.com"},{email_verified:false},{exp:1},{iss:"attacker"},{iat:Math.floor(Date.now()/1000)+1000}])("rejects invalid claims %j",async patch=>{
    mockKeys();expect(await authenticatePubSub(request(patch),env())).toBe(false);
  });
  it("rejects a forged signature even with valid claims",async()=>{mockKeys();expect(await authenticatePubSub(request({},true),env())).toBe(false);});
  it("rejects unauthenticated and unconfigured push",async()=>{
    expect(await authenticatePubSub(new Request("https://example.com"),env())).toBe(false);
    expect(await authenticatePubSub(request(),env({RTDN_AUDIENCE:undefined}))).toBe(false);
  });
});

describe("Google rewarded SSV",()=>{
  const {privateKey,publicKey}=generateKeyPairSync("ec",{namedCurve:"prime256v1"});
  function callback(patch:Record<string,string|undefined>={},tamper=false) {
    const safePatch=Object.fromEntries(Object.entries(patch).filter((entry):entry is [string,string]=>entry[1] !== undefined));
    const query=new URLSearchParams({ad_network:"google",ad_unit:envVars.ADMOB_REWARDED_UNIT,reward_amount:"1",reward_item:"question",timestamp:String(Date.now()),transaction_id:"transaction-123",user_id:installation,...safePatch}).toString();
    const signature=sign("sha256",Buffer.from(query),privateKey).toString("base64url");
    return new Request(`https://example.com/v1/ads/reward?${query}${tamper ? "&extra=forged" : ""}&signature=${signature}&key_id=42`);
  }
  function setup() {
    const grant=vi.fn().mockResolvedValue(Response.json({ok:true}));
    vi.stubGlobal("fetch",vi.fn().mockResolvedValue(Response.json({keys:[{keyId:42,pem:publicKey.export({type:"spki",format:"pem"})}]})));
    const quota={idFromName:()=>installation,get:()=>({fetch:grant})} as unknown as Env["AI_QUOTA"];
    return {grant,bindings:env({AI_QUOTA:quota})};
  }
  it("grants only after verifying an actual ECDSA signature",async()=>{
    const {bindings,grant}=setup();expect((await handleReward(callback(),bindings)).status).toBe(200);
    expect(grant).toHaveBeenCalledWith("https://quota/grant-bonus",expect.objectContaining({body:JSON.stringify({transactionId:"transaction-123"})}));
  });
  it("rejects changed signed parameters",async()=>{
    const {bindings,grant}=setup();expect((await handleReward(callback({},true),bindings)).status).toBe(403);expect(grant).not.toHaveBeenCalled();
  });
  it.each([{ad_unit:"other"},{timestamp:"1"},{user_id:"bad-id"},{reward_amount:"0"}])("rejects invalid event %j",async patch=>{
    const {bindings,grant}=setup();expect((await handleReward(callback(patch),bindings)).status).toBe(400);expect(grant).not.toHaveBeenCalled();
  });
});

describe("Strongly consistent token and report registry",()=>{
  function setup() {
    const db=new DatabaseSync(":memory:");
    const sql={exec(query:string,...bindings:SqlStorageValue[]) {
      const values=bindings.map(v=>v instanceof ArrayBuffer ? new Uint8Array(v) : v);
      const rows=/^\s*SELECT/i.test(query) ? db.prepare(query).all(...values) : [];
      if (!/^\s*SELECT/i.test(query)) { if(bindings.length) db.prepare(query).run(...values);else db.exec(query); }
      return {toArray:()=>rows};
    }} as SqlStorage;
    const registry=new RegistryDO({storage:{sql}} as DurableObjectState);
    const call=(path:string,body?:object)=>registry.fetch(new Request(`https://registry${path}`,{method:body ? "POST" : "GET",...(body ? {body:JSON.stringify(body)} : {})}));
    return {db,call};
  }
  it("limits a shared receipt to three installations but permits repeated restore",async()=>{
    const {call,db}=setup();
    for(const installationId of ["one","two","three"]) expect((await call("/bind-token",{tokenHash:"receipt",installationId,kind:"subscription"})).status).toBe(200);
    expect((await call("/bind-token",{tokenHash:"receipt",installationId:"four",kind:"subscription"})).status).toBe(409);
    expect((await call("/bind-token",{tokenHash:"receipt",installationId:"one",kind:"subscription"})).status).toBe(200);db.close();
  });
  it("requires the Play obfuscated account on the first binding",async()=>{
    const {call,db}=setup();
    expect((await call("/bind-token",{tokenHash:"receipt",installationId:installation,kind:"subscription",accountId:"wrong"})).status).toBe(403);
    expect((await call("/bind-token",{tokenHash:"receipt",installationId:installation,kind:"subscription",accountId:await accountHash(installation)})).status).toBe(200);db.close();
  });
  it("restores a previously verified receipt after deleting its old device binding",async()=>{
    const {call,db}=setup();
    const accountId=await accountHash(installation);
    await call("/bind-token",{tokenHash:"receipt",installationId:installation,kind:"subscription",accountId});
    await call("/delete",{installationId:installation});
    expect(db.prepare("SELECT COUNT(*) AS count FROM owners").get()?.count).toBe(0);
    expect((await call("/bind-token",{tokenHash:"receipt",installationId:"replacement-phone",kind:"subscription",accountId})).status).toBe(200);
    db.close();
  });

  it("stores reports centrally, marks review, and deletes installation content",async()=>{
    const {call,db}=setup();
    await call("/report",{id:"report",installationId:installation,category:"unsafe",detail:"unsafe answer",excerpt:"short excerpt"});
    const result=await (await call("/reports")).json() as {reports:Array<{id:string;reviewed_at:number|null;installation_id?:string}>};
    expect(result.reports).toHaveLength(1);expect(result.reports[0].installation_id).toBeUndefined();
    await call("/review",{id:"report"});expect(db.prepare("SELECT reviewed_at FROM reports").get()?.reviewed_at).toBeGreaterThan(0);
    await call("/delete",{installationId:installation});expect((await (await call("/reports")).json() as {reports:unknown[]}).reports).toHaveLength(0);db.close();
  });
  it("bounds daily aggregate spend independently of installation ID",async()=>{
    const {call,db}=setup();
    expect(await (await call("/cost-guard",{key:"ip",limit:1})).json()).toEqual({allowed:true});
    expect(await (await call("/cost-guard",{key:"ip",limit:1})).json()).toEqual({allowed:false});db.close();
  });
});
