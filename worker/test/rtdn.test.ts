import { beforeEach, describe, expect, it, vi } from "vitest";
import { handleRtdn } from "../src/routes/billing.js";
import { verifySubscription } from "../src/billing/googlePlay.js";
import { authenticatePubSub } from "../src/security/pubsub.js";
import { sha256 } from "../src/security/crypto.js";
import { envVars } from "./helpers/vars.js";
import type { Env } from "../src/types.js";

vi.mock("../src/billing/googlePlay.js",()=>({verifySubscription:vi.fn(),verifyOneTimeProduct:vi.fn(),getAccessToken:vi.fn()}));
vi.mock("../src/security/pubsub.js",()=>({authenticatePubSub:vi.fn()}));

function setup(type=12) {
    const quota=vi.fn().mockResolvedValue(Response.json({ok:true}));
    const owners=vi.fn().mockImplementation(()=>Response.json({installations:[{installation_id:"owner",kind:"subscription"}]}));
    const env={...envVars,GOOGLE_SERVICE_ACCOUNT_JSON:"configured",
        SECURITY_REGISTRY:{idFromName:()=>"registry",get:()=>({fetch:owners})},
        AI_QUOTA:{idFromName:()=>"owner",get:()=>({fetch:quota})},
    } as unknown as Env;
    const data=btoa(JSON.stringify({packageName:envVars.PLAY_PACKAGE_NAME,subscriptionNotification:{notificationType:type,purchaseToken:"receipt",subscriptionId:envVars.AI_MONTHLY_SKU}}));
    const request=new Request("https://example.com/v1/billing/google/rtdn",{method:"POST",body:JSON.stringify({message:{data}})});
    return {env,request,quota};
}
beforeEach(()=>{vi.clearAllMocks();vi.mocked(authenticatePubSub).mockResolvedValue(true);});
describe("RTDN canonical entitlement updates",()=>{
    it("rejects unauthenticated pushes before purchase lookup",async()=>{
        vi.mocked(authenticatePubSub).mockResolvedValue(false);
        const {request,env,quota}=setup();
        expect((await handleRtdn(request,env)).status).toBe(401);
        expect(verifySubscription).not.toHaveBeenCalled();expect(quota).not.toHaveBeenCalled();
    });
    it("does not revoke canceled subscriptions with remaining paid time",async()=>{
        vi.mocked(verifySubscription).mockResolvedValue({status:"active",expiryTimeMillis:123456});
        const {request,env,quota}=setup(3);
        expect((await handleRtdn(request,env)).status).toBe(200);
        const body=JSON.parse(quota.mock.calls[0][1].body);
        expect(body).toMatchObject({tier:"pro",proExpiry:123456});
        expect(body.expectedTokenHash).toBe(await sha256("receipt"));
    });
    it("downgrades confirmed inactive subscriptions conditionally on the receipt",async()=>{
        vi.mocked(verifySubscription).mockResolvedValue({status:"inactive"});
        const {request,env,quota}=setup();
        expect((await handleRtdn(request,env)).status).toBe(200);
        expect(JSON.parse(quota.mock.calls[0][1].body)).toMatchObject({tier:"free",proExpiry:0});
    });
    it("asks Pub/Sub to retry a temporary Play error without changing entitlement",async()=>{
        vi.mocked(verifySubscription).mockResolvedValue({status:"error",message:"temporary"});
        const {request,env,quota}=setup();
        expect((await handleRtdn(request,env)).status).toBe(503);expect(quota).not.toHaveBeenCalled();
    });
    it("asks Pub/Sub to retry a storage exception",async()=>{
        vi.mocked(verifySubscription).mockResolvedValue({status:"inactive"});
        const {request,env,quota}=setup();quota.mockRejectedValueOnce(new Error("temporary storage failure"));
        expect((await handleRtdn(request,env)).status).toBe(503);
    });
});
