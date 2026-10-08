package dev.fanfly.wingslog.feature.tasks.suggestions.update.review

import dev.fanfly.wingslog.feature.tasks.suggestions.model.SuggestionItem

/** The cards filed against one component slot; an empty [slotKey] is the Thing itself. */
data class CardGroup(
  val slotKey: String,
  val cards: List<SuggestionItem>
)

/**
 * [items] grouped by component (PRD R25). The Thing's own section comes first (1d: Airframe, then Engine, then Propeller), the rest in the
 * order each first appears.
 */
fun groupsOf(items: List<SuggestionItem>): List<CardGroup> {
  return items.map { it.suggestion.component_slot_key }
    .distinct()
    .sortedBy { it.isNotEmpty() }
    .map { key -> CardGroup(key, items.filter { it.suggestion.component_slot_key == key }) }
}
