package dev.fanfly.wingslog.feature.tasks.model

import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.thing.Attachment

/**
 * The 1-based PDF page to open [attachment] at: the page an AI suggestion cited, when this is the
 * document it cited (PRD R30). Null for any other attachment, or a task that kept no page.
 */
fun MaintenanceTask.citedPageOf(attachment: Attachment): Int? {
  val origin = origin ?: return null
  if (origin.source_attachment_id?.value_ != attachment.id) return null
  return origin.source_page.takeIf { it > 0 }
}
