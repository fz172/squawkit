package dev.fanfly.wingslog.feature.thing.dashboard.data

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.thing.Thing
import org.junit.Test

/**
 * Owner-only affordance gating (Edit Aircraft / Delete / Manage Access). A technician on a shared
 * thing gets a read-only screen; owners (and own thing, resolved to OWNER) may manage.
 */
class ThingOverviewGatingTest {

  private fun state(role: ShareRole?) =
    ThingOverviewUiState.Success(
      thing = Thing(id = "ac-1"),
      myRole = role
    )

  @Test
  fun owner_canManage() {
    assertThat(state(ShareRole.OWNER).canManageAircraft).isTrue()
  }

  @Test
  fun technician_cannotManage() {
    assertThat(state(ShareRole.TECHNICIAN).canManageAircraft).isFalse()
  }

  @Test
  fun unresolvedRole_defaultsToManageable() {
    // null only appears briefly before the role resolves; own thing resolve to OWNER, so
    // defaulting to manageable avoids hiding an owner's controls on first frame.
    assertThat(state(null).canManageAircraft).isTrue()
  }

  // --- Sharing is not available to a guest (PRD F1) ---

  @Test
  fun anonymous_cannotOpenManageAccess() {
    // Redeeming and inviting both require a permanent account, and a share must attach to an
    // identity that survives a reinstall. Showing a guest the entry point offers a door that leads
    // only to a sign-in prompt.
    val state = ThingOverviewUiState.Success(
      thing = Thing(id = "ac-1"),
      myRole = ShareRole.OWNER,
      isAnonymous = true,
    )

    assertThat(state.canOpenManageAccess).isFalse()
  }

  @Test
  fun signedIn_canOpenManageAccess_whateverTheirRole() {
    // A technician gets it too: it is read-only for them, and it is their only route out of a share.
    assertThat(
      ThingOverviewUiState.Success(
        thing = Thing(id = "ac-1"),
        myRole = ShareRole.TECHNICIAN,
        isAnonymous = false,
      ).canOpenManageAccess,
    ).isTrue()
    assertThat(
      ThingOverviewUiState.Success(
        thing = Thing(id = "ac-1"),
        myRole = ShareRole.OWNER,
        isAnonymous = false,
      ).canOpenManageAccess,
    ).isTrue()
  }
}
