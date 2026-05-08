package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing


/**
 * A standardized BottomSheet template for selection/picker flows.
 *
 * Differences from [DetailSheet]:
 * - Header is simpler, usually just a title.
 * - Scrolling is handled by the caller (often a LazyColumn or a custom Column with sticky footer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerSheet(
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  headerSlot: @Composable () -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.extraLarge)
        .padding(bottom = Spacing.huge),
      verticalArrangement = Arrangement.spacedBy(Spacing.none),
    ) {
      // Header Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        headerSlot()
      }
      content()
    }
  }
}
