package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.ComponentSlot
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate

/** The three fields every component carries, whatever slot it fills. */
enum class ComponentField { MAKE, MODEL, SERIAL }

fun Component.valueOf(field: ComponentField): String = when (field) {
  ComponentField.MAKE -> make
  ComponentField.MODEL -> model
  ComponentField.SERIAL -> serial
}

fun Component.with(field: ComponentField, value: String): Component = when (field) {
  ComponentField.MAKE -> copy(make = value)
  ComponentField.MODEL -> copy(model = value)
  ComponentField.SERIAL -> copy(serial = value)
}

/**
 * One row of the component tree: a slot the template declares, paired with the component filling it.
 *
 * The tree the edit form draws is a flattened walk of the template's slots (#729). Flattened rather
 * than nested because the renderer needs [depth] for indentation anyway, and a flat list is what a
 * `LazyColumn` wants — the nesting is already expressed by [path].
 */
data class ComponentRow(
  val slot: ComponentSlot,
  val path: ComponentPath,
  val component: Component?,
  val depth: Int,
  /** Index among siblings in the same slot — the "2" in "Blade 2". Null when the slot holds one. */
  val ordinal: Int?,
) {
  /**
   * "Engine", or "Blade 2" when the slot repeats.
   *
   * Numbered only when repeatable: an airframe is never "Airframe 1", and a car with one engine
   * should not read "Engine 1" either.
   */
  val label: String
    get() = if (ordinal == null) slot.label else "${slot.label} ${ordinal + 1}"

  /** A slot that repeats can always take another; one that does not is created with the tree. */
  val canRemove: Boolean get() = slot.repeatable && component != null
}

/**
 * The rows to draw for [template], in declaration order, depth first.
 *
 * **A non-repeatable slot always yields a row**, even with no component stored — that is what makes
 * an empty form fillable rather than presenting nothing to type into. A repeatable slot yields one
 * row per component and none when empty, because "how many" is the user's to decide; the add
 * control belongs to the parent.
 */
fun ThingTemplate?.componentRows(thing: Thing): List<ComponentRow> {
  fun walk(
    slots: List<ComponentSlot>,
    parentPath: ComponentPath,
    siblings: List<Component>,
    depth: Int,
  ): List<ComponentRow> = slots.flatMap { slot ->
    val filling = siblings.filter { it.slot_key == slot.slot_key }
    val occurrences: List<Pair<Component?, Int?>> = when {
      slot.repeatable -> filling.mapIndexed { index, component -> component to index }
      else -> listOf(filling.firstOrNull() to null)
    }
    occurrences.flatMapIndexed { index, (component, ordinal) ->
      val path = parentPath + (slot.slot_key to index)
      listOf(ComponentRow(slot, path, component, depth, ordinal)) +
        walk(slot.children, path, component?.children.orEmpty(), depth + 1)
    }
  }
  return walk(this?.component_slots.orEmpty(), emptyList(), thing.components, 0)
}

/**
 * Slots under [parentPath] that the user may add another of.
 *
 * Only repeatable ones. `ComponentSlot` carries a single bool where PRD §4.3 designed a three-way
 * cardinality, so "may add" and "may hold several" are the same question here — a non-repeatable
 * slot already has its row and adding a second would produce a tree the template cannot describe.
 */
fun ThingTemplate?.addableSlotsUnder(parentPath: ComponentPath): List<ComponentSlot> {
  if (this == null) return emptyList()
  if (parentPath.isEmpty()) return component_slots.filter { it.repeatable }
  var slots: List<ComponentSlot> = component_slots
  var found: ComponentSlot? = null
  for ((slotKey, _) in parentPath) {
    found = slots.firstOrNull { it.slot_key == slotKey } ?: return emptyList()
    slots = found.children
  }
  return found?.children.orEmpty().filter { it.repeatable }
}

/** A new component for [slot], with the non-repeatable descendants the template expects. */
fun newComponentFor(slot: ComponentSlot): Component = Component(
  slot_key = slot.slot_key,
  // Only the slots that always exist. A repeatable child is the user's to add — creating one
  // would assert a count the template deliberately leaves open.
  children = slot.children.filterNot { it.repeatable }.map { newComponentFor(it) },
)

/**
 * Every component present but missing a serial the template expects.
 *
 * **Present is the operative word.** A slot with no component has nothing to validate — a car with
 * no engine recorded is complete, not invalid — so this walks what is stored rather than what is
 * declared.
 */
fun ThingTemplate?.componentsMissingSerials(thing: Thing): List<ComponentRow> =
  componentRows(thing).filter { row ->
    row.slot.serial_expected && row.component != null && row.component.serial.isBlank()
  }
