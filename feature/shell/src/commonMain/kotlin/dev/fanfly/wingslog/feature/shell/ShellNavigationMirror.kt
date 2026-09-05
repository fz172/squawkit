package dev.fanfly.wingslog.feature.shell

import androidx.navigation.NavController
import dev.fanfly.wingslog.core.ui.adaptive.ShellSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The shell's navigation state that lives outside the root NavController: the selected section
 * (ViewModel state) and, on sidebar tiers, the nested Settings NavController. A host that has to
 * mirror navigation somewhere else — the web host binds it into browser history — reads and drives
 * it through this; hosts that don't need it pass none to [AdaptiveShellRoute].
 *
 * [section] and [settingsNav] are null while the owning composable is not on screen.
 */
class ShellNavigationMirror {
  private val _section = MutableStateFlow<ShellSection?>(null)
  val section: StateFlow<ShellSection?> = _section.asStateFlow()

  private val _settingsNav = MutableStateFlow<NavController?>(null)
  val settingsNav: StateFlow<NavController?> = _settingsNav.asStateFlow()

  private var selectHandler: ((ShellSection) -> Unit)? = null
  private var pendingSection: ShellSection? = null

  /** Switches the shell's section; held until the shell attaches if it isn't composed yet. */
  fun selectSection(section: ShellSection) {
    val handler = selectHandler
    if (handler == null) pendingSection = section else handler(section)
  }

  internal fun attachShell(select: (ShellSection) -> Unit) {
    selectHandler = select
    pendingSection?.let {
      pendingSection = null
      select(it)
    }
  }

  internal fun detachShell() {
    selectHandler = null
    _section.value = null
  }

  internal fun publishSection(section: ShellSection) {
    _section.value = section
  }

  internal fun attachSettingsNav(navController: NavController) {
    _settingsNav.value = navController
  }

  internal fun detachSettingsNav(navController: NavController) {
    _settingsNav.compareAndSet(navController, null)
  }
}
