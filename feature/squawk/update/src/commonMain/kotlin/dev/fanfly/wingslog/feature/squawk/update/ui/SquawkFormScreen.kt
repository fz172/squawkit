package dev.fanfly.wingslog.feature.squawk.update.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.aircraft.SquawkPriority
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.squawk.update.viewmodel.SquawkFormState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.add_squawk
import wingslog.feature.squawk.sharedassets.generated.resources.edit_squawk
import wingslog.feature.squawk.sharedassets.generated.resources.priority_aog
import wingslog.feature.squawk.sharedassets.generated.resources.priority_high
import wingslog.feature.squawk.sharedassets.generated.resources.priority_low
import wingslog.feature.squawk.sharedassets.generated.resources.priority_medium
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_description_label
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_priority_label
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_title_label
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_title_required
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_updated
import wingslog.feature.squawk.update.generated.resources.Res as UpdateRes
import wingslog.feature.squawk.update.generated.resources.save

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquawkFormScreen(
  state: SquawkFormState,
  onTitleChange: (String) -> Unit,
  onDescriptionChange: (String) -> Unit,
  onPriorityChange: (SquawkPriority) -> Unit,
  onSave: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isEdit = state.squawkId != null
  val title = if (isEdit) stringResource(Res.string.edit_squawk) else stringResource(Res.string.add_squawk)

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(title) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = Color.Transparent,
          scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(padding)
        .padding(horizontal = Spacing.screenPadding),
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      Spacer(Modifier.height(Spacing.small))

      OutlinedTextField(
        value = state.title,
        onValueChange = onTitleChange,
        label = { Text(stringResource(Res.string.squawk_title_label)) },
        isError = state.titleError,
        supportingText = if (state.titleError) {
          { Text(stringResource(Res.string.squawk_title_required)) }
        } else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        readOnly = state.isAddressedReadOnly,
      )

      OutlinedTextField(
        value = state.description,
        onValueChange = onDescriptionChange,
        label = { Text(stringResource(Res.string.squawk_description_label)) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
        readOnly = state.isAddressedReadOnly,
      )

      Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(
          text = stringResource(Res.string.squawk_priority_label),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val priorities = listOf(
          SquawkPriority.SQUAWK_PRIORITY_LOW to stringResource(Res.string.priority_low),
          SquawkPriority.SQUAWK_PRIORITY_MEDIUM to stringResource(Res.string.priority_medium),
          SquawkPriority.SQUAWK_PRIORITY_HIGH to stringResource(Res.string.priority_high),
          SquawkPriority.SQUAWK_PRIORITY_AOG to stringResource(Res.string.priority_aog),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          priorities.forEachIndexed { index, (priority, label) ->
            SegmentedButton(
              selected = state.priority == priority,
              onClick = { onPriorityChange(priority) },
              shape = SegmentedButtonDefaults.itemShape(index = index, count = priorities.size),
            ) { Text(label) }
          }
        }
      }

      Spacer(Modifier.height(Spacing.medium))

      Button(
        onClick = onSave,
        enabled = !state.isSaving && !state.isAddressedReadOnly,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(stringResource(UpdateRes.string.save))
      }

      Spacer(Modifier.height(Spacing.screenPadding))
    }
  }
}
