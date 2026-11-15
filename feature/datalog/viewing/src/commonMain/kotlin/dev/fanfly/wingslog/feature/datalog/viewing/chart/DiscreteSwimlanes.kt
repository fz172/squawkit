package dev.fanfly.wingslog.feature.datalog.viewing.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey
import dev.fanfly.wingslog.feature.datalog.model.ViewWindow
import dev.fanfly.wingslog.feature.datalog.model.chart.DiscreteSpan
import dev.fanfly.wingslog.feature.datalog.model.chart.Navigation
import dev.fanfly.wingslog.feature.datalog.model.chart.SpanTone
import dev.fanfly.wingslog.feature.datalog.model.chart.TimeTicks

/** A discrete or text series rendered as a horizontal Gantt swimlane underneath the numeric chart. */
data class SwimlaneSeries(
  val key: SeriesKey,
  val label: String,
  val color: Color,
  val spans: List<DiscreteSpan>,
)

/** A span temporarily projected onto [ChartPane] when hovering or pinning a swimlane pill. */
data class ProjectedSpan(
  val startSec: Int,
  val endSec: Int,
  val color: Color,
  val label: String,
)

private val SwimlaneRowHeight = 20.dp
private val PillCornerRadius = 4.dp
private val PillTextSize = 10.sp
private val CautionAmber = Color(0xFFD97706)

/**
 * Option 6 swimlane strip: renders discrete, text, and frequency series as compact horizontal
 * Gantt pill tracks aligned to [view] and [cursorT], plus an expanded chip readout for compound
 * text series (e.g. `CAS Alert`) at the current cursor time.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscreteSwimlanes(
  swimlanes: List<SwimlaneSeries>,
  durationSeconds: Int,
  view: ViewWindow?,
  cursorT: Double?,
  pinnedSpan: ProjectedSpan?,
  onHoverSpan: (ProjectedSpan?) -> Unit,
  onTogglePinSpan: (ProjectedSpan) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (swimlanes.isEmpty()) return
  val window = Navigation.effective(view, durationSeconds)
  val errorColor = MaterialTheme.colorScheme.error
  val outlineColor = MaterialTheme.colorScheme.outlineVariant
  val cursorColor = MaterialTheme.colorScheme.tertiary
  val textDefaultColor = MaterialTheme.colorScheme.onSurface
  val measurer = rememberTextMeasurer()
  val textStyle = WingslogTypography.dataSmall.copy(fontSize = PillTextSize)
  val density = LocalDensity.current
  val cornerPx = with(density) { PillCornerRadius.toPx() }
  val labelPadPx = with(density) { Spacing.small.toPx() }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    swimlanes.forEach { lane ->
      var trackWidthPx by remember { mutableIntStateOf(0) }
      val currentLane by rememberUpdatedState(lane)
      val currentWindow by rememberUpdatedState(window)

      fun spanAtX(xPx: Float): ProjectedSpan? {
        if (trackWidthPx <= 0) return null
        val t = currentWindow.startSeconds +
          (xPx / trackWidthPx) * currentWindow.lengthSeconds
        val hit =
          currentLane.spans.firstOrNull { t >= it.startSec && t <= it.endSec }
            ?: return null
        val c = spanColor(hit.tone, currentLane.color, errorColor)
        return ProjectedSpan(
          startSec = hit.startSec,
          endSec = hit.endSec,
          color = c,
          label = "${currentLane.label}: ${hit.pillLabel}",
        )
      }

      // Full-width track so X coordinates align 1:1 with ChartPane and TimeAxis.
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(SwimlaneRowHeight)
          .clip(RoundedCornerShape(PillCornerRadius))
          .onSizeChanged { trackWidthPx = it.width }
          .pointerInput(lane.key) {
            awaitPointerEventScope {
              while (true) {
                val event = awaitPointerEvent()
                when (event.type) {
                  PointerEventType.Move, PointerEventType.Enter -> {
                    val pos = event.changes.firstOrNull()?.position
                    onHoverSpan(pos?.let { spanAtX(it.x) })
                  }

                  PointerEventType.Exit -> onHoverSpan(null)
                }
              }
            }
          }
          .pointerInput(lane.key) {
            detectTapGestures { offset ->
              spanAtX(offset.x)?.let(onTogglePinSpan)
            }
          },
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val midY = size.height / 2f
          drawLine(
            color = outlineColor.copy(alpha = 0.7f),
            start = Offset(0f, midY),
            end = Offset(size.width, midY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
          )

          val wStart = window.startSeconds.toDouble()
          val wEnd = window.endSeconds.toDouble()
          lane.spans.forEach { span ->
            if (span.endSec < wStart || span.startSec > wEnd) return@forEach
            val x1 = TimeTicks.xOf(
              span.startSec.toDouble(),
              window,
              size.width.toInt()
            )
              .coerceIn(0f, size.width)
            val x2 =
              TimeTicks.xOf(span.endSec.toDouble(), window, size.width.toInt())
                .coerceIn(0f, size.width)
            val pillW = (x2 - x1).coerceAtLeast(2f)
            val c = spanColor(span.tone, lane.color, errorColor)
            val isPinned = pinnedSpan != null &&
              pinnedSpan.startSec == span.startSec &&
              pinnedSpan.endSec == span.endSec &&
              pinnedSpan.label == "${lane.label}: ${span.pillLabel}"

            drawRoundRect(
              color = c.copy(alpha = if (isPinned) 0.30f else 0.16f),
              topLeft = Offset(x1, 1f),
              size = Size(pillW, size.height - 2f),
              cornerRadius = CornerRadius(cornerPx, cornerPx),
            )
            drawRoundRect(
              color = c,
              topLeft = Offset(x1, 1f),
              size = Size(pillW, size.height - 2f),
              cornerRadius = CornerRadius(cornerPx, cornerPx),
              style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = if (isPinned) 1.5.dp.toPx() else 1.dp.toPx()
              ),
            )
            val measured = measurer.measure(
              span.pillLabel,
              textStyle.copy(color = if (span.tone == SpanTone.NEUTRAL) textDefaultColor else c),
            )
            if (pillW >= measured.size.width + labelPadPx) {
              drawText(
                measured,
                topLeft = Offset(
                  x1 + (pillW - measured.size.width) / 2f,
                  (size.height - measured.size.height) / 2f,
                ),
              )
            }
          }

          if (cursorT != null && trackWidthPx > 0) {
            val cx = TimeTicks.xOf(cursorT, window, trackWidthPx)
            if (cx in 0f..size.width) {
              drawLine(
                color = cursorColor,
                start = Offset(cx, 0f),
                end = Offset(cx, size.height),
                strokeWidth = 1.dp.toPx(),
              )
            }
          }
        }
        Surface(
          shape = RoundedCornerShape(PillCornerRadius),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
          modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = 2.dp),
        ) {
          Text(
            text = lane.label,
            style = WingslogTypography.dataSmall.copy(fontSize = PillTextSize),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
          )
        }
      }
    }

    // Expanded token chips for compound text series (e.g. CAS Alert) at the cursor position.
    if (cursorT != null) {
      val compoundHits = swimlanes.mapNotNull { lane ->
        val span =
          lane.spans.firstOrNull { cursorT >= it.startSec && cursorT <= it.endSec }
        if (span != null && span.tokens.size > 1) lane to span else null
      }
      if (compoundHits.isNotEmpty()) {
        FlowRow(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
              RoundedCornerShape(Spacing.smallCornerRadius),
            )
            .border(
              Spacing.hairline,
              MaterialTheme.colorScheme.outlineVariant,
              RoundedCornerShape(Spacing.smallCornerRadius),
            )
            .padding(horizontal = Spacing.small, vertical = Spacing.extraSmall),
          horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
          verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
        ) {
          compoundHits.forEach { (lane, span) ->
            val chipColor = spanColor(span.tone, lane.color, errorColor)
            Text(
              text = "${lane.label}:",
              style = WingslogTypography.dataSmall.copy(fontSize = PillTextSize),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.align(Alignment.CenterVertically),
            )
            span.tokens.forEach { token ->
              Surface(
                shape = RoundedCornerShape(PillCornerRadius),
                color = chipColor.copy(alpha = 0.14f),
              ) {
                Text(
                  text = token,
                  style = WingslogTypography.dataSmall.copy(fontSize = PillTextSize),
                  color = chipColor,
                  modifier = Modifier.padding(
                    horizontal = 6.dp,
                    vertical = 2.dp
                  ),
                )
              }
            }
          }
        }
      }
    }
  }
}

private fun spanColor(
  tone: SpanTone,
  seriesColor: Color,
  errorColor: Color
): Color =
  when (tone) {
    SpanTone.FAULT -> errorColor
    SpanTone.CAUTION -> CautionAmber
    SpanTone.INFO, SpanTone.NEUTRAL -> seriesColor
  }
