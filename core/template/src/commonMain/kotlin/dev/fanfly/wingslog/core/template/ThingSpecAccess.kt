package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Thing

/**
 * Reading and editing a Thing's `spec` and `components`, by key and by path.
 *
 * Key lookups rather than named accessors: which spec fields exist is the template's decision,
 * so a `Thing.make` extension would reintroduce the assumption #668 removed.
 */
object SpecKeys {
  const val MAKE = "make"
  const val MODEL = "model"
  const val SERIAL = "serial"
  const val TAIL_NUMBER = "tail_number"
}

/** The value under [key], or empty. Absent and blank read the same to a caller. */
fun Thing.specValue(key: String): String =
  spec.firstOrNull { it.key == key }?.value_.orEmpty()

/** True when [key] has a non-empty value — for deciding whether to render a row at all. */
fun Thing.hasSpec(key: String): Boolean = specValue(key).isNotEmpty()

/**
 * The same lookup for a component's own spec — a tyre's pressure and position.
 *
 * A component carries make, model and serial as named fields and everything else in this bag, for
 * the same reason a Thing does: which fields exist is the slot's decision, not this code's.
 */
fun Component.specValue(key: String): String =
  spec.firstOrNull { it.key == key }?.value_.orEmpty()

/** Sets [key] on a component, preserving order. An empty [value] removes the entry. */
fun Component.withSpec(key: String, value: String): Component {
  val without = spec.filterNot { it.key == key }
  return copy(
    spec = if (value.isEmpty()) without else without + Spec(key = key, value_ = value),
  )
}

// ---------------------------------------------------------------------------------------------
// Component tree access
// ---------------------------------------------------------------------------------------------

/** Airplane slot keys. Asserted against the template by `TemplateKeysResolveTest`. */
object SlotKeys {
  const val ENGINE = "engine"
  const val PROPELLER = "propeller"
  const val BLADE = "blade"

  /**
   * Slots the airplane template no longer declares (#729).
   *
   * `airframe` was a component row repeating the Thing's own identity, and `hub` asked for the make,
   * model and serial the propeller already carries. Both are gone from the template; these remain
   * only for the one-off migration that restructures Things stored under the old shape, and go with
   * it once no stored Thing predates the change.
   */
  const val LEGACY_AIRFRAME = "airframe"
  const val LEGACY_HUB = "hub"
}

/** Direct children of this component in [slotKey], in declared order. */
fun Component.childrenInSlot(slotKey: String): List<Component> =
  children.filter { it.slot_key == slotKey }

/** The first direct child in [slotKey], or null — for slots that are not repeatable. */
fun Component.childInSlot(slotKey: String): Component? =
  children.firstOrNull { it.slot_key == slotKey }

/** Top-level components in [slotKey]. For nested slots use [Component.childrenInSlot]. */
fun Thing.rootComponentsInSlot(slotKey: String): List<Component> =
  components.filter { it.slot_key == slotKey }

/** The first root component in [slotKey], or null. */
fun Thing.rootComponentInSlot(slotKey: String): Component? =
  components.firstOrNull { it.slot_key == slotKey }

/**
 * Every component with [slotKey], anywhere in the tree.
 *
 * Use [Component.childrenInSlot] when the parent matters — this cannot tell engine 1's blades
 * from engine 0's.
 */
fun Thing.allComponentsInSlot(slotKey: String): List<Component> {
  fun walk(list: List<Component>): List<Component> =
    list.flatMap { component ->
      (if (component.slot_key == slotKey) listOf(component) else emptyList()) + walk(
        component.children
      )
    }
  return walk(components)
}

// ---------------------------------------------------------------------------------------------
// Editing
// ---------------------------------------------------------------------------------------------

/** Sets [key], preserving order. An empty [value] removes the entry rather than storing a blank. */
fun Thing.withSpec(key: String, value: String): Thing {
  val without = spec.filterNot { it.key == key }
  return copy(
    spec = if (value.isEmpty()) without else without + Spec(
      key = key,
      value_ = value
    )
  )
}

/**
 * Re-derives every component id from its position.
 *
 * Ids embed the Thing id, which a Thing being created does not have yet — so the form edits an
 * id-less tree and this runs at save. Positional derivation keeps ids stable across saves, which
 * matters because they are the join key logs and tasks point at.
 */
fun Thing.withDerivedComponentIds(): Thing {
  // Roots are numbered, but their path is not passed down — see walkPreservingOrder's note.
  val counters = mutableMapOf<String, Int>()
  return copy(
    components = components.map { root ->
      val index = counters.getOrElse(root.slot_key) { 0 }
      counters[root.slot_key] = index + 1
      root.copy(
        id = ThingInflater.componentId(
          id,
          listOf(root.slot_key, index.toString())
        ),
        children = walkPreservingOrder(root.children, emptyList(), id),
      )
    },
  )
}

/**
 * Siblings are numbered per slot key, so a hub beside a blade does not shift the blade's index.
 *
 * The root is absent from its descendants' paths — an engine is `engine.0`, not
 * `airframe.0.engine.0`. An irregularity from the cutover, preserved because these ids are stored.
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

/**
 * A component address: slot key and index within that slot, one entry per level.
 *
 * Indexing within a slot rather than the child list keeps a path stable when other slots are
 * added beside it, and matches how ids are derived.
 */
typealias ComponentPath = List<Pair<String, Int>>

private fun List<Component>.at(slotKey: String, index: Int): Component? =
  filter { it.slot_key == slotKey }.getOrNull(index)

/** The component at [path], or null if any step is missing. */
fun Thing.componentAt(path: ComponentPath): Component? {
  var current: Component? = null
  var siblings = components
  for ((slotKey, index) in path) {
    current = siblings.at(slotKey, index) ?: return null
    siblings = current.children
  }
  return current
}

/** Replaces the component at [path]; a no-op if absent. Template-agnostic — the path carries the domain. */
fun Thing.updateComponentAt(
  path: ComponentPath,
  transform: (Component) -> Component
): Thing {
  if (path.isEmpty()) return this
  return copy(components = updateIn(components, path, transform))
}

private fun updateIn(
  siblings: List<Component>,
  path: ComponentPath,
  transform: (Component) -> Component,
): List<Component> {
  val (slotKey, index) = path.first()
  var seen = -1
  return siblings.map { component ->
    if (component.slot_key != slotKey) return@map component
    seen++
    if (seen != index) return@map component
    if (path.size == 1) {
      transform(component)
    } else {
      component.copy(
        children = updateIn(
          component.children,
          path.drop(1),
          transform
        )
      )
    }
  }
}

/** Appends under [parent], or at the root when empty. An absent parent is a no-op, not invented. */
fun Thing.addComponent(parent: ComponentPath, component: Component): Thing =
  if (parent.isEmpty()) {
    copy(components = components + component)
  } else {
    updateComponentAt(parent) { it.copy(children = it.children + component) }
  }

/** Removes the component at [path]. A no-op if it does not exist. */
fun Thing.removeComponentAt(path: ComponentPath): Thing {
  if (path.isEmpty()) return this
  val parent = path.dropLast(1)
  val (slotKey, index) = path.last()
  fun prune(siblings: List<Component>): List<Component> {
    var seen = -1
    return siblings.filterNot { component ->
      if (component.slot_key != slotKey) return@filterNot false
      seen++
      seen == index
    }
  }
  return if (parent.isEmpty()) {
    copy(components = prune(components))
  } else {
    updateComponentAt(parent) { it.copy(children = prune(it.children)) }
  }
}

/** Creates [path] and any missing ancestors — typing into a hub on an engine with no propeller. */
fun Thing.ensureComponentAt(path: ComponentPath): Thing {
  var result = this
  for (depth in path.indices) {
    val prefix = path.subList(0, depth + 1)
    if (result.componentAt(prefix) == null) {
      result = result.addComponent(
        prefix.dropLast(1),
        Component(slot_key = prefix.last().first),
      )
    }
  }
  return result
}
