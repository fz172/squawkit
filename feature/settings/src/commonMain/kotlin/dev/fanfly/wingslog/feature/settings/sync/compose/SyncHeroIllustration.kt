package dev.fanfly.wingslog.feature.settings.sync.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * A flat illustration of a device synchronizing to the cloud, drawn with Compose primitives so it
 * carries no asset weight. A faint dashed arc orbits between the device and the cloud, animating
 * to suggest activity. Colors come from [MaterialTheme.colorScheme] so light + dark themes both
 * read cleanly.
 *
 * @param active when true, the orbit dashes drift; when false, the illustration is static.
 *   Disabled state mutes the palette so the page reads as inactive at a glance.
 */
@Composable
fun SyncHeroIllustration(active: Boolean, modifier: Modifier = Modifier) {
  val cs = MaterialTheme.colorScheme
  val cloudColor = if (active) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.45f)
  val deviceColor = if (active) cs.tertiary else cs.onSurfaceVariant.copy(alpha = 0.35f)
  val orbitColor = if (active) cs.primary.copy(alpha = 0.55f) else cs.outline.copy(alpha = 0.25f)
  val accentColor = if (active) cs.secondary else cs.onSurfaceVariant.copy(alpha = 0.3f)

  val transition = rememberInfiniteTransition(label = "sync-hero")
  val phase by transition.animateFloat(
    initialValue = 0f,
    targetValue = if (active) 1f else 0f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 4_000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "sync-hero-phase",
  )

  Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f

    // Faint background halo behind the cloud — gives the page warmth without a real image.
    drawCircle(
      color = cloudColor.copy(alpha = 0.08f),
      radius = h * 0.55f,
      center = Offset(cx, cy - h * 0.05f),
    )

    drawCloud(
      center = Offset(cx, cy - h * 0.18f),
      width = w * 0.32f,
      color = cloudColor,
    )

    // Up-arrow inside the cloud denoting "uploading."
    drawArrowUp(
      center = Offset(cx, cy - h * 0.18f),
      size = h * 0.10f,
      color = cs.surface,
      strokeWidth = h * 0.020f,
    )

    drawDevice(
      center = Offset(cx, cy + h * 0.30f),
      width = w * 0.18f,
      color = deviceColor,
    )

    // Animated dashed orbit between device and cloud.
    val orbitPath = Path().apply {
      addArc(
        oval = androidx.compose.ui.geometry.Rect(
          offset = Offset(cx - w * 0.32f, cy - h * 0.45f),
          size = Size(w * 0.64f, h * 1.10f),
        ),
        startAngleDegrees = 200f,
        sweepAngleDegrees = 140f,
      )
    }
    val dashLen = h * 0.06f
    drawPath(
      path = orbitPath,
      color = orbitColor,
      style = Stroke(
        width = h * 0.014f,
        pathEffect = PathEffect.dashPathEffect(
          intervals = floatArrayOf(dashLen, dashLen * 0.7f),
          phase = -phase * dashLen * 1.7f,
        ),
      ),
    )

    // Three small accent dots floating around the cloud to imply activity / data packets.
    val packetRadius = h * 0.018f
    val orbitR = h * 0.36f
    repeat(3) { i ->
      val angle = (phase * 360f + i * 120f) * (3.14159265f / 180f)
      val px = cx + orbitR * cos(angle.toDouble()).toFloat()
      val py = cy - h * 0.15f + orbitR * 0.55f * sin(angle.toDouble()).toFloat()
      drawCircle(
        color = if (i == 0) accentColor else cloudColor.copy(alpha = 0.65f),
        radius = packetRadius,
        center = Offset(px, py),
      )
    }
  }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloud(
  center: Offset,
  width: Float,
  color: Color,
) {
  val r1 = width * 0.42f
  val r2 = width * 0.34f
  val r3 = width * 0.30f
  drawCircle(color = color, radius = r1, center = center)
  drawCircle(color = color, radius = r2, center = Offset(center.x - width * 0.55f, center.y + width * 0.08f))
  drawCircle(color = color, radius = r3, center = Offset(center.x + width * 0.58f, center.y + width * 0.10f))
  // Flat base
  drawRect(
    color = color,
    topLeft = Offset(center.x - width * 0.85f, center.y + width * 0.08f),
    size = Size(width = width * 1.70f, height = width * 0.30f),
  )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowUp(
  center: Offset,
  size: Float,
  color: Color,
  strokeWidth: Float,
) {
  val top = Offset(center.x, center.y - size)
  val bottom = Offset(center.x, center.y + size * 0.4f)
  drawLine(color = color, start = top, end = bottom, strokeWidth = strokeWidth)
  drawLine(
    color = color,
    start = top,
    end = Offset(top.x - size * 0.6f, top.y + size * 0.55f),
    strokeWidth = strokeWidth,
  )
  drawLine(
    color = color,
    start = top,
    end = Offset(top.x + size * 0.6f, top.y + size * 0.55f),
    strokeWidth = strokeWidth,
  )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDevice(
  center: Offset,
  width: Float,
  color: Color,
) {
  val height = width * 1.7f
  // Body
  drawRoundRect(
    color = color,
    topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
    size = Size(width = width, height = height),
    cornerRadius = androidx.compose.ui.geometry.CornerRadius(width * 0.22f, width * 0.22f),
  )
  // Screen — a small inset rect uses surface color to suggest a glowing display.
  drawRoundRect(
    color = Color.White.copy(alpha = 0.20f),
    topLeft = Offset(center.x - width * 0.38f, center.y - height * 0.40f),
    size = Size(width = width * 0.76f, height = height * 0.62f),
    cornerRadius = androidx.compose.ui.geometry.CornerRadius(width * 0.10f, width * 0.10f),
  )
  // Home indicator
  drawRoundRect(
    color = Color.White.copy(alpha = 0.30f),
    topLeft = Offset(center.x - width * 0.18f, center.y + height * 0.36f),
    size = Size(width = width * 0.36f, height = height * 0.04f),
    cornerRadius = androidx.compose.ui.geometry.CornerRadius(width * 0.04f, width * 0.04f),
  )
}
