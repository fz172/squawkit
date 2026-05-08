package dev.fanfly.wingslog.core.storage.blob

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RemoteStateCoverageTest {

  private val allKnownStates: List<RemoteState> = listOf(
    RemoteState.LocalOnly,
    RemoteState.Uploading,
    RemoteState.Synced,
    RemoteState.RemoteOnly,
  )

  @Test
  fun every_known_state_is_in_ALL_and_sizes_match() {
    for (state in allKnownStates) {
      assertThat(RemoteState.ALL).contains(state)
    }
    assertThat(RemoteState.ALL).hasSize(allKnownStates.size)
  }

  @Test
  fun every_state_has_unique_wireName() {
    val wireNames = RemoteState.ALL.map { it.wireName }
    assertThat(wireNames.toSet()).hasSize(RemoteState.ALL.size)
  }

  @Test
  fun fromWire_round_trips_for_every_state() {
    for (state in RemoteState.ALL) {
      assertThat(RemoteState.fromWire(state.wireName)).isSameInstanceAs(state)
    }
  }

  @Test
  fun fromWire_unknown_wire_name_throws() {
    var threw = false
    try {
      RemoteState.fromWire("totally_unknown_state")
    } catch (e: IllegalStateException) {
      threw = true
    }
    assertThat(threw).isTrue()
  }
}
