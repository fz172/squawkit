package dev.fanfly.wingslog.feature.tasks.model

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.task.TaskOrigin
import dev.fanfly.wingslog.task.TaskOriginKind
import org.junit.Test

class AiSuggestedTest {
  private fun from(kind: TaskOriginKind) = MaintenanceTask(title = "T", origin = TaskOrigin(kind = kind))

  @Test
  fun `the AI's tasks are marked, from the Thing or a document`() {
    assertThat(from(TaskOriginKind.TASK_ORIGIN_KIND_AI_THING).isAiSuggested).isTrue()
    assertThat(from(TaskOriginKind.TASK_ORIGIN_KIND_AI_DOCUMENT).isAiSuggested).isTrue()
    assertThat(from(TaskOriginKind.TASK_ORIGIN_KIND_AI_LOG_BACKFILL).isAiSuggested).isTrue()
  }

  @Test
  fun `curated, starter and hand-made tasks are not`() {
    assertThat(from(TaskOriginKind.TASK_ORIGIN_KIND_PRE_CURATED).isAiSuggested).isFalse()
    assertThat(from(TaskOriginKind.TASK_ORIGIN_KIND_UNSPECIFIED).isAiSuggested).isFalse()
    assertThat(from(TaskOriginKind.TASK_ORIGIN_KIND_USER).isAiSuggested).isFalse()
    assertThat(MaintenanceTask(title = "By hand").isAiSuggested).isFalse()
  }
}
