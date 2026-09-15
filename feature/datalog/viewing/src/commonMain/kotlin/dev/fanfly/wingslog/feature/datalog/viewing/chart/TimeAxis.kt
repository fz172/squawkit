package dev.fanfly.wingslog.feature.datalog.viewing.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.datalog.model.ViewWindow
import dev.fanfly.wingslog.feature.datalog.model.chart.Navigation
import dev.fanfly.wingslog.feature.datalog.model.chart.TimeTicks

private val AxisHeight = 28.dp
private val MinLabelSpacing = 72.dp

/** `14:48:00` is about twice the width of `00:10`, so the clock axis needs its ticks further apart. */
private val MinClockLabelSpacing = 132.dp
private val TickHeight = 4.dp

/** The pill is the cursor's grab handle, so its touch target is widened to a finger's worth. */
private val MinHandleWidth = 48.dp

/** Where the cursor pill sits, so the painter and the drag handle cannot disagree about it. */
private data class CursorPill(val text: String, val left: Float, val width: Float, val height: Float) {
  /** The pill itself, widened for a fingertip and given the axis's full height to be grabbed by. */
  fun handle(axisHeightPx: Float, minWidthPx: Float, axisWidthPx: Float): Rect {
    val centre = left + width / 2f
    val half = maxOf(width, minWidthPx) / 2f
    return Rect(
      left = (centre - half).coerceAtLeast(0f),
      top = 0f,
      right = (centre + half).coerceAtMost(axisWidthPx),
      bottom = axisHeightPx,
    )
  }
}

/**
 * The shared time axis under the pane stack (design §11.2, PRD R23a): ticks from the step ladder,
 * `mm:ss` labels at least 72 dp apart with the edge labels kept inside, and the cursor's time in a
 * `tertiary` pill.
 *
 * The pill is a handle: dragging it scrubs the cursor along, which is the only way to move the line
 * without a mouse to hover with, and a steadier one than hovering when reading a particular moment.
 */
@Composable
fun TimeAxis(
  view: ViewWindow?,
  durationSeconds: Int,
  cursorT: Double?,
  modifier: Modifier = Modifier,
  /** PRD R32: label the recorder's wall clock, [originSecondsOfDay] at elapsed zero. */
  clockAxis: Boolean = false,
  originSecondsOfDay: Int = 0,
  /** Where the drag put the cursor, as a fraction of the visible window. */
  onScrub: ((Double) -> Unit)? = null,
) {
  val measurer = rememberTextMeasurer()
  val labelStyle = axisLabelStyle()
  val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
  val tickColor = MaterialTheme.colorScheme.outlineVariant
  val pillColor = MaterialTheme.colorScheme.tertiary
  val onPill = MaterialTheme.colorScheme.onTertiary
  val window = Navigation.effective(view, durationSeconds)
  val density = LocalDensity.current
  var widthPx by remember { mutableIntStateOf(0) }

  val step = remember(window, widthPx, clockAxis, density) {
    val minSpacing = with(density) { (if (clockAxis) MinClockLabelSpacing else MinLabelSpacing).toPx() }
    TimeTicks.step(window.lengthSeconds, widthPx, minSpacing)
  }
  fun textFor(t: Int) =
    if (clockAxis) TimeTicks.clockLabel(originSecondsOfDay + t, step) else TimeTicks.label(t)

  val pill = remember(cursorT, window, widthPx, step, clockAxis, originSecondsOfDay, labelStyle, density) {
    cursorPill(cursorT, window, widthPx, ::textFor, measurer, labelStyle, density)
  }

  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .height(AxisHeight)
      .onSizeChanged { widthPx = it.width }
      .pointerInput(pill, window, widthPx, onScrub) {
        val scrub = onScrub ?: return@pointerInput
        val handle = pill?.handle(size.height.toFloat(), with(density) { MinHandleWidth.toPx() }, size.width.toFloat())
          ?: return@pointerInput
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          if (!handle.contains(down.position)) return@awaitEachGesture
          down.consume()
          drag(down.id) { change ->
            change.consume()
            scrub((change.position.x / size.width).coerceIn(0f, 1f).toDouble())
          }
        }
      },
  ) {
    val width = size.width.toInt()
    if (width <= 0 || window.lengthSeconds <= 0) return@Canvas
    val tickPx = TickHeight.toPx()
    val ticks =
      if (clockAxis) TimeTicks.clockTicks(window, step, originSecondsOfDay) else TimeTicks.ticks(window, step)
    ticks.forEach { t ->
      val x = TimeTicks.xOf(t.toDouble(), window, width)
      drawLine(tickColor, Offset(x, 0f), Offset(x, tickPx), strokeWidth = Spacing.hairline.toPx())
      val label = measurer.measure(textFor(t), labelStyle.copy(color = labelColor))
      val centre = TimeTicks.labelCenterX(x, label.size.width.toFloat(), width)
      drawText(label, topLeft = Offset(centre - label.size.width / 2f, tickPx + Spacing.extraSmall.toPx()))
    }
    if (pill != null) {
      drawRoundRect(
        pillColor,
        Offset(pill.left, 0f),
        Size(pill.width, pill.height),
        CornerRadius(pill.height / 2f),
      )
      drawText(
        measurer.measure(pill.text, labelStyle.copy(color = onPill)),
        topLeft = Offset(pill.left + Spacing.small.toPx(), Spacing.extraSmall.toPx() / 2f),
      )
    }
  }
}

/** Null when there is no cursor, no width yet, or the cursor sits outside the visible window. */
private fun cursorPill(
  cursorT: Double?,
  window: ViewWindow,
  widthPx: Int,
  textFor: (Int) -> String,
  measurer: androidx.compose.ui.text.TextMeasurer,
  labelStyle: TextStyle,
  density: Density,
): CursorPill? {
  if (cursorT == null || widthPx <= 0 || window.lengthSeconds <= 0) return null
  val x = TimeTicks.xOf(cursorT, window, widthPx)
  if (x !in 0f..widthPx.toFloat()) return null
  val text = textFor(cursorT.toInt())
  val label = measurer.measure(text, labelStyle)
  val padX = with(density) { Spacing.small.toPx() }
  val width = label.size.width + padX * 2
  return CursorPill(
    text = text,
    left = (x - width / 2f).coerceIn(0f, (widthPx - width).coerceAtLeast(0f)),
    width = width,
    height = label.size.height + with(density) { Spacing.extraSmall.toPx() },
  )
}
