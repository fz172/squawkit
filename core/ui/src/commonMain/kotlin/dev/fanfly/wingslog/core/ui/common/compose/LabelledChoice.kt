package dev.fanfly.wingslog.core.ui.common.compose

/** One option a picker offers: the words the user reads, and what choosing them means. */
data class LabelledChoice<T>(val label: String, val value: T)
