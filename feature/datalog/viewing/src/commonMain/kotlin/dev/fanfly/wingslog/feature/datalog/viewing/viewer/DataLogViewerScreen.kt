package dev.fanfly.wingslog.feature.datalog.viewing.viewer

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.core.nav.Screen.Companion.CROSS_SCREEN_SUCCESS_MESSAGE
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.dataLogNoun
import dev.fanfly.wingslog.core.ui.layout.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.layout.layoutTierFor
import dev.fanfly.wingslog.core.ui.selection.TextSelectionLayer
import dev.fanfly.wingslog.feature.datalog.viewing.chart.DropTarget
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SeriesDrag
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SeriesDragState
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SidebarWidth
import dev.fanfly.wingslog.feature.datalog.viewing.list.toDataLogRow
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import wingslog.core.sharedassets.generated.resources.delete_failed
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_deleted
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
  // The sidebar's inputs, hoisted above the scaffold: on a phone the sidebar is a drawer around
  // the whole screen rather than inside its content, or the top bar paints over it.
  val infoByColumn = remember(ready?.record) {
    ready?.record?.series?.associateBy { it.column }
      .orEmpty()
  }
  val dragState = remember(ready?.record) { SeriesDragState() }
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
  val sidebar = viewerSidebar(
    state = ready,
    row = row,
    dragState = dragState,
    onDrop = onDrop,
    viewModel = viewModel,
  )
  // The viewer is a top-level route outside the adaptive shell, so it derives its own tier;
  // otherwise every window would take the phone layout and hide the sidebar in a drawer.
  BoxWithConstraints {
    CompositionLocalProvider(LocalLayoutTier provides layoutTierFor(maxWidth)) {
      val compact = LocalLayoutTier.current.isCompact
      // A right-hand drawer behind the tune control (PRD R27, design §11.7), around the scaffold so
      // it covers the top bar too: the drawer is laid out right-to-left and its content flipped
      // back, the standard trick for an end drawer.
      val scaffold: @Composable () -> Unit = {
        Scaffold(
          topBar = {
            ViewerTopBar(
              title = row?.startLocal?.date?.toDisplayFormat(numberOnly = false)
                ?: LexiconFormatter.titleCase(lexicon.dataLogNoun),
              ready = ready != null,
              clockAxis = ready?.clockAxis == true,
              onBack = { navController.popBackStack() },
              onToggleClockAxis = viewModel::toggleClockAxis,
              onOpenSidebar = { scope.launch { drawerState.open() } },
              onDelete = viewModel::requestDelete,
            )
          },
          snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
          val content = Modifier.padding(innerPadding)
            .fillMaxSize()
          when (val s = state) {
            is DataLogViewerUiState.Loading -> ViewerLoading(s, content)

            is DataLogViewerUiState.Failed -> ViewerFailed(
              s,
              viewModel::retry,
              content
            )

            is DataLogViewerUiState.Ready -> ViewerReady(
              state = s,
              row = checkNotNull(row),
              dragState = dragState,
              onDrop = onDrop,
              viewModel = viewModel,
              sidebar = sidebar,
              compact = compact,
              modifier = content,
            )
          }
        }
      }
      if (compact && sidebar != null) {
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
              scaffold()
            }
          }
        }
      } else {
        scaffold()
      }
    }
  }
}
