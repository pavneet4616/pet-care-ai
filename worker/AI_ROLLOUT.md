# AI Pet Companion — Rollout Checklist

Backend is live at `https://pet-companion-ai.neetpav46.workers.dev`.
App code (chat UI, quotas, billing, follow-ups) is in the repo. Manual steps below.

## 1. Worker secrets (run in `worker/`)
- `npx wrangler secret put GOOGLE_SERVICE_ACCOUNT_JSON`
  Paste the service-account JSON that has **Android Publisher** access
  (Play Console → Users and permissions → invite the SA email with
  “View app information” + financial/subscription rights).
- Optional kill-switch / tuning without redeploy:
  `npx wrangler kv:key put --namespace-id=7b0f46f3cf6641ce86b1572283022a93 ai_config '{"aiEnabled":true}'`

## 2. Google Play Console
- AI Pro is one subscription family with two base plans:
  - SUBS product **`ai_weekly_pass`** → weekly base plan, **$2.99/week** (+ 3-day trial offer)
  - SUBS product **`ai_monthly_pass`** → monthly base plan, **$9.99/month** (highlighted BEST VALUE)
  Both grant the same 20/day features; the app reads live prices from ProductDetails.
- Existing INAPPs stay: `premium_unlock`, `remove_ads`.
- Monetization → Real-time developer notifications: create Pub/Sub topic,
  point it at `POST https://pet-companion-ai.neetpav46.workers.dev/v1/billing/google/rtdn`.
- Add license testers; test trial purchase end-to-end and check the DO
  entitlement flips to `pro` (server log: verify → set-entitlement).
- Play listing: name the feature **AI Pet Companion** (never “AI Vet”);
  disclose that AI answers are educational and not a diagnosis.

## 3. Quota reality check
- Free 5/day · lifetime-premium 10/day · weekly Pro 20/day · rewarded bonus +1/day.
- Server (Durable Object) is authoritative; midnight UTC reset.
- Cost sanity: ~1.5k in + ~0.4k out tokens per Q ≈ $0.0002–0.0003/Q.

## 4. Staged rollout
1. Internal track → verify chat, quota 429, paywall, trial, rewarded +1, report flow.
2. 10% production → watch funnel: open→Q1→Q2→5/5→paywall→trial→retained.
3. Full rollout. Kill-switch: set `aiEnabled:false` in KV (clients hide/grey entry).

## 5. Privacy & policy (done in code, verify in review)
- Each AI request sends only: question + pet snapshot (7-day summary) + last ~10 messages.
- Chat screen shows the AI-guidance disclaimer; every answer has Helpful/Report.
- Emergency screen is vet-first; AI is offered only as “while preparing”.
