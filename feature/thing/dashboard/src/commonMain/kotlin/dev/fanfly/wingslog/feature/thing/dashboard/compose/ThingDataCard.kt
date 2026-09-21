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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.componentTree
import dev.fanfly.wingslog.core.template.specLines
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.thing.dashboard.data.LogStats
import dev.fanfly.wingslog.thing.MeterDef
import dev.fanfly.wingslog.thing.Thing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.edit
import wingslog.core.sharedassets.generated.resources.manage_access
import wingslog.feature.logs.viewing.generated.resources.collapse_details
import wingslog.feature.logs.viewing.generated.resources.expand_details
import wingslog.feature.logs.viewing.generated.resources.s_n_placeholder
import wingslog.feature.logs.viewing.generated.resources.thing_data
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.thing.dashboard.generated.resources.Res as DashboardRes
import wingslog.feature.thing.dashboard.generated.resources.overview_meters
import wingslog.feature.thing.dashboard.generated.resources.overview_meters_as_of
import wingslog.feature.logs.viewing.generated.resources.Res as MaintenanceRes


@Composable
fun ThingDataCard(
  thing: Thing,
  /** The meters' current readings; null hides the block, as does a template with no meters. */
  stats: LogStats? = null,
  onEditClick: (() -> Unit)? = null,
  onManageAccessClick: (() -> Unit)? = null,
) {
  var expanded by rememberSaveable { mutableStateOf(true) }
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
          // what says "this is a part attached to the thing", and the spec IS the thing. Make and
          // model head the block as the phrase that names it; everything else carries the label
          // the template gives it, which is what stops an airplane's tail number from riding
          // along in the make/model run and its serial from being captioned with that value.
          val template = LocalThingTemplate.current
          val spec = template.specLines(thing)
          if (!spec.isEmpty) {
            ThingSpecBlock(spec)
          }

          // The whole block is behind `meters` because a capability removes UI: a homeowner should
          // never see a meter cell at all (PRD §4.8).
          val meters = template?.meters.orEmpty()
          if (stats != null && LocalThingCapabilities.current.meters && meters.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MeterReadings(meters, stats)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          }

          // Every stored component, walked from the template's slots. Drawn as a tree by
          // containment — an engine's propeller sits inside its card — rather than as a flat
          // stack that says nothing about what is attached to what.
          //
          // Top-level slots go through the same grouping as nested ones, which is the fix for a
          // car listing four tyres and four brakes as eight full-width rows: a slot marked
          // `compact_instances` draws its components as chips wherever it sits in the tree.
          if (LocalThingCapabilities.current.components) {
            ComponentGroups(
              template.componentTree(thing)
                .filter { it.row.component != null },
            )
          }

          if (onEditClick != null || onManageAccessClick != null) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
              if (onManageAccessClick != null) {
                OutlinedButton(onClick = onManageAccessClick, modifier = Modifier.weight(1f)) {
                  Text(text = stringResource(CoreRes.string.manage_access))
                }
              }
              if (onEditClick != null) {
                OutlinedButton(onClick = onEditClick, modifier = Modifier.weight(1f)) {
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

/**
 * What each meter last read, and when. Plain numbers: the app knows no overhaul interval to count
 * down to, so a progress bar here would be inventing one.
 */
@Composable
private fun MeterReadings(meters: List<MeterDef>, stats: LogStats) {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = stringResource(DashboardRes.string.overview_meters),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      stats.readingsAsOf?.let { asOf ->
        Text(
          text = stringResource(DashboardRes.string.overview_meters_as_of, asOf.toDisplayFormat()),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
      meters.forEach { meter ->
        Column(modifier = Modifier.weight(1f)) {
          Text(
            // `decimal` is the template's call: hours take a decimal place, an odometer does not.
            // A declared meter nothing has recorded shows a dash — zero would read as a measurement.
            text = stats.valueFor(meter.key)
              ?.let { if (meter.decimal) it.formatToOneDecimalPlace() else it.toLong().toString() }
              ?: NO_READING,
            style = WingslogTypography.dataLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = meter.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

/** Shown for a meter the template declares but nothing has recorded a reading for yet. */
private const val NO_READING = "\u2014"

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
        style = WingslogTypography.dataSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
