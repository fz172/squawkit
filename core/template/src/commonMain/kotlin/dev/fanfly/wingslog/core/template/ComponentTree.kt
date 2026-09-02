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
 * The three lines a compacted component shows, in the order they are drawn.
 *
 * Split out here rather than assembled in the composable so the fallback — serial standing in as
 * the headline for a slot that asks for no make or model — is covered by a test rather than by a
 * screenshot.
 */
data class ComponentChipLines(
  /** "Tire 3", or plain "Propulsion" when the slot holds one. */
  val label: String,
  /** "Michelin Pilot Sport", falling back to the serial when the slot names no make or model. */
  val headline: String,
  /** Blank when [headline] is already the serial, or when the slot does not ask for one. */
  val serial: String,
)

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
   * Renders as a chip beside its siblings rather than as a card of its own.
   *
   * **The template says so now** — `compact_instances`, read verbatim. It used to be guessed from
   * `depth > 0`, on the theory that a part nested inside another was the matched-set kind and a
   * top-level one was an individual. Nesting was never the question: a boat's propulsion and a
   * car's tyres are top-level and still a set, so a rule that read correctly for an aeroplane's
   * blades listed four tyres as four full-width rows.
   *
   * Guarded on having no children because a chip has nowhere to put them — a slot declaring both
   * is a template error, and dropping the children silently would be the worse failure.
   */
  val rendersAsChip: Boolean
    get() = slot.compact_instances && slot.children.isEmpty()

  /**
   * What a chip shows: its label, the phrase naming the part, and its serial.
   *
   * [ComponentChipLines.headline] falls back to the serial when the slot asks for no make or
   * model — blades declare `spec_keys: "serial"`, so the serial *is* the identity there and
   * belongs on the prominent line rather than under an empty one.
   */
  val chipLines: ComponentChipLines?
    get() {
      val component = component ?: return null
      val visible = fields.toSet()
      val name = listOf(ComponentField.MAKE, ComponentField.MODEL)
        .filter { it in visible }
        .map { component.valueOf(it) }
        .filter { it.isNotBlank() }
        .joinToString(" ")
      val serial = if (ComponentField.SERIAL in visible) component.serial else ""
      return ComponentChipLines(
        label = label,
        headline = name.ifBlank { serial },
        // Never repeated: when the serial is already the headline there is no second line to draw.
        serial = if (name.isBlank()) "" else serial,
      )
    }

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
 * Slots under [parentPath] that the user may add another of, given the [existing] rows there.
 *
 * Repeatable ones that are not already full. A non-repeatable slot already has its row, and adding
 * a second would produce a tree the template cannot describe.
 *
 * [existing] is what makes `max_instances` real: a car's engine is repeatable so that an EV can
 * have none, and capped at one so a hatchback is not offered a second. Passed in rather than
 * re-walked from the Thing because every caller is already holding the rows at that path.
 */
fun ThingTemplate?.addableSlotsUnder(
  parentPath: ComponentPath,
  existing: List<ComponentRow> = emptyList(),
): List<ComponentSlot> {
  if (this == null) return emptyList()
  fun notFull(slot: ComponentSlot): Boolean {
    if (!slot.repeatable) return false
    if (slot.max_instances <= 0) return true
    return existing.count { it.slot.slot_key == slot.slot_key && it.component != null } <
      slot.max_instances
  }
  if (parentPath.isEmpty()) return component_slots.filter(::notFull)
  var slots: List<ComponentSlot> = component_slots
  var found: ComponentSlot? = null
  for ((slotKey, _) in parentPath) {
    found = slots.firstOrNull { it.slot_key == slotKey } ?: return emptyList()
    slots = found.children
  }
  return found?.children.orEmpty()
    .filter(::notFull)
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

  /**
   * Children that draw as their own nested card.
   *
   * Chips are excluded as well as inline ones. They used to be excluded only by accident — no
   * preset declared a chip slot that was not also `inline_with_parent` — and a slot that did
   * would have been drawn twice, once as a chip and once as a card.
   */
  val cardChildren: List<ComponentNode>
    get() = children.filterNot { it.row.slot.inline_with_parent || it.row.rendersAsChip }

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

  /**
   * Everything drawn *after* [inlineBlockGroups], in declaration order: chips and cards together.
   *
   * One list rather than a chip list and a card list, because two lists can only be drawn one
   * after the other — and that reorders a template that interleaves them. A car declares engine,
   * battery, brakes, tyres; drawing all its chips first would hoist the brakes above the engine.
   *
   * Inline chips belong here even though they are inline: a blade has no block of its own to flow
   * into, and the chip *is* how it flows under the propeller.
   */
  val groupedChildren: List<ComponentNode>
    get() = children.filter { it.row.rendersAsChip || !it.row.slot.inline_with_parent }
}

fun ThingTemplate?.componentTree(thing: Thing): List<ComponentNode> {
  val rows = componentRows(thing)
  fun nodesAt(parent: ComponentPath): List<ComponentNode> =
    rows.filter { it.path.size == parent.size + 1 && it.path.dropLast(1) == parent }
      .map { ComponentNode(it, nodesAt(it.path)) }
  return nodesAt(emptyList())
}

/**
 * A run of siblings that draw as one widget: a single card, or one slot's components as chips.
 *
 * Exists to keep chips **in declaration order**. Collecting every chip slot and drawing it first
 * would be simpler and wrong: a car declares engine, battery, brakes, tyres, and hoisting the
 * chipped brakes and tyres above the engine reorders a list the template deliberately ordered.
 */
sealed interface ComponentGroup {
  data class Card(val node: ComponentNode) : ComponentGroup
  /** Every component of one slot, drawn together — "Tire 1 … Tire 4" as one block. */
  data class Chips(val nodes: List<ComponentNode>) : ComponentGroup
}

/**
 * Chunks siblings into the widgets that draw them, in order.
 *
 * Consecutive chip nodes of the SAME slot merge into one [ComponentGroup.Chips]; everything else
 * stands alone. Same-slot components are always adjacent — `componentRows` walks a slot's
 * occurrences together — so a single pass is enough and no reordering happens.
 */
fun List<ComponentNode>.componentGroups(): List<ComponentGroup> =
  fold(mutableListOf()) { groups: MutableList<ComponentGroup>, node ->
    val previous = groups.lastOrNull()
    if (node.row.rendersAsChip &&
      previous is ComponentGroup.Chips &&
      previous.nodes.first().row.slot.slot_key == node.row.slot.slot_key
    ) {
      groups[groups.lastIndex] = ComponentGroup.Chips(previous.nodes + node)
    } else if (node.row.rendersAsChip) {
      groups += ComponentGroup.Chips(listOf(node))
    } else {
      groups += ComponentGroup.Card(node)
    }
    groups
  }
