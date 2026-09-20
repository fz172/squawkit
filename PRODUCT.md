# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

People who maintain something they own and want a dependable record of it: an aircraft owner
logging a condition inspection, a homeowner who has just learned the water heater wants a flush,
someone tracking oil changes on a car, a boat, a bike. The founding audience is aviation and it
stays first-class — every screen must still read as it did to a pilot or mechanic — but one
codebase now serves seven kinds of Thing (airplane, car, motorcycle, bike, boat, home, custom).
The job is the same in every domain: dependable record-keeping and never missing a due date, with
as little friction as possible.

A second audience arrives by invitation: the A&P or IA, the co-owner, the yard, the family member.
They are given access to one Thing rather than to an account, and they log work against the same
record the owner sees. Technicians keep certifications on file, and a sign-off stays attached to
the work it belongs to.

## Product Purpose

SquawkIt is a maintenance logbook for anything worth maintaining. The product is the squawk / task
/ log triad — *something is wrong*, *something is due*, *something was done*. Everything else is
configuration.

Success is that the user never misses a due date, and that years of care add up to a history they
can hand to a mechanic, a buyer, or their future self.

## Positioning

Three things a neighboring maintenance tracker could not truthfully copy:

- **The template is the product.** Each Thing carries a template that supplies its lexicon, spec
  fields, component tree, meters, due rules, starter tasks and capability flags. The app speaks the
  domain's own language — squawks and AOG and tach time on an airplane, attention items and chores
  and no meters at all in a home — rather than offering one generic tracker with a category
  dropdown. Roughly 230 user-facing strings resolve through the lexicon.
- **Local-first, not offline-tolerant.** A SQLDelight entity store is the single source of truth for
  every read and write; sync runs in the background. The app works fully offline because that is the
  only data path, not because a cache was bolted on.
- **Share the Thing, not the account.** An invitation grants access to one Thing. The host stays the
  owner, shared data lives in place under the host's tree as pointers rather than copies, and the
  host's storage entitlement governs — a member is never blocked by their own subscription.

## Operating Context

Entries are made where the work happened and shortly after it happened: a hangar, a ramp, a
driveway, a basement. Connectivity is unreliable in most of those places and the phone is often
being used one-handed with dirty hands, which is why local-first is a product decision rather than
an infrastructure one.

A log entry is written against a component picked from the Thing's tree, carrying the meter reading
and usually a photo of the receipt or the logbook page. Tasks come due by calendar, by meter
(engine hours, odometer, ride distance) or on condition, and notifications are what brings the user
back. A Thing's full history exports as a ZIP holding a PDF, a CSV and a spreadsheet with every
attachment, to be emailed to a mechanic or a buyer.

Aviation users are keeping this record *alongside* the official logbooks their authority requires,
never instead of them.

## Capabilities and Constraints

**Shipped.** Things and the template system; maintenance logs against a template-defined component
tree; scheduled tasks with due-status computation; squawks with an Open → Addressed / Dismissed
lifecycle and a "down" state where the template has one; attachments (files, photos, PDFs, links)
with upload gated by Pro and links always free; logbook export with optional email delivery;
per-Thing sharing with roles; technicians and certifications; comments on squawks and tasks;
notifications on all three platforms; Basic (free, ad-supported) and Pro subscription tiers via
RevenueCat; display ads on the free tier for Android and iOS; guest use with upgrade to a permanent
account, Google / Apple / email-link sign-in, and account deletion; analytics.

**Not started:** Weight and Balance, intelligent search, life limits, forecasting. Web display ads.

**Navigation shape.** There is no fleet list screen. The adaptive shell owns the current Thing and
the switcher, and renders Dashboard → Squawks → Tasks → Logs plus Settings.

**Terminology is load-bearing.** Nouns, field labels, meter names, empty-state copy and status words
come from the Thing's template, never from the code. A string that says "aircraft" or "tail number"
is a bug on six of the seven presets; a per-Thing noun on an account-level surface (login, settings,
subscription) is a bug on all of them. Aviation words survive only for parts that are permanently
airplanes. The test is a screen that reads right for a home and an airplane at once.

**One design language, three shipping targets.** Android, iOS and web are built from one Compose
Multiplatform codebase and all render Material 3 — iOS is deliberately not moved toward HIG. iOS and
web are shipping products, not previews. Material everywhere does not waive the OS guarantees iOS
owes on its own hardware: safe-area insets, Reduce Motion, and the edge-swipe back gesture.

**Gating is exactly three mechanisms**, kept separate: `AppCapability` (build/platform),
`SubscriptionManager` (entitlement), `DeveloperFlags` (developer override). FeatureLab was removed;
a fourth must not be invented.

**Scope resolution.** Per-Thing data resolves its scope through `ThingScopeResolver`, never from the
signed-in uid.

**Not a legal record.** SquawkIt is a personal convenience tool. It does not replace the official
aircraft logbooks required by an aviation authority, or any other record the user is required to
keep. Exports are backups and snapshots, not the legal source of truth. This is stated in the
shipped store listing and must not be contradicted anywhere in the product.

**Published identity is frozen.** The user-facing brand is SquawkIt; the Kotlin package
(`dev.fanfly.wingslog`), the Gradle root project (`wingslog`) and the Firebase project
(`wingslog-9ca4e`) keep the original WingsLog name because renaming would break Play Store, App
Store and Firebase registration. Surviving `aircraft` identifiers in code are grandfathered.

**Open from the pivot.** Every template is still baked into the build; template publishing, the
`fetch_templates` RPC and the canonical template cache are undecided and untracked by any ship date.

## Brand Commitments

- **Name:** SquawkIt. Store name `SquawkIt: Maintenance Logbook`. Domain `squawkit.fanfly.dev`.
- **Voice — Dependable, Precise, Calm.** It must feel trustworthy above all else; users are relying
  on it to track safety-critical dates. It should read like a well-made instrument, not an app
  trying to impress. Modern without being flashy, professional without being cold.
- **Account-level copy stays neutral.** Anything that belongs to the whole account rather than to one
  Thing uses no per-Thing vocabulary.
- **The visual world lives in DESIGN.md** — palette, typography, motion, components, and the
  "Logbook" north star. This file does not restate them, so that there is one authority to change.

## Evidence on Hand

- **Live on both stores.** Google Play in production since 2026-09-05; App Store released 2026-09-09,
  app id `6801955033`. Category Productivity. Ads declared on the free tier; subscription removes them.
- **Store copy**, written and character-checked: `docs/product/store_listing.md`,
  `docs/product/play_store_description.txt`, validated by
  `docs/product/screenshot_generator/check_listing.py`.
- **Store imagery** in `docs/product/store_assets/`, generated from device captures.
- **Web app and landing pages** live at `squawkit.fanfly.dev`, with a page per type (`/aircraft`,
  `/car`, `/boat`, `/bike`, `/home`). Both search consoles verified; sitemap submitted.
- **Brand assets:** app icon at `docs/branding/cloud-console-app-icon-120.{png,svg}`; per-type icons,
  mono and colour, at `docs/branding/thing-icons/`.
- **Product specs:** `docs/product/PRD.md` (overview as built) and
  `docs/product/multi_domain_maintenance_PRD.md` (the pivot), plus per-topic folders under `docs/`.
- **No testimonials, case studies, press, named customers, usage numbers, install counts, review
  quotes or revenue figures exist.** None may be fabricated for any surface, including marketing.

## Product Principles

1. **Dependability first.** Every interaction reinforces trust. The user must never wonder whether
   their data was saved — confirm destructive actions, show clear success, be honest about offline
   state rather than optimistic.
2. **The template speaks.** Vocabulary, fields, meters and status words come from the Thing, not from
   the screen. Generalizing the product means teaching the template, never special-casing a domain.
3. **Safety-critical status wins.** Overdue, due soon, and the template's down state surface above
   everything else in any list or overview, and no user action may bury them.
4. **Minimal friction at the moment of work.** Entry happens right after the job, often one-handed
   and offline. Every field earns its place; multi-step work goes in tabs or wizard flows, never one
   long form.
5. **Share the Thing, not the account.** Access is granted per Thing. The host remains the owner and
   the host's entitlement governs what a member can do.
