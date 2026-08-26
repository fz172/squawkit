package dev.fanfly.wingslog.feature.notifications.datamanager

import dev.fanfly.wingslog.core.model.settings.NotificationSettings
import kotlinx.coroutines.flow.Flow

/**
 * Resolution of [NotificationPrefsManager.observe] against local hydration state (design §4.3).
 *
 * **Not the same question as "signed in or not."** A signed-out user, or one with cloud sync off,
 * resolves immediately (there is either nothing to sync, or it will never sync) — [Unresolved] means
 * specifically "signed in, cloud sync on, and this device has not yet learned whether a preferences
 * doc exists." [DeveloperOptionsManagerImpl] never needs this distinction because
 * `CollectionKind.DeveloperOptions` is not in `SyncEngine.TOP_LEVEL_KINDS` and so never hydrates;
 * `NotificationSettings` is, on purpose (§4.2), which is exactly what makes this state reachable.
 */
sealed interface PrefsState {
  /**
   * Signed in with cloud sync on, and this device has not yet learned whether a preferences doc
   * exists on the account. Reading through this as if it were "all on" would show the wrong toggles;
   * **writing** through it would push a whole-message overwrite that silently reverts whatever the
   * user already set on another device — `NotificationSettings.copy` has no field-level merge, so an
   * update built from a guessed baseline clobbers every field the guess got wrong.
   */
  data object Unresolved : PrefsState

  data class Resolved(val settings: NotificationSettings) : PrefsState
}

/**
 * Reads and writes account-level notification preferences (design §4.3). The `*_disabled` proto
 * fields are never touched directly by callers — see `NotificationSettingsExt` in `:model` for the
 * readable names, and mutate through those, not by constructing a [NotificationSettings] by hand.
 */
interface NotificationPrefsManager {
  fun observe(): Flow<PrefsState>

  /**
   * Copies [mutate] onto the currently [PrefsState.Resolved] value and persists it. Fails —
   * writing nothing — while [PrefsState.Unresolved]: a write from an unresolved state is exactly
   * the whole-message overwrite [PrefsState.Unresolved]'s own doc explains.
   */
  suspend fun update(mutate: (NotificationSettings) -> NotificationSettings): Result<Unit>
}
