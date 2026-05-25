@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.fanfly.wingslog.core.datetime

import com.squareup.wire.Instant

actual fun toWireInstant(epochSeconds: Long, nanos: Int): Instant {
  return Instant(epochSeconds, nanos)
}
