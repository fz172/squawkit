package dev.fanfly.wingslog.core.ui.common.datetime

import com.squareup.wire.Instant

actual fun createWireInstant(epochSeconds: Long, nanos: Int): Instant {
    return java.time.Instant.ofEpochSecond(epochSeconds, nanos.toLong())
}
