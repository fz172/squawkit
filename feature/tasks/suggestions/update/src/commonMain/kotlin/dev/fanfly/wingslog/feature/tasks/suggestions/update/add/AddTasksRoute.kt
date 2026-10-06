package dev.fanfly.wingslog.feature.tasks.suggestions.update.add

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.ai.AiSkipped
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.nav.Screen.Companion.CROSS_SCREEN_LINK_ACCOUNT
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.badge.ProBadge
import dev.fanfly.wingslog.core.ui.layout.LayoutTier
import dev.fanfly.wingslog.core.ui.layout.layoutTierFor
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.attachment.viewing.rememberFilePicker
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestEntry
import dev.fanfly.wingslog.feature.tasks.suggestions.update.review.MAX_DOCUMENT_MB
import dev.fanfly.wingslog.feature.tasks.suggestions.update.review.SourcesState
import dev.fanfly.wingslog.feature.tasks.suggestions.update.review.message
import dev.fanfly.wingslog.feature.tasks.suggestions.update.review.text
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.remove
import wingslog.feature.tasks.suggestions.update.generated.resources.Res
import wingslog.feature.tasks.suggestions.update.generated.resources.add_tasks_add_manual
import wingslog.feature.tasks.suggestions.update.generated.resources.add_tasks_basis
import wingslog.feature.tasks.suggestions.update.generated.resources.add_tasks_create_manually
import wingslog.feature.tasks.suggestions.update.generated.resources.add_tasks_manual_limits
import wingslog.feature.tasks.suggestions.update.generated.resources.add_tasks_manuals
import wingslog.feature.tasks.suggestions.update.generated.resources.add_tasks_missing_identity
import wingslog.feature.tasks.suggestions.update.generated.resources.add_tasks_promo
import wingslog.feature.tasks.suggestions.update.generated.resources.add_tasks_sign_in
import wingslog.feature.tasks.suggestions.update.generated.resources.add_tasks_title
import wingslog.feature.tasks.suggestions.update.generated.resources.add_tasks_upgrade
import wingslog.feature.tasks.suggestions.update.generated.resources.ai_error_sign_in_required
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_suggest
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_title
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_add_details
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_checking
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * The Add Tasks sheet on the shell's nav graph: what the task tab's add button opens. Every way out
 * replaces it, so back from the next screen lands on the task tab.
 */
@Composable
fun AddTasksRoute(
  navController: NavController,
  viewModel: AddTasksViewModel = koinViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val replaceWith = { route: String ->
    navController.navigate(route) {
      popUpTo(Screen.AddTasks.route) { inclusive = true }
    }
  }
  CompositionLocalProvider(LocalThingLexicon provides state.lexicon) {
    AddTasksSheet(
      state = state,
      onAddDocuments = viewModel::onAddDocuments,
      onPickError = viewModel::onPickFailed,
      onRemove = viewModel::onRemoveDocument,
      onUpgrade = { replaceWith(Screen.Subscription.route) },
      onCreateManually = {
        replaceWith(
          Screen.AddMaintenanceTask.createRoute(
            viewModel.thingId
          )
        )
      },
      onSuggest = {
        when (state.entry) {
          // The link-account flow lives on the shell, which opens it once this closes.
          SuggestEntry.SignInRequired -> {
            navController.previousBackStackEntry?.savedStateHandle?.set(
              CROSS_SCREEN_LINK_ACCOUNT,
              true,
            )
            navController.popBackStack()
          }

          is SuggestEntry.MissingIdentity ->
            replaceWith(Screen.EditThing.createRoute(viewModel.thingId))

          else -> viewModel.onSuggest()
            ?.let(replaceWith)
        }
      },
      onDismiss = { navController.popBackStack() },
    )
  }
}

/**
 * A bottom sheet on a phone (3a, 3b), a centered dialog with its buttons at the end on wider
 * layouts (3c). Drawn in the nav dialog's own window, over its scrim: tapping outside closes it.
 */
@Composable
internal fun AddTasksSheet(
  state: AddTasksUiState,
  onAddDocuments: (List<PickedFile>) -> Unit,
  onPickError: () -> Unit,
  onRemove: (String) -> Unit,
  onUpgrade: () -> Unit,
  onCreateManually: () -> Unit,
  onSuggest: () -> Unit,
  onDismiss: () -> Unit,
) {
  val pickFiles =
    rememberFilePicker(onResult = onAddDocuments, onReadError = onPickError)
  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onDismiss,
      ),
  ) {
    val compact = layoutTierFor(maxWidth) == LayoutTier.COMPACT
    val panel = Modifier
      // Taps on the panel stay on it.
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = {},
      )
    if (compact) {
      Surface(
        modifier = panel
          .align(Alignment.BottomCenter)
          .fillMaxWidth(),
        shape = RoundedCornerShape(
          topStart = SheetCorner,
          topEnd = SheetCorner
        ),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
      ) {
        Column(
          modifier = Modifier
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(
              start = Spacing.extraLarge,
              end = Spacing.extraLarge,
              top = Spacing.medium,
              bottom = Spacing.extraLarge,
            ),
          verticalArrangement = Arrangement.spacedBy(Spacing.xLarge),
        ) {
          Box(
            modifier = Modifier
              .align(Alignment.CenterHorizontally)
              .size(width = 36.dp, height = 4.dp)
              .background(
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(2.dp)
              ),
          )
          Title()
          Body(state, pickFiles, onRemove, onUpgrade)
          Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            Actions(
              state = state,
              wide = false,
              onCreateManually = onCreateManually,
              onSuggest = onSuggest,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    } else {
      Surface(
        modifier = panel
          .align(Alignment.Center)
          .padding(Spacing.huge)
          .widthIn(max = DialogWidth)
          .fillMaxWidth(),
        shape = RoundedCornerShape(SheetCorner),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
      ) {
        Column(
          modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(Spacing.extraLarge),
          verticalArrangement = Arrangement.spacedBy(Spacing.xLarge),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Title() }
            IconButton(onClick = onDismiss) {
              Icon(
                Icons.Default.Close,
                contentDescription = stringResource(CoreRes.string.cancel)
              )
            }
          }
          Body(state, pickFiles, onRemove, onUpgrade)
          Row(
            modifier = Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
          ) {
            Actions(
              state = state,
              wide = true,
              onCreateManually = onCreateManually,
              onSuggest = onSuggest,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun Title() {
  Text(
    text = stringResource(
      Res.string.add_tasks_title,
      LexiconFormatter.titleCasePlural(LocalThingLexicon.current.taskNoun),
    ),
    style = MaterialTheme.typography.titleLarge,
  )
}

/** The suggestion card, then the one slot that changes with the plan. */
@Composable
private fun ColumnScope.Body(
  state: AddTasksUiState,
  pickFiles: () -> Unit,
  onRemove: (String) -> Unit,
  onUpgrade: () -> Unit,
) {
  SuggestCard(state)
  when {
    state.entry == SuggestEntry.Available && state.sources.isChecking -> Checking()
    state.showsManuals -> Manuals(state.sources, pickFiles, onRemove)
    state.showsPromo -> ProPromo(onUpgrade)
  }
}

@Composable
private fun SuggestCard(state: AddTasksUiState) {
  val lexicon = LocalThingLexicon.current
  Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
    Icon(
      Icons.Default.AutoAwesome,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
    )
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = stringResource(
          Res.string.sources_title,
          LexiconFormatter.titleCasePlural(lexicon.taskNoun),
        ),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = stringResource(
          Res.string.add_tasks_basis,
          lexicon.thingNoun.singular
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (state.details.isNotEmpty()) {
        Text(
          text = state.details,
          style = WingslogTypography.dataSmall,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(top = Spacing.extraSmall),
        )
      }
      // Why *Suggest* does something else, or gives the curated list alone today.
      val note = when (val entry = state.entry) {
        SuggestEntry.SignInRequired -> stringResource(Res.string.ai_error_sign_in_required)
        is SuggestEntry.MissingIdentity -> stringResource(
          Res.string.add_tasks_missing_identity,
          entry.fieldLabels.joinToString(", "),
        )

        else -> state.sources.blocked?.let {
          AiSkipped(
            it,
            state.sources.availableAt
          ).text()
        }
      }
      if (note != null) {
        Text(
          text = note,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = Spacing.extraSmall),
        )
      }
    }
  }
}

@Composable
private fun Checking() {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    CircularProgressIndicator(
      modifier = Modifier.size(Spacing.large),
      strokeWidth = 2.dp
    )
    Text(
      text = stringResource(Res.string.suggestions_checking),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** Pro: the manuals the run will read, and the way to add one (3b). */
@Composable
private fun Manuals(
  sources: SourcesState,
  pickFiles: () -> Unit,
  onRemove: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Text(
      text = stringResource(Res.string.add_tasks_manuals).uppercase(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    sources.documents.forEach { document ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(44.dp)
          .background(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            RoundedCornerShape(Spacing.cardCornerRadius),
          )
          .border(
            Spacing.hairline,
            MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(Spacing.cardCornerRadius),
          )
          .padding(start = Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          Icons.Outlined.Description,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text = document.name,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onRemove(document.id) }) {
          Icon(
            Icons.Default.Close,
            contentDescription = stringResource(CoreRes.string.remove),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
    if (!sources.atLimit) {
      val dash = MaterialTheme.colorScheme.outline
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(40.dp)
          .dashedBorder(dash)
          .clickable(enabled = !sources.isAdding, onClick = pickFiles)
          .padding(horizontal = Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        if (sources.isAdding) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
          )
        } else {
          Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
        }
        Text(
          text = stringResource(Res.string.add_tasks_add_manual),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
    sources.problem?.let { problem ->
      Text(
        text = problem.message(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }
    Text(
      text = stringResource(
        Res.string.add_tasks_manual_limits,
        SourcesState.MAX_DOCUMENTS_PER_RUN,
        MAX_DOCUMENT_MB,
      ),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * Not Pro: one outlined line, the badge the only amber (3a). It never blocks the free suggestion
 * beside it.
 */
@Composable
private fun ProPromo(onUpgrade: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .border(
        BorderStroke(
          Spacing.hairline,
          MaterialTheme.colorScheme.outlineVariant
        ),
        RoundedCornerShape(Spacing.cardCornerRadius),
      )
      .padding(start = Spacing.medium, end = Spacing.extraSmall),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    ProBadge()
    Text(
      text = stringResource(Res.string.add_tasks_promo),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier
        .weight(1f)
        .padding(vertical = 10.dp),
    )
    TextButton(onClick = onUpgrade) { Text(stringResource(Res.string.add_tasks_upgrade)) }
  }
}

/**
 * *Create manually* outlined and *Suggest* filled: the manual way is second, but a real button.
 * Side by side and equal on a phone; at the end, sized to their labels, in the dialog.
 */
@Composable
private fun RowScope.Actions(
  state: AddTasksUiState,
  wide: Boolean,
  onCreateManually: () -> Unit,
  onSuggest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val height = if (wide) 44.dp else 52.dp
  val shape = RoundedCornerShape(Spacing.buttonCornerRadius)
  OutlinedButton(
    onClick = onCreateManually,
    shape = shape,
    modifier = modifier.height(height),
  ) {
    Icon(
      Icons.Default.Edit,
      contentDescription = null,
      modifier = Modifier.size(20.dp)
    )
    Spacer(Modifier.width(6.dp))
    Text(stringResource(Res.string.add_tasks_create_manually), maxLines = 1)
  }
  Button(
    onClick = onSuggest,
    enabled = state.canSuggest,
    shape = shape,
    modifier = modifier.height(height),
  ) {
    val label = when (state.entry) {
      SuggestEntry.SignInRequired -> stringResource(Res.string.add_tasks_sign_in)
      is SuggestEntry.MissingIdentity -> stringResource(Res.string.suggestions_add_details)
      SuggestEntry.Available, SuggestEntry.Hidden -> if (wide) {
        stringResource(
          Res.string.sources_title,
          LexiconFormatter.titleCasePlural(LocalThingLexicon.current.taskNoun),
        )
      } else {
        stringResource(Res.string.sources_suggest)
      }
    }
    if (state.entry == SuggestEntry.Available) {
      Icon(
        Icons.Default.AutoAwesome,
        contentDescription = null,
        modifier = Modifier.size(20.dp)
      )
      Spacer(Modifier.width(6.dp))
    }
    Text(label, maxLines = 1)
  }
}

/** The *Add manual* outline: dashed, so it reads as a place to put something. */
private fun Modifier.dashedBorder(color: Color): Modifier = drawBehind {
  val stroke = Spacing.hairline.toPx()
  drawRoundRect(
    color = color,
    style = Stroke(
      width = stroke,
      pathEffect = PathEffect.dashPathEffect(
        floatArrayOf(
          4.dp.toPx(),
          4.dp.toPx()
        )
      ),
    ),
    cornerRadius = CornerRadius(Spacing.cardCornerRadius.toPx()),
  )
}

private val SheetCorner = 28.dp
private val DialogWidth = 520.dp
