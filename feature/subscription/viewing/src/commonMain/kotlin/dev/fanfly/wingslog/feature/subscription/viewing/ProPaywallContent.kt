package dev.fanfly.wingslog.feature.subscription.viewing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.common.compose.formatFileSize
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.subscription.viewing.viewmodel.SubscriptionUiState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.subscription.viewing.generated.resources.Res
import wingslog.feature.subscription.viewing.generated.resources.subscription_activating
import wingslog.feature.subscription.viewing.generated.resources.subscription_aircraft_free
import wingslog.feature.subscription.viewing.generated.resources.subscription_aircraft_unlimited
import wingslog.feature.subscription.viewing.generated.resources.subscription_billing_note
import wingslog.feature.subscription.viewing.generated.resources.subscription_cell_excluded
import wingslog.feature.subscription.viewing.generated.resources.subscription_cell_unlimited
import wingslog.feature.subscription.viewing.generated.resources.subscription_col_free
import wingslog.feature.subscription.viewing.generated.resources.subscription_col_pro
import wingslog.feature.subscription.viewing.generated.resources.subscription_compare_header
import wingslog.feature.subscription.viewing.generated.resources.subscription_compare_subhead
import wingslog.feature.subscription.viewing.generated.resources.subscription_cta_caption
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_ads
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_aircraft
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_attachments
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_backup
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_email
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_export
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_records
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_sharing
import wingslog.feature.subscription.viewing.generated.resources.subscription_includes_header
import wingslog.feature.subscription.viewing.generated.resources.subscription_purchase_on_mobile
import wingslog.feature.subscription.viewing.generated.resources.subscription_sign_in_to_subscribe
import wingslog.feature.subscription.viewing.generated.resources.subscription_storage_used

/**
 * The pre-purchase page: what Pro adds, and one way to get it.
 *
 * The argument is scale, not features — Free is already a complete logbook, so the comparison leads
 * with the thing count and only then lists what Pro unlocks. Nothing here quotes a price; the
 * store's paywall does that, and saying so up front is what keeps the CTA honest.
 */
@Composable
internal fun ProPaywallContent(
  state: SubscriptionUiState,
  onSubscribe: () -> Unit
) {
  Text(
    text = stringResource(Res.string.subscription_compare_header),
    style = MaterialTheme.typography.headlineMedium,
  )
  Text(
    text = stringResource(Res.string.subscription_compare_subhead),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )

  BillingNote()
  ComparisonTable(isAdsSupported = state.isAdsSupported)

  SubscribeButton(
    onClick = onSubscribe,
    // Disabled while activating so a pilot who has just paid can't start a second purchase in the
    // window before their entitlement syncs, and for a guest, who has no durable account to attach
    // a subscription to.
    enabled = state.isPurchaseSupported && !state.isActivating && !state.isGuest,
  )

  // Most actionable first: a guest can fix their case, and until they do nothing else about the
  // button matters. The default line sets expectations for the store sheet that is about to open.
  SubscriptionCaption(
    text = when {
      state.isGuest -> stringResource(Res.string.subscription_sign_in_to_subscribe)
      state.isActivating -> stringResource(Res.string.subscription_activating)
      // Web: purchasing is mobile-only, but a subscription bought there unlocks Pro here too.
      !state.isPurchaseSupported -> stringResource(Res.string.subscription_purchase_on_mobile)
      else -> stringResource(Res.string.subscription_cta_caption)
    },
    textAlign = TextAlign.Center,
  )

  HorizontalDivider()
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(Res.string.subscription_storage_used),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = state.storageBytesUsed.formatFileSize(),
      style = WingslogTypography.dataMedium
    )
  }
}

/**
 * Says where pricing lives before the pilot taps through.
 *
 * A paywall that shows no price reads as evasive unless it explains itself, and the store genuinely
 * owns the number — plan, currency, tax and any introductory offer are decided there.
 */
@Composable
private fun BillingNote() {
  SubscriptionPanel {
    Row(
      modifier = Modifier.padding(
        horizontal = Spacing.large,
        vertical = Spacing.medium
      ),
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(Spacing.xLarge),
      )
      Text(
        text = stringResource(Res.string.subscription_billing_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun ComparisonTable(isAdsSupported: Boolean) {
  SubscriptionPanel {
    CompareHeader()
    CompareRow(
      label = stringResource(Res.string.subscription_feature_aircraft),
      free = Cell.Label(stringResource(Res.string.subscription_aircraft_free)),
      pro = Cell.Unlimited,
    )
    CompareRow(
      stringResource(Res.string.subscription_feature_records),
      Cell.Yes,
      Cell.Yes
    )
    CompareRow(
      stringResource(Res.string.subscription_feature_export),
      Cell.Yes,
      Cell.Yes
    )
    CompareRow(
      stringResource(Res.string.subscription_feature_backup),
      Cell.Yes,
      Cell.Yes
    )
    // Only in a build that actually ships ads (#384) — the table has to describe the build the
    // pilot is holding, not one where this row would be advertising a feature that doesn't exist.
    if (isAdsSupported) {
      CompareRow(
        stringResource(Res.string.subscription_feature_ads),
        Cell.No,
        Cell.Yes
      )
    }
    CompareRow(
      stringResource(Res.string.subscription_feature_attachments),
      Cell.No,
      Cell.Yes
    )
    CompareRow(
      stringResource(Res.string.subscription_feature_email),
      Cell.No,
      Cell.Yes
    )
    CompareRow(
      label = stringResource(Res.string.subscription_feature_sharing),
      free = Cell.No,
      pro = Cell.Yes,
      divider = false,
    )
  }
}

@Composable
private fun CompareHeader() {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = Spacing.large, end = Spacing.extraSmall)
      .padding(top = Spacing.medium, bottom = Spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SectionLabel(
      text = stringResource(Res.string.subscription_includes_header),
      modifier = Modifier.weight(1f),
    )
    Text(
      text = stringResource(Res.string.subscription_col_free),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.width(FREE_COLUMN),
    )
    // The one tinted element in the table: the column the page is arguing for.
    Text(
      text = stringResource(Res.string.subscription_col_pro),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .width(PRO_COLUMN)
        .clip(RoundedCornerShape(Spacing.badgeCornerRadius))
        .background(MaterialTheme.colorScheme.primary.copy(alpha = PRO_HEADER_TINT))
        .padding(vertical = Spacing.extraSmall),
    )
  }
  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** One cell of the comparison table. */
private sealed interface Cell {
  /** Included in this tier. */
  data object Yes : Cell

  /** Not in this tier — an em dash, never an unchecked box, which reads as a broken control. */
  data object No : Cell

  data class Label(val text: String) : Cell

  /** The thing count on Pro: an ∞ glyph, announced as the word. */
  data object Unlimited : Cell
}

@Composable
private fun CompareRow(
  label: String,
  free: Cell,
  pro: Cell,
  divider: Boolean = true
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(ROW_HEIGHT)
      .padding(start = Spacing.large, end = Spacing.extraSmall),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.weight(1f),
    )
    CellContent(
      cell = free,
      isPro = false,
      modifier = Modifier.width(FREE_COLUMN)
    )
    CellContent(cell = pro, isPro = true, modifier = Modifier.width(PRO_COLUMN))
  }
  if (divider) {
    HorizontalDivider(
      color = MaterialTheme.colorScheme.outlineVariant.copy(
        alpha = ROW_RULE_ALPHA
      )
    )
  }
}

/**
 * @param isPro tints the cell and carries the Pro column's full-height wash, so the eye can follow
 *   one column down the table without a heavy vertical rule.
 */
@Composable
private fun CellContent(
  cell: Cell,
  isPro: Boolean,
  modifier: Modifier = Modifier
) {
  val tint =
    if (isPro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
  Box(
    modifier = modifier
      .fillMaxHeight()
      .background(
        if (isPro) {
          MaterialTheme.colorScheme.primary.copy(alpha = PRO_COLUMN_TINT)
        } else {
          Color.Transparent
        },
      ),
    contentAlignment = Alignment.Center,
  ) {
    when (cell) {
      Cell.Yes -> Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(Spacing.xLarge),
      )

      Cell.No -> Text(
        text = stringResource(Res.string.subscription_cell_excluded),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
      )

      is Cell.Label -> Text(
        text = cell.text,
        style = WingslogTypography.dataSmall,
        color = tint
      )

      Cell.Unlimited -> {
        val spoken = stringResource(Res.string.subscription_aircraft_unlimited)
        Text(
          text = stringResource(Res.string.subscription_cell_unlimited),
          style = WingslogTypography.dataMedium,
          fontWeight = FontWeight.Bold,
          color = tint,
          modifier = Modifier.semantics { contentDescription = spoken },
        )
      }
    }
  }
}

/**
 * Comparison-table geometry. Not [Spacing] values: these are the table's own column widths and row
 * height, sized so "Unlimited" and a 44dp touch target both fit, and pushing one screen's layout
 * into the shared token file would make it a dumping ground.
 */
private val FREE_COLUMN = 64.dp
private val PRO_COLUMN = 72.dp
private val ROW_HEIGHT = 44.dp

/** Barely-there washes: enough to group the Pro column, not enough to look like a selected row. */
private const val PRO_HEADER_TINT = 0.08f
private const val PRO_COLUMN_TINT = 0.05f
private const val ROW_RULE_ALPHA = 0.7f
