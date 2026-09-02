package dev.fanfly.wingslog.feature.thing.dashboard.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.componentTree
import dev.fanfly.wingslog.core.template.specLines
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.thing.Thing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.edit
import wingslog.core.sharedassets.generated.resources.manage_access
import wingslog.feature.logs.viewing.generated.resources.collapse_details
import wingslog.feature.logs.viewing.generated.resources.expand_details
import wingslog.feature.logs.viewing.generated.resources.s_n_placeholder
import wingslog.feature.logs.viewing.generated.resources.thing_data
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.logs.viewing.generated.resources.Res as MaintenanceRes


@Composable
fun ThingDataCard(
  thing: Thing,
  initiallyExpanded: Boolean = true,
  onEditClick: (() -> Unit)? = null,
  onManageAccessClick: (() -> Unit)? = null,
) {
  var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
  val rotationState by animateFloatAsState(
    targetValue = if (expanded) 180f else 0f,
    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
  )

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    )
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth()
          .clickable { expanded = !expanded }
          .padding(
            horizontal = Spacing.large,
            vertical = Spacing.large
          ),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Dataset,
          contentDescription = null,
          modifier = Modifier.size(Spacing.xLarge),
          tint = MaterialTheme.colorScheme.primary
        )

        Text(
          text = stringResource(
            MaintenanceRes.string.thing_data,
            LexiconFormatter.titleCase(LocalThingLexicon.current.thingNoun),
          ),
          modifier = Modifier
            .padding(start = Spacing.medium)
            .weight(1f),

          style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            letterSpacing = 0.05.sp
          ),
          color = MaterialTheme.colorScheme.secondary
        )

        Icon(
          imageVector = Icons.Default.KeyboardArrowDown,
          contentDescription = if (expanded) stringResource(MaintenanceRes.string.collapse_details) else stringResource(
            MaintenanceRes.string.expand_details
          ),
          modifier = Modifier.size(Spacing.extraLarge)
            .rotate(rotationState),
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      AnimatedVisibility(visible = expanded) {
        Column(
          modifier = Modifier.padding(
            bottom = Spacing.large,
            start = Spacing.large,
            end = Spacing.large
          ),
          verticalArrangement = Arrangement.spacedBy(Spacing.large)
        ) {
          // The thing's identity, from the spec fields the template declares. It used to be
          // captioned AIRFRAME and read make/model/serial, which a home has none of (#729).
          //
          // It is drawn into this card directly rather than into a nested one: a nested card is
          // what says "this is a part attached to the thing", and the spec IS the thing. Every
          // identifier the template declares now carries its own label, which is what stops an
          // airplane's tail number from riding along in the make/model run and its serial from
          // being captioned with the other one's value.
          val template = LocalThingTemplate.current
          val spec = template.specLines(thing)
          if (!spec.isEmpty) {
            ThingSpecBlock(spec)
          }

          // Every stored component, walked from the template's slots. Drawn as a tree by
          // containment — an engine's propeller sits inside its card — rather than as a flat
          // stack that says nothing about what is attached to what.
          if (LocalThingCapabilities.current.components) {
            template.componentTree(thing)
              .filter { it.row.component != null }
              .forEach { ComponentDetails(it) }
          }

          if (onEditClick != null || onManageAccessClick != null) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.End,
            ) {
              if (onManageAccessClick != null) {
                TextButton(onClick = onManageAccessClick) {
                  Text(text = stringResource(CoreRes.string.manage_access))
                }
              }
              if (onEditClick != null) {
                TextButton(onClick = onEditClick) {
                  Text(text = stringResource(CoreRes.string.edit))
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun ComponentCard(
  category: String,
  name: String,
  serial: String,
  modifier: Modifier = Modifier,
  content: @Composable (() -> Unit)? = null,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    color = Color.Transparent,
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    )
  ) {
    Column(modifier = Modifier.padding(Spacing.large)) {
      ComponentSummary(category = category, name = name, serial = serial)

      if (content != null) {
        Column(modifier = Modifier.padding(top = Spacing.large)) {
          content()
        }
      }
    }
  }
}

/**
 * The category, make/model and serial lines a component shows.
 *
 * Extracted from [ComponentCard] so a slot the template marks `inline_with_parent` renders exactly
 * the same three lines inside its parent's card, with no card of its own — the propeller case.
 */
@Composable
fun ComponentSummary(category: String, name: String, serial: String) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = category,
      style = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 0.1.sp,
      ),
      color = MaterialTheme.colorScheme.primary,
    )
    // Same reasoning as the serial below: a component recorded with neither make nor model has
    // nothing to show on this line, and a blank one reads as a load that failed.
    if (name.isNotBlank()) {
      Text(
        text = name,
        modifier = Modifier.padding(top = Spacing.extraSmall),
        style = TextStyle(
          fontFamily = FontFamily.SansSerif,
          fontWeight = FontWeight.SemiBold,
          fontSize = 16.sp,
        ),
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
    // Omitted entirely when there is none. A home has no serial to give, and "S/N:" followed by
    // nothing reads as data that failed to load rather than data that does not exist.
    if (serial.isNotBlank()) {
      Text(
        text = stringResource(MaintenanceRes.string.s_n_placeholder, serial),
        modifier = Modifier.padding(top = Spacing.extraSmall),
        style = TextStyle(
          fontFamily = FontFamily.SansSerif,
          fontWeight = FontWeight.Normal,
          fontSize = 13.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
