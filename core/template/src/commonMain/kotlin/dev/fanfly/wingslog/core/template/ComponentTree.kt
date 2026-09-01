package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.ComponentSlot
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate

/** The three fields every component carries, whatever slot it fills. */
enum class ComponentField(val key: String) {
  MAKE("make"),
  MODEL("model"),
  SERIAL("serial"),
}

fun Component.valueOf(field: ComponentField): String = when (field) {
  ComponentField.MAKE -> make
  ComponentField.MODEL -> model
  ComponentField.SERIAL -> serial
}

fun Component.with(field: ComponentField, value: String): Component =
  when (field) {
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
  /** How many components fill this slot here. One means the label carries no number. */
  val siblingCount: Int,
) {
  /**
   * "Engine", or "Blade 2" when there is more than one.
   *
   * A single engine is just "Engine" — the number only earns its place once there is something to
   * tell it apart from, which is how the form has always read.
   */
  val label: String
    get() = if (ordinal == null || siblingCount <= 1) {
      slot.label
    } else {
      "${slot.label} ${ordinal + 1}"
    }

  /**
   * Which of make / model / serial this slot asks for (PRD §4.3's `spec_keys`).
   *
   * Empty in the template means all three, so a preset that has not thought about it is unchanged.
   * Blades declare `serial` alone: they are a matched set, so their make and model are the
   * propeller's and asking per blade invites data that cannot be true.
   */
  val fields: List<ComponentField>
    get() = if (slot.spec_keys.isEmpty()) {
      ComponentField.entries
    } else {
      ComponentField.entries.filter { it.key in slot.spec_keys }
    }

  /**
   * With [ComponentSlot.compact_fields], every field takes a line of its own except the final two,
   * which share one.
   *
   * The same rule the spec block follows: make alone, then model beside serial. It reads the way a
   * plate does, rather than as three inputs each half empty. A slot asking for a serial alone has
   * nothing to pair and packs its instances instead.
   */
  val leadingFields: List<ComponentField>
    get() = if (fields.size > 1) fields.dropLast(2) else emptyList()

  val pairedFields: List<ComponentField>
    get() = if (fields.size > 1) fields.takeLast(2) else fields

  /**
   * Renders as a chip rather than a card: a repeating leaf slot **inside** another component.
   *
   * Blades on a propeller are near-identical parts told apart by a serial, and a card each buries
   * the rest of the tree in scroll. A *top-level* repeating slot is different — a boat's propulsion
   * or a car's tyre is a component in its own right, whose make and model are worth reading, and a
   * chip shows only the serial.
   *
   * **Depth is a heuristic standing in for a schema field that does not exist.** What actually
   * distinguishes the two is whether the part has an identity of its own worth showing, which PRD
   * §4.3 designed `spec_keys` to say and the shipped `ComponentSlot` cannot. Recorded on #732 with
   * the other §4.2/§4.3 gaps; when that field arrives this reads it instead of guessing from nesting.
   */
  val rendersAsChip: Boolean
    get() = slot.repeatable && slot.children.isEmpty() && depth > 0

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
      listOf(ComponentRow(slot, path, component, depth, ordinal, occurrences.size)) +
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
  return found?.children.orEmpty()
    .filter { it.repeatable }
}

/** A new component for [slot], with the non-repeatable descendants the template expects. */
fun newComponentFor(slot: ComponentSlot): Component = Component(
  slot_key = slot.slot_key,
  // Only the slots that always exist. A repeatable child is the user's to add — creating one
  // would assert a count the template deliberately leaves open.
  children = slot.children.filterNot { it.repeatable }
    .map { newComponentFor(it) },
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

/**
 * The rows of [componentRows], nested as they actually are.
 *
 * Both shapes exist because both are wanted: the flat walk answers "every component, in order",
 * which validation and the dashboard's chip grouping need, while this one is what draws a tree —
 * a propeller *inside* its engine's card rather than a card beside it with an indent.
 */
data class ComponentNode(
  val row: ComponentRow,
  val children: List<ComponentNode>,
) {
  /** Children that draw as chips — a matched set of blades, rendered under the card, not in it. */
  val chipChildren: List<ComponentNode> get() = children.filter { it.row.rendersAsChip }

  /** Children that draw as their own nested card. */
  val cardChildren: List<ComponentNode>
    get() = children.filterNot { it.row.slot.inline_with_parent }

  /**
   * Children that flow inside this card instead of nesting into one, grouped by slot.
   *
   * Grouped because a repeating inline slot is one block with several inputs rather than several
   * blocks — four blade serials under a single "Blade" heading, not four headings.
   *
   * **Includes the ones the dashboard draws as chips.** `rendersAsChip` is a display choice that
   * surface makes; the edit form needs every inline child, and filtering here once dropped blades
   * from the form entirely — they were in neither this list nor [cardChildren].
   */
  val inlineGroups: List<List<ComponentNode>>
    get() = children.filter { it.row.slot.inline_with_parent }
      .groupBy { it.row.slot.slot_key }
      .values
      .toList()

  /** [inlineGroups] minus the chip ones — what the dashboard draws as blocks. */
  val inlineBlockGroups: List<List<ComponentNode>>
    get() = inlineGroups.filterNot { it.first().row.rendersAsChip }
}

fun ThingTemplate?.componentTree(thing: Thing): List<ComponentNode> {
  val rows = componentRows(thing)
  fun nodesAt(parent: ComponentPath): List<ComponentNode> =
    rows.filter { it.path.size == parent.size + 1 && it.path.dropLast(1) == parent }
      .map { ComponentNode(it, nodesAt(it.path)) }
  return nodesAt(emptyList())
}
