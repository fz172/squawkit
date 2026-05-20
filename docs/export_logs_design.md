# Design Doc: Logbook Export

**PRD:** `docs/export_logs_PRD.md`
**Status:** Draft
**Last updated:** 2026-05-18

---

## 1. Overview

A user-triggered, on-device export pipeline that reads aircraft, logs, tasks, squawks, and technicians from the local R1 entity store, fans them into a set of CSV files arranged like paper logbook volumes, and packages the result into a ZIP saved to the platform's user-visible filesystem (Files / Downloads).

Pipeline at a glance:

```
ExportSelectionScreen (update/)
        │  user taps Export
        ▼
ExportViewModel.startExport(request)
        │
        ▼
ExportManager.exportLogs(request)         ── Flow<ExportProgress>
        │
        ├─ for each selected aircraft:
        │     LogbookExportAggregator.collect(aircraftId, dateRange)
        │       → AircraftBundle (Aircraft, logs, tasks, squawks, technicians)
        │     LogbookExportWriter.writeAircraftFolder(bundle, zip)
        │
        ├─ writeFleetSummary(zip)         ── only if ≥2 aircraft
        ├─ writeReadme(zip)
        │
        ▼
ZipFileWriter.close() → atomic rename to ExportDestination
        │
        ▼
emit ExportProgress.Success(path, sizeBytes)
```

The whole pipeline lives in **`feature/export/`** as a new canonical-layout module. No changes to protos, Firestore schema, or sync engine. Two tiny touch points: a new row in Settings and one new entry in the navigation `Screen` sealed class.

---

## 2. Module Layout

```
feature/export/
  datamanager/
    src/commonMain/kotlin/dev/fanfly/wingslog/feature/export/datamanager/
      ExportManager.kt                  # interface
      ExportRequest.kt                  # request/progress data classes
      impl/
        ExportManagerImpl.kt            # orchestrates aggregator + writer + zip
        LogbookExportAggregator.kt      # snapshots data per aircraft
        LogbookExportWriter.kt          # CSV row composition + writer dispatch
        CsvWriter.kt                    # RFC 4180 escaper (commonMain)
        ZipFileWriter.kt                # expect class
        ExportDestination.kt            # expect fun resolveDestination
        ReadmeRenderer.kt               # static + per-export README composer
      di/ExportDataManagerModule.kt
    src/androidMain/kotlin/.../impl/
      ZipFileWriter.android.kt          # java.util.zip.ZipOutputStream
      ExportDestination.android.kt      # MediaStore Downloads/Hopply/
    src/iosMain/kotlin/.../impl/
      ZipFileWriter.ios.kt              # pure-Kotlin store/deflate writer
      ExportDestination.ios.kt          # ~/Documents/Hopply/
    src/test/kotlin/.../impl/
      CsvWriterTest.kt
      LogbookExportWriterTest.kt        # golden CSV tests
      LogbookExportAggregatorTest.kt    # fixed-clock + mocked managers

  sharedassets/
    src/commonMain/composeResources/values/strings.xml
    src/commonMain/composeResources/values/strings.xml
    src/commonMain/kotlin/.../sharedassets/   # (no Kotlin yet — resources only)

  update/
    src/commonMain/kotlin/dev/fanfly/wingslog/feature/export/update/
      ExportSelectionRoute.kt           # @Composable destination wrapper
      ExportSelectionScreen.kt          # stateless screen
      compose/
        AircraftSelectionList.kt
        DateRangeSegment.kt
        ExportProgressView.kt
        ExportSuccessView.kt
      viewmodel/
        ExportViewModel.kt
        ExportUiState.kt                # sealed class
        ExportUiModule.kt               # Koin viewmodel module
    src/test/kotlin/.../viewmodel/
      ExportViewModelTest.kt
```

### Dependency direction

```
update        →  :datamanager, :sharedassets,
                 core:ui, core:model, core:appinfo,
                 feature:fleet:datamanager      (aircraft list for selection)
datamanager   →  core:model, core:storage, core:datetime,
                 feature:logs:datamanager,
                 feature:tasks:datamanager,
                 feature:squawk:datamanager,
                 feature:technician:datamanager,
                 feature:fleet:datamanager
sharedassets  →  Compose resources only
```

Per CLAUDE.md, `update` depending on multiple sibling features' `datamanager` modules is unusual but acceptable here — export is by its nature a multi-feature aggregator. Datamanager modules already follow the rule of never depending on UI / sharedassets.

### Settings touch point

`feature/settings/SettingsScreen.kt`: add one row, between the existing **Backup & Sync** and **Feature Lab** rows. Uses `Icons.Default.FileDownload`. String resource `export_logs` (defined in `feature:settings:sharedassets`-equivalent — settings is flat so we put it in `feature:export:sharedassets` and import).

### Navigation touch point

`core/ui/.../navigation/Screen.kt`: add

```kotlin
data object ExportLogs : Screen("export_logs")
```

`composeApp/AppEntry.kt`: register

```kotlin
composable(Screen.ExportLogs.route) {
  ExportSelectionRoute(navController = navController)
}
```

No deep-link params; the screen owns its own selection state from a fresh start each entry.

---

## 3. Public API

### 3.1 `ExportManager`

```kotlin
package dev.fanfly.wingslog.feature.export.datamanager

import kotlinx.coroutines.flow.Flow

interface ExportManager {
  /**
   * Generate a logbook export for the requested aircraft and time range.
   *
   * Returns a cold Flow that, when collected, drives the export forward and
   * emits progress events. Cancelling the collection cancels the export and
   * deletes any partial file written.
   *
   * Emits in order:
   *   Running(step, percent) — zero or more times
   *   exactly one terminal event:
   *     Success(filePath, sizeBytes)
   *   | Error(message, cause)
   *   | (none — collector cancelled; treat absence as cancellation)
   */
  fun exportLogs(request: ExportRequest): Flow<ExportProgress>
}
```

### 3.2 Data classes

```kotlin
data class ExportRequest(
  val aircraftIds: List<String>,
  val dateRange: ExportDateRange,
  val includeOpenSquawks: Boolean,
)

sealed interface ExportDateRange {
  data object AllTime : ExportDateRange
  data class LastNMonths(val months: Int) : ExportDateRange
  data class Custom(val start: LocalDate, val endInclusive: LocalDate) : ExportDateRange
}

sealed interface ExportProgress {
  data class Running(val step: String, val percent: Int) : ExportProgress
  data class Success(val filePath: String, val displayLocation: String, val sizeBytes: Long) : ExportProgress
  data class Error(val message: String, val cause: Throwable?) : ExportProgress
}
```

`displayLocation` is human-readable (`"Files → Hopply"` / `"Downloads/Hopply"`), distinct from `filePath` which is the raw OS path used by `Open`.

### 3.3 UI state (in `update/viewmodel/`)

```kotlin
sealed interface ExportUiState {
  data class Configuring(
    val aircraft: List<AircraftSelectionRow>,
    val selectedAircraftIds: Set<String>,
    val dateRange: DateRangeOption,
    val customStart: LocalDate,
    val customEnd: LocalDate,
    val includeOpenSquawks: Boolean,
    val estimatedSizeBytes: Long,
    val estimatedLogCount: Int,
  ) : ExportUiState

  data class Running(val step: String, val percent: Int) : ExportUiState

  data class Success(
    val fileName: String,
    val displayLocation: String,
    val filePath: String,
    val sizeBytes: Long,
  ) : ExportUiState

  data class Error(val message: String) : ExportUiState
}

data class AircraftSelectionRow(
  val aircraftId: String,
  val tailNumber: String,
  val makeModel: String,
  val logCount: Int,
)

enum class DateRangeOption { AllTime, Last12Months, Custom }
```

---

## 4. Data Aggregation

### 4.1 `LogbookExportAggregator`

Per-aircraft data fetch. Returns one `AircraftBundle` per aircraft. Uses `.first()` on each manager's observe Flow so we get a snapshot at the moment of export — later edits do not contaminate the file mid-write.

```kotlin
internal class LogbookExportAggregator(
  private val fleetManager: FleetManager,
  private val logsManager: MaintenanceLogManager,
  private val tasksManager: TaskDataManager,
  private val taskDueManager: TaskDueManager,
  private val squawkManager: SquawkManager,
  private val technicianManager: TechnicianManager,
  private val clock: Clock = Clock.System,
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

  suspend fun collect(aircraftId: String, range: ExportDateRange): AircraftBundle = coroutineScope {
    val aircraftDeferred = async { fleetManager.loadAircraft(aircraftId).first() }
    val logsDeferred = async { logsManager.observeLogs(aircraftId).first() }
    val tasksDeferred = async { tasksManager.observeTasks(aircraftId).first() }
    val squawksDeferred = async { squawkManager.observeSquawks(aircraftId).first() }

    val aircraft = aircraftDeferred.await() ?: error("Aircraft $aircraftId not found")
    val allLogs = logsDeferred.await()
    val allTasks = tasksDeferred.await()
    val allSquawks = squawksDeferred.await()

    val rangeFilter = range.toInstantPredicate(clock, timeZone)
    val logsInRange = allLogs.filter { rangeFilter(it.timestamp) }
    val squawksInRange = allSquawks.filter { rangeFilter(it.created_at) }

    val techniciansById = resolveTechnicians(logsInRange)
    val tasksById = allTasks.associateBy { it.id }
    val squawksById = allSquawks.associateBy { it.id }
    val dueByTaskId = allTasks.associate { task ->
      task.id to taskDueManager.computeNextDue(task, allLogs, allTasks)
    }
    // Last-complied is sourced ONLY from log entries that reference the task via inspection_ids.
    // MaintenanceTask.force_complied_status is intentionally NOT consulted here — see §18 Decision 4.
    val lastCompliedByTaskId: Map<String, MaintenanceLog> = allTasks.associateBy({ it.id }) { task ->
      allLogs.filter { task.id in it.inspection_ids }.maxByOrNull { it.timestamp }
    }.filterValues { it != null }.mapValues { (_, log) -> log!! }

    AircraftBundle(
      aircraft = aircraft,
      logs = logsInRange.sortedBy { it.timestamp },             // oldest-first per paper logbook
      tasks = allTasks,                                         // not date-filtered (current state)
      dueByTaskId = dueByTaskId,
      lastCompliedByTaskId = lastCompliedByTaskId,
      squawks = if (request.includeOpenSquawks) squawksInRange
                else squawksInRange.filter { it.addressed_by_log_id.isNotEmpty() ||
                                             it.dismiss_reason != SquawkDismissReason.UNKNOWN },
      tasksById = tasksById,
      squawksById = squawksById,
      techniciansById = techniciansById,
    )
  }

  private suspend fun resolveTechnicians(logs: List<MaintenanceLog>): Map<String, Technician> {
    // Prefer the embedded `technician` field on each log; fall back to TechnicianManager only when missing.
    val embedded = logs.mapNotNull { log -> log.technician?.takeIf { it.name.isNotBlank() } }
      .associateBy { it.id }
    val missingIds = logs.mapNotNull { log -> log.technician_id.takeIf { it.isNotBlank() } }
      .toSet() - embedded.keys
    val resolved = missingIds.mapNotNull { id ->
      technicianManager.loadTechnician(id).first()?.let { id to it }
    }.toMap()
    return embedded + resolved
  }
}

internal data class AircraftBundle(
  val aircraft: Aircraft,
  val logs: List<MaintenanceLog>,
  val tasks: List<MaintenanceTask>,
  val dueByTaskId: Map<String, DueMetadata>,
  val lastCompliedByTaskId: Map<String, MaintenanceLog>,
  val squawks: List<Squawk>,
  val tasksById: Map<String, MaintenanceTask>,
  val squawksById: Map<String, Squawk>,
  val techniciansById: Map<String, Technician>,
)
```

`ExportDateRange.toInstantPredicate` converts the range into `(Instant) -> Boolean`. For `LastNMonths(n)`, `start = now.minus(n.months).toInstant(tz)`; for `Custom`, `endInclusive` includes the entire day.

### 4.2 Why `.first()` rather than `.flowOn().collect`

The Manager interfaces all expose `Flow<...>` observers wired to the local SQLDelight store. Each emission is a stable list (not a partial result). Taking `.first()` yields a consistent in-memory snapshot. No new fetch APIs needed.

Memory bound: one aircraft's logs + tasks + squawks in RAM at a time. With `~200 logs × 12 columns × ~200 bytes/row ≈ 480 KB` per aircraft, this is fine. A fleet export is iterative per-aircraft; only one bundle is live at a time.

---

## 5. Tab Writers

> **Reference sample:** `docs/export_logs_sample/N12345_Cessna_172/` contains a hand-written, byte-level-faithful example of every CSV described in this section. Use it as the expected fixture for the golden-file tests in §14.1 — the implementation's output should match it character-for-character (modulo CRLF vs LF, which both implementations should emit as CRLF per RFC 4180).

### 5.1 `LogbookExportWriter`

Stateless functions, one per CSV tab type. Each accepts the `AircraftBundle` and a `CsvWriter` and writes rows. The orchestrator (`ExportManagerImpl`) determines which writers to call based on the aircraft's components.

```kotlin
internal object LogbookExportWriter {
  fun writeAircraftInfo(bundle: AircraftBundle, csv: CsvWriter, meta: ExportMeta)
  fun writeAirframeLog(bundle: AircraftBundle, csv: CsvWriter)
  fun writeEngineLog(bundle: AircraftBundle, engineIndex: Int, engine: Engine, csv: CsvWriter)
  fun writePropellerLog(bundle: AircraftBundle, engineIndex: Int, propeller: Propeller, csv: CsvWriter)
  fun writeUnknownEngineLog(bundle: AircraftBundle, csv: CsvWriter)        // orphaned serials
  fun writeUnknownPropellerLog(bundle: AircraftBundle, csv: CsvWriter)
  fun writeCompliance(bundle: AircraftBundle, csv: CsvWriter)
  fun writeSquawks(bundle: AircraftBundle, csv: CsvWriter)
  fun writeTechnicians(bundle: AircraftBundle, csv: CsvWriter)
  fun writeFleetSummary(rows: List<FleetSummaryRow>, csv: CsvWriter)
}
```

### 5.2 Routing logs to tabs

For each log:

```kotlin
when (log.component_type) {
  COMPONENT_AIRFRAME -> airframeRows += log
  COMPONENT_ENGINE -> {
    val engineIdx = aircraft.engine.indexOfFirst { it.serial == log.component_serial }
    if (engineIdx >= 0) engineRows.getOrPut(engineIdx, ::mutableListOf).add(log)
    else unknownEngineRows += log
  }
  COMPONENT_PROPELLER -> {
    val match = aircraft.engine.withIndex().firstOrNull { (_, e) ->
      e.propeller?.let { p ->
        p.hub?.serial == log.component_serial ||
        p.blades.any { it.serial == log.component_serial }
      } ?: false
    }
    if (match != null) propellerRows.getOrPut(match.index, ::mutableListOf).add(log)
    else unknownPropellerRows += log
  }
  COMPONENT_UNKNOWN -> airframeRows += log
}
```

The "unknown" bucket protects against orphaned data after a component was replaced; the README explains the bucket.

### 5.3 Inspection / squawk / attachment cross-reference

For an airframe / engine / prop row's multi-value columns:

```kotlin
private const val CELL_SEP = "\n"   // newline-joined inside an RFC-4180-quoted cell

val inspectionTitles = log.inspection_ids
  .map { id -> bundle.tasksById[id]?.title ?: "[deleted]" }
  .joinToString(CELL_SEP)

val referenceNumbers = log.inspection_ids
  .mapNotNull { id -> bundle.tasksById[id]?.reference_number?.takeIf { it.isNotBlank() } }
  .joinToString(CELL_SEP)

val squawkTitles = log.squawk_ids
  .map { id -> bundle.squawksById[id]?.title ?: "[deleted]" }
  .joinToString(CELL_SEP)

val attachments = log.attachments.map { att -> AttachmentCell.format(att, bundle.attachmentPaths) }
  .joinToString(CELL_SEP)
```

`CELL_SEP = "\n"` (single LF). The CsvWriter (§6) detects the embedded newline, wraps the cell in `"..."`, and emits a valid RFC 4180 record. Google Sheets renders the cell as multi-line, one entry per row, so each entry stays independently scannable and filterable. The cell's serialized bytes look like `"Annual\n100hr\nPitot-Static"`. **Never** join with `", "` — commas inside cells would parse correctly (the CsvWriter would quote them too) but flatten to a single string in the spreadsheet view.

`AttachmentCell.format(att, paths)` renders one of:
- `"<name> → attachments/<file>"` for IMAGE / PDF / FILE that downloaded successfully
- `"<name> → <url>"` for LINK
- `"<name> → [download failed]"` / `"[upload pending]"` / `"[download requires sign-in]"` / `"[legacy attachment]"` for the failure modes in §11 below.

### 5.4 Cell formatters

```kotlin
internal object CellFormat {
  fun date(instant: Instant, tz: TimeZone): String =
    instant.toLocalDateTime(tz).date.toString()                // ISO 2026-05-18

  fun hours(value: Double): String =
    if (value == 0.0) "" else value.format1dp()                // "1247.3"

  fun certType(t: Technician): String {
    val enum = when (t.certificate_type) {
      CertificateType.CERTIFICATE_TYPE_REPAIRMAN -> "Repairman"
      CertificateType.CERTIFICATE_TYPE_AMT -> "A&P"
      CertificateType.CERTIFICATE_TYPE_NONE -> ""
    }
    return enum.ifBlank { t.cert_type }                        // fall back to legacy string
  }

  fun priority(p: SquawkPriority): String = when (p) {
    SquawkPriority.SQUAWK_PRIORITY_LOW -> "Low"
    SquawkPriority.SQUAWK_PRIORITY_MEDIUM -> "Medium"
    SquawkPriority.SQUAWK_PRIORITY_HIGH -> "High"
    SquawkPriority.SQUAWK_PRIORITY_AOG -> "AOG"
    SquawkPriority.SQUAWK_PRIORITY_UNKNOWN -> ""
  }

  fun component(c: ComponentType): String = when (c) {
    COMPONENT_AIRFRAME -> "Airframe"
    COMPONENT_ENGINE -> "Engine"
    COMPONENT_PROPELLER -> "Propeller"
    COMPONENT_UNKNOWN -> ""
  }

  fun scheduleRule(rules: List<InspectionRule>): String { /* "Every 12 months", "Every 100 hours", … */ }
  fun complianceType(t: ComplianceType): String                // "Routine Inspection", "Service Bulletin", "Airworthiness Directive"
  fun dueStatus(meta: DueMetadata): String                     // "OK", "Due Soon", "Overdue", "Complied (one-time)"
}
```

`Double.format1dp()` is a tiny helper: `((x * 10).roundToLong() / 10.0).toString()`. We avoid `String.format` because it is JVM-only.

---

## 6. CSV Writer

`CsvWriter` is ~50 LOC, RFC 4180–compliant, in commonMain:

```kotlin
internal class CsvWriter(private val sink: BufferedSink) {       // okio BufferedSink

  fun writeRow(cells: List<String>) {
    cells.forEachIndexed { i, cell ->
      if (i > 0) sink.writeByte(','.code)
      writeCell(cell)
    }
    sink.writeUtf8("\r\n")                                       // CRLF per RFC 4180
  }

  fun writeRow(vararg cells: String) = writeRow(cells.toList())

  private fun writeCell(value: String) {
    val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    if (!needsQuote) {
      sink.writeUtf8(value)
      return
    }
    sink.writeByte('"'.code)
    for (c in value) {
      if (c == '"') sink.writeByte('"'.code)                     // escape by doubling
      sink.writeByte(c.code)
    }
    sink.writeByte('"'.code)
  }
}
```

Why okio's `BufferedSink`? Already pulled in transitively by Wire 6 / SQLDelight; no new dependency. The sink is wrapped around the `ZipFileWriter`'s current-entry output stream so CSV bytes stream directly into the ZIP without an intermediate in-memory buffer.

UTF-8 is implicit (okio writes UTF-8). A BOM is **not** prepended — Google Sheets handles UTF-8 without BOM correctly, and a BOM would surface as a stray `?` character in the first cell for less-forgiving viewers.

---

## 7. ZIP Writer

```kotlin
expect class ZipFileWriter(destination: Path) : Closeable {
  /** Opens a new entry. Returns a BufferedSink streaming into that entry. */
  fun openEntry(path: String): BufferedSink
  /** Closes the current entry and flushes its central directory record. */
  fun closeEntry()
  /** Closes the whole archive. */
  override fun close()
}
```

### 7.1 Android (`java.util.zip`)

```kotlin
actual class ZipFileWriter actual constructor(destination: Path) : Closeable {
  private val zip = ZipOutputStream(BufferedOutputStream(FileOutputStream(destination.toFile())))
  init { zip.setLevel(Deflater.DEFAULT_COMPRESSION) }

  actual fun openEntry(path: String): BufferedSink {
    zip.putNextEntry(ZipEntry(path).apply { method = ZipEntry.DEFLATED })
    return zip.sink().buffer()                                   // okio adapter; do NOT close it
  }
  actual fun closeEntry() { zip.closeEntry() }
  actual override fun close() { zip.close() }
}
```

### 7.2 iOS (pure-Kotlin)

Apple's Foundation does not include a public ZIP writer. The CommonCrypto-style alternatives (`Compression.framework`) provide deflate streams but not the ZIP container.

For MVP, we ship a **pure-Kotlin ZIP writer in iosMain** using `kotlinx.io` primitives for byte-level writes, and Apple's `compression_stream_*` (via cinterop) for the DEFLATE engine. Wire format references PKWARE APPNOTE.TXT section 4 (local file header, central directory, end-of-central-directory). Estimated ~250 LOC.

```kotlin
actual class ZipFileWriter actual constructor(destination: Path) : Closeable {
  private val sink: BufferedSink = SystemFileSystem.sink(destination).buffered()
  private val entries = mutableListOf<CentralDirectoryRecord>()
  private var currentOffset = 0L
  private var currentEntry: PendingEntry? = null

  actual fun openEntry(path: String): BufferedSink { /* write local header, return deflating sink */ }
  actual fun closeEntry() { /* finalize CRC + sizes, append CDR record */ }
  actual override fun close() { /* write central directory + EOCD, close sink */ }
}
```

**Why not a third-party Pod (e.g. SSZipArchive)?** Adds an Objective-C dependency, complicates the iOS framework build for cinterop, and the surface we need (single-stream sequential writes) is small enough to implement directly. We will revisit if the writer becomes a maintenance burden.

**Compression mode:** Both implementations default to DEFLATE. The writer interface does not expose a STORED fallback. If iOS deflate-via-compression-framework proves flaky in early testing, the iOS actual can fall back to STORED (size penalty ~3–5× for CSV); the public API does not change.

### 7.3 Atomic write

Both actuals write to `<destination>.partial`. On `close()` (success), they `rename` to the final filename. On any throw, the partial file is deleted (`finally` block in `ExportManagerImpl`).

---

## 7A. Attachment Bundling

The orchestrator delegates all attachment work to `AttachmentBundler`. The bundler is responsible for: (1) collecting every attachment referenced by the in-scope logs / tasks / squawks of a single aircraft, (2) ensuring each binary is locally available, (3) streaming the bytes into the zip under `attachments/`, and (4) returning a `Map<attachmentId, RenderedAttachment>` that the CSV writers use to format the multi-value Attachments cells.

### 7A.1 Contract

```kotlin
internal class AttachmentBundler(
  private val attachmentManager: AttachmentManager,
) {

  /**
   * For the given aircraft bundle:
   *   1. Walk every Attachment referenced by logs, tasks, and squawks (de-duped by id).
   *   2. For non-LINK attachments: call attachmentManager.ensureLocal(att) and await Done.
   *   3. Stream the local bytes into [zip] at "attachments/<short_id>_<sanitized_name>".
   *   4. Emit Progress events on [progress] (current attachment N of M, bytes transferred).
   * Returns the resolved RenderedAttachment per id so callers can format CSV cells.
   *
   * Cancellation propagates through the collected Flow; partial blob downloads are NOT
   * retained — AttachmentManager handles atomicity for the local store.
   */
  suspend fun bundle(
    bundle: AircraftBundle,
    aircraftFolder: String,
    zip: ZipFileWriter,
    progress: suspend (BundlerProgress) -> Unit,
  ): Map<String, RenderedAttachment>
}

internal sealed interface RenderedAttachment {
  data class Local(val name: String, val zipPath: String) : RenderedAttachment   // → attachments/8f3a_…
  data class Link(val name: String, val url: String) : RenderedAttachment        // → <url>
  data class Skipped(val name: String, val reason: SkipReason) : RenderedAttachment
}

internal enum class SkipReason {
  DOWNLOAD_FAILED,
  UPLOAD_PENDING,
  REQUIRES_SIGN_IN,
  LEGACY,                  // R1-era attachment with no storage_path / sha256
}

internal data class BundlerProgress(
  val currentIndex: Int,
  val totalCount: Int,
  val currentName: String,
  val bytesTransferred: Long,
  val bytesTotal: Long,    // sum of attachment.size_bytes across in-scope attachments
)
```

### 7A.2 Walking the attachment graph

```kotlin
internal fun AircraftBundle.collectAttachments(): List<Attachment> = buildList {
  logs.flatMapTo(this) { it.attachments }
  tasks.flatMapTo(this) { it.attachments }
  squawks.flatMapTo(this) { it.attachments }
}.distinctBy { it.id }
```

`distinctBy { it.id }` dedupes when one attachment is referenced from multiple logs (or from both a squawk and the addressing log). The bundler writes each attachment to the zip **at most once per aircraft folder**.

### 7A.3 Ensure-local + stream

```kotlin
suspend fun bundleOne(att: Attachment, zip: ZipFileWriter, aircraftFolder: String): RenderedAttachment {
  if (att.type == AttachmentType.ATTACHMENT_TYPE_LINK) {
    return RenderedAttachment.Link(att.name, att.url)
  }
  if (att.storage_path.isBlank() && att.url.isBlank()) {
    return RenderedAttachment.Skipped(att.name, SkipReason.LEGACY)
  }

  // Drive the AttachmentManager download to terminal state.
  val terminal = attachmentManager.ensureLocal(att)
    .onEach { state -> if (state is DownloadState.Downloading) reportProgress(state.progress) }
    .first { it is DownloadState.Done || it is DownloadState.Failed }

  if (terminal is DownloadState.Failed) {
    return RenderedAttachment.Skipped(att.name, skipReasonFor(terminal.error))
  }

  // Stream local bytes into the zip entry. Both platforms expose this via okio's Source.
  val zipPath = "$aircraftFolder/attachments/${shortId(att.id)}_${sanitize(att.name, att.mime_type)}"
  val entrySink = zip.openEntry(zipPath)
  attachmentManager.openLocalSource(att).use { src ->
    entrySink.writeAll(src)
  }
  zip.closeEntry()
  return RenderedAttachment.Local(att.name, "attachments/${zipPath.substringAfterLast('/')}")
}
```

`AttachmentManager` does not currently expose `openLocalSource(att): Source`; this is a small addition that wraps `LocalBlobStore.localUri(att.blobId)`-resolved file IO behind a platform-agnostic `okio.Source`. Adding the method is part of this feature's scope; the existing R2 `AttachmentOpener` already opens local files for the in-app viewer and can be refactored to expose the same Source.

`skipReasonFor(error)` maps `AuthException → REQUIRES_SIGN_IN`, `NotUploadedException → UPLOAD_PENDING`, anything else → `DOWNLOAD_FAILED`. Concrete error types come from `AttachmentManager` — see `feature/attachment/datamanager`.

### 7A.4 Filename rendering

```kotlin
private fun shortId(id: String): String = id.take(4)                            // 4 hex chars

private fun sanitize(name: String, mime: String): String {
  val ext = name.substringAfterLast('.', missingDelimiterValue = "")
    .ifBlank { extensionForMime(mime) }                                         // "pdf", "jpg", …
  val stem = name.removeSuffix(".$ext").ifBlank { "file" }
  return stem.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60) +
    if (ext.isNotBlank()) ".$ext" else ""
}
```

The 60-char cap on `stem` prevents pathological names; the `.ext` is appended afterwards so the file extension is always preserved.

### 7A.5 Progress accounting

The orchestrator (§9) tracks two cost surfaces:

| Surface | Estimate | Used for |
|:---|:---|:---|
| Tab writes | constant per tab | dominant when attachments are local or absent |
| Attachment bytes | sum of `attachment.size_bytes` | dominant when downloads are in flight |

Progress percent = `(tabsWritten + bytesTransferred / totalAttachmentBytes * weightedTabsEquivalent) / totalSteps`. The "(network)" suffix is appended to step messages whenever the bundler is awaiting an `ensureLocal()` that started in `Downloading` state.

### 7A.6 README augmentation

After the bundler finishes per aircraft, the orchestrator appends a per-aircraft block to the README listing any non-`Local` attachments — the rendered `[upload pending]` / `[download failed]` markers in the CSVs are durable references, and the README provides the inspector-readable summary.

---

## 8. Destination Resolution

```kotlin
expect suspend fun resolveExportDestination(filename: String): ExportDestination

data class ExportDestination(
  val path: Path,            // OS path where we write
  val displayLocation: String, // human-readable, e.g. "Files → Hopply"
  val openUri: String,       // hand to platform open intent
)
```

### 8.1 Android

```kotlin
actual suspend fun resolveExportDestination(filename: String): ExportDestination {
  val values = ContentValues().apply {
    put(MediaStore.Downloads.DISPLAY_NAME, filename)
    put(MediaStore.Downloads.MIME_TYPE, "application/zip")
    put(MediaStore.Downloads.RELATIVE_PATH, "Download/Hopply/")
    put(MediaStore.Downloads.IS_PENDING, 1)
  }
  val resolver = context.contentResolver
  val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    ?: error("MediaStore insert failed")
  // Resolve to an absolute file path via openFileDescriptor for streaming;
  // mark IS_PENDING = 0 in finally after close().
  ...
}
```

minSdk is 33, so MediaStore's scoped storage flow is always available — no legacy `WRITE_EXTERNAL_STORAGE` branch.

### 8.2 iOS

```kotlin
actual suspend fun resolveExportDestination(filename: String): ExportDestination {
  val fm = NSFileManager.defaultManager
  val docs = fm.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).first() as NSURL
  val hopplyDir = docs.URLByAppendingPathComponent("Hopply", isDirectory = true)!!
  fm.createDirectoryAtURL(hopplyDir, withIntermediateDirectories = true, ...)
  val target = hopplyDir.URLByAppendingPathComponent(filename)!!
  return ExportDestination(
    path = target.path!!.toPath(),
    displayLocation = "Files → Hopply",
    openUri = target.absoluteString!!,
  )
}
```

The app's `Info.plist` already needs `UISupportsDocumentBrowser = false` and `LSSupportsOpeningDocumentsInPlace = true` for the Hopply Documents folder to show up in the Files app. **Action item:** verify these keys exist in `iosApp/iosApp/Info.plist`; add them in the implementation PR if missing.

---

## 9. Orchestrator

```kotlin
internal class ExportManagerImpl(
  private val fleetManager: FleetManager,
  private val aggregator: LogbookExportAggregator,
  private val readmeRenderer: ReadmeRenderer,
  private val clock: Clock,
  private val timeZone: TimeZone,
) : ExportManager {

  override fun exportLogs(request: ExportRequest): Flow<ExportProgress> = flow {
    val now = clock.now()
    val fileName = chooseFileName(request, now)
    val destination = resolveExportDestination(fileName)
    val partialPath = destination.path.appendSuffix(".partial")
    try {
      ZipFileWriter(partialPath).use { zip ->
        val totalSteps = computeTotalSteps(request)
        var stepIndex = 0
        emit(Running("Preparing export…", percent = 0))

        for ((acIndex, aircraftId) in request.aircraftIds.withIndex()) {
          val bundle = aggregator.collect(aircraftId, request.dateRange)
          val folder = folderName(bundle.aircraft, isMulti = request.aircraftIds.size > 1)

          writeAircraftInfo(zip, folder, bundle, request, now)
          stepIndex++; emit(running(stepIndex, totalSteps, "Aircraft info — ${bundle.aircraft.tail_number}"))

          writeAirframe(zip, folder, bundle)
          stepIndex++; emit(running(stepIndex, totalSteps, "Airframe — ${bundle.aircraft.tail_number}"))

          bundle.aircraft.engine.forEachIndexed { i, engine ->
            writeEngine(zip, folder, bundle, i, engine)
            stepIndex++; emit(running(stepIndex, totalSteps,
              "Engine ${i + 1} — ${bundle.aircraft.tail_number}"))
            engine.propeller?.let { prop ->
              writePropeller(zip, folder, bundle, i, prop)
              stepIndex++; emit(running(stepIndex, totalSteps,
                "Propeller ${i + 1} — ${bundle.aircraft.tail_number}"))
            }
          }
          writeUnknownBucketsIfAny(zip, folder, bundle)
          writeCompliance(zip, folder, bundle); stepIndex++; emit(...)
          writeSquawks(zip, folder, bundle, request); stepIndex++; emit(...)
          writeTechnicians(zip, folder, bundle); stepIndex++; emit(...)
        }

        if (request.aircraftIds.size > 1) writeFleetSummary(zip, request, now)
        writeReadme(zip, request, now)
      }

      SystemFileSystem.atomicMove(partialPath, destination.path)
      val size = SystemFileSystem.metadataOrNull(destination.path)?.size ?: 0L
      emit(Success(destination.path.toString(), destination.displayLocation, size))
    } catch (t: CancellationException) {
      SystemFileSystem.delete(partialPath, mustExist = false)
      throw t
    } catch (t: Throwable) {
      SystemFileSystem.delete(partialPath, mustExist = false)
      emit(Error(t.message ?: "Export failed", t))
    }
  }
}
```

The flow is `flow { }` (cold). One collector — the ViewModel — drives it; `flowOn(Dispatchers.Default)` is applied at the consumer.

### 9.1 Step count

Steps per aircraft: `1 (info) + 1 (airframe) + engines + propellers + 1 (compliance) + 1 (squawks) + 1 (technicians)`. Plus `1` for fleet summary (multi-aircraft only) and `1` for README. Used purely for the progress percentage; the user-facing label is the human string from the most recent emission.

### 9.2 File naming

```kotlin
private fun chooseFileName(request: ExportRequest, now: Instant): String {
  val date = now.toLocalDateTime(timeZone).date.format(YYYYMMDD)
  return if (request.aircraftIds.size == 1) {
    val tail = sanitize(aircraft.tail_number.ifBlank { "aircraft" })
    "Hopply_Logs_${tail}_$date.zip"
  } else "Hopply_Logs_Fleet_$date.zip"
}

private fun sanitize(s: String) = s.replace(Regex("[^A-Za-z0-9-]"), "_")
```

If a file with the chosen name already exists, the implementation **overwrites** (PRD §6 — "Re-export overwrites a same-named file from the same day").

---

## 10. UI Layer

### 10.1 `ExportSelectionRoute`

Thin wrapper that:
1. Calls `koinViewModel<ExportViewModel>()`.
2. Wires `navController.popBackStack` to the back arrow.
3. Hands `ExportUiState` to `ExportSelectionScreen` along with event callbacks.

### 10.2 `ExportSelectionScreen`

Stateless. One `LazyColumn` with sections:

1. **Aircraft selection** — `items(state.aircraft)` rendered as `Checkbox + tail + makeModel + logCountTrailing`. Header row with **Select all / Clear all** chips.
2. **Date range** — `SegmentedButtonRow(options = DateRangeOption.values())`. When `Custom`, a `DateRangePicker` (Material 3) is rendered below.
3. **Options** — `Switch` for `Include open squawks`.
4. **Pinned bottom bar** — `Button("Export · ${state.estimatedSizeBytes.toReadable()}")`, disabled when `selectedAircraftIds.isEmpty()`.

When `state` is `Running`, the screen content swaps to `ExportProgressView` (determinate `LinearProgressIndicator` + step label + `Cancel`). When `Success`, swaps to `ExportSuccessView` (icon, file name, location, Open + Done).

### 10.3 `ExportViewModel`

```kotlin
class ExportViewModel(
  private val exportManager: ExportManager,
  private val fleetManager: FleetManager,
  private val logsManager: MaintenanceLogManager,
  private val clock: Clock,
  private val timeZone: TimeZone,
) : ViewModel() {

  private val _state = MutableStateFlow<ExportUiState>(ExportUiState.Configuring(/* defaults */))
  val state: StateFlow<ExportUiState> = _state.asStateFlow()

  private var exportJob: Job? = null

  init {
    viewModelScope.launch {
      fleetManager.observeFleetDashboard()
        .map { aircraft -> aircraft.map { toRow(it) } }
        .collect { rows -> reduce { it.copy(aircraft = rows, selectedAircraftIds = rows.map { r -> r.aircraftId }.toSet()) } }
    }
  }

  fun onToggleAircraft(id: String) = reduce { current ->
    val newSelection = if (id in current.selectedAircraftIds) current.selectedAircraftIds - id
                       else current.selectedAircraftIds + id
    current.copy(selectedAircraftIds = newSelection).recomputeEstimates()
  }

  fun onSelectAll() = reduce { it.copy(selectedAircraftIds = it.aircraft.map { r -> r.aircraftId }.toSet()).recomputeEstimates() }
  fun onClearAll() = reduce { it.copy(selectedAircraftIds = emptySet()).recomputeEstimates() }
  fun onDateRangeChange(option: DateRangeOption) = reduce { it.copy(dateRange = option).recomputeEstimates() }
  fun onCustomRange(start: LocalDate, end: LocalDate) = reduce { it.copy(customStart = start, customEnd = end).recomputeEstimates() }
  fun onToggleIncludeOpenSquawks() = reduce { it.copy(includeOpenSquawks = !it.includeOpenSquawks) }

  fun onExport() {
    val configuring = (_state.value as? ExportUiState.Configuring) ?: return
    exportJob = viewModelScope.launch {
      exportManager.exportLogs(configuring.toRequest()).collect { progress ->
        _state.value = progress.toUiState(fileName = /* derived */, configuring = configuring)
      }
    }
  }

  fun onCancel() {
    exportJob?.cancel()
    exportJob = null
    _state.value = lastConfiguring                          // restore selections
  }

  fun onDone() { _state.value = lastConfiguring }
}
```

`recomputeEstimates()` walks `selectedAircraftIds` and sums the `logCount` of selected rows; estimated bytes is a coarse `logsInScope * ~250 bytes/row * tabs` approximation, tightened by reading `metadataOrNull(path).size` post-export. The estimate is **for the button label only** — accuracy within an order of magnitude is fine.

### 10.4 Cancellation

`exportJob?.cancel()` cancels the cold Flow's coroutine. The orchestrator's `try/finally` deletes `<file>.partial`. The ViewModel restores its previously-cached `Configuring` state.

---

## 11. Threading

| Layer | Dispatcher |
|:---|:---|
| ViewModel — state mutation | `Dispatchers.Main` (the default for `viewModelScope`) |
| `ExportManager.exportLogs` collection | `.flowOn(Dispatchers.Default)` applied by the ViewModel on collect |
| `LogbookExportAggregator.collect` | inherits `Dispatchers.Default` — CPU work |
| `ZipFileWriter` I/O | inside the same dispatcher; okio sink writes are buffered |
| MediaStore insert / FS path resolution | `Dispatchers.IO` via `withContext(Dispatchers.IO)` inside `resolveExportDestination` |

Because the file is opened only once per export and writes are sequential, contention between dispatchers is a non-issue.

---

## 12. Koin Wiring

### 12.1 `ExportDataManagerModule`

```kotlin
val exportDataManagerModule = module {
  factory<LogbookExportAggregator> {
    LogbookExportAggregator(
      fleetManager = get<FleetManager>(),
      logsManager = get<MaintenanceLogManager>(),
      tasksManager = get<TaskDataManager>(),
      taskDueManager = get<TaskDueManager>(),
      squawkManager = get<SquawkManager>(),
      technicianManager = get<TechnicianManager>(),
      attachmentManager = get<AttachmentManager>(),
    )
  }
  factory<ReadmeRenderer> { ReadmeRenderer(appVersion = get<AppVersionProvider>().version) }
  single<ExportManager> {
    ExportManagerImpl(
      fleetManager = get<FleetManager>(),
      aggregator = get<LogbookExportAggregator>(),
      readmeRenderer = get<ReadmeRenderer>(),
      attachmentBundler = get<AttachmentBundler>(),
      clock = Clock.System,
      timeZone = TimeZone.currentSystemDefault(),
    )
  }
  factory<AttachmentBundler> {
    AttachmentBundler(attachmentManager = get<AttachmentManager>())
  }
}
```

Per project convention, all Koin `get()` calls are typed (`get<ClassType>()`) — this makes dependency resolution explicit at the call site and produces clearer errors when a binding is missing.

`AppVersionProvider` is a small interface wrapping app-info so `ReadmeRenderer` can read the version from a non-Composable context; `getAppVersion()` in `core:appinfo` is a `@Composable` and can't be called from the export pipeline. Add a trivial expect/actual `AppVersionProvider` (`core/appinfo`) returning `version: String`.

### 12.2 `ExportUiModule`

```kotlin
val exportUiModule = module {
  viewModelOf(::ExportViewModel)
}
```

### 12.3 `composeApp/di/initKoin.kt`

Append:

```kotlin
modules(
  ...
  exportDataManagerModule,
  exportUiModule,
)
```

---

## 13. Edge Case Handling

| Case | Implementation |
|:---|:---|
| Zero aircraft in fleet | `ExportSelectionScreen` renders empty-state composable when `state.aircraft.isEmpty()`; Export button hidden. |
| Zero aircraft selected | Export button `enabled = selectedAircraftIds.isNotEmpty()`. |
| Aircraft has zero logs / squawks | Tab CSVs still emitted with header row only — `LogbookExportWriter` writes the header unconditionally and zero data rows. |
| Aircraft has no engines / props | `bundle.aircraft.engine` empty → `forEachIndexed` no-ops → tabs not emitted. |
| Orphaned engine/prop serial (component replaced) | Routed to `02_Engine_Unknown.csv` / `03_Propeller_Unknown.csv`. README explains. |
| Deleted task referenced by `inspection_ids` | Title cell rendered as `[deleted]`; reference number omitted. |
| Deleted squawk referenced by `squawk_ids` | Same: `[deleted]`. |
| Cancel mid-export | Orchestrator's `finally` deletes `<file>.partial`; ViewModel restores last `Configuring`. |
| Disk full / write error | Orchestrator emits `Error`; partial file deleted. UI shows `Error` state with retry. |
| Filename collision (same day) | `SystemFileSystem.atomicMove` overwrites; no prompt. |
| Two aircraft share tail number | Folder names disambiguate with `(2)` suffix in `folderName(bundle.aircraft, isMulti)`. |
| Backgrounded app during export | `viewModelScope` survives screen rotation but not process death. Process death mid-export ⇒ partial file remains until next export run cleans it. (Background-safe completion via foreground service is future work — see PRD §10.) |
| Legacy logs missing `technician` embed but with `technician_id` | Aggregator looks up via `TechnicianManager.loadTechnician(id).first()`. Cached per-export. |
| Logs with neither `technician` nor `technician_id` | Technician columns blank; row omitted from Technicians tab. |

---

## 14. Testing Strategy

### 14.1 Unit tests (commonMain → `src/test/`, JVM-only runner)

- **`CsvWriterTest`** — RFC 4180 edge cases: cells with commas, embedded quotes, newlines, leading/trailing whitespace, empty cells, Unicode, very long cells. Verify CRLF line endings.
- **`LogbookExportWriterTest`** — golden CSV per tab. Build a fixed `AircraftBundle` (in-memory protos, fixed clock), invoke each writer, compare the produced CSV byte-for-byte to a checked-in `.csv.golden` file.
- **`LogbookExportAggregatorTest`** — mock all managers (MockK, per CLAUDE.md). Cases:
  - Date range filters logs correctly (inclusive end).
  - Embedded technician preferred over manager lookup.
  - Tasks not date-filtered (current compliance state always exported).
  - Open squawks excluded when `includeOpenSquawks = false`.
- **`ExportManagerImplTest`** — integration test using `okio.fakefilesystem.FakeFileSystem`. Run a full export against a synthetic two-aircraft fleet; open the resulting zip with `java.util.zip.ZipInputStream` (JVM test only) and verify the entry list and per-entry CSV content matches golden files.
- **`ExportViewModelTest`** — state transitions, cancellation restores last Configuring, error path emits Error state.

### 14.2 Manual / E2E

- Single-engine Cessna 172 with 200 logs, 30 tasks, 5 squawks → export single-aircraft → import into Google Sheets → manually inspect each tab.
- Twin Beechcraft with 2 engines, 2 props, 100 logs → verify Engine 1/2 and Prop 1/2 tabs each contain only their respective serials.
- Fleet of 10 aircraft × 100 logs → export → file under 5 MB, completes under 30 s on a Pixel 6.
- Cancel mid-export at progress ~50% → no zip file appears in Downloads.

### 14.3 Stress test integration

`feature/stresstest` already generates fake aircraft + logs. Add a `runExportSmokeTest()` action to the Stress Test screen (dogfood only) that programmatically exports the current fleet and asserts the output zip is well-formed. This lets us regression-test the pipeline on every dogfood build.

---

## 15. Performance

| Cost source | Estimate |
|:---|:---|
| Snapshot fetch per aircraft (4 `.first()` calls in parallel) | ~50 ms on warm cache, dominated by SQLDelight query |
| Technician resolution | one Manager `.first()` per missing-embed technician id (typically 0–5 per aircraft) |
| Per-row CSV composition | O(1) string concatenation + 1 cell-format pass; ~5 µs/row |
| Deflate (DEFLATE level 6) | ~50 MB/s on modern devices; CSV input is tiny |
| MediaStore insert (Android) | ~30 ms |
| Atomic rename | constant |

PRD performance targets (single-aircraft × 200 logs in < 5 s, fleet × 1000 logs in < 30 s) have ~100× headroom from the back-of-envelope above. The slow path will be the aggregator's flow `.first()` when SQLDelight is cold — acceptable.

---

## 16. Localization

CSV cell content is **English-only** for MVP. Rationale:

- The target user is an A&P / IA reviewing in a spreadsheet — the conventions (e.g. `A&P`, `AOG`, `Service Bulletin`) are English-language industry terms.
- Localizing CSV content adds review burden without clear demand.

UI strings (selection screen, progress messages, success/error states) **are** localized via Compose resources in `feature/export/sharedassets/`. The generated archive `README.txt` body lives as a plain text template at `feature/export/datamanager/src/commonMain/resources/export_readme_template.txt`; Gradle turns that text file into a generated constant for common code so the editable template is not native Kotlin source.

---

## 17. Implementation Phases

### Phase 1 — Skeleton + Settings entry (foundation)

- Scaffold `feature/export/` modules (use the `feature-module-scaffolder` agent).
- Add `Screen.ExportLogs` and register the composable in `AppEntry.kt`.
- Add the new Settings row, wired to a placeholder `ExportSelectionScreen` that renders `"Coming soon"`.
- No `ExportManager` implementation yet — Koin module registers a stub.

This ships as a no-op feature behind feature flag (`FeatureFlags.exportLogsEnabled`) so the entry-point UI work can land independently.

The flag is stored as positive `export_logs_enabled` state in `FeatureLabSettings`, unlike the older `*_disabled` flags. Default value is false so the Phase 1 Settings entry stays hidden until explicitly enabled in Feature Lab.

### Phase 2 — Selection screen + ViewModel

- Real `ExportSelectionScreen` with all interactions.
- `ExportViewModel` with state + selection logic.
- `ExportManager.exportLogs` returns a fake Flow that emits Running → Success without actually writing anything. End-to-end nav works; user sees a fake success state.

### Phase 3 — CSV + writers + aggregator

- `CsvWriter`, `CellFormat`, `LogbookExportWriter` (all tabs).
- `LogbookExportAggregator`.
- All golden-file unit tests pass.
- Output is still mocked at the ZIP layer (writers write to in-memory `Buffer`s).

### Phase 4 — ZipFileWriter (per platform)

- Android `java.util.zip` actual.
- iOS pure-Kotlin actual with Foundation `compression_stream` cinterop.
- `ZipFileWriterTest` (JVM round-trip) + manual iOS verification.

### Phase 5 — Destination resolution + atomic write + flag flip

- `ExportDestination` actuals (MediaStore Android, Documents iOS).
- Verify Info.plist keys for Files-app visibility on iOS.
- Flip `FeatureFlags.exportLogsEnabled` to true after dogfood validation.

Each phase is independently reviewable and ships to dogfood. The flag stays off in prod until Phase 5.

---

## 18. Decisions

The questions raised during design review are resolved as follows. New questions discovered during implementation should be added below with `Pending:` until resolved.

1. **Time-zone source.** **Decision:** Use the device's current time zone at export time. Each `Date` cell renders the log's `timestamp` as `LocalDateTime` in `TimeZone.currentSystemDefault()`. Revisit only if cross-time-zone operators report off-by-one-day issues.
2. **iOS ZIP writer.** **Decision:** Hand-roll the pure-Kotlin ZIP writer in `iosMain` for MVP. ~250 LOC, fully unit-testable from JVM tests via the same byte format. Revisit if the writer becomes a maintenance burden.
3. **Compression mode.** **Decision:** DEFLATE on both platforms — Android uses `java.util.zip.ZipOutputStream` defaults; iOS uses Foundation `compression_stream` via cinterop driving the deflate engine.
4. **Compliance tab — force-complied tasks without log entries.** **Decision:** Do **not** treat `MaintenanceTask.force_complied_status` as a Last-Complied source. The Compliance tab's `Last Complied — Date` / `Last Complied — Hours` columns are populated **only** from the most recent log entry that references the task via `inspection_ids`. If a task has been force-complied with no backing log, both Last-Complied columns are blank. The Next Due columns still reflect `TaskDueManager.computeNextDue(...)` — which itself accounts for force-complied state — so Status remains accurate.

---

## 19. Non-Goals (re-statement from PRD)

Listed here so the design doc is self-contained:

- No XLSX writing in MVP — CSV in ZIP only.
- No OAuth-based direct Google Sheets push.
- Attachment binaries are bundled for IMAGE / PDF / FILE attachments when local or downloadable; failures degrade to textual markers in the CSV and README notes.
- No PDF rendering.
- No server-side generation; pipeline is entirely on-device.
- No re-import / round-trip.
- No background-safe completion notification.
- No per-feature analytics beyond a single `export_logs_completed` event (instrumentation tracked in PRD §7.3).
