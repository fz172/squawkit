package dev.fanfly.wingslog.web

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.savedstate.read
import androidx.savedstate.savedState
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.ui.adaptive.ShellSection
import dev.fanfly.wingslog.feature.shell.ShellNavigationMirror
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.w3c.dom.PopStateEvent
import org.w3c.dom.events.Event

/**
 * Binds app navigation to browser history. Replaces the library's `bindToBrowserNavigation`,
 * which only sees the root NavController: the shell's tabs are ViewModel state and, in sidebar
 * tiers, Settings pages open on a nested NavController, so neither ever made a history entry and
 * Back skipped straight past them.
 *
 * Every history entry carries a [HistorySnapshot] of all three — root routes, shell section,
 * nested Settings routes — and a popstate re-applies whichever parts differ. The address bar shows
 * `#app/<section>`, `#app/settings/<page>`, or `#<route>` for other root destinations, and a reload
 * or pasted address restores from it.
 *
 * Suspends until [scope][coroutineScope] is cancelled; call from a `LaunchedEffect`.
 */
suspend fun bindToBrowserHistory(
  navController: NavController,
  mirror: ShellNavigationMirror,
) = coroutineScope {
  val binding = BrowserHistoryBinding(navController, mirror, this)

  // Reload or pasted address: the entry's own snapshot when we wrote one, else the fragment.
  binding.restoreFrom(window.history.state)
  launch {
    popStateEvents().collect { event -> binding.restoreFrom(event.state) }
  }
  launch {
    mirror.settingsNav.filterNotNull().collect(binding::onSettingsNavAttached)
  }
  val settingsStack = mirror.settingsNav.flatMapLatest { nav ->
    nav?.currentBackStack ?: flowOf(emptyList())
  }
  combine(navController.currentBackStack, mirror.section, settingsStack) { root, section, settings ->
    HistorySnapshot(
      rootRoutes = root.encodedRoutes(),
      section = section,
      // The nested root ("settings list") is implied by the section; only pages above it count.
      settingsRoutes =
        if (section == ShellSection.SETTINGS) settings.encodedRoutes().drop(1) else emptyList(),
    )
  }.collect(binding::record)
}

/** How long a popstate restore may take to settle before ordinary push behaviour resumes. */
private const val RESTORE_SETTLE_MS = 1_000L

private class BrowserHistoryBinding(
  private val navController: NavController,
  private val mirror: ShellNavigationMirror,
  private val scope: CoroutineScope,
) {
  private val appAddress = with(window.location) { origin + pathname }

  /** The snapshot a popstate is restoring; entries are replaced, not pushed, until the app matches it. */
  private var restoreTarget: String? = null
  private var restoreSettle: Job? = null

  /** Settings pages to reopen once the nested NavController composes. */
  private var pendingSettingsRoutes: List<String>? = null
  private var recordedOnce = false

  /** Re-applies the entry's snapshot, or the address fragment when the entry has none (typed URL). */
  fun restoreFrom(state: Any?) {
    val snapshot = (state as? String)?.let(HistorySnapshot::decode)
      ?: HistorySnapshot.fromFragment(window.location.hash)
      ?: return
    // A typed route the graph doesn't know must not kill the binding.
    runCatching { restore(snapshot) }
      .onFailure { console.warn("Can't restore ${window.location.hash}: ${it.message}") }
  }

  private fun restore(snapshot: HistorySnapshot) {
    restoreTarget = snapshot.encode()
    restoreSettle?.cancel()
    restoreSettle = scope.launch {
      delay(RESTORE_SETTLE_MS)
      restoreTarget = null
    }
    navController.syncTo(snapshot.rootRoutes)
    snapshot.section?.let(mirror::selectSection)
    pendingSettingsRoutes = null
    if (snapshot.section == ShellSection.SETTINGS) {
      val nav = mirror.settingsNav.value
      if (nav != null) nav.syncBelowRootTo(snapshot.settingsRoutes)
      else if (snapshot.settingsRoutes.isNotEmpty()) pendingSettingsRoutes = snapshot.settingsRoutes
    }
  }

  fun onSettingsNavAttached(nav: NavController) {
    val pending = pendingSettingsRoutes ?: return
    pendingSettingsRoutes = null
    nav.syncBelowRootTo(pending)
  }

  fun record(snapshot: HistorySnapshot) {
    // Before the shell has published a section the snapshot is incomplete; recording it would
    // leave a junk entry that Back lands on.
    if (snapshot.isShell && snapshot.section == null) return
    val state = snapshot.encode() ?: return
    val url = appAddress + snapshot.fragment()
    val target = restoreTarget
    when {
      !recordedOnce -> window.history.replaceState(state, "", url)
      target != null -> {
        window.history.replaceState(state, "", url)
        if (state == target) {
          restoreTarget = null
          restoreSettle?.cancel()
        }
      }
      window.history.state == state -> window.history.replaceState(state, "", url)
      else -> window.history.pushState(state, "", url)
    }
    recordedOnce = true
  }
}

/**
 * One history entry's worth of navigation: [rootRoutes] bottom-up with arguments filled in and
 * URL-encoded, the shell [section] when the shell is on top, and the Settings pages open above the
 * nested Settings root.
 */
private data class HistorySnapshot(
  val rootRoutes: List<String>,
  val section: ShellSection?,
  val settingsRoutes: List<String>,
) {
  val isShell: Boolean
    get() = rootRoutes.lastOrNull() == Screen.AdaptiveShell.route

  fun encode(): String? {
    if (rootRoutes.isEmpty()) return null
    return buildList {
      rootRoutes.forEach { add("$ROOT$it") }
      if (isShell) section?.let { add("$SECTION${it.name}") }
      if (isShell) settingsRoutes.forEach { add("$SETTINGS$it") }
    }.joinToString("\n")
  }

  fun fragment(): String {
    val top = rootRoutes.lastOrNull() ?: return ""
    if (!isShell) return "#$top"
    val section = section ?: return "#$top"
    val page = settingsRoutes.lastOrNull()
    return buildString {
      append("#$top/${section.name.lowercase()}")
      if (section == ShellSection.SETTINGS && page != null) append("/$page")
    }
  }

  companion object {
    private const val ROOT = "r:"
    private const val SECTION = "s:"
    private const val SETTINGS = "n:"

    fun decode(state: String): HistorySnapshot? {
      val lines = state.lines()
      val roots = lines.filter { it.startsWith(ROOT) }.map { it.removePrefix(ROOT) }
      if (roots.isEmpty()) return null
      val section = lines.firstOrNull { it.startsWith(SECTION) }
        ?.removePrefix(SECTION)
        ?.let { name -> ShellSection.entries.firstOrNull { it.name == name } }
      val settings = lines.filter { it.startsWith(SETTINGS) }.map { it.removePrefix(SETTINGS) }
      return HistorySnapshot(roots, section, settings)
    }

    /** A typed or reloaded address: `#app/<section>[/<settings page>]` or `#<root route>`. */
    fun fromFragment(hash: String): HistorySnapshot? {
      val fragment = hash.substringAfter('#', "")
      if (fragment.isEmpty()) return null
      val segments = fragment.split('/')
      val shell = Screen.AdaptiveShell.route
      if (segments.first() != shell) return HistorySnapshot(listOf(shell, fragment), null, emptyList())
      val section = segments.getOrNull(1)
        ?.let { name -> ShellSection.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        ?: ShellSection.DASHBOARD
      val page = segments.drop(2).joinToString("/").takeIf { it.isNotEmpty() && section == ShellSection.SETTINGS }
      return HistorySnapshot(listOf(shell), section, listOfNotNull(page))
    }
  }
}

/** Pops and navigates so this controller's stack (root included) reads [target]. */
private fun NavController.syncTo(target: List<String>) {
  val current = currentBackStack.value.encodedRoutes()
  var commonTail = -1
  for (index in target.indices) {
    if (index >= current.size || target[index] != current[index]) break
    commonTail = index
  }
  if (commonTail == -1) {
    current.firstOrNull()?.let { popBackStack(decodeURIComponent(it), inclusive = true) }
  } else {
    popBackStack(decodeURIComponent(current[commonTail]), inclusive = false)
  }
  target.drop(commonTail + 1).forEach { navigate(decodeURIComponent(it)) }
}

/** [syncTo] for a nested stack whose root is fixed: keeps its start destination, syncs the rest. */
private fun NavController.syncBelowRootTo(pages: List<String>) {
  val root = currentBackStack.value.encodedRoutes().firstOrNull() ?: return
  syncTo(listOf(root) + pages)
}

private fun List<NavBackStackEntry>.encodedRoutes(): List<String> =
  filter { it.destination !is NavGraph }.mapNotNull { it.encodedRoute() }

private val argPlaceholder = Regex("""\{.*?\}""")

/** The entry's route with its arguments filled in and URL-encoded, as `navigate` accepts. */
private fun NavBackStackEntry.encodedRoute(): String? {
  val route = destination.route ?: return null
  if (!route.contains(argPlaceholder)) return route
  val args = arguments ?: savedState()
  val typedValues = destination.arguments.mapValues { (name, arg) ->
    arg.type.serializeAsValue(arg.type[args, name])
  }
  return route.replace(argPlaceholder) { match ->
    val key = match.value.trim('{', '}')
    val value = typedValues[key] ?: args.read { getStringOrNull(key) ?: "" }
    encodeURIComponent(value)
  }
}

private fun popStateEvents(): Flow<PopStateEvent> = callbackFlow {
  val callback: (Event) -> Unit = { event -> (event as? PopStateEvent)?.let(::trySend) }
  window.addEventListener("popstate", callback)
  awaitClose { window.removeEventListener("popstate", callback) }
}

private external fun decodeURIComponent(str: String): String
private external fun encodeURIComponent(str: String): String
