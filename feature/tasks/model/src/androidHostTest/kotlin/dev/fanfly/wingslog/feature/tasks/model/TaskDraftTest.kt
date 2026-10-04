package dev.fanfly.wingslog.feature.tasks.model

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.task.InspectionRule
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.task.TaskOrigin
import dev.fanfly.wingslog.task.TaskOriginKind
import dev.fanfly.wingslog.task.TimeRule
import org.junit.Test

class TaskDraftTest {

  @Test
  fun `a task survives the trip through a route argument, origin included`() {
    val task = MaintenanceTask(
      title = "Tire rotation & balance — every 6,250 mi",
      notes = "Rotate front/rear; check pressure?",
      rules = listOf(InspectionRule(time_rule = TimeRule(interval_months = 6))),
      origin = TaskOrigin(
        kind = TaskOriginKind.TASK_ORIGIN_KIND_AI_THING,
        citation = "Tesla service"
      ),
    )

    val arg = task.toDraftArg()

    // Nothing a route's query string would mangle.
    assertThat(arg).matches("[A-Za-z0-9_-]*")
    assertThat(taskFromDraftArg(arg)).isEqualTo(task)
  }

  @Test
  fun `an argument that is not a task reads as none`() {
    assertThat(taskFromDraftArg("%%%")).isNull()
  }
}
