package dev.fanfly.wingslog.core.ui.common.datetime

import com.squareup.wire.Instant
import java.time.Instant as JavaInstant

actual fun createWireInstant(epochSeconds: Long, nanos: Int): Instant {
  return JavaInstant.ofEpochSecond(epochSeconds, nanos.toLong())
}
