package dev.fanfly.wingslog.feature.export.update

import androidx.compose.runtime.Composable
import androidx.navigation.NavController

@Composable
fun ExportSelectionRoute(navController: NavController) {
  ExportSelectionScreen(
    onNavigateBack = { navController.popBackStack() },
  )
}
