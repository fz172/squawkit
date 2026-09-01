package dev.fanfly.wingslog.core.template.impl

import dev.fanfly.wingslog.core.template.DegradedReason
import dev.fanfly.wingslog.core.template.GenericLexicon
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.template.TemplateResolution
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.core.template.namesUnrecognisedEnumValue
import dev.fanfly.wingslog.thing.Lexicon
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate

/**
 * The baked-in registry: the seven presets compiled into this build, no cache, no fetch.
 *
 * The fetched pool and its local cache (`template_system_design.md` §4, §7.1) are designed but not
 * built — #726 and #727. Until they exist the pool cannot change without a release, which is also
 * why [fallback] can be a constant.
 */
class BakedInTemplateRegistry(
  /** This build's versionCode, compared against a template's `min_app_version` floor. */
  private val appVersionCode: Int,
  private val templates: List<ThingTemplate> = CanonicalTemplates.ALL,
  private val fallback: ThingTemplate = AirplaneTemplate.TEMPLATE,
) : TemplateRegistry {

  private val byId: Map<String, ThingTemplate> = templates.associateBy { it.id }

  /**
   * The Thing's DNA, verbatim.
   *
   * **Deliberately not refreshed from the canonical pool by id**, which is the obvious-looking fix
   * for a corrected label never reaching a Thing that already exists (PRD §4.7 asks for exactly
   * that). A customised template shares its id with the preset it came from, so consulting the pool
   * would silently revert every customisation — the failure the DNA model exists to prevent
   * (design §5), and the one `dnaWinsOverTheBakedInPresetEvenAtTheSameId` guards.
   *
   * The cost is real and worth stating: **a preset edit reaches only Things created after it.**
   * Correcting a word in a shipped preset needs a data migration, not a new asset.
   */
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

  override fun lexiconFor(template: ThingTemplate?): Lexicon {
    val id = template?.id ?: return GenericLexicon.LEXICON
    // byId first, always: this build's words win over whatever the Thing froze at creation.
    return byId[id]?.lexicon ?: template.lexicon ?: GenericLexicon.LEXICON
  }

  override fun canonical(): List<ThingTemplate> =
    templates.sortedBy { it.sort_order }

  override fun canonicalById(id: String): ThingTemplate? = byId[id]
}
