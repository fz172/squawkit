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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.LocalAnalytics
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.lifecycle.AppForegroundObserver
import dev.fanfly.wingslog.core.lifecycle.compose.AppForegroundEffect
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalTextSelectionLayers
import dev.fanfly.wingslog.core.ui.adaptive.compose.TextSelectionLayer
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.ui.theme.AppearanceController
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import dev.fanfly.wingslog.core.ui.theme.resolveDarkTheme
import dev.fanfly.wingslog.feature.login.AuthFlow
import dev.fanfly.wingslog.feature.sharing.update.RedeemHost
import dev.fanfly.wingslog.feature.shell.AdaptiveShellRoute
import dev.fanfly.wingslog.feature.shell.NavigateToLoginOnSignOut
import dev.fanfly.wingslog.feature.shell.PopToShellOnNotificationTap
import dev.fanfly.wingslog.feature.shell.ShellNavigationMirror
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
@Composable
fun WebApp() {
  val appearanceController: AppearanceController = koinInject()
  val currentThingTemplate: CurrentThingTemplate = koinInject()
  val appearanceMode by appearanceController.mode.collectAsState()
  val isDark = appearanceMode.resolveDarkTheme()
  LaunchedEffect(isDark) { updateBrowserGutterColor(isDark) }

  // App-session boundaries, at the root rather than in a nav destination (see AppForegroundEffect).
  // On this host it rides `document.visibilitychange`. Web carries no ads in v1, so nothing consumes
  // it here yet — it is installed for parity so the two hosts cannot drift.
  AppForegroundEffect(koinInject<AppForegroundObserver>())
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
        // Tabs and sidebar-tier Settings pages navigate outside the root controller; the history
        // binding reads them through this. See ShellBrowserHistory.kt.
        val shellNavigation = remember { ShellNavigationMirror() }

        NavigateToLoginOnSignOut(navController)
        PopToShellOnNotificationTap(navController)

        LaunchedEffect(browserNavigationBound) {
          if (browserNavigationBound) {
            bindToBrowserHistory(navController, shellNavigation)
          }
        }

        TrackRootScreenViews(navController, analytics)

        // Above the NavHost so the per-thing form dialogs see it too: they are root
        // destinations composed in DialogHost, a sibling of the shell (CurrentThingTemplate).
        val thingLexicon by currentThingTemplate.lexicon.collectAsState()
        val thingCapabilities by currentThingTemplate.capabilities.collectAsState()
      val thingTemplate by currentThingTemplate.template.collectAsState()
        CompositionLocalProvider(
          LocalAnalytics provides analytics,
          LocalThingLexicon provides thingLexicon,
          LocalThingCapabilities provides thingCapabilities,
          // The fields a template declares — its meters, slots and spec fields (#703).
          LocalThingTemplate provides thingTemplate,
          // Compose draws to a canvas, so the browser's own text selection never applies; without
          // this nothing on the page can be selected or copied. See TextSelectionLayer.
          LocalTextSelectionLayers provides true,
        ) {
          TextSelectionLayer {
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
              composable(Screen.AdaptiveShell.route) { entry ->
                AdaptiveShellRoute(
                  navController = navController,
                  shellEntry = entry,
                  navigationMirror = shellNavigation,
                )
              }
              formDialogs(navController)
              sharingRoutes(navController)
              // Compact tiers (no sidebar) open settings detail pages as full-screen routes.
              settingsDetailRoutes(navController)
            }
          }
          // App-root overlay for inbound share deep links (parked invites), above the nav graph.
          RedeemHost()
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
