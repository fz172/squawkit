package dev.fanfly.wingslog.core.ui.perf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import co.touchlab.kermit.Logger
import kotlin.time.TimeSource

/**
 * Times a section switch: each [step] logs its offset from the last [begin]. Off unless a host sets
 * [enabled]; Android turns it on for every build type but release. Filter logcat on `SwitchTrace`.
 */
object SwitchTrace {
  var enabled = false

  private val log = Logger.withTag("SwitchTrace")
  private var start: TimeSource.Monotonic.ValueTimeMark? = null
  private var label = ""

  fun begin(label: String) {
    if (!enabled) return
    this.label = label
    start = TimeSource.Monotonic.markNow()
    log.i { "begin $label" }
  }

  fun step(what: String) {
    if (!enabled) return
    val mark = start ?: return
    log.i { "+${mark.elapsedNow().inWholeMilliseconds}ms $label: $what" }
  }

  /** Runs [block], logging how long it took. */
  inline fun <T> timed(what: String, block: () -> T): T {
    if (!enabled) return block()
    val mark = TimeSource.Monotonic.markNow()
    return block().also { step("$what took ${mark.elapsedNow().inWholeMilliseconds}ms") }
  }
}

/** After each [key] change, logs the first frame and any frame over [slowMillis] for [windowMillis]. */
@Composable
fun TraceFrames(key: Any, windowMillis: Long = 2_000, slowMillis: Long = 20) {
  if (!SwitchTrace.enabled) return
  LaunchedEffect(key) {
    var last = withFrameNanos { it }
    SwitchTrace.step("first frame")
    var elapsed = 0L
    while (elapsed < windowMillis * 1_000_000) {
      val now = withFrameNanos { it }
      val frame = now - last
      elapsed += frame
      if (frame > slowMillis * 1_000_000) SwitchTrace.step("slow frame ${frame / 1_000_000}ms")
      last = now
    }
  }
}
