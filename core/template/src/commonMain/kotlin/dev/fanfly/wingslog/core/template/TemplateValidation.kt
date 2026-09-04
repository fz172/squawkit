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
  certifications.filter { it.key.isEmpty() }
    .forEach { add("$id: certification with blank key (label '${it.label}')") }
  // A certification with no label is unpickable: the add flow has nothing to draw for it, and a
  // technician holding it would be tagged with a blank.
  certifications.filter { it.label.isEmpty() }
    .forEach { add("$id: certification '${it.key}' has no label") }
  // `custom_N` is the add flow's own namespace for a credential the user names themselves. A
  // template claiming one would collide with whatever they typed, under a key that then means two
  // different things depending on who wrote it.
  certifications.filter { it.key.startsWith(CUSTOM_CERTIFICATION_PREFIX) }
    .forEach { add("$id: certification '${it.key}' uses the reserved custom_ prefix") }

  addAll(duplicates("spec field", spec_fields.map { it.key }))
  addAll(duplicates("meter", meters.map { it.key }))
  addAll(duplicates("component slot", allSlots().map { it.slot_key }))
  addAll(duplicates("certification", certifications.map { it.key }))

  // A meter naming no declared slot has nowhere to render.
  val slotKeys = allSlots().map { it.slot_key }
    .toSet()
  meters.filter { it.component_slot_key.isNotEmpty() && it.component_slot_key !in slotKeys }
    .forEach { add("$id: meter '${it.key}' names slot '${it.component_slot_key}', which is not declared") }

  // A starter task becomes an ordinary MaintenanceTask the moment it is accepted, so anything the
  // task form would refuse — no title, no rule, a meter the Thing cannot read — is refused here.
  val meterKeys = meters.map { it.key }
    .toSet()
  starter_tasks.forEach { task ->
    val label = task.title.ifEmpty { "(untitled)" }
    if (task.title.isEmpty()) add("$id: starter task with blank title")
    if (task.interval_months <= 0 && (task.meter_key.isEmpty() || task.interval <= 0f)) {
      add("$id: starter task '$label' carries no rule")
    }
    if (task.meter_key.isNotEmpty() && task.meter_key !in meterKeys) {
      add("$id: starter task '$label' schedules against meter '${task.meter_key}', which is not declared")
    }
    if (task.component_slot_key.isNotEmpty() && task.component_slot_key !in slotKeys) {
      add("$id: starter task '$label' names slot '${task.component_slot_key}', which is not declared")
    }
  }
  addAll(duplicates("starter task", starter_tasks.map { it.title }))
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

/** The add flow's namespace for a credential the user names themselves — see `Certification.label`. */
const val CUSTOM_CERTIFICATION_PREFIX = "custom_"
