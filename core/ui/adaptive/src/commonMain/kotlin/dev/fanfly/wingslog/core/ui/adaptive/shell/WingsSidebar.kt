package dev.fanfly.wingslog.core.ui.adaptive.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.adaptive.layout.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.shell.switcher.AllThingsRow
import dev.fanfly.wingslog.core.ui.adaptive.shell.switcher.SelectedThingBlock
import dev.fanfly.wingslog.core.ui.adaptive.thingIcon
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.widget.avataricon.compose.AvatarIcon
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.app_name
import wingslog.core.sharedassets.generated.resources.ic_launcher_foreground
import wingslog.core.sharedassets.generated.resources.switcher_switch_to
import wingslog.core.sharedassets.generated.resources.Res as UiRes

/**
 * The sidebar tiers' nav container: brand, the selected thing, its sections, the switch list,
 * and an account footer. MEDIUM draws it narrower with abbreviated labels.
 */
@Composable
internal fun WingsSidebar(
  state: AdaptiveShellUiState,
  onSelectSection: (ShellSection) -> Unit,
  onSelectThing: (String) -> Unit,
  onAddThing: () -> Unit,
  onEnterInviteCode: (() -> Unit)? = null,
  onOpenAccount: () -> Unit,
  // When true (empty fleet) the switcher is hidden and the per-thing sections are muted — but
  // still tappable, so tapping any of them leaves Settings and returns to the add-thing prompt.
  // With no thing there's no per-thing content, so none of them appears selected.
  sectionsMuted: Boolean = false,
  showSwitcher: Boolean = true,
) {
  Surface(
    modifier = Modifier.fillMaxHeight()
      .width(LocalLayoutTier.current.sidebarWidth),
    color = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier.fillMaxHeight()
        .padding(vertical = 12.dp)
    ) {
      // Brand
      Row(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          painter = painterResource(UiRes.drawable.ic_launcher_foreground),
          contentDescription = null,
          modifier = Modifier.size(44.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
          stringResource(UiRes.string.app_name),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
      }

      if (showSwitcher) {
        SelectedThingBlock(
          state = state,
          onSelectThing = onSelectThing,
          onAddThing = onAddThing,
          onEnterInviteCode = onEnterInviteCode,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
      }

      // Directly under the block, because they are its sections.
      perThingSections().forEach { section ->
        SidebarItem(
          section,
          selected = !sectionsMuted && state.section == section,
          muted = sectionsMuted,
          onClick = { onSelectSection(section) })
      }

      // Nothing here scrolls: four others at most, then the picker for the rest. The structure is
      // the same at three things and at seventeen.
      val others = state.things.filter { it.id != state.selectedThingId }
      if (showSwitcher && others.isNotEmpty()) {
        HorizontalDivider(
          modifier = Modifier.padding(
            horizontal = 20.dp,
            vertical = Spacing.medium
          )
        )
        Text(
          stringResource(UiRes.string.switcher_switch_to),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(
            horizontal = 24.dp,
            vertical = Spacing.extraSmall
          ),
        )
        others.take(QUICK_SWITCH_ROWS)
          .forEach { thing ->
            NavigationDrawerItem(
              label = {
                Text(
                  thing.label,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              },
              icon = {
                Icon(
                  thingIcon(thing.template?.icon.orEmpty()),
                  contentDescription = null
                )
              },
              selected = false,
              onClick = { onSelectThing(thing.id) },
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
          }
        if (others.size > QUICK_SWITCH_ROWS) {
          AllThingsRow(
            count = state.things.size,
            state = state,
            onSelectThing = onSelectThing,
            onAddThing = onAddThing,
            onEnterInviteCode = onEnterInviteCode,
          )
        }
      }

      Spacer(Modifier.weight(1f))
      HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
      // Combined account + settings entry: the user's avatar and name; opens the Settings section.
      NavigationDrawerItem(
        label = {
          AccountLabel(state)
        },
        icon = {
          AvatarIcon(
            displayName = state.accountName,
            photoUri = state.accountPhotoUrl,
            size = 28.dp,
          )
        },
        selected = state.section == ShellSection.SETTINGS,
        onClick = onOpenAccount,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
      )
    }
  }
}

@Composable
private fun SidebarItem(
  section: ShellSection,
  selected: Boolean,
  onClick: () -> Unit,
  // Muted (empty-fleet) items keep the greyed-out look but stay tappable, so they can route back to
  // the add-thing prompt from Settings.
  muted: Boolean = false,
) {
  val label =
    if (LocalLayoutTier.current.hasWideSidebar) section.title()
    else section.narrowSidebarLabel()
  NavigationDrawerItem(
    label = { Text(label) },
    icon = { Icon(section.icon, contentDescription = null) },
    selected = selected,
    onClick = onClick,
    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
      .then(if (muted) Modifier.alpha(DisabledSectionAlpha) else Modifier),
  )
}

/** The sidebar account/settings entry label: the signed-in user's name, not the current section. */
@Composable
private fun AccountLabel(state: AdaptiveShellUiState) = Text(
  state.accountName?.takeIf { it.isNotBlank() } ?: "Account",
  maxLines = 1,
  overflow = TextOverflow.Ellipsis,
)

private const val DisabledSectionAlpha = 0.38f

/** How many other things the sidebar offers before sending the rest to the picker. */
private const val QUICK_SWITCH_ROWS = 4
