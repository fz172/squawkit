package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.ComponentSlot
import dev.fanfly.wingslog.thing.ThingTemplate

/**
 * Everything structurally wrong with a canonical template, phrased for a log line — the checks a
 * broken template fails silently. Refused before inflating, never after (§4.2).
 */
fun ThingTemplate.structuralProblems(): List<String> = buildList {
  if (id.isEmpty()) add("template has no id")
  // `id` identifies the template everywhere; `display_name` is what the picker draws.
  if (display_name.isEmpty()) add("$id: template has no display_name")

  spec_fields.filter { it.key.isEmpty() }
    .forEach { add("$id: spec field with blank key (label '${it.label}')") }
  meters.filter { it.key.isEmpty() }
    .forEach { add("$id: meter with blank key (label '${it.label}')") }
  allSlots().filter { it.slot_key.isEmpty() }
    .forEach { add("$id: component slot with blank key (label '${it.label}')") }

  addAll(duplicates("spec field", spec_fields.map { it.key }))
  addAll(duplicates("meter", meters.map { it.key }))
  addAll(duplicates("component slot", allSlots().map { it.slot_key }))

  // A meter naming no declared slot has nowhere to render.
  val slotKeys = allSlots().map { it.slot_key }
    .toSet()
  meters.filter { it.component_slot_key.isNotEmpty() && it.component_slot_key !in slotKeys }
    .forEach { add("$id: meter '${it.key}' names slot '${it.component_slot_key}', which is not declared") }
}

/** Every slot in the tree, not only the top level — slots nest, and keys are unique across all of them. */
fun ThingTemplate.allSlots(): List<ComponentSlot> {
  fun flatten(slots: List<ComponentSlot>): List<ComponentSlot> =
    slots.flatMap { listOf(it) + flatten(it.children) }
  return flatten(component_slots)
}

private fun ThingTemplate.duplicates(
  kind: String,
  keys: List<String>
): List<String> =
  keys.filter { it.isNotEmpty() }
    .groupingBy { it }
    .eachCount()
    .filterValues { it > 1 }
    .keys
    .map { "$id: duplicate $kind key '$it'" }
