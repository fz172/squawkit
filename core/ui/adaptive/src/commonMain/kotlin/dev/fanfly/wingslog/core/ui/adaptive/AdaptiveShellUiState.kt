package dev.fanfly.wingslog.core.ui.adaptive

/** Plain UI state for [AdaptiveAppShell]; produced by a host-side ViewModel. */
data class AdaptiveShellUiState(
  val things: List<ShellThing> = emptyList(),
  val selectedThingId: String? = null,
  val section: ShellSection = ShellSection.DASHBOARD,
  /** Current user's display name + photo, for the sidebar account/settings entry. */
  val accountName: String? = null,
  val accountPhotoUrl: String? = null,
) {
  val selectedThing: ShellThing?
    get() = things.firstOrNull { it.id == selectedThingId }
}
