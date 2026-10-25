package dev.fanfly.wingslog.feature.squawk.dashboard

import com.google.common.truth.Truth.assertThat
import com.squareup.wire.Instant
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.thing.Squawk
import dev.fanfly.wingslog.thing.SquawkPriority
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val THING_ID = "thing-1"

@OptIn(ExperimentalCoroutinesApi::class)
class SquawkOverviewViewModelTest {

  private val squawkManager: SquawkManager = mockk()

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  @Test
  fun computesOpenAogAndNewestThreeOpenSquawks() = runTest {
    val openAog = Squawk(
      id = "s-aog",
      title = "Mag drop",
      priority = SquawkPriority.SQUAWK_PRIORITY_AOG,
      created_at = Instant.ofEpochSecond(400L, 0),
    )
    val addressedAog = Squawk(
      id = "s-aog-closed",
      title = "Old mag drop",
      priority = SquawkPriority.SQUAWK_PRIORITY_AOG,
      addressed_by_log_id = "log-1",
      created_at = Instant.ofEpochSecond(500L, 0),
    )
    val open1 = Squawk(id = "s-1", title = "1", created_at = Instant.ofEpochSecond(100L, 0))
    val open2 = Squawk(id = "s-2", title = "2", created_at = Instant.ofEpochSecond(200L, 0))
    val open3 = Squawk(id = "s-3", title = "3", created_at = Instant.ofEpochSecond(300L, 0))
    every { squawkManager.observeSquawks(THING_ID) } returns flowOf(
      listOf(open1, open2, open3, openAog, addressedAog)
    )

    val state = SquawkOverviewViewModel(squawkManager, THING_ID).uiState.value

    assertThat(state.loaded).isTrue()
    assertThat(state.aogSquawks).containsExactly(openAog)
    assertThat(state.openPreview.map { it.squawk.id })
      .containsExactly("s-aog", "s-3", "s-2")
      .inOrder()
  }
}
