package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate

/**
 * Resolves the template that renders a [Thing], and lists the templates a picker may offer.
 *
 * See `docs/product/template_system_design.md` §5 and §8. Two ideas do all the work here:
 *
 * **A Thing carries its own template.** `Thing.template` is DNA, inflated at creation, not a
 * reference — so resolution is a field read, never a lookup. That is why there is no cache to miss,
 * no second document to arrive late, and why a share member gets the template with the read they
 * already make.
 *
 * **A Thing without DNA can only be an airplane.** The set of such Things is closed: it is exactly
 * those created before templates existed. A second preset can only be chosen through a picker, and
 * a client with a picker inflates DNA — so even an un-updated client writing today produces an
 * airplane, having no way to make anything else. The fallback below needs no stored hint, which is
 * why `Thing.template_id` was removed rather than kept as one.
 */
interface TemplateRegistry {

  /**
   * The template that renders [thing], falling back when it carries none.
   *
   * The name says what this adds over reading `thing.template` directly: **the fallback**. Almost
   * every Thing has DNA and this is a field read — the method exists for the ones that do not, and
   * a caller reaching for `thing.template` instead is one that will render a legacy Thing blank.
   *
   * Never null and never a network call. Deliberately non-nullable, unlike
   * `CollectionKind.fromWire`'s `error()` on an unknown name: that is right for an unknown
   * collection, which means a corrupt local database, but a Thing without DNA is ordinary and has
   * a correct answer, so failing would turn a legacy Thing into a crash.
   */
  fun forThingWithFallback(thing: Thing): ThingTemplate

  /**
   * [forThingWithFallback], plus whether this build can actually interpret what it found (§6.2).
   *
   * Every surface that *renders* a Thing wants this one rather than the template alone: the
   * template is readable in both outcomes, so a caller reading only the field cannot tell a
   * renderable Thing from one it is about to draw wrong.
   */
  fun resolve(thing: Thing): TemplateResolution

  /**
   * Templates a picker may offer, in [ThingTemplate.sort_order].
   *
   * Only ever the **canonical** pool — never a Thing's DNA. A user's customised template is theirs
   * and is not on offer to anyone, including them, for a second Thing.
   */
  fun canonical(): List<ThingTemplate>

  /**
   * A canonical template by id, or null when this build does not carry it.
   *
   * Null is expected rather than exceptional once the fetch RPC exists: a template may name a
   * `min_app_version` above this build, or simply not have been fetched yet.
   */
  fun canonicalById(id: String): ThingTemplate?
}
