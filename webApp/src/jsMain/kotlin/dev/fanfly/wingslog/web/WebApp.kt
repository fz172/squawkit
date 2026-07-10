package dev.fanfly.wingslog.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.bindToBrowserNavigation
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.LocalAnalytics
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.ui.theme.AppearanceController
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import dev.fanfly.wingslog.core.ui.theme.resolveDarkTheme
import dev.fanfly.wingslog.feature.login.AuthFlow
import dev.fanfly.wingslog.feature.shell.AdaptiveShellRoute
import dev.fanfly.wingslog.feature.shell.NavigateToLoginOnSignOut
import dev.fanfly.wingslog.feature.shell.TrackRootScreenViews
import dev.fanfly.wingslog.feature.shell.formDialogs
import dev.fanfly.wingslog.feature.shell.settingsDetailRoutes
import dev.fanfly.wingslog.feature.shell.sharingRoutes
import kotlinx.browser.document
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.rememberResourceEnvironment
import org.koin.compose.koinInject
import org.w3c.dom.HTMLElement
import wingslog.core.sharedassets.generated.resources.app_name
import wingslog.core.sharedassets.generated.resources.Res as UiRes

/**
 * Web host entry. The navigation graph itself (shell route, form dialogs, settings detail
 * pages) is shared with composeApp via `feature:shell`; this host adds the browser-history
 * binding, the resource warm-up workaround, the SEO login landing page, the browser gutter
 * color, and the tab-retitling analytics wrapper.
 */
@OptIn(ExperimentalBrowserHistoryApi::class)
@Composable
fun WebApp() {
  val appearanceController: AppearanceController = koinInject()
  val appearanceMode by appearanceController.mode.collectAsState()
  val isDark = appearanceMode.resolveDarkTheme()
  LaunchedEffect(isDark) { updateBrowserGutterColor(isDark) }
  // Read packed string resources via whole-file fetches instead of HTTP Range requests, which
  // Firebase Hosting's gzip breaks. Must wrap the resource warm-up below. See WholeFileResourceReader.
  ProvideWholeFileResourceReader {
    WingslogTheme(darkTheme = isDark) {
      Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
      ) {
        // Web fetches the compose-resources string tables lazily, so on first paint stringResource()
        // can return "" — shell tabs and other labels render blank until a manual refresh warms the
        // cache. Warm the shared table once up front (using the same ResourceEnvironment the UI reads
        // from, so the cache key matches) and hold content until it's ready, so labels are populated
        // on the first composition. The themed Surface stays as the background during this brief load.
        val resourceEnvironment = rememberResourceEnvironment()
        var resourcesReady by remember { mutableStateOf(false) }
        LaunchedEffect(resourceEnvironment) {
          runCatching { getString(resourceEnvironment, UiRes.string.app_name) }
          resourcesReady = true
        }
        if (!resourcesReady) return@Surface

        val navController = rememberNavController()
        val appCapability: AppCapability = koinInject()
        // Wrap the platform manager so every screen view also retitles the browser tab (and tags the
        // event with page_title). Wrapping once + providing it to LocalAnalytics covers all call sites.
        val baseAnalytics: AnalyticsManager = koinInject()
        val analytics =
          remember(baseAnalytics) { BrowserTitleAnalytics(baseAnalytics) }
        var browserNavigationBound by remember { mutableStateOf(false) }

        NavigateToLoginOnSignOut(navController)

        LaunchedEffect(browserNavigationBound) {
          if (browserNavigationBound) {
            navController.bindToBrowserNavigation()
          }
        }

        TrackRootScreenViews(navController, analytics)

        CompositionLocalProvider(LocalAnalytics provides analytics) {
          NavHost(
            navController = navController,
            startDestination = Screen.Login.route,
          ) {
            composable(Screen.Login.route) {
              AuthFlow(
                onComplete = {
                  navController.navigate(Screen.AdaptiveShell.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                  }
                  browserNavigationBound = true
                },
                // Web swaps the shared LoginScreen for the SEO landing page; the onboarding tail
                // (name entry + welcome) and Firebase auth wiring are reused unchanged.
                loginContent = { onLoginSuccess, onChooseEmail ->
                  WebLoginLandingScreen(
                    onLoginSuccess = onLoginSuccess,
                    onChooseEmail = onChooseEmail,
                  )
                },
              )
            }
            composable(Screen.AdaptiveShell.route) {
              AdaptiveShellRoute(
                navController = navController,
                isStressTestSupported = appCapability.isStressTestSupported,
              )
            }
            formDialogs(navController)
            sharingRoutes(navController)
            // Compact tiers (no sidebar) open settings detail pages as full-screen routes.
            settingsDetailRoutes(
              navController,
              appCapability.isStressTestSupported
            )
          }
        }
      }
    }
  }
}

private fun updateBrowserGutterColor(isDark: Boolean) {
  val color = if (isDark) "#211F26" else "#F3EDF7"
  (document.documentElement as? HTMLElement)?.style?.background = color
  document.body?.style?.background = color
}
