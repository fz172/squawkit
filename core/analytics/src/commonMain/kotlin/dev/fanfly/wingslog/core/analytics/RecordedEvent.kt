package dev.fanfly.wingslog.core.analytics

/** One event as it reached the SDK boundary: its name and the flattened params. */
data class RecordedEvent(val name: String, val params: Map<String, String>)
