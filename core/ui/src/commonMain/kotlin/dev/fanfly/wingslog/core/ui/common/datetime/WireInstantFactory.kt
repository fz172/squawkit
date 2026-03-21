@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package dev.fanfly.wingslog.core.ui.common.datetime

import com.squareup.wire.Instant

expect fun createWireInstant(epochSeconds: Long, nanos: Int): Instant
