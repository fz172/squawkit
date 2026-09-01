package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.thing.ThingTemplate
import org.junit.Test
import java.io.File

/**
 * That the committed `airplane.v1.pb` is what the app thinks it is (#675).
 *
 * **Why a committed binary is checked by a test rather than compiled in Gradle.** Wire has no
 * protobuf text-format parser, so turning `airplane.v1.textproto` into bytes needs `protoc`, which
 * this build otherwise has no use for — Wire does all codegen. Rather than put a native binary on
 * every developer's Gradle configuration path for an asset that changes about once per preset, the
 * `.pb` is committed and `templates/compile-template.sh` regenerates it into `templates/binary`. This test is what keeps
 * that honest: it is the only thing standing between an edited `.textproto` and stale bytes
 * shipping.
 */
class AirplaneTemplateAssetTest {

  private fun repoRoot(): File {
    var dir = File(System.getProperty("user.dir")!!)
    while (!File(dir, "settings.gradle.kts").exists()) {
      dir = checkNotNull(dir.parentFile) {
        "settings.gradle.kts not found above ${System.getProperty("user.dir")}"
      }
    }
    return dir
  }

  private val asset: File
    get() = File(repoRoot(), "core/template/templates/binary/airplane.v1.pb")

  private val decoded: ThingTemplate
    get() = ThingTemplate.ADAPTER.decode(asset.readBytes())

  @Test
  fun theCompiledAssetExists() {
    // Every assertion below is meaningless if the file is missing, and `readBytes` on a missing
    // file throws a FileNotFoundException that reads like an infrastructure fault rather than the
    // real cause: somebody added the .textproto and did not commit the .pb.
    assertThat(asset.exists()).isTrue()
    assertThat(asset.length()).isGreaterThan(0L)
  }

  /**
   * That what the app carries is what is committed.
   *
   * Not a tautology, though both sides trace to one file: the left is `airplane.v1.pb` read from
   * disk, the right is the base64 constant Gradle embedded from it at build time. They agree only
   * while `generateTemplateAssets` is actually wired into compilation — so this is what catches the
   * task being skipped, mis-wired, or its output going stale, none of which produce a build error.
   *
   * It began as the migration proof for #675, asserting the compiled bytes against the hand-written
   * Kotlin `ThingTemplate`. That comparison passed, which is why the Kotlin could be deleted; it
   * lives in this file's history rather than here, because the constructor it compared against is
   * gone.
   */
  @Test
  fun theEmbeddedTemplateMatchesTheCommittedAsset() {
    assertThat(AirplaneTemplate.TEMPLATE).isEqualTo(decoded)
  }

  @Test
  fun theAssetCarriesTheIdentityTheCodeAssumes() {
    // AirplaneTemplate.ID and VERSION stayed in Kotlin — the registry and the fallback key off them
    // — so they are now asserted against the asset rather than being its source.
    assertThat(decoded.id).isEqualTo(AirplaneTemplate.ID)
    assertThat(decoded.version).isEqualTo(AirplaneTemplate.VERSION)

    // A floor above 0 would degrade every airplane on every build (#728). The template ships inside
    // the binary that reads it, so it can never legitimately be newer than its reader.
    assertThat(decoded.min_app_version).isEqualTo(0)
  }

  /**
   * Wire's re-encoding decodes back to the same message — **not** back to the same bytes.
   *
   * protoc and Wire disagree on the encoding of repeated enums, and both are right. proto3 makes
   * packed the default, which protoc emits (`priorities` as `3a04 01020304`: one length-delimited
   * run); Wire writes them unpacked, one tag per value (`3801 3802 3803 3804`). A conformant reader
   * accepts either, so the messages are equal while the bytes differ — 703 from protoc, 707 from
   * Wire, for this template.
   *
   * Worth stating because the obvious stronger assertion, byte-equality against the committed
   * `.pb`, **cannot hold**, and would look like a corrupt asset rather than a wire-format detail.
   * Anything that fingerprints a template has to hash the published bytes, never a local re-encode.
   */
  @Test
  fun theAssetSurvivesAWireEncodeDecodeCycle() {
    val reEncoded = ThingTemplate.ADAPTER.encode(decoded)

    assertThat(ThingTemplate.ADAPTER.decode(reEncoded)).isEqualTo(decoded)
  }

  /**
   * The user-facing words, pinned independently of the asset chain.
   *
   * **Why this exists, and why it is not the second copy #675 deleted.** `.textproto` → `.pb` →
   * embedded constant is self-consistent: every check above compares one link against another, so a
   * wrong word introduced at the source flows through all of them and passes. Verified by flipping
   * a byte in the committed `.pb` — the generator re-ran, the constant matched, and the suite went
   * green with the lexicon reading "Birworthy". The `.pb` is binary, so review will not catch it
   * either.
   *
   * The deleted Kotlin constructor used to be that independent statement, as a side effect of being
   * the source. This is the part worth keeping: an expectation in reviewable code, not a second
   * thing to render from.
   *
   * Only what is **user-visible and not already covered**. The nouns are checked far more strictly
   * by `StringSnapshotTest`, which compares fully rendered strings against the shipped snapshot;
   * these are the fields with no current rendering for it to compare (design §4.5) — which is
   * exactly why they need saying here.
   */
  @Test
  fun theLexiconStillSaysWhatTheAppSays() {
    val lexicon = AirplaneTemplate.AIRPLANE_LEXICON

    assertThat(lexicon.thing?.singular).isEqualTo("aircraft")
    // "aircraft" is its own plural and takes "an" — the case that makes `article` a stored field.
    assertThat(lexicon.thing?.plural).isEqualTo("aircraft")
    assertThat(lexicon.thing?.article).isEqualTo("an")

    assertThat(lexicon.ready_status).isEqualTo("Airworthy")
    // These two name an OS notification channel the user sees in system settings (PRD §8.5), so a
    // regression here escapes the app's own surfaces.
    assertThat(lexicon.down_status).isEqualTo("AOG")
    assertThat(lexicon.down_status_long).isEqualTo("Aircraft on Ground")
    assertThat(lexicon.collection_label).isEqualTo("Fleet")
    assertThat(lexicon.authority_label).isEqualTo("FAA")

    assertThat(lexicon.compliance_mandatory?.abbreviation).isEqualTo("AD")
    assertThat(lexicon.compliance_mandatory?.plural)
      .isEqualTo("Airworthiness Directives")
    assertThat(lexicon.compliance_advisory?.abbreviation).isEqualTo("SB")
    assertThat(lexicon.compliance_advisory?.plural).isEqualTo("Service Bulletins")
  }

  @Test
  fun theStructureTheAirplaneScreensAssumeIsIntact() {
    // Labels and order, not just the keys TemplateKeysResolveTest checks: the shell renders
    // `sections` in this sequence, and the export path branches on the layout.
    // Declaration order is render order: make, model, then serial beside tail number.
    assertThat(AirplaneTemplate.AIRPLANE_SPEC_FIELDS.map { it.key })
      .containsExactly("make", "model", "serial", "tail_number")
      .inOrder()
    assertThat(AirplaneTemplate.AIRPLANE_SPEC_FIELDS.filter { it.is_identifier }
                 .map { it.key })
      .containsExactly("tail_number", "serial")

    assertThat(AirplaneTemplate.AIRPLANE_METERS.map { it.key })
      .containsExactly("airframe_hours", "engine_hours", "prop_hours")
      .inOrder()
    // component_slot_key is what makes engine hours the ENGINE's rather than the airframe's.
    assertThat(AirplaneTemplate.AIRPLANE_METERS.map { it.component_slot_key })
      .containsExactly("", "engine", "propeller")
      .inOrder()

    val capabilities = AirplaneTemplate.AIRPLANE_CAPABILITIES
    assertThat(capabilities.sections.map { it.name }).containsExactly(
      "SECTION_DASHBOARD",
      "SECTION_SQUAWKS",
      "SECTION_TASKS",
      "SECTION_LOGS",
    )
      .inOrder()
    assertThat(capabilities.export_layout.name).isEqualTo("EXPORT_LAYOUT_LOGBOOK")
    // The airplane column of PRD §4.8 is on for everything; a false here silently removes UI.
    assertThat(capabilities.components).isTrue()
    assertThat(capabilities.meters).isTrue()
    assertThat(capabilities.compliance).isTrue()
    assertThat(capabilities.technicians).isTrue()
    assertThat(capabilities.technician_certificates).isTrue()
    assertThat(capabilities.component_serial_prompt).isTrue()
  }

  @Test
  fun theAssetCarriesNoUnknownFields() {
    // Unknown fields here would mean protoc encoded something this build's schema does not define,
    // which for a template compiled from this repo's own .proto can only be a stale .pb.
    assertThat(decoded.unknownFields.size).isEqualTo(0)
    assertThat(decoded.capabilities?.unknownFields?.size ?: 0).isEqualTo(0)
  }
}
