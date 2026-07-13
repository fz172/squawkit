package dev.fanfly.wingslog.web

import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import kotlinx.browser.document

private const val BRAND = "SquawkIt"

/**
 * Maps an analytics screen-name key to a human-readable browser-tab title. The keys are the same
 * values passed to [AnalyticsManager.logScreenView] across the web: NavController route *templates*
 * (e.g. `edit_aircraft/{aircraftId}`), shell-section keys (`shell/squawks`), and form-tab keys
 * (`task_form/...`). Keep this in sync with the route strings in [dev.fanfly.wingslog.core.nav.Screen]
 * and the `logScreenView` call sites in WebApp.kt and the feature forms. Unmapped keys fall back to a
 * best-effort prettified label so a new screen still gets a unique, readable title.
 */
fun webPageTitle(screenName: String): String {
  val page = when {
    screenName == "login" -> ""
    screenName == "settings_root" || screenName == "shell/settings" -> "Settings"
    screenName == "shell/dashboard" -> "Dashboard"
    screenName.startsWith("shell/squawks") -> "Squawks"
    screenName.startsWith("shell/tasks") -> "Tasks"
    screenName.startsWith("shell/logs") -> "Logbook"

    screenName == "sync_settings" -> "Sync"
    screenName == "export_logs" -> "Export logs"
    screenName == "export_history" -> "Export history"
    screenName == "feature_lab" -> "Feature Lab"
    screenName == "manage_technicians" -> "Technicians"
    screenName.startsWith("edit_technician") -> "Edit technician"

    screenName == "add_aircraft" -> "Add aircraft"
    screenName.startsWith("edit_aircraft") -> "Edit aircraft"

    screenName.startsWith("maintenance_task_create") -> "New task"
    screenName.startsWith("maintenance_task_edit") -> "Edit task"
    screenName.startsWith("task_form") -> "Task"

    screenName.startsWith("maintenance_log_create") -> "New log"
    screenName.startsWith("maintenance_log_edit") -> "Edit log"
    screenName.startsWith("log_form") -> "Log entry"

    screenName.startsWith("squawk_create") -> "New squawk"
    screenName.startsWith("squawk_edit") -> "Edit squawk"
    screenName.startsWith("squawk_form") -> "Squawk"

    else -> prettifyScreenName(screenName)
  }
  return if (page.isEmpty() || page == BRAND) BRAND else "$page · $BRAND"
}

/** Best-effort label for an unmapped key: drop path-param templates, then Title-Case the segments. */
private fun prettifyScreenName(screenName: String): String =
  screenName
    .substringBefore("/{")
    .replace('/', ' ')
    .replace('_', ' ')
    .split(' ')
    .filter { it.isNotEmpty() }
    .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }

/**
 * Decorates an [AnalyticsManager] so each page view also updates the browser tab title
 * (`document.title`) and attaches a friendly `page_title` to the logged event. Wrap the
 * Koin-provided manager once at the web root and provide the wrapped instance to `LocalAnalytics`,
 * so every screen-view call site (routes, shell sections, form tabs) funnels through it.
 */
class BrowserTitleAnalytics(
  private val delegate: AnalyticsManager,
) : AnalyticsManager {
  override fun logScreenView(screenName: String, params: Map<String, String>) {
    val title = webPageTitle(screenName)
    document.title = title
    delegate.logScreenView(screenName, params + ("page_title" to title))
  }

  // Only screen views carry a page title; other events pass straight through.
  override fun logEvent(name: String, params: Map<String, String>) =
    delegate.logEvent(name, params)

  override fun setAnalyticsCollectionEnabled(enabled: Boolean) =
    delegate.setAnalyticsCollectionEnabled(enabled)
}
