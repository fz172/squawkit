package dev.fanfly.wingslog.feature.stresstest.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.stresstest.generated.resources.Res
import wingslog.feature.stresstest.generated.resources.stress_test_config_template

@Composable
internal fun TemplateRow(selectedId: String, onSelect: (String) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Text(
      text = stringResource(Res.string.stress_test_config_template),
      style = MaterialTheme.typography.bodyMedium,
    )
    // Read from the registry rather than a hardcoded list, so a preset added to
    // CanonicalTemplates.ALL appears here without touching this screen.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
      CanonicalTemplates.ALL.sortedBy { it.sort_order }
        .forEach { template ->
          FilterChip(
            selected = template.id == selectedId,
            onClick = { onSelect(template.id) },
            label = { Text(template.display_name) },
          )
        }
    }
  }
}
