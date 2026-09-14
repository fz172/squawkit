package dev.fanfly.wingslog.feature.datalog.viewing.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.datalog.model.ViewWindow
import dev.fanfly.wingslog.feature.datalog.model.chart.Navigation
import dev.fanfly.wingslog.feature.datalog.model.chart.TimeTicks

private val AxisHeight = 28.dp
private val MinLabelSpacing = 72.dp
private val TickHeight = 4.dp

/**
 * The shared time axis under the pane stack (design §11.2, PRD R23a): ticks from the step ladder,
 * `mm:ss` labels at least 72 dp apart with the edge labels kept inside, and the cursor's time in a
 * `tertiary` pill.
 */
@Composable
fun TimeAxis(
  view: ViewWindow?,
  durationSeconds: Int,
  cursorT: Double?,
  modifier: Modifier = Modifier,
) {
  val measurer = rememberTextMeasurer()
  val labelStyle = axisLabelStyle()
  val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
  val tickColor = MaterialTheme.colorScheme.outlineVariant
  val pillColor = MaterialTheme.colorScheme.tertiary
  val onPill = MaterialTheme.colorScheme.onTertiary
  val window = Navigation.effective(view, durationSeconds)

  Canvas(modifier = modifier.fillMaxWidth().height(AxisHeight)) {
    val width = size.width.toInt()
    if (width <= 0 || window.lengthSeconds <= 0) return@Canvas
    val step = TimeTicks.step(window.lengthSeconds, width, MinLabelSpacing.toPx())
    val tickPx = TickHeight.toPx()
    TimeTicks.ticks(window, step).forEach { t ->
      val x = TimeTicks.xOf(t.toDouble(), window, width)
      drawLine(tickColor, Offset(x, 0f), Offset(x, tickPx), strokeWidth = Spacing.hairline.toPx())
      val label = measurer.measure(TimeTicks.label(t), labelStyle.copy(color = labelColor))
      val centre = TimeTicks.labelCenterX(x, label.size.width.toFloat(), width)
      drawText(label, topLeft = Offset(centre - label.size.width / 2f, tickPx + Spacing.extraSmall.toPx()))
    }
    if (cursorT != null) {
      val x = TimeTicks.xOf(cursorT, window, width)
      if (x in 0f..size.width) {
        val label = measurer.measure(TimeTicks.label(cursorT.toInt()), labelStyle.copy(color = onPill))
        val padX = Spacing.small.toPx()
        val pillWidth = label.size.width + padX * 2
        val left = (x - pillWidth / 2f).coerceIn(0f, (size.width - pillWidth).coerceAtLeast(0f))
        val pillHeight = label.size.height + Spacing.extraSmall.toPx()
        drawRoundRect(pillColor, Offset(left, 0f), Size(pillWidth, pillHeight), CornerRadius(pillHeight / 2f))
        drawText(label, topLeft = Offset(left + padX, Spacing.extraSmall.toPx() / 2f))
      }
    }
  }
}
