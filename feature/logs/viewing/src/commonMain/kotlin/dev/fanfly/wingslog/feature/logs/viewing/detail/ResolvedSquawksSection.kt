package dev.fanfly.wingslog.feature.logs.viewing.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.Squawk
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.viewing.generated.resources.resolved_squawks
import wingslog.feature.logs.viewing.generated.resources.unknown_squawk
import wingslog.feature.logs.viewing.generated.resources.Res as ViewingRes

@Composable
internal fun ResolvedSquawksSection(
  log: MaintenanceLog,
  availableSquawks: List<Squawk>,
  onSquawkClick: ((String) -> Unit)?,
) {
  Text(
    text = stringResource(
      ViewingRes.string.resolved_squawks,
      LexiconFormatter.titleCasePlural(LocalThingLexicon.current.squawkNoun),
    ),
    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
    color = MaterialTheme.colorScheme.onSurface,
  )

  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    log.squawk_ids.forEach { squawkId ->
      val squawk = availableSquawks.find { it.id == squawkId }
      val title = squawk?.title?.takeIf { it.isNotBlank() }
        ?: stringResource(
          ViewingRes.string.unknown_squawk,
          squawkId,
          LocalThingLexicon.current.squawkNoun.singular,
        )
      LinkedEntityRow(
        title = title,
        icon = Icons.Default.Warning,
        onClick = onSquawkClick?.let { cb -> { cb(squawkId) } },
      )
    }
  }
}
