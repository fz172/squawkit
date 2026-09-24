package dev.fanfly.wingslog.feature.subscription.viewing.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.core.ui.theme.toneFor
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.subscription.viewing.generated.resources.Res
import wingslog.feature.subscription.viewing.generated.resources.subscription_status_active
import wingslog.feature.subscription.viewing.generated.resources.subscription_status_canceled
import wingslog.feature.subscription.viewing.generated.resources.subscription_status_grace
import wingslog.feature.subscription.viewing.generated.resources.subscription_status_trialing

/** A dot and a word — the same status language the rest of the app uses for operational state. */
@Composable
internal fun StatusIndicator(lifecycle: Subscription.Lifecycle) {
  val (labelRes, tier) = when (lifecycle) {
    Subscription.Lifecycle.LIFECYCLE_TRIALING ->
      Res.string.subscription_status_trialing to StatusTier.POSITIVE

    // Still Pro, but ending: amber, because there is a decision to make before the period end.
    Subscription.Lifecycle.LIFECYCLE_CANCELED ->
      Res.string.subscription_status_canceled to StatusTier.CAUTION

    // The store could not take payment. Loudest state on the page — access is about to stop.
    Subscription.Lifecycle.LIFECYCLE_GRACE ->
      Res.string.subscription_status_grace to StatusTier.CRITICAL

    else -> Res.string.subscription_status_active to StatusTier.POSITIVE
  }
  val accent = MaterialTheme.statusColors.toneFor(tier).accent
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      Modifier.size(Spacing.small)
        .background(accent, CircleShape)
    )
    Spacer(Modifier.width(Spacing.small))
    Text(
      text = stringResource(labelRes).uppercase(),
      style = WingslogTypography.dataSmall,
      fontWeight = FontWeight.Bold,
      letterSpacing = StatusTracking,
      color = accent,
    )
  }
}
