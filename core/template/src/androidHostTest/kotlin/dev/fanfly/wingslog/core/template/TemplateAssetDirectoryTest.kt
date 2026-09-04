package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * That `templates/` holds exactly one version of each preset — the check a **version bump is not**.
 *
 * #781 and #783 each bumped `custom` on their own branch, one adding a spec field and the other
 * `empty_states`. The two versions are different *files*, so git merged both cleanly and left
 * `custom.v3.pb` and `custom.v4.pb` side by side. Gradle keys the generated constant on the preset
 * id and takes the highest version it finds, so the later bump silently won and the app shipped a
 * `custom` with no empty-state copy. Nothing failed: the merge was clean, the asset tests each
 * compared one file against itself, and only a test that happened to require the dropped field
 * caught it.
 *
 * That last part is the reason this exists rather than a note in the README. The field that went
 * missing was covered; the next one may not be, and the failure mode is a preset losing a change
 * nobody deleted. Parallel branches bumping the same preset is the normal case, not the unlucky
 * one.
 *
 * Reads the directory rather than [dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates]
 * on purpose: by the time a template is a decoded constant the extra version is already gone, since
 * that is exactly what the generator did with it.
 */
class TemplateAssetDirectoryTest {

  private fun repoRoot(): File {
    var dir = File(System.getProperty("user.dir")!!)
    while (!File(dir, "settings.gradle.kts").exists()) {
      dir = checkNotNull(dir.parentFile) {
        "settings.gradle.kts not found above ${System.getProperty("user.dir")}"
      }
    }
    return dir
  }

  private val templateDir: File get() = File(repoRoot(), "core/template/templates")

  /** `custom.v5.pb` -> "custom", or null for anything not named that way. */
  private fun presetId(name: String): String? =
    Regex("""^(.+)\.v\d+\.(?:pb|textproto)$""").find(name)?.groupValues?.get(1)

  @Test
  fun eachPresetHasExactlyOneCompiledAsset() {
    val byPreset = templateDir.resolve("binary")
      .listFiles()
      .orEmpty()
      .mapNotNull { file -> presetId(file.name)?.let { it to file.name } }
      .groupBy({ it.first }, { it.second })

    assertThat(byPreset).isNotEmpty()
    byPreset.forEach { (preset, files) ->
      assertWithMessage(
        "$preset has ${files.size} compiled assets: ${files.sorted()}. Two branches almost " +
          "certainly bumped it in parallel — fold both changes into one new version and delete " +
          "the rest, because the generator takes the highest and drops the other silently.",
      ).that(files).hasSize(1)
    }
  }

  @Test
  fun eachPresetHasExactlyOneSourceTextProto() {
    // The .textproto is what a reader edits, so a stale one left behind is a second source of
    // truth for the same preset — the state #675 removed hand-written Kotlin to get out of.
    val byPreset = templateDir.listFiles()
      .orEmpty()
      .mapNotNull { file -> presetId(file.name)?.let { it to file.name } }
      .groupBy({ it.first }, { it.second })

    assertThat(byPreset).isNotEmpty()
    byPreset.forEach { (preset, files) ->
      assertWithMessage("$preset has ${files.size} text protos: ${files.sorted()}")
        .that(files).hasSize(1)
    }
  }

  @Test
  fun everyCompiledAssetHasItsSourceAndTheVersionsAgree() {
    // A .pb with no .textproto beside it is bytes nobody can review; the reverse is a template the
    // app does not carry. Both are what the parallel-bump merge produced.
    val binaries = templateDir.resolve("binary")
      .listFiles()
      .orEmpty()
      .map { it.name.removeSuffix(".pb") }
      .toSet()
    val sources = templateDir.listFiles()
      .orEmpty()
      .filter { it.name.endsWith(".textproto") }
      .map { it.name.removeSuffix(".textproto") }
      .toSet()

    assertThat(binaries).isEqualTo(sources)

    // And the version inside the file matches the version in its name, which is the other half of
    // "(id, version) always names the same bytes".
    sources.forEach { stem ->
      val declared = templateDir.resolve("$stem.textproto")
        .readLines()
        .firstNotNullOfOrNull { line ->
          Regex("""^version:\s*(\d+)$""").find(line.trim())?.groupValues?.get(1)?.toInt()
        }
      val named = stem.substringAfterLast(".v").toInt()
      assertWithMessage("$stem declares version $declared").that(declared).isEqualTo(named)
    }
  }
}
