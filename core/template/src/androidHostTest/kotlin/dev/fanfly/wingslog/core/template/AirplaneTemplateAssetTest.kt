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
 * `.pb` is committed and `templates/compile-template.sh` regenerates it. This test is what keeps
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
    get() = File(repoRoot(), "core/template/templates/airplane.v1.pb")

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
   * **The one chance to prove the hand-written template was right before it is deleted.**
   *
   * `AirplaneTemplate.TEMPLATE` is still constructed in Kotlin at this commit. The `.textproto` was
   * transcribed from it by hand, and a transcription error — a dropped `is_identifier`, a swapped
   * plural, a slot in the wrong parent — would otherwise ship silently as a wrong label on a real
   * screen. Comparing the whole message is what makes that impossible: it covers every field,
   * including the ones no other test names.
   *
   * This assertion is why the deletion in the next commit is safe, and it is the reason the two
   * commits are separate rather than squashed.
   */
  @Test
  fun theCompiledAssetEqualsTheHandWrittenTemplate() {
    assertThat(decoded).isEqualTo(AirplaneTemplate.TEMPLATE)
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

  @Test
  fun theAssetCarriesNoUnknownFields() {
    // Unknown fields here would mean protoc encoded something this build's schema does not define,
    // which for a template compiled from this repo's own .proto can only be a stale .pb.
    assertThat(decoded.unknownFields.size).isEqualTo(0)
    assertThat(decoded.capabilities?.unknownFields?.size ?: 0).isEqualTo(0)
  }
}
