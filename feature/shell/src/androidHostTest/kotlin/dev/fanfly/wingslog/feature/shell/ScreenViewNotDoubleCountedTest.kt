package dev.fanfly.wingslog.feature.shell

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * That no screen is counted twice.
 *
 * **The bug this exists for**, found in DebugView during #667: opening the squawk form emitted two
 * `screen_view` events — `squawk_edit/{aircraftId}/{squawkId}` from the root back-stack observer and
 * `squawk_form` from the screen's own `LaunchedEffect`. One screen open, counted twice, split across
 * two names, so neither series was the real number. The task and log forms had it too.
 *
 * Nothing catches this at build time, and it does not look like a bug in GA4 — it looks like traffic.
 *
 * The check is a repo scan rather than an assertion about `SELF_LOGGING_ROUTES`'s contents, because
 * the failure is a screen being *added* that logs its own view while its route stays unsuppressed.
 * A test that only pinned the current set would pass through exactly that change.
 */
class ScreenViewNotDoubleCountedTest {

  /**
   * Screens that call `logScreenView` themselves, by file name, and whether they are reached by a
   * route (so feeder 1 would also log them) or are shell state (so nothing else logs them).
   *
   * Adding a screen here is the deliberate step: if it is route-backed, its route must also go in
   * `SELF_LOGGING_ROUTES` in AppNavHelpers.kt, or this test fails.
   */
  private val knownSelfLoggingScreens = mapOf(
    // Route-backed: feeder 1 sees these, so their routes must be suppressed.
    "SquawkFormScreen.kt" to RouteBacked(
      listOf(
        "squawk_create/",
        "squawk_edit/"
      )
    ),
    "AddTaskScreen.kt" to RouteBacked(listOf("maintenance_task_create/")),
    "EditTaskScreen.kt" to RouteBacked(listOf("maintenance_task_edit/")),
    "MaintenanceLogFormScreen.kt" to
      RouteBacked(listOf("maintenance_log_create/", "maintenance_log_edit/")),
    // Shell state, not routes: the section and its tabs live under one `app` route, which is
    // suppressed for its own reason. Nothing double-counts these.
    "AdaptiveShellRoute.kt" to ShellState,
    "SquawkTab.kt" to ShellState,
    "MaintenanceTasksTab.kt" to ShellState,
  )

  private sealed interface Kind
  private data class RouteBacked(val routePrefixes: List<String>) : Kind
  private data object ShellState : Kind

  @Test
  fun everySelfLoggingScreenIsAccountedFor() {
    val found = repoRoot().walkTopDown()
      .filter { it.extension == "kt" && "/build/" !in it.path }
      .filter { file ->
        // The feeders themselves are how logging happens, not screens that self-log.
        file.name !in setOf(
          "ScreenTracking.kt",
          "AppNavHelpers.kt",
          "BrowserTitleAnalytics.kt"
        ) &&
          Regex("""analytics\.logScreenView\(""").containsMatchIn(file.readText())
      }
      .map { it.name }
      .toSortedSet()

    assertThat(found).containsExactlyElementsIn(knownSelfLoggingScreens.keys.toSortedSet())
  }

  @Test
  fun everyRouteBackedSelfLoggingScreenHasItsRouteSuppressed() {
    val suppressed = suppressedRoutesDeclaredInAppNavHelpers()

    val unsuppressed = knownSelfLoggingScreens
      .mapNotNull { (screen, kind) -> (kind as? RouteBacked)?.let { screen to it } }
      .flatMap { (screen, kind) ->
        kind.routePrefixes
          .filterNot { prefix -> suppressed.any { it.startsWith(prefix) } }
          .map { "$screen -> $it" }
      }

    assertThat(unsuppressed).isEmpty()
  }

  /**
   * Read back what `TrackRootScreenViews` actually suppresses. Reading the source rather than the
   * value keeps this test out of Compose — the declaration is `private`, and making it visible only
   * so a test can read it would be the tail wagging the dog.
   */
  private fun suppressedRoutesDeclaredInAppNavHelpers(): List<String> {
    val source = File(repoRoot(), SUPPRESS_DECL_PATH)
    assertThat(source.exists()).isTrue()

    val block = Regex(
      """SELF_LOGGING_ROUTES\s*=\s*setOf\((.*?)\)""",
      RegexOption.DOT_MATCHES_ALL
    )
      .find(source.readText())
      ?.groupValues
      ?.get(1)
    assertThat(block).isNotNull()

    // "Screen.AddSquawk.route" -> the route literal on that Screen object.
    return Regex("""Screen\.(\w+)\.route""").findAll(block!!)
      .map { it.groupValues[1] }
      .map { routeLiteralFor(it) }
      .toList()
  }

  /** The `Screen("…")` literal for a nav object, read from core/nav so a rename cannot drift. */
  private fun routeLiteralFor(screenObject: String): String {
    val nav = File(repoRoot(), SCREEN_PATH).readText()
    val match =
      Regex("""data object $screenObject\s*:?\s*\n?\s*Screen\("([^"]+)"\)""")
        .find(nav)
        ?: Regex("""data object $screenObject\s*:\s*Screen\("([^"]+)"\)""").find(
          nav
        )
    assertThat(match).isNotNull()
    return match!!.groupValues[1]
  }

  private fun repoRoot(): File {
    var dir = File(System.getProperty("user.dir")!!)
    while (!File(dir, "settings.gradle.kts").exists()) {
      dir = dir.parentFile ?: error("repo root not found")
    }
    return dir
  }

  private companion object {
    const val SUPPRESS_DECL_PATH =
      "feature/shell/src/commonMain/kotlin/dev/fanfly/wingslog/feature/shell/AppNavHelpers.kt"
    const val SCREEN_PATH =
      "core/nav/src/commonMain/kotlin/dev/fanfly/wingslog/core/nav/Screen.kt"
  }
}
