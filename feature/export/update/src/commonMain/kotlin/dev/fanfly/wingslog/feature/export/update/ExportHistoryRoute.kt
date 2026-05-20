package dev.fanfly.wingslog.feature.export.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.feature.export.update.viewmodel.ExportHistoryViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ExportHistoryRoute(
  navController: NavController,
  viewModel: ExportHistoryViewModel = koinViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val exportFileSharer = rememberExportFileSharer()

  ExportHistoryScreen(
    state = state,
    onNavigateBack = { navController.popBackStack() },
    onShareExport = exportFileSharer::share,
    onDelete = { record -> viewModel.onDelete(record.filePath) },
  )
}
