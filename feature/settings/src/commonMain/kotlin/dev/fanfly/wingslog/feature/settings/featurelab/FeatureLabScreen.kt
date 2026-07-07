package dev.fanfly.wingslog.feature.settings.featurelab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.settings.generated.resources.Res
import wingslog.feature.settings.generated.resources.feature_lab
import wingslog.feature.settings.generated.resources.feature_lab_attachments_subtitle
import wingslog.feature.settings.generated.resources.feature_lab_attachments_title
import wingslog.feature.settings.generated.resources.feature_lab_export_email_subtitle
import wingslog.feature.settings.generated.resources.feature_lab_export_email_title
import wingslog.feature.settings.generated.resources.feature_lab_subtitle
import wingslog.feature.settings.generated.resources.feature_lab_technician_subtitle
import wingslog.feature.settings.generated.resources.feature_lab_technician_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureLabScreen(
  navController: NavController,
  viewModel: FeatureLabViewModel = koinViewModel(),
  dogfoodContent: @Composable () -> Unit = {},
) {
  val flags by viewModel.flags.collectAsStateWithLifecycle()

  Scaffold(
    topBar = {
      ConstrainedTopBar {
        WingsLogTopAppBar(
          title = stringResource(Res.string.feature_lab),
          onBackClick = { navController.popBackStack() },
        )
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .padding(innerPadding)
        .fillMaxSize(),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .constrainedContentWidth(ContentWidth.Reading)
          .fillMaxSize()
          .padding(Spacing.screenPadding),
      ) {
        Text(
          text = stringResource(Res.string.feature_lab_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.large))

        HorizontalDivider()

        FeatureToggleRow(
          icon = Icons.Default.Engineering,
          title = stringResource(Res.string.feature_lab_technician_title),
          subtitle = stringResource(Res.string.feature_lab_technician_subtitle),
          checked = flags.technicianEnabled,
          onCheckedChange = viewModel::setTechnicianEnabled,
        )

        HorizontalDivider()

        FeatureToggleRow(
          icon = Icons.Outlined.AttachFile,
          title = stringResource(Res.string.feature_lab_attachments_title),
          subtitle = stringResource(Res.string.feature_lab_attachments_subtitle),
          checked = flags.attachmentUploadEnabled,
          onCheckedChange = viewModel::setAttachmentUploadEnabled,
        )

        HorizontalDivider()

        FeatureToggleRow(
          icon = Icons.Default.Mail,
          title = stringResource(Res.string.feature_lab_export_email_title),
          subtitle = stringResource(Res.string.feature_lab_export_email_subtitle),
          checked = flags.exportEmailDeliveryEnabled,
          onCheckedChange = viewModel::setExportEmailDeliveryEnabled,
        )
        HorizontalDivider()

        dogfoodContent()
      }
    }
  }
}

@Composable
private fun FeatureToggleRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = Spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(end = Spacing.medium),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.padding(start = Spacing.medium),
    )
  }
}
