package dev.fanfly.wingslog.core.datetime

import com.squareup.wire.Instant
import java.time.Instant as JavaInstant

actual fun toWireInstant(epochSeconds: Long, nanos: Int): Instant {
  return JavaInstant.ofEpochSecond(epochSeconds, nanos.toLong())
}