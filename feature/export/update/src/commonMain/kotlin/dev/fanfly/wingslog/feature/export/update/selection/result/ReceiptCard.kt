package dev.fanfly.wingslog.feature.export.update.selection.result

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.export.datamanager.ExportFormat
import dev.fanfly.wingslog.feature.export.update.selection.joinFormats
import dev.fanfly.wingslog.feature.export.update.selection.thingSummary
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.your_stuff
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_receipt_attachments
import wingslog.feature.export.sharedassets.generated.resources.export_receipt_attachments_included
import wingslog.feature.export.sharedassets.generated.resources.export_receipt_file_subtitle
import wingslog.feature.export.sharedassets.generated.resources.export_receipt_range
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@Composable
internal fun ReceiptCard(
  fileName: String,
  sizeText: String,
  formats: Set<ExportFormat>,
  thingSummary: String,
  rangeText: String,
  deliveryFailure: DeliveryFailure? = null,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(
        width = Spacing.hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(Spacing.cardCornerRadius),
      )
      .padding(Spacing.large),
    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    if (deliveryFailure != null) {
      DeliveryFailureSection(deliveryFailure)
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(RoundedCornerShape(Spacing.cardCornerRadius))
          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Default.FolderZip,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(24.dp),
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = fileName,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = stringResource(
            Res.string.export_receipt_file_subtitle,
            sizeText,
            joinFormats(formats)
          ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    ReceiptRow(
      Icons.Default.Category,
      stringResource(CoreRes.string.your_stuff),
      thingSummary,
      mono = true
    )
    ReceiptRow(
      Icons.Default.DateRange,
      stringResource(Res.string.export_receipt_range),
      rangeText
    )
    ReceiptRow(
      Icons.Default.Attachment,
      stringResource(Res.string.export_receipt_attachments),
      stringResource(Res.string.export_receipt_attachments_included),
    )
  }
}
