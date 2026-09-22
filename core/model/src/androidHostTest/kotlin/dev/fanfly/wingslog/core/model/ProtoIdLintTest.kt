package dev.fanfly.wingslog.core.model

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * New messages never carry an id as a bare string (docs/datalog/data_log_visualizer_design.md
 * §4.4): an id is boxed in `id/ids.proto` so the generated class is the dedicated type and a
 * `ThingId` cannot be handed where a `DataLogId` belongs. Wire cannot enforce that, so this reads
 * the schema text. Messages that predate the rule are listed by name; nothing joins that list.
 */
class ProtoIdLintTest {

  /** Every message that already carried a bare string id when the rule landed. Frozen. */
  private val grandfathered = setOf(
    "Attachment",
    "Comment",
    "Component",
    "DeveloperSettings",
    "ExportRecord",
    "LinkedRule",
    "MaintenanceLog",
    "MaintenanceOverview",
    "MaintenanceTask",
    "MeterReading",
    "RequestExportDeliveryRequest",
    "RequestExportDeliveryResponse",
    "SharedAircraftRef",
    "Squawk",
    "Technician",
    "Thing",
    "ThingTemplate",
    "UserInfo",
  )

  /**
   * String fields whose name ends in `_id` but which are not record identifiers: a recorder's own
   * serial string kept verbatim, and a series registry key such as "engine[1].oil_press". Neither
   * names a document, so neither is boxed. Listed by `Message.field` with the reason beside it.
   */
  private val notIdentifiers = setOf(
    "DataLogSource.system_id", // the avionics' hardware id, a verbatim header string
    "DataLogSeries.canonical_id", // CanonicalSeriesRegistry key, "" when unmapped
  )

  private val idFieldNames = Regex(
    """^\s*(?:repeated\s+)?string\s+(id|[a-z0-9_]+_id)\s*=""",
    RegexOption.MULTILINE
  )

  @Test
  fun noNewMessageCarriesABareStringId() {
    val offenders = protoFiles().flatMap { file ->
      messages(file.readText()).filter { (name, _) -> name !in grandfathered }
        .flatMap { (name, body) ->
          idFieldNames.findAll(body)
            .map { "$name.${it.groupValues[1]}" }
            .filter { it !in notIdentifiers }
            .map { "${file.name}: $it" }
        }
    }
    assertWithMessage(
      "Box these ids as messages in id/ids.proto (design §4.4) rather than string fields",
    ).that(offenders)
      .isEmpty()
  }

  @Test
  fun everyIdMessageIsFrozenAtOneValueField() {
    // Wire's equality includes unknown fields, so an id that ever grew a second field would compare
    // unequal across builds.
    val ids = File(protoRoot(), "id/ids.proto").readText()
    val messages = messages(ids)
    assertWithMessage("id/ids.proto declares no messages").that(messages)
      .isNotEmpty()
    messages.forEach { (name, body) ->
      val fields =
        Regex("""^\s*\S+\s+([a-z0-9_]+)\s*=\s*(\d+);""", RegexOption.MULTILINE)
          .findAll(body)
          .map { it.groupValues[1] to it.groupValues[2] }
          .toList()
      assertWithMessage("$name must be exactly `string value = 1`")
        .that(fields)
        .containsExactly("value" to "1")
      assertWithMessage("$name.value must be a string").that(body)
        .contains("string value = 1;")
    }
  }

  /** `(name, body)` for each top-level message in [proto]; nested messages are not used here. */
  private fun messages(proto: String): List<Pair<String, String>> =
    Regex(
      """^message\s+(\w+)\s*\{(.*?)^\}""",
      setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
    )
      .findAll(proto)
      .map { it.groupValues[1] to it.groupValues[2] }
      .toList()

  private fun protoFiles(): List<File> =
    protoRoot().walkTopDown()
      .filter { it.isFile && it.extension == "proto" }
      .toList()

  private fun protoRoot(): File =
    File(repoRoot(), "core/model/src/commonMain/proto")

  private fun repoRoot(): File {
    var dir = File(System.getProperty("user.dir"))
    while (!File(dir, "settings.gradle.kts").exists()) {
      dir = requireNotNull(dir.parentFile)
    }
    return dir
  }
}
