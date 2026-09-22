package dev.fanfly.wingslog.feature.tasks.datamanager

/**
 * A quantity on a meter, named by the meter's key: a rule's interval ("every 5,000 mi"), or a
 * forced due ("at 250 hrs"). The key alone, because a task carries no template to resolve it with.
 */
data class MeterAmount(val meterKey: String, val value: Float)
