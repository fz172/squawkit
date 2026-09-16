package dev.fanfly.wingslog.feature.datalog.update.viewer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.datetime.formatDuration
import dev.fanfly.wingslog.core.datetime.toClockText
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.core.nav.Screen.Companion.CROSS_SCREEN_SUCCESS_MESSAGE
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.dataLogNoun
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.compose.TextSelectionLayer
import dev.fanfly.wingslog.core.ui.adaptive.compose.layoutTierFor
import dev.fanfly.wingslog.core.ui.common.compose.AlertDialog
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.common.compose.StatusChip
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.feature.ads.datamanager.AdsManager
import dev.fanfly.wingslog.feature.ads.model.AdSurface
import dev.fanfly.wingslog.feature.ads.model.AdUnitSize
import dev.fanfly.wingslog.feature.ads.viewing.AdSlot
import dev.fanfly.wingslog.feature.datalog.model.GestureIntent
import dev.fanfly.wingslog.feature.datalog.model.MapTileProvider
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey
import dev.fanfly.wingslog.feature.datalog.model.chart.Decimation
import dev.fanfly.wingslog.feature.datalog.model.chart.PaneKind
import dev.fanfly.wingslog.feature.datalog.model.chart.TimeTicks
import dev.fanfly.wingslog.feature.datalog.model.chart.isPlottable
import dev.fanfly.wingslog.feature.datalog.model.chart.paneKind
import dev.fanfly.wingslog.feature.datalog.viewing.chart.ChartPane
import dev.fanfly.wingslog.feature.datalog.viewing.chart.ChipInfo
import dev.fanfly.wingslog.feature.datalog.viewing.chart.DropTarget
import dev.fanfly.wingslog.feature.datalog.viewing.chart.InfoFact
import dev.fanfly.wingslog.feature.datalog.viewing.chart.MapPane
import dev.fanfly.wingslog.feature.datalog.viewing.chart.NewPaneTarget
import dev.fanfly.wingslog.feature.datalog.viewing.chart.PaneHeaderChips
import dev.fanfly.wingslog.feature.datalog.viewing.chart.PaneSeries
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SeriesDrag
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SeriesDragState
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SeriesPalette
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SeriesSidebar
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SidebarWidth
import dev.fanfly.wingslog.feature.datalog.viewing.chart.TimeAxis
import dev.fanfly.wingslog.feature.datalog.viewing.chart.dropTarget
import dev.fanfly.wingslog.feature.datalog.viewing.chart.formatSeriesValue
import dev.fanfly.wingslog.feature.datalog.viewing.list.DataLogRow
import dev.fanfly.wingslog.feature.datalog.viewing.list.toDataLogRow
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.delete
import wingslog.core.sharedassets.generated.resources.delete_failed
import wingslog.core.sharedassets.generated.resources.retry
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_delete_body
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_delete_title
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_deleted
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_airframe_hours
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_date
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_duration
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_engine_hours
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_file
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_identity
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_offset
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_product
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_rate
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_rate_value
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_samples
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_series
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_series_value
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_software
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_start
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_system_id
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_unit
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_sidebar_open
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_tail_mismatch
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_viewer_clock_axis
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_viewer_downloading
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_viewer_load_failed
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_viewer_missing
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_viewer_reading
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_viewer_reset
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_viewer_utc_offset
import kotlin.math.roundToInt
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * The full-screen viewer route (design §10.4). Until the panes land (T28), the body lists the
 * catalogue so the load path can be exercised end to end on a developer build.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataLogViewerScreen(
  thingId: ThingId,
  dataLogId: DataLogId,
  navController: NavController,
  viewModel: DataLogViewerViewModel = koinViewModel(
    key = "viewer-${dataLogId.value}",
    parameters = { parametersOf(thingId.value, dataLogId.value) },
  ),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val lexicon = LocalThingLexicon.current
  val snackbarHostState = remember { SnackbarHostState() }
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  val deletedMessage = stringResource(
    Res.string.data_log_deleted,
    LexiconFormatter.sentenceCase(lexicon.dataLogNoun)
  )
  val deleteFailedMessage = stringResource(CoreRes.string.delete_failed)

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        DataLogViewerEvent.Deleted -> {
          navController.previousBackStackEntry?.savedStateHandle?.set(
            CROSS_SCREEN_SUCCESS_MESSAGE,
            deletedMessage
          )
          navController.popBackStack()
        }

        DataLogViewerEvent.DeleteFailed -> snackbarHostState.showSnackbar(
          deleteFailedMessage
        )
      }
    }
  }

  val ready = state as? DataLogViewerUiState.Ready
  val row = ready?.record?.toDataLogRow()
  // The viewer is a top-level route outside the adaptive shell, so it derives its own tier;
  // otherwise every window would take the phone layout and hide the sidebar in a drawer.
  BoxWithConstraints {
    CompositionLocalProvider(LocalLayoutTier provides layoutTierFor(maxWidth)) {
      Scaffold(
        topBar = {
          WingsLogTopAppBar(
            title = row?.startLocal?.date?.toDisplayFormat(numberOnly = false)
              ?: LexiconFormatter.titleCase(lexicon.dataLogNoun),
            onBackClick = { navController.popBackStack() },
            actions = {
              if (ready != null) {
                IconButton(onClick = viewModel::toggleClockAxis) {
                  Icon(
                    Icons.Filled.Schedule,
                    contentDescription = stringResource(Res.string.data_log_viewer_clock_axis),
                    tint = if (ready.clockAxis) MaterialTheme.colorScheme.primary
                    else LocalContentColor.current,
                  )
                }
              }
              if (ready != null && LocalLayoutTier.current.isCompact) {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                  Icon(
                    Icons.Filled.Tune,
                    contentDescription = stringResource(Res.string.data_log_sidebar_open)
                  )
                }
              }
              if (ready != null) {
                IconButton(onClick = viewModel::requestDelete) {
                  Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(CoreRes.string.delete)
                  )
                }
              }
            },
          )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
      ) { innerPadding ->
        val content = Modifier.padding(innerPadding)
          .fillMaxSize()
        when (val s = state) {
          is DataLogViewerUiState.Loading -> Box(
            content,
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
              CircularProgressIndicator()
              val phase = when {
                s.reading -> Res.string.data_log_viewer_reading
                s.download != null -> Res.string.data_log_viewer_downloading
                else -> null
              }
              if (phase != null) {
                Text(
                  stringResource(phase),
                  style = MaterialTheme.typography.bodyMedium
                )
              }
            }
          }

          is DataLogViewerUiState.Failed -> EmptyState(
            title = if (s.reason == LoadFailure.NOT_FOUND) stringResource(
              Res.string.data_log_viewer_missing,
              lexicon.dataLogNoun.singular
            )
            else stringResource(Res.string.data_log_viewer_load_failed),
            description = "",
            icon = Icons.Filled.ShowChart,
            actionText = if (s.reason == LoadFailure.NOT_FOUND) null else stringResource(
              CoreRes.string.retry
            ),
            onActionClick = if (s.reason == LoadFailure.NOT_FOUND) null else viewModel::retry,
            modifier = content,
          )

          is DataLogViewerUiState.Ready -> {
            val r = checkNotNull(row)
            val byColumn = remember(s.record, s.data) {
              s.record.series.filter { it.isPlottable }
                .mapNotNull { info ->
                  s.data.numeric[info.column]?.let { column ->
                    info.column to PaneSeries(
                      SeriesKey(info.column),
                      info.unit,
                      info.canonical_id,
                      column.filled
                    )
                  }
                }
                .toMap()
            }
            val infoByColumn =
              remember(s.record) { s.record.series.associateBy { it.column } }
            val dragState = remember(s.record) { SeriesDragState() }
            val tileProvider: MapTileProvider = koinInject()
            val dark = isSystemInDarkTheme()
            val cursorIndex = remember(s.cursorT, s.data) {
              s.cursorT?.let { Decimation.indexAt(s.data.timeSeconds, it) }
                ?: -1
            }
            var boxOrigin by remember { mutableStateOf(Offset.Zero) }
            val onDrop: (SeriesDrag, DropTarget?) -> Unit = { drag, target ->
              val from = drag.from
              when (target) {
                is DropTarget.OnPane ->
                  if (from == null) viewModel.addSeries(
                    target.pane,
                    drag.key
                  ) else viewModel.moveSeries(drag.key, from, target.pane)

                DropTarget.NewPane -> {
                  if (from != null) viewModel.removeSeries(from, drag.key)
                  viewModel.spawnPane(drag.key)
                }

                null -> Unit
              }
            }
            val compact = LocalLayoutTier.current.isCompact
            val adsManager: AdsManager = koinInject()
            val showAds by adsManager.shouldShowsAds()
              .collectAsState(initial = false)
            // PRD R44a: one fixed unit, never in a pane and never over a chart. Android and iOS
            // only — shouldShowsAds() is already false where AppCapability has no ad product.
            val adSlot: @Composable () -> Unit = {
              if (showAds) {
                AdSlot(
                  surface = AdSurface.DATA_LOGS,
                  slotIndex = 0,
                  size = AdUnitSize.BANNER,
                  // The sidebar footer is a fixed column; a two-up band would run past its edge.
                  maxUnits = 1,
                )
              }
            }
            val facts = viewerFacts(s.record, r)
            val sidebar: @Composable () -> Unit = {
              SeriesSidebar(
                catalogue = s.record.series,
                // The map pane's series reads as charted wherever the target happens to be: it is
                // the only pane a position series can be in, so the target says nothing about it.
                inTargetPane = s.layout.panes.firstOrNull { it.id == s.layout.targetPane }?.series?.toSet()
                  .orEmpty() +
                  s.layout.panes.filter { pane ->
                    pane.series.firstOrNull()
                      ?.let { infoByColumn[it.column]?.paneKind() } == PaneKind.MAP
                  }
                    .flatMap { it.series },
                tab = s.sidebarTab,
                onTab = viewModel::setSidebarTab,
                query = s.seriesQuery,
                onQuery = viewModel::setSeriesQuery,
                onAdd = { key ->
                  s.layout.targetPane?.let {
                    viewModel.toggleSeries(
                      it,
                      key
                    )
                  } ?: viewModel.spawnPane(key)
                },
                dragState = dragState,
                onDrop = onDrop,
                facts = facts,
                identityMismatch = r.identityMismatch,
              )
            }
            val panes: @Composable (Modifier) -> Unit = { paneModifier ->
              Box(modifier = paneModifier.onGloballyPositioned {
                boxOrigin = it.positionInWindow()
              }) {
                LazyColumn(
                  modifier = Modifier.fillMaxSize(),
                  contentPadding = PaddingValues(
                    horizontal = Spacing.screenPadding,
                    vertical = Spacing.large
                  ),
                  verticalArrangement = Arrangement.spacedBy(Spacing.small),
                ) {
                  item {
                    if (r.identity.isNotBlank()) Text(
                      r.identity,
                      style = WingslogTypography.dataMedium
                    )
                    if (r.identityMismatch) StatusChip(
                      label = stringResource(Res.string.data_log_tail_mismatch),
                      tier = StatusTier.CAUTION,
                      modifier = Modifier.padding(top = Spacing.medium)
                    )
                    Text(
                      text = listOf(
                        stringResource(
                          Res.string.data_log_viewer_utc_offset,
                          r.startLocal.time.toClockText(),
                          offsetText(s.record.utc_offset_minutes)
                        ),
                        formatDuration(r.durationSeconds),
                        r.product,
                      ).filter { it.isNotBlank() }
                        .joinToString(" · "),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(vertical = Spacing.medium),
                    )
                  }
                  if (s.view != null) {
                    item {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                      ) {
                        Text(
                          text = "${TimeTicks.label(s.view.startSeconds)} – ${
                            TimeTicks.label(
                              s.view.endSeconds
                            )
                          }",
                          style = WingslogTypography.dataSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { viewModel.onGesture(GestureIntent.Reset) }) {
                          Text(stringResource(Res.string.data_log_viewer_reset))
                        }
                      }
                    }
                  }
                  items(s.layout.panes, key = { it.id.value }) { pane ->
                    val chips = pane.series.mapNotNull { key ->
                      val info =
                        infoByColumn[key.column] ?: return@mapNotNull null
                      val column = s.data.numeric[key.column]
                      val value =
                        if (cursorIndex >= 0 && column != null) column.raw[cursorIndex].takeUnless { it.isNaN() } else null
                      ChipInfo(
                        key = key,
                        shortName = info.short_name.ifBlank { info.name },
                        unit = info.unit,
                        color = SeriesPalette.colorFor(
                          key,
                          info.canonical_id,
                          dark
                        ),
                        value = value?.let(::formatSeriesValue),
                      )
                    }
                    Column(
                      modifier = Modifier.dropTarget(
                        DropTarget.OnPane(pane.id),
                        dragState
                      ),
                      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
                    ) {
                      PaneHeaderChips(
                        pane = pane.id,
                        chips = chips,
                        dragState = dragState,
                        onRemoveSeries = { key ->
                          viewModel.removeSeries(
                            pane.id,
                            key
                          )
                        },
                        onRemovePane = { viewModel.removePane(pane.id) },
                        onDrop = onDrop,
                      )
                      val paneKind = pane.series.firstOrNull()
                        ?.let { infoByColumn[it.column]?.paneKind() }
                        ?: PaneKind.CHART
                      val positions = s.data.position
                      if (paneKind == PaneKind.MAP && positions != null) {
                        MapPane(
                          position = positions,
                          timeSeconds = s.data.timeSeconds,
                          durationSeconds = s.record.duration_seconds,
                          view = s.view,
                          cursorIndex = cursorIndex,
                          isTarget = pane.id == s.layout.targetPane,
                          provider = tileProvider,
                        )
                      } else {
                        ChartPane(
                          series = pane.series.mapNotNull { key -> byColumn[key.column] },
                          timeSeconds = s.data.timeSeconds,
                          durationSeconds = s.record.duration_seconds,
                          view = s.view,
                          cursorT = s.cursorT,
                          isTarget = pane.id == s.layout.targetPane,
                          onGesture = { intent ->
                            // The last pane touched is where the sidebar adds series (PRD R25).
                            viewModel.setTargetPane(pane.id)
                            viewModel.onGesture(intent)
                          },
                        )
                      }
                    }
                  }
                  item {
                    TimeAxis(
                      view = s.view,
                      durationSeconds = s.record.duration_seconds,
                      cursorT = s.cursorT,
                      clockAxis = s.clockAxis,
                      originSecondsOfDay = r.startLocal.time.toSecondOfDay(),
                      onScrub = { fraction ->
                        viewModel.onGesture(
                          GestureIntent.Cursor(
                            fraction
                          )
                        )
                      },
                    )
                  }
                  item {
                    NewPaneTarget(
                      dragState = dragState,
                      onTap = { viewModel.spawnPane() })
                  }
                  // Phones carry the slot here, under the panes. Wider layouts have a sidebar footer.
                  if (compact) item { adSlot() }
                }
                // The chip in flight, following the pointer above everything else.
                dragState.drag?.let { drag ->
                  val local = drag.position - boxOrigin
                  Surface(
                    shape = RoundedCornerShape(Spacing.smallCornerRadius),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = Spacing.extraSmall,
                    shadowElevation = Spacing.extraSmall,
                    modifier = Modifier.offset {
                      IntOffset(
                        local.x.roundToInt(),
                        local.y.roundToInt()
                      )
                    },
                  ) {
                    Text(
                      drag.label,
                      style = MaterialTheme.typography.labelMedium,
                      modifier = Modifier.padding(
                        horizontal = Spacing.medium,
                        vertical = Spacing.small
                      ),
                    )
                  }
                }
              }
            }
            if (compact) {
              // A right-hand drawer behind the tune control (PRD R27, design §11.7): the drawer is laid
              // out right-to-left and its content flipped back, the standard trick for an end drawer.
              CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModalNavigationDrawer(
                  drawerState = drawerState,
                  drawerContent = {
                    ModalDrawerSheet(modifier = Modifier.width(SidebarWidth)) {
                      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        TextSelectionLayer { sidebar() }
                      }
                    }
                  },
                ) {
                  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    panes(content)
                  }
                }
              }
            } else {
              Row(modifier = content) {
                panes(
                  Modifier.weight(1f)
                    .fillMaxSize()
                )
                VerticalDivider()
                Column(
                  Modifier.width(SidebarWidth)
                    .fillMaxSize()
                ) {
                  Box(Modifier.weight(1f)) { sidebar() }
                  adSlot()
                }
              }
            }
            if (s.deleting) {
              AlertDialog(
                onDismissRequest = viewModel::cancelDelete,
                title = {
                  Text(
                    stringResource(
                      Res.string.data_log_delete_title,
                      LexiconFormatter.titleCase(lexicon.dataLogNoun)
                    )
                  )
                },
                text = { Text(stringResource(Res.string.data_log_delete_body)) },
                confirmButton = {
                  TextButton(
                    onClick = viewModel::confirmDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                  ) { Text(stringResource(CoreRes.string.delete)) }
                },
                dismissButton = {
                  TextButton(onClick = viewModel::cancelDelete) {
                    Text(
                      stringResource(CoreRes.string.cancel)
                    )
                  }
                },
              )
            }
          }
        }
      }
    }
  }
}

/** `UTC-07:00` from the record's offset minutes. */
private fun offsetText(minutes: Int): String {
  val sign = if (minutes < 0) "-" else "+"
  val abs = kotlin.math.abs(minutes)
  return "UTC$sign${
    (abs / 60).toString()
      .padStart(2, '0')
  }:${
    (abs % 60).toString()
      .padStart(2, '0')
  }"
}

/** The Info tab's lines (PRD R26), in the order the mock lists them. */
@Composable
private fun viewerFacts(record: DataLog, row: DataLogRow): List<InfoFact> {
  val source = record.source
  val plottable = record.series.count { it.isPlottable }
  return listOfNotNull(
    InfoFact(
      stringResource(Res.string.data_log_fact_date),
      row.startLocal.date.toDisplayFormat(numberOnly = false)
    ),
    InfoFact(
      stringResource(Res.string.data_log_fact_start),
      row.startLocal.time.toClockText()
    ),
    InfoFact(
      stringResource(Res.string.data_log_fact_offset),
      offsetText(record.utc_offset_minutes)
    ),
    InfoFact(
      stringResource(Res.string.data_log_fact_duration),
      formatDuration(row.durationSeconds)
    ),
    InfoFact(
      stringResource(Res.string.data_log_fact_samples),
      record.sample_count.toString()
    ),
    InfoFact(
      stringResource(Res.string.data_log_fact_rate),
      stringResource(
        Res.string.data_log_fact_rate_value,
        formatSeriesValue(record.sample_rate_hz)
      )
    ),
    InfoFact(
      stringResource(Res.string.data_log_fact_series),
      stringResource(
        Res.string.data_log_fact_series_value,
        plottable,
        record.series.size - plottable
      )
    ),
    InfoFact(stringResource(Res.string.data_log_fact_file), record.file_name),
    source?.product?.takeIf { it.isNotBlank() }
      ?.let { InfoFact(stringResource(Res.string.data_log_fact_product), it) },
    source?.unit?.takeIf { it.isNotBlank() }
      ?.let { InfoFact(stringResource(Res.string.data_log_fact_unit), it) },
    source?.software_version?.takeIf { it.isNotBlank() }
      ?.let { InfoFact(stringResource(Res.string.data_log_fact_software), it) },
    source?.system_id?.takeIf { it.isNotBlank() }
      ?.let {
        InfoFact(
          stringResource(Res.string.data_log_fact_system_id),
          it
        )
      },
    source?.identity?.takeIf { it.isNotBlank() }
      ?.let { InfoFact(stringResource(Res.string.data_log_fact_identity), it) },
    source?.airframe_hours?.takeIf { it.isNotBlank() }
      ?.let {
        InfoFact(
          stringResource(Res.string.data_log_fact_airframe_hours),
          it
        )
      },
    source?.engine_hours?.takeIf { it.isNotBlank() }
      ?.let {
        InfoFact(
          stringResource(Res.string.data_log_fact_engine_hours),
          it
        )
      },
  )
}
