package dev.fanfly.wingslog.feature.subscription.viewing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.subscription.viewing.generated.resources.Res
import wingslog.feature.subscription.viewing.generated.resources.subscription_cta_placeholder
import wingslog.feature.subscription.viewing.generated.resources.subscription_cta_start_trial
import wingslog.feature.subscription.viewing.generated.resources.subscription_title
import wingslog.feature.subscription.viewing.generated.resources.upsell_body_add_aircraft
import wingslog.feature.subscription.viewing.generated.resources.upsell_body_attachment
import wingslog.feature.subscription.viewing.generated.resources.upsell_body_email
import wingslog.feature.subscription.viewing.generated.resources.upsell_body_share
import wingslog.feature.subscription.viewing.generated.resources.upsell_see_plans

/**
 * Where a locked, Pro-only affordance was tapped. Drives the sheet's contextual copy, and its
 * [name] is the analytics tag for "which gate drove the upsell" (wired when subscription analytics
 * lands). See docs/subscription/subscription_design.html §9.
 */
enum class UpsellTrigger(val bodyRes: StringResource) {
  ADD_AIRCRAFT(Res.string.upsell_body_add_aircraft),
  ATTACHMENT_UPLOAD(Res.string.upsell_body_attachment),
  EMAIL_EXPORT(Res.string.upsell_body_email),
  SHARE_HOST(Res.string.upsell_body_share),
}

/**
 * Reusable "gate as promo" bottom sheet. A gated feature opens this instead of failing silently: a
 * contextual benefit + a trial CTA (placeholder until billing) + a link to the full comparison page.
 *
 * Consumers hold a `mutableStateOf<UpsellTrigger?>(null)` and render this when non-null:
 * ```
 * upsell?.let {
 *   ProUpsellSheet(
 *     trigger = it,
 *     onSeePlans = { navController.navigate(Screen.Subscription.route); upsell = null },
 *     onDismiss = { upsell = null },
 *   )
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProUpsellSheet(
  trigger: UpsellTrigger,
  onSeePlans: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.extraLarge)
        .padding(bottom = Spacing.extraLarge),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Icon(
        imageVector = Icons.Default.Star,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = stringResource(Res.string.subscription_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = stringResource(trigger.bodyRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      // Purchasing is a placeholder until the billing integration lands.
      Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.subscription_cta_start_trial))
      }
      Text(
        text = stringResource(Res.string.subscription_cta_placeholder),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      TextButton(onClick = onSeePlans, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.upsell_see_plans))
      }
    }
  }
}
