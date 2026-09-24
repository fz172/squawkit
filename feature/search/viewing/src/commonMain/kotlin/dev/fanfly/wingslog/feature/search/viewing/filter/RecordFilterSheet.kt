package dev.fanfly.wingslog.feature.search.viewing.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.popup.ModalBottomSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordFilterSheet(
  onDismiss: () -> Unit,
  content: @Composable ColumnScope.() -> Unit
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth()
        .padding(horizontal = Spacing.xLarge),
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      content()
      Spacer(Modifier.height(Spacing.large))
    }
  }
}
