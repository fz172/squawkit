package dev.fanfly.wingslog.feature.tasks.update.form

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.task.ComplianceType
import dev.fanfly.wingslog.task.InspectionRule
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.task.TaskOrigin
import dev.fanfly.wingslog.task.TaskOriginKind
import dev.fanfly.wingslog.task.TimeRule
import org.junit.Test

class OriginEditTest {

  private fun task(kind: TaskOriginKind?) = MaintenanceTask(
    id = "t1",
    title = "Oil change",
    rules = listOf(InspectionRule(time_rule = TimeRule(interval_months = 6))),
    type = ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
    origin = kind?.let { TaskOrigin(kind = it) },
  )

  @Test
  fun `an edited AI task reports its origin and what changed`() {
    val before = task(TaskOriginKind.TASK_ORIGIN_KIND_AI_THING)
    val after = before.copy(rules = listOf(InspectionRule(time_rule = TimeRule(interval_months = 12))))

    val event = originEditOf("car", before, after)!!

    assertThat(event.originKind).isEqualTo("ai_thing")
    assertThat(event.fieldGroup).isEqualTo("schedule")
    assertThat(event.templateId).isEqualTo("car")
  }

  @Test
  fun `several groups are joined, in a fixed order`() {
    val before = task(TaskOriginKind.TASK_ORIGIN_KIND_PRE_CURATED)
    val after = before.copy(
      title = "Oil and filter",
      reference_number = "SB-1",
      rules = emptyList(),
    )

    val event = originEditOf("car", before, after)!!

    assertThat(event.originKind).isEqualTo("pre_curated")
    assertThat(event.fieldGroup).isEqualTo("details+schedule+compliance")
  }

  @Test
  fun `a task made by hand, one with no origin, and an unchanged save report nothing`() {
    val user = task(TaskOriginKind.TASK_ORIGIN_KIND_USER)
    val legacy = task(null)
    val ai = task(TaskOriginKind.TASK_ORIGIN_KIND_AI_DOCUMENT)

    assertThat(originEditOf("car", user, user.copy(title = "x"))).isNull()
    assertThat(originEditOf("car", legacy, legacy.copy(title = "x"))).isNull()
    assertThat(originEditOf("car", ai, ai)).isNull()
  }
}
