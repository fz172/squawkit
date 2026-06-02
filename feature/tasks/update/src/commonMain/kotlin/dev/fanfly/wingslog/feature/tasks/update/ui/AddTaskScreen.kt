package dev.fanfly.wingslog.feature.tasks.update.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.compose.UnsavedChangesDialog
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.tasks.update.compose.BASIC_TAB
import dev.fanfly.wingslog.feature.tasks.update.compose.DETAILS_TAB
import dev.fanfly.wingslog.feature.tasks.update.compose.SCHEDULE_TAB
import dev.fanfly.wingslog.feature.tasks.update.compose.ScheduleState
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskDetailTab
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskIdentityTab
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskScheduleTab
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskTabRow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.back
import wingslog.feature.tasks.sharedassets.generated.resources.add_task
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.tasks.sharedassets.generated.resources.Res as SharedTaskRes

@OptIn(
  ExperimentalMaterial3Api::class,
  ExperimentalComposeUiApi::class
)
@Composable
fun AddTaskScreen(
  availableInspections: List<MaintenanceTask>,
  onSave: (MaintenanceTask) -> Unit,
  onCancel: () -> Unit,
  isSaving: Boolean = false,
  attachmentSection: @Composable () -> Unit = {},
) {
  var title by remember { mutableStateOf("") }
  var component by remember { mutableStateOf(ComponentType.COMPONENT_AIRFRAME) }
  var type by remember { mutableStateOf(ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION) }
  var schedule by remember { mutableStateOf(ScheduleState()) }
  var refNumber by remember { mutableStateOf("") }
  var complianceAuthority by remember { mutableStateOf("") }
  var complianceNotes by remember { mutableStateOf("") }
  var showUnsavedChangesDialog by remember { mutableStateOf(false) }

  val hasChanges = title.isNotEmpty() ||
    component != ComponentType.COMPONENT_AIRFRAME ||
    type != ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION ||
    schedule != ScheduleState() ||
    refNumber.isNotEmpty() ||
    complianceAuthority.isNotEmpty() ||
    complianceNotes.isNotEmpty()

  val tryCancel = {
    if (hasChanges) showUnsavedChangesDialog = true else onCancel()
  }

  BackHandler(enabled = hasChanges) {
    showUnsavedChangesDialog = true
  }


  if (showUnsavedChangesDialog) {
    UnsavedChangesDialog(
      onConfirm = {
        showUnsavedChangesDialog = false
        onCancel()
      },
      onDismiss = { showUnsavedChangesDialog = false },
    )
  }

  val pagerState = rememberPagerState(pageCount = { 3 })
  val coroutineScope = rememberCoroutineScope()

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      Column {
        ConstrainedTopBar(ContentWidth.Form) {
          TopAppBar(
            title = {
              Text(
                stringResource(SharedTaskRes.string.add_task).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
              )
            },
            navigationIcon = {
              IconButton(onClick = { tryCancel() }) {
                Icon(
                  Icons.AutoMirrored.Default.ArrowBack,
                  contentDescription = stringResource(CoreRes.string.back)
                )
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(
              containerColor = Color.Transparent,
              scrolledContainerColor = Color.Transparent,
            ),
          )
        }
        Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.TopCenter
        ) {
          TaskTabRow(
            tabs = listOf(
              BASIC_TAB,
              DETAILS_TAB,
              SCHEDULE_TAB,
            ),
            selectedIndex = pagerState.currentPage,
            onSelect = {
              coroutineScope.launch {
                pagerState.animateScrollToPage(
                  it
                )
              }
            },
            modifier = Modifier.constrainedContentWidth(ContentWidth.Form),
          )
        }
      }
    }) { padding ->
    Column(
      modifier = Modifier.padding(padding)
        .fillMaxSize()
    ) {
      HorizontalPager(
        state = pagerState,
        modifier = Modifier.weight(1f),
        beyondViewportPageCount = 2,
        verticalAlignment = Alignment.Top
      ) { page ->
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.TopCenter,
        ) {
          Column(
            modifier = Modifier.fillMaxHeight()
              .constrainedContentWidth(ContentWidth.Form)
              .verticalScroll(rememberScrollState())
              .padding(Spacing.screenPadding)
          ) {
            when (page) {
              0 -> TaskIdentityTab(
                title = title,
                onTitleChange = { title = it },
                component = component,
                onComponentChange = { component = it },
                complianceType = type,
                onComplianceTypeChange = { type = it },
              )

              1 -> TaskDetailTab(
                refNumber = refNumber,
                onRefNumberChange = { refNumber = it },
                complianceAuthority = complianceAuthority,
                onComplianceAuthorityChange = { complianceAuthority = it },
                complianceNotes = complianceNotes,
                onComplianceNotesChange = { complianceNotes = it },
                attachmentSection = attachmentSection
              )

              2 -> TaskScheduleTab(
                state = schedule,
                onChange = { schedule = it },
                availableInspections = availableInspections,
              )
            }
          }
        }
      }

      BottomButtons(
        onPrimaryClick = {
          val card = MaintenanceTask(
            id = "",
            title = title,
            component = component,
            type = type,
            rules = schedule.toRules(),
            reference_number = refNumber.takeIf { it.isNotBlank() } ?: "",
            compliance_authority = complianceAuthority.takeIf { it.isNotBlank() }
              ?: "",
            compliance_details = complianceNotes.takeIf { it.isNotBlank() }
              ?: "",
            is_one_time = schedule.isOneTime,
            force_due_engine_hour = 0f,
            force_due_date = null,
            notes = "",
          )
          onSave(card)
        },
        onSecondaryClick = { tryCancel() },
        primaryEnabled = title.isNotBlank(),
        isPrimaryFunctionInProgress = isSaving
      )
    }
  }
}
