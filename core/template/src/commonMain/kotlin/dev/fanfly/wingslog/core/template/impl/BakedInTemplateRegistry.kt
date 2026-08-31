package dev.fanfly.wingslog.core.template.impl

import dev.fanfly.wingslog.core.template.DegradedReason
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.template.TemplateResolution
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.namesUnrecognisedEnumValue
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
  /** This build's versionCode, compared against a template's `min_app_version` floor. */
  private val appVersionCode: Int,
  private val templates: List<ThingTemplate> = listOf(AirplaneTemplate.TEMPLATE),
  private val fallback: ThingTemplate = AirplaneTemplate.TEMPLATE,
) : TemplateRegistry {

  private val byId: Map<String, ThingTemplate> = templates.associateBy { it.id }

  override fun forThingWithFallback(thing: Thing): ThingTemplate =
    thing.template ?: fallback

  override fun resolve(thing: Thing): TemplateResolution {
    val template = forThingWithFallback(thing)
    val reason = when {
      template.min_app_version > appVersionCode -> DegradedReason.APP_TOO_OLD
      template.capabilities?.namesUnrecognisedEnumValue() == true ->
        DegradedReason.UNRECOGNISED_CAPABILITY
      // A Thing with no DNA resolves to the baked-in fallback, which this build ships and can
      // always render — so the legacy case never degrades.
      else -> return TemplateResolution.Renderable(template)
    }
    return TemplateResolution.Degraded(template, reason)
  }

  override fun canonical(): List<ThingTemplate> =
    templates.sortedBy { it.sort_order }

  override fun canonicalById(id: String): ThingTemplate? = byId[id]
}
