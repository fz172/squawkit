package dev.fanfly.wingslog.feature.tasks.update.form.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import kotlinx.datetime.Month

/** Twelve toggles, three rows of four, for a SEASONAL schedule's months. */
@Composable
internal fun MonthGrid(
  selected: Set<Int>,
  onToggle: (Int) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    (1..12).chunked(4)
      .forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
          row.forEach { month ->
            val active = month in selected
            Box(
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(Spacing.smallCornerRadius))
                .background(
                  if (active) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.surfaceContainer
                )
                .border(
                  Spacing.hairline,
                  if (active) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.outlineVariant,
                  RoundedCornerShape(Spacing.smallCornerRadius)
                )
                .clickable { onToggle(month) },
              contentAlignment = Alignment.Center,
            ) {
              Text(
                shortMonthName(month),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = if (active) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }
  }
}

/**
 * English month names from the platform enum. The app ships in English only, and localising
 * these is part of the same decision as the lexicon (`template_system_design.md` §13).
 */
internal fun monthName(month: Int): String =
  Month(month).name.lowercase()
    .replaceFirstChar { it.titlecase() }

internal fun shortMonthName(month: Int): String = monthName(month).take(3)

/** "April", "April & October", "April, July & October". */
internal fun formatMonthList(months: Collection<Int>): String {
  val names = months.filter { it in 1..12 }
    .distinct()
    .sorted()
    .map(::monthName)
  return when (names.size) {
    0 -> ""
    1 -> names.single()
    else -> names.dropLast(1)
      .joinToString(", ") + " & " + names.last()
  }
}
