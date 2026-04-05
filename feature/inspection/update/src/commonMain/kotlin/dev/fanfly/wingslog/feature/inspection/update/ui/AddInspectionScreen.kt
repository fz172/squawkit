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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.EngineHourRule
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.InspectionComponentType
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.aircraft.LinkedRule
import dev.fanfly.wingslog.aircraft.TimeRule
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.inspection.update.compose.DocumentationFields
import dev.fanfly.wingslog.feature.inspection.update.compose.IntervalFields
import dev.fanfly.wingslog.feature.inspection.update.compose.LinkedInspectionFields
import dev.fanfly.wingslog.feature.inspection.update.compose.OneTimeComplianceFields
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.back
import wingslog.core.ui.generated.resources.component_airframe
import wingslog.core.ui.generated.resources.component_avionics
import wingslog.core.ui.generated.resources.component_engine
import wingslog.core.ui.generated.resources.component_propeller
import wingslog.core.ui.generated.resources.component_type
import wingslog.feature.inspection.sharedassets.generated.resources.Res as SharedInspectionRes
import wingslog.feature.inspection.sharedassets.generated.resources.add_inspection
import wingslog.feature.inspection.sharedassets.generated.resources.compliance_type_ad_short
import wingslog.feature.inspection.sharedassets.generated.resources.compliance_type_sb_short
import wingslog.feature.inspection.update.generated.resources.Res as InspectionRes
import wingslog.feature.inspection.update.generated.resources.basics
import wingslog.feature.inspection.update.generated.resources.compliance_type
import wingslog.feature.inspection.update.generated.resources.compliance_type_routine_short
import wingslog.feature.inspection.update.generated.resources.details
import wingslog.feature.inspection.update.generated.resources.inspection_title
import wingslog.feature.inspection.update.generated.resources.schedule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInspectionScreen(
  availableInspections: List<InspectionCard>,
  onSave: (InspectionCard) -> Unit,
  onCancel: () -> Unit,
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
          IconButton(onClick = onCancel) {
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
            text = { Text(stringResource(InspectionRes.string.basics)) }
          )
          Tab(
            selected = pagerState.currentPage == 1,
            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
            text = { Text(stringResource(InspectionRes.string.schedule)) }
          )
          Tab(
            selected = pagerState.currentPage == 2,
            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
            text = { Text(stringResource(InspectionRes.string.details)) }
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
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenPadding)
        ) {
          when (page) {
            0 -> {
              // --- Page 0: Identity ---
              OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(InspectionRes.string.inspection_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
              )

              Spacer(modifier = Modifier.height(Spacing.medium))

              // Component Type
              Text(
                stringResource(CoreRes.string.component_type),
                style = MaterialTheme.typography.labelLarge
              )
              Spacer(modifier = Modifier.height(Spacing.small))
              val components =
                InspectionComponentType.entries.filter { it != InspectionComponentType.INSPECTION_COMPONENT_UNKNOWN }
              SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                components.forEachIndexed { index, entry ->
                  SegmentedButton(
                    selected = component == entry,
                    onClick = { component = entry },
                    shape = SegmentedButtonDefaults.itemShape(
                      index = index,
                      count = components.size
                    ),
                    icon = {},
                    label = {
                      val componentLabel = when (entry) {
                        InspectionComponentType.INSPECTION_COMPONENT_AIRFRAME -> stringResource(
                          CoreRes.string.component_airframe
                        )

                        InspectionComponentType.INSPECTION_COMPONENT_ENGINE -> stringResource(
                          CoreRes.string.component_engine
                        )

                        InspectionComponentType.INSPECTION_COMPONENT_PROPELLER -> stringResource(
                          CoreRes.string.component_propeller
                        )

                        InspectionComponentType.INSPECTION_COMPONENT_AVIONICS -> stringResource(
                          CoreRes.string.component_avionics
                        )

                        else -> entry.name.removePrefix("INSPECTION_COMPONENT_")
                      }
                      Text(
                        text = componentLabel,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                      )
                    })
                }
              }

              Spacer(modifier = Modifier.height(Spacing.medium))

              // Compliance Type
              Text(
                stringResource(InspectionRes.string.compliance_type),
                style = MaterialTheme.typography.labelLarge
              )
              Spacer(modifier = Modifier.height(Spacing.small))
              val types = ComplianceType.entries
              SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                types.forEachIndexed { index, entry ->
                  SegmentedButton(
                    selected = type == entry,
                    onClick = { type = entry },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                    icon = {},
                    label = {
                      val labelText = when (entry) {
                        ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE -> stringResource(
                          SharedInspectionRes.string.compliance_type_ad_short
                        )

                        ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN -> stringResource(
                          SharedInspectionRes.string.compliance_type_sb_short
                        )

                        ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION -> stringResource(
                          InspectionRes.string.compliance_type_routine_short
                        )
                      }
                      Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                      )
                    })
                }
              }
            }

            1 -> {
              // --- Page 1: Scheduling ---
              OneTimeComplianceFields(
                isOneTime = isOneTime,
                onOneTimeChange = { isOneTime = it }
              )

              Spacer(modifier = Modifier.height(Spacing.large))

              if (linkedToId == null) {
                IntervalFields(
                  intervalMonths = intervalMonths,
                  onMonthsChange = { intervalMonths = it },
                  intervalHours = intervalHours,
                  onHoursChange = { intervalHours = it }
                )
              }

              Spacer(modifier = Modifier.height(Spacing.large))

              LinkedInspectionFields(
                linkedToId = linkedToId,
                onLinkChange = { linkedToId = it },
                availableInspections = availableInspections
              )
            }

            2 -> {
              // --- Page 2: Documentation & Notes ---
              DocumentationFields(
                refNumber = refNumber,
                onRefNumberChange = { refNumber = it },
                complianceAuthority = complianceAuthority,
                onComplianceAuthorityChange = { complianceAuthority = it },
                complianceNotes = complianceNotes,
                onComplianceNotesChange = { complianceNotes = it }
              )

              Spacer(modifier = Modifier.height(Spacing.large))

              attachmentSection()
            }
          }
        }
      }

      BottomButtons(
        onPrimaryClick = {
          val ruleList = mutableListOf<InspectionRule>()
          if (linkedToId != null) {
            ruleList.add(InspectionRule(linked_rule = LinkedRule(parent_inspection_id = linkedToId!!)))
          } else {
            intervalMonths.toIntOrNull()?.let {
              ruleList.add(InspectionRule(time_rule = TimeRule(interval_months = it)))
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
            compliance_authority = complianceAuthority.takeIf { it.isNotBlank() } ?: "",
            compliance_details = complianceNotes.takeIf { it.isNotBlank() } ?: "",
            is_one_time = isOneTime,
            force_due_engine_hour = 0f,
            force_due_date = null,
            notes = ""
          )
          onSave(card)
        },
        onSecondaryClick = onCancel,
        primaryEnabled = title.isNotBlank()
      )
    }
  }
}

