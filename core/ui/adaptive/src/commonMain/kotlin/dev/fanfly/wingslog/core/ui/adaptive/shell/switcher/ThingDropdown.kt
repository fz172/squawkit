package dev.fanfly.wingslog.core.ui.adaptive.shell.switcher

// core/ui/adaptive cannot use the core/ui shadow; the menu body resets the scope itself.
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.adaptive.shell.AdaptiveShellUiState
import dev.fanfly.wingslog.core.ui.adaptive.thingIcon
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.enter_invite_code
import wingslog.core.sharedassets.generated.resources.switcher_add_thing
import wingslog.core.sharedassets.generated.resources.your_stuff
import androidx.compose.material3.DropdownMenu as M3DropdownMenu
import wingslog.core.sharedassets.generated.resources.Res as UiRes

/** The picker every switcher surface opens: one row per thing, then the add / invite actions. */
@Composable
internal fun ThingDropdown(
  expanded: Boolean,
  onDismiss: () -> Unit,
  state: AdaptiveShellUiState,
  onSelectThing: (String) -> Unit,
  onAddThing: (() -> Unit)?,
  onEnterInviteCode: (() -> Unit)?,
) {
  M3DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
    DisableSelection {
      Text(
        // Never a template's collection_label: the switcher spans the account, so titling it "Fleet"
        // because today's rows are all aircraft renames the whole surface as the fleet changes.
        stringResource(UiRes.string.your_stuff),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      )
      // Grouping is a sort, not a section: same-type rows sit together with no heading and no rule
      // between them. The icon already says which type a row is, and on a short menu a label per
      // group was more furniture than the grouping was worth.
      state.things.groupBy { it.template?.id.orEmpty() }.values.forEach { rows ->
        rows.forEach { ac ->
          // Colour as well as the checkmark: a tick in the trailing corner is easy to miss while
          // scanning the labels, and it is the only thing distinguishing the row you are already on.
          val selected = ac.id == state.selectedThingId
          val accent = MaterialTheme.colorScheme.primary
          DropdownMenuItem(
            text = {
              Column {
                Text(
                  ac.label,
                  style = MaterialTheme.typography.titleSmall,
                  color = if (selected) accent else Color.Unspecified,
                )
                if (ac.subtitle.isNotBlank()) {
                  Text(
                    ac.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) accent else Color.Unspecified,
                  )
                }
              }
            },
            leadingIcon = {
              Icon(
                thingIcon(ac.template?.icon.orEmpty()),
                contentDescription = null,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            },
            onClick = {
              onSelectThing(ac.id)
              onDismiss()
            },
            trailingIcon = {
              if (selected) {
                Icon(
                  Icons.Filled.Check,
                  contentDescription = null,
                  tint = accent
                )
              }
            },
          )
        }
      }
      if (onAddThing != null || onEnterInviteCode != null) {
        HorizontalDivider()
      }
      if (onAddThing != null) {
        DropdownMenuItem(
          text = {
            // Neutral, not the selected thing's word: the switcher is where a user moves between
            // templates, so "Add Aircraft" would offer to add another airplane to someone whose next
            // Thing is a house.
            Text(stringResource(UiRes.string.switcher_add_thing))
          },
          leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
          onClick = {
            onAddThing()
            onDismiss()
          },
        )
      }
      if (onEnterInviteCode != null) {
        DropdownMenuItem(
          text = { Text(stringResource(UiRes.string.enter_invite_code)) },
          leadingIcon = {
            Icon(
              Icons.Filled.Keyboard,
              contentDescription = null
            )
          },
          onClick = {
            onEnterInviteCode()
            onDismiss()
          },
        )
      }
    }
  }
}
