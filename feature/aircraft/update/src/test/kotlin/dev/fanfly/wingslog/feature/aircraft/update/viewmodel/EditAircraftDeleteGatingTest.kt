package dev.fanfly.wingslog.feature.aircraft.update.viewmodel

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Aircraft
import org.junit.Test

/**
 * Who may delete an aircraft. Deleting tears the whole share down for every member (§3.3), so it
 * belongs to the account the aircraft lives under — not to anyone merely holding the OWNER role.
 * The rules enforce this server-side; these cover the UI telling the same story.
 */
class EditAircraftDeleteGatingTest {

  private fun state(hostedByMe: Boolean, id: String = "ac-1") =
    EditAircraftUiState(aircraft = Aircraft(id = id), hostedByMe = hostedByMe)

  @Test
  fun host_canDelete() {
    assertThat(state(hostedByMe = true).canDelete).isTrue()
  }

  @Test
  fun coOwner_cannotDelete() {
    // A co-owner holds the same OWNER role as the host and may edit — but the aircraft is not
    // theirs to destroy, and the rules would reject their tombstone anyway.
    assertThat(state(hostedByMe = false).canDelete).isFalse()
  }

  @Test
  fun beforeOwnershipResolves_deleteIsHidden() {
    // hostedByMe defaults to false: offering Delete on the first frame and retracting it would be
    // worse than showing it a beat late.
    assertThat(EditAircraftUiState(aircraft = Aircraft(id = "ac-1")).canDelete).isFalse()
  }

  @Test
  fun unsavedNewAircraft_hasNothingToDelete() {
    assertThat(state(hostedByMe = true, id = "").canDelete).isFalse()
  }
}
