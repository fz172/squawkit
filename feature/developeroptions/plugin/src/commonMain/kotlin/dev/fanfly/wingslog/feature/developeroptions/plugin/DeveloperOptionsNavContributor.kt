package dev.fanfly.wingslog.feature.developeroptions.plugin

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder

/**
 * Nav destinations a developer-only feature adds to the settings graph.
 *
 * The counterpart to [DeveloperOptionsExtra]: that one contributes the *row*, this one contributes
 * the *page the row opens*. Without it a feature can add its section without the shell knowing, but
 * the shell still has to register the screen behind it — so `feature:shell` kept a compile
 * dependency on a developer-only module (`feature:stresstest:config`) purely to call its route
 * registration.
 *
 * Resolved through Koin by `ShellNavGraph.settingsDetailRoutes`, which is a `NavGraphBuilder`
 * extension rather than a `@Composable` — so resolution goes through `KoinPlatform.getKoin()`, not
 * `org.koin.compose.getKoin()`. `MainViewController.kt` already reaches Koin that way from
 * non-composable code.
 *
 * **Deliberately scoped to developer options rather than named `NavGraphContributor`.** The shell's
 * fan-out to product feature modules is intentional — its build file calls that its "aggregator role
 * for composables/nav" — and inverting all of it is a much larger question. This solves the narrow
 * case: a *developer-only, capability-gated* feature has no business in the central graph. If it
 * later earns generalizing, it moves to a `core:nav:plugin` sibling.
 *
 * Note this does not put contributed routes into `core:nav`'s `Screen`, so they stay outside that
 * index — as `STRESS_TEST_ROUTE` already was. It does not make that worse; moving the constants is a
 * separate change.
 */
interface DeveloperOptionsNavContributor {

  /**
   * False registers nothing. The capability gate lives with the feature that owns the destination,
   * which is why the shell no longer needs `isStressTestSupported` to decide for it.
   */
  fun isAvailable(): Boolean = true

  /** Called inside the settings graph builder. Register with `builder.composable(route) { … }`. */
  fun register(builder: NavGraphBuilder, navController: NavController)
}
