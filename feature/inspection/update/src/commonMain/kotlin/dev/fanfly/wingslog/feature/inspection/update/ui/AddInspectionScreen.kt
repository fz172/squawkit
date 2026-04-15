package dev.fanfly.wingslog.feature.inspection.update.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.EngineHourRule
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.InspectionComponentType
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.aircraft.LinkedRule
import dev.fanfly.wingslog.aircraft.TimeRule
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.compose.UnsavedChangesDialog
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.inspection.update.compose.InspectionDetailTab
import dev.fanfly.wingslog.feature.inspection.update.compose.InspectionIdentityTab
import dev.fanfly.wingslog.feature.inspection.update.compose.InspectionScheduleTab
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.back
import wingslog.feature.inspection.sharedassets.generated.resources.add_inspection
import wingslog.feature.inspection.update.generated.resources.basics
import wingslog.feature.inspection.update.generated.resources.details
import wingslog.feature.inspection.update.generated.resources.schedule
import kotlin.time.Clock
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.feature.inspection.sharedassets.generated.resources.Res as SharedInspectionRes
import wingslog.feature.inspection.update.generated.resources.Res as InspectionRes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun AddInspectionScreen(
  availableInspections: List<InspectionCard>,
  onSave: (InspectionCard) -> Unit,
  onCancel: () -> Unit,
  isSaving: Boolean = false,
  attachmentSection: @Composable () -> Unit = {},
) {
  var title by remember { mutableStateOf("") }
  var component by remember { mutableStateOf(InspectionComponentType.INSPECTION_COMPONENT_AIRFRAME) }
  var type by remember { mutableStateOf(ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION) }
  var intervalMonths by remember { mutableStateOf("") }
  var intervalHours by remember { mutableStateOf("") }
  var isOneTime by remember { mutableStateOf(false) }
  var refNumber by remember { mutableStateOf("") }
  var complianceAuthority by remember { mutableStateOf("") }
  var complianceNotes by remember { mutableStateOf("") }
  var linkedToId by remember { mutableStateOf<String?>(null) }
  var showUnsavedChangesDialog by remember { mutableStateOf(false) }

  val hasChanges = title.isNotEmpty() ||
    component != InspectionComponentType.INSPECTION_COMPONENT_AIRFRAME ||
    type != ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION ||
    intervalMonths.isNotEmpty() ||
    intervalHours.isNotEmpty() ||
    isOneTime ||
    refNumber.isNotEmpty() ||
    complianceAuthority.isNotEmpty() ||
    complianceNotes.isNotEmpty() ||
    linkedToId != null

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
    topBar = {
      Column {
        TopAppBar(title = {
          Text(
            stringResource(SharedInspectionRes.string.add_inspection).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
        }, navigationIcon = {
          IconButton(onClick = { tryCancel() }) {
            Icon(
              Icons.AutoMirrored.Default.ArrowBack,
              contentDescription = stringResource(CoreRes.string.back)
            )
          }
        })
        PrimaryTabRow(
          selectedTabIndex = pagerState.currentPage,
          containerColor = MaterialTheme.colorScheme.background,
        ) {
          Tab(
            selected = pagerState.currentPage == 0,
            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
            text = { Text(stringResource(InspectionRes.string.basics)) })
          Tab(
            selected = pagerState.currentPage == 1,
            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
            text = { Text(stringResource(InspectionRes.string.details)) })
          Tab(
            selected = pagerState.currentPage == 2,
            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
            text = { Text(stringResource(InspectionRes.string.schedule)) },
          )
        }
      }
    }) { padding ->
    Column(
      modifier = Modifier.padding(padding).fillMaxSize()
    ) {
      HorizontalPager(
        state = pagerState,
        modifier = Modifier.weight(1f),
        beyondViewportPageCount = 2,
        verticalAlignment = Alignment.Top
      ) { page ->
        Column(
          modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(Spacing.screenPadding)
        ) {
          when (page) {
            0 -> {
              // --- Page 0: Identity ---
              InspectionIdentityTab(
                title = title,
                onTitleChange = { title = it },
                component = component,
                onComponentChange = { component = it },
                complianceType = type,
                onComplianceTypeChange = { type = it },
              )
            }

            1 -> {
              // --- Page 2: Documentation & Notes ---
              InspectionDetailTab(
                refNumber = refNumber,
                onRefNumberChange = { refNumber = it },
                complianceAuthority = complianceAuthority,
                onComplianceAuthorityChange = { complianceAuthority = it },
                complianceNotes = complianceNotes,
                onComplianceNotesChange = { complianceNotes = it },
                attachmentSection = attachmentSection
              )
            }

            2 -> {
              // --- Page 1: Scheduling ---
              InspectionScheduleTab(
                isOneTime = isOneTime,
                onOneTimeChange = { isOneTime = it },
                intervalMonths = intervalMonths,
                onMonthsChange = { intervalMonths = it },
                intervalHours = intervalHours,
                onHoursChange = { intervalHours = it },
                linkedToId = linkedToId,
                onLinkChange = { linkedToId = it },
                availableInspections = availableInspections
              )
            }
          }
        }
      }

      BottomButtons(
        onPrimaryClick = {
          val ruleList = mutableListOf<InspectionRule>()
          val now = Clock.System.now()
          if (linkedToId != null) {
            ruleList.add(InspectionRule(linked_rule = LinkedRule(parent_inspection_id = linkedToId!!)))
          } else {
            intervalMonths.toIntOrNull()?.let {
              ruleList.add(
                InspectionRule(
                  time_rule = TimeRule(
                    interval_months = it,
                    creation_date = toWireInstant(now.epochSeconds, now.nanosecondsOfSecond),
                  )
                )
              )
            }
            intervalHours.toDoubleOrNull()?.let {
              ruleList.add(InspectionRule(engine_hour_rule = EngineHourRule(interval_hours = it.toFloat())))
            }
          }

          val card = InspectionCard(
            id = "",
            title = title,
            component = component,
            type = type,
            rules = ruleList,
            reference_number = refNumber.takeIf { it.isNotBlank() } ?: "",
            compliance_authority = complianceAuthority.takeIf { it.isNotBlank() }
              ?: "",
            compliance_details = complianceNotes.takeIf { it.isNotBlank() }
              ?: "",
            is_one_time = isOneTime,
            force_due_engine_hour = 0f,
            force_due_date = null,
            notes = "")
          onSave(card)
        },
        onSecondaryClick = { tryCancel() },
        primaryEnabled = title.isNotBlank(),
        isPrimaryFunctionInProgress = isSaving
      )
    }
  }
}
