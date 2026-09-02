package dev.fanfly.wingslog.feature.thing.dashboard.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.template.ThingSpecLines
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.thing.dashboard.generated.resources.Res
import wingslog.feature.thing.dashboard.generated.resources.spec_identifier_label

/**
 * The thing's own identity, drawn straight into its card rather than into a card of its own.
 *
 * A nested card is how a *component* reads — an engine is something attached to the thing, so it
 * gets a container that says where it stops. The spec is the thing itself, and boxing it made the
 * card's first row look like one more part bolted on. It now reads as the card's own subject: what
 * it is, then each identifier beside the word for it.
 */
@Composable
fun ThingSpecBlock(lines: ThingSpecLines, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxWidth()) {
    if (lines.headline.isNotBlank()) {
      Text(
        text = lines.headline,
        style = TextStyle(
          fontFamily = FontFamily.SansSerif,
          fontWeight = FontWeight.SemiBold,
          fontSize = 18.sp,
        ),
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
    lines.identifiers.forEach { identifier ->
      Row(
        modifier = Modifier.padding(top = Spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(Res.string.spec_identifier_label, identifier.label),
          style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Mono, per DESIGN.md: a value in JetBrains Mono is a measurement or an identifier, and
        // character alignment is what makes one tail number comparable to another at a glance.
        Text(
          text = identifier.value,
          modifier = Modifier.padding(start = Spacing.small),
          style = WingslogTypography.dataMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}
