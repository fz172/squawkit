# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Primary reference

The canonical guidance for this repo lives in [AGENTS.md](AGENTS.md). Read it first — it covers build/test commands, module layout, the canonical feature module pattern (`model` / `datamanager` / `sharedassets` / `viewing` / `update`), the local-first storage architecture (R1), the sync engine, dogfood build wiring, and coding conventions. Everything below is a quick-orientation supplement; AGENTS.md is authoritative on conflicts.

## Quick orientation

**SquawkIt** is a Kotlin Multiplatform aviation logbook / fleet management app targeting Android, iOS, and web with Compose Multiplatform. The codebase still uses the original **WingsLog** name for identifiers (package `dev.fanfly.wingslog`, Gradle module names); only the user-facing brand is SquawkIt.

**Architecture in one line:** MVVM + StateFlow, Koin DI, local-first `EntityStore` (SQLDelight) as the single source of truth, with a background Firestore sync engine (`feature/sync`) as the *only* Firestore client. Feature managers never touch Firestore directly.

**Module tree:** `app/` (Android entry), `composeApp/` (Android/iOS host + Koin init), `webApp/` (Kotlin/JS host), `iosApp/` (Xcode project), `core/*` (model, nav, sharedassets, analytics, di, ui[+theme/adaptive/widget], auth, firebase, storage, datetime, appinfo), `feature/*` (one module per feature — see AGENTS.md for the canonical submodule pattern; `feature/shell` holds the shared nav graph both hosts render), `backend/firebase/functions/` (TypeScript Cloud Functions — not a Gradle module).

## Common commands

```bash
./gradlew assembleDebug                              # Build debug APK (developer tooling on)
./gradlew assembleRelease                            # Build release APK (developer tooling off)
./gradlew assembleRelease -PdeveloperBuild=true       # "Dogfood-style" release APK (developer tooling on)
./gradlew lint
./gradlew testDebugUnitTest                          # All Android unit tests
./gradlew :feature:fleet:viewing:testDebugUnitTest   # Single-module tests
./gradlew :composeApp:iosSimulatorArm64Test          # iOS simulator unit tests (local only — not on CI)
./gradlew :webApp:jsBrowserDevelopmentWebpack        # Web dev bundle
```

CI runs lint → `assembleDebug` → `testDebugUnitTest` and requires the `GOOGLE_SERVICES_JSON` secret. iOS is not built on CI; use the **iosAppDogfood** scheme in Xcode for the iOS dogfood build.

## Before non-trivial changes

- **UI work** — read `PRODUCT.md`, `DESIGN.md`, and `.impeccable/design.json` first. The aviation palette, typography, and brand principles are required (no dynamic color).
- **Feature work** — check `docs/` for the relevant PRD / design doc. Docs are grouped into per-topic subfolders (e.g. `docs/storage/storage_r1_design.md`, `docs/storage/storage_r2_design.md`, `docs/attachments/attachments_design.md`, `docs/squawks/squawk_design.md`, `docs/export/export_logs_design.md`); see the **Design Docs** section of AGENTS.md for the full map. Each carries an "Implementation Status" note reflecting what has actually shipped. New docs are authored in HTML.
- **New feature module** — follow `feature/tasks` as the reference; respect the strict dependency rules in AGENTS.md (`sharedassets` carries no feature deps; `datamanager` and `model` never depend on UI). Register the Koin module in `core/di/CommonAppModules.kt` (the single list shared by all hosts), and register routes in `feature/shell`'s shared nav graph.

## Coding conventions worth repeating

- Use `kotlin.time.Instant`, never `kotlinx.datetime.Instant`.
- Feature managers read/write the local `EntityStore` only — never Firestore.
- `core:storage` and `core:ui` api-export most shared deps; don't redeclare them downstream.
- Feature flags are gated by `FeatureLabManager` / `FeatureFlags` (e.g. `attachmentUploadEnabled`).
- Build-time/platform gates go through the injected `AppCapability` singleton (`core:appinfo`), not `isDeveloperBuild` checks or scattered `expect`/`actual` booleans.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
