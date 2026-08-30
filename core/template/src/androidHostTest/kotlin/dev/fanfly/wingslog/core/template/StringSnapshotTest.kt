package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * **Phase 2's whole safety argument** (#658, PRD §10, §14 "Dilution").
 *
 * Phase 2 converts ~189 strings from fixed text into format strings filled from a lexicon. The
 * claim that justifies doing that at all is that an aviation user's app stays *verbally identical*
 * — not "mostly the same". This is what holds that claim to account: a snapshot of every string as
 * it read **before** any conversion, compared against what the app renders after.
 *
 * ## The snapshot was captured before the conversion, deliberately
 *
 * A snapshot taken from converted code asserts only that the code matches itself. `string_snapshot
 * .tsv` was generated on the commit that introduced this test, while every string was still fixed
 * text — so it is independent evidence of what the app said, not a restatement of what it says.
 *
 * ## If this fails
 *
 * **Regenerating the snapshot is almost never the fix.** A failure means one of:
 *
 * - a conversion changed the rendered wording — a product change, revert or justify it;
 * - a string was edited without intent — the interesting case, and the one this catches;
 * - a string was deliberately reworded — then update the snapshot **in the same commit as the
 *   wording change**, so review sees both halves together.
 *
 * The one thing that destroys its value is regenerating it to get to green. Nothing downstream
 * would notice.
 *
 * ## It needs the Gradle input declaration in `build.gradle.kts`
 *
 * This reads `strings.xml` from the filesystem, which Gradle does not track. Without the
 * `repoStringResources` input wired up in this module's build file, the task is UP-TO-DATE
 * whenever `core/template` itself is unchanged — so it would be skipped on exactly the commit that
 * edits a string elsewhere, and a skipped task reports success. Do not remove that block as dead
 * configuration; it is what makes this test run at all.
 *
 * ## What it does not cover
 *
 * Recorded because a test that looks total and is not is worse than one whose limits are known:
 *
 * - **Two lexicon fields render nowhere today** — `ready_status` and `collection_label`. The app
 *   has no "Airworthy" string and shows "Fleet" only inside `no_fleet_title`, so nothing here can
 *   check them (see `AirplaneTemplate`).
 * - **OS notification channel names live outside the app.** `GROUNDED`'s display name is in system
 *   settings; this test cannot see it, which is why #663 pins the channel *id* separately.
 * - **It compares source, not pixels.** A string correct in `strings.xml` and passed to the wrong
 *   composable still renders wrongly, and this will pass.
 */
class StringSnapshotTest {

  private data class Entry(val module: String, val resource: String, val value: String)

  @Test
  fun everyStringStillReadsExactlyAsItDidBeforePhase2() {
    val snapshot = loadSnapshot()
    val current = readAllStrings()

    val missing = snapshot.keys - current.keys
    val added = current.keys - snapshot.keys
    val changed = snapshot.keys.intersect(current.keys)
      .filter { snapshot.getValue(it) != current.getValue(it) }
      .map { "$it\n    was: ${snapshot.getValue(it)}\n    now: ${current.getValue(it)}" }

    // Reported together rather than failing on the first, so one run shows the whole picture.
    assertThat(
      buildString {
        if (changed.isNotEmpty()) {
          appendLine("${changed.size} string(s) changed wording:")
          changed.take(20).forEach { appendLine("  $it") }
        }
        if (missing.isNotEmpty()) {
          appendLine("${missing.size} string(s) removed: ${missing.take(20)}")
        }
        if (added.isNotEmpty()) {
          // Additions are legitimate and common — a new feature adds strings. They are listed so
          // the snapshot is updated deliberately rather than drifting out of date silently.
          appendLine("${added.size} new string(s) not in the snapshot: ${added.take(20)}")
        }
      },
    ).isEmpty()
  }

  @Test
  fun theSnapshotItselfIsIntact() {
    // A truncated or empty snapshot would make the test above pass vacuously — the failure mode
    // where a broken guard looks exactly like a satisfied one.
    val snapshot = loadSnapshot()
    assertThat(snapshot.size).isAtLeast(900)
    assertThat(snapshot).containsKey("app:app_name")
    assertThat(snapshot.getValue("app:app_name")).isEqualTo("SquawkIt")
  }

  private fun loadSnapshot(): Map<String, String> =
    javaClass.classLoader!!.getResourceAsStream("string_snapshot.tsv")!!
      .bufferedReader()
      .readLines()
      .asSequence()
      .filterNot { it.startsWith("#") || it.isBlank() || it.startsWith("module\t") }
      .map { it.split("\t", limit = 3) }
      .filter { it.size == 3 }
      .associate { (module, resource, value) -> "$module:$resource" to value.unescape() }

  /**
   * Reads `strings.xml` from source rather than through `Res.string`.
   *
   * Deliberate: the resource accessors need a composition or a suspending call, and this test's
   * subject is the authored text, not the runtime lookup. Reading source also means the test sees
   * a string the moment it is written, without a build step in between.
   */
  private fun readAllStrings(): Map<String, String> {
    val stringRe = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    return repoRoot().walkTopDown()
      .filter { it.name == "strings.xml" && "/build/" !in it.path }
      .flatMap { file ->
        val module = file.path.removePrefix(repoRoot().path + "/").substringBefore("/src/")
        stringRe.findAll(file.readText()).map { m ->
          Entry(module, m.groupValues[1], m.groupValues[2])
        }
      }
      .associate { "${it.module}:${it.resource}" to it.value }
  }

  /**
   * Restores real newlines and tabs from the sentinels the snapshot stores them as.
   *
   * The snapshot uses U+0001 and U+0002 rather than backslash escapes because `strings.xml` itself
   * contains literal `\n` — a backslash followed by an `n`, Android's own escape — as authored
   * text. A backslash scheme cannot tell that from an escaped real newline: unescaping `\\` and
   * `\n` in either order mangles one case or the other, and the first version of this file turned
   * all 12 of those strings into multi-line ones. Control characters cannot occur in a string
   * resource, so there is nothing for them to collide with.
   */
  private fun String.unescape(): String = replace('\u0001', '\n').replace('\u0002', '\t')

  /** Walks up from the module directory until `settings.gradle.kts` appears. */
  private fun repoRoot(): File {
    var dir = File(System.getProperty("user.dir"))
    while (!File(dir, "settings.gradle.kts").exists()) {
      dir = requireNotNull(dir.parentFile) {
        "settings.gradle.kts not found above ${System.getProperty("user.dir")}"
      }
    }
    return dir
  }
}
