package dev.fanfly.wingslog.core.model.id

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.id.AttachmentId
import dev.fanfly.wingslog.id.CommentId
import dev.fanfly.wingslog.id.ComponentId
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ExportId
import dev.fanfly.wingslog.id.MaintenanceLogId
import dev.fanfly.wingslog.id.MaintenanceTaskId
import dev.fanfly.wingslog.id.SquawkId
import dev.fanfly.wingslog.id.SuggestionId
import dev.fanfly.wingslog.id.TechnicianId
import dev.fanfly.wingslog.id.TemplateId
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.id.UserId
import org.junit.Test

class IdValuesTest {

  @Test
  fun allBoxedIdsUnboxValueCorrectly() {
    assertThat(AttachmentId("att-1").value).isEqualTo("att-1")
    assertThat(CommentId("com-1").value).isEqualTo("com-1")
    assertThat(ComponentId("comp-1").value).isEqualTo("comp-1")
    assertThat(DataLogId("dl-1").value).isEqualTo("dl-1")
    assertThat(ExportId("exp-1").value).isEqualTo("exp-1")
    assertThat(MaintenanceLogId("log-1").value).isEqualTo("log-1")
    assertThat(MaintenanceTaskId("task-1").value).isEqualTo("task-1")
    assertThat(SquawkId("sq-1").value).isEqualTo("sq-1")
    assertThat(SuggestionId("sug-1").value).isEqualTo("sug-1")
    assertThat(TechnicianId("tech-1").value).isEqualTo("tech-1")
    assertThat(TemplateId("tmpl-1").value).isEqualTo("tmpl-1")
    assertThat(ThingId("thing-1").value).isEqualTo("thing-1")
    assertThat(UserId("user-1").value).isEqualTo("user-1")
  }
}
