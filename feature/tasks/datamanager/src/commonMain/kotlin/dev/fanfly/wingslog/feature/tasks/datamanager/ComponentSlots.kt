package dev.fanfly.wingslog.feature.tasks.datamanager

import dev.fanfly.wingslog.core.template.SlotKeys
import dev.fanfly.wingslog.core.template.usesComponentTypes
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.ThingTemplate

/**
 * The `ComponentType` a task filed against component slot [slotKey] gets, for the AI
 * suggestions written as tasks (design §3, §7.4). `ComponentType` is aviation's frozen enum. On the airplane
 * preset a task on the Thing itself is an airframe task — the template declares no airframe slot,
 * so an empty key is what "airframe" looks like — and every other preset files it against the Thing
 * with no component (#732).
 */
fun componentTypeForSlot(
  slotKey: String,
  template: ThingTemplate?
): ComponentType =
  when {
    !template.usesComponentTypes -> ComponentType.COMPONENT_UNKNOWN
    slotKey == SlotKeys.ENGINE -> ComponentType.COMPONENT_ENGINE
    slotKey == SlotKeys.PROPELLER -> ComponentType.COMPONENT_PROPELLER
    else -> ComponentType.COMPONENT_AIRFRAME
  }

/**
 * The component slot a task on [type] is filed against, the inverse of [componentTypeForSlot]: what
 * an existing task or a log tells the AI pipeline (design §4.2). Airframe and unknown are the Thing
 * itself, an empty key.
 */
fun slotKeyFor(type: ComponentType): String =
  when (type) {
    ComponentType.COMPONENT_ENGINE -> SlotKeys.ENGINE
    ComponentType.COMPONENT_PROPELLER -> SlotKeys.PROPELLER
    else -> ""
  }
