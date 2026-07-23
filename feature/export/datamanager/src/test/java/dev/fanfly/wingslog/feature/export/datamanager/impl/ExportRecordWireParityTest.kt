package dev.fanfly.wingslog.feature.export.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.export.ExportRecord
import dev.fanfly.wingslog.export.ExportRecordAircraft
import dev.fanfly.wingslog.export.ExportRecordDateRange
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import org.junit.Test

/**
 * Guards against silent proto→wire drift for the export-history docs. Each wire class is hand-
 * mirrored from its Wire proto; if the proto later gains a field the wire doc doesn't map, the
 * conversion leaves it at its default and the client reads a 0/"" as if it were truth. These tests
 * turn that into a build failure: a fully-populated wire doc, converted to its proto, must leave no
 * proto field at its default value — bar fields intentionally not persisted to the remote doc.
 */
class ExportRecordWireParityTest {

  @Test
  fun `ExportRecordWire maps every field of the ExportRecord proto`() {
    val fromFullWire = ExportRecordWire(
      exportId = "e1",
      uid = "u1",
      fileName = "logbook.zip",
      sizeBytes = 1_024,
      createdAtEpochMillis = 1_700_000_000_000,
      displayLocation = "Downloads",
      formats = listOf("PDF"),
      dateRange = ExportRecordDateRangeWire(
        kind = "custom",
        months = 3,
        customStart = "2024-01-01",
        customEnd = "2024-12-31",
      ),
      aircraft = listOf(ExportRecordAircraftWire(tailNumber = "N123", makeModel = "Cessna 172")),
      remoteArchiveRef = "gs://bucket/e1.zip",
      destinationEmail = "pilot@example.com",
      destinationEmailSource = "auth",
      persistedDeliveryState = "SENT",
      deliverySentAtEpochMillis = 1_700_000_500_000,
      deliveryFailureCode = "NONE",
      deliveryFailureMessage = "n/a",
      remoteExpiresAtEpochMillis = 1_800_000_000_000,
    ).toExportRecord()

    // file_path is a local device path, never persisted to the remote history doc (toExportRecord
    // sets it to ""), so it is the one field intentionally left unmapped.
    assertEveryProtoFieldPopulated(
      fromFullWire,
      ExportRecord(),
      intentionallyUnmapped = setOf("file_path"),
    )
  }

  @Test
  fun `ExportRecordDateRangeWire maps every field of the proto`() {
    val proto = ExportRecordDateRangeWire(
      kind = "custom",
      months = 6,
      customStart = "2024-01-01",
      customEnd = "2024-06-30",
    ).toProto()

    assertEveryProtoFieldPopulated(proto, ExportRecordDateRange())
  }

  @Test
  fun `ExportRecordAircraftWire maps every field of the proto`() {
    val proto = ExportRecordAircraftWire(tailNumber = "N123", makeModel = "Cessna 172").toProto()

    assertEveryProtoFieldPopulated(proto, ExportRecordAircraft())
  }
}

/**
 * Asserts a fully-populated proto leaves no schema field at its default (comparing against an
 * all-defaults instance), proving the wire doc that produced it maps every field. `unknownFields`
 * is Wire's extension-bytes sidecar, not a schema field; [intentionallyUnmapped] documents fields
 * deliberately not carried on the wire.
 */
private inline fun <reified T : Any> assertEveryProtoFieldPopulated(
  populated: T,
  allDefaults: T,
  intentionallyUnmapped: Set<String> = emptySet(),
) {
  val schemaFieldNames = T::class.primaryConstructor!!.parameters
    .mapNotNull { it.name }
    .filter { it != "unknownFields" }
    .toSet()
  val props = T::class.memberProperties.filter { it.name in schemaFieldNames }
  // Prove the reflection actually resolved the schema, so an empty result can't false-pass below.
  assertThat(schemaFieldNames).isNotEmpty()
  assertThat(props).hasSize(schemaFieldNames.size)

  val unmapped = props
    .filter { it.name !in intentionallyUnmapped }
    .filter { it.get(populated) == it.get(allDefaults) }
    .map { it.name }

  assertThat(unmapped).isEmpty()
}
