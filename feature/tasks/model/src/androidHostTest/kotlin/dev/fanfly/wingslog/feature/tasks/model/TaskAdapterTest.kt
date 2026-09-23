package dev.fanfly.wingslog.feature.tasks.model

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.feature.search.model.Facet
import dev.fanfly.wingslog.feature.search.model.TimeDirection
import dev.fanfly.wingslog.thing.ComplianceType
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceTask
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.junit.Test

class TaskAdapterTest {

  private fun at(date: String) =
    toWireInstant(Instant.parse("${date}T12:00:00Z").epochSeconds)

  private val card = MaintenanceTask(
    id = "t1",
    title = "Transponder and altimeter test",
    notes = "Two-year IFR certification",
    reference_number = "91.413",
    component = ComponentType.COMPONENT_AIRFRAME,
  )

  @Test
  fun task_fieldsAndComponent() {
    val adapter = TaskAdapter()
    val item = MaintenanceTaskWithStatus(card, DueMetadata())
    assertThat(
      adapter.fields(item)
        .map { it.name })
      .containsExactly(
        "reference_number",
        "title",
        "notes",
        "compliance_authority",
        "compliance_details"
      )
      .inOrder()
    assertThat(adapter.component(item)).isEqualTo(ComponentType.COMPONENT_AIRFRAME)
    assertThat(adapter.nullDateMatches).isTrue()
  }

  @Test
  fun task_complianceFacet() {
    val adapter = TaskAdapter()
    val ad = MaintenanceTaskWithStatus(
      card.copy(type = ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE),
      DueMetadata()
    )
    assertThat(
      adapter.facetMatches(
        ad,
        Facet.Compliance(ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE)
      )
    ).isTrue()
    assertThat(
      adapter.facetMatches(
        ad,
        Facet.Compliance(ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN)
      )
    ).isFalse()
  }

  @Test
  fun task_activeLooksForwardToDue_compliedLooksBack() {
    val adapter = TaskAdapter()
    val active = MaintenanceTaskWithStatus(
      card,
      DueMetadata(nextDueDate = LocalDate(2027, 5, 20))
    )
    assertThat(adapter.date(active)).isEqualTo(LocalDate(2027, 5, 20))
    assertThat(adapter.direction(active)).isEqualTo(TimeDirection.FUTURE)

    val meterOnly =
      MaintenanceTaskWithStatus(card, DueMetadata(nextDueEngine = 2889f))
    assertThat(adapter.date(meterOnly)).isNull()

    val complied = MaintenanceTaskWithStatus(
      card,
      DueMetadata(
        status = DueStatus.COMPLIED,
        compliedDate = LocalDate(2026, 3, 14)
      ),
    )
    assertThat(adapter.date(complied)).isEqualTo(LocalDate(2026, 3, 14))
    assertThat(adapter.direction(complied)).isEqualTo(TimeDirection.PAST)
  }
}
