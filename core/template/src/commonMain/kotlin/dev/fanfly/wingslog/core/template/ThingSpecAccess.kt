package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.Spec
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

// ---------------------------------------------------------------------------------------------
// Editing
// ---------------------------------------------------------------------------------------------

/**
 * This Thing with [key] set to [value], preserving the order of the other entries.
 *
 * An empty [value] removes the entry rather than storing a blank, which is what `ThingInflater`
 * does and what the backend cutover did — so a Thing edited to clear its serial ends up in the same
 * shape as one that never had one.
 */
fun Thing.withSpec(key: String, value: String): Thing {
  val without = spec.filterNot { it.key == key }
  return copy(spec = if (value.isEmpty()) without else without + Spec(key = key, value_ = value))
}

/**
 * This Thing with every component id re-derived from its position.
 *
 * **Why the form cannot assign ids itself.** A component id is `"$thingId:$path"`, and a Thing being
 * created has no id until it is saved — so a form that built final ids would bake an empty one into
 * every component. Deriving them here, at save, is what lets the form edit a tree without knowing
 * the Thing's identity.
 *
 * It is also what keeps the ids *stable*: they come from position, so re-running this over an
 * already-saved Thing reproduces exactly the same ids. That matters because the ids are the join key
 * logs, tasks and squawks use to point at a component — a derivation that drifted would silently
 * repoint all of them.
 */
fun Thing.withDerivedComponentIds(): Thing {
  // Roots are numbered, but their path is not passed down — see walkPreservingOrder's note.
  val counters = mutableMapOf<String, Int>()
  return copy(
    components = components.map { root ->
      val index = counters.getOrElse(root.slot_key) { 0 }
      counters[root.slot_key] = index + 1
      root.copy(
        id = ThingInflater.componentId(id, listOf(root.slot_key, index.toString())),
        children = walkPreservingOrder(root.children, emptyList(), id),
      )
    },
  )
}

/**
 * Depth-first re-derivation that keeps declared order.
 *
 * Index within a slot is what the path uses — `engine.0`, `engine.1` — so siblings are numbered per
 * slot key rather than per position in the list. Two blades under different propellers therefore do
 * not collide, and a hub beside a blade does not push the blade's index along.
 *
 * **The root does not appear in its descendants' paths**, which is an irregularity rather than a
 * design: an engine is `engine.0`, not `airframe.0.engine.0`, while a propeller *is*
 * `engine.0.propeller.0`. `thingPayloads.ts` built the ids that way during the cutover and
 * `ThingInflater` matched it, so the ids in production have this shape. It is preserved rather than
 * tidied because these ids are the join key logs, tasks and squawks point at — regularising the
 * scheme would silently repoint every one of them.
 */
private fun walkPreservingOrder(
  list: List<Component>,
  prefix: List<String>,
  thingId: String,
): List<Component> {
  val counters = mutableMapOf<String, Int>()
  return list.map { component ->
    val index = counters.getOrElse(component.slot_key) { 0 }
    counters[component.slot_key] = index + 1
    val path = prefix + listOf(component.slot_key, index.toString())
    component.copy(
      id = ThingInflater.componentId(thingId, path),
      children = walkPreservingOrder(component.children, path, thingId),
    )
  }
}
