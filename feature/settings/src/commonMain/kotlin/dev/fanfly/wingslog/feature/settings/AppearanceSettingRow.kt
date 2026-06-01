package dev.fanfly.wingslog.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.theme.AppearanceMode
import dev.fanfly.wingslog.core.ui.theme.Spacing
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

/**
 * The Appearance setting: a light/system/dark choice persisted device-locally.
 *
 * Responsive to the layout tier — on compact widths the three-state toggle drops to a full-width row
 * beneath the title/subtitle; on wider tiers it sits inline on the trailing edge.
 */
@Composable
fun AppearanceSettingRow(
  mode: AppearanceMode,
  onModeChange: (AppearanceMode) -> Unit,
) {
  val compact = LocalLayoutTier.current.isCompact
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 72.dp)
      .padding(horizontal = Spacing.small, vertical = Spacing.large),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(Spacing.huge)
        .clip(RoundedCornerShape(Spacing.small))
        .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Default.DarkMode,
        contentDescription = stringResource(SettingsRes.string.appearance_title),
        modifier = Modifier.size(Spacing.extraLarge),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Spacer(modifier = Modifier.width(Spacing.large))

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Text(
        text = stringResource(SettingsRes.string.appearance_title),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = stringResource(SettingsRes.string.appearance_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.alpha(0.6f),
      )

      // Compact: the toggle is too wide to share the row, so it wraps to the next line full-width.
      if (compact) {
        Spacer(modifier = Modifier.height(Spacing.small))
        AppearanceToggle(
          mode = mode,
          onModeChange = onModeChange,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    if (!compact) {
      Spacer(modifier = Modifier.width(Spacing.large))
      AppearanceToggle(mode = mode, onModeChange = onModeChange)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceToggle(
  mode: AppearanceMode,
  onModeChange: (AppearanceMode) -> Unit,
  modifier: Modifier = Modifier,
) {
  SingleChoiceSegmentedButtonRow(modifier = modifier) {
    APPEARANCE_OPTIONS.forEachIndexed { index, option ->
      SegmentedButton(
        selected = mode == option,
        onClick = { onModeChange(option) },
        shape = SegmentedButtonDefaults.itemShape(
          index = index,
          count = APPEARANCE_OPTIONS.size,
        ),
        label = { Text(option.label()) },
      )
    }
  }
}

@Composable
private fun AppearanceMode.label(): String = stringResource(
  when (this) {
    AppearanceMode.LIGHT -> SettingsRes.string.appearance_light
    AppearanceMode.SYSTEM -> SettingsRes.string.appearance_system
    AppearanceMode.DARK -> SettingsRes.string.appearance_dark
  }
)
