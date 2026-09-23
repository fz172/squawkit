package dev.fanfly.wingslog.feature.logs.viewing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.template.componentTypesApply
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.logs.sharedassets.util.displayName
import dev.fanfly.wingslog.thing.ComponentType

@Composable
private fun badgeSchemeFor(type: ComponentType): BadgeScheme = when (type) {
  ComponentType.COMPONENT_ENGINE -> BadgeScheme(
    MaterialTheme.colorScheme.primaryContainer,
    MaterialTheme.colorScheme.onPrimaryContainer,
  )

  ComponentType.COMPONENT_AIRFRAME -> BadgeScheme(
    MaterialTheme.colorScheme.surfaceContainerHigh,
    MaterialTheme.colorScheme.onSurfaceVariant,
  )

  ComponentType.COMPONENT_PROPELLER -> BadgeScheme(
    MaterialTheme.colorScheme.secondaryContainer,
    MaterialTheme.colorScheme.onSecondaryContainer,
  )

  else -> BadgeScheme(
    MaterialTheme.colorScheme.surfaceContainerHigh,
    MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/**
 * The component pill, or nothing when the type describes nothing the user picked.
 *
 * The form defaults to `COMPONENT_AIRFRAME` and stamped it on even where the picker never appeared,
 * so a car's every log wore an "Airframe" pill.
 */
@Composable
internal fun LogComponentBadge(
  type: ComponentType,
  modifier: Modifier = Modifier,
) {
  if (!componentTypesApply || type == ComponentType.COMPONENT_UNKNOWN) return
  ComponentTypeBadge(type, modifier)
}

@Composable
internal fun ComponentTypeBadge(
  type: ComponentType,
  modifier: Modifier = Modifier,
) {
  val scheme = badgeSchemeFor(type)
  Box(
    modifier = modifier
      .background(
        color = scheme.background,
        shape = RoundedCornerShape(Spacing.badgeCornerRadius)
      )
      .padding(
        horizontal = Spacing.small,
        vertical = Spacing.extraSmall
      ),
  ) {
    Text(
      text = type.displayName()
        .uppercase(),
      color = scheme.contentColor,
      fontSize = 10.sp,
      fontWeight = FontWeight.SemiBold,
      letterSpacing = 0.6.sp,
      lineHeight = 14.sp,
    )
  }
}
