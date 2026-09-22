package dev.fanfly.wingslog.core.template

/** One level of a [ComponentPath]: which slot, and which of that slot's components. */
data class ComponentPathStep(val slotKey: String, val index: Int)
