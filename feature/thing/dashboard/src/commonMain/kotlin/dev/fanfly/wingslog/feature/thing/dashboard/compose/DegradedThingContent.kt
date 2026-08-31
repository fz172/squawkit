package dev.fanfly.wingslog.feature.thing.dashboard.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import dev.fanfly.wingslog.core.appinfo.rememberAppUpdatePrompt
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.thing.Thing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.thing.dashboard.generated.resources.degraded_body
import wingslog.feature.thing.dashboard.generated.resources.degraded_no_stored_details
import wingslog.feature.thing.dashboard.generated.resources.degraded_reload
import wingslog.feature.thing.dashboard.generated.resources.degraded_stored_details
import wingslog.feature.thing.dashboard.generated.resources.degraded_title
import wingslog.feature.thing.dashboard.generated.resources.degraded_update
import wingslog.feature.thing.dashboard.generated.resources.Res as DashboardRes

/**
 * A Thing whose DNA this build cannot interpret (`template_system_design.md` §6.2).
 *
 * Replaces *every* per-thing section rather than appearing inside one: the sections themselves are
 * template-declared, so which of them should exist is exactly what is unknown here.
 *
 * Three rules the design fixes, all visible in what this does not do. It does not hide the Thing —
 * it is the user's data and stays in the switcher. It does not label the spec, because the only
 * labels available come from a fallback template and would caption a boat's data in airplane words.
 * It offers no edit affordance, because writing under rules we cannot read is how a client produces
 * data that violates them.
 */
@Composable
fun DegradedThingContent(thing: Thing) {
  val prompt = rememberAppUpdatePrompt()
  Column(
    modifier = Modifier.fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(Spacing.screenPadding),
    verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    // The one thing renderable without the template: the Thing carries its name directly. Omitted
    // rather than filled with a placeholder when blank — a generated stand-in would be this
    // screen's only invented text, on the screen whose whole point is showing what is stored.
    if (thing.name.isNotBlank()) {
      Text(
        text = thing.name,
        style = MaterialTheme.typography.headlineSmall,
      )
    }

    Card(
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
      ),
    ) {
      Column(
        modifier = Modifier.padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(Spacing.small),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Outlined.Info, contentDescription = null)
          Text(
            text = stringResource(DashboardRes.string.degraded_title),
            style = MaterialTheme.typography.titleMedium,
          )
        }
        Text(
          text = stringResource(DashboardRes.string.degraded_body),
          style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = prompt.launch) {
          Text(
            stringResource(
              if (prompt.isReload) DashboardRes.string.degraded_reload
              else DashboardRes.string.degraded_update,
            ),
          )
        }
      }
    }

    Text(
      text = stringResource(DashboardRes.string.degraded_stored_details),
      style = MaterialTheme.typography.titleSmall,
    )

    if (thing.spec.isEmpty()) {
      Text(
        text = stringResource(DashboardRes.string.degraded_no_stored_details),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      thing.spec.forEach { entry ->
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
          // The raw key, monospaced, because it is a stored identifier and not a label — nothing
          // here should read as copy the app chose.
          Text(
            text = entry.key,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = entry.value_,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
          )
        }
      }
    }
  }
}
