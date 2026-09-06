package dev.fanfly.wingslog.feature.search.viewing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.fanfly.wingslog.core.ui.theme.Spacing

/** A text-button footprint without the button’s minimum height, for compact rows. */
@Composable
internal fun Modifier.clickableText(onClick: () -> Unit): Modifier =
  clip(RoundedCornerShape(Spacing.smallCornerRadius))
    .clickable(onClick = onClick)
    .padding(horizontal = Spacing.small, vertical = Spacing.extraSmall)
