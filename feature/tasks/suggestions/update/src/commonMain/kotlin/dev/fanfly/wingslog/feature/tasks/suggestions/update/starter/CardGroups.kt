package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import dev.fanfly.wingslog.feature.tasks.suggestions.model.StarterPackItem

/** The cards filed against one component slot; an empty [slotKey] is the Thing itself. */
data class CardGroup(val slotKey: String, val cards: List<IndexedValue<StarterPackItem>>)

/**
 * [items] grouped by component (PRD R25), in the order each component first appears, keeping each
 * card's index into [items] for checking it.
 */
fun groupsOf(items: List<StarterPackItem>): List<CardGroup> {
  val indexed = items.withIndex().toList()
  return indexed.map { it.value.suggestion.component_slot_key }
    .distinct()
    .map { key -> CardGroup(key, indexed.filter { it.value.suggestion.component_slot_key == key }) }
}
