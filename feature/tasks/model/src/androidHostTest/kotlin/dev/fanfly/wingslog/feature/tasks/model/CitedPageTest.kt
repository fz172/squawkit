package dev.fanfly.wingslog.feature.tasks.model

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.id.AttachmentId
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.task.TaskOrigin
import dev.fanfly.wingslog.thing.Attachment
import org.junit.Test

class CitedPageTest {
  private val manual = Attachment(id = "blob-mm", name = "MM.pdf")
  private val task = MaintenanceTask(
    title = "Lubricate main wheel bearings",
    origin = TaskOrigin(source_attachment_id = AttachmentId(value_ = "blob-mm"), source_page = 212),
    attachments = listOf(manual),
  )

  @Test
  fun `the cited document opens at its page`() {
    assertThat(task.citedPageOf(manual)).isEqualTo(212)
  }

  @Test
  fun `another attachment, or a task with no page, opens at the start`() {
    assertThat(task.citedPageOf(Attachment(id = "blob-photo"))).isNull()
    assertThat(task.copy(origin = task.origin!!.copy(source_page = 0)).citedPageOf(manual)).isNull()
    assertThat(MaintenanceTask(title = "By hand").citedPageOf(manual)).isNull()
  }
}
