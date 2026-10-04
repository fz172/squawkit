package dev.fanfly.wingslog.feature.tasks.model

import dev.fanfly.wingslog.task.MaintenanceTask
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

/**
 * A [MaintenanceTask] as a navigation argument: the task form's draft mode (task population T18)
 * opens pre-filled from one and hands the edited one back. The proto's bytes in URL-safe base64
 * without padding, so it travels in a route's query string untouched.
 */
fun MaintenanceTask.toDraftArg(): String = encode().toByteString()
  .base64Url()
  .trimEnd('=')

/** The task [arg] encodes, or null when it does not decode. */
fun taskFromDraftArg(arg: String): MaintenanceTask? =
  arg.decodeBase64()
    ?.let { bytes -> runCatching { MaintenanceTask.ADAPTER.decode(bytes) }.getOrNull() }
