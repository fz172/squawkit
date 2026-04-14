package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing

/**
 * A standardized BottomSheet template for displaying record details.
 *
 * Features:
 * - Consistent horizontal padding ([Spacing.extraLarge]).
 * - Built-in vertical scrolling.
 * - Standardized header layout with a title slot and an optional action slot.
 * - Proper spacing at the bottom ([Spacing.huge]) to ensure content isn't obscured by the system bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  actionSlot: (@Composable () -> Unit)? = null,
  headerSlot: @Composable ColumnScope.() -> Unit,
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
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      // Header Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          modifier = Modifier
            .weight(1f)
            .padding(end = Spacing.small),
        ) {
          headerSlot()
        }
        actionSlot?.invoke()
      }

      // Body Content
      content()

      // Footer Spacer
      Spacer(Modifier.height(Spacing.huge))
    }
  }
}
