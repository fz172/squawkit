package dev.fanfly.wingslog.core.model.id

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

/**
 * Wire renames a proto field called `value` to `value_` because `value` is a Kotlin soft keyword.
 * These keep the schema's name at the call sites that unbox an id at the grandfathered string edge.
 */
val AttachmentId.value: String get() = value_
val CommentId.value: String get() = value_
val ComponentId.value: String get() = value_
val DataLogId.value: String get() = value_
val ExportId.value: String get() = value_
val MaintenanceLogId.value: String get() = value_
val MaintenanceTaskId.value: String get() = value_
val SquawkId.value: String get() = value_
val SuggestionId.value: String get() = value_
val TechnicianId.value: String get() = value_
val TemplateId.value: String get() = value_
val ThingId.value: String get() = value_
val UserId.value: String get() = value_
