package dev.fanfly.wingslog.feature.subscription.viewing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.subscription.viewing.generated.resources.Res
import wingslog.feature.subscription.viewing.generated.resources.subscription_title
import wingslog.feature.subscription.viewing.generated.resources.upsell_body_add_thing
import wingslog.feature.subscription.viewing.generated.resources.upsell_body_attachment
import wingslog.feature.subscription.viewing.generated.resources.upsell_body_email
import wingslog.feature.subscription.viewing.generated.resources.upsell_body_share

/**
 * Where a locked, Pro-only affordance was tapped. Drives the sheet's contextual copy, and its
 * [name] is the analytics tag for "which gate drove the upsell" (wired when subscription analytics
 * lands). See docs/subscription/subscription_design.html §9.
 */
enum class UpsellTrigger {
  ADD_THING,
  ATTACHMENT_UPLOAD,
  EMAIL_EXPORT,
  SHARE_HOST,
}

/**
 * The trigger's body copy.
 *
 * **A `when` rather than a `StringResource` field on the enum.** Two of these four take the thing
 * noun and two do not, and a resource handle stored in a constructor hides that difference: the
 * single `stringResource(trigger.bodyRes)` that used to render all four passed no arguments, so
 * "Share %1$s and invite others with SquawkIt Pro." reached users with the placeholder intact.
 *
 * Naming each resource at the point it is read is what makes the argument list visible, and it is
 * what `StringSnapshotTest.everyConvertedStringIsReadInline` now requires.
 */
@Composable
private fun UpsellTrigger.body(): String = when (this) {
  UpsellTrigger.ADD_THING -> stringResource(Res.string.upsell_body_add_thing)

  UpsellTrigger.SHARE_HOST -> stringResource(Res.string.upsell_body_share)

  UpsellTrigger.ATTACHMENT_UPLOAD -> stringResource(Res.string.upsell_body_attachment)
  UpsellTrigger.EMAIL_EXPORT -> stringResource(Res.string.upsell_body_email)
}

/**
 * Reusable "gate as promo" bottom sheet. A gated feature opens this instead of failing silently: a
 * contextual benefit + a CTA, both routing to the Subscription page where the comparison and the
 * store paywall live.
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
        text = trigger.body(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      // Routes to the Subscription page rather than opening the paywall inline: the sheet is a
      // contextual promo shown over whatever the pilot was doing, and the page is where the full
      // comparison and the store's own paywall live.
      SubscribeButton(onClick = onSeePlans)
    }
  }
}
