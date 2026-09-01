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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import dev.fanfly.wingslog.core.template.SpecKeys
import dev.fanfly.wingslog.core.template.componentRows
import dev.fanfly.wingslog.core.template.specValue
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
          val template = LocalThingTemplate.current
          val identity = template?.spec_fields.orEmpty()
            .filterNot { it.key == SpecKeys.SERIAL }
            .map { it.label to thing.specValue(it.key) }
            .filter { (_, value) -> value.isNotBlank() }
          if (identity.isNotEmpty()) {
            ComponentCard(
              category = LexiconFormatter.titleCase(
                LocalThingLexicon.current.thingNoun
              )
                .uppercase(),
              name = identity.joinToString("  ·  ") { (_, value) -> value },
              serial = thing.specValue(SpecKeys.SERIAL),
            )
          }

          // Every stored component, walked from the template's slots rather than from
          // airframe-then-engines. A bike shows its drivetrain and wheels; a home shows nothing,
          // which is what `components: false` means.
          if (LocalThingCapabilities.current.components) {
            val rows = template.componentRows(thing).filter { it.component != null }
            rows.filterNot { it.rendersAsChip }.forEach { row ->
              // Indented by depth so the tree reads as a tree: a propeller sits under its engine
              // and its blades under it, rather than four cards in a flat stack that say nothing
              // about what is attached to what.
              ComponentDetails(
                label = row.label.uppercase(),
                component = row.component!!,
                depth = row.depth,
              )
              // Its repeating leaf children ride along as chips under their parent — blades under
              // a propeller — rather than as a card each, one level further in again.
              rows.filter { it.rendersAsChip && it.path.dropLast(1) == row.path }
                .groupBy { it.slot.slot_key }
                .forEach { (_, chips) ->
                  ComponentChips(
                    label = chips.first().slot.label,
                    components = chips.mapNotNull { it.component },
                    modifier = Modifier.padding(start = indentFor(row.depth + 1)),
                  )
                }
            }
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
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = category,
            style = TextStyle(
              fontFamily = FontFamily.SansSerif,
              fontWeight = FontWeight.Bold,
              fontSize = 10.sp,
              letterSpacing = 0.1.sp
            ),
            color = MaterialTheme.colorScheme.primary
          )
          Text(
            text = name,
            modifier = Modifier.padding(top = Spacing.extraSmall),
            style = TextStyle(
              fontFamily = FontFamily.SansSerif,
              fontWeight = FontWeight.SemiBold,
              fontSize = 16.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = stringResource(
              MaintenanceRes.string.s_n_placeholder,
              serial
            ),
            modifier = Modifier.padding(top = Spacing.extraSmall),
            style = TextStyle(
              fontFamily = FontFamily.SansSerif,
              fontWeight = FontWeight.Normal,
              fontSize = 13.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      if (content != null) {
        Column(modifier = Modifier.padding(top = Spacing.large)) {
          content()
        }
      }
    }
  }
}

/**
 * How far a component at [depth] is inset.
 *
 * Small on purpose: the card border already separates rows, so the indent only has to say which
 * one owns which. A full step per level runs out of width by the third — an aeroplane's blades are
 * three deep — on the phone this card is mostly read on.
 */
internal fun indentFor(depth: Int): Dp = (depth * 12).dp
