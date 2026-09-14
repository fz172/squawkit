# Design Doc: Data Log Visualizer (Flight Data)

**PRD:** [`data_log_visualizer_PRD.md`](data_log_visualizer_PRD.md)
**Status:** 📋 Proposed
**Last updated:** 2026-09-13

---

## 1. Overview

Six pieces, in dependency order. Each is one or two PRs (§15).

1. **Foundation with no UI.** A `DataLog` proto and `CollectionKind`, the raw file modelled as an
   `Attachment` embedded in the record so every existing blob mechanism (reconciler, tombstone GC,
   server GC, upload drivers, broker) applies unchanged. A new `Section` value and lexicon fields,
   an airplane template bump, and `TemplateRegistry.capabilitiesFor` so the section reaches existing
   Things without a DNA refresh (PRD R42). `AppCapability.isDataLogsSupported`, developer builds only.
2. **Import.** A `DataLogParser` seam with the Garmin parser behind it (G3X now, G1000 later), a
   canonical-series registry, gzip through a three-line `expect`/`actual`, and a `DataLogManager`
   that turns a picked file into a synced record plus a blob.
3. **Section.** `ShellSection.DATA_LOGS`, the list, the upload button and FAB, the guest gate, and
   the variable-width bottom pill (PRD R2b).
4. **Visualizer.** A full-screen `Screen.DataLogViewer` route: Compose `Canvas` panes with min/max
   decimation, the three gestures with pointer equivalents (PRD R23), unit-grouped axes, chips
   legend, series sidebar, stable per-series colours per theme, map pane through Coil.
5. **Attachment type.** `ATTACHMENT_TYPE_DATA_LOG` as a blobless reference, handled at every
   link-versus-file branch the codebase has (there are fifteen; §9).
6. **Cross-cutting.** Collaboration notifications, four analytics events, the mobile free-tier
   banner, strings and the snapshot, the rollout flip. Drag-and-drop is a later, shared piece (§13.3).

No Firestore rules change is needed for size: the storage rules carry no size or content constraints
at all (`backend/firebase/storage.rules:19-24`). One rules edit adds the new kind to the shared-Thing
member allow-list.

### 1.1 Data flow, end to end

Read this before the protos in §4. One principle runs through it: **the raw file is the only thing
stored beyond the record's metadata.** Everything drawn on screen is derived from it on the device
that draws, and the server never parses it.

```
 CLIENT A (uploads)                                              shapes
 ─────────────────────────────────────────────────────────────  ───────────────────────────────
 ① pick        feature/attachment/viewing  FilePicker           PickedFile(uri, name, mime, size)
 ② read        feature/attachment/datamanager  FileByteReader   ByteArray — raw CSV
 ③ sniff       feature/datalog/datamanager  HeaderSniffer       DataLogFormat (proto enum)
 ④ parse       feature/datalog/datamanager  GarminParser        ParsedDataLog (Kotlin, transient)
                                                                 ├ record fields (source, start, …)
                                                                 └ DataLogSeriesData (columnar arrays)
 ⑤ enrich      feature/datalog/datamanager  CanonicalSeriesRegistry, identity, airborne, raw_sha256
 ⑥ encode      feature/datalog/datamanager  GzipCodec           ByteArray — gzip
 ⑦ store bytes core/storage  LocalBlobStore.put                  blob_object row LOCAL_ONLY + file on disk
                                                                 → Attachment proto (raw_file)
 ⑧ store record core/storage  EntityStore.put                    DataLog proto → entity row, dirty=1
 ⑨ schedule    core/storage  UploadScheduler                     ─
        │                      │
        ▼ PushWorker           ▼ BlobUploadDriver                (feature/sync/data)
 FIRESTORE  users/{uid}/thing/{thingId}/data_log/{id}            SyncDocWire{payload: base64(DataLog), schema: "datalog.DataLog", …}
 STORAGE    users/{uid}/thing/{thingId}/blobs/{blobId}           gzip bytes, contentType application/gzip
        │
 SERVER (Cloud Functions, backend/firebase/functions)
 ⑩ onRecordWritten   decode SyncDocWire → DataLog (ts-proto)    → push notification to other members
 ⑪ onRecordDeleted   tombstone → blobRefs.ts → delete Storage object
 ⑫ V2 only           nearest-ident lookup from end_latitude/longitude → end_location_ident
        │
 CLIENT B (any device with access, including A after reinstall)
 ⑬ pull        feature/sync/data  PullListener                  SyncDocWire → base64 decode → WireCodec(DataLog.ADAPTER) → entity row
 ⑭ index       feature/attachment/datamanager  BlobIndexReconciler  AttachmentRefs.of(DataLog) → blob_object row REMOTE_ONLY (no bytes)
 ⑮ list        feature/datalog/viewing  DataLogListViewModel   List<DataLog> proto → rows, straight from catalogue fields
 ⑯ open        feature/datalog/update  DataLogViewerViewModel  ensureLocal → BlobDownloadDriver (broker if foreign) → sha256 verified
 ⑰ decode      feature/datalog/datamanager  GzipCodec, parser by DataLog.format → DataLogSeriesData → DataLogCache (memory)
 ⑱ draw        feature/datalog/viewing  ChartPane              ChartLayout + DataLogSeriesData → decimate(window, width) → Path → Canvas
 ⑲ remember    feature/datalog/datamanager  ChartLayoutStore    ChartLayout → JSON on the device, unsynced
 ⑳ attach      feature/attachment/*                            Attachment{type = DATA_LOG, data_log_id} inside a log/task/squawk proto
```

**Who owns what.**

| Concern | Module | Notes |
|---|---|---|
| Picking and reading a file | `feature/attachment` (existing) | Reused as is. `PickedFile` and `FileByteReader` are already platform-neutral. |
| Recognising and parsing a format | `feature/datalog/datamanager` | The only code that knows CSV. Output is a Kotlin data class, never a proto. |
| Deciding what a column *means* | `feature/datalog/datamanager` `CanonicalSeriesRegistry` | Fills `canonical_id`; presets and defaults speak only canonical ids. |
| Persisting bytes | `core/storage` `LocalBlobStore` + `feature/sync/data` drivers (existing) | The record's embedded `Attachment` is what every blob mechanism reads. |
| Persisting and syncing the record | `core/storage` `EntityStore` + `feature/sync/data` (existing) | One new `CollectionKind`; the wire envelope and codec are generic. |
| Server behaviour | `backend/firebase/functions` | Notification title and blob GC only. No parsing, no derived data in V1. |
| Reading on another device | `feature/sync/data`, `feature/attachment/datamanager` (existing) | Pull writes the record; the reconciler indexes the blob as remote-only. |
| Turning bytes back into series | `feature/datalog/datamanager` `DataLogManager.load` | Same parser as import, chosen by the stored `format`; result cached in memory. |
| Drawing | `feature/datalog/viewing` | Pure functions from `(DataLogSeriesData, ChartLayout, window, width)` to paths. |
| What the user changed | `feature/datalog/update` ViewModel, `ChartLayoutStore` | Layout is device state, not a record. |

**The shapes, in order.**

1. **Raw CSV bytes.** Exactly what the avionics wrote. Kept byte-exact inside the gzip blob so a
   future parser can re-read it (PRD R9).
2. **`ParsedDataLog`** (Kotlin, `feature/datalog/model`). Two halves: the record-shaped metadata
   (source header, start, offset, duration, catalogue) and `DataLogSeriesData`, the columnar arrays.
   Transient: it exists during import and while a viewer is open, and is rebuilt from shape 1.
3. **`DataLog` proto** (`core/model`, §4.1). The metadata half of shape 2, plus the embedded
   `Attachment` pointing at the blob, `raw_sha256`, encoding, and derived flags. Persisted in the
   `entity` table and synced. About 7 KB for a G3X file. It is enough to render the list and the
   sidebar's *Flight* tab without touching the bytes.
4. **Gzip blob.** Shape 1 compressed, addressed by a fresh blob id, described by the record's
   `Attachment` (`sha256` and `size_bytes` of the *stored* bytes, which is what the download driver
   verifies).
5. **`SyncDocWire`** (Firestore document). The generic envelope: `payload` is base64 of shape 3,
   `schema` is `"datalog.DataLog"`. Nothing DataLog-specific here; it is what every record kind uses.
6. **`DataLog` again, on another device.** Decoded by `WireCodec(DataLog.ADAPTER)` into the same
   Wire class. The list renders from it directly.
7. **`DataLogSeriesData` again.** Only when a viewer opens: download shape 4, verify, decompress,
   parse with the parser named by `format`. Cached in memory, never written back.
8. **`ChartLayout`** (Kotlin, `feature/datalog/model`). Panes and series keys, the time window, the
   target pane. ViewModel state, remembered per device as JSON. Never a proto, never synced.
9. **`Attachment{type = DATA_LOG, data_log_id}`** inside a `MaintenanceLog`, `MaintenanceTask`, or
   `Squawk`. A pointer to shape 3's id, with no bytes and an empty `sha256`, so the blob machinery
   ignores it and the DataLog record outlives the reference.

Every id above that belongs to a new type is a boxed proto message (`ThingId`, `DataLogId`, `UserId`)
in the schema and therefore in the generated Kotlin and TypeScript; only the grandfathered
`Attachment.id` stays a string, wrapped as `BlobId` where the blob store reads it. See §4.4.

Two things worth noticing before reading §4. First, shapes 2 and 7 are the same class produced by
the same parser; import and open differ only in where the bytes come from, which is why the parser
version is stored on the record. Second, the server sees only shapes 5 and 4, and reads shape 5
purely to name the record in a notification and to find the blob on delete. The V2 destination
lookup is the first server-side write into a DataLog record and will need its own design note on
how a function writes a field without racing the owning client's last-writer-wins push.

## 2. What exists today, verified

| Piece                 | Where                                                                                                                                                                               | Notes                                                                                                                                                       |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Kind registry         | `core/storage/.../CollectionKind.kt:12,123-136`, `CollectionKindCoverageTest.kt:8-21`, `EntityCodecRegistry.kt:14-34`, `di/StorageModule.kt:55-100`                                 | `TEXT` column, zero-migration; coverage test and `verifyCoverage()` force registration                                                                      |
| Per-Thing sync        | `feature/sync/data/.../SyncEngine.kt:744-750` `PER_THING_KINDS`                                                                                                                     | One list entry; `HydrationRunner`, `FirestoreRefs` are kind-agnostic                                                                                        |
| Blob ownership        | `core/storage/.../blob/AttachmentRefs.kt:24` `of(kind, payload)`                                                                                                                    | Exhaustive `when`; adding a kind breaks the build until it says whether the kind owns blobs                                                                 |
| Blob store            | `core/storage/.../blob/LocalBlobStore.kt:25-131`                                                                                                                                    | `put(id, bytes, contentType, scope)` takes a whole `ByteArray`; `installDownloaded` verifies sha256                                                         |
| Blob path             | `feature/sync/data/.../blob/BlobUploadDriver.kt:79-101`                                                                                                                             | `users/{uid}/thing/{thingId}/blobs/{blobId}`; foreign scope goes through `HttpsAttachmentBroker`                                                            |
| Remote-only index     | `feature/attachment/datamanager/.../BlobIndexReconciler.kt:31-55`                                                                                                                   | Upserts `REMOTE_ONLY` for every attachment with non-blank `id` and `sha256` that `AttachmentRefs.of` returns                                                |
| Lazy fetch            | `core/storage/.../blob/UploadScheduler.kt:12` `prefetchRemoteOnly = false`                                                                                                          | Bytes download on open, which is PRD R18                                                                                                                    |
| Server GC             | `backend/.../storage/onRecordDeleted.ts:48-151`, `blobRefs.ts:20-94`                                                                                                                | `{kind}` wildcard trigger; `schemaCanOwnBlobs` and `blobIdsInPayload` keyed on `schemaName`; `null` means delete nothing (#428)                             |
| Payload ceiling       | `FirestoreSyncWriter.kt:29` base64 envelope                                                                                                                                         | About 750 KB of proto bytes; a 108-series catalogue is roughly 7 KB                                                                                         |
| Attachment type sites | §9 table                                                                                                                                                                            | Every branch is `LINK` versus everything else; none is exhaustive                                                                                           |
| Add-attachment sheet  | `feature/attachment/viewing/.../AttachmentFormSection.kt:305-423`                                                                                                                   | Three options; "Add link" swaps the sheet body in place                                                                                                     |
| Form controllers      | `AttachmentFormController.kt:33-274`; `MaintenanceLogFormViewModel.kt:118-569`, `SquawkFormViewModel.kt:128-440`, `TaskViewModel.kt:204-219`                                        | `addLink` is the blobless template to copy                                                                                                                  |
| Detail taps           | `LogsTab.kt:93-105`, `SquawkTab.kt:466-476`, `ThingSectionContent.kt:494-505`                                                                                                       | `attachmentOpener.open` called synchronously inside the click for the web `window.open` gesture rule                                                        |
| File picking          | `feature/attachment/viewing/.../FilePicker.kt` (android SAF, iOS `UIDocumentPickerViewController`, web `<input type=file>` with eager `arrayBuffer()` into `WebPickedFileRegistry`) | No `accept` filter; no drag-and-drop anywhere in the repo                                                                                                   |
| Routes                | `core/nav/.../Screen.kt:5-113`; `feature/shell/.../ShellNavGraph.kt:194-255`                                                                                                        | Full-screen roots are plain `composable(...)`; forms are `selectionDialog`                                                                                  |
| Shell                 | `core/ui/adaptive/.../AdaptiveAppShell.kt:143-233,632-682,798-808`; `feature/thing/dashboard/.../ThingSectionContent.kt:73-179`                                                     | Sections are ViewModel state under one route; FAB is a per-section slot                                                                                     |
| Pill                  | `core/ui/adaptive/.../compose/FloatingPillNavigationBar.kt:101-152`                                                                                                                 | Selected: icon + label chip. Unselected: **text only**, no icon                                                                                             |
| Template resolution   | `core/template/.../CurrentThingTemplate.kt:91-96`, `impl/BakedInTemplateRegistry.kt:48-70`, `TemplateResolution.kt:39-68`                                                           | Lexicon resolves by id from the build; capabilities read straight from DNA; `ENUM_FIELDS = {7, 8, 9}` degrades a Thing whose DNA names an unknown `Section` |
| Template assets       | `core/template/templates/airplane.v11.textproto:140-144`, `templates/README.md:23-60`, `compile-template.sh`                                                                        | Every edit is a new version; `AirplaneTemplateAssetTest.kt:202-208` pins the four sections in order                                                         |
| Notifications         | `backend/.../notifications/onRecordWritten.ts:68-131`, `notificationModels.ts:66-108`, `recordPayloads.ts:86-110`, `pushMessages.ts:97-124`                                         | Body key is `notification_n1_body_record_{kind}`; generic per activity kind, so no new server string                                                        |
| Notification client   | `feature/notifications/viewing/.../PushPayloadRendering.kt:144-157`, `PushPayload.kt:102-121`, `NotificationTapRouter.kt:63-104`, `AdaptiveShellViewModel.kt:169-194`               | `noun()` falls through to the log noun; unknown tap kinds fall back to the Thing                                                                            |
| Analytics             | `core/analytics/.../AnalyticsEvent.kt:34-110`, `AnalyticsEvents.kt`, `AnalyticsTaxonomyTest.kt:214-235`                                                                             | Append-only enums; new `ThingScopedEvent`s must join the template-id list                                                                                   |
| Ads                   | `feature/ads/model/.../AdSurface.kt:12-16`, `feature/ads/viewing/.../AdSlot.kt:80-169`, `AdView.js.kt:19-29`                                                                        | Three list surfaces only; `AdSlot` requests `LARGE_BANNER`; web is a deliberate no-op                                                                       |
| Theme                 | `core/ui/theme/.../Theme.kt:11-70`, `Color.kt`, `StatusColors.kt`                                                                                                                   | No categorical palette; no chart or sparkline code anywhere                                                                                                 |
| Coil                  | `core/ui/widget/avataricon/.../CircularImage.kt:64-74`                                                                                                                              | `rememberAsyncImagePainter` is the house pattern; default singleton loader; ktor engine bound for Android and iOS, **not** in `webApp`                      |
| Dispatchers           | `core/storage/.../StorageDispatchers.kt`, `feature/search/model/.../SearchTuning.kt:7-10`                                                                                           | Injected dispatcher defaulting to `Dispatchers.Default`; JS is single-threaded                                                                              |
| Compression           | `feature/export/datamanager/.../StoredZipArchive.kt`, `ZipFileWriter.android.kt`                                                                                                    | Android has `java.util.zip`; iOS and web zip with STORE only. A `Crc32` exists, `internal` to export                                                        |
| Local prefs           | `core/ui/theme/.../Appearance.kt:23-26` `AppearanceStore`                                                                                                                           | Per-platform key-value store; the only unsynced preference mechanism                                                                                        |

## 3. Module layout

```
core/
  model/src/commonMain/proto/id/ids.proto  # NEW: boxed id messages ThingId, DataLogId, UserId (§4.4)
  model/src/commonMain/proto/datalog/data_log.proto  # NEW (§4.1)
  model/src/commonMain/proto/thing/
    attachment.proto                     # +ATTACHMENT_TYPE_DATA_LOG, +data_log_id (§4.2)
    capabilities.proto                   # +SECTION_DATA_LOGS
    lexicon.proto                        # +Noun data_log, +EmptyStates.data_log_hint, +data_log_description
  storage/.../CollectionKind.kt          # +DataLog
  storage/.../blob/AttachmentRefs.kt     # DataLog owns raw_file; DATA_LOG refs own nothing
  template/                              # capabilitiesFor, airplane.v12, GenericLexicon defaults (§8)
  appinfo/.../AppCapability.kt           # +isDataLogsSupported
  nav/.../Screen.kt                      # +DataLogViewer
  ui/adaptive/.../AdaptiveAppShell.kt    # +ShellSection.DATA_LOGS; pill icon-only unselected

feature/datalog/                         # NEW, canonical layout
  model/          DataLogSeriesData (parsed columns), ParsedDataLog, ImportProgress, CanonicalSeries,
                  ChartLayout (panes/series/view), GestureIntent
  datamanager/    DataLogManager (+impl), DataLogImporter, DataLogParser + GarminParser,
                  HeaderSniffer, CanonicalSeriesRegistry, GzipCodec (expect/actual), DataLogCache,
                  ChartLayoutStore, DataLogDataManagerModule
  sharedassets/   strings.xml (section, picker, badges, errors), icons
  viewing/        DataLogSectionContent + DataLogListViewModel (the shell section body — dashboard
                  depends on viewing, never update), DataLogCard, UploadGateCard/Sheet,
                  DataLogAttachmentPicker (the in-sheet picker body), chart composables:
                  DataLogChart, ChartPane, PaneHeaderChips, TimeAxis, MapPane, SeriesSidebar,
                  SeriesPalette
  update/         DataLogViewerScreen + DataLogViewerViewModel (route body: layout edits, delete),
                  DataLogUpdateModule
  di/             dataLogModule = includes(dataLogDataManagerModule, dataLogUpdateModule)
```

Chart composables sit in `viewing` although only the viewer route uses them today, because the
attachment row's future sparkline and the dashboard's list both live in modules that may depend on
`viewing` but never on `update`. The route and its ViewModel sit in `update` because they write
(layout memory, delete), matching `feature/tasks`.

Wiring per the five-step checklist: six `include(":feature:datalog:*")` lines in `settings.gradle.kts`,
`dataLogModule` in `core/di/CommonAppModules.kt` with the Gradle line in `core/di/build.gradle.kts`,
routes in `core/nav` and `feature/shell`, strings in `sharedassets`.

## 4. Data model

### 4.1 `datalog/data_log.proto`

```proto
enum DataLogFormat {
  DATA_LOG_FORMAT_UNKNOWN = 0;
  DATA_LOG_FORMAT_GARMIN_G3X = 1;
  DATA_LOG_FORMAT_GARMIN_G1000 = 2;
  DATA_LOG_FORMAT_DYNON_SKYVIEW = 3;
}
enum DataLogSeriesKind { UNKNOWN = 0; NUMERIC = 1; DISCRETE = 2; TEXT = 3; POSITION = 4; }
enum DataLogEncoding { UNKNOWN = 0; NONE = 1; GZIP = 2; }

message DataLogSource {              // the recorder's own header, verbatim strings
  string product = 1;                // "GDU 460"
  string unit = 2;                   // "PFD1"
  string software_version = 3;
  string system_id = 4;
  string identity = 5;               // aircraft_ident on Garmin; compared with the Thing (R11)
  string airframe_hours = 6;
  string engine_hours = 7;
}

message DataLogSeries {
  int32 column = 1;                  // index in the source file
  string name = 2;                   // "Oil Press"
  string short_name = 3;             // "E1 OilP"
  string unit = 4;                   // "PSI", as recorded (PRD decision 6)
  DataLogSeriesKind kind = 5;
  string canonical_id = 6;           // "engine[1].oil_press" or "" (§6.3)
  double min = 7;
  double max = 8;
  int32 sample_count = 9;            // non-empty cells
}

message DataLog {
  DataLogId id = 1;                  // boxed, never a bare string (§4.4)
  DataLogFormat format = 2;
  int32 parser_version = 3;          // bumps when the parser changes what it emits
  DataLogSource source = 4;
  google.protobuf.Timestamp start = 5;
  int32 utc_offset_minutes = 6;
  int32 duration_seconds = 7;
  int32 sample_count = 8;            // rows
  float sample_rate_hz = 9;          // median of 1/dt
  repeated DataLogSeries series = 10;
  Attachment raw_file = 11;          // type FILE, id = blob id, sha256/size of the STORED bytes
  DataLogEncoding encoding = 12;     // how raw_file's bytes are wrapped
  string raw_sha256 = 13;            // of the original file, for duplicate detection (R10)
  int64 raw_size_bytes = 14;
  string file_name = 15;
  bool identity_mismatch = 16;
  bool airborne = 17;                // false => "Ground run" (R13)
  string start_location_ident = 18;  // from the filename where the format carries one (R36)
  double end_latitude = 19;          // for the V2 server lookup
  double end_longitude = 20;
  string end_location_ident = 21;    // written by the server in V2, empty until then
  google.protobuf.Timestamp created_at = 22;
  UserId created_by = 23;
}
```

**Why `raw_file` is an `Attachment`.** Every blob mechanism in the app keys off `Attachment` protos
returned by `AttachmentRefs.of`: the remote-only reconciler, `TombstoneGc`, and the server's
`blobIdsInPayload`. Embedding one means the bytes are indexed on a second device, garbage-collected on
delete, uploaded through the same drivers and broker, and shown with the same sync-state badges, with
one `when` branch added rather than a parallel pipeline. Its `sha256` and `size_bytes` describe the
*stored* bytes because `LocalBlobStore.put` and `installDownloaded` verify those; the original file's
hash lives in `raw_sha256`.

**Size.** The sample's 108 series at about 60 bytes each is roughly 7 KB before base64, far under the
750 KB envelope ceiling. A pathological 1,000-column source would still fit.

### 4.2 `attachment.proto`

```proto
enum AttachmentType { ...; ATTACHMENT_TYPE_DATA_LOG = 5; }
message Attachment { ...; DataLogId data_log_id = 11; }   // set only for DATA_LOG; sha256 stays empty
```

A `DATA_LOG` attachment is a reference like `LINK`: no blob, empty `sha256`, `size_bytes = 0`,
`storage_path = ""`. The reconciler already skips it (blank sha256). Everything else needs the
explicit exclusion listed in §9.

### 4.3 `CollectionKind.DataLog`

`wireName = "data_log"`, `schemaName = "datalog.DataLog"`. Path
`users/{uid}/thing/{thingId}/data_log/{id}`. Touch points, following the Comment addition in
`4299ec2c6`:

| File                                                  | Change                                                                                                                                                     |
|-------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `core/storage/.../CollectionKind.kt`                  | `data object DataLog`; append to `ALL`                                                                                                                     |
| `CollectionKindCoverageTest.kt`                       | add to `allKnownKinds`                                                                                                                                     |
| `di/StorageModule.kt`                                 | `register(CollectionKind.DataLog, WireCodec(DataLog.ADAPTER))`                                                                                             |
| `blob/AttachmentRefs.kt`                              | `DataLog -> listOfNotNull(payload.raw_file)`; and in the attachment-bearing kinds, filter `type != DATA_LOG` out of `blobIdsIn` (a reference owns nothing) |
| `feature/sync/data/.../SyncEngine.kt`                 | add to `PER_THING_KINDS`                                                                                                                                   |
| `backend/firebase/firestore.rules:65-68`              | add `"data_log"` to `isSharedAircraftKind`                                                                                                                 |
| `backend/.../functions/package.json` `generate:proto` | add `id/ids.proto` and `datalog/data_log.proto` |
| `backend/.../storage/blobRefs.ts`                     | `SCHEMA["datalog.DataLog"]`, `schemaCanOwnBlobs` true, `blobIdsInPayload` returns `[raw_file.id]`; `blobIds()` excludes `DATA_LOG` alongside `LINK`          |
| `backend/.../notifications/*`                         | §11                                                                                                                                                        |
| `backend/.../test/blob-cleanup.test.ts`               | a DataLog delete reclaims its blob; a log delete never reclaims a referenced DataLog's blob                                                                |

`TombstoneGc` (`core/storage/.../TombstoneGc.kt:64-102`) is kind-agnostic once `AttachmentRefs` knows
the kind, and its `stillReferenced` cross-check already protects a blob another live record shows,
which is the case for a data log referenced from a log entry.

### 4.4 Typed identifiers, at the model level

**New model types never carry an id as a bare string, in the proto or in Kotlin.** Existing messages
(`Thing`, `Attachment.id`, `Squawk`, …) are grandfathered. The one typed id the codebase has today,
`BlobId` (`core/storage/.../blob/BlobId.kt:13`), is a Kotlin value class over a grandfathered string
field; new types go one step further and box the id in the schema itself, so the generated model
class is already the dedicated type and there is exactly one `DataLogId` in the codebase.

```proto
// core/model/src/commonMain/proto/id/ids.proto
message ThingId   { string value = 1; }
message DataLogId { string value = 1; }
message UserId    { string value = 1; }
```

Wire generates `DataLogId(value: String)` and friends as ordinary message classes with structural
equality, so they serve as map keys and `StateFlow` values without a parallel Kotlin wrapper; ts-proto
generates the matching interfaces for the functions, which read `doc.id.value`. The wire cost is two
bytes per id.

Rules for this feature:

- Every id field on a **new** message is a boxed message: `DataLog.id`, `DataLog.created_by`,
  `Attachment.data_log_id` (a new field on an old message still uses the boxed type).
- Every Kotlin API takes and returns the generated types: `DataLogManager`, `ImportProgress`,
  the ViewModels, `NotificationTapTarget.DataLog`, `Screen.DataLogViewer.createRoute(thingId:
  ThingId, dataLogId: DataLogId)`, analytics event constructors.
- Ids that are not persisted and not shared, `PaneId` and `SeriesKey` inside `ChartLayout`, are
  Kotlin value classes in `feature/datalog/model`, because a proto message for device-local UI state
  would be schema for schema's sake.
- Conversion to the grandfathered string world happens once, at the edge: `EntityStore.put(id.value,
  …)`, `ThingScopeResolver.resolve(thingId.value)`, the nav-argument reader wrapping into `ThingId(…)`
  and `DataLogId(…)`, and `BlobId(rawFile.id)` for the embedded attachment's blob.

A `ThingId` and a `DataLogId` can no longer be swapped in a call or in a proto field, which is the
whole point. Migrating the grandfathered messages and the existing string-id APIs is a separate
cleanup; this feature sets the precedent and does not do the sweep.

**`ThingId` and `UserId` name pre-existing concepts. What introducing them touches, verified:**

- No existing proto field changes type. `Thing.id`, every `thing_id`, every uid on the wire, the
  Firestore document paths, the rules, and the `SyncDocWire` envelope stay strings. Existing stored
  data decodes exactly as before, and older builds are unaffected because no old message gains a
  message-typed field they would have to skip.
- No name collides. There is no `ThingId` or `UserId` class, alias, or proto message anywhere in
  the Kotlin sources, the functions' TypeScript, or the proto tree today. Wire compiles every file
  under `core/model/src/commonMain/proto` automatically; the functions' `generate:proto` list is
  explicit and gains `id/ids.proto`.
- What it does create is a **second representation** of the same concept until the grandfathered
  APIs are migrated: `ThingScopeResolver.resolve(thingId: String)` and the shell's `selectedThingId`
  stay strings while this feature's APIs take `ThingId`. The seam is one `.value` or one `ThingId(…)`
  per edge, always at a module boundary, never inside a manager. That is a known half-state, not a
  break, and the sweep that ends it is the same cleanup either way.
- **Nullability changes.** A proto3 `string id` generates a non-null `String` defaulting to `""`; a
  message-typed `DataLogId id` generates `DataLogId?`. Every read of `dataLog.id` therefore needs a
  null decision. The rule here: a record without an id is corrupt, so `DataLogManager` drops such
  rows on read with a logged error and exposes `val DataLog.dataLogId: DataLogId` as a non-null
  accessor for everything above it; the same for `Attachment.data_log_id`, which is null by design on
  every other type and read only through `dataLogIdOrNull()`.
- Id messages are frozen at one field. Wire's equality includes unknown fields, so an id message
  that ever grew a second field would compare unequal across builds; the proto lint in §14 refuses
  any id message with more than `value`.

## 5. Storage and transfer

### 5.1 Bytes

`LocalBlobStore.put(BlobId(newId), gzipBytes, "application/gzip", scope)` where `scope` comes from
`ThingScopeResolver.resolveNow(thingId)`. The blob id is a fresh id, never the record id or the
attachment id, so `blobRefs.ts` collecting attachment ids from a log payload can never name a real
data-log object. Upload is scheduled with the existing `UploadScheduler.scheduleUpload`; on a shared
Thing `BlobUploadDriver` already routes a foreign scope through the broker.

No `QuotaChecker` call. The checker is attachment-form logic with its own caps; the manager does not
use it (PRD R14). `put` takes a whole `ByteArray`, which is fine for the tens of megabytes a data log
can reach; there is no streaming path and none is added.

### 5.2 Compression: `GzipCodec`

```kotlin
expect object GzipCodec {
  fun isAvailable(): Boolean
  fun compress(bytes: ByteArray): ByteArray
  fun decompress(bytes: ByteArray): ByteArray
}
```

- **Android:** `java.util.zip.GZIPOutputStream` / `GZIPInputStream`.
- **iOS:** Apple's Compression framework (`compression_encode_buffer` with `COMPRESSION_ZLIB`, which
  is raw deflate) wrapped in a gzip header and trailer. The trailer needs a CRC-32; `Crc32` moves from
  `feature/export/datamanager` (where it is `internal`) into `core/storage` so both use one copy.
- **Web:** `CompressionStream("gzip")` / `DecompressionStream`, available in every browser the web
  target supports. `isAvailable()` false where the API is missing.

`DataLog.encoding` records what was stored. Readers branch on it, so a platform without gzip stores
`NONE` and everything still works; the ratio on G3X CSV is about 8 to 10 times, so this is worth the
three small actuals. Decompression of a 10 MB raw file is well under 200 ms on a phone.

### 5.3 Download on open

`DataLogManager.ensureLocal(id)` mirrors `AttachmentManager.ensureLocal`: if `blob_object` is
`REMOTE_ONLY`, `scheduleDownload`, emit `Downloading`, then `Done`. `BlobDownloadDriver` verifies the
stored sha256 through `installDownloaded`. The viewer shows the same badges the attachment rows do.

### 5.4 Parsed cache

In memory only for V1: `DataLogCache` holds the last two `DataLogSeriesData` (columnar Float32-style
arrays; a 6-hour log at 1 Hz with 67 numeric series is about 6 MB). Reopen within a session is
instant; a cold open re-parses, which R8 bounds at 3 seconds. A disk cache is deferred until a
measurement says reopen time matters; it would be a sibling directory under `BlobFilesystem`, keyed
by `(id, parser_version)`.

### 5.5 Layout memory

`ChartLayoutStore` keeps the last layout per data log on the device (PRD R31) as a small JSON string
through the same per-platform key-value seam `AppearanceStore` uses (SharedPreferences,
`NSUserDefaults`, `localStorage`). Not synced, not a `CollectionKind`; wiped with `wipeLocalData`.

## 6. Import and parsing

### 6.1 Pipeline: `DataLogImporter.import(thingId, picked: PickedFile): Flow<ImportProgress>`

```
Reading      FileByteReader.readBytes(uri)                         main-safe, IO context
Sniffing     first 4 KB → each DataLogParser.sniff → best DEFINITE   fail: Unrecognized
Parsing      parser.parse(bytes) on Dispatchers.Default (JS: chunked, yield() every 500 rows)
Checking     raw_sha256 in Thing's logs → Duplicate(existingId)
             (source.system_id, start) match → ProbableDuplicate → caller confirms, then continues
Encoding     GzipCodec.compress or NONE
Storing      LocalBlobStore.put → DataLog record → EntityStore.put → scheduleUpload
Done(id)
```

`ImportProgress` is a sealed class: `Reading, Parsing(rowsSoFar), Storing, Done(id: DataLogId),
NeedsConfirmation(existing: DataLogId), Failed(reason)` with `reason` an enum that maps one-to-one onto the
`data_log_import_failed` analytics values (`unrecognized`, `duplicate`, `parse_error`).

Threading follows the house pattern: an injected `CoroutineDispatcher` defaulting to
`Dispatchers.Default` (`SearchTuning.kt:7-10`). On web that is the UI thread, so the Garmin parser
is written as a resumable loop that calls `yield()` every 500 rows; a 21,600-row file yields about 40
times and the spinner keeps animating.

### 6.2 `DataLogParser`

```kotlin
interface DataLogParser {
  val format: DataLogFormat
  val version: Int
  fun sniff(header: ByteArray): Confidence          // NONE, POSSIBLE, DEFINITE
  suspend fun parse(bytes: ByteArray, fileName: String): ParsedDataLog
}
```

`ParsedDataLog` carries the record fields of §4.1 plus `DataLogSeriesData`: `timeSeconds: IntArray`
(elapsed from row 0), one `FloatArray` per numeric or discrete series with `NaN` for empty cells and a
forward-filled twin for drawing, `Array<String?>` for text series, and `lat`/`lon` `DoubleArray` for
the position pseudo-series.

**Garmin parser** (`DATA_LOG_FORMAT_GARMIN_G3X`, and G1000 as a second format sharing the code):

- Sniff: line 1 starts `#airframe_info,`. `product="GDU` gives G3X `DEFINITE`; `airframe_name=`
  gives G1000 `DEFINITE`; either bare prefix gives `POSSIBLE` for the Garmin parser.
- Header: `key="value"` pairs into `DataLogSource`; `aircraft_ident` into `identity`.
- Columns: G3X has long names with `(unit)` on line 2 and short names on line 3; G1000 has a `#`
  units row then short names with leading spaces. Both normalise to `(name, shortName, unit)`; the
  short name is the canonical-registry key (§6.3) because it is the vocabulary the two share.
- Time: `Date` + `Time` columns to a local wall clock; `UTC Offset` column to
  `utc_offset_minutes`; `start` = first row as an `Instant` via `kotlin.time.Instant`
  (never `kotlinx.datetime.Instant`, per conventions). Elapsed seconds per row; `sample_rate_hz` is
  the median reciprocal of row deltas; rows with a non-monotonic clock (a GPS time step) keep their
  index order and the elapsed axis uses row index times the median period in that region.
- Cells: `parseFloat` semantics with a leading `+` accepted (latitude is `+37.08…`); a column is
  `NUMERIC` if any cell parses, `DISCRETE` if its unit is `discrete`, `TEXT` if only non-numeric
  non-empty cells, and dropped from the catalogue if entirely empty.
- Position: `Latitude` plus `Longitude` collapse into one `POSITION` series named from the lexicon
  ("Aircraft Position" on airplane), inserted where latitude was.
- Derived: `airborne` is true when any row has GPS ground speed above 30 kt or height above ground
  above 50 ft (R13), using canonical ids so the rule survives a format change; `end_latitude`
  and `end_longitude` from the last row with a 3D fix; `start_location_ident` from a
  `log_YYYYMMDD_HHMMSS_<ident>.csv` filename.

The parser never allocates per cell beyond the output arrays: it walks the line with an index and
parses numbers in place. Splitting every line into a `List<String>` is what makes a naive parser
slow on a phone.

### 6.3 `CanonicalSeriesRegistry`

A map from source short names to namespaced ids, plus the reverse for presets:

```
E1 RPM → engine[1].rpm        E1 MAP → engine[1].map         E1 OilP → engine[1].oil_press
E1 OilT → engine[1].oil_temp  E1 CHTn → engine[1].cht[n]     E1 EGTn → engine[1].egt[n]
E1 FFlow → engine[1].fuel_flow   E1 %Pwr → engine[1].power_pct   E1 FPres → engine[1].fuel_press
FQty1/2 → fuel.qty[1..2]      Volts1/2 → elec.volts[1..2]    Amps1/2 → elec.amps[1..2]
IAS → flight.ias  TAS → flight.tas  AltGPS → flight.alt_gps  AltP → flight.alt_pressure
AltInd → flight.alt_baro  VSpd → flight.vs  GndSpd → flight.ground_speed  OAT → air.oat
Pitch/Roll → flight.pitch/roll  NormAc/LatAc → flight.g_normal/g_lateral  HDG → nav.heading
Latitude/Longitude → position.lat/lon
```

Unknown columns keep `canonical_id = ""` and are still plottable by raw name. Presets (R30) and the
default series (R21) are lists of canonical ids resolved against the record's catalogue at open time;
a preset series the log lacks is skipped silently. The Dynon parser (later) supplies its own mapping
into the same ids, which is the whole point of the indirection.

### 6.4 Fixture

`docs/datalog/samples/g3x_ground_run.csv`: the real sample with `aircraft_ident` replaced by a
fictitious tail, `system_id` scrambled, and every latitude and longitude offset by a constant so the
track shape survives but the location does not. `GarminParserTest` asserts the catalogue (112
columns, 108 series, 67 numeric, the position collapse), the time base (`14:47:56`, `-07:00`,
255 seconds), `airborne = false`, and exact values for a handful of cells.

## 7. `DataLogManager`

```kotlin
interface DataLogManager {
  fun observe(thingId: ThingId): Flow<List<DataLog>>                       // newest first
  fun observeOne(thingId: ThingId, id: DataLogId): Flow<DataLog?>
  fun import(thingId: ThingId, file: PickedFile, confirmDuplicate: Boolean = false): Flow<ImportProgress>
  fun ensureLocal(thingId: ThingId, id: DataLogId): Flow<DownloadState>
  suspend fun load(thingId: ThingId, id: DataLogId): Result<DataLogSeriesData>   // cache → blob → parse
  suspend fun delete(thingId: ThingId, id: DataLogId): Result<Unit>
  fun observeBlobState(thingId: ThingId, id: DataLogId): Flow<BlobSyncState?>
}
```

`DataLogManagerImpl(scopeResolver: ThingScopeResolver, storeFactory: EntityStoreFactory,
blobs: LocalBlobStore, scheduler: UploadScheduler, importer: DataLogImporter, cache: DataLogCache,
auth: AuthManager, dispatcher)` follows `SquawkManagerImpl`: `store = storeFactory.create(
CollectionKind.DataLog)`, reads through `scopeResolver.resolve(thingId).flatMapLatest { store.observeAll }`,
writes through `resolveNow`. Never the signed-in uid for scope. `delete` tombstones the record;
the blob goes through `TombstoneGc` locally and `onRecordDeleted` remotely, both already built.

Identity: the Thing's tail comes from its spec through `ThingSpecAccess` (the airplane template's
identifier field); `identity_mismatch = identity.isNotBlank() && !identity.equals(tail, ignoreCase)`.
R12's "file it under the other Thing" offer compares against every Thing the user can see and is
a `NeedsConfirmation` variant carrying the candidate Thing id.

## 8. Template, capability, and gating

### 8.1 Protos and assets

- `capabilities.proto`: `SECTION_DATA_LOGS = 5`; update the comment that says the enum mirrors
  `ShellSection`'s first four.
- `lexicon.proto`: `Noun data_log = 19` (airplane: "flight data log" / "flight data logs" /
  short plural "Flight Data"), `string data_log_description = 20` (the section subtitle),
  `EmptyStates.data_log_hint = 11`.
- `airplane.v12.textproto`: `sections` gains `SECTION_DATA_LOGS` after `SECTION_LOGS`; the lexicon
  and empty-state lines above; `git mv`, `version: 12`, `git rm binary/airplane.v11.pb`,
  `./compile-template.sh airplane.v12`, per `templates/README.md:23-42`. No other preset changes.
- `GenericLexicon.LEXICON` gets neutral defaults ("data log" / "data logs" / "Data"; "Upload a data
  log recorded by the thing to chart it."). `LexiconNouns.dataLogNoun` falls back to it.
- `AirplaneTemplateAssetTest.kt:202-208` pins the new five-section order;
  `EveryCapabilityIsConsultedTest` already sees `sections` consulted.

### 8.2 `TemplateRegistry.capabilitiesFor` (PRD R42)

```kotlin
// TemplateRegistry
fun capabilitiesFor(template: ThingTemplate?): Capabilities

// BakedInTemplateRegistry — mirrors lexiconFor at :66-70
override fun capabilitiesFor(template: ThingTemplate?): Capabilities {
  val id = template?.id ?: return CurrentThingTemplate.ALL_ENABLED
  return byId[id]?.capabilities ?: template.capabilities ?: CurrentThingTemplate.ALL_ENABLED
}
```

`CurrentThingTemplate.publish` calls it instead of `value.capabilities`. `resolve()` skips the
`namesUnrecognisedEnumValue` check when `byId` carries the DNA's id, because that build renders
from its own canonical and the stored enum values are never read; degradation remains for ids the
build lacks. `TemplateRegistryTest` gains `capabilitiesResolveByIdLikeTheLexicon` and
`aKnownIdNeverDegradesOnEnumValues`. `scheduleTypesOffered`'s legacy default stays as a belt for
Things with no DNA.

This is the same rule the lexicon has carried since the pivot, and safe for the reason the DNA
refresh script gives: a stored template can only be an unmodified copy of a canonical one while
custom templates are unbuilt. When they land, the rule becomes "canonical wins for an unmodified
copy", decided there.

### 8.3 `AppCapability.isDataLogsSupported` (PRD R43)

`AppCapability` gains the field; each `createAppCapability(isDeveloperBuild)` actual sets it to
`isDeveloperBuild` until launch, then `true`. It gates:

- `perThingSectionsFor(capabilities, appCapability)` drops `DATA_LOGS` when false.
  `core/ui/adaptive` gets a `core/appinfo` dependency and reads the singleton through `koinInject`,
  the way `AdSlot` does.
- The attachment picker's fourth option (§9.2).
- `ShellNavGraph` registers `Screen.DataLogViewer` regardless; an unreachable route is harmless and
  keeps the graph static.
- Notification taps for `data_log` when false fall back to `NotificationTapTarget.Thing`.

### 8.4 Account gate (PRD R40)

`DataLogListViewModel.uiState.uploadGate: UploadGate` is `SignedIn` when
`AuthManager.currentUser` is non-null and not anonymous, else `Guest`. Web never has a guest, so the
guest states are mobile only.

- **Phone:** the FAB opens `LinkAccountPromptSheet` (mock 3b). *Open Settings* calls
  `AdaptiveShellViewModel.openSettings()` and sets `OPEN_LINK_ACCOUNT` on the shell back-stack
  entry's `savedStateHandle`, the same channel `CROSS_SCREEN_SUCCESS_MESSAGE` uses
  (`AdaptiveShellRoute.kt:116-135`); `SettingsScreen` consumes it and opens the existing
  *Link to an account* sheet (mock 3c).
- **Wide:** the list body renders `UploadGateCard` (mock 3a). Its three provider buttons reuse
  `feature/login`'s `UpgradeProviderPicker` if `feature/login` depends on no feature module (verify
  at implementation; it appears to depend on core only). If that dependency is not clean, the card
  carries one *Link to an account* button routing as on the phone, and the provider buttons wait for
  the picker to move into a shared module.

Viewing is never gated: a member opening a shared Thing's data log needs only Thing access.

## 9. The attachment type

### 9.1 Every branch that must learn `DATA_LOG`

| Site | Today | Change |
|---|---|---|
| `AttachmentRow.kt:131-149` `typeIcon`, `subtitle` | `else` = file | icon `ShowChart`; subtitle from the referenced record: "G3X log · 4m 15s · Opens in visualizer", or *Removed* when the record is gone |
| `AttachmentRow.kt:48-60` enabled | null sync state = enabled | pass the DataLog's blob state, not the attachment's |
| `AttachmentSection.kt:40-46` | `syncStates[attachment.id]` | look up by `data_log_id` through a `dataLogs: Map<String, DataLogRowInfo>` parameter |
| `AttachmentFormSection.kt:197-198` remove confirm | warns for saved files | no warning for a reference |
| `AttachmentFormSection.kt:264-269` `toIcon` | `else` | new branch |
| `AttachmentFormSection.kt:305-374` picker sheet | three options | fourth option (§9.2) |
| `PendingAttachment.kt:44-50` `fileCount` | `Local -> true` | new `LocalDataLogRef` variant, excluded; `Saved` excludes `DATA_LOG` |
| `AttachmentFormController.kt:205-212` `remove` | tombstones saved files | a ref drops outright |
| `AttachmentFormController.kt:236-255` `resolveForSave` | three variants | include `LocalDataLogRef` |
| `AttachmentFormController.kt:269-274` `deleteSavedFiles` | `type != LINK` | also `!= DATA_LOG` |
| `LocalFirstAttachmentManagerImpl.kt:117-136` `makeLink` | template | add `makeDataLogRef(dataLogId: DataLogId, name)` |
| `LocalFirstAttachmentManagerImpl.kt:139` `delete` | `LINK` early return | also `DATA_LOG` |
| `LogbookExportArchiveBuilder.kt:1301-1311` `attachmentCell` | `[attachment unavailable]` | "name (flight data log, 4m 15s)" text; bytes are not exported in V1 |
| `AttachmentExportResolver.kt:55` | `type != LINK` | also `!= DATA_LOG` |
| `ExportViewModel.kt:414-418` `exportedBytes` | sums non-LINK | exclude; `size_bytes` is 0 anyway |
| `backend/.../blobRefs.ts:89-94` `blobIds` | `!== LINK` | also `!== DATA_LOG` |
| `core/storage/.../AttachmentRefs.kt:49` `blobIdsIn` | non-LINK ids | also exclude `DATA_LOG` |

`AttachmentType` has no exhaustive `when` anywhere, so none of this is compiler-enforced. A
`feature/attachment` test, `everyTypeBranchHandlesDataLogRef`, exercises `fileCount`, `remove`,
`resolveForSave`, `deleteSavedFiles`, and `delete` with a `DATA_LOG` attachment and asserts no blob
call is made. The backend gets the mirror case in `blob-cleanup.test.ts`.

### 9.2 Picker

`AttachmentPickerSheet` gains `onAttachDataLog` and a fourth `AttachmentPickerOption` shown when
`LocalThingCapabilities.current.sections` contains `SECTION_DATA_LOGS` and `isDataLogsSupported`.
Choosing it swaps the sheet body to `DataLogAttachmentPicker(thingId, recordDate)` exactly as
"Add link" swaps to the URL field: a list of the Thing's data logs from `DataLogManager.observe`,
same-day rows annotated, rows already attached elsewhere dimmed but selectable, a radio selection,
*Upload log file* (runs the importer, then selects the result) and *Attach*. The composable lives in
`feature/datalog/viewing`; `feature/attachment/viewing` cannot depend on it, so the sheet takes the
body as a slot lambda supplied by the form screens, which already depend on both.

The three form ViewModels add `attachDataLog(id: DataLogId, name)` calling
`controller.addDataLogRef(id, name)`, a non-suspending sibling of `addLink` with no quota and no
error case.

### 9.3 Opening from a row

The three tap handlers branch before `attachmentOpener.open`:
`attachment.dataLogIdOrNull()?.let(onOpenDataLog)`, where the tab already receives cross-navigation lambdas
(`LogsTab.kt:107-108`). The handler dismisses the detail sheet and navigates to
`Screen.DataLogViewer.createRoute(thingId, dataLogId)`.

`ThingOverviewViewModel` observes `DataLogManager.observe(ThingId(thingId))` and exposes
`dataLogs: Map<DataLogId, DataLogRowInfo>` so rows can render subtitles and *Removed*. `feature/thing/dashboard` depends on `feature/datalog/model`,
`datamanager`, `sharedassets`, `viewing`, never `update`.

## 10. Section, list, and shell

### 10.1 `ShellSection.DATA_LOGS`

`DATA_LOGS(Icons.Filled.ShowChart)` between `LOGS` and `SETTINGS`. Switches to extend: `label()`
returns `LexiconFormatter.shortPlural(LocalThingLexicon.current.dataLogNoun)`; `title()` the title-case
plural; `Section.toShellSection()` maps `SECTION_DATA_LOGS`; `DEFAULT_PER_THING_SECTIONS` stays at
four (fail-open is for missing declarations, and a template that names no sections is not an
aeroplane with data logs); `ThingSectionContent.kt:135-179` FAB and `:287-293` scroll routing;
`AdaptiveShellViewModel.kt:216-222` tab mapping (`"datalogs"`); `ShellBrowserHistory.kt:195-215`
needs nothing, it matches enum names; `PerThingSectionsTest` gains the capability-off case.

### 10.2 Body and FAB

`ShellSectionBody` renders `DataLogSectionContent(thingId: ThingId, onOpen: (DataLogId) -> Unit,
onNavigateToSettings)` from `feature/datalog/viewing`. `DataLogListViewModel(thingId: ThingId)` combines `DataLogManager.observe`, the
upload gate, and `ImportProgress` of any in-flight imports into `uiState`. Rows show date and route
or *Ground run*, start time, duration in mono, product, series count, the attached-to line, and an
inline progress or error row during import (R34, R35). A pending scroll target from a notification
highlights the row like the other sections.

`ShellSectionFab` for `DATA_LOGS` renders *Upload Log* with `rememberFilePicker` (no `accept`
filter is possible today; the sniffer rejects wrong files fast). On `Guest` it opens the prompt
sheet instead. Wide layouts show the same button in the header; the mock's dashed drop zone is
not drawn until drag-and-drop exists (§13.3), so the section never shows a target that does nothing.

### 10.3 Variable-width pill (PRD R2b)

`FloatingPillNavigationBar.kt:143-149`: the unselected branch replaces its `Text` with
`Icon(item.icon, contentDescription = item.label, tint = onSurfaceVariant, Modifier.size(20.dp))`.
`label` already documents itself as the accessibility name (`:48`). Horizontal padding stays 12 dp,
giving a 44 dp target. The doc block at `:55-68` that reasons about four labelled items is rewritten
for five icon items plus one chip. `FloatingPillNavBarHeight` is unchanged. This changes every
preset's bar, which is the PRD's intent; a screenshot in the PR shows a 320 dp phone.

### 10.4 Route

```kotlin
const val DATA_LOG_ID = "dataLogId"
data object DataLogViewer : Screen("data_log/{$THING_ID}/{$DATA_LOG_ID}") {
  fun createRoute(thingId: ThingId, dataLogId: DataLogId) = "data_log/${thingId.value}/${dataLogId.value}"
}
```

Registered as a plain `composable` in `ShellNavGraph.settingsDetailRoutes`' style with
`navArgument`s, on both hosts through the shared graph. It is a full-screen root, not a
`selectionDialog`: charts need the whole viewport and their own gesture ownership.

## 11. Visualizer

### 11.1 State

`DataLogViewerViewModel(thingId: ThingId, dataLogId: DataLogId)` owns everything the user can change,
per the hoist-to-ViewModel rule: `ChartLayout(panes: List<Pane(id: PaneId, series: List<SeriesKey>)>,
targetPane: PaneId?)`,
`view: TimeWindow?` (null = full), `cursorT: Double?`, `sidebarTab`, `query`. It loads through
`DataLogManager.ensureLocal` then `load`, exposing `uiState: Loading(download progress) | Ready(record,
data, layout, view, cursor) | Failed`. Layout edits debounce into `ChartLayoutStore`. Delete goes
through the manager and pops the route with the shell snackbar message on the back-stack entry.

### 11.2 Drawing

One `Canvas` per pane inside a `LazyColumn` of panes (`ChartPane`). Per frame, for each numeric
series in the pane: the visible index range `[i0, i1]` from the window; bucket width
`(i1 - i0) / widthPx`; per bucket a min and a max from the forward-filled array; a `Path` with two
points per pixel column. That is O(rows) per series with no allocation beyond the path, well under a
millisecond for 21,600 rows, so it runs synchronously in a `remember(window, width, seriesKeys)`
block; no worker is needed even on web.

Unit groups: series in a pane group by `unit`; each group fits `[min, max]` of the *visible* range
with 8% padding; the first group scales to the left axis, the second to the right, the rest draw on
their own scale and read only in chips (R22). Grid at quartiles; axis labels in JetBrains Mono at
10 sp in the group's first series colour.

The shared `TimeAxis` under the pane list draws ticks from a step ladder `5, 10, 30, 60, 120, 300,
600, 900, 1800, 3600` seconds chosen so labels are at least 72 dp apart, edge labels shifted inward
(R23a). The cursor is a 1 dp `tertiary` line across every pane with the time in a `tertiary`
pill on the axis; chips show `data[series][idxAt(cursorT)]`.

### 11.3 Series colour (PRD R24a)

`SeriesPalette` in `feature/datalog/viewing`: eight colours per theme, chosen for contrast on the
theme's `surface`. Dark starts from the mock's set (`#A7C8FF`, `#FFBA4E`, `#81C784`, `#FF8A80`,
`#4DD0E1`, `#CE93D8`, `#FFCA28`, `#BAC8E0`); light is its 40-tone counterpart (`#1A5FAE`,
`#7A5200`, `#276B39`, `#B3261E`, `#00696F`, `#6A3F9D`, `#8B5E00`, `#525E72`). Index is
`fixedIndex[canonical_id] ?: fnv1a(seriesKey) % 8`, with the fixed table covering the twenty most
common engine and flight ids so RPM, MAP, oil, CHT, EGT, fuel flow, altitude and airspeed never
collide. Colour depends only on the series and `isSystemInDarkTheme()`, never on pane position.
The palette is verified against both surfaces with the dataviz contrast check before merge.

The chart surface follows the theme: `surface` for the pane, `outlineVariant` for grid and borders,
`onSurfaceVariant` for labels. `tertiary` (amber) is reserved for cursor and target-pane border.

### 11.4 Gestures (PRD R23)

One `Modifier.pointerInput(Unit) { awaitEachGesture { ... } }` per pane, plus one
`Modifier.onPointerEvent(PointerEventType.Scroll)`.

```
awaitEachGesture:
  down = awaitFirstDown(requireUnconsumed = false)
  loop awaitPointerEvent:
    pressed pointers == 1:
      before slop: awaitHorizontalTouchSlopOrCancellation → if vertical slop wins, return
                   without consuming (the LazyColumn scrolls the pane stack)
      after slop: consume; brush = [downX, currentX]; cursor follows
      up: if |brush| > 8 dp → view = brushToWindow(brush) else cursor tap
    pressed pointers == 2:
      cancel brush; consume both
      per event: centroid dx → pan(dx / widthPx * span);
                 zoom = distance / lastDistance → zoomAround(centroidX, zoom)
    mouse hover (no button): cursor follows, nothing consumed

onPointerEvent(Scroll):
  mods = event.keyboardModifiers; d = change.scrollDelta
  ctrl or meta pressed          → zoomAround(pointerX, 1 + d.y * 0.1); consume
  shift pressed                 → pan(d.y); consume
  |d.x| > |d.y|                 → pan(d.x); consume          (trackpad swipe, horizontal wheel)
  else                          → leave unconsumed          (page scrolls)
```

`pan` clamps to `[0, duration]` and is a no-op at full zoom-out; `zoomAround` clamps the span to
`[5 s, duration]` and sets `view = null` at full span (R23). Trackpad pinch arrives as ctrl+wheel in
browsers and as a two-pointer gesture on touch, so both paths reach `zoomAround`.

Risk: on web the browser's own ctrl+wheel page zoom fires unless the canvas consumes the event with
`preventDefault`. Compose for Web does this for events it consumes; the PR verifies in Chrome,
Safari, and Firefox, and if a browser still zooms the page, the web host adds a `wheel` listener
on the canvas element that calls `preventDefault` when the pointer is over a pane.

### 11.5 Chips and drag between panes

`PaneHeaderChips` renders one chip per series (swatch, short name, value, unit, remove). Chips drag
with Compose's `dragAndDropSource` / `dragAndDropTarget`, available on all three CMP targets for
in-app transfer; the payload is `seriesKey:fromPaneId`. Drop targets: each pane and the *New pane*
strip. Moving onto a pane whose kind (chart vs map) differs spawns a new pane, matching the mock's
`placeKey`. The sidebar rows are sources too; tap adds to the target pane.

### 11.6 Map pane (R29)

`MapPane` draws raster tiles fetched through Coil (`rememberAsyncImagePainter`, house pattern) laid
out with Web Mercator math, the track as a `Path`, the cursor dot in `tertiary`. Zoom level fits the
track's bounding box to 80% of the pane. Provider is a `MapTileProvider(urlTemplate, attribution)`
value bound in Koin; PRD decision 7 says free and simple, so the default is a raster provider with a
free tier and required attribution shown in the pane corner, and Google Maps Platform is acceptable
through its raster Map Tiles API. Web needs `ktor-client-js` added to `feature/datalog/viewing`'s
`jsMain` for Coil's fetcher; Coil has no disk cache on JS, so memory only there. Failed tiles leave
the dark surface and the track still draws (R29's degrade rule).

### 11.7 Sidebar and narrow layouts

`SeriesSidebar` is 300 dp on layouts with side navigation and a right-hand drawer below that,
toggled by the *tune* action. The drawer is Material's `ModalNavigationDrawer` laid out right-to-left
(a drawer is not one of the scoped popups, but its content still sits inside a `TextSelectionLayer`
because it hosts a text field). Tabs
*Series* (search, range, add or check) and *Flight* (record facts, source facts, identity notice).
Narrow header collapses *Upload* and *Reset* to icons; pane height 170 dp on compact, 150 dp
otherwise.

### 11.8 Presets and default layout (R21, R30)

`ChartPresets` in `feature/datalog/model`: `Engine`, `Fuel`, `Flight`, `Electrical`, each a list of
pane lists of canonical ids. Default layout on first open: one pane with the template's default
series (`engine[1].rpm` on airplane, declared as a lexicon-adjacent template field later; hard-coded
per format in V1 with a TODO to move into the template when automotive arrives), or the first numeric
series.

## 12. Notifications (PRD R39)

Server: `RECORD_TYPE.DATA_LOG = "data_log"`, `recordTypeForKind("data_log")`,
`thingTabForRecordType(DATA_LOG) = "datalogs"`, and a `recordTitleOf` branch decoding `DataLog` to
`"<date> · <start_location_ident or Ground run>"` (the switch is exhaustive, so TypeScript flags the
gap). The body key is already `notification_n1_body_record_{created|updated|deleted}`; layout edits
never write the record, so only import and delete notify. Test: `dataLogEdit()` helper and the
create and delete body-key assertions mirroring `notification-fanout.test.ts:253-282`.

Client: `PushPayload.noun()` and `sectionTitle()` branches for `"data_log"` using `dataLogNoun`;
`parseTapTarget` prefix `data_log`; `NotificationTapTarget.DataLog(thingId, dataLogId: DataLogId)`
(the existing targets hold a `String` thing id; this one matches them for `thingId` and types its own
id, until the sealed interface is migrated to `ThingId` as a whole);
`NotificationTapRouter` `wingslog://…/data_log/{thingId}/{id}`; `AdaptiveShellViewModel.onNotificationTap`
selects the Thing, `DATA_LOGS`, and sets the pending scroll id; `WebForeignWriteDetector` gets the
kind for its web-only detector.

## 13. Cross-cutting

### 13.1 Analytics

`Name`: `DATA_LOG_IMPORTED("data_log_imported")`, `DATA_LOG_IMPORT_FAILED`, `DATA_LOG_OPENED`,
`DATA_LOG_LAYOUT_APPLIED`. `Param` additions: `DURATION_BUCKET`, `SIZE_BUCKET`, `SERIES_COUNT`,
`PRESET`; `FORMAT`, `SOURCE`, `REASON` exist. Four `ThingScopedEvent` data classes, appended to
`everyThingScopedEventCarriesTemplateId`. Logged from `DataLogListViewModel` (import, failure) and
`DataLogViewerViewModel` (open, preset) through the injected `AnalyticsManager`.

### 13.2 Ads (PRD R44a) — mobile only

`AdSurface.DATA_LOGS("data_logs")`, with the enum's doc comment updated to admit one fixed slot.
`AdSlot` gains `size: AdUnitSize = LARGE_BANNER`; the viewer calls `AdSlot(DATA_LOGS, 0, size = BANNER)`
in the sidebar footer on tablet layouts and under the *New pane* strip on phones, inside
`if (showAds)` from `AdsManager.shouldShowsAds()`. Web is out of scope by requirement, and
`AdView.js.kt` is already a no-op with `isAdsSupported = false`, so no web code is touched.

### 13.3 Drag-and-drop (PRD R2c, P2, not in V1)

Nothing in the repo handles external drag-and-drop, and `web_attachments_design.md:30` listed it as
a non-goal. When it is built it is one shared mechanism, not a data-log feature: a
`FileDropTarget` in `feature/attachment/viewing` that exposes dropped files as `List<PickedFile>`
through the same `WebPickedFileRegistry` path the picker uses, consumed by `AttachmentFormSection`
(record forms and the add-attachment sheet) and by `DataLogSectionContent` alike. On web it is a
`document`-level `dragover`/`drop` listener in `webApp`; on Android and iOS tablets it is Compose's
`dragAndDropTarget` for external content where the platform supports it. Sequenced after V1.

### 13.4 Strings

New strings in `feature/datalog/sharedassets`; the lexicon supplies the noun and the description, so
frames use `%1$s` substitution. Every new string is appended to `string_snapshot.tsv` in the same
commit (`StringSnapshotTest` fails on `added` otherwise), with lexicon-bearing ones registered in
`LEXICON_ARGS`. No `\'` anywhere; popups from `core.ui.common.compose`.

## 14. Tests

- **Parser:** `GarminParserTest` on the fixture (§6.4); header sniffing table for G3X, G1000, Dynon
  headers and three wrong files; malformed rows; a 20,000-row synthetic file under a time budget.
- **Registry:** every canonical id round-trips; presets resolve against the fixture catalogue.
- **Ids:** a compile-time check by construction; plus a small test that `Attachment.data_log_id` is
  null for every other type, that route round-trips preserve `ThingId` and `DataLogId`, and a proto
  lint in `core/model` tests that every `string` field on a new message whose name ends in `_id` or
  is `id` fails (grandfathered messages listed explicitly).
- **Importer / manager:** MockK `LocalBlobStore`, `EntityStoreFactory`, `UploadScheduler`;
  duplicate by `raw_sha256`; probable duplicate needs confirmation; scope from the resolver, never
  the uid; gzip round-trip on Android host tests; `NONE` path when `isAvailable()` is false.
- **Storage:** `CollectionKindCoverageTest`, `AttachmentRefs` for DataLog and for `DATA_LOG` refs,
  `TombstoneGc` keeps a referenced blob.
- **Template:** `capabilitiesResolveByIdLikeTheLexicon`, `aKnownIdNeverDegradesOnEnumValues`,
  the airplane asset pins five sections, `PerThingSectionsTest` with the flag off and the section
  absent.
- **Attachment:** `everyTypeBranchHandlesDataLogRef` (§9.1); form controller `addDataLogRef`
  survives `resolveForSave`.
- **Chart model:** pure functions for decimation (min/max per bucket on a known array), unit
  grouping and axis assignment, tick ladder, `zoomAround` and `pan` clamping, brush-to-window,
  colour index stability across pane moves and add/remove.
- **ViewModels:** viewer load path through download states; list gate for guest versus signed-in;
  analytics events fire with the template id.
- **Backend:** `blob-cleanup.test.ts` DataLog delete reclaims, log delete does not;
  `notification-fanout.test.ts` create and delete for `data_log`; `sharing-rules.test.ts` member
  write to `data_log`.
- **Manual:** the three gestures on Android touch, iOS touch, Chrome trackpad and mouse, Safari;
  ctrl+wheel does not zoom the page; a 6-hour synthetic log pans at frame rate on a mid-range phone.

## 15. Sequencing

| PR | Contents | Gate |
|---|---|---|
| 1 | Protos, `CollectionKind.DataLog`, `AttachmentRefs`, sync list, rules, backend proto and `blobRefs.ts`, `capabilitiesFor`, airplane v12, `isDataLogsSupported`, `ShellSection.DATA_LOGS` with an empty body | tests green; developer builds show an empty section |
| 2 | Garmin parser, registry, `GzipCodec`, importer, manager, fixture | fixture tests; import from a developer build lists the record and it syncs |
| 3 | Section list, FAB, guest gate, pill change, snapshot rows | mocks 1a, 2a, 3b, 3c on phone and web |
| 4 | Viewer route, panes, decimation, gestures, chips, sidebar, colours | mock 1c reproduced; gesture matrix passed |
| 5 | Attachment type end to end (§9) | mock 1b; `everyTypeBranchHandlesDataLogRef` |
| 6 | Map pane, presets, layout memory, clock axis, R12, R13 | mock 1d |
| 7 | Notifications, analytics, mobile ad slot | fan-out tests; events visible in DebugView |
| 8 | Flip `isDataLogsSupported` on every host; release notes | V1 |
| 9+ | G1000 sniff and mapping; Dynon parser with the channel-mapping prompt; shared drag-and-drop (§13.3) | per fixture |

Each PR runs `lint`, `testDebugUnitTest`, `testAndroidHostTest` locally (CI's Kotlin build is
manual-dispatch) and the post-task cleanup pass over changed `.kt` files.

## 16. Risks

- **Old builds and the new enum.** Until every user is on a build with `capabilitiesFor`, a Thing
  created from airplane v12 renders degraded on an older build (`ENUM_FIELDS` includes field 8).
  PR 1 ships `capabilitiesFor` first and the template bump in the same release, so the window is the
  normal update lag, and the degraded state is the designed one with its update prompt.
- **Web memory.** A 20 MB CSV becomes roughly 20 MB of string plus 6 MB of columns plus the gzip
  buffer, all on one thread. Acceptable for the browsers targeted; the parser releases the string
  after the columns are built.
- **iOS compression interop.** The Compression framework binding is a small cinterop; if it stalls,
  iOS stores `NONE` behind `isAvailable()` and nothing else changes.
- **Browser page zoom on ctrl+wheel** (§11.4). Verified per browser in PR 4.
- **Template version collision.** Two branches bumping airplane to v12 lose one silently;
  `TemplateAssetDirectoryTest` catches it, and PR 1 is the only one that touches the asset.
- **Ad surface doc drift.** `AdSurface`'s comment promises list-only ads; updating it in PR 7 keeps
  the promise honest rather than silently broken.
- **Coil on web.** Tiles need the JS ktor engine on the viewing module; if Coil's JS fetcher
  misbehaves, the map pane on web falls back to track-only until fixed, which R29 permits.

## 17. Task breakdown

One row per unit of work small enough to review alone. `PR` matches §15. Size: **S** under half a
day, **M** one to two days, **L** three days or more. `Needs` lists task ids that must land first.
Rows map one-to-one onto sub-issues when the project board is created; PRs 1 to 8 are the V1 epic,
PR 9+ the formats epic.

| Id | PR | Task | Where | Size | Needs | PRD |
|---|---|---|---|---|---|---|
| T01 | 1 | `ids.proto` with `ThingId`, `DataLogId`, `UserId`; proto lint test for bare string ids on new messages | `core/model` proto + test | S | — | §4.4 |
| T02 | 1 | `data_log.proto` (`DataLog`, `DataLogSource`, `DataLogSeries`, three enums); `ATTACHMENT_TYPE_DATA_LOG` and `Attachment.data_log_id` | `core/model` proto | S | T01 | R15, §4.1–4.2 |
| T03 | 1 | `CollectionKind.DataLog`, coverage test, codec registration, `AttachmentRefs.of(DataLog)` and the `DATA_LOG` exclusion in `blobIdsIn` | `core/storage` | S | T02 | R16, R17 |
| T04 | 1 | `PER_THING_KINDS` entry; `isSharedAircraftKind` rules entry; `sharing-rules.test.ts` member write | `feature/sync/data`, `backend/firebase` | S | T03 | R37 |
| T05 | 1 | Backend proto generation for `ids.proto` and `data_log.proto`; `blobRefs.ts` schema, `schemaCanOwnBlobs`, `blobIdsInPayload`, `DATA_LOG` exclusion in `blobIds()`; `blob-cleanup.test.ts` cases | `backend/firebase/functions` | M | T02 | R19, §4.3 |
| T06 | 1 | `SECTION_DATA_LOGS`; `Lexicon.data_log`, `data_log_description`, `EmptyStates.data_log_hint`; `GenericLexicon` defaults; `LexiconNouns.dataLogNoun` | `core/model` proto, `core/template` | S | — | R42, R45 |
| T07 | 1 | `airplane.v12.textproto` with the section and lexicon lines; compile; asset test pins five sections | `core/template/templates` | S | T06 | R42 |
| T08 | 1 | `TemplateRegistry.capabilitiesFor`; `CurrentThingTemplate` uses it; `resolve()` skips the enum check for a known id; registry tests | `core/template` | M | T06 | R42 |
| T09 | 1 | `AppCapability.isDataLogsSupported`, three actuals set to `isDeveloperBuild`; `core/ui/adaptive` depends on `core/appinfo` | `core/appinfo`, hosts | S | — | R43 |
| T10 | 1 | `ShellSection.DATA_LOGS`: enum, `label`/`title`, `toShellSection`, `perThingSectionsFor` with the flag, `PerThingSectionsTest`; empty section body and no-op FAB | `core/ui/adaptive`, `feature/thing/dashboard` | M | T07, T08, T09 | R1 |
| T11 | 1 | `feature/datalog` module skeleton: six submodules, Gradle, `dataLogModule`, `settings.gradle.kts`, `CommonAppModules` (use the scaffolder) | `feature/datalog`, `core/di` | S | — | §3 |
| T12 | 2 | `DataLogParser` interface, `Confidence`, `HeaderSniffer`; `ParsedDataLog` and `DataLogSeriesData` models | `feature/datalog/model`, `datamanager` | S | T11 | §6.2 |
| T13 | 2 | Anonymised G3X fixture in `docs/datalog/samples/` | docs | S | — | §6.4 |
| T14 | 2 | Garmin parser: header, three-line columns, time base, cell typing, position collapse, allocation-free row walk, `yield()` every 500 rows | `feature/datalog/datamanager` | L | T12, T13 | R6, R8, §6.2 |
| T15 | 2 | `CanonicalSeriesRegistry` and the fixed-index table; derived `airborne`, identity mismatch, `start_location_ident`, end position | `feature/datalog/datamanager` | M | T14 | R11, R13, R36, §6.3 |
| T16 | 2 | `GzipCodec` expect/actual (Android, iOS with `Crc32` moved to `core/storage`, web) with `isAvailable()`; round-trip tests | `feature/datalog/datamanager`, `core/storage`, `feature/export` | M | T11 | R9, §5.2 |
| T17 | 2 | `DataLogImporter` pipeline with `ImportProgress`, duplicate and probable-duplicate checks | `feature/datalog/datamanager` | M | T14, T15, T16 | R7, R10 |
| T18 | 2 | `DataLogManager` and impl: observe, import, `ensureLocal`, `load` with `DataLogCache`, delete, blob state; scope through the resolver; MockK tests | `feature/datalog/datamanager` | M | T03, T17 | R16–R19, §7 |
| T19 | 2 | `GarminParserTest` on the fixture; sniff table; 20,000-row synthetic timing test | tests | S | T14 | R8 |
| T20 | 3 | `DataLogListViewModel` and `DataLogSectionContent`: rows, empty state, inline import progress and errors, pending scroll target | `feature/datalog/viewing` | M | T18, T10 | R2, R34, R35 |
| T21 | 3 | `ShellSectionFab` for `DATA_LOGS` with `rememberFilePicker`; wide-layout header button | `feature/thing/dashboard` | S | T20 | R2 |
| T22 | 3 | Guest gate: `UploadGate` state, phone prompt sheet, `OPEN_LINK_ACCOUNT` hand-off to Settings, wide `UploadGateCard` (provider picker reuse or single CTA) | `feature/datalog/viewing`, `feature/shell`, `feature/settings` | M | T20 | R40 |
| T23 | 3 | Variable-width pill: icon-only unselected items, doc block rewrite, 320 dp screenshot | `core/ui/adaptive` | S | — | R2b |
| T24 | 3 | Strings for the section and picker; `string_snapshot.tsv` rows and `LEXICON_ARGS` entries | `feature/datalog/sharedassets`, `core/template` test resources | S | T20 | R45 |
| T25 | 3 | Phone search action on the list (filter by date, identifier, attached title) | `feature/datalog/viewing` | S | T20 | R2a |
| T26 | 4 | `Screen.DataLogViewer` route and registration; `DataLogViewerViewModel` load path through download states; delete with snackbar | `core/nav`, `feature/shell`, `feature/datalog/update` | M | T18 | R20, §10.4, §11.1 |
| T27 | 4 | Chart model as pure functions: decimation, unit grouping and axis assignment, tick ladder, `zoomAround`/`pan` clamping, brush-to-window; unit tests | `feature/datalog/model` | M | T12 | R22, R23, R23a, R28 |
| T28 | 4 | `ChartPane` Canvas drawing, `TimeAxis`, cursor line and pill, target-pane border | `feature/datalog/viewing` | L | T27 | R20–R23 |
| T29 | 4 | Gesture state machine: one-pointer brush with vertical pass-through, two-pointer pan and pinch, scroll handler with modifiers; per-browser ctrl+wheel check | `feature/datalog/viewing` | L | T28 | R23 |
| T30 | 4 | `PaneHeaderChips` with drag between panes and the *New pane* target; mixed-kind spawn rule | `feature/datalog/viewing` | M | T28 | R21, R24 |
| T31 | 4 | `SeriesPalette` light and dark, fixed-index table, hash fallback; contrast check; colour stability test | `feature/datalog/viewing` | S | T15 | R24a |
| T32 | 4 | `SeriesSidebar` (Series and Flight tabs, search, range, target-pane hint) and the compact right-hand drawer; icon-only header on narrow | `feature/datalog/viewing` | M | T28 | R25–R27 |
| T33 | 5 | `PendingAttachment.LocalDataLogRef`; `AttachmentFormController.addDataLogRef`, `remove`, `resolveForSave`, `deleteSavedFiles`; `makeDataLogRef`; `delete` early return; `everyTypeBranchHandlesDataLogRef` test | `feature/attachment/model`, `datamanager` | M | T02 | R3, R5, §9.1 |
| T34 | 5 | `AttachmentRow` icon and subtitle, *Removed* state; `AttachmentSection` keyed by `data_log_id`; form-section icon and no-confirm removal | `feature/attachment/viewing` | S | T33 | R4 |
| T35 | 5 | Picker sheet fourth option behind capability and flag; `DataLogAttachmentPicker` body as a slot; *Upload log file* inside the picker | `feature/attachment/viewing`, `feature/datalog/viewing`, three form screens | M | T33, T18 | R3, §9.2 |
| T36 | 5 | Three form ViewModels `attachDataLog`; three tap handlers branch to the viewer route; `ThingOverviewViewModel.dataLogs` | `feature/logs`, `feature/tasks`, `feature/squawk`, `feature/thing/dashboard` | M | T35, T26 | R4, §9.3 |
| T37 | 5 | Export and backend exclusions: `attachmentCell`, `AttachmentExportResolver`, `exportedBytes` | `feature/export` | S | T02 | §9.1 |
| T38 | 6 | `MapPane`: tile provider binding, Mercator layout, track path, cursor dot, attribution, web ktor engine | `feature/datalog/viewing` | L | T28 | R29 |
| T39 | 6 | `ChartPresets` and default layout; preset chips in the sidebar | `feature/datalog/model`, `viewing` | S | T31, T32 | R21, R30 |
| T40 | 6 | `ChartLayoutStore` per-device layout memory; clock-time axis toggle | `feature/datalog/datamanager`, `viewing` | S | T26 | R31, R32 |
| T41 | 6 | R12 "file it under the other Thing" confirmation | `feature/datalog/datamanager`, `viewing` | S | T17 | R12 |
| T42 | 7 | Server: `RECORD_TYPE.DATA_LOG`, `recordTypeForKind`, `thingTabForRecordType`, `recordTitleOf`; fan-out tests | `backend/firebase/functions` | S | T05 | R39 |
| T43 | 7 | Client notifications: `noun()`/`sectionTitle()`, `parseTapTarget`, `NotificationTapTarget.DataLog`, router, shell tap routing, web detector | `feature/notifications`, `feature/shell` | M | T10, T42 | R39 |
| T44 | 7 | Analytics: four `Name`s, four `Param`s, four events, taxonomy test list, ViewModel logging | `core/analytics`, `feature/datalog` | S | T20, T26 | R46 |
| T45 | 7 | `AdSurface.DATA_LOGS`, `AdSlot` size parameter, placement in sidebar footer and under *New pane* on Android and iOS | `feature/ads`, `feature/datalog/viewing` | S | T32 | R44a |
| T46 | 8 | Flip `isDataLogsSupported` on every host; release notes; `NEW` pill | hosts, `feature/datalog/viewing` | S | T20–T45 | R43 |
| T47 | 9+ | G1000 sniff, units row, short-name mapping; fixture; tests | `feature/datalog/datamanager` | M | T14 | §7 |
| T48 | 9+ | Dynon SkyView parser; thermocouple channel-mapping prompt and per-unit storage | `feature/datalog/datamanager`, `viewing` | L | T14 | §7 |
| T49 | 9+ | Shared drag-and-drop `FileDropTarget` for attachments and data logs (web document listener, tablet `dragAndDropTarget`) | `feature/attachment/viewing`, `webApp` | M | T21, T35 | R2c |
| T50 | 9+ | V2 server destination lookup design note (server write into a client-owned record) | docs | S | T42 | R36 |

Critical path to a usable developer build: T01 → T02 → T03 → T11 → T12 → T14 → T17 → T18 → T20 → T26 →
T27 → T28 → T29. Everything in PRs 1 and 2 except T14 is parallelisable; T23 and T31 have no
dependencies and can land any time.
