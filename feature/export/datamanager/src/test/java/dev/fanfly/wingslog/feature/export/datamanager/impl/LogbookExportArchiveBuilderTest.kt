package dev.fanfly.wingslog.feature.export.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.Engine
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.Propeller
import dev.fanfly.wingslog.aircraft.PropellerHub
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.SquawkDismissReason
import dev.fanfly.wingslog.feature.export.datamanager.ExportDateRange
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Test
import com.squareup.wire.Instant as WireInstant

class LogbookExportArchiveBuilderTest {

  private val aircraftFolder = "N12345_Cessna_172"

  @Test
  fun buildEntries_embedsAttachmentPayloadsAndLinksCsvRows() {
    val availableAttachment = attachment(id = "abcd1234", name = "inspection photo.jpg")
    val missingAttachment = attachment(id = "efgh5678", name = "missing.pdf")
    val bundle = aircraftBundle(
      logs = listOf(
        MaintenanceLog(
          id = "log-1",
          work_description = "Annual inspection",
          component_type = ComponentType.COMPONENT_AIRFRAME,
          attachments = listOf(availableAttachment, missingAttachment),
        )
      )
    )

    val entries = LogbookExportArchiveBuilder(
      appVersion = "Hopply 1.0.260519.10 (364)",
    ).buildEntries(
      request = ExportRequest(
        aircraftIds = listOf(bundle.aircraft.id),
        dateRange = ExportDateRange.AllTime,
        includeOpenSquawks = true,
      ),
      bundles = listOf(bundle),
      attachmentManifests = mapOf(
        bundle.aircraft.id to AttachmentExportManifest(
          byAttachmentId = mapOf(
            availableAttachment.id to AttachmentExportPayload(
              attachmentId = availableAttachment.id,
              relativePath = "attachments/abcd_inspection_photo.jpg",
              bytes = "photo-bytes".encodeToByteArray(),
            )
          ),
          notes = listOf("Attachment efgh5678 has no local blob record."),
        )
      ),
      generatedAt = LocalDateTime(2026, 5, 19, 14, 45),
      timeZone = TimeZone.UTC,
    ).associateBy { entry -> entry.path }

    assertThat(entries["$aircraftFolder/attachments/abcd_inspection_photo.jpg"]?.bytes?.decodeToString())
      .isEqualTo("photo-bytes")
    assertThat(entries["$aircraftFolder/N12345_Cessna_172.pdf"]?.bytes?.decodeToString())
      .startsWith("%PDF-1.4")
    assertThat(entries["$aircraftFolder/csv/01_Airframe.csv"]?.bytes?.decodeToString())
      .contains("inspection photo.jpg -> attachments/abcd_inspection_photo.jpg\n" +
        "missing.pdf -> [attachment unavailable]")
    assertThat(entries["README.txt"]?.bytes?.decodeToString())
      .contains("Attachment efgh5678 has no local blob record.")
    assertThat(entries["$aircraftFolder/csv/00_Aircraft_Info.csv"]?.bytes?.decodeToString())
      .contains("Export App Version,Hopply 1.0.260519.10 (364)")
    assertThat(entries["README.txt"]?.bytes?.decodeToString())
      .contains("App:       Hopply 1.0.260519.10 (364)")
    assertThat(entries["$aircraftFolder/csv/01_Airframe.csv"]?.bytes?.decodeToString())
      .doesNotContain("Log ID")
    assertThat(entries["$aircraftFolder/csv/10_Tasks.csv"]?.bytes?.decodeToString())
      .doesNotContain("Task ID")
    assertThat(entries["$aircraftFolder/csv/10_Tasks.csv"]?.bytes?.decodeToString())
      .doesNotContain("Status")
    assertThat(entries["$aircraftFolder/csv/10_Tasks.csv"]?.bytes?.decodeToString())
      .contains("One-Time")
    assertThat(entries["$aircraftFolder/csv/10_Tasks.csv"]?.bytes?.decodeToString())
      .contains("Task Details")
    assertThat(entries["$aircraftFolder/csv/11_Squawks.csv"]?.bytes?.decodeToString())
      .doesNotContain("Squawk ID")
    assertThat(entries["$aircraftFolder/csv/20_Technicians.csv"]?.bytes?.decodeToString())
      .doesNotContain("Technician ID")
    assertThat(entries["$aircraftFolder/csv/20_Technicians.csv"]?.bytes?.decodeToString())
      .doesNotContain("Sign-Offs in Export")
    val workbookEntries = readZipEntries(
      requireNotNull(entries["$aircraftFolder/Hopply_Logs_N12345_20260519.xlsx"]?.bytes)
    )
    assertThat(workbookEntries.keys).containsAtLeast(
      "[Content_Types].xml",
      "xl/workbook.xml",
      "xl/worksheets/sheet1.xml",
    )
    assertThat(workbookEntries["xl/workbook.xml"])
      .contains("<sheet name=\"00 Aircraft Info\"")
    assertThat(workbookEntries["xl/workbook.xml"])
      .contains("<sheet name=\"01 Airframe\"")
    assertThat(workbookEntries["xl/workbook.xml"])
      .contains("<sheet name=\"10 Tasks\"")
    assertThat(workbookEntries["xl/worksheets/sheet2.xml"])
      .contains("inspection photo.jpg -&gt; attachments/abcd_inspection_photo.jpg")
    assertThat(entries["README.txt"]?.bytes?.decodeToString())
      .contains("Inside each aircraft folder:")
    assertThat(entries["README.txt"]?.bytes?.decodeToString())
      .contains("-> Tasks -> Squawks -> Technicians")
  }

  @Test
  fun buildEntries_singleEngineAndPropellerUseUnnumberedTimeLabels() {
    val aircraft = Aircraft(
      id = "aircraft-1",
      make = "Cessna",
      model = "172",
      serial = "172001",
      tail_number = "N12345",
      engine = listOf(
        Engine(
          make = "Lycoming",
          model = "IO-360",
          serial = "ENG-1",
          propeller = Propeller(
            hub = PropellerHub(
              make = "McCauley",
              model = "2A34C",
              serial = "PROP-1",
            )
          ),
        )
      ),
    )
    val bundle = aircraftBundle(
      aircraft = aircraft,
      logs = listOf(
        MaintenanceLog(
          id = "log-1",
          component_type = ComponentType.COMPONENT_AIRFRAME,
          work_description = "Oil change",
        )
      )
    )

    val entries = LogbookExportArchiveBuilder(
      appVersion = "Hopply 1.0.260520.1 (365)",
    ).buildEntries(
      request = ExportRequest(
        aircraftIds = listOf(bundle.aircraft.id),
        dateRange = ExportDateRange.AllTime,
        includeOpenSquawks = true,
      ),
      bundles = listOf(bundle),
      attachmentManifests = emptyMap(),
      generatedAt = LocalDateTime(2026, 5, 20, 9, 30),
      timeZone = TimeZone.UTC,
    ).associateBy { entry -> entry.path }

    val aircraftInfoCsv = requireNotNull(entries["$aircraftFolder/csv/00_Aircraft_Info.csv"]?.bytes?.decodeToString())
    val airframeCsv = requireNotNull(entries["$aircraftFolder/csv/01_Airframe.csv"]?.bytes?.decodeToString())
    val engineCsv = requireNotNull(entries["$aircraftFolder/csv/02_Engine.csv"]?.bytes?.decodeToString())
    val propellerCsv = requireNotNull(entries["$aircraftFolder/csv/03_Propeller.csv"]?.bytes?.decodeToString())
    val workbookEntries = readZipEntries(
      requireNotNull(entries["$aircraftFolder/Hopply_Logs_N12345_20260520.xlsx"]?.bytes)
    )
    assertThat(entries["$aircraftFolder/N12345_Cessna_172.pdf"]?.bytes?.decodeToString()).startsWith("%PDF-1.4")

    assertThat(entries.keys).contains("$aircraftFolder/csv/02_Engine.csv")
    assertThat(entries.keys).contains("$aircraftFolder/csv/03_Propeller.csv")
    assertThat(entries.keys).doesNotContain("$aircraftFolder/csv/02_Engine_1.csv")
    assertThat(entries.keys).doesNotContain("$aircraftFolder/csv/03_Propeller_1.csv")
    assertThat(aircraftInfoCsv).contains("Current Engine Time")
    assertThat(aircraftInfoCsv).contains("Current Propeller Time")
    assertThat(aircraftInfoCsv).doesNotContain("Current Engine 1 Time")
    assertThat(aircraftInfoCsv).doesNotContain("Current Propeller 1 Time")
    assertThat(airframeCsv).contains("Date,Airframe Time,Engine Time,Work Description")
    assertThat(airframeCsv).doesNotContain("Engine 1 Time")
    assertThat(engineCsv).contains("Engine Time,Airframe Time,Work Description")
    assertThat(propellerCsv).contains("Prop Time,Airframe Time,Work Description")
    assertThat(workbookEntries["xl/workbook.xml"]).contains("<sheet name=\"02 Engine\"")
    assertThat(workbookEntries["xl/workbook.xml"]).contains("<sheet name=\"03 Prop\"")
    assertThat(workbookEntries["xl/workbook.xml"]).doesNotContain("<sheet name=\"02 Engine 1\"")
    assertThat(workbookEntries["xl/workbook.xml"]).doesNotContain("<sheet name=\"03 Prop 1\"")
    assertThat(workbookEntries.values.joinToString(separator = "\n")).contains("Current Engine Time")
    assertThat(workbookEntries.values.joinToString(separator = "\n")).contains("Current Propeller Time")
    assertThat(workbookEntries.values.joinToString(separator = "\n")).contains("Engine Time")
    assertThat(workbookEntries.values.joinToString(separator = "\n")).doesNotContain("Engine 1 Time")
  }

  @Test
  fun buildEntries_squawksUseCombinedStatusAndActionDateColumns() {
    val addressedLog = MaintenanceLog(
      id = "log-addressed",
      component_type = ComponentType.COMPONENT_AIRFRAME,
      timestamp = WireInstant.ofEpochSecond(1_715_728_000L),
      work_description = "Resolved squawk",
    )
    val bundle = aircraftBundle(
      logs = listOf(addressedLog),
      squawks = listOf(
        Squawk(
          id = "sq-addressed",
          title = "Oil seep",
          component_type = ComponentType.COMPONENT_AIRFRAME,
          addressed_by_log_id = addressedLog.id,
          created_at = WireInstant.ofEpochSecond(1_715_641_600L),
        ),
        Squawk(
          id = "sq-dismissed",
          title = "Legacy note",
          component_type = ComponentType.COMPONENT_ENGINE,
          dismiss_reason = SquawkDismissReason.SQUAWK_DISMISS_REASON_OBSOLETE,
          dismissed_at = WireInstant.ofEpochSecond(1_715_814_400L),
          created_at = WireInstant.ofEpochSecond(1_715_555_200L),
        ),
      ),
    )

    val entries = LogbookExportArchiveBuilder(
      appVersion = "Hopply 1.0.260520.2 (366)",
    ).buildEntries(
      request = ExportRequest(
        aircraftIds = listOf(bundle.aircraft.id),
        dateRange = ExportDateRange.AllTime,
        includeOpenSquawks = true,
      ),
      bundles = listOf(bundle),
      attachmentManifests = emptyMap(),
      generatedAt = LocalDateTime(2026, 5, 20, 10, 0),
      timeZone = TimeZone.UTC,
    ).associateBy { entry -> entry.path }

    val squawkCsv = requireNotNull(entries["$aircraftFolder/csv/11_Squawks.csv"]?.bytes?.decodeToString())
    val workbookEntries = readZipEntries(
      requireNotNull(entries["$aircraftFolder/Hopply_Logs_N12345_20260520.xlsx"]?.bytes)
    )
    val workbookText = workbookEntries.values.joinToString(separator = "\n")

    assertThat(squawkCsv).contains("Created,Title,Description,Priority,Component,Component Serial,Status,Action Date")
    assertThat(squawkCsv).doesNotContain("Dismiss Reason")
    assertThat(squawkCsv).doesNotContain("Addressed By - Date")
    assertThat(squawkCsv).contains("Oil seep")
    assertThat(squawkCsv).contains("Addressed")
    assertThat(squawkCsv).contains("Legacy note")
    assertThat(squawkCsv).contains("Dismissed - Obsolete")
    assertThat(workbookText).contains("11 Squawks")
    assertThat(workbookText).contains("Action Date")
    assertThat(workbookText).contains("Dismissed - Obsolete")
    assertThat(workbookText).doesNotContain("Dismiss Reason")
  }

  @Test
  fun buildEntries_multiAircraftUsesOneFolderPerAircraftAndRootReadme() {
    val secondAircraft = Aircraft(
      id = "aircraft-2",
      make = "Beechcraft",
      model = "Bonanza",
      serial = "BE35-1",
      tail_number = "N54321",
    )
    val firstBundle = aircraftBundle(
      logs = listOf(
        MaintenanceLog(
          id = "log-1",
          component_type = ComponentType.COMPONENT_AIRFRAME,
          work_description = "Inspection",
        )
      )
    )
    val secondBundle = aircraftBundle(
      logs = listOf(
        MaintenanceLog(
          id = "log-2",
          component_type = ComponentType.COMPONENT_AIRFRAME,
          work_description = "Brake service",
        )
      ),
      aircraft = secondAircraft,
    )

    val entries = LogbookExportArchiveBuilder().buildEntries(
      request = ExportRequest(
        aircraftIds = listOf(firstBundle.aircraft.id, secondBundle.aircraft.id),
        dateRange = ExportDateRange.AllTime,
        includeOpenSquawks = true,
      ),
      bundles = listOf(firstBundle, secondBundle),
      attachmentManifests = emptyMap(),
      generatedAt = LocalDateTime(2026, 5, 20, 11, 0),
      timeZone = TimeZone.UTC,
    ).associateBy { it.path }

    assertThat(entries.keys).contains("README.txt")
    assertThat(entries.keys).contains("N12345_Cessna_172/csv/00_Aircraft_Info.csv")
    assertThat(entries.keys).contains("N12345_Cessna_172/Hopply_Logs_N12345_20260520.xlsx")
    assertThat(entries.keys).contains("N12345_Cessna_172/N12345_Cessna_172.pdf")
    assertThat(entries.keys).contains("N54321_Beechcraft_Bonanza/csv/00_Aircraft_Info.csv")
    assertThat(entries.keys).contains("N54321_Beechcraft_Bonanza/Hopply_Logs_N54321_20260520.xlsx")
    assertThat(entries.keys).contains("N54321_Beechcraft_Bonanza/N54321_Beechcraft_Bonanza.pdf")
    assertThat(entries.keys).doesNotContain("00_Fleet_Summary.csv")
    assertThat(entries.keys).doesNotContain("Hopply_Logs_Fleet_20260520.xlsx")
  }

  private fun readZipEntries(bytes: ByteArray): Map<String, String> =
    buildMap {
      ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
          val entry = zip.nextEntry ?: break
          put(entry.name, zip.readBytes().decodeToString())
        }
      }
    }

  private fun aircraftBundle(
    logs: List<MaintenanceLog>,
    squawks: List<Squawk> = emptyList(),
    aircraft: Aircraft = Aircraft(
      id = "aircraft-1",
      make = "Cessna",
      model = "172",
      serial = "172001",
      tail_number = "N12345",
    ),
  ) = AircraftBundle(
    logs = logs,
    aircraft = aircraft,
    tasks = emptyList(),
    dueByTaskId = emptyMap(),
    lastCompliedByTaskId = emptyMap(),
    squawks = squawks,
    tasksById = emptyMap(),
    squawksById = squawks.associateBy { it.id },
    techniciansById = emptyMap(),
  )

  private fun attachment(id: String, name: String) = Attachment(
    id = id,
    name = name,
    type = AttachmentType.ATTACHMENT_TYPE_IMAGE,
    mime_type = "image/jpeg",
    size_bytes = 10L,
    sha256 = "sha",
  )
}
