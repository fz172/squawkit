package dev.fanfly.wingslog.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.theme.AppearanceMode
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.resolveDarkTheme
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.settings.generated.resources.appearance_dark
import wingslog.feature.settings.generated.resources.appearance_light
import wingslog.feature.settings.generated.resources.appearance_subtitle
import wingslog.feature.settings.generated.resources.appearance_system
import wingslog.feature.settings.generated.resources.appearance_title
import wingslog.feature.settings.generated.resources.Res as SettingsRes

private val APPEARANCE_OPTIONS = listOf(
  AppearanceMode.LIGHT,
  AppearanceMode.SYSTEM,
  AppearanceMode.DARK,
)

// Leading icon chip — matches the navigation rows' chip in [SettingsRow].
private val IconChipSize = 48.dp
private val IconChipRadius = 13.dp
private val IconSize = 22.dp

// Segmented "pill" toggle measurements from the design handoff.
private val TrackRadius = 10.dp
private val TrackPadding = 4.dp
private val OptionRadius = 7.dp
private val OptionIconSize = 15.dp

/**
 * The Appearance setting: a light / system / dark choice persisted device-locally, rendered as a
 * row inside a [SettingsCard]. The leading icon reflects the *resolved* theme (sun / monitor /
 * moon); the trailing segmented control highlights the stored *preference*.
 *
 * Responsive to the layout tier — on compact widths the toggle drops to a full-width row beneath
 * the title/subtitle; on wider tiers it sits inline on the trailing edge.
 */
@Composable
fun AppearanceSettingRow(
  mode: AppearanceMode,
  onModeChange: (AppearanceMode) -> Unit,
) {
  val compact = LocalLayoutTier.current.isCompact
  val resolvedDark = mode.resolveDarkTheme()
  val leadingIcon = when {
    mode == AppearanceMode.SYSTEM -> Icons.Default.Computer
    resolvedDark -> Icons.Default.DarkMode
    else -> Icons.Default.LightMode
  }

  if (compact) {
    // Compact: the toggle is too wide to share the row, so it drops to its own full-width row
    // spanning the entire setting (under the icon too) — otherwise the labels are cramped and wrap.
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.xLarge, vertical = Spacing.large),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        AppearanceLeadingIcon(leadingIcon)
        Spacer(modifier = Modifier.width(Spacing.large))
        AppearanceLabels(modifier = Modifier.weight(1f))
      }
      AppearanceToggle(
        mode = mode,
        onModeChange = onModeChange,
        fillWidth = true,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  } else {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.xLarge, vertical = Spacing.large),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AppearanceLeadingIcon(leadingIcon)
      Spacer(modifier = Modifier.width(Spacing.large))
      AppearanceLabels(modifier = Modifier.weight(1f))
      Spacer(modifier = Modifier.width(Spacing.large))
      AppearanceToggle(mode = mode, onModeChange = onModeChange)
    }
  }
}

@Composable
private fun AppearanceLeadingIcon(icon: ImageVector) {
  Box(
    modifier = Modifier
      .size(IconChipSize)
      .clip(RoundedCornerShape(IconChipRadius))
      .background(MaterialTheme.colorScheme.surfaceVariant),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = stringResource(SettingsRes.string.appearance_title),
      modifier = Modifier.size(IconSize),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun AppearanceLabels(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    Text(
      text = stringResource(SettingsRes.string.appearance_title),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = stringResource(SettingsRes.string.appearance_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * A custom three-state pill toggle. The track is a recessed `surfaceVariant` pill; the active
 * option lifts onto a `surface` chip with a subtle shadow, mirroring the design handoff's
 * `.theme-switch` / `.theme-opt` styling rather than M3's outlined segmented buttons.
 */
@Composable
private fun AppearanceToggle(
  mode: AppearanceMode,
  onModeChange: (AppearanceMode) -> Unit,
  modifier: Modifier = Modifier,
  fillWidth: Boolean = false,
) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(TrackRadius))
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .padding(TrackPadding),
    horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    APPEARANCE_OPTIONS.forEach { option ->
      AppearanceOption(
        option = option,
        selected = mode == option,
        onClick = { onModeChange(option) },
        // When the toggle stretches full-width (compact), share it evenly across the three options.
        modifier = if (fillWidth) Modifier.weight(1f) else Modifier,
      )
    }
  }
}

@Composable
private fun AppearanceOption(
  option: AppearanceMode,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(OptionRadius)
  val contentColor =
    if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
  Row(
    modifier = modifier
      .then(if (selected) Modifier.shadow(2.dp, shape) else Modifier)
      .clip(shape)
      .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
      .clickable(onClick = onClick)
      .padding(horizontal = Spacing.medium, vertical = Spacing.small),
    horizontalArrangement = Arrangement.spacedBy(
      Spacing.small,
      Alignment.CenterHorizontally
    ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = option.icon(),
      contentDescription = null,
      modifier = Modifier.size(OptionIconSize),
      tint = contentColor,
    )
    Text(
      text = option.label(),
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = contentColor,
      maxLines = 1,
    )
  }
}

private fun AppearanceMode.icon(): ImageVector = when (this) {
  AppearanceMode.LIGHT -> Icons.Default.LightMode
  AppearanceMode.SYSTEM -> Icons.Default.Computer
  AppearanceMode.DARK -> Icons.Default.DarkMode
}

@Composable
private fun AppearanceMode.label(): String = stringResource(
  when (this) {
    AppearanceMode.LIGHT -> SettingsRes.string.appearance_light
    AppearanceMode.SYSTEM -> SettingsRes.string.appearance_system
    AppearanceMode.DARK -> SettingsRes.string.appearance_dark
  }
)
