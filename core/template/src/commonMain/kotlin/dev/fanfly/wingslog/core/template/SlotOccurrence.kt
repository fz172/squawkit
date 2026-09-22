package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.Component

/** A slot's [ordinal]-th component, or the empty slot when [component] is null. */
internal data class SlotOccurrence(val component: Component?, val ordinal: Int?)
