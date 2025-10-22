package dev.fanfly.wingslog.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopAppBar(onBackClick: () -> Unit) {
  CenterAlignedTopAppBar(
    title = {
      Text(
        stringResource(R.string.settings),
        fontWeight = FontWeight.Bold,
      )
    },
    navigationIcon = {
      IconButton(onClick = onBackClick) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(R.string.back),
        )
      }
    },
  )
}