# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Primary reference

The canonical guidance for this repo lives in [AGENTS.md](AGENTS.md). Read it first — it covers build/test commands, module layout, the canonical feature module pattern (`model` / `datamanager` / `sharedassets` / `viewing` / `update`), the local-first storage architecture (R1), the sync engine, dogfood build wiring, and coding conventions. Everything below is a quick-orientation supplement; AGENTS.md is authoritative on conflicts.

## Quick orientation

WingsLog (user-facing brand: **Hopply**) is a Kotlin Multiplatform aviation logbook / fleet management app targeting Android, iOS, and web with Compose Multiplatform. Package: `dev.fanfly.wingslog`.

**Architecture in one line:** MVVM + StateFlow, Koin DI, local-first `EntityStore` (SQLDelight) as the single source of truth, with a background Firestore sync engine (`feature/sync`) as the *only* Firestore client. Feature managers never touch Firestore directly.

**Module tree:** `app/` (Android entry), `composeApp/` (shared Compose + Koin init), `webApp/` (Kotlin/JS host), `iosApp/` (Xcode project), `core/*` (model, ui, auth, firebase, storage, datetime, appinfo, attachments[orphaned]), `feature/*` (one module per feature, see AGENTS.md for the canonical submodule pattern), `backend/firebase/functions/` (TypeScript Cloud Functions — not a Gradle module).

## Common commands

```bash
./gradlew assembleDebug                              # Build debug APK
./gradlew assembleDogfoodDebug                       # Build dogfood APK (includes Fake Data Generator)
./gradlew lint
./gradlew testDebugUnitTest                          # All Android unit tests
./gradlew :feature:fleet:viewing:testDebugUnitTest   # Single-module tests
./gradlew :composeApp:iosSimulatorArm64Test          # iOS simulator unit tests (local only — not on CI)
./gradlew :webApp:jsBrowserDevelopmentWebpack        # Web dev bundle
```

CI runs lint → `assembleDebug` → `testDebugUnitTest` and requires the `GOOGLE_SERVICES_JSON` secret. iOS is not built on CI; use the **iosAppDogfood** scheme in Xcode for the iOS dogfood build.

## Before non-trivial changes

- **UI work** — read `PRODUCT.md`, `DESIGN.md`, and `.impeccable/design.json` first. The aviation palette, typography, and brand principles are required (no dynamic color).
- **Feature work** — check `docs/` for the relevant PRD / design doc (e.g. `storage_r1_design.md`, `storage_r2_design.md`, `attachments_design.md`, `squawk_design.md`, `export_logs_design.md`). Each carries an "Implementation Status" note reflecting what has actually shipped.
- **New feature module** — follow `feature/tasks` as the reference; respect the strict dependency rules in AGENTS.md (`sharedassets` carries no feature deps; `datamanager` and `model` never depend on UI). Register the Koin module in `composeApp/src/commonMain/kotlin/dev/fanfly/wingslog/di/initKoin.kt`.

## Coding conventions worth repeating

- Use `kotlin.time.Instant`, never `kotlinx.datetime.Instant`.
- Feature managers read/write the local `EntityStore` only — never Firestore.
- `core:storage` and `core:ui` api-export most shared deps; don't redeclare them downstream.
- Feature flags are gated by `FeatureLabManager` / `FeatureFlags` (e.g. `attachmentUploadEnabled`).
