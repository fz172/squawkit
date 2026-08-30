package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.thing.Lexicon
import org.junit.Test
import java.io.File

/**
 * **Phase 2's whole safety argument** (#658, PRD §10, §14 "Dilution").
 *
 * Phase 2C converts ~189 strings from fixed text into format strings filled from a lexicon. The
 * claim that justifies doing that at all is that an aviation user's app stays *verbally identical*
 * — not "mostly the same". This holds that claim to account: a snapshot of every string as it read
 * **before** any conversion, compared against what the airplane lexicon renders after.
 *
 * ## It compares renders, not source
 *
 * `"Add aircraft"` becoming `"Add %1${'$'}s"` is the expected outcome of #656, not a regression, so
 * comparing `strings.xml` against the snapshot directly would fail on every correct conversion.
 * Instead each converted string is rendered with [AirplaneTemplate.AIRPLANE_LEXICON] and *that* is
 * compared. `"Add %1${'$'}s"` filled with the `thing` singular must produce exactly `"Add aircraft"`
 * again — including its case, which is why the recipe names a formatter rather than just a slot.
 *
 * ## The snapshot was captured before the conversion, deliberately
 *
 * A snapshot taken from converted code asserts only that the code matches itself. `string_snapshot
 * .tsv` was generated while every string was still fixed text, so it is independent evidence of
 * what the app said rather than a restatement of what it says.
 *
 * ## Converting a string requires declaring its recipe
 *
 * [LEXICON_ARGS] maps a resource to the lexicon-derived arguments that fill it. A string whose
 * value changed but which has no entry **fails the test** — so a conversion cannot quietly drop out
 * of coverage by being converted, which is the one way this guard could be defeated while still
 * looking green.
 *
 * ## It needs the Gradle input declaration in `build.gradle.kts`
 *
 * This reads `strings.xml` from the filesystem, which Gradle does not track. Without the
 * `repoStringResources` input wired up in this module's build file, the task is UP-TO-DATE whenever
 * `core/template` itself is unchanged — so it would be skipped on exactly the commit that edits a
 * string elsewhere, and a skipped task reports success. Do not remove that block as dead
 * configuration; it is what makes this test run at all.
 *
 * ## If this fails
 *
 * **Regenerating the snapshot is almost never the fix.** A failure means one of:
 *
 * - a conversion changed the rendered wording — a product change, revert it or justify it;
 * - a string was edited without intent — the interesting case, and the one this catches;
 * - a string was deliberately reworded — then update the snapshot **in the same commit as the
 *   wording change**, so review sees both halves together.
 *
 * The one thing that destroys its value is regenerating it to get to green. Nothing downstream
 * would notice.
 *
 * ## What it does not cover
 *
 * Recorded because a test that looks total and is not is worse than one whose limits are known:
 *
 * - **It checks the recipe, not the call site.** [LEXICON_ARGS] says `add_aircraft` is filled with
 *   `sentenceCase(thing)`; a call site passing `titleCase(thing)` instead still passes here. The
 *   two are written in the same commit, which is the mitigation, not a proof.
 * - **Two lexicon fields render nowhere today** — `ready_status` and `collection_label`. The app
 *   has no "Airworthy" string and shows "Fleet" only inside `no_fleet_title`, so nothing here can
 *   check them (see [AirplaneTemplate]).
 * - **OS notification channel names live outside the app.** `GROUNDED`'s display name is in system
 *   settings; this test cannot see it, which is why #663 pins the channel *id* separately.
 * - **It compares source, not pixels.** A string correct in `strings.xml` but passed to the wrong
 *   composable still renders wrongly, and this will pass.
 */
class StringSnapshotTest {

  /**
   * How the airplane lexicon fills each converted string, by argument position.
   *
   * Keyed `module:resource`, valued by the positional arguments the lexicon supplies — `3 to "…"`
   * fills `%3${'$'}s`. Positions the *caller* supplies at runtime (a thing's name, a count) are
   * deliberately absent and stay as literal placeholders on both sides of the comparison, because
   * the snapshot recorded them that way too.
   *
   * Empty until #656 begins converting. Each conversion adds its entry in the same commit.
   */
  private val LEXICON_ARGS: Map<String, (Lexicon) -> Map<Int, String>> = mapOf()

  private data class Entry(
    val module: String,
    val resource: String,
    val value: String
  )

  @Test
  fun everyStringStillReadsExactlyAsItDidBeforePhase2() {
    val snapshot = loadSnapshot()
    val current = readAllStrings()
    val lexicon = AirplaneTemplate.AIRPLANE_LEXICON

    val missing = snapshot.keys - current.keys
    val added = current.keys - snapshot.keys
    val changed = mutableListOf<String>()
    val undeclared = mutableListOf<String>()

    for (key in snapshot.keys.intersect(current.keys)) {
      val was = snapshot.getValue(key)
      val recipe = LEXICON_ARGS[key]
      val now = current.getValue(key)
        .let { if (recipe == null) it else it.fill(recipe(lexicon)) }
      if (was == now) continue
      // A string that changed with no recipe was converted without declaring how it renders —
      // reported separately because the fix is different: declare it, don't revert it.
      if (recipe == null && current.getValue(key) != was) undeclared += key
      changed += "$key\n    was: $was\n    now: $now"
    }

    // Reported together rather than failing on the first, so one run shows the whole picture.
    assertThat(
      buildString {
        if (changed.isNotEmpty()) {
          appendLine("${changed.size} string(s) no longer render as they did:")
          changed.take(20)
            .forEach { appendLine("  $it") }
        }
        if (undeclared.isNotEmpty()) {
          appendLine(
            "${undeclared.size} of those have no LEXICON_ARGS entry. If they were converted, add " +
              "the recipe here in the same commit: ${undeclared.take(20)}",
          )
        }
        if (missing.isNotEmpty()) {
          appendLine("${missing.size} string(s) removed: ${missing.take(20)}")
        }
        if (added.isNotEmpty()) {
          // Additions are legitimate and common — a new feature adds strings. They are listed so
          // the snapshot is updated deliberately rather than drifting out of date silently.
          appendLine(
            "${added.size} new string(s) not in the snapshot: ${
              added.take(
                20
              )
            }"
          )
        }
      },
    ).isEmpty()
  }

  @Test
  fun theRenderPathReconstructsTheOriginalWording() {
    // While LEXICON_ARGS is empty this is the substitution's only coverage, and an unproven
    // substitution would leave every future conversion's evidence resting on untested machinery.
    // Wire generates message fields as nullable; the airplane lexicon populates all of them.
    val lexicon = AirplaneTemplate.AIRPLANE_LEXICON

    // Mid-sentence, so the bare singular — not sentenceCase, which would render "Add Aircraft".
    // Getting this wrong on the first example written is exactly the mistake the test is here for.
    assertThat("Add %1\$s".fill(mapOf(1 to lexicon.thing!!.singular)))
      .isEqualTo("Add aircraft")

    // A title, where the case does come from a formatter.
    assertThat("%1\$s".fill(mapOf(1 to LexiconFormatter.titleCase(lexicon.down_status_long))))
      .isEqualTo("Aircraft on Ground")

    // Caller-supplied positions must survive untouched: the snapshot recorded them as placeholders,
    // so the comparison only works if both sides still carry them literally.
    assertThat(
      "%1\$s: %2\$d %3\$s increased in priority"
        .fill(mapOf(3 to LexiconFormatter.plural(lexicon.squawk!!))),
    ).isEqualTo("%1\$s: %2\$d squawks increased in priority")

    // Two-digit positions must not be truncated by a prefix match on %1$s.
    assertThat("%1\$s %11\$s".fill(mapOf(11 to "x", 1 to "y"))).isEqualTo("y x")
  }

  @Test
  fun everyRecipeNamesAStringThatExists() {
    // A recipe whose resource was renamed or deleted stops doing anything, and its string silently
    // leaves coverage — the same failure as never declaring it.
    val current = readAllStrings()
    assertThat(LEXICON_ARGS.keys - current.keys).isEmpty()
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

  /** Substitutes `%N${'$'}s` for the positions the lexicon supplies, leaving caller args alone. */
  private fun String.fill(args: Map<Int, String>): String =
    args.entries.fold(this) { acc, (position, value) ->
      acc.replace("%$position\$s", value)
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
    val stringRe = Regex(
      """<string name="([^"]+)"[^>]*>(.*?)</string>""",
      RegexOption.DOT_MATCHES_ALL
    )
    return repoRoot().walkTopDown()
      .filter { it.name == "strings.xml" && "/build/" !in it.path }
      .flatMap { file ->
        val module = file.path.removePrefix(repoRoot().path + "/")
          .substringBefore("/src/")
        stringRe.findAll(file.readText())
          .map { m ->
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
  private fun String.unescape(): String =
    replace('\u0001', '\n').replace('\u0002', '\t')

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
