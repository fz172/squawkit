# Product Requirements Document (PRD): SquawkIt

## 1. Product Overview

SquawkIt is a maintenance logbook for **anything worth maintaining**. It started as an aviation logbook and
fleet-management app for aircraft owners and mechanics, and that audience stays first-class, but since the
2026-09 pivot one codebase serves every kind of **Thing** — airplane, car, motorcycle, bike, boat, home, or
custom — through a template that drives its vocabulary, spec fields, component tree, meters, due rules, and
starter tasks. The product is the squawk / task / log triad: *something is wrong*, *something is due*,
*something was done*. Everything else is configuration.

> **Implementation status (refreshed 2026-09-05).** Verified against `main` and the issue tracker.
>
> | Area                                                                       | State                                                                                               |
> |----------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
> | Local-first storage (R1) + Firestore sync                                  | **Shipped** — the only data path                                                                    |
> | Thing management, logs, tasks, due status                                  | **Shipped**                                                                                         |
> | Squawks                                                                    | **Shipped**                                                                                         |
> | Attachments (R2), incl. across shared Things                               | **Shipped** — upload gated by Pro, links free                                                       |
> | Logbook export + email delivery                                            | **Shipped** — export now renders from the template                                                  |
> | Sharing (co-owners, technicians, roles, invites)                           | **Shipped** 2026-07                                                                                 |
> | Technicians + certifications                                               | **Shipped** — certifications carry the domain (2026-09-04)                                          |
> | Subscription (Basic / Pro, RevenueCat)                                     | **Shipped**, GA 2026-08-16                                                                          |
> | Display ads (free tier, Android + iOS)                                     | **Shipped**, GA 2026-08-17 — web ads not started                                                    |
> | Notifications (urgency + collaboration, all platforms)                     | **Shipped** 2026-08-19 → 08-26                                                                      |
> | Comments on squawks and tasks                                              | **Shipped** 2026-09-04                                                                              |
> | Account upgrade, Apple / Google / email-link sign-in, account deletion     | **Shipped**                                                                                         |
> | Analytics (Firebase → GA4, typed taxonomy)                                 | **Shipped** 2026-08-30                                                                              |
> | Web app (adaptive shell, landing + support pages)                          | **Shipped** at squawkit.fanfly.dev                                                                  |
> | Multi-domain pivot (Thing migration, lexicon, presets, template rendering) | **Shipped** 2026-08-28 → 09-04; template distribution still baked-in                                |
> | Store publishing                                                           | **Android live** on Google Play (full production rollout, 2026-09-05); App Store review submission open |
> | Weight & Balance, intelligent search, life limits, forecasting             | **Not started**                                                                                     |
>
> Gating is one of exactly three mechanisms — `AppCapability` (build/platform), `SubscriptionManager`
> (entitlement), `DeveloperFlags` (developer override). FeatureLab and its lab flags no longer exist.

> **The pivot.** [`multi_domain_maintenance_PRD.md`](multi_domain_maintenance_PRD.md) is the product spec for the
> generalization; Phases 1, 2, 3 and 5 have shipped and Phase 4 is proposed. Its companions
> [`thing_migration_design.md`](thing_migration_design.md), [`template_system_design.md`](template_system_design.md) and
> [`pivot_rollout_design.md`](pivot_rollout_design.md) record how each phase landed. This document is the overview of
> the product as built; where the two disagree on Thing-level detail, the multi-domain PRD wins.

## 2. Scope

- **Stuff management**: create, update, and delete Things of any supported kind; a switcher between them.
- **Maintenance records**: logs against a template-defined component tree with template-defined meters.
- **Compliance tracking**: scheduled tasks with due-status computation surfaced on the dashboard.
- **Squawks**: ad-hoc defect tracking with an Open → Addressed / Dismissed lifecycle and a "down" (AOG) state
  where the template has one.
- **Attachments**: files, photos, PDFs and links on logs, tasks, and squawks.
- **Logbook export**: on-device PDF / CSV / XLSX ZIP with optional email delivery.
- **Sharing**: multiple accounts on one Thing with roles; technicians linked across shared Things.
- **Comments**: threads on squawks and tasks visible to everyone with access.
- **Notifications**: urgency escalation and collaboration alerts on Android, iOS and web.
- **Subscription and ads**: Basic (free, ad-supported) and Pro tiers.
- **Accounts**: guest use, upgrade to a permanent account, Google / Apple / email-link sign-in, deletion.
- **Tools** (future): Weight & Balance, intelligent search, life limits, forecasting.

## 3. Features & Requirements

### 3.1 Things and the template system (Shipped — `feature/thing`, `core/template`)

**Goal**: One product for every kind of maintained Thing, with the aviation experience unchanged.

- **Presets** (six `.textproto` assets in `core/template/templates/`, covering seven kinds): airplane,
  automotive (car and motorcycle), bike, boat, home, custom. Each carries a lexicon (what a squawk, a
  down state, an identifier and a meter are called), spec fields, component slots, meter definitions,
  starter tasks, certification definitions, and capability flags.
- **Create flow**: type picker → template-driven create form (spec fields from the template) → optional
  starter task pack. A template that cannot be rendered is refused before inflation; a Thing whose stored
  template is uninterpretable renders in a degraded state instead of crashing.
- **Rendering from the template**: spec, component tree, meter labels, task rules, notification copy, and
  the export all read the Thing's template. ~230 user-facing strings resolve through the lexicon; a
  byte-identical snapshot test guards the aviation cohort's copy.
- **Stuff switcher**: there is no fleet list screen. The adaptive shell owns the current Thing
  (`SelectedAircraftStore`) and the switcher, and renders the sections **Dashboard → Squawks → Tasks → Logs**
  plus Settings.
- **Data model**: the `Thing` proto lives at `/users/{uid}/thing/...` with the ACL at
  `thing_shares/{hostUid}/thing/{id}`; the 2026-08-28/29 migration moved every account. Retired trees are
  deleted after their grace windows.
- **Still open from the pivot** (every template is baked into the build): the publishing script (#725),
  the `fetch_templates` RPC (#726), and the canonical template cache (#727).
- **Codebase naming**: Kotlin package, Gradle modules and the Firebase project keep the original WingsLog
  identifiers; surviving `aircraft` names in code are grandfathered (#638).

### 3.2 Maintenance Log Management (Shipped — `feature/logs`)

**Goal**: Comprehensive digital record-keeping for maintenance actions.

- **Log entry structure**: a component picked from the Thing's component tree (auto-filling its serial),
  meter readings from the template's meter set, a description of work, and optional inspection types.
- **CRUD**: list (newest first, with search and component filter), create, update, delete with confirmation.
- **Linkage**: a log can address one or more squawks, picked from either side. A log opened from a task's
  "Create Work" action records that task; compliance itself is derived by `TaskDueManager`, not stored.
- **Attachments** (Shipped — R2): local-first blob store with background upload (WorkManager / URLSession)
  and lazy download on other devices; on web, blobs stream through the `streamBlob` function. File and photo
  upload are gated by `SubscriptionManager.canUploadAttachments()`; links are always free. On a shared Thing
  the host's entitlement governs, enforced by the Cloud Function blob broker. Deleting a record cascades to
  its blobs; a scheduled sweep collects orphans.

### 3.3 Dashboard & Compliance (Shipped — `feature/thing/dashboard`, `feature/tasks`)

**Goal**: An at-a-glance view of the Thing's state and everything that is due.

- **Dashboard section**: hero header, configuration card, a down-state alert (open AOG-priority squawks,
  with each template's own sentence), overdue and due-soon tasks, and log stats / onboarding.
- **Tasks section**: active and complied tasks with a due / history toggle, due-status chips, add task.
- **Compliance types**: `ROUTINE_INSPECTION`, `SERVICE_BULLETIN`, `AIRWORTHINESS_DIRECTIVE`, with reference
  number, authority, details, and a one-time flag.
- **Scheduling rules**: `TimeRule`, `MeterRule` (any template meter; replaced the aviation-only
  `EngineHourRule`), `OnConditionRule`, `LinkedRule`, `ImmediateRule`. `TaskDueManager` computes `DueStatus`
  from the last complying log.
- **Open polish**: the due preview in the task form (#424).

### 3.4 Squawks (Shipped — `feature/squawk`)

**Goal**: Track defects that arise outside the scheduled workflow.

- Title, priority (`Low` / `Medium` / `High` / `AOG`, named by the lexicon), description, component,
  attachments, comments.
- Lifecycle: **Open → Addressed** (linked log) or **Open → Dismissed** (Obsolete / Not Reproducible /
  Duplicate / Intended Behavior), with **Reopen**.
- AOG-priority squawks raise the dashboard's down-state alert. A fleet-level AOG badge was deliberately
  left out of scope.
- See `docs/squawks/user_squawking_prd.md` and `docs/squawks/squawk_design.md`.

### 3.5 Comments (Shipped 2026-09-04 — `feature/comments`)

- A Comments tab on Update Squawk and Update Task. Anyone with access to the record, host or share member,
  can leave notes; threads sync through the same local-first engine.
- See `docs/comments/comments_design.md`.

### 3.6 Logbook Export (Shipped — `feature/export`)

**Goal**: A portable copy of all logbook data for handoff, backup, and pre-buy.

- Entry point: **Settings → Export logs**. One Thing or all of them, optional date range, options.
- Output: a ZIP with a PDF, CSV files, an XLSX workbook, attachments, and a README, saved to Files (iOS) /
  Downloads (Android). The layout renders from the template rather than as a paper aviation logbook (#770).
- **Email delivery** (Pro): `requestExportDelivery` uploads the bundle, writes a manifest, and mails a signed
  link. Export history supports re-send, retry, and delete. Export-to-device stays free.
- **Open**: background export with progress (#343), partial-success surfacing for failed attachments (#342).
- See `docs/export/`.

### 3.7 Sharing & Technicians (Shipped 2026-07 — `feature/sharing`, `feature/technician`)

**Goal**: Co-owners and the mechanics who work on a Thing manage one shared logbook.

- Shared data lives in place under the host's tree; members hold a ref, not a copy. Roles are enforced by
  Firestore rules, and invites are links that need no email identity (`createThingShareInvite`,
  `previewThingShareInvite`, `redeemThingShareInvite`, `revokeThingShare`, `updateThingShareRole`).
- Sync fans out into foreign scopes; revoke purges the member's cache only. Every per-Thing manager resolves
  its scope through `ThingScopeResolver`, never from the signed-in uid.
- Hosting a share is gated by Pro (`canHostShare()`); accepting an invite never is.
- **Technicians**: managed profiles with a mirror across shared Things, a picker on logs, merge of
  duplicates, and an authorship indicator. Since 2026-09-04 a technician's certifications carry the domain
  and their role is derived from them.
- See `docs/sharing/` and `docs/technician/`.

### 3.8 Notifications (Shipped 2026-08-19 → 08-26 — `feature/notifications`)

**Goal**: Never miss a due date, and know when a collaborator changes something.

- **Urgency escalation (N2)**: a task crossing into due-soon or overdue, or an open squawk's priority
  raised to High / AOG, notifies on Android and iOS. Tier titles and bodies come from the lexicon; the AOG
  channel name is the template's down-state word.
- **Collaboration (N1)**: a backend fan-out (`onNotifiableThingRecordWritten`, `onNotifiableThingWritten`)
  notifies other members of a shared Thing on every write, with no coalescing. Delivered on Android, iOS,
  web push, and as sync-driven in-tab alerts on the web.
- Settings screen for per-category control; permission flows per platform; developer-options plumbing.
- See `docs/notifications/`.

### 3.9 Subscription (Shipped, GA 2026-08-16 — `feature/subscription`)

- Tiers are **Basic** (free) and **Pro**. Free accounts hold up to two Things (`FREE_THING_LIMIT = 2`).
- Pro unlocks file and photo attachments, email export delivery, hosting shares, and an ad-free experience.
- Billing through RevenueCat on Android and iOS; web has no purchase path and links to platform management.
  The entitlement is server-authoritative, written only by Cloud Functions (`revenueCatWebhook`,
  `reconcileMyEntitlement`, `scheduledEntitlementReconcile`, `grantPromoEntitlement`) and mirrored
  read-only into the local store. Anonymous users cannot subscribe.
- **Open**: letting promo codes grant Pro to end users (#750); per-tier storage quota enforcement is deferred.
- See `docs/subscription/`.

### 3.10 Display Ads (Shipped, GA 2026-08-17 — `feature/ads`)

- In-list AdMob banners on the free tier, Android and iOS, with a per-session cap and a UMP consent step in
  onboarding. `shouldShowAds()` is false wherever `isAdsSupported` is off, so a build that cannot sell Pro
  never shows ads; the web build is ad-free.
- **Not started**: phase 2 web ads on Google Ad Manager (#387, #388).
- See `docs/ads/`.

### 3.11 Accounts & Sign-in (Shipped — `core/auth`, `feature/login`)

- Guest (anonymous) use with full local-first data; **guest → account upgrade** preserves the UID on Google
  and Apple (including iOS Sign in with Apple), with clean and merge paths.
- Sign-in with Google, Apple, and **passwordless email link** on Android, iOS, and web.
- **Account deletion** via `deleteMyAccount`.
- **Open**: launch-time reconciliation if the app dies mid-upgrade (orphan rows are preserved but not yet
  recovered); one unreproduced iOS upgrade failure (#422).
- See `docs/account/`.

### 3.12 Web (Shipped — `webApp/`)

- Kotlin/JS host at `https://squawkit.fanfly.dev` with the adaptive shell, web attachments, Apple and Google
  sign-in, a landing page with store badges, and static privacy, support, and account-deletion-request pages.
- See `docs/web/`.

### 3.13 Analytics (Shipped 2026-08-30 — `core/analytics`)

- Firebase Analytics → GA4 on all platforms: automatic screen tracking, a typed event taxonomy (no untyped
  `logEvent` call sites), `template_id` on every Thing-scoped event, and starter-pack events. Delivery is
  verified in GA4. (The design doc's own status banner in `docs/analytics/` predates this and still reads
  "proposed".)

### 3.14 Store Publishing (Android live; iOS in progress)

- Google Play: **fully launched to production on 2026-09-05** (#437). Internal testing, listing content, content
  rating, data safety, and the public listing all preceded it; the web landing page links to the listing.
- App Store Connect: app record, subscription products, and TestFlight are done; listing, privacy label,
  and App Review submission are tracked in #440.
- Store assets (phone, tablet, iPhone 6.5", iPad) render from `docs/product/screenshot_generator/`.

### 3.15 Future

- **Weight & Balance** calculator: stations, weights, CG, envelope plot. No code exists.
- **Search and filter** (per-tab bars, time and component filters, typo- and acronym-tolerant matching): proposed, `docs/search/search_filter_PRD.md` and `search_filter_design.md`. The earlier FTS5 + embeddings research in `docs/search/intelligentsearch.md` stays as the escalation path.
- **Component life limits** (TSN / TSO / TBO, #50) and **utilization-based forecasting** (#53).
- **Multi-domain Phase 4** as proposed in the multi-domain PRD.

## 4. Technical Requirements

- **Local storage**: SQLDelight `EntityStore` — the single source of truth for every read and write.
- **Cloud sync**: Firestore push/pull engine (`feature/sync/data`), the only entity-path Firestore client,
  with foreign-scope fan-out for shared Things. Anonymous users work fully offline.
- **Media storage**: Firebase Storage for blobs under user-scoped paths; a Cloud Function broker for
  foreign-hosted Things; scheduled orphan sweep.
- **Backend**: Firebase Functions v2 (TypeScript, Node 22) in `backend/firebase/functions/` — export
  delivery, sharing, storage broker and GC, subscription entitlement, notification fan-out, account deletion,
  migration and audit scripts, plus Firestore / Storage rules with emulator-backed tests.
- **Authentication**: Firebase Auth — Google, Apple, email link, anonymous; App Check on every callable.
- **Templates**: `ThingTemplate` protos baked in as `.textproto` assets; `TemplateRegistry` and
  `CurrentThingTemplate` resolve them per Thing.
- **Platforms**: Android (minSdk 33), iOS, and web from one Compose Multiplatform codebase; the same
  `AppCapability` decides per host what is supported.
- **UI/UX**: Material 3 with the instrument palette shared by every domain, dynamic color disabled
  (`PRODUCT.md`, `DESIGN.md`). Every Thing speaks its own vocabulary through the lexicon; account-level copy
  stays neutral.

## 5. Roadmap

1. ✅ **Phase 1–4** — aircraft editor, `MaintenanceLog` schema, log UI with the component picker, compliance
   logic (`feature/tasks`).
2. ✅ **Phase 5** — local-first storage (R1), the only data path. `docs/storage/storage_r1_design.md`.
3. ✅ **Phase 6** — Squawks. `docs/squawks/squawk_design.md`.
4. ✅ **Phase 7** — Attachments (R2), later extended across shared Things. `docs/storage/storage_r2_design.md`.
5. ✅ **Phase 8** — Logbook export with email delivery. `docs/export/`.
6. ✅ **Phase 9** — Sharing and technicians (2026-07), deletion cascade and blob GC. `docs/sharing/`,
   `docs/storage/deletion_gc_design.html`.
7. ✅ **Phase 10** — Subscription (GA 2026-08-16) and display ads (GA 2026-08-17). `docs/subscription/`,
   `docs/ads/`.
8. ✅ **Phase 11** — Notifications (2026-08-19 → 08-26). `docs/notifications/`.
9. ✅ **Phase 12** — Multi-domain pivot: Thing migration (08-28/29), lexicon plumbing and analytics taxonomy
   (08-29/30), presets, template-driven rendering, starter packs, certifications, create flow, and copy
   (08-31 → 09-04); comments (09-04). Remaining: template publishing / fetch / cache (#725–#727).
10. 🔄 **Phase 13** — Store launch: Google Play production rollout ✅ (2026-09-05, #437); App Store review
    submission (#440) still open.
11. 📋 **Later** — web ads (#387), promo-code Pro (#750), export background task and partial-success
    (#342, #343), multi-domain Phase 4, Weight & Balance, intelligent search, life limits, forecasting.
