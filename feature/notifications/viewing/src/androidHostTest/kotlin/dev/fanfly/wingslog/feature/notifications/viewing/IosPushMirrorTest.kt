package dev.fanfly.wingslog.feature.notifications.viewing

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.fanfly.wingslog.core.template.GenericLexicon
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.core.template.dataLogNoun
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.feature.notifications.model.NotificationTapTarget
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.MaintenanceLogId
import dev.fanfly.wingslog.id.MaintenanceTaskId
import dev.fanfly.wingslog.id.SquawkId
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.thing.Lexicon
import dev.fanfly.wingslog.thing.ThingTemplate
import org.junit.Test
import java.io.File

/**
 * The iOS notification service extension's Swift copy of this module's push code, held to the
 * server's wire format and to the Kotlin side.
 *
 * The copy exists because the extension cannot link `ComposeApp.framework` (see
 * `NotificationService.swift`), and it is kept in sync by hand. It drifted three ways at once,
 * each of which a test here fails on: the encoder kept the pre-#638 `aircraft/` path (#1303), the
 * parser had no `data_log` kind (#1304), and the renderer knew none of the body keys the server had
 * moved to (#1305). The Swift is read as text — there is no Swift toolchain in this build — so these
 * assert what a `case` covers, not what it renders.
 */
class IosPushMirrorTest {

  private val repoRoot: File by lazy {
    var dir = File(System.getProperty("user.dir")!!)
    while (!File(dir, "settings.gradle.kts").exists()) {
      dir = dir.parentFile
        ?: error("settings.gradle.kts not found above ${System.getProperty("user.dir")}")
    }
    dir
  }

  private fun read(path: String): String = File(repoRoot, path).readText()

  private val serverPush = read("$SERVER/pushMessages.ts")
  private val serverModels = read("$SERVER/notificationModels.ts")
  private val kotlinRenderer = read(
    "feature/notifications/viewing/src/commonMain/kotlin/dev/fanfly/wingslog/feature/" +
      "notifications/viewing/PushPayloadRendering.kt",
  )
  private val swiftParser = read("$EXTENSION/PushPayload.swift")
  private val swiftRenderer = read("$EXTENSION/PushPayloadRendering.swift")
  private val swiftEncoder = read("$EXTENSION/NotificationTapEncoder.swift")
  private val swiftNouns = read("$EXTENSION/PushNouns.swift")

  private fun swiftCases(source: String): Set<String> =
    Regex("""case "([a-z0-9_]+)"""").findAll(source)
      .map { it.groupValues[1] }
      .toSet()

  private fun notificationKeys(source: String, pattern: String): Set<String> =
    Regex(pattern).findAll(source)
      .map { it.groupValues[1] }
      .toSet()

  /**
   * Every key the server can send: the literals, and each templated one
   * (`notification_n1_body_record_${input.kind}`) expanded over the `kind:` union declared nearest
   * above it — the input type the template reads.
   */
  private fun serverKeys(): Set<String> {
    val literals =
      notificationKeys(serverPush, """"(notification_[a-z0-9_]+)"""")
    val templated =
      Regex("""`(notification_[a-z0-9_]+)\$\{input\.kind\}`""").findAll(
        serverPush
      )
        .flatMap { template ->
          val union = Regex("""kind: ("[a-z]+"(?: \| "[a-z]+")*);""")
            .findAll(serverPush.substring(0, template.range.first))
            .last()
            .groupValues[1]
          Regex(""""([a-z]+)"""").findAll(union)
            .map { template.groupValues[1] + it.groupValues[1] }
        }
    return literals + templated
  }

  @Test
  fun everyKeyTheServerSendsIsRenderedOnBothPlatforms() {
    val sent = serverKeys()
    // Sanity: a regex that matched nothing would pass every assertion below.
    assertThat(sent).contains("notification_n1_body_record_created")

    val kotlin =
      notificationKeys(kotlinRenderer, """"(notification_[a-z0-9_]+)" ->""")
    val swift =
      swiftCases(swiftRenderer).filter { it.startsWith("notification_") }
        .toSet()

    assertWithMessage("keys the server sends that PushPayloadRendering.kt does not render")
      .that(sent - kotlin)
      .isEmpty()
    // Equal, not a superset: a key only Swift renders is one the Kotlin side retired.
    assertWithMessage("PushPayloadRendering.swift vs PushPayloadRendering.kt")
      .that(swift)
      .isEqualTo(kotlin)
  }

  @Test
  fun everyTapKindTheServerSendsIsParsedOnBothPlatforms() {
    // Literal prefixes (`squawk:${…}`), plus `${input.recordType}:` — every RECORD_TYPE value.
    val literal = notificationKeys(serverPush, """tapTarget:\s*`([a-z_]+):""") +
      notificationKeys(serverPush, """\? `([a-z_]+):\$\{""")
    val recordTypes = notificationKeys(
      serverModels.substringAfter("export const RECORD_TYPE = {")
        .substringBefore("}"),
      """:\s*"([a-z_]+)"""",
    )
    val sent = literal + recordTypes
    assertThat(sent).containsAtLeast(
      "aircraft",
      "squawk",
      "data_log",
      "suggestions"
    )

    for (kind in sent) {
      assertWithMessage("PushPayload.parseTapTarget(\"$kind:T1:R1\")")
        .that(PushPayload.parseTapTarget("$kind:T1:R1"))
        .isNotNull()
    }
    assertWithMessage("tap kinds the server sends that PushPayload.swift has no case for")
      .that(sent - swiftCases(swiftParser))
      .isEmpty()
  }

  @Test
  fun theSwiftEncoderWritesThePathsTheRouterEncodes() {
    val targets = listOf(
      NotificationTapTarget.Thing(ThingId("T1"), tab = "squawks"),
      NotificationTapTarget.Squawk(ThingId("T1"), SquawkId("R1")),
      NotificationTapTarget.Task(ThingId("T1"), MaintenanceTaskId("R1")),
      NotificationTapTarget.Log(ThingId("T1"), MaintenanceLogId("R1")),
      NotificationTapTarget.DataLog(ThingId("T1"), DataLogId("R1")),
      NotificationTapTarget.Suggestions(ThingId("T1")),
    )
    val segment = Regex("""notification-tap/([a-z_]+)/""")
    val kotlin =
      targets.map { segment.find(NotificationTapRouter.encode(it))!!.groupValues[1] }
        .toSet()
    val swift = segment.findAll(swiftEncoder)
      .map { it.groupValues[1] }
      .toSet()

    assertThat(swift).isEqualTo(kotlin)
  }

  /**
   * The extension's per-template words (#1426), against this build's lexicons: the Swift line each
   * template should have, as `PushNouns.swift` writes it, so a failure prints the fix.
   */
  @Test
  fun theSwiftNounTableSaysWhatEachTemplatesLexiconSays() {
    val registry = BakedInTemplateRegistry(appVersionCode = Int.MAX_VALUE)
    val expected = CanonicalTemplates.ALL.map { it.id }
      .sorted()
      .map { id ->
        "    \"$id\": ${
          nounsLine(
            registry.lexiconFor(
              ThingTemplate(
                id = id
              )
            )
          )
        },"
      } +
      "  static let generic = ${nounsLine(GenericLexicon.LEXICON)}"
    val actual = swiftNouns.lines()
      .filter { "PushNouns(thing:" in it }

    assertThat(actual).containsExactlyElementsIn(expected)
      .inOrder()
  }

  /** One `PushNouns(…)` initialiser, in `PushNouns.swift`'s field order. */
  private fun nounsLine(lexicon: Lexicon): String {
    val fields = listOf(
      "thing" to lexicon.thingNoun.singular,
      "thingTitle" to LexiconFormatter.titleCase(lexicon.thingNoun),
      "squawk" to lexicon.squawkNoun.singular,
      "aSquawk" to LexiconFormatter.withArticle(lexicon.squawkNoun),
      "squawksTitle" to LexiconFormatter.titleCasePlural(lexicon.squawkNoun),
      "task" to lexicon.taskNoun.singular,
      "aTask" to LexiconFormatter.withArticle(lexicon.taskNoun),
      "tasks" to lexicon.taskNoun.plural,
      "tasksTitle" to LexiconFormatter.titleCasePlural(lexicon.taskNoun),
      "log" to lexicon.logNoun.singular,
      "aLog" to LexiconFormatter.withArticle(lexicon.logNoun),
      "logsTitle" to LexiconFormatter.titleCasePlural(lexicon.logNoun),
      "dataLog" to lexicon.dataLogNoun.singular,
      "aDataLog" to LexiconFormatter.withArticle(lexicon.dataLogNoun),
      "dataLogsTitle" to LexiconFormatter.titleCasePlural(lexicon.dataLogNoun),
    )
    return fields.joinToString(
      ", ",
      prefix = "PushNouns(",
      postfix = ")"
    ) { (name, value) ->
      "$name: \"$value\""
    }
  }

  private companion object {
    const val SERVER = "backend/firebase/functions/src/notifications"
    const val EXTENSION = "iosApp/NotificationServiceExtension"
  }
}
