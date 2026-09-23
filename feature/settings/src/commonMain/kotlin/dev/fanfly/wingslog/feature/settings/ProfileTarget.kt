package dev.fanfly.wingslog.feature.settings

/** What [SettingsViewModel.openProfile] resolved the profile card's tap to. */
sealed interface ProfileTarget {
  data class Self(val technicianId: String) : ProfileTarget

  /** No self record to edit yet — a guest who never named themselves. */
  data object Roster : ProfileTarget
}
