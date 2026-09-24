package dev.fanfly.wingslog.feature.sharing.viewing.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.template.technicianNoun
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.invite_role_owner
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_help_footer
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_help_title
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_perm_manage_access
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_perm_squawks_tasks
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_perm_thing_details

@Composable
internal fun PermissionsCard(expanded: Boolean, onToggle: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(
        MaterialTheme.colorScheme.surfaceContainer,
        RoundedCornerShape(Spacing.cardCornerRadius)
      ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onToggle)
        .padding(Spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Icon(
        Icons.Filled.Security,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        stringResource(Res.string.manage_access_help_title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.weight(1f),
      )
      Icon(
        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (expanded) {
      Column(
        modifier = Modifier.padding(
          start = Spacing.medium,
          end = Spacing.medium,
          bottom = Spacing.medium
        )
      ) {
        val rows = listOf(
          Triple(
            stringResource(
              Res.string.manage_access_perm_squawks_tasks,
              LexiconFormatter.sentenceCasePlural(LocalThingLexicon.current.squawkNoun),
            ), true, true
          ),
          Triple(
            LexiconFormatter.sentenceCasePlural(LocalThingLexicon.current.logNoun),
            true,
            true
          ),
          Triple(
            stringResource(
              Res.string.manage_access_perm_thing_details,
              LexiconFormatter.sentenceCase(LocalThingLexicon.current.thingNoun),
            ), false, true
          ),
          Triple(
            stringResource(Res.string.manage_access_perm_manage_access),
            false,
            true
          ),
        )
        Row(Modifier.fillMaxWidth()) {
          Spacer(Modifier.weight(1f))
          Text(
            LexiconFormatter.titleCase(LocalThingLexicon.current.technicianNoun),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.Center,
          )
          Text(
            stringResource(Res.string.invite_role_owner),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.Center,
          )
        }
        rows.forEach { (label, tech, owner) ->
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          Row(
            modifier = Modifier.fillMaxWidth()
              .padding(vertical = Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              label,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.weight(1f)
            )
            PermCell(tech, Modifier.width(64.dp))
            PermCell(owner, Modifier.width(64.dp))
          }
        }
        Text(
          stringResource(
            Res.string.manage_access_help_footer,
            LocalThingLexicon.current.thingNoun.singular,
          ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = Spacing.small),
        )
      }
    }
  }
}
