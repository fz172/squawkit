package dev.fanfly.wingslog.feature.dashboard.host

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
internal fun SectionAddFab(label: String, onClick: () -> Unit) {
  ExtendedFloatingActionButton(
    onClick = onClick,
    icon = { Icon(Icons.Default.Add, contentDescription = null) },
    text = { Text(label) },
    // Nudge the FAB inward so it clears the wide-screen Logs table's right border. Applied to the
    // shared FAB so the position stays identical across the Squawks/Tasks/Logs sections.
    modifier = Modifier.padding(end = Spacing.medium),
  )
}
