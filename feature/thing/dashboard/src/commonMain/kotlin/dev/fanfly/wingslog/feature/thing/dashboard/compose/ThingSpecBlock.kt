package dev.fanfly.wingslog.feature.thing.dashboard.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.template.SpecLine
import dev.fanfly.wingslog.core.template.ThingSpecLines
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.thing.dashboard.generated.resources.Res
import wingslog.feature.thing.dashboard.generated.resources.spec_line_label

/**
 * The thing's own identity, drawn straight into its card rather than into a card of its own.
 *
 * A nested card is how a *component* reads — an engine is something attached to the thing, so it
 * gets a container that says where it stops. The spec is the thing itself, and boxing it made the
 * card's first row look like one more part bolted on. It now reads as the card's own subject: what
 * it is, then every value beside the word the template gives it.
 *
 * Shaped by whichever preset is in scope, so the same block is an aeroplane's tail number above
 * its serial, a car's year above its VIN, or a home's address and year built with no identifier
 * and no headline at all.
 */
@Composable
fun ThingSpecBlock(spec: ThingSpecLines, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxWidth()) {
    if (spec.headline.isNotBlank()) {
      Text(
        text = spec.headline,
        style = TextStyle(
          fontFamily = FontFamily.SansSerif,
          fontWeight = FontWeight.SemiBold,
          fontSize = 18.sp,
        ),
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
    spec.lines.forEach { line -> SpecLineText(line) }
  }
}

/**
 * "Tail Number: N532SL" — the label and its value as one piece of text.
 *
 * One [Text] rather than a Row of two: the label and the value are set in different faces and
 * sizes, and a Row aligns their boxes rather than their baselines. It is also what lets a long
 * value — a home's street address — wrap under its own label instead of running off the card.
 */
@Composable
private fun SpecLineText(line: SpecLine) {
  // Mono for identifiers only, per DESIGN.md: a value in JetBrains Mono is a measurement or an
  // identifier, never copy — character alignment is what makes one tail number comparable to
  // another at a glance, and it is the wrong face for a home's address.
  val valueStyle = if (line.isIdentifier) {
    WingslogTypography.dataMedium.toSpanStyle()
  } else {
    SpanStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Medium,
      fontSize = 14.sp,
    )
  }
  // A template is free to declare a field with no label. Rendering ": 1974" for one would be worse
  // than rendering the value alone, so the caption is dropped rather than left empty.
  val label = line.label.takeIf { it.isNotBlank() }
    ?.let { stringResource(Res.string.spec_line_label, it) }
  // Read outside buildAnnotatedString: its builder lambda is not composable, so the theme cannot
  // be reached from inside it.
  val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
  val valueColor = MaterialTheme.colorScheme.onSurface

  Text(
    text = buildAnnotatedString {
      if (label != null) {
        withStyle(
          SpanStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            color = labelColor,
          ),
        ) {
          append("$label ")
        }
      }
      withStyle(valueStyle.copy(color = valueColor)) {
        append(line.value)
      }
    },
    modifier = Modifier.padding(top = Spacing.extraSmall),
  )
}
