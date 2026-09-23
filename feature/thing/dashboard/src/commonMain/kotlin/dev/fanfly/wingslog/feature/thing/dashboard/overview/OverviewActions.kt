package dev.fanfly.wingslog.feature.thing.dashboard.overview

import dev.fanfly.wingslog.feature.thing.dashboard.ThingOverviewAction
import dev.fanfly.wingslog.feature.thing.dashboard.ThingOverviewUiState

/**
 * Wires an owner-only thing action: returns a click handler only when the caller may manage the
 * thing (owner, not a technician) and mutations are enabled; otherwise null so the affordance is
 * hidden. Server rules remain the enforcement (docs/sharing §6.3).
 */
internal fun manageAction(
  state: ThingOverviewUiState.Success,
  onMutationAction: ((ThingOverviewAction) -> Unit)?,
  action: () -> ThingOverviewAction,
): (() -> Unit)? =
  if (state.canManageThing && onMutationAction != null) {
    { onMutationAction(action()) }
  } else {
    null
  }

/**
 * Wires an action open to every member of the share, regardless of role. Manage Access is the one
 * such affordance: owners manage the roster there, while a technician sees it read-only and it is
 * their only route to leaving the share.
 *
 * Hidden from guests. Sharing needs a permanent account at both ends (PRD F1) — a share must attach
 * to an identity that survives a reinstall — so for a guest this is a door that leads only to a
 * sign-in prompt.
 */
internal fun memberAction(
  state: ThingOverviewUiState.Success,
  onMutationAction: ((ThingOverviewAction) -> Unit)?,
  action: () -> ThingOverviewAction,
): (() -> Unit)? =
  if (state.canOpenManageAccess && onMutationAction != null) {
    { onMutationAction(action()) }
  } else {
    null
  }
