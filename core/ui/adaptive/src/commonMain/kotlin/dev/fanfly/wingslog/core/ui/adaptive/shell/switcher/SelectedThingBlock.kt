package dev.fanfly.wingslog.core.ui.adaptive.shell.switcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.ui.adaptive.AdaptiveShellUiState
import dev.fanfly.wingslog.core.ui.adaptive.thingIcon
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.switcher_select_thing
import wingslog.core.sharedassets.generated.resources.switcher_switch
import wingslog.core.sharedassets.generated.resources.Res as UiRes

/**
 * The selected thing, as a block rather than a row: filled and bordered, name over identifier,
 * with a switch glyph. A highlighted row reads as "this one is tinted"; a block says the sections
 * under it belong to it. Tapping it opens the full picker.
 */
@Composable
internal fun SelectedThingBlock(
  state: AdaptiveShellUiState,
  onSelectThing: (String) -> Unit,
  onAddThing: () -> Unit,
  onEnterInviteCode: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  var open by remember { mutableStateOf(false) }
  val thing = state.selectedThing
  Box(modifier = modifier) {
    Surface(
      onClick = { open = true },
      shape = RoundedCornerShape(Spacing.cardCornerRadius),
      color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = SELECTED_BLOCK_FILL),
      border = BorderStroke(
        Spacing.hairline,
        MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_BLOCK_BORDER)
      ),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.padding(Spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier
            .size(Spacing.huge)
            .clip(RoundedCornerShape(Spacing.smallCornerRadius))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_BLOCK_ICON_FILL)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            thingIcon(thing?.template?.icon.orEmpty()),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Spacing.xLarge),
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            thing?.label ?: stringResource(UiRes.string.switcher_select_thing),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          thing?.subtitle?.takeIf { it.isNotBlank() }
            ?.let {
              Text(
                it,
                style = WingslogTypography.dataSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
        }
        Icon(
          Icons.Filled.UnfoldMore,
          contentDescription = stringResource(UiRes.string.switcher_switch),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    ThingDropdown(
      expanded = open,
      onDismiss = { open = false },
      state = state,
      onSelectThing = onSelectThing,
      onAddThing = onAddThing,
      onEnterInviteCode = onEnterInviteCode,
    )
  }
}

private const val SELECTED_BLOCK_FILL = 0.35f
private const val SELECTED_BLOCK_BORDER = 0.4f
private const val SELECTED_BLOCK_ICON_FILL = 0.18f
