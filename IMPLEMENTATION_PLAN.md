# Pet Care AI - audit implementation status

Updated: 2026-10-08. Scope: fix the supplied code audit before growth and new features.

## Product boundaries

- New purchases offer only `ai_weekly_pass` and `ai_monthly_pass`.
- Historical `premium_unlock` and `remove_ads` purchases remain restorable.
- Existing prices, eligible Play trials and quotas are preserved: free 5/day, legacy lifetime 10/day, Pro 20/day, rewarded bonus 1/day.
- Interstitial ads are removed. Free banners and explicitly requested rewarded ads remain behind UMP consent.
- No annual plan, new subscription IDs, acquisition campaigns or new paid features have been added.
- R9/account cloud sync belongs to the deferred new-feature phase. It is not marked fixed by Android auto-backup or by Firebase setup.

## Status definitions

**Implemented locally** means a patch exists; it does not mean that production has been updated. **Console/device check** means a release cannot be called verified until the listed external check succeeds. This document replaces the outdated first-batch report.

## Audit findings

| ID | Implementation and evidence | Remaining requirement |
|---|---|---|
| S1 | Local Git baseline and private GitHub repository created: https://github.com/pavneet4616/pet-care-ai. Credential files are excluded. | Push the tested final commit and verify remote HEAD. |
| S2 | Upload keystore and signing properties moved outside the workspace; passwords changed to strong random values while preserving the signing certificate. Release signing reads environment/Gradle/external properties. | Confirm the supplied Play certificate against Play Console App Signing and test the uploaded build. |
| S3 | Service-account JSON moved to private storage and excluded from Git. No credential is bundled into the app. | Relocation does not revoke a previously shared key. If it was shared/zipped, rotate the GCP key and replace the Worker secret. Exposure history is unknown. |
| S4 | Bundled SDK and command-line-tools archive moved outside the workspace; generated tooling excluded. | Complete locally. |
| W1 | Standard Play Integrity request hashing and server decode checks; package, Play signature, time, device and licensing policy; per-install secret authentication; 60 POST/IP/minute; 32 KiB bodies; no wildcard CORS; aggregate daily cost guards. Integrity policy and HTTP guards tested. | Stage the new client and Worker together. Production is not updated. Integrity does not guarantee a unique physical device or prevent every legitimate reinstall. |
| W2 | Play obfuscated account hash, server receipt-hash registry, first purchase account match and max three active attested installations. Anonymous known-receipt marker supports restore after deleting old-device bindings. Real SQLite registry tests cover these cases. | Exercise first purchase, reinstall, three-device restore and fourth-device denial with Play testers. Previously unbound legacy receipts use the documented restore path. |
| W3 | Real signed AdMob SSV callbacks grant idempotent bonus credit; timestamp/ad-unit/signature validation; no client honor-system grant. UI waits for verified credit. First status poll can attest an installation before its first chat. | Set the AdMob SSV callback and test a release ad through AdMob. Debug test ads are not proof of signed production SSV. |
| W4 | Historical lifetime token included in server verification; confirmed lifetime stored independently from Pro, survives automatic Pro expiry and is cleared on confirmed refund. SQLite regressions cover same-day and midnight expiry. | Live historical purchase restore needs a tester/owned receipt. Lifetime refunds are rechecked on verification; immediate one-time-product voided-purchase notifications are not implemented. |
| W5 | KV AI switch checked on the server before inference/quota work; absent/unavailable/invalid configuration fails closed. | Verify production KV and deployment. |
| W6 | Central SQLite Durable Object report registry; authenticated paginated admin read and mark-reviewed endpoints; 90-day pruning on new reports; deletion removes installation reports. Registry tests cover review/deletion. | Configure admin secret, deploy, and review reported content weekly. Existing reports in old per-install objects are not automatically backfilled. |
| W7 | Pub/Sub Google RSA JWT verified for issuer, audience, service identity and expiry. Every RTDN rechecks canonical Publisher state; cancellation keeps paid time; revoke/expiry updates only the matching current receipt; transient persistence errors request retry. Signature, route and stale-token regressions. | Create authenticated push subscription, grant publisher/token permissions, select the topic in Play Console and send the test notification. |
| W8 | Reservations retain regular/bonus kind and original day; failure refunds the matching counter exactly once. | Complete locally; SQLite tests include midnight and duplicate release. |
| W9 | Old request IDs pruned after two days. | Complete locally; migration/pruning regression passes. |
| A1 | UMP flow from activity; Mobile Ads initializes only when ads can be requested, in background; privacy choices; revoked consent clears cached ads. | Publish AdMob privacy messages and validate all consent paths on devices. Code alone does not configure the CMP message. |
| A2 | Interstitial implementation, call sites and ad-unit fields removed. | Complete locally. |
| A3 | Implemented notification preferences read at delivery. Unsupported switches hidden. Reminder wording is species-neutral and localized. | Device checks for disabled preferences and permission revocation. |
| A4 | Checkout refreshes Play ownership, blocks pending/same/multiple plans, and sends old token plus Billing 9 product replacement parameters using time proration. Atomic canonical reconciliation preserves historical entitlements and rejects stale queries. Checkout/reconciliation unit tests. | Test weekly-to-monthly and reverse changes, acknowledgement, pending payment, cancel, refund and restore in Play. Older double subscriptions must be managed in Play. |
| R1 | Medication uses exact idle alarms with permission explanation and window fallback; routine tasks/reports use WorkManager. Reschedule on boot/time/timezone/app update/permission grant. | Real Doze, reboot and revoked-exact-permission checks. Fallback timing is approximate. |
| R2 | Mark done/snooze actions, explicit occurrence IDs, idempotent completion and skip already-completed reminders. | Real notification action/delivery checks. |
| R3 | Photos copied to private relative paths, sampled/compressed and stripped of EXIF; backup includes care DB/photo directory; old URI imports best effort. Removed/replaced photos cleaned up. | Restore/reboot test on Android; inaccessible historical URIs cannot be recovered. |
| R4 | Interrupted sending rows older than 90 seconds become failed on session open and can be retried. Pending chat results are invalidated on deletion. | Process-kill device test. |
| R5 | Missing values are unknown, not invented health facts. AI context includes actual medicine, symptoms, vomiting, weight change and age in months. Room v6 stores nullable values and birthdate. Current weekly summaries describe records, not inferred health/personality. | Migration SQL tested against actual exported schemas with foreign keys and all nine dependent tables. Actual Android restore/migration still needs device validation. |
| R6 | One-to-one pet/type-aware occurrence matching accepts early logs, respects adjacent occurrences and explicit done actions; new reminders do not invent pre-creation missed tasks. | Unit tests cover early logs, overlapping reminders, DST, creation date and long streaks. |
| R7 | Bounded activity/water dashboard queries; compact daily aggregates preserve long streaks; midnight refresh updates bounds. | Long-history scrolling/performance measurement on a device. |
| R8 | Clears Room, photos, cache, personal preferences, alarms, notifications and jobs; queues authenticated server deletion with retry/pending UI. Server removes reports and device bindings. Anonymous anti-abuse counters, receipt markers and identity proof intentionally remain and are disclosed. | Test offline deletion/retry and absence of retained personal data after reconnect. Endpoint becomes available only after Worker deployment. |
| R9 | Android backup rules improved; Firebase Android app configured. | Optional-account cloud backup/sync is deferred as the requested new-feature phase. It is not implemented. |
| T1 | UI/background/billing/emergency/export strings extracted; complete target resource sets for de/fr/es/it/pt-BR/nl/ja; placeholder checks; unsupported partial locales archived; all languages packaged for offline switching. | Machine-assisted translations need native-speaker and layout review. Complete resource coverage is not semantic translation certification. |
| T2 | Locale-default kg/lb setting; canonical kg storage; locale numeric input; nullable weight/age; optional birthdate and age in months for young pets. | Unit conversion/parsing and infant-age tests pass. Validate profile editing and rendering on device. |
| T3 | Expense amounts formatted using locale currency and numeric parsing; category choices show translated labels while retaining stable stored IDs. | Review layouts and currencies for supported regions. |
| T4 | App, report and share branding unified as Pet Care AI. | A custom owned domain was not provided. Existing privacy/API hosts remain; domain purchase, DNS binding and legal content review are open. |
| T5 | Offline care versus internet AI wording corrected. | Complete locally. |
| T6 | Localized vet-first first aid, selectable US/UK poison contact numbers, dial intent and nearest-emergency-vet Maps action with browser fallback. Veterinary sources linked. | Human/native-language emergency-copy review and dial/Maps checks on device. |

## Engineering hygiene

- Firebase enabled in the existing project `ai-pdf-chat-497609` (number `138551758971`); Android application `com.petcares.companionlites` registered with the supplied Play SHA-256. Config is present in `app/google-services.json`.
- Analytics and Crashlytics are independently opt-in, disabled by default, with no ad-identifier collection. Analytics accepts only enumerated events/values, not names, pet history, messages or receipts. Consent-aware onboarding/care/AI/permission/paywall/reminder events are wired; console event receipt and Crashlytics delivery are unverified.
- Play review requests follow helpful AI or a seven-day care streak, at most once every 90 days. Play controls whether a dialog appears.
- Notification permission requested after the first reminder with an explanation. Exact-alarm permission remains a separate setting.
- Selected-pet state drives dashboard, tools, AI and emergency navigation. Repeated intents handled while the activity is already open.
- Tests added for scheduling, early completion, DST, streaks, birthdates, parsing/units, checkout/reconciliation and the v5-to-v6 migration.
- Compose updated within the existing API 36/AGP 9 toolchain; Ads/UMP/Integrity/Review/Room/Firebase updated. MPAndroidChart removed; existing stats use a Compose canvas and accessible record text.
- Broad library ProGuard keep rules removed; release R8 validation is required below.
- Weekly notifications combine records for all pets. Individual reports use factual care counts.
- Dashboard and AI chat split into smaller component files.

## Validation record

- Worker: TypeScript check passed; all 98 tests passed across six suites (64 original plus 34 staged-rollout tests); deployment dry-run bundled successfully. No hardened application code deployment occurred.
- Android unit tests: 31 tests passed, covering checkout/reconciliation, scheduling, units and migration.
- Android debug build passed. Lint reports 0 errors, 179 warnings and 2 hints, without a suppression/baseline. Warnings include unused resources, typography suggestions, older dependency checks and obsolete library lint detectors.
- Localization validator: all seven target sets have 619 translatable resource names with complete coverage and matching placeholders.
- Credential scan passed across tracked and eligible untracked files; signing key, passwords and service-account JSON are absent from Git.
- Device test round (2026-10-09, OPPO CPH2065, Android 12): release and debug builds were tested on a phone, including a 1.1.0 to 1.1.1 in-place upgrade (Room 5 to 6, data, chats and reminders preserved). It found 39 issues, recorded in the session issue list and fixed in this build. The most important:
  - R8 full mode removed WorkManager's `OverwritingInputMerger` constructor, so every WorkManager job failed in release: non-medicine reminders, weekly report, AI follow-up and the server data-deletion retry. Fixed by a keep rule in `app/proguard-rules.pro` and verified on device ("Worker result SUCCESS").
  - Onboarding's reminder step hid its buttons behind the bottom bar on 800dp-tall phones, and the bottom bar was shown during onboarding.
  - ColorOS widened exact medicine alarms by up to 1 hour. Medicine reminders now use `setAlarmClock` and a high-importance channel.
  - Photos picked in 1.1.0 became unreadable after the update. The app now imports them where possible and otherwise shows a fallback avatar.
  - Other fixes: brand name was "PetCare" in UI strings and translated in launcher names; about 50 mistranslations; 12/24h time display regression; hard-coded English and prices; the rewarded ad never retried; notifications on Android 8-12 were assumed enabled; PendingIntent collisions; the weekly report hour was overwritten. The UI/UX was refreshed (theme, animations, AI chat, paywall, dashboard).
- Final-source release build (2026-10-09, after the fixes): unit tests (31 passed), lint (0 errors, 270 warnings, 2 hints), R8 release, APK and AAB.
  - `app/build/outputs/bundle/release/app-release.aab`: SHA-256 `93FF0D0A965512730D924214AFFB1A6AD9ECC94BAF86EBE8DE75670CECA0351B`. `jarsigner -verify` reports "jar verified".
  - `app/build/outputs/apk/release/app-release.apk`: SHA-256 `99AE054F2C3C0C33D5BA0ADFC32A803D08273327F5ACE1AD3BD50A68C5FF7174`. apksigner verifies it, and it is 16 KB aligned.
  - versionCode 11 / versionName 1.1.1, upload certificate `E3:64:84:C5:…:FE:EF`. All earlier artifacts are superseded.
  - Gradle cache: drive E: (`E:\GradleHome`) returned I/O device errors (Windows disk event 51). Builds now use `%USERPROFILE%\.gradle`. Check drive E:.

Commands:

```powershell
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleRelease :app:bundleRelease --max-workers=2
```

```powershell
Set-Location worker
npm run typecheck
npm run test
node node_modules/wrangler/bin/wrangler.js deploy --dry-run --outdir .wrangler/audit-build
```

adb 37.0.1 works at `%LOCALAPPDATA%\Android\sdk\platform-tools\adb.exe`, but it is not on PATH. `adb devices` currently shows no connected device. No purchase, consent, alarm timing, backup/restore, launch or live Integrity/SSV claim has been made.

## Release and console checklist

1. Compare Play Console App Signing SHA-256 with `AD:19:25:B7:FE:C7:68:2D:4B:78:E4:1C:61:F2:0E:E8:30:76:6E:95:41:31:08:89:57:68:E3:C6:04:D5:AA:5E`. The local upload certificate is a different identity; it has been preserved.
2. The Cloud project is already linked per the supplied setup. Verify Play Integrity API is enabled for project number `138551758971`, service-account access to token decoding and Play Publisher permissions. Test verdicts from a Play-distributed license-test installation, not a sideloaded debug APK.
3. Keep the existing `GOOGLE_SERVICE_ACCOUNT_JSON` Worker secret. Rotate through GCP first if exposed. A separate random `REPORTS_ADMIN_TOKEN` has been provisioned in Worker secrets and stored outside Git. Use `python tools/review_ai_reports.py` after deployment; mark a manually reviewed ID with `--reviewed ID`.
4. AdMob rewarded unit `3416986503`: callback URL `https://pet-companion-ai.neetpav46.workers.dev/v1/ads/reward`; client supplies the attested installation ID as SSV user ID. Publish UMP messages separately.
5. Configure Pub/Sub authenticated push to `https://pet-companion-ai.neetpav46.workers.dev/v1/billing/google/rtdn`, expected audience exactly that URL, using `pet-companion@ai-pdf-chat-497609.iam.gserviceaccount.com`. Give the Pub/Sub service agent the token-creation permission needed for that service account. Grant `google-play-developer-notifications@system.gserviceaccount.com` publisher access to the RTDN topic, select it in Play Console, and send a test.
6. Validate the Worker in a staging environment with separate storage/quotas and the signed client on a Play test track. The new production defaults require Integrity and signed reward credit; deploying immediately would reject old clients. Follow "Staged Worker rollout" below (deploy with `INTEGRITY_MODE=legacy_allowed` first).
7. Check both purchase directions, trials/intro pricing shown from Play, historical restores, offline/pending payment, expiry/refund, reporting/admin review, deletion retry, consent, process death, exact alarms and photo restore.
8. Check Firebase events/Crashlytics with a consenting test installation; confirm measurement linkage in Firebase. Update Play Data safety and the privacy policy to reflect optional diagnostics, AI/report uploads, deletion retention and Android backup. This is a release prerequisite.
9. Review all translations with native speakers and inspect long strings/large font sizes. Register a domain when an owned domain is available.
10. Push the verified private Git commit. Keep signing/private credentials backed up separately and securely; they are intentionally absent from Git.

## Deferred phase

After reliability/security rollout and device checks: vet-ready new reports, health documents, medication courses, optional account/cloud sync and family sharing. Growth, store experiments and campaigns remain deferred. Competitor pricing, CPI/LTV, market-share and ranking claims in the supplied proposal have not been independently verified and should not drive spend or price changes.

## Primary references

- [Play Integrity setup](https://developer.android.com/google/play/integrity/setup) and [Standard requests](https://developer.android.com/google/play/integrity/standard)
- [Subscription replacement](https://codelabs.developers.google.com/codelabs/play-billing-subs-replacement?hl=en)
- [AdMob signed reward verification](https://developers.google.com/admob/android/ssv) and [UMP](https://developers.google.com/admob/android/privacy)
- [Authenticated Pub/Sub push](https://docs.cloud.google.com/pubsub/docs/authenticate-push-subscriptions)
- [Exact alarm behavior](https://developer.android.com/develop/background-work/services/alarms)
- [Firebase Android setup](https://firebase.google.com/docs/android/setup)
- [AVMA first aid](https://ebusiness.avma.org/files/ProductDownloads/mcm-client-brochures-pet-first-aid-2025.pdf), [ASPCA poison control](https://www.aspca.org/pet-care/aspca-poison-control), [US Pet Poison Helpline](https://www.petpoisonhelpline.com/contact/), [UK Animal PoisonLine](https://www.animalpoisonline.co.uk/)

## Staged Worker rollout

Updated: 2026-10-09. Production still runs the pre-hardening Worker. Nothing in this section has been deployed.

Config lives in `worker/wrangler.jsonc` vars. Change a value there and deploy.
- `INTEGRITY_MODE`: `enforce` by default. Any value other than `legacy_allowed` also enforces. Enforce rejects every request without a valid Play Integrity token and installation secret.
- `INTEGRITY_MIN_VERSION_CODE`: `11`, the first build that sends `x-play-integrity` and `x-installation-secret`. A missing or invalid value disables legacy mode.
- `LEGACY_IP_DAILY_LIMIT` (25) and `LEGACY_GLOBAL_DAILY_LIMIT` (500): daily caps for legacy traffic only. They are separate from the authenticated caps (50 per IP, 2000 global). Set to `0` to stop legacy AI. The values are placeholders to tune from phase 1 logs.

Legacy path (`legacy_allowed`). A request is legacy only if it has neither `x-play-integrity` nor `x-installation-secret`, and it does not claim an `x-app-version-code` at or above the minimum. An absent claim means a pre-Integrity build. Any presented attestation is fully verified and never downgraded. Legacy requests reach `/v1/chat` only: the free allowance of 5 per day in a separate `legacy:` bucket, with no purchase attach, no bonus, and the legacy IP and global caps. `GET /v1/ai/status` is public and unchanged, and builds 10 and below use it. Verify, report, deletion, quota status, admin, reward and RTDN keep their existing auth and reject tokenless calls.

Phase 1: deploy the Worker first, then publish the versionCode 11 client.
1. Set `INTEGRITY_MODE` to `legacy_allowed`. Run `npm run typecheck`, `npm test` and `wrangler deploy --dry-run`.
2. Deploy. Builds 10 and below keep chatting at the free allowance.
3. Watch `legacy_chat` log events, 429 `RATE_LIMITED` (caps too tight, so tune the two `LEGACY_*` vars), and 401 `INTEGRITY_REQUIRED` (should stay near zero from build 11).

Phase 2: switch to enforce.
1. Switch when `legacy_chat` events have stayed under a threshold you choose for seven consecutive days, and the Play Console version split shows versionCode 11 or later at target. No threshold is set in this plan; it is a product decision.
2. Set `INTEGRITY_MODE` to `enforce` and deploy. Builds 10 and below then receive 401 for chat. Announce the cut-over first.

Rollback.
- Stop legacy: set `INTEGRITY_MODE` to `enforce` and deploy (old builds fail closed), or set `LEGACY_GLOBAL_DAILY_LIMIT` to `0` and deploy to keep the mode but stop legacy AI.
- Stop all AI with no deploy: set KV `ai_config` to `{"aiEnabled":false}`.
- Last resort: `wrangler rollback` to the previous Worker version. That restores pre-audit behaviour, so use it only if the mode switch cannot resolve the problem.

Known trade-offs during phase 1.
- Builds 10 and below cannot verify or restore purchases; `/v1/billing/google/verify` returns 401 for them. Paying old-build users fall to the free allowance until they update. Stored entitlements are not used by legacy requests. Decision (2026-10-09): accepted. The app has no paying users yet, so no one loses a paid entitlement. The baseline client also keeps its local purchase flags when verification fails.
- Old-build Report calls return 401. Allowing legacy reports would add an unauthenticated write path, so decide whether Play policy requires it during phase 1.
- Legacy requests are unauthenticated. They can create per-installation Durable Objects, bounded only by the 60 per minute per IP limiter. AI spend is bounded by the legacy caps.
- The build 11 client does not send `x-app-version-code`. Chat is unaffected, because that client sends token and secret together or sends nothing. Add the header (`BuildConfig.VERSION_CODE`) to every request in the next build so the claim check has real data.
- Keep `INTEGRITY_REQUIRED` unset in production. Its `"false"` value is a test-only global bypass that also disables the authenticated cost guard.

Validation for this change: typecheck passed. 98 tests in 6 suites passed (64 existing, plus 34 in `test/integrity-mode.test.ts`). `wrangler deploy --dry-run` bundled successfully into a scratch directory. Nothing was deployed.
