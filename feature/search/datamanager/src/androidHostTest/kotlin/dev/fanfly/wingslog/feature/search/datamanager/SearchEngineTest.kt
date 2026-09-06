package dev.fanfly.wingslog.feature.search.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.search.datamanager.impl.SearchEngineImpl
import dev.fanfly.wingslog.feature.search.model.Facet
import dev.fanfly.wingslog.feature.search.model.RecordAdapter
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.SearchField
import dev.fanfly.wingslog.feature.search.model.SearchHit
import dev.fanfly.wingslog.feature.search.model.TimeDirection
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.thing.ComponentType
import kotlinx.datetime.LocalDate
import org.junit.Test

class SearchEngineTest {

  private data class Record(
    val id: String,
    val title: String,
    val body: String = "",
    val component: ComponentType = ComponentType.COMPONENT_AIRFRAME,
    val date: LocalDate? = LocalDate(2026, 8, 1),
    val direction: TimeDirection = TimeDirection.PAST,
  )

  private class Adapter(override val nullDateMatches: Boolean = false) :
    RecordAdapter<Record> {
    override fun fields(item: Record) = listOf(
      SearchField("title", item.title, weight = 3),
      SearchField("body", item.body, weight = 1),
    )

    override fun component(item: Record) = item.component
    override fun date(item: Record) = item.date
    override fun direction(item: Record) = item.direction
    override fun facetMatches(item: Record, facet: Facet) =
      facet is Facet.Technician && item.body.contains(facet.name)
  }

  private val today = LocalDate(2026, 9, 6)
  private val engine: SearchEngine = SearchEngineImpl()

  private fun ids(hits: List<SearchHit<Record>>) = hits.map { it.item.id }

  private val records = listOf(
    Record(
      "gasket",
      "Replaced left magneto base gasket",
      component = ComponentType.COMPONENT_ENGINE,
      date = LocalDate(2026, 9, 1)
    ),
    Record(
      "xpdr",
      "Installed GTX 335 transponder",
      body = "Tested per 91.413",
      date = LocalDate(2026, 8, 25)
    ),
    Record(
      "oil",
      "Oil and filter change",
      body = "Phillips 20W-50",
      component = ComponentType.COMPONENT_ENGINE,
      date = LocalDate(2025, 7, 30)
    ),
    Record(
      "annual",
      "Annual inspection",
      body = "Transponder check included",
      date = LocalDate(2026, 3, 14)
    ),
  )

  @Test
  fun blankQuery_keepsCallerOrderAndEveryRecord() {
    val hits = engine.search(records, Adapter(), RecordFilter(), today)
    assertThat(ids(hits)).containsExactly("gasket", "xpdr", "oil", "annual")
      .inOrder()
    assertThat(hits.map { it.score }).containsExactly(0.0, 0.0, 0.0, 0.0)
  }

  @Test
  fun componentFilter_keepsOnlyThoseComponents() {
    val filter =
      RecordFilter(components = setOf(ComponentType.COMPONENT_ENGINE))
    assertThat(
      ids(
        engine.search(
          records,
          Adapter(),
          filter,
          today
        )
      )
    ).containsExactly("gasket", "oil")
      .inOrder()
  }

  @Test
  fun timeWindow_usesAdapterDateAndDirection() {
    val filter = RecordFilter(time = TimeWindow.LastMonths(3))
    assertThat(
      ids(
        engine.search(
          records,
          Adapter(),
          filter,
          today
        )
      )
    ).containsExactly("gasket", "xpdr")
      .inOrder()

    val due = listOf(
      Record(
        "soon",
        "Due soon",
        date = LocalDate(2026, 10, 1),
        direction = TimeDirection.FUTURE
      ),
      Record(
        "later",
        "Due later",
        date = LocalDate(2027, 5, 1),
        direction = TimeDirection.FUTURE
      ),
    )
    assertThat(
      ids(
        engine.search(
          due,
          Adapter(),
          filter,
          today
        )
      )
    ).containsExactly("soon")
  }

  @Test
  fun timeWindow_nullDate_followsAdapterPolicy() {
    val undated = listOf(Record("meter", "Oil change every 50 hr", date = null))
    val filter = RecordFilter(time = TimeWindow.LastMonths(12))
    assertThat(
      engine.search(
        undated,
        Adapter(nullDateMatches = false),
        filter,
        today
      )
    ).isEmpty()
    assertThat(
      ids(
        engine.search(
          undated,
          Adapter(nullDateMatches = true),
          filter,
          today
        )
      )
    ).containsExactly("meter")
    assertThat(
      ids(
        engine.search(
          undated,
          Adapter(nullDateMatches = false),
          RecordFilter(),
          today
        )
      )
    ).containsExactly("meter")
  }

  @Test
  fun facet_isAppliedThroughTheAdapter() {
    val filter = RecordFilter(facet = Facet.Technician("Phillips"))
    assertThat(
      ids(
        engine.search(
          records,
          Adapter(),
          filter,
          today
        )
      )
    ).containsExactly("oil")
  }

  @Test
  fun query_isCaseInsensitiveAndRequiresEveryToken() {
    assertThat(
      ids(
        engine.search(
          records,
          Adapter(),
          RecordFilter(query = "TRANSPONDER"),
          today
        )
      )
    )
      .containsExactly("xpdr", "annual")
    assertThat(
      ids(
        engine.search(
          records,
          Adapter(),
          RecordFilter(query = "transponder 91.413"),
          today
        )
      )
    )
      .containsExactly("xpdr")
    assertThat(
      engine.search(
        records,
        Adapter(),
        RecordFilter(query = "transponder nothing"),
        today
      )
    ).isEmpty()
  }

  @Test
  fun query_ranksHeavierFieldFirst_thenNewer() {
    // "transponder" is in xpdr's title (weight 3) but only annual's body (weight 1).
    val hits = engine.search(
      records,
      Adapter(),
      RecordFilter(query = "transponder"),
      today
    )
    assertThat(ids(hits)).containsExactly("xpdr", "annual")
      .inOrder()
    assertThat(hits[0].score).isEqualTo(3.0)
    assertThat(hits[1].score).isEqualTo(1.0)

    // Equal scores: newer first, whatever the input order.
    val tie = listOf(
      Record("old", "Oil change", date = LocalDate(2025, 1, 1)),
      Record("new", "Oil change", date = LocalDate(2026, 1, 1)),
    )
    assertThat(
      ids(
        engine.search(
          tie,
          Adapter(),
          RecordFilter(query = "oil"),
          today
        )
      )
    ).containsExactly("new", "old")
      .inOrder()
  }

  @Test
  fun query_andFilters_combine() {
    val filter = RecordFilter(
      query = "oil",
      components = setOf(ComponentType.COMPONENT_ENGINE),
      time = TimeWindow.LastMonths(3)
    )
    assertThat(engine.search(records, Adapter(), filter, today)).isEmpty()
    assertThat(
      ids(
        engine.search(
          records,
          Adapter(),
          filter.copy(time = TimeWindow.All),
          today
        )
      )
    ).containsExactly("oil")
  }

  @Test
  fun recordFilter_helpers() {
    val engineOnly =
      RecordFilter().toggleComponent(ComponentType.COMPONENT_ENGINE)
    assertThat(engineOnly.components).containsExactly(ComponentType.COMPONENT_ENGINE)
    assertThat(engineOnly.toggleComponent(ComponentType.COMPONENT_ENGINE).components).isEmpty()

    val full = RecordFilter(
      query = "oil",
      components = setOf(ComponentType.COMPONENT_ENGINE),
      time = TimeWindow.LastMonths(3)
    )
    assertThat(full.isActive).isTrue()
    assertThat(full.hasNonQueryFilter).isTrue()
    assertThat(full.withoutFilters()).isEqualTo(RecordFilter(query = "oil"))
    assertThat(RecordFilter(query = "oil").hasNonQueryFilter).isFalse()
    assertThat(RecordFilter().isActive).isFalse()
  }
}
