package dev.fanfly.wingslog.feature.notifications.settings.compose

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/**
 * [dev.fanfly.wingslog.feature.sync.settings.compose.SyncHeroIllustration]'s pattern, one subject
 * and a breathing halo, applied here: the master switch (§9.2's `allEnabled`) swaps the icon and
 * halo between lively and quiet, the same way sync's hero reads active vs. paused at a glance.
 */
@Composable
fun NotificationHeroIllustration(
  active: Boolean,
  modifier: Modifier = Modifier,
) {
  val cs = MaterialTheme.colorScheme
  val tint = if (active) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.55f)
  val haloColor = if (active) cs.primary else cs.onSurfaceVariant

  val transition = rememberInfiniteTransition(label = "notification-hero")
  val pulse by transition.animateFloat(
    initialValue = if (active) 0f else 0.5f,
    targetValue = if (active) 1f else 0.5f,
    animationSpec = infiniteRepeatable(
      animation = tween(
        durationMillis = 2_400,
        easing = EaseInOutSine
      ),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "notification-hero-pulse",
  )

  Box(
    modifier = modifier.fillMaxWidth()
      .height(180.dp),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(
      modifier = Modifier.fillMaxWidth()
        .height(180.dp)
    ) {
      val center = Offset(
        size.width / 2f,
        size.height / 2f
      )
      val baseRadius = size.height * 0.36f
      // Outer halo — broader, softer.
      drawCircle(
        color = haloColor.copy(alpha = 0.06f + 0.04f * pulse),
        radius = baseRadius * (1f + 0.18f * pulse),
        center = center,
      )
      // Inner halo — tighter ring under the icon.
      drawCircle(
        color = haloColor.copy(alpha = 0.10f + 0.06f * pulse),
        radius = baseRadius * (0.66f + 0.08f * pulse),
        center = center,
      )
    }
    Icon(
      imageVector = if (active) Icons.Default.Notifications else Icons.Default.NotificationsOff,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.height(96.dp)
        .fillMaxWidth(0.30f),
    )
  }
}
