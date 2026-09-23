package dev.fanfly.wingslog.feature.export.update.selection.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import dev.fanfly.wingslog.core.ui.text.formatFileSize
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
import dev.fanfly.wingslog.feature.export.update.selection.ExportUiState
import dev.fanfly.wingslog.feature.export.update.selection.rangeSummary
import dev.fanfly.wingslog.feature.export.update.selection.thingSummary
import dev.fanfly.wingslog.feature.subscription.viewing.ProUpsellSheet
import dev.fanfly.wingslog.feature.subscription.viewing.UpsellTrigger
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_download
import wingslog.feature.export.sharedassets.generated.resources.export_email_sent_action
import wingslog.feature.export.sharedassets.generated.resources.export_location_downloads_squawkit
import wingslog.feature.export.sharedassets.generated.resources.export_location_files_squawkit
import wingslog.feature.export.sharedassets.generated.resources.export_send_to_email_action
import wingslog.feature.export.sharedassets.generated.resources.export_stub_preview_file_name
import wingslog.feature.export.sharedassets.generated.resources.export_stub_preview_location
import wingslog.feature.export.sharedassets.generated.resources.export_success_delivery_auth
import wingslog.feature.export.sharedassets.generated.resources.export_success_delivery_failed
import wingslog.feature.export.sharedassets.generated.resources.export_success_delivery_failed_title
import wingslog.feature.export.sharedassets.generated.resources.export_success_title
import wingslog.feature.export.sharedassets.generated.resources.export_view_exports

@Composable
internal fun SuccessResult(
  state: ExportUiState.Success,
  modifier: Modifier,
  onDownload: (exportId: String, filePath: String, fileName: String) -> Unit,
  onSendToEmail: () -> Unit,
  onHistory: () -> Unit,
  onSeePlans: () -> Unit,
  onActionsHeightChanged: (Dp) -> Unit = {},
) {
  val density = LocalDensity.current
  val fileName =
    state.fileName.ifBlank { stringResource(Res.string.export_stub_preview_file_name) }
  val location = state.displayLocation.ifBlank {
    when (state.displayLocationKind) {
      ExportDisplayLocation.DOWNLOADS_SQUAWKIT -> stringResource(Res.string.export_location_downloads_squawkit)
      ExportDisplayLocation.FILES_SQUAWKIT -> stringResource(Res.string.export_location_files_squawkit)
      ExportDisplayLocation.UNKNOWN -> stringResource(Res.string.export_stub_preview_location)
    }
  }

  var showUpsell by remember { mutableStateOf(false) }

  // The archive is always saved locally first; email is a separate, explicit action the user
  // takes below, never automatic.
  val emailSucceeded = state.persistedDeliveryState == "SENT"
  val deliveryFailed = state.persistedDeliveryState == "FAILED"
  // A failed email delivery folds into the receipt as a labeled status section rather than a
  // separate stacked card, so the success screen stays a single card.
  val deliveryFailure = if (deliveryFailed) {
    val reason = state.deliveryFailureMessage.ifBlank {
      stringResource(Res.string.export_success_delivery_failed)
    }
    val destination = state.deliveryInfo?.destinationEmail.orEmpty()
    DeliveryFailure(
      title = stringResource(Res.string.export_success_delivery_failed_title),
      message = if (destination.isNotBlank()) {
        reason + "\n" + stringResource(
          Res.string.export_success_delivery_auth,
          destination
        )
      } else {
        reason
      },
    )
  } else {
    null
  }

  ResultShell(
    modifier = modifier,
    heroIcon = Icons.Default.Check,
    heroColor = MaterialTheme.statusColors.positive.accent,
    heroContainer = MaterialTheme.statusColors.positive.container,
    title = stringResource(Res.string.export_success_title),
    subtitle = location,
    body = {
      ReceiptCard(
        fileName = fileName,
        sizeText = state.sizeBytes.formatFileSize(),
        formats = state.formats,
        thingSummary = thingSummary(state.selectedTailNumbers),
        rangeText = rangeSummary(
          state.dateRange,
          state.customStart,
          state.customEnd
        ),
        deliveryFailure = deliveryFailure,
      )
    },
    actions = {
      Row(
        modifier = Modifier.fillMaxWidth()
          .onGloballyPositioned { coordinates ->
            onActionsHeightChanged(with(density) { coordinates.size.height.toDp() })
          },
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        SuccessBarAction(
          modifier = Modifier.weight(1f),
          icon = Icons.Default.Download,
          label = stringResource(Res.string.export_download),
          onClick = { onDownload(state.exportId, state.filePath, fileName) },
        )
        when {
          state.deliveryInfo != null -> SuccessBarAction(
            modifier = Modifier.weight(1f),
            icon = if (emailSucceeded) Icons.Default.Check else Icons.Default.Mail,
            label = stringResource(
              if (emailSucceeded) Res.string.export_email_sent_action
              else Res.string.export_send_to_email_action
            ),
            enabled = !emailSucceeded && !state.isSendingEmail,
            onClick = onSendToEmail,
          )

          state.emailDeliveryLocked -> SuccessBarAction(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Lock,
            label = stringResource(Res.string.export_send_to_email_action),
            onClick = { showUpsell = true },
          )

          else -> Spacer(Modifier.weight(1f))
        }
        SuccessBarAction(
          modifier = Modifier.weight(1f),
          icon = Icons.Default.History,
          label = stringResource(Res.string.export_view_exports),
          onClick = onHistory,
        )
      }
    },
  )

  if (showUpsell) {
    ProUpsellSheet(
      trigger = UpsellTrigger.EMAIL_EXPORT,
      onSeePlans = {
        onSeePlans()
        showUpsell = false
      },
      onDismiss = { showUpsell = false },
    )
  }
}
