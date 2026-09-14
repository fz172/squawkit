package dev.fanfly.wingslog.feature.datalog.model

import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.thing.Attachment

/**
 * Wire generates a message-typed id as nullable. A record without one is corrupt and the manager
 * drops it on read, so everything above reads through this accessor (design §4.4).
 */
val DataLog.dataLogId: DataLogId
  get() = checkNotNull(id) { "DataLog without an id" }

/** Set only on a `DATA_LOG` attachment; null on every other type by design. */
fun Attachment.dataLogIdOrNull(): DataLogId? = data_log_id
