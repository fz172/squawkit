package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res
import wingslog.core.ui.generated.resources.back
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WingsLogTopAppBar(title: String, onBackClick: () -> Unit) {
  TopAppBar(
    title = {
      Text(
        text = title,
      )
    },
    navigationIcon = {
      IconButton(onClick = onBackClick) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(Res.string.back),
        )
      }
    },
  )
}