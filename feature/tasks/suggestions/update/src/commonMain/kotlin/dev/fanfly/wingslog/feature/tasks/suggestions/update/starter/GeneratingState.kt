package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.suggestions.update.generated.resources.Res
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_pack_stage_hint
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_coming_documents
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_coming_thing

/**
 * What the working run is doing, and that the user need not wait for it (1c): one quiet line
 * above the rows, which can be picked from meanwhile.
 */
@Composable
internal fun SuggestingNote(stage: String?, stageArg: String?) {
  Text(
    text = stageText(stage, stageArg) + " " + stringResource(Res.string.starter_pack_stage_hint),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/**
 * Where the model's rows will land when the run ends (1c): a section header and a dashed box of
 * pulsing placeholder rows. The rows above are the curated ones, ready to pick now.
 */
@Composable
internal fun ComingGroup(readsDocuments: Boolean) {
  val title = if (readsDocuments) {
    stringResource(Res.string.suggestions_coming_documents)
  } else {
    stringResource(
      Res.string.suggestions_coming_thing,
      LocalThingLexicon.current.thingNoun.singular,
    )
  }
  val pulse by rememberInfiniteTransition(label = "placeholder").animateFloat(
    initialValue = 1f,
    targetValue = 0.5f,
    animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
    label = "placeholder alpha",
  )
  val outline = MaterialTheme.colorScheme.outlineVariant
  val bar = MaterialTheme.colorScheme.surfaceContainerHighest
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Text(
      text = title.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .semantics { contentDescription = title }
        .drawBehind {
          drawRoundRect(
            color = outline,
            style = Stroke(
              width = Spacing.hairline.toPx(),
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            ),
            cornerRadius = CornerRadius(Spacing.cardCornerRadius.toPx()),
          )
        }
        .padding(Spacing.large)
        .alpha(pulse),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      PlaceholderRow(titleWidth = 0.6f, lineWidth = 0.35f, color = bar)
      PlaceholderRow(titleWidth = 0.72f, lineWidth = 0.28f, color = bar)
    }
  }
}

@Composable
private fun PlaceholderRow(
  titleWidth: Float,
  lineWidth: Float,
  color: Color,
) {
  val shape = RoundedCornerShape(Spacing.badgeCornerRadius)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Box(Modifier.size(20.dp).background(color, shape))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Box(Modifier.fillMaxWidth(titleWidth).height(12.dp).background(color, shape))
      Box(
        Modifier
          .fillMaxWidth(lineWidth)
          .height(10.dp)
          .background(color.copy(alpha = 0.7f), shape),
      )
    }
  }
}
