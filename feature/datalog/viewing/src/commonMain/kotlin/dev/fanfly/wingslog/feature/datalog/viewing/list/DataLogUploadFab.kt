package dev.fanfly.wingslog.feature.datalog.viewing.list

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.dataLogNoun
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.attachment.viewing.rememberFilePicker
import dev.fanfly.wingslog.id.ThingId
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_upload

/** The same list ViewModel the section body uses, keyed by thing so the switcher never reuses it. */
@Composable
internal fun dataLogListViewModel(thingId: ThingId): DataLogListViewModel =
  koinViewModel(
    key = "datalog-${thingId.value}",
    parameters = { parametersOf(thingId.value) })

/**
 * The phone FAB for the section (PRD R2): opens the file picker, or the link-account prompt for a
 * guest. Rendered in the shell's FAB slot by the dashboard, which owns that slot.
 */
@Composable
fun DataLogUploadFab(
  thingId: ThingId,
  onLinkAccount: () -> Unit,
  modifier: Modifier = Modifier
) {
  val viewModel = dataLogListViewModel(thingId)
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showPrompt by remember { mutableStateOf(false) }
  val pick = rememberFilePicker(onResult = viewModel::upload)
  ExtendedFloatingActionButton(
    onClick = {
      if (state.uploadGate == UploadGate.SignedIn) pick() else showPrompt = true
    },
    icon = { Icon(Icons.Filled.UploadFile, contentDescription = null) },
    text = {
      Text(
        stringResource(
          Res.string.data_log_upload,
          LexiconFormatter.titleCase(LocalThingLexicon.current.dataLogNoun)
        )
      )
    },
    modifier = modifier.padding(end = Spacing.medium),
  )
  if (showPrompt) {
    LinkAccountPromptSheet(
      onOpenSettings = { showPrompt = false; onLinkAccount() },
      onDismiss = { showPrompt = false },
    )
  }
}
