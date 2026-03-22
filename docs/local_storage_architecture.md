# Local Storage Architecture Design

## Overview

This document proposes replacing Firebase Firestore with fully local on-device storage for all
WingsLog data: user license info, fleet metadata, and maintenance logs. No data leaves the device
unless explicitly exported by the user.

**Motivation:**
- Privacy: sensitive aviation records stay on-device
- No Firebase dependency / billing / auth complexity
- Full offline operation with no degraded mode
- App works without internet, forever

---

## ⚠️ KMP Constraint: Android-Only Solutions Are Out

WingsLog targets **Android, iOS, and Web** via KMP + Compose Multiplatform. This rules out
Android-only storage APIs:

| Library | Android | iOS | Web | Verdict |
|---|---|---|---|---|
| Jetpack Room | ✅ | ❌ | ❌ | **Not viable** |
| Proto DataStore | ✅ | ❌ partial | ❌ | **Not viable as-is** |
| SQLDelight | ✅ | ✅ | ✅ (WASM) | ✅ **Recommended** |
| Multiplatform Settings | ✅ | ✅ | ✅ | ✅ **Recommended** |
| Files (expect/actual) | ✅ | ✅ | limited | ✅ for attachments |

All storage choices must be **KMP-native** and work across all three targets.

---

## What Needs to Be Stored

| Data | Current Home | Size / Query Needs |
|---|---|---|
| License info | Firestore blob (`users/{uid}/profile/license_info`) | Single object, rarely changes, no queries |
| Fleet metadata | Firestore blobs (`users/{uid}/fleet/{aircraftId}`) | Small collection, nested components |
| Maintenance logs | Firestore subcollection (`fleet/{id}/maintenance_logs`) | Grows over time, filter/sort by date, component, inspection type |
| Attachments (PDFs, photos) | Firebase Storage | Binary blobs, platform filesystem |

---

## Storage Format Evaluation

### ❌ Markdown as Primary Storage

Fan's suggestion of Markdown files deserves honest analysis.

**Why it won't work as the database:**
- No indexing → filtering/sorting requires full file scan across all records
- No atomic writes → a crash mid-save corrupts the record
- No relationships → linking logs to aircraft requires fragile filename conventions
- No schema enforcement → any manual edit can break parsing
- Concurrent writes unsafe (multiple coroutines racing to update a file)
- Querying (e.g., "show all ENGINE logs since tach 1234") requires parsing every file
- No KMP standard library for safe, atomic file I/O across iOS and Android

**Where Markdown *does* make sense:** as an export/print format. A "Export logbook as Markdown"
feature would produce beautiful, portable, human-readable records — perfect for sending to an A&P,
printing, or archiving in git. This is included in the export strategy below.

### ✅ Recommended: SQLDelight + Multiplatform Settings

| Use | Technology | Why |
|---|---|---|
| License info | **Multiplatform Settings** | Single object, key-value typed, KMP-native, no boilerplate |
| Fleet + maintenance logs | **SQLDelight** | KMP SQLite, typed queries, Kotlin Flow, compile-time SQL verification |
| Attachments | **expect/actual filesystem** | Large blobs on native filesystem, paths stored in SQLDelight |

---

## SQLDelight

[SQLDelight](https://cashapp.github.io/sqldelight/) is the standard KMP SQL database library by
Cash App. It generates type-safe Kotlin APIs from `.sq` files at compile time.

**Platform drivers:**

| Platform | Driver |
|---|---|
| Android | `AndroidSqliteDriver` (standard Android SQLite) |
| iOS | `NativeSqliteDriver` (SQLite bundled in iOS) |
| Web (JS/WASM) | `WebWorkerDriver` (SQLite compiled to WASM via `sql.js`) |

All platforms share the same `.sq` schema and query files in `commonMain`. Zero platform-specific
SQL needed.

### Dependency Setup

```toml
# libs.versions.toml
sqldelight = "2.0.2"
multiplatformSettings = "1.1.1"
```

```kotlin
// build.gradle.kts (commonMain)
plugins {
    id("app.cash.sqldelight") version "2.0.2"
}

sqldelight {
    databases {
        create("WingsLogDatabase") {
            packageName.set("com.wingslog.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")
        }
        androidMain.dependencies {
            implementation("app.cash.sqldelight:android-driver:2.0.2")
        }
        iosMain.dependencies {
            implementation("app.cash.sqldelight:native-driver:2.0.2")
        }
        jsMain.dependencies {
            implementation("app.cash.sqldelight:web-worker-driver:2.0.2")
        }
    }
}
```

### Schema (`.sq` files in `commonMain/sqldelight/`)

#### `Aircraft.sq`

```sql
CREATE TABLE aircraft (
    id TEXT NOT NULL PRIMARY KEY,
    tail_number TEXT NOT NULL,
    make TEXT NOT NULL,
    model TEXT NOT NULL,
    airframe_serial TEXT NOT NULL,
    created_at INTEGER NOT NULL,   -- epoch ms
    updated_at INTEGER NOT NULL
);

selectAll:
SELECT * FROM aircraft ORDER BY tail_number ASC;

selectById:
SELECT * FROM aircraft WHERE id = ?;

upsert:
INSERT OR REPLACE INTO aircraft VALUES (?, ?, ?, ?, ?, ?, ?);

deleteById:
DELETE FROM aircraft WHERE id = ?;
```

#### `Engine.sq`

```sql
CREATE TABLE engine (
    id TEXT NOT NULL PRIMARY KEY,
    aircraft_id TEXT NOT NULL,
    position INTEGER NOT NULL,          -- 1-indexed
    make TEXT NOT NULL,
    model TEXT NOT NULL,
    serial TEXT NOT NULL,
    FOREIGN KEY (aircraft_id) REFERENCES aircraft(id) ON DELETE CASCADE
);

selectByAircraft:
SELECT * FROM engine WHERE aircraft_id = ? ORDER BY position ASC;

upsert:
INSERT OR REPLACE INTO engine VALUES (?, ?, ?, ?, ?, ?);

deleteByAircraft:
DELETE FROM engine WHERE aircraft_id = ?;
```

#### `Propeller.sq`

```sql
CREATE TABLE propeller (
    id TEXT NOT NULL PRIMARY KEY,
    engine_id TEXT NOT NULL,
    hub_make TEXT NOT NULL,
    hub_model TEXT NOT NULL,
    hub_serial TEXT NOT NULL,
    blades_json TEXT NOT NULL,          -- JSON array of blade serials/make/model
    FOREIGN KEY (engine_id) REFERENCES engine(id) ON DELETE CASCADE
);

selectByEngine:
SELECT * FROM propeller WHERE engine_id = ?;

upsert:
INSERT OR REPLACE INTO propeller VALUES (?, ?, ?, ?, ?, ?);
```

#### `MaintenanceLog.sq`

```sql
CREATE TABLE maintenance_log (
    id TEXT NOT NULL PRIMARY KEY,
    aircraft_id TEXT NOT NULL,
    timestamp INTEGER NOT NULL,         -- epoch ms
    technician_name TEXT NOT NULL,
    work_description TEXT NOT NULL,
    component_type TEXT NOT NULL,       -- AIRFRAME | ENGINE | PROPELLER
    component_serial TEXT NOT NULL,
    component_reference TEXT NOT NULL,  -- JSON: { type, position, serial }
    inspections_json TEXT NOT NULL,     -- JSON array: ["ANNUAL","OIL_CHANGE"]
    tach_time REAL NOT NULL,
    attachment_paths TEXT NOT NULL,     -- JSON array of local file paths
    FOREIGN KEY (aircraft_id) REFERENCES aircraft(id) ON DELETE CASCADE
);

CREATE INDEX idx_log_aircraft ON maintenance_log(aircraft_id);
CREATE INDEX idx_log_timestamp ON maintenance_log(timestamp);
CREATE INDEX idx_log_component_type ON maintenance_log(component_type);

selectByAircraft:
SELECT * FROM maintenance_log
WHERE aircraft_id = ?
ORDER BY timestamp DESC;

selectByAircraftAndComponent:
SELECT * FROM maintenance_log
WHERE aircraft_id = ? AND component_type = ?
ORDER BY timestamp DESC;

selectLatestByAircraft:
SELECT * FROM maintenance_log
WHERE aircraft_id = ?
ORDER BY timestamp DESC
LIMIT 1;

upsert:
INSERT OR REPLACE INTO maintenance_log
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

deleteById:
DELETE FROM maintenance_log WHERE id = ?;
```

### Platform Driver Setup (expect/actual)

```kotlin
// commonMain
expect fun createDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver

// androidMain
actual fun createDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver =
    AndroidSqliteDriver(schema, context, "wingslog.db")

// iosMain
actual fun createDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver =
    NativeSqliteDriver(schema, "wingslog.db")

// jsMain
actual fun createDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver =
    WebWorkerDriver(Worker(js("""new URL("@cashapp/sqldelight-sqljs-worker/sqljs.worker.js", import.meta.url)""")))
```

---

## Multiplatform Settings for License Info

[Multiplatform Settings](https://github.com/russhwolf/multiplatform-settings) (Russell Wolf) is the
standard KMP key-value store.

**Platform backing:**

| Platform | Backing Store |
|---|---|
| Android | `SharedPreferences` |
| iOS | `NSUserDefaults` |
| Web | `LocalStorage` |

Since `LicenseInfo` is a Wire proto, encode it to bytes and store as a Base64 string:

```kotlin
// commonMain
class LicenseRepository(private val settings: Settings) {
    private val KEY = "license_info_proto"

    fun getLicense(): LicenseInfo? {
        val encoded = settings.getStringOrNull(KEY) ?: return null
        return LicenseInfo.ADAPTER.decode(encoded.decodeBase64Bytes())
    }

    fun saveLicense(info: LicenseInfo) {
        val encoded = LicenseInfo.ADAPTER.encode(info).encodeBase64()
        settings.putString(KEY, encoded)
    }
}
```

Dependencies:
```kotlin
commonMain.dependencies {
    implementation("com.russhwolf:multiplatform-settings:1.1.1")
    implementation("com.russhwolf:multiplatform-settings-no-arg:1.1.1")
}
```

---

## Attachment Storage (Platform-Specific Paths)

PDFs and photos are stored on the native filesystem. Only the path is stored in SQLDelight.

**Path roots per platform:**

| Platform | Directory | Notes |
|---|---|---|
| Android | `context.filesDir` | Private to app, auto-backed up |
| iOS | `NSFileManager.defaultManager.applicationSupportDirectory` | Private, included in iCloud Backup by default |
| Web | N/A | Attachments not supported on web (no persistent filesystem) |

```kotlin
// expect/actual for platform-specific base path
expect fun attachmentDirectory(): Path  // okio Path

// Structure (same on Android + iOS):
// <app-files>/attachments/{aircraftId}/{logId}/filename.pdf
```

Use [Okio](https://square.github.io/okio/) (`com.squareup.okio:okio`) for KMP filesystem access —
already Wire's dependency, so it's likely already in the build.

---

## Proposed Architecture

```
┌─────────────────────────────────────────────────────┐
│           Compose Multiplatform UI (commonMain)      │
└────────────────────────┬────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────┐
│              ViewModels (commonMain, Koin)           │
└────────────────────────┬────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────┐
│         Repository Interfaces (commonMain)           │
│  FleetRepository | MaintenanceRepo | LicenseRepo    │
└──────┬─────────────────────────────────┬────────────┘
       │                                 │
┌──────▼──────────┐         ┌────────────▼───────────┐
│   SQLDelight    │         │  Multiplatform Settings │
│  (wingslog.db)  │         │  (NSUserDefaults /      │
│  Android/iOS/   │         │   SharedPrefs /         │
│  Web WASM       │         │   LocalStorage)         │
└─────────────────┘         └────────────────────────┘
```

The repository abstraction is critical. ViewModels never touch SQLDelight or Settings directly. If
cloud sync is added later, it plugs in at the repository layer with zero UI changes.

---

## Repository Interface Design

```kotlin
// commonMain — all interfaces
interface FleetRepository {
    fun observeFleet(): Flow<List<Aircraft>>
    suspend fun getAircraft(id: String): Aircraft?
    suspend fun upsertAircraft(aircraft: Aircraft)
    suspend fun deleteAircraft(id: String)
}

interface MaintenanceRepository {
    fun observeLogs(aircraftId: String): Flow<List<MaintenanceLog>>
    fun observeLogsByComponent(
        aircraftId: String,
        componentType: ComponentType
    ): Flow<List<MaintenanceLog>>
    suspend fun upsertLog(log: MaintenanceLog)
    suspend fun deleteLog(id: String)
    suspend fun getLastInspection(aircraftId: String, type: InspectionType): MaintenanceLog?
}

interface LicenseRepository {
    fun getLicense(): LicenseInfo?
    fun saveLicense(info: LicenseInfo)
}
```

---

## Module Structure

```
:core
  :core:database          ← SQLDelight schema (.sq files), generated DAOs, platform drivers
  :core:settings          ← Multiplatform Settings, LicenseRepository
  :core:model             ← Domain models (Aircraft, MaintenanceLog, etc.)
  :core:data              ← Repository implementations (Koin-provided)
  :core:filesystem        ← expect/actual attachment path helpers (Okio)
```

Each feature module depends only on `:core:data` interfaces. Never on SQLDelight or Settings
directly.

---

## iOS-Specific Notes

### Storage Location
- SQLite DB: placed in `Library/Application Support/` (not in `Documents/`) per Apple guidelines.
  This keeps it private and prevents it from appearing in the Files app.
- `Library/Application Support/` is **included in iCloud Backup** automatically.
- `Library/Caches/` is excluded from backup — do NOT put the database here.

### Performance
- SQLDelight's `NativeSqliteDriver` is synchronous on iOS/Kotlin/Native. For large result sets,
  dispatch reads off the main thread. SQLDelight coroutines extension handles this automatically
  when using `Flow`.
- WAL (Write-Ahead Logging) mode is recommended for concurrent read/write. Enable via pragma:
  ```sql
  PRAGMA journal_mode=WAL;
  ```

### File Attachments
- Attachments go in `Application Support/wingslog/attachments/` — included in iCloud Backup.
- For user-initiated export, use `UIDocumentInteractionController` (via expect/actual interop).

### App Sandbox
- iOS apps are sandboxed. Files are not accessible by other apps unless explicitly shared via
  `UIActivityViewController` / `FileProvider`.

---

## Android-Specific Notes

### Storage Location
- `context.filesDir` — private to the app, not visible in Files app, included in Auto Backup.
- For the SQLite file, SQLDelight/Android driver handles placement automatically.

### Android Auto Backup
- Declare `android:allowBackup="true"` in `AndroidManifest.xml`.
- Add `backup_rules.xml` to include `wingslog.db` and exclude caches:
  ```xml
  <full-backup-content>
      <include domain="database" path="wingslog.db"/>
      <include domain="file" path="attachments/"/>
  </full-backup-content>
  ```
- Auto Backup stores up to 25MB encrypted to the user's Google account.

---

## Web-Specific Notes

### SQLite WASM
- SQLDelight's `WebWorkerDriver` uses `sql.js` (SQLite compiled to WASM) running in a Web Worker.
- Data persists to `IndexedDB` between sessions (via `sql.js-httpvfs` or `absurd-sql` backend).
- **Bundle size impact**: ~1.5MB additional WASM payload. Acceptable for an authenticated tool app.
- Storage limits: browsers may cap IndexedDB at 1-5GB; more than sufficient for logbook data.

### Attachments on Web
- No persistent native filesystem. Attachments are not supported on the web target.
- Web UI should display a notice: *"Attachment upload/download is only available on mobile."*
- Multiplatform Settings falls back to `LocalStorage` on Web — fine for license info.

---

## Migration from Firebase

A one-time migration is needed to move existing Firestore data to SQLDelight.

### Migration Strategy

```
Phase 1: Dual-write (backward-compatible)
  - All writes → SQLDelight (primary) + Firestore (mirror)
  - All reads → SQLDelight only

Phase 2: One-time import on first launch
  - Show migration progress screen
  - Pull all Firestore data → write to SQLDelight
  - Write migration_complete flag to Multiplatform Settings

Phase 3: Remove Firebase Firestore
  - Remove GitLive Firestore dependency
  - Optionally keep Firebase Auth if cloud login still desired
  - Or go fully local (no auth)
```

### Data Shape Mapping

| Firestore | SQLDelight / Settings |
|---|---|
| `users/{uid}/profile/license_info` blob | Multiplatform Settings (`license_info_proto`) |
| `users/{uid}/fleet/{id}` Aircraft blob | `aircraft` + `engine` + `propeller` tables |
| `fleet/{id}/maintenance_logs/{logId}` | `maintenance_log` table |

The nested `Aircraft` proto gets normalized into separate tables — enables queries like
"all engines with serial X" without deserializing blobs.

---

## Backup & Export Strategy

Since data is fully local, backup becomes the user's responsibility. WingsLog should provide:

1. **Platform Auto-Backup** (free, automatic)
   - Android: Google Auto Backup (encrypted, 25MB limit)
   - iOS: iCloud Backup (included automatically via `Application Support/`)
   - Web: browser `LocalStorage` persists until cleared

2. **Markdown Export** — human-readable per-aircraft logbook:
   ```markdown
   # N12345 — Cessna 172 (S/N 17280001)

   ## Annual Inspection — 2024-03-15
   **Tach:** 1234.5 hrs  
   **Component:** Airframe (S/N 17280001)  
   **Work performed:** Annual inspection per FAR 43 Appendix D...
   ```

3. **JSON/ZIP Export** — full backup, importable on a new device. Produced in `commonMain`
   using Kotlin serialization + Okio; exported via platform share sheet.

4. **Manual share** — "Export to Files / Google Drive / iCloud Drive" via
   `UIActivityViewController` (iOS) / `FileProvider` (Android).

---

## Implementation Checklist

- [ ] Add SQLDelight Gradle plugin + dependencies (Android, iOS, JS drivers)
- [ ] Write `.sq` schema files: `Aircraft.sq`, `Engine.sq`, `Propeller.sq`, `MaintenanceLog.sq`
- [ ] Add `app.cash.sqldelight:coroutines-extensions` for Flow support
- [ ] Add `com.russhwolf:multiplatform-settings` for license info
- [ ] Implement `expect/actual createDriver(...)` for each platform
- [ ] Implement `expect/actual attachmentDirectory()` using Okio
- [ ] Create `:core:database` and `:core:settings` modules
- [ ] Implement Repository interfaces in `:core:data` (Koin-provided)
- [ ] Build Firebase → SQLDelight one-time migration utility
- [ ] Enable WAL mode pragma in database setup
- [ ] Add `backup_rules.xml` for Android Auto Backup
- [ ] Verify iOS storage location is `Application Support/` not `Documents/` or `Caches/`
- [ ] Add web attachment-unsupported notice in UI
- [ ] Add Markdown export feature
- [ ] Update `PRD.md` and `database_schema_design.md`

---

## Open Questions

1. **Multi-device sync**: Local-only means no sync between phone and tablet. Is manual
   export/import sufficient, or should sync be a future phase (e.g., CloudKit on iOS, Google
   Drive sync on Android)?

2. **Auth**: If Firebase Firestore is fully removed, is Firebase Auth also removed? If so, the
   app becomes single-user device-local — is this the intent?

3. **Protobuf domain model**: Keep Wire proto types as the domain model passed between layers
   (ViewModel ↔ Repository), with SQLDelight-generated types used only internally in
   `:core:database`? This is the recommended approach — keeps domain model clean.

4. **Web attachment support**: Since the web platform has no persistent filesystem, is the web
   target considered read-only, or should we support upload via `IndexedDB`/`File API`?
