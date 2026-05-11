package dev.fanfly.wingslog.feature.squawk.update.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.compose.IconLabelTabRow
import dev.fanfly.wingslog.core.ui.common.compose.IconLabelTabSpec
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.squawk.update.compose.SquawkBasicTab
import dev.fanfly.wingslog.feature.squawk.update.compose.SquawkDetailsTab
import dev.fanfly.wingslog.feature.squawk.update.viewmodel.SquawkFormState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.add_squawk
import wingslog.feature.squawk.sharedassets.generated.resources.edit_squawk
import wingslog.feature.squawk.update.generated.resources.Res as UpdateRes
import wingslog.feature.squawk.update.generated.resources.tab_basic
import wingslog.feature.squawk.update.generated.resources.tab_details

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquawkFormScreen(
  state: SquawkFormState,
  onTitleChange: (String) -> Unit,
  onDescriptionChange: (String) -> Unit,
  onPriorityChange: (dev.fanfly.wingslog.aircraft.SquawkPriority) -> Unit,
  onSave: () -> Unit,
  onBack: () -> Unit,
  onViewLog: (() -> Unit)?,
  modifier: Modifier = Modifier,
  attachmentSection: @Composable () -> Unit = {},
) {
  val isEdit = state.squawkId != null
  val screenTitle = if (isEdit) stringResource(Res.string.edit_squawk)
  else stringResource(Res.string.add_squawk)

  val tabs = listOf(
    IconLabelTabSpec(Icons.Default.Edit, stringResource(UpdateRes.string.tab_basic)),
    IconLabelTabSpec(Icons.Default.Info, stringResource(UpdateRes.string.tab_details)),
  )
  val pagerState = rememberPagerState(pageCount = { tabs.size })
  val coroutineScope = rememberCoroutineScope()

  Scaffold(
    modifier = modifier,
    topBar = {
      Column {
        TopAppBar(
          title = {
            Text(
              text = screenTitle.uppercase(),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
          },
          navigationIcon = {
            IconButton(onClick = onBack) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
          },
        )
        IconLabelTabRow(
          tabs = tabs,
          selectedIndex = pagerState.currentPage,
          onSelect = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
        )
      }
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .padding(padding)
        .fillMaxSize(),
    ) {
      HorizontalPager(
        state = pagerState,
        modifier = Modifier.weight(1f),
        beyondViewportPageCount = 1,
        verticalAlignment = Alignment.Top,
      ) { page ->
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenPadding),
        ) {
          when (page) {
            0 -> SquawkBasicTab(
              title = state.title,
              onTitleChange = onTitleChange,
              priority = state.priority,
              onPriorityChange = onPriorityChange,
              reportedDateFormatted = state.reportedDateFormatted,
              readOnly = state.isAddressedReadOnly,
              titleError = state.titleError,
            )
            1 -> SquawkDetailsTab(
              description = state.description,
              onDescriptionChange = onDescriptionChange,
              isEdit = isEdit,
              addressedByLogId = state.addressedByLogId,
              onViewLog = onViewLog,
              readOnly = state.isAddressedReadOnly,
              attachmentSection = attachmentSection,
            )
          }
        }
      }

      BottomButtons(
        onPrimaryClick = onSave,
        onSecondaryClick = onBack,
        primaryEnabled = !state.isSaving && !state.isAddressedReadOnly,
        isPrimaryFunctionInProgress = state.isSaving,
      )
    }
  }
}
