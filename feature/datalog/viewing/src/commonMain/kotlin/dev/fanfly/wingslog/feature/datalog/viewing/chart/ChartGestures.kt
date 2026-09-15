package dev.fanfly.wingslog.feature.datalog.viewing.chart

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import dev.fanfly.wingslog.feature.datalog.model.GestureIntent
import kotlin.math.abs

/** One wheel or trackpad delta unit moves the view by this fraction of the visible span. */
private const val SCROLL_PAN_FRACTION = 0.05

/** One wheel delta unit with ctrl or meta scales the span by this much. */
private const val SCROLL_ZOOM_STEP = 0.1

/** A brush shorter than this, in px, is a tap that places the cursor. */
private const val TAP_THRESHOLD_PX = 8f

/**
 * The three R23 gestures on one pane (design §11.4), each with its pointer equivalent:
 *
 * - one pointer: a horizontal drag brushes a span and zooms to it on release; a vertical drag is
 *   never consumed so the pane stack scrolls; a tap places the cursor;
 * - two pointers: the centroid pans, the distance pinches, centred on the fingers;
 * - scroll: ctrl or meta zooms around the pointer, shift pans, a horizontal delta pans, and a plain
 *   vertical wheel is left for the page.
 *
 * A hovering mouse deliberately does *not* move the cursor. It used to, which meant the line
 * chased the pointer and could not be left anywhere: the cursor is placed by a tap and moved by
 * dragging its timestamp in the axis, which is also the only way touch can move it at all.
 *
 * [onBrush] reports the live selection for the pane to draw, and null when it ends.
 */
fun Modifier.chartGestures(
  onIntent: (GestureIntent) -> Unit,
  onBrush: (ClosedFloatingPointRange<Float>?) -> Unit,
): Modifier = this
  .pointerInput(Unit) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false)
      val width = size.width
      if (width <= 0) return@awaitEachGesture
      val slop = viewConfiguration.touchSlop
      var brushing = false
      var pinched = false
      var lastCentroid: Offset? = null
      var lastDistance = 0f
      while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }
        if (pressed.isEmpty()) {
          val up = event.changes.firstOrNull { it.id == down.id }
            ?: event.changes.first()
          when {
            brushing -> {
              onBrush(null)
              val x1 = up.position.x
              if (abs(x1 - down.position.x) > TAP_THRESHOLD_PX) onIntent(
                GestureIntent.Brush(down.position.x, x1, width)
              )
              else onIntent(GestureIntent.Cursor((x1 / width).toDouble()))
            }

            !pinched -> onIntent(GestureIntent.Cursor((down.position.x / width).toDouble()))
          }
          break
        }
        if (pressed.size >= 2) {
          if (brushing) {
            brushing = false
            onBrush(null)
          }
          pinched = true
          val a = pressed[0].position
          val b = pressed[1].position
          val centroid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
          val distance = (a - b).getDistance()
          lastCentroid?.let { previous ->
            val dx = centroid.x - previous.x
            // Content follows the fingers: dragging right moves the view earlier in time.
            if (dx != 0f) onIntent(GestureIntent.Pan(-dx.toDouble() / width))
          }
          if (lastDistance > 0f && distance > 0f) {
            onIntent(
              GestureIntent.Zoom(
                (centroid.x / width).toDouble(),
                (distance / lastDistance).toDouble()
              )
            )
          }
          lastCentroid = centroid
          lastDistance = distance
          pressed.forEach { it.consume() }
          continue
        }
        if (pinched) {
          // One finger lifted after a pinch: swallow the rest of the gesture.
          pressed.forEach { it.consume() }
          continue
        }
        val change = pressed.first()
        if (!brushing) {
          val dx = change.position.x - down.position.x
          val dy = change.position.y - down.position.y
          // Vertical slop first: the list scrolls the pane stack and we consume nothing.
          if (abs(dy) > slop && abs(dy) > abs(dx)) return@awaitEachGesture
          if (abs(dx) > slop) brushing = true
        }
        if (brushing) {
          change.consume()
          val lo = minOf(down.position.x, change.position.x).coerceIn(
            0f,
            width.toFloat()
          )
          val hi = maxOf(down.position.x, change.position.x).coerceIn(
            0f,
            width.toFloat()
          )
          onBrush(lo..hi)
          onIntent(GestureIntent.Cursor((change.position.x / width).toDouble()))
        }
      }
    }
  }
  .pointerInput(Unit) {
    // Hover and scroll share one loop: neither starts a gesture, and both arrive as pointer events
    // on every target. A hovering mouse (no button) moves the cursor; touch never produces those.
    awaitPointerEventScope {
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val width = size.width
        if (width <= 0) continue
        when (event.type) {
          PointerEventType.Scroll -> {
            val change = event.changes.first()
            val delta = change.scrollDelta
            val mods = event.keyboardModifiers
            when {
              mods.isCtrlPressed || mods.isMetaPressed -> {
                // Wheel up (negative delta) zooms in, matching browsers and map apps; a trackpad
                // pinch arrives as ctrl+wheel on the web.
                val factor =
                  (1.0 - delta.y * SCROLL_ZOOM_STEP).coerceAtLeast(0.1)
                onIntent(
                  GestureIntent.Zoom(
                    (change.position.x / width).toDouble(),
                    factor
                  )
                )
                change.consume()
              }

              mods.isShiftPressed -> {
                onIntent(GestureIntent.Pan(delta.y * SCROLL_PAN_FRACTION))
                change.consume()
              }

              abs(delta.x) > abs(delta.y) -> {
                onIntent(GestureIntent.Pan(delta.x * SCROLL_PAN_FRACTION))
                change.consume()
              }
              // A plain vertical wheel scrolls the page.
              else -> Unit
            }
          }

          else -> Unit
        }
      }
    }
  }
