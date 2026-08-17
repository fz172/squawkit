# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

## Primary reference

**[AGENTS.md](AGENTS.md) is the source of truth. Read it first.** It is the full engineering
reference — build/test commands, CI/CD, the module tree, the canonical feature module pattern
(`model` / `datamanager` / `sharedassets` / `viewing` / `update`) and its dependency rules,
local-first storage (R1), the sync engine, sharing and scope resolution, the three gating mechanisms,
developer builds, coding conventions, testing, and the design-doc map.

Everything below is a pointer, not a parallel copy. On any conflict, AGENTS.md wins — update it
rather than patching around it here.

## Quick orientation

**SquawkIt** is a Kotlin Multiplatform aviation logbook / fleet-management app for Android, iOS, and
web, built with Compose Multiplatform. Identifiers still use the original **WingsLog** name (package
`dev.fanfly.wingslog`, Gradle module names, Firebase project `wingslog-9ca4e`) — only the brand is
SquawkIt. See [AGENTS.md § What This Is](AGENTS.md#what-this-is).

**Architecture in one line:** MVVM + StateFlow, Koin DI, a local-first SQLDelight `EntityStore` as
the single source of truth, with a background Firestore sync engine (`feature/sync/data`) as the only
entity-path Firestore client. Feature managers never touch Firestore.
See [§ Architecture](AGENTS.md#architecture).

**Where things live:** `app/` (Android entry) · `composeApp/` (Android/iOS host + Koin init) ·
`webApp/` (Kotlin/JS host) · `iosApp/` (Xcode project) · `core/*` · `feature/*` (one per feature;
`feature/shell` holds the shared nav graph both hosts render) · `backend/firebase/` (TypeScript Cloud
Functions and security rules — not a Gradle module). Full tree in
[§ Module Structure](AGENTS.md#module-structure).

## Common commands

```bash
./gradlew assembleDebug                              # Debug APK (developer tooling on)
./gradlew lint
./gradlew testDebugUnitTest                          # All Android unit tests
./gradlew :feature:fleet:datamanager:testDebugUnitTest   # Single module
./gradlew :webApp:jsBrowserDevelopmentWebpack        # Web dev bundle
```

Two things worth knowing before you run anything else: **CI's Kotlin build is manual-dispatch only**,
so run `lint` and `testDebugUnitTest` locally before pushing; and **`assembleRelease` mutates
`version.properties`**. Full command list, iOS/web builds, backend commands, and the workflow table:
[§ Build & Test Commands](AGENTS.md#build--test-commands) and [§ CI / CD](AGENTS.md#ci--cd-githubworkflows).

## Before non-trivial changes

- **UI work** — read `PRODUCT.md`, `DESIGN.md`, and `.impeccable/design.json` first. The aviation
  palette and typography are required; dynamic color is disabled.
  [§ Design System](AGENTS.md#design-system).
- **Feature work** — read the matching PRD / design doc in `docs/` (per-topic subfolders).
  [§ Design Docs](AGENTS.md#design-docs).
- **New feature module** — follow `feature/tasks`, respect the dependency rules, and complete the
  five-step checklist (submodules → `settings.gradle.kts` → `core/di/CommonAppModules.kt` →
  `core/nav` + `feature/shell` routes → strings).
  [§ Canonical Feature Module Pattern](AGENTS.md#canonical-feature-module-pattern).

## Conventions that are easy to get wrong

These are enforced or load-bearing; the reasoning for each is in AGENTS.md.

- `kotlin.time.Instant`, never `kotlinx.datetime.Instant`.
- `get<ClassType>()` in Koin modules, never bare `get()` — a repo hook rejects it.
- No `\'` in Kotlin strings or `strings.xml` — use `’`. Also hook-enforced.
- User-facing strings always come from `strings.xml`; reuse before adding.
- Per-aircraft data resolves its scope through `AircraftScopeResolver`, never from the signed-in uid.
- Three separate gating mechanisms — `AppCapability` (build/platform), `SubscriptionManager`
  (entitlement), `DeveloperFlags` (developer override). Don't invent a fourth; **FeatureLab was
  removed**. [§ Gating](AGENTS.md#gating-three-mechanisms-kept-separate).
- Tests live in `src/test/kotlin`, using JUnit 4 + MockK + Truth + coroutines-test.
  [§ Testing](AGENTS.md#testing).
- Run the post-task cleanup pass over changed `.kt` files before the final commit.
  [§ Engineering Best Practices](AGENTS.md#engineering-best-practices).

## graphify

This project can carry a knowledge graph at `graphify-out/` (git-ignored, so a fresh clone won't have
one).

- For codebase questions, first run `graphify query "<question>"` when `graphify-out/graph.json`
  exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for
  focused concepts — these return a scoped subgraph, usually much smaller than `GRAPH_REPORT.md` or
  raw grep output.
- If `graphify-out/wiki/index.md` exists, use it for broad navigation instead of raw source browsing.
- Read `graphify-out/GRAPH_REPORT.md` only for broad architecture review, or when
  query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
