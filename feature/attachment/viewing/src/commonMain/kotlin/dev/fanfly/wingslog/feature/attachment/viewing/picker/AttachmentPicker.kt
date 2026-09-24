package dev.fanfly.wingslog.feature.attachment.viewing.picker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.ui.layout.ContentWidth
import dev.fanfly.wingslog.core.ui.layout.LayoutTier
import dev.fanfly.wingslog.core.ui.layout.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.popup.ModalBottomSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.attachment.model.PickedDataLog
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.attachment.viewing.FileDropTarget
import dev.fanfly.wingslog.feature.attachment.viewing.chooseFileDescription
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.feature.attachment.sharedassets.generated.resources.add_link
import wingslog.feature.attachment.sharedassets.generated.resources.add_link_description
import wingslog.feature.attachment.sharedassets.generated.resources.attach_data_log_description
import wingslog.feature.attachment.sharedassets.generated.resources.attachment_group_device
import wingslog.feature.attachment.sharedassets.generated.resources.attachment_group_other
import wingslog.feature.attachment.sharedassets.generated.resources.choose_file
import wingslog.feature.attachment.sharedassets.generated.resources.take_photo
import wingslog.feature.attachment.sharedassets.generated.resources.take_photo_description
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.attachment.sharedassets.generated.resources.Res as AttachRes

/**
 * Grouped attachment sources. A bottom sheet of rows on COMPACT; a centered dialog of two-column
 * cards on wider tiers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachmentPicker(
  fileCount: Int,
  filesAtLimit: Boolean,
  uploadEnabled: Boolean,
  onChooseFile: () -> Unit,
  onDropFiles: (List<PickedFile>) -> Unit,
  onDropError: () -> Unit,
  onTakePhoto: () -> Unit,
  onAddLink: (url: String, name: String) -> Unit,
  onUpsell: (() -> Unit)?,
  onDismiss: () -> Unit,
  dataLogPicker: DataLogPickerSlot?,
  onAttachDataLogs: (List<PickedDataLog>) -> Unit,
) {
  // Gated upload with an upsell keeps the file/photo options tappable; they open the promo.
  val upsellLocked = !uploadEnabled && onUpsell != null
  val uploadOptionEnabled = upsellLocked || (uploadEnabled && !filesAtLimit)
  var step by remember { mutableStateOf(PickerStep.OPTIONS) }
  val appCapability: AppCapability = koinInject()

  val deviceOptions = buildList {
    if (appCapability.isCameraCaptureSupported) {
      add(
        PickerOption(
          icon = Icons.Outlined.PhotoCamera,
          title = stringResource(AttachRes.string.take_photo),
          description = stringResource(AttachRes.string.take_photo_description),
          enabled = uploadOptionEnabled,
          onClick = if (upsellLocked) onUpsell else onTakePhoto,
        )
      )
    }
    add(
      PickerOption(
        icon = Icons.Outlined.UploadFile,
        title = stringResource(AttachRes.string.choose_file),
        description = stringResource(chooseFileDescription),
        enabled = uploadOptionEnabled,
        onClick = if (upsellLocked) onUpsell else onChooseFile,
      )
    )
  }
  val otherOptions = buildList {
    add(
      PickerOption(
        icon = Icons.Outlined.Link,
        title = stringResource(AttachRes.string.add_link),
        description = stringResource(AttachRes.string.add_link_description),
        enabled = true,
        onClick = { step = PickerStep.LINK },
      )
    )
    if (dataLogPicker != null) {
      add(
        PickerOption(
          icon = Icons.Outlined.ShowChart,
          title = dataLogPicker.label,
          description = stringResource(AttachRes.string.attach_data_log_description),
          enabled = true,
          onClick = { step = PickerStep.DATA_LOG },
        )
      )
    }
  }

  val isDialog = LocalLayoutTier.current != LayoutTier.COMPACT
  val body: @Composable ColumnScope.() -> Unit = {
    when {
      step == PickerStep.DATA_LOG && dataLogPicker != null ->
        dataLogPicker.body(onAttachDataLogs) { step = PickerStep.OPTIONS }

      step == PickerStep.LINK -> Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
      ) {
        PickerHeader(showClose = isDialog, onDismiss = onDismiss)
        LinkForm(
          onAddLink = onAddLink,
          onCancel = { step = PickerStep.OPTIONS })
      }

      else -> FileDropTarget(
        enabled = uploadEnabled && !filesAtLimit,
        onDrop = onDropFiles,
        onReadError = onDropError,
      ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          PickerHeader(showClose = isDialog, onDismiss = onDismiss)
          PickerGroup(
            label = stringResource(AttachRes.string.attachment_group_device),
            options = deviceOptions,
            columns = if (isDialog) 2 else 1,
            trailing = {
              if (uploadEnabled) {
                DeviceLimits(fileCount = fileCount)
              }
            },
            footer = if (uploadEnabled) null else {
              { UpsellHint() }
            },
          )
          PickerGroup(
            label = stringResource(AttachRes.string.attachment_group_other),
            options = otherOptions,
            columns = if (isDialog) 2 else 1,
          )
          if (isDialog) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xLarge),
              horizontalArrangement = Arrangement.End,
            ) {
              OutlinedButton(onClick = onDismiss) {
                Text(stringResource(CoreRes.string.cancel))
              }
            }
          }
        }
      }
    }
  }

  if (isDialog) {
    Dialog(
      onDismissRequest = onDismiss,
      properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      Surface(
        modifier = Modifier
          .padding(Spacing.huge)
          .widthIn(max = ContentWidth.Dialog)
          .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
          Spacing.hairline,
          MaterialTheme.colorScheme.secondaryContainer
        ),
      ) {
        DisableSelection {
          Column(
            modifier = Modifier.padding(Spacing.extraLarge),
            content = body,
          )
        }
      }
    }
  } else {
    ModalBottomSheet(
      onDismissRequest = onDismiss,
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = Spacing.large),
        content = body,
      )
      Spacer(Modifier.height(Spacing.huge))
    }
  }
}
