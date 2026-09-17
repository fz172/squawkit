package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.LayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.common.compose.FormSectionLabel
import dev.fanfly.wingslog.core.ui.common.compose.FormTextField
import dev.fanfly.wingslog.core.ui.common.compose.ModalBottomSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.id.DataLogId
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.dismiss
import wingslog.feature.attachment.sharedassets.generated.resources.add_link
import wingslog.feature.attachment.sharedassets.generated.resources.add_link_description
import wingslog.feature.attachment.sharedassets.generated.resources.attach_data_log_description
import wingslog.feature.attachment.sharedassets.generated.resources.attachment_file_count
import wingslog.feature.attachment.sharedassets.generated.resources.attachment_group_device
import wingslog.feature.attachment.sharedassets.generated.resources.attachment_group_other
import wingslog.feature.attachment.sharedassets.generated.resources.attachment_picker_title
import wingslog.feature.attachment.sharedassets.generated.resources.attachment_size_hint
import wingslog.feature.attachment.sharedassets.generated.resources.choose_file
import wingslog.feature.attachment.sharedassets.generated.resources.choose_file_description
import wingslog.feature.attachment.sharedassets.generated.resources.file_upload_coming_soon
import wingslog.feature.attachment.sharedassets.generated.resources.invalid_url
import wingslog.feature.attachment.sharedassets.generated.resources.link_name
import wingslog.feature.attachment.sharedassets.generated.resources.link_url
import wingslog.feature.attachment.sharedassets.generated.resources.take_photo
import wingslog.feature.attachment.sharedassets.generated.resources.take_photo_description
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.attachment.sharedassets.generated.resources.Res as AttachRes

/**
 * The data log picker a form screen supplies: the option's label and the body that replaces the
 * options once it is chosen, exactly as *Add link* swaps to the URL field (design §9.2).
 */
class DataLogPickerSlot(
  val label: String,
  val body: @Composable (onAttach: (List<PickedDataLog>) -> Unit, onCancel: () -> Unit) -> Unit,
)

/** A data log checked in the picker; [displayName] becomes the attachment's name. */
sealed data class PickedDataLog(
  val id: DataLogId,
  val displayName: String,
)

private enum class PickerStep { OPTIONS, LINK, DATA_LOG }

private class PickerOption(
  val icon: ImageVector,
  val title: String,
  val description: String,
  val enabled: Boolean,
  val onClick: () -> Unit,
)

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
        description = stringResource(AttachRes.string.choose_file_description),
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

      else -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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

@Composable
private fun PickerHeader(showClose: Boolean, onDismiss: () -> Unit) {
  Column {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = if (showClose) Spacing.none else Spacing.small),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(AttachRes.string.attachment_picker_title),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f),
      )
      if (showClose) {
        IconButton(onClick = onDismiss) {
          Icon(
            Icons.Default.Close,
            contentDescription = stringResource(CoreRes.string.dismiss),
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
      }
    }
    if (showClose) {
      HorizontalDivider(modifier = Modifier.padding(top = Spacing.medium))
    }
  }
}

@Composable
private fun PickerGroup(
  label: String,
  options: List<PickerOption>,
  columns: Int,
  trailing: @Composable () -> Unit = {},
  footer: (@Composable () -> Unit)? = null,
) {
  val isGrid = columns > 1
  Column(
    modifier = Modifier.padding(top = Spacing.medium),
    verticalArrangement = Arrangement.spacedBy(if (isGrid) Spacing.small else Spacing.none),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = if (isGrid) Spacing.extraSmall else Spacing.small),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FormSectionLabel(
        text = label,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.weight(1f),
      )
      trailing()
    }
    if (isGrid) {
      options.chunked(columns)
        .forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            row.forEach { OptionCard(it, Modifier.weight(1f)) }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
          }
        }
    } else {
      options.forEach { OptionRow(it) }
    }
    footer?.invoke()
  }
}

/** Phone row: icon tile, title + description, chevron. */
@Composable
private fun OptionRow(option: PickerOption) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .clickable(enabled = option.enabled, onClick = option.onClick)
      .padding(Spacing.small)
      .alpha(if (option.enabled) 1f else DISABLED_ALPHA),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    OptionContent(option)
    Icon(
      Icons.Default.ChevronRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.outline,
      modifier = Modifier.size(Spacing.xLarge),
    )
  }
}

/** Dialog card: outlined tile in a grid cell, no chevron. */
@Composable
private fun OptionCard(option: PickerOption, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(Spacing.cardCornerRadius)
  Row(
    modifier = modifier
      .clip(shape)
      .background(MaterialTheme.colorScheme.surfaceContainerLow)
      .border(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant, shape)
      .clickable(enabled = option.enabled, onClick = option.onClick)
      .padding(Spacing.medium)
      .alpha(if (option.enabled) 1f else DISABLED_ALPHA),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    OptionContent(option)
  }
}

@Composable
private fun RowScope.OptionContent(option: PickerOption) {
  val tileShape = RoundedCornerShape(Spacing.smallCornerRadius)
  Box(
    modifier = Modifier
      .clip(tileShape)
      .border(
        Spacing.hairline,
        MaterialTheme.colorScheme.secondaryContainer,
        tileShape
      )
      .padding(Spacing.small),
  ) {
    Icon(
      option.icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(Spacing.extraLarge),
    )
  }
  Column(modifier = Modifier.weight(1f)) {
    Text(
      text = option.title,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = option.description,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** Size hint plus the used/allowed file badge, which also shows when the cap is reached. */
@Composable
private fun DeviceLimits(fileCount: Int) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    Text(
      text = stringResource(AttachRes.string.attachment_size_hint),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = stringResource(AttachRes.string.attachment_file_count, fileCount),
      style = WingslogTypography.dataSmall,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier
        .background(
          MaterialTheme.colorScheme.primaryContainer,
          RoundedCornerShape(Spacing.badgeCornerRadius),
        )
        .padding(horizontal = Spacing.small),
    )
  }
}

@Composable
private fun UpsellHint() {
  Text(
    text = stringResource(AttachRes.string.file_upload_coming_soon),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = Spacing.small),
  )
}

@Composable
private fun LinkForm(
  onAddLink: (url: String, name: String) -> Unit,
  onCancel: () -> Unit,
) {
  var linkUrl by remember { mutableStateOf("") }
  var linkName by remember { mutableStateOf("") }
  var urlError by remember { mutableStateOf(false) }

  Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
    FormTextField(
      value = linkUrl,
      onValueChange = { linkUrl = it; urlError = false },
      label = stringResource(AttachRes.string.link_url),
      isError = urlError,
      supportingText = if (urlError) stringResource(AttachRes.string.invalid_url) else null,
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
    )
    FormTextField(
      value = linkName,
      onValueChange = { linkName = it },
      label = stringResource(AttachRes.string.link_name),
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
    )
    Row(
      horizontalArrangement = Arrangement.End,
      modifier = Modifier.fillMaxWidth(),
    ) {
      TextButton(onClick = onCancel) {
        Text(stringResource(CoreRes.string.cancel))
      }
      FilledTonalButton(onClick = {
        val trimmed = linkUrl.trim()
        if (!isValidUrl(trimmed)) {
          urlError = true
        } else {
          val normalized =
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
              trimmed
            } else {
              "https://$trimmed"
            }
          onAddLink(
            normalized,
            linkName.trim()
              .ifBlank { normalized.extractDomain() })
        }
      }) {
        Text(stringResource(AttachRes.string.add_link))
      }
    }
  }
}

private const val DISABLED_ALPHA = 0.38f

private fun String.extractDomain(): String {
  val withoutScheme = if (contains("://")) substringAfter("://") else this
  val hostAndPort = withoutScheme.substringBefore("/")
    .substringBefore("?")
    .substringBefore("#")
  val host = hostAndPort.substringBefore(":")
  return if (host.startsWith("www.")) host.drop(4) else host
}

private fun isValidUrl(url: String): Boolean {
  if (url.isBlank()) return false
  val lower = url.lowercase()
    .trim()
  if (lower.startsWith("ftp://") || lower.startsWith("file://") || lower.startsWith(
      "mailto:"
    )
  ) return false
  val normalized =
    if (lower.startsWith("http://") || lower.startsWith("https://")) lower else "https://$lower"
  val withoutProtocol = normalized.substringAfter("://")
  val dotIndex = withoutProtocol.indexOf('.')
  return dotIndex > 0 && dotIndex < withoutProtocol.lastIndex && !withoutProtocol.contains(
    ' '
  )
}
