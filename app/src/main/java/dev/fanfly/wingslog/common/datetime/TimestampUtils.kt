package dev.fanfly.wingslog.common.datetime

import com.google.protobuf.Timestamp
import com.google.protobuf.timestamp
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
fun Timestamp.toInstant() = Instant.fromEpochSeconds(this.seconds, this.nanos)

@OptIn(ExperimentalTime::class)
fun Instant.toTimestamp() = timestamp {
  seconds = epochSeconds
  nanos =nanosecondsOfSecond

}