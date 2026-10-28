package dev.fanfly.wingslog.feature.sync.data

import dev.fanfly.wingslog.id.ThingId

/** A thing shared into this account: whose it is, and which of theirs. */
data class SharedThingRef(val hostUid: String, val thingId: ThingId)
