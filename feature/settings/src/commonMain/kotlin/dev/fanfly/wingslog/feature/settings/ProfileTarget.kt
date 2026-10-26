package dev.fanfly.wingslog.feature.settings

import dev.fanfly.wingslog.id.TechnicianId

/** What [SettingsViewModel.openProfile] resolved the profile card's tap to. */
sealed interface ProfileTarget {
  data class Self(val technicianId: TechnicianId) : ProfileTarget

  /** No self record to edit yet — a guest who never named themselves. */
  data object Roster : ProfileTarget
}
