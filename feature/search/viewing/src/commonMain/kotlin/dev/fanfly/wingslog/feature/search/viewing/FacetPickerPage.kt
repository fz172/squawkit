package dev.fanfly.wingslog.feature.search.viewing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.search.sharedassets.generated.resources.Res
import wingslog.feature.search.sharedassets.generated.resources.filter_picker_back
import wingslog.feature.search.sharedassets.generated.resources.filter_recent_here
import wingslog.feature.search.sharedassets.generated.resources.filter_search_people
import wingslog.feature.search.sharedassets.generated.resources.filter_selected_count
import wingslog.feature.search.sharedassets.generated.resources.filter_selected_none

/** One person the picker lists, with what they have to show for themselves on this thing. */
data class FacetOption(
  val name: String,
  val count: Int,
  val selected: Boolean,
)

/** Above this many, scanning beats scrolling and the search field earns its space. */
private const val SearchThreshold = 8

/** Tall enough for six rows. Fixed, so five people and 250 cost the same vertical space. */
private val ListViewport = 312.dp
private val RowHeight = 52.dp
private val AvatarSize = 32.dp

/**
 * The picker behind "All 24 people": a search field over a list whose viewport never grows.
 *
 * Chips are for a closed set of four or five known options — components, urgency, date ranges.
 * People are an open list that grows with the operation, and a tail with two dozen technicians
 * wraps its chips into four ragged rows that push everything else off the sheet. The sheet keeps
 * exactly three lines for people whatever the roster does; this is where the rest of them live.
 *
 * [recent] are listed first under their own heading — the people this thing actually saw lately are
 * the ones being looked for — and the rest follow alphabetically. Anyone with nothing on this thing
 * is not in [options] at all: hidden, not greyed, because a name that cannot narrow anything is
 * noise in a list this long.
 */
@Composable
fun ColumnScope.FacetPickerPage(
  title: String,
  options: List<FacetOption>,
  recent: List<FacetOption>,
  nounSingular: String,
  nounPlural: String,
  query: String,
  onQueryChange: (String) -> Unit,
  onToggle: (FacetOption) -> Unit,
  onBack: () -> Unit,
) {
  val selectedCount = options.count { it.selected }
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    IconButton(onClick = onBack) {
      Icon(
        Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringResource(Res.string.filter_picker_back),
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleLarge)
      Text(
        if (selectedCount == 0) {
          stringResource(Res.string.filter_selected_none)
        } else {
          stringResource(
            Res.string.filter_selected_count,
            selectedCount,
            if (selectedCount == 1) nounSingular else nounPlural,
          )
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  if (options.size > SearchThreshold) {
    OutlinedTextField(
      value = query,
      onValueChange = onQueryChange,
      placeholder = {
        Text(stringResource(Res.string.filter_search_people, options.size, nounPlural))
      },
      leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
      singleLine = true,
      shape = RoundedCornerShape(Spacing.smallCornerRadius),
      modifier = Modifier.fillMaxWidth(),
    )
  }

  val matching = if (query.isBlank()) {
    options
  } else {
    options.filter { it.name.contains(query.trim(), ignoreCase = true) }
  }
  // Recency only earns its heading on the unfiltered list; once someone is searching, the one
  // ordering that helps is the one they are typing against.
  val recentShown = if (query.isBlank()) recent.filter { it in matching } else emptyList()
  val rest = matching.filterNot { it in recentShown }

  LazyColumn(modifier = Modifier.height(ListViewport)) {
    if (recentShown.isNotEmpty()) {
      item { PickerHeading(stringResource(Res.string.filter_recent_here)) }
      items(recentShown, key = { "recent:${it.name}" }) { PickerRow(it, onToggle) }
    }
    var letter: Char? = null
    rest.sortedBy { it.name.lowercase() }.forEach { option ->
      val initial = option.name.firstOrNull()?.uppercaseChar()
      if (initial != null && initial != letter) {
        letter = initial
        item(key = "heading:$initial") { PickerHeading(initial.toString()) }
      }
      item(key = "row:${option.name}") { PickerRow(option, onToggle) }
    }
  }
}

@Composable
private fun PickerHeading(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(
      start = Spacing.small,
      top = Spacing.small,
      bottom = Spacing.extraSmall,
    ),
  )
}

@Composable
private fun PickerRow(option: FacetOption, onToggle: (FacetOption) -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(RowHeight)
      .clip(RoundedCornerShape(Spacing.smallCornerRadius))
      .background(
        if (option.selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
      )
      .clickable { onToggle(option) }
      .padding(horizontal = Spacing.small),
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = option.selected, onCheckedChange = null)
    Box(
      modifier = Modifier
        .size(AvatarSize)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        option.name.initials(),
        style = WingslogTypography.dataSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Text(
      option.name,
      style = MaterialTheme.typography.bodyLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    Text(
      option.count.toString(),
      style = WingslogTypography.dataSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** "Fan Zhang" → "FZ". One letter when there is only one word to take it from. */
private fun String.initials(): String =
  trim().split(" ").filter { it.isNotBlank() }
    .let { parts ->
      when (parts.size) {
        0 -> ""
        1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
      }
    }
