package dev.fanfly.wingslog.feature.subscription.viewing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.core.ui.common.compose.formatFileSize
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.core.ui.theme.toneFor
import dev.fanfly.wingslog.feature.subscription.model.PurchasePlatform
import dev.fanfly.wingslog.feature.subscription.viewing.viewmodel.SubscriptionUiState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.subscription.viewing.generated.resources.Res
import wingslog.feature.subscription.viewing.generated.resources.subscription_col_pro
import wingslog.feature.subscription.viewing.generated.resources.subscription_ends
import wingslog.feature.subscription.viewing.generated.resources.subscription_manage
import wingslog.feature.subscription.viewing.generated.resources.subscription_manage_caption
import wingslog.feature.subscription.viewing.generated.resources.subscription_managed_elsewhere_body
import wingslog.feature.subscription.viewing.generated.resources.subscription_managed_elsewhere_body_web
import wingslog.feature.subscription.viewing.generated.resources.subscription_managed_elsewhere_caption
import wingslog.feature.subscription.viewing.generated.resources.subscription_managed_elsewhere_title
import wingslog.feature.subscription.viewing.generated.resources.subscription_member_since
import wingslog.feature.subscription.viewing.generated.resources.subscription_perk_aircraft_body
import wingslog.feature.subscription.viewing.generated.resources.subscription_perk_aircraft_title
import wingslog.feature.subscription.viewing.generated.resources.subscription_perk_attachments_body
import wingslog.feature.subscription.viewing.generated.resources.subscription_perk_attachments_title
import wingslog.feature.subscription.viewing.generated.resources.subscription_perk_sharing_body
import wingslog.feature.subscription.viewing.generated.resources.subscription_perk_sharing_title
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_amazon
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_app_store
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_mac_app_store
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_play_store
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_test_store
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_web
import wingslog.feature.subscription.viewing.generated.resources.subscription_purchased_on
import wingslog.feature.subscription.viewing.generated.resources.subscription_renews
import wingslog.feature.subscription.viewing.generated.resources.subscription_status_active
import wingslog.feature.subscription.viewing.generated.resources.subscription_status_canceled
import wingslog.feature.subscription.viewing.generated.resources.subscription_status_grace
import wingslog.feature.subscription.viewing.generated.resources.subscription_status_trialing
import wingslog.feature.subscription.viewing.generated.resources.subscription_storage_used
import wingslog.feature.subscription.viewing.generated.resources.subscription_title
import wingslog.feature.subscription.viewing.generated.resources.subscription_unlocked_header

/**
 * The post-purchase page: a receipt, not a pitch.
 *
 * Everything the subscriber might come here to check is in the one card at the top — is it on, since
 * when, what happens next, how much storage it is holding, and which store to go back to. The perks
 * below are a reminder of what the money buys, not another attempt to sell it.
 */
@Composable
internal fun ColumnScope.ProMembershipContent(state: SubscriptionUiState, onManage: () -> Unit) {
  MembershipCard(state)

  SectionLabel(stringResource(Res.string.subscription_unlocked_header))
  PerkGrid()

  Spacer(Modifier.height(Spacing.small))
  if (state.canManage) {
    ManageAction(onManage)
  } else {
    ManagedElsewhere(isPurchaseSupported = state.isPurchaseSupported)
  }
}

@Composable
private fun MembershipCard(state: SubscriptionUiState) {
  // Tinted rather than neutral: the one surface on the page that should read as "this is yours".
  SubscriptionPanel(
    modifier = Modifier.fillMaxWidth(),
    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = CARD_BORDER_TINT),
    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = CARD_SURFACE_TINT),
  ) {
    Box(Modifier.fillMaxWidth()) {
      ProRibbon(Modifier.align(Alignment.TopEnd))
      Column(Modifier.padding(Spacing.xLarge)) {
        StatusIndicator(state.lifecycle)
        Text(
          text = stringResource(Res.string.subscription_title),
          style = MaterialTheme.typography.headlineSmall,
          modifier = Modifier.padding(top = Spacing.large),
        )

        HorizontalDivider(
          modifier = Modifier.padding(top = Spacing.xLarge),
          color = MaterialTheme.colorScheme.primary.copy(alpha = CARD_RULE_TINT),
        )
        Row(
          modifier = Modifier.padding(top = Spacing.large),
          horizontalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
          DateTile(
            label = stringResource(Res.string.subscription_member_since),
            value = state.memberSince,
            modifier = Modifier.weight(1f),
          )
          DateTile(
            // "Renews" would be a lie once the subscription is set to lapse, and the date is the
            // one thing a pilot on their way out is here to check.
            label = if (state.willRenew) {
              stringResource(Res.string.subscription_renews)
            } else {
              stringResource(Res.string.subscription_ends)
            },
            value = state.currentPeriodEnd,
            modifier = Modifier.weight(1f),
          )
        }

        HorizontalDivider(
          modifier = Modifier.padding(top = Spacing.large),
          color = MaterialTheme.colorScheme.primary.copy(alpha = CARD_RULE_TINT),
        )
        FactRow(
          label = stringResource(Res.string.subscription_storage_used),
          modifier = Modifier.padding(top = Spacing.large),
        ) {
          Text(state.storageBytesUsed.formatFileSize(), style = WingslogTypography.dataSmall)
        }
        // Omitted entirely when there is no store to name (a comp, or an unrecognised platform) —
        // see purchasePlatformOf. Sourced from the synced entitlement, so it names the store that
        // actually billed even when the pilot is reading this on a different platform.
        state.purchasePlatform?.let { platform ->
          FactRow(
            label = stringResource(Res.string.subscription_purchased_on),
            modifier = Modifier.padding(top = Spacing.small),
          ) {
            Icon(
              imageVector = platform.icon,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(Spacing.large),
            )
            Spacer(Modifier.width(Spacing.small))
            Text(stringResource(platform.labelRes), style = WingslogTypography.dataSmall)
          }
        }
      }
    }
  }
}

/**
 * The corner banner. Purely decorative — every fact it implies is stated in words inside the card —
 * so it carries no content description and is clipped to the card's corner rather than overlapping
 * any text.
 */
@Composable
private fun ProRibbon(modifier: Modifier = Modifier) {
  Box(modifier = modifier.size(RIBBON_BOX).clipToBounds()) {
    Text(
      text = stringResource(Res.string.subscription_col_pro).uppercase(),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      letterSpacing = RIBBON_TRACKING,
      color = MaterialTheme.colorScheme.onPrimary,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .align(Alignment.TopEnd)
        .offset(x = RIBBON_OFFSET_X, y = RIBBON_OFFSET_Y)
        .rotate(RIBBON_ANGLE)
        .width(RIBBON_WIDTH)
        .background(MaterialTheme.colorScheme.primary)
        .padding(vertical = Spacing.extraSmall),
    )
  }
}

/** A dot and a word — the same status language the rest of the app uses for operational state. */
@Composable
private fun StatusIndicator(lifecycle: Subscription.Lifecycle) {
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
    Box(Modifier.size(Spacing.small).background(accent, CircleShape))
    Spacer(Modifier.width(Spacing.small))
    Text(
      text = stringResource(labelRes).uppercase(),
      style = WingslogTypography.dataSmall,
      fontWeight = FontWeight.Bold,
      letterSpacing = STATUS_TRACKING,
      color = accent,
    )
  }
}

/** A labelled date, or nothing at all — an empty slot beats a placeholder dash under a heading. */
@Composable
private fun DateTile(label: String, value: String?, modifier: Modifier = Modifier) {
  Column(modifier) {
    if (value != null) {
      TileLabel(label)
      Text(
        text = value,
        style = WingslogTypography.dataMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = Spacing.extraSmall),
      )
    }
  }
}

@Composable
private fun FactRow(
  label: String,
  modifier: Modifier = Modifier,
  value: @Composable RowScope.() -> Unit,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TileLabel(label)
    Row(verticalAlignment = Alignment.CenterVertically, content = value)
  }
}

@Composable
private fun TileLabel(text: String) {
  Text(
    text = text.uppercase(),
    style = MaterialTheme.typography.labelSmall,
    letterSpacing = SECTION_LABEL_TRACKING,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/**
 * The three things Pro actually changes, in the order they were sold on the paywall.
 *
 * A hand-laid two-column grid rather than a lazy one: three fixed tiles inside an already-scrolling
 * column, where nesting a lazy grid would fight the outer scroll for no benefit.
 */
@Composable
private fun PerkGrid() {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
      PerkCard(
        icon = Icons.Default.AirplanemodeActive,
        title = stringResource(Res.string.subscription_perk_aircraft_title),
        body = stringResource(Res.string.subscription_perk_aircraft_body),
        modifier = Modifier.weight(1f),
      )
      PerkCard(
        icon = Icons.Default.AttachFile,
        title = stringResource(Res.string.subscription_perk_attachments_title),
        body = stringResource(Res.string.subscription_perk_attachments_body),
        modifier = Modifier.weight(1f),
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
      PerkCard(
        icon = Icons.Default.Group,
        title = stringResource(Res.string.subscription_perk_sharing_title),
        body = stringResource(Res.string.subscription_perk_sharing_body),
        modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.weight(1f))
    }
  }
}

@Composable
private fun PerkCard(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
  SubscriptionPanel(modifier) {
    Column(Modifier.padding(Spacing.large)) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(Spacing.xLarge),
      )
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = Spacing.medium),
      )
      Text(
        text = body,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.none),
      )
    }
  }
}

@Composable
private fun ColumnScope.ManageAction(onManage: () -> Unit) {
  OutlinedButton(
    onClick = onManage,
    shape = RoundedCornerShape(Spacing.buttonCornerRadius),
    modifier = Modifier
      .fillMaxWidth()
      .height(Spacing.buttonHeight),
  ) {
    Text(
      text = stringResource(Res.string.subscription_manage),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.width(Spacing.small))
    // Leaving the app: the Customer Center is the store's surface, not ours.
    Icon(
      imageVector = Icons.AutoMirrored.Filled.OpenInNew,
      contentDescription = null,
      modifier = Modifier.size(Spacing.xLarge),
    )
  }
  SubscriptionCaption(
    text = stringResource(Res.string.subscription_manage_caption),
    textAlign = TextAlign.Center,
  )
}

/**
 * The subscription is real and Pro is unlocked here, but this device's store did not sell it.
 *
 * Shown as an explanation *above* a visibly inert button rather than by hiding the control: a
 * subscriber looking for the cancel button will keep hunting for it, so the page has to say where it
 * went. The reassurance at the bottom is the other half — "you can't manage it here" must not read
 * as "your subscription doesn't work here".
 *
 * @param isPurchaseSupported false on web, which has no store to be a *different* store from.
 */
@Composable
private fun ColumnScope.ManagedElsewhere(isPurchaseSupported: Boolean) {
  val caution = MaterialTheme.statusColors.toneFor(StatusTier.CAUTION).accent
  SubscriptionPanel(
    modifier = Modifier.fillMaxWidth(),
    borderColor = caution.copy(alpha = NOTICE_BORDER_TINT),
    containerColor = caution.copy(alpha = NOTICE_SURFACE_TINT),
  ) {
    Row(
      modifier = Modifier.padding(Spacing.large),
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Icon(
        imageVector = Icons.Default.Info,
        contentDescription = null,
        tint = caution,
        modifier = Modifier.size(Spacing.xLarge),
      )
      Column {
        Text(
          text = stringResource(Res.string.subscription_managed_elsewhere_title),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = if (isPurchaseSupported) {
            stringResource(Res.string.subscription_managed_elsewhere_body)
          } else {
            stringResource(Res.string.subscription_managed_elsewhere_body_web)
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = Spacing.extraSmall),
        )
      }
    }
  }

  Spacer(Modifier.height(Spacing.medium))
  LockedManageButton()
  SubscriptionCaption(
    text = stringResource(Res.string.subscription_managed_elsewhere_caption),
    textAlign = TextAlign.Center,
    modifier = Modifier.padding(top = Spacing.medium),
  )
}

/**
 * A button-shaped statement that there is no button.
 *
 * Deliberately not a disabled [OutlinedButton]: a dashed outline reads as "not available here" at a
 * glance, where a greyed solid one reads as "temporarily broken, try again".
 */
@Composable
private fun LockedManageButton() {
  val outline = MaterialTheme.colorScheme.outline
  val stroke = Stroke(
    width = LOCKED_STROKE_PX,
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(LOCKED_DASH_PX, LOCKED_DASH_PX)),
  )
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(Spacing.buttonHeight)
      .drawBehind {
        drawRoundRect(
          color = outline,
          style = stroke,
          cornerRadius = CornerRadius(Spacing.buttonCornerRadius.toPx()),
        )
      },
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = Icons.Default.Lock,
      contentDescription = null,
      tint = outline,
      modifier = Modifier.size(Spacing.xLarge),
    )
    Spacer(Modifier.width(Spacing.small))
    Text(
      text = stringResource(Res.string.subscription_manage),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      color = outline,
    )
  }
}

/**
 * The store's display name.
 *
 * Lives here rather than on the enum because [PurchasePlatform] is a `model` type shared with the
 * billing layer, which has no Compose resources — and a store's *name* is a presentation concern in
 * a way its identity is not.
 */
private val PurchasePlatform.labelRes: StringResource
  get() = when (this) {
    PurchasePlatform.APP_STORE -> Res.string.subscription_platform_app_store
    PurchasePlatform.MAC_APP_STORE -> Res.string.subscription_platform_mac_app_store
    PurchasePlatform.PLAY_STORE -> Res.string.subscription_platform_play_store
    PurchasePlatform.AMAZON -> Res.string.subscription_platform_amazon
    PurchasePlatform.WEB -> Res.string.subscription_platform_web
    PurchasePlatform.TEST_STORE -> Res.string.subscription_platform_test_store
  }

/**
 * The glyph for the store that billed the subscription.
 *
 * A per-store icon rather than a generic receipt: the pilot is being pointed somewhere, and the
 * Play/App Store marks are what they will recognise when they get there.
 */
private val PurchasePlatform.icon: ImageVector
  get() = when (this) {
    PurchasePlatform.APP_STORE -> Icons.Default.PhoneIphone
    PurchasePlatform.MAC_APP_STORE -> Icons.Default.Laptop
    PurchasePlatform.PLAY_STORE -> Icons.Default.Android
    PurchasePlatform.AMAZON -> Icons.Default.ShoppingBag
    PurchasePlatform.WEB -> Icons.Default.Language
    PurchasePlatform.TEST_STORE -> Icons.Default.Science
  }

/**
 * Corner ribbon geometry, in the card's own top-right box — a bar wider than the box it is clipped
 * to, rotated about its centre. Not [Spacing] values: these are one decoration's construction, and
 * snapping them to the spacing scale would just move the ribbon off the corner.
 */
private val RIBBON_BOX = 104.dp
private val RIBBON_WIDTH = 150.dp
private val RIBBON_OFFSET_X = 42.dp
private val RIBBON_OFFSET_Y = 22.dp
private const val RIBBON_ANGLE = 45f
private val RIBBON_TRACKING = 2.6.sp
private val STATUS_TRACKING = 1.1.sp

/** Washes over the page surface; the card is a tint of the brand blue, not a second background. */
private const val CARD_BORDER_TINT = 0.28f
private const val CARD_SURFACE_TINT = 0.06f
private const val CARD_RULE_TINT = 0.16f
private const val NOTICE_BORDER_TINT = 0.32f
private const val NOTICE_SURFACE_TINT = 0.07f

private const val LOCKED_STROKE_PX = 4f
private const val LOCKED_DASH_PX = 10f
