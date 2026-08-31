package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.Thing

/**
 * Reading a Thing's spec values by key, instead of off the transitional fields 2–6.
 *
 * ## Why these exist
 *
 * `make`, `model`, `serial` and `tail_number` are proto fields on `Thing` *and* entries in its
 * `spec` list. Two places holding the same truth is the state #668 ends, and the fields are the half
 * that goes: they are airplane vocabulary baked into the schema, and a house has none of them
 * (PRD §4.2).
 *
 * ## Why a key lookup rather than named properties
 *
 * It would be easy to add `Thing.make` back as an extension reading `spec`, and it would be wrong.
 * The point of the template system is that **which spec fields exist is the template's decision** —
 * a car declares a VIN, a boat a hull id, a house nothing at all. A named accessor per key
 * reintroduces the assumption the schema change removes, one extension at a time.
 *
 * So callers ask for a key. Where that key is a *convention* rather than a template's own
 * invention, [SpecKeys] names it once so the string is not scattered.
 *
 * ## The keys here are conventions, not guarantees
 *
 * `make` / `model` / `serial` / `tail_number` are the four the airplane template declares and the
 * four `ThingInflater` writes, which is why they are worth naming. **They are not promised to
 * exist** — [specValue] returns empty for a Thing whose template never declared them, which is the
 * correct answer rather than an error. A screen that must not render an empty row should check.
 */
object SpecKeys {
  const val MAKE = "make"
  const val MODEL = "model"
  const val SERIAL = "serial"
  const val TAIL_NUMBER = "tail_number"
}

/**
 * The value stored under [key], or empty if this Thing has none.
 *
 * Empty rather than null: every caller renders a string, and the absent case and the blank case are
 * the same thing to a reader. `ThingInflater` drops empty values rather than storing blanks, so a
 * key that is present is a key with a value.
 */
fun Thing.specValue(key: String): String =
  spec.firstOrNull { it.key == key }?.value_.orEmpty()

/** True when [key] has a non-empty value — for deciding whether to render a row at all. */
fun Thing.hasSpec(key: String): Boolean = specValue(key).isNotEmpty()

// ---------------------------------------------------------------------------------------------
// Component tree access
// ---------------------------------------------------------------------------------------------

/**
 * Slot keys the airplane template declares, and `ThingInflater` writes.
 *
 * Named here so the strings are not scattered, and asserted against the template itself by
 * `TemplateKeysResolveTest` — a rename on one side without the other fails there rather than
 * silently rendering an empty tree.
 *
 * These are **airplane** slots. A car or a house declares its own, which is why navigation below
 * takes a key rather than offering `thing.engines`.
 */
object SlotKeys {
  const val AIRFRAME = "airframe"
  const val ENGINE = "engine"
  const val PROPELLER = "propeller"
  const val HUB = "hub"
  const val BLADE = "blade"
}

/** Direct children of this component in [slotKey], in declared order. */
fun Component.childrenInSlot(slotKey: String): List<Component> =
  children.filter { it.slot_key == slotKey }

/** The first direct child in [slotKey], or null — for slots that are not repeatable. */
fun Component.childInSlot(slotKey: String): Component? =
  children.firstOrNull { it.slot_key == slotKey }

/**
 * Top-level components in [slotKey].
 *
 * The airplane tree has a single `airframe` root with engines beneath it, so most callers want
 * [Component.childrenInSlot] on the airframe rather than this. Kept separate because "the roots" and
 * "anything with this key, anywhere" are different questions and conflating them is how a blade
 * under engine 0 gets returned for a query about engine 1.
 */
fun Thing.rootComponentsInSlot(slotKey: String): List<Component> =
  components.filter { it.slot_key == slotKey }

/** The first root component in [slotKey], or null. */
fun Thing.rootComponentInSlot(slotKey: String): Component? =
  components.firstOrNull { it.slot_key == slotKey }

/**
 * Every component anywhere in the tree with [slotKey], depth-first in declared order.
 *
 * For a slot that appears at one level this is simply "all of them" — `allComponentsInSlot(ENGINE)`
 * returns the aircraft's engines without the caller navigating through the airframe first, and it
 * keeps working if a template ever nests them differently.
 *
 * Prefer [Component.childrenInSlot] when the *parent* matters: the blades of engine 1 are a
 * different question from every blade on the aircraft, and this cannot tell them apart.
 */
fun Thing.allComponentsInSlot(slotKey: String): List<Component> {
  fun walk(list: List<Component>): List<Component> =
    list.flatMap { component ->
      (if (component.slot_key == slotKey) listOf(component) else emptyList()) + walk(component.children)
    }
  return walk(components)
}
