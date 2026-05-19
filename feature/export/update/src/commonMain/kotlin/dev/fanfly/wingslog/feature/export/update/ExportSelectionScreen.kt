package dev.fanfly.wingslog.feature.export.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_logs_coming_soon_body
import wingslog.feature.export.sharedassets.generated.resources.export_logs_coming_soon_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSelectionScreen(
  onNavigateBack: () -> Unit,
) {
  Scaffold(
    topBar = {
      WingsLogTopAppBar(
        title = stringResource(Res.string.export_logs_coming_soon_title),
        onBackClick = onNavigateBack,
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .padding(innerPadding)
        .fillMaxSize()
        .padding(Spacing.screenPadding),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(
        space = Spacing.large,
        alignment = Alignment.CenterVertically,
      ),
    ) {
      Icon(
        imageVector = Icons.Default.FileDownload,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
      )
      Text(
        text = stringResource(Res.string.export_logs_coming_soon_title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Text(
        text = stringResource(Res.string.export_logs_coming_soon_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
