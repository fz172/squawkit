package dev.fanfly.wingslog.feature.search.model

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.thing.ComponentType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class RecordFilterFlowsTest {

  @Test
  fun typingIsDebounced_structureAndClearAreImmediate() = runTest {
    val source = MutableStateFlow(RecordFilter())
    val seen = mutableListOf<RecordFilter>()
    val job = launch {
      source.debouncedQuery(150)
        .collect { seen += it }
    }
    advanceTimeBy(1.milliseconds)
    assertThat(seen).containsExactly(RecordFilter())

    source.value = RecordFilter(query = "t")
    source.value = RecordFilter(query = "tr")
    advanceTimeBy(100.milliseconds)
    assertThat(seen).hasSize(1)
    advanceTimeBy(100.milliseconds)
    assertThat(seen.last().query).isEqualTo("tr")
    assertThat(seen).hasSize(2)

    source.value = RecordFilter(
      query = "tr",
      components = setOf(ComponentType.COMPONENT_ENGINE)
    )
    advanceTimeBy(1.milliseconds)
    assertThat(seen.last()).isEqualTo(source.value)

    source.value =
      RecordFilter(components = setOf(ComponentType.COMPONENT_ENGINE))
    advanceTimeBy(1.milliseconds)
    assertThat(seen.last().query).isEmpty()
    job.cancel()
  }
}
