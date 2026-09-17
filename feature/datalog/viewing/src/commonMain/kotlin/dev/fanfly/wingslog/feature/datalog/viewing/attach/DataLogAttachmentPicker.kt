package dev.fanfly.wingslog.feature.datalog.viewing.attach

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.core.datetime.formatDuration
import dev.fanfly.wingslog.core.datetime.toClockText
import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.dataLogNoun
import dev.fanfly.wingslog.core.ui.common.compose.StatusChip
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.attachment.model.PickedDataLog
import dev.fanfly.wingslog.feature.attachment.viewing.DataLogPickerSlot
import dev.fanfly.wingslog.feature.attachment.viewing.rememberFilePicker
import dev.fanfly.wingslog.feature.datalog.viewing.list.DataLogRow
import dev.fanfly.wingslog.feature.datalog.viewing.list.ImportRowCard
import dev.fanfly.wingslog.feature.datalog.viewing.list.titleText
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.thing.Section
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_empty_title
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_ground_run
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_picker_attach
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_picker_attached
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_picker_same_day
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_picker_title
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_picker_upload
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * The attachment picker's data log option for a form on [thingId], or null when the Thing has no
 * data logs section or this build has no visualizer (design §9.2). [attachedIds] are the logs
 * already on the parent, shown checked and not offered again; [recordDate] annotates same-day rows.
 * [onAttach] receives every checked log.
 */
@Composable
fun rememberDataLogPickerSlot(
  thingId: ThingId,
  recordDate: LocalDate?,
  attachedIds: Set<DataLogId>,
): DataLogPickerSlot? {
  // The Thing's own template decides, and nothing else does since the rollout switch went (T46).
  val hasSection =
    LocalThingCapabilities.current.sections.contains(Section.SECTION_DATA_LOGS)
  if (!hasSection) return null
  val label =
    LexiconFormatter.sentenceCase(LocalThingLexicon.current.dataLogNoun)
  return DataLogPickerSlot(label) { onAttach, onCancel ->
    DataLogAttachmentPicker(
      thingId,
      recordDate,
      attachedIds,
      onAttach,
      onCancel
    )
  }
}

@Composable
fun DataLogAttachmentPicker(
  thingId: ThingId,
  recordDate: LocalDate?,
  attachedIds: Set<DataLogId>,
  onAttach: (List<PickedDataLog>) -> Unit,
  onCancel: () -> Unit,
) {
  val viewModel: DataLogAttachmentPickerViewModel = koinViewModel(
    key = "datalog-picker-${thingId.value}",
    parameters = { parametersOf(thingId.value) },
  )
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val pick = rememberFilePicker(onResult = viewModel::upload)
  val noun = LocalThingLexicon.current.dataLogNoun.singular
  val groundRun = stringResource(Res.string.data_log_ground_run)
  val toAttach =
    state.rows.filter { it.id in state.selected && it.id !in attachedIds }

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    Text(
      stringResource(Res.string.data_log_picker_title, noun),
      style = MaterialTheme.typography.titleMedium,
    )
    state.import?.let { row ->
      ImportRowCard(
        row,
        onKeepBoth = viewModel::confirmImport,
        onDismiss = viewModel::dismissImport
      )
    }
    if (state.loaded && state.rows.isEmpty()) {
      Text(
        stringResource(Res.string.data_log_empty_title, noun),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
      items(state.rows, key = { it.id.value }) { row ->
        PickerRow(
          row = row,
          title = row.titleText(groundRun),
          selected = row.id in state.selected,
          attached = row.id in attachedIds,
          sameDay = recordDate != null && row.startLocal.date == recordDate,
          onClick = { viewModel.toggle(row.id) },
        )
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      if (state.canUpload) {
        TextButton(onClick = pick) {
          Icon(
            Icons.Filled.Upload,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
          )
          Spacer(Modifier.size(ButtonDefaults.IconSpacing))
          Text(stringResource(Res.string.data_log_picker_upload))
        }
      }
      Spacer(Modifier.weight(1f))
      TextButton(onClick = onCancel) { Text(stringResource(CoreRes.string.cancel)) }
      FilledTonalButton(
        enabled = toAttach.isNotEmpty(),
        onClick = {
          onAttach(toAttach.map {
            PickedDataLog(
              it.id,
              it.titleText(groundRun)
            )
          })
        },
      ) {
        Text(stringResource(Res.string.data_log_picker_attach))
      }
    }
  }
}

@Composable
private fun PickerRow(
  row: DataLogRow,
  title: String,
  selected: Boolean,
  attached: Boolean,
  sameDay: Boolean,
  onClick: () -> Unit,
) {
  val details = buildList {
    add(row.startLocal.time.toClockText())
    add(formatDuration(row.durationSeconds))
    if (row.product.isNotBlank()) add(row.product)
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(enabled = !attached, onClick = onClick)
      .padding(vertical = Spacing.small),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    Checkbox(
      checked = selected || attached,
      onCheckedChange = null,
      enabled = !attached
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        title,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        details.joinToString(" · "),
        style = WingslogTypography.dataSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (attached) {
      StatusChip(
        stringResource(Res.string.data_log_picker_attached),
        StatusTier.NEUTRAL
      )
    } else if (sameDay) {
      StatusChip(
        stringResource(Res.string.data_log_picker_same_day),
        StatusTier.POSITIVE
      )
    }
  }
}
