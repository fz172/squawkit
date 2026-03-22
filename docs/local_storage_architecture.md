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

## What Needs to Be Stored

| Data | Current Home | Size Characteristics |
|---|---|---|
| License info | Firestore blob (`users/{uid}/profile/license_info`) | Single object, rarely changes |
| Fleet metadata | Firestore blobs (`users/{uid}/fleet/{aircraftId}`) | Small collection, nested protos |
| Maintenance logs | Firestore subcollection (`fleet/{id}/maintenance_logs`) | Grows over time, needs querying |

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

**Where Markdown *does* make sense:** as an export/print format. A "Export logbook as Markdown"
feature would produce beautiful, portable, human-readable records. Great for sending to an A&P,
printing, or archiving in git. But not the internal database.

### ✅ Recommended: Room (SQLite) + Proto DataStore

| Use | Technology | Why |
|---|---|---|
| License info | **Jetpack Proto DataStore** | Single object, typed, coroutine-friendly, atomic |
| Fleet + Aircraft components | **Room (SQLite)** | Queryable, relational, ACID, Hilt-compatible |
| Maintenance logs | **Room (SQLite)** | Needs filtering/sorting by date, component, inspection type |
| Attachments (PDFs, photos) | **Internal app storage** (files/) | Large binary blobs belong on filesystem, not in DB |

**Why Room over raw SQLite:** Less boilerplate, compile-time query verification, Kotlin Flow
integration, migration support via `@Migration`, works seamlessly with Hilt.

**Why Proto DataStore over SharedPreferences for license:** Already have the `LicenseInfo` proto.
DataStore is coroutine-native and atomic. SharedPreferences is deprecated for complex objects.

**Why not JSON files:** Same atomicity and querying problems as Markdown, just less readable.

---

## Proposed Architecture

```
┌─────────────────────────────────────────────────────┐
│                   Compose UI Layer                   │
└────────────────────────┬────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────┐
│              ViewModels (Hilt injected)              │
└────────────────────────┬────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────┐
│              Repository Interfaces                   │
│   FleetRepository  |  MaintenanceRepository         │
│   LicenseRepository                                 │
└──────┬─────────────────────────────────┬────────────┘
       │                                 │
┌──────▼──────────┐         ┌────────────▼───────────┐
│  Room Database  │         │   Proto DataStore       │
│  (SQLite)       │         │   (license_info.pb)     │
│  wingslog.db    │         └────────────────────────┘
└─────────────────┘
```

The Repository layer is the critical abstraction. This means:
1. ViewModels never talk to Room or DataStore directly
2. If we ever add cloud sync, it slots in at the Repository layer — no UI changes
3. Unit-testable with fake repositories

---

## Room Database Schema

### `aircraft` table

```kotlin
@Entity(tableName = "aircraft")
data class AircraftEntity(
    @PrimaryKey val id: String,           // UUID
    val tailNumber: String,
    val make: String,
    val model: String,
    val airframeSerial: String,
    val createdAt: Long,                  // epoch ms
    val updatedAt: Long
)
```

### `engine` table

```kotlin
@Entity(
    tableName = "engine",
    foreignKeys = [ForeignKey(
        entity = AircraftEntity::class,
        parentColumns = ["id"],
        childColumns = ["aircraftId"],
        onDelete = CASCADE
    )]
)
data class EngineEntity(
    @PrimaryKey val id: String,           // UUID
    val aircraftId: String,
    val position: Int,                    // 1-indexed (engine 1, engine 2)
    val make: String,
    val model: String,
    val serial: String
)
```

### `propeller` table

```kotlin
@Entity(
    tableName = "propeller",
    foreignKeys = [ForeignKey(
        entity = EngineEntity::class,
        parentColumns = ["id"],
        childColumns = ["engineId"],
        onDelete = CASCADE
    )]
)
data class PropellerEntity(
    @PrimaryKey val id: String,
    val engineId: String,
    val hubMake: String,
    val hubModel: String,
    val hubSerial: String,
    val bladesJson: String                // JSON array of PropellerBlade
)
```

### `maintenance_log` table

```kotlin
@Entity(
    tableName = "maintenance_log",
    foreignKeys = [ForeignKey(
        entity = AircraftEntity::class,
        parentColumns = ["id"],
        childColumns = ["aircraftId"],
        onDelete = CASCADE
    )],
    indices = [
        Index("aircraftId"),
        Index("timestamp"),
        Index("componentType")
    ]
)
data class MaintenanceLogEntity(
    @PrimaryKey val id: String,           // UUID
    val aircraftId: String,
    val timestamp: Long,                  // epoch ms
    val technicianName: String,
    val workDescription: String,
    val componentType: String,            // AIRFRAME | ENGINE | PROPELLER
    val componentSerial: String,
    val componentReference: String,       // JSON: { type, position, serial }
    val inspectionsJson: String,          // JSON array: ["ANNUAL","OIL_CHANGE"]
    val tachTime: Double,
    val attachmentPaths: String           // JSON array of local file paths
)
```

**Indices:** `aircraftId`, `timestamp`, and `componentType` are indexed for fast filtering —
the compliance screen will query "all ANNUAL inspections for this aircraft ordered by timestamp desc".

### Full Database

```kotlin
@Database(
    entities = [
        AircraftEntity::class,
        EngineEntity::class,
        PropellerEntity::class,
        MaintenanceLogEntity::class
    ],
    version = 1,
    exportSchema = true                   // enables migration audit trail
)
abstract class WingsLogDatabase : RoomDatabase() {
    abstract fun aircraftDao(): AircraftDao
    abstract fun engineDao(): EngineDao
    abstract fun propellerDao(): PropellerDao
    abstract fun maintenanceLogDao(): MaintenanceLogDao
}
```

---

## Proto DataStore for License Info

Reuse the existing `LicenseInfo` proto:

```kotlin
// In :core:datastore module
val Context.licenseDataStore: DataStore<LicenseInfo> by dataStore(
    fileName = "license_info.pb",
    serializer = LicenseInfoSerializer       // implements Serializer<LicenseInfo>
)
```

Read/write are simple coroutine flows:
```kotlin
val license: Flow<LicenseInfo> = context.licenseDataStore.data
suspend fun updateLicense(info: LicenseInfo) = context.licenseDataStore.updateData { info }
```

---

## Attachment Storage

PDFs and photos are stored as files in the app's internal storage (not in the DB):

```
/data/data/com.wingslog/files/
  attachments/
    {aircraftId}/
      {logId}/
        8130_form.pdf
        annual_inspection_photo.jpg
```

The `maintenance_log.attachmentPaths` field stores the relative paths. The full path is
reconstructed at runtime using `context.filesDir`. This avoids storing large blobs in SQLite (which
degrades performance).

**Important:** Android's internal storage is private to the app. For user export/backup, we'll need
an explicit "Export" flow using `FileProvider` to share with other apps.

---

## Migration from Firebase

A one-time migration is needed to move existing Firestore data to Room.

### Migration Strategy

```
Phase 1: Add Room alongside Firebase (dual-write)
  - All writes go to both Room and Firestore
  - All reads come from Room (local-first)
  - Firebase kept as backup

Phase 2: One-time import
  - On first launch after update: pull all Firestore data → write to Room
  - Show migration progress screen
  - Mark migration complete in DataStore

Phase 3: Remove Firebase (optional)
  - Once users have migrated, remove Firestore dependency
  - Keep Firebase Auth if cloud login is still desired
  - Or move to local-only with no auth
```

### Data Shape Mapping

| Firestore | Room |
|---|---|
| `users/{uid}/profile/license_info` blob | Proto DataStore `license_info.pb` |
| `users/{uid}/fleet/{id}` Aircraft blob | `aircraft` + `engine` + `propeller` tables |
| `fleet/{id}/maintenance_logs/{logId}` | `maintenance_log` table |

The nested `Aircraft` proto (Engine → Propeller → Blades) gets normalized into separate tables.
This enables queries like "show all engines with serial X" without deserializing blobs.

---

## Repository Interface Design

```kotlin
interface FleetRepository {
    fun observeFleet(): Flow<List<Aircraft>>
    suspend fun getAircraft(id: String): Aircraft?
    suspend fun upsertAircraft(aircraft: Aircraft)
    suspend fun deleteAircraft(id: String)
}

interface MaintenanceRepository {
    fun observeLogs(aircraftId: String): Flow<List<MaintenanceLog>>
    fun observeLogsFiltered(
        aircraftId: String,
        componentType: ComponentType? = null,
        inspectionType: InspectionType? = null
    ): Flow<List<MaintenanceLog>>
    suspend fun getLog(id: String): MaintenanceLog?
    suspend fun upsertLog(log: MaintenanceLog)
    suspend fun deleteLog(id: String)
    suspend fun getLastInspection(aircraftId: String, type: InspectionType): MaintenanceLog?
}

interface LicenseRepository {
    fun observeLicense(): Flow<LicenseInfo>
    suspend fun updateLicense(info: LicenseInfo)
}
```

---

## Module Structure

```
:core
  :core:database          ← Room DB, DAOs, entities, type converters
  :core:datastore         ← Proto DataStore for license info
  :core:model             ← Domain models (Aircraft, MaintenanceLog, etc.)
  :core:data              ← Repository implementations (Hilt-provided)
```

Each feature module (`:feature:fleet`, `:feature:maintenance`) depends only on `:core:data`
interfaces — never directly on Room or DataStore.

---

## Backup & Export Strategy

Since data is fully local, backup becomes the user's responsibility. We should provide:

1. **Markdown Export** (as Fan originally envisioned) — per-aircraft logbook export:
   ```
   # N12345 - Cessna 172 (S/N 17280001)
   
   ## Annual Inspection — 2024-03-15
   **Tach:** 1234.5 hrs
   **Component:** Airframe (S/N 17280001)
   **Work performed:** Annual inspection per FAR 43...
   ```

2. **JSON/ZIP Export** — machine-readable full backup, importable into a new device

3. **Android Auto Backup** — declare `android:allowBackup="true"` with a `backup_rules.xml`
   that includes `wingslog.db` and `license_info.pb`. Google will back these up to the user's
   Google account automatically (up to 25MB, encrypted).

4. **Manual backup** — "Export to file" using `FileProvider` → user saves ZIP to Google Drive,
   iCloud, etc.

---

## Implementation Checklist

- [ ] Add Room dependency to `:core:database`
- [ ] Define entities + DAOs + `WingsLogDatabase`
- [ ] Add Proto DataStore dependency to `:core:datastore`
- [ ] Implement `LicenseInfoSerializer`
- [ ] Implement Repository interfaces in `:core:data`
- [ ] Provide via Hilt (`@Singleton` scope)
- [ ] Build Firebase → Room migration utility
- [ ] Add Markdown export feature
- [ ] Add backup_rules.xml for Android Auto Backup
- [ ] Update PRD and database_schema_design.md

---

## Open Questions

1. **Multi-device sync**: If Fan ever wants to use WingsLog on multiple devices (phone + tablet),
   local-only means manual export/import. Is this acceptable, or should sync be a future phase?

2. **Auth**: If Firebase is fully removed, there's no login. The app becomes single-user,
   device-local. Is this the intent?

3. **Room vs SQLite directly**: Room is strongly recommended for Android. Any reason to drop down
   to raw SQLite?

4. **Protobuf reuse**: Existing proto definitions can still be used as the domain model layer
   (passed around in ViewModels), even if Room stores the data relationally. Type converters handle
   the translation. Worth keeping protos as the canonical domain model?
