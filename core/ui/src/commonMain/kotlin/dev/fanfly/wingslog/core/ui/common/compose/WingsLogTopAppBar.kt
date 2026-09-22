package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.Res
import wingslog.core.sharedassets.generated.resources.back

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WingsLogTopAppBar(
  title: String,
  onBackClick: () -> Unit,
  scrollBehavior: TopAppBarScrollBehavior? = null,
  actions: @Composable RowScope.() -> Unit = {},
) {
  TopAppBar(
    title = { Text(text = title) },
    navigationIcon = {
      IconButton(onClick = onBackClick) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(Res.string.back),
        )
      }
    },
    actions = actions,
    scrollBehavior = scrollBehavior,
    colors = TopAppBarDefaults.topAppBarColors(
      // Transparent, so the bar is whatever its scaffold is. A screen hosted in the web dialog has
      // its ground tinted by the dialog's elevation, and a bar painted in the raw background
      // colour stood out against it; full-screen, the scaffold's ground is the background anyway.
      containerColor = Color.Transparent,
      scrolledContainerColor = Color.Transparent,
    ),
  )
}
