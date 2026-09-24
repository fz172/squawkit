package dev.fanfly.wingslog.feature.developeroptions.stresstest.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.developeroptions.stresstest.FakeDataGenerator
import dev.fanfly.wingslog.thing.ThingTemplate
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.developeroptions.stresstest.generated.resources.Res
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_config_components
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_config_meters
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_config_none
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_config_slot_count

/**
 * What [FakeDataGenerator] will build for a preset that has no knobs of its own: each top-level
 * slot with the count it will get, and the meters its logs will read. Read from the template so a
 * preset added later previews correctly without touching this screen.
 */
@Composable
internal fun TemplatePreview(template: ThingTemplate) {
  val none = stringResource(Res.string.stress_test_config_none)
  val components = template.component_slots.map { slot ->
    stringResource(
      Res.string.stress_test_config_slot_count,
      slot.label,
      FakeDataGenerator.instanceCount(slot),
    )
  }
  val meters = template.meters.map { it.label }
  ReadOnlyRow(
    label = stringResource(Res.string.stress_test_config_components),
    value = components.ifEmpty { listOf(none) }
      .joinToString(" · "),
  )
  ReadOnlyRow(
    label = stringResource(Res.string.stress_test_config_meters),
    value = meters.ifEmpty { listOf(none) }
      .joinToString(" · "),
  )
}

@Composable
private fun ReadOnlyRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.primary,
      textAlign = TextAlign.End,
      modifier = Modifier.padding(start = Spacing.medium),
    )
  }
}
