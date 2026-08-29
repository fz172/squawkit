package dev.fanfly.wingslog.core.template.impl

import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate

/**
 * The Phase 2 registry: one baked-in preset, no cache, no fetch.
 *
 * The fetched pool and its local cache (`template_system_design.md` §4, §7.1) are designed but not
 * built — there is no second template to justify a distribution path, and building one for a pool
 * that cannot change would be rewritten before it was first exercised.
 */
class BakedInTemplateRegistry(
  private val templates: List<ThingTemplate> = listOf(AirplaneTemplate.TEMPLATE),
  private val fallback: ThingTemplate = AirplaneTemplate.TEMPLATE,
) : TemplateRegistry {

  private val byId: Map<String, ThingTemplate> = templates.associateBy { it.id }

  override fun forThingWithFallback(thing: Thing): ThingTemplate = thing.template ?: fallback

  override fun canonical(): List<ThingTemplate> = templates.sortedBy { it.sort_order }

  override fun canonicalById(id: String): ThingTemplate? = byId[id]
}
