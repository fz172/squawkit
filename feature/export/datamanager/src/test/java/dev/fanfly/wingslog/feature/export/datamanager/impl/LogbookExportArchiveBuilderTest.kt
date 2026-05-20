package dev.fanfly.wingslog.feature.export.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.feature.export.datamanager.ExportDateRange
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Test

class LogbookExportArchiveBuilderTest {

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

    assertThat(entries["attachments/abcd_inspection_photo.jpg"]?.bytes?.decodeToString())
      .isEqualTo("photo-bytes")
    assertThat(entries["01_Airframe.csv"]?.bytes?.decodeToString())
      .contains("inspection photo.jpg -> attachments/abcd_inspection_photo.jpg\n" +
        "missing.pdf -> [attachment unavailable]")
    assertThat(entries["README.txt"]?.bytes?.decodeToString())
      .contains("Attachment efgh5678 has no local blob record.")
    assertThat(entries["00_Aircraft_Info.csv"]?.bytes?.decodeToString())
      .contains("Export App Version,Hopply 1.0.260519.10 (364)")
    assertThat(entries["README.txt"]?.bytes?.decodeToString())
      .contains("App:       Hopply 1.0.260519.10 (364)")
    assertThat(entries["01_Airframe.csv"]?.bytes?.decodeToString())
      .doesNotContain("Log ID")
    assertThat(entries["10_Compliance.csv"]?.bytes?.decodeToString())
      .doesNotContain("Task ID")
    assertThat(entries["11_Squawks.csv"]?.bytes?.decodeToString())
      .doesNotContain("Squawk ID")
    assertThat(entries["20_Technicians.csv"]?.bytes?.decodeToString())
      .doesNotContain("Technician ID")
    assertThat(entries["20_Technicians.csv"]?.bytes?.decodeToString())
      .doesNotContain("Sign-Offs in Export")
  }

  private fun aircraftBundle(logs: List<MaintenanceLog>) = AircraftBundle(
    aircraft = Aircraft(
      id = "aircraft-1",
      make = "Cessna",
      model = "172",
      serial = "172001",
      tail_number = "N12345",
    ),
    logs = logs,
    tasks = emptyList(),
    dueByTaskId = emptyMap(),
    lastCompliedByTaskId = emptyMap(),
    squawks = emptyList(),
    tasksById = emptyMap(),
    squawksById = emptyMap(),
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
