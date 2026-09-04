package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.core.model.technician.resolvedCertifications
import dev.fanfly.wingslog.thing.CertificationDef
import dev.fanfly.wingslog.thing.Technician
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate

/**
 * A certification kind, and the template that recognises it.
 *
 * @property templateId the declaring template's stable id — the join between a stored
 *   `Certification.type` and the words below.
 * @property roleLabel what a technician holding this certification is tagged as. The template's
 *   `display_name`, because that is the domain the credential implies: an A&P is tagged "Airplane".
 */
data class OfferedCertification(
  val templateId: String,
  val roleLabel: String,
  val def: CertificationDef,
) {
  val key: String get() = def.key
  val label: String get() = def.label
}

/**
 * Every certification this build knows about — the words a **stored** key renders under, and the
 * roles it implies.
 *
 * Deliberately the whole installed pool rather than the account's own templates, because what a
 * credential *means* does not depend on what the user happens to own: an A&P is an aviation
 * credential on a household account too, and a technician linked from someone else's shared boat
 * arrives carrying a key from a preset this account has no Thing of. What the account owns decides
 * only what the add flow *offers* — see [offeredCertifications].
 *
 * A key outside this list stays untagged rather than failing, matching `canonicalById` returning
 * null as ordinary rather than exceptional (design §4.7).
 */
fun TemplateRegistry.knownCertifications(): List<OfferedCertification> =
  canonical().certifications()

/**
 * The certification kinds the add flow offers, given the [things] the account holds (PRD §8.6).
 *
 * **The account is the scope, not a Thing.** The roster aggregates every technician the user has,
 * including ones linked from shared Things, so the question has to be asked of the whole fleet.
 * That is exactly why the retired `technician_certificates` capability could not answer it: a
 * capability is read from one Thing's template.
 *
 * **Words come from this build, not from a Thing's DNA** — the same rule as
 * [TemplateRegistry.lexiconFor], for the same reason. A certification's label is app UI written
 * against a release; a copy frozen into each Thing at creation would leave a corrected label
 * unreachable. Only the `key` is data, and it is the key a stored `Certification` names.
 *
 * **An empty fleet offers everything, not nothing.** The roster is reachable from settings before a
 * user has added anything, and no Things means the domain is *unknown* rather than absent — so the
 * narrowing this does has nothing to narrow. Offering nothing there would let someone add a mechanic
 * and find no way to say what they are certified in.
 */
fun TemplateRegistry.offeredCertifications(things: List<Thing>): List<OfferedCertification> {
  if (things.isEmpty()) return knownCertifications()
  return things
    .map { forThingWithFallback(it) }
    .distinctBy { it.id }
    // The canonical copy first: a Thing created two releases ago froze whatever labels shipped then.
    .map { dna -> canonicalById(dna.id) ?: dna }
    .certifications()
}

/** Deduplicated by key, in template `sort_order` then declaration order, so the list is stable. */
private fun List<ThingTemplate>.certifications(): List<OfferedCertification> =
  sortedBy { it.sort_order }
    .flatMap { template ->
      template.certifications.map {
        OfferedCertification(
          templateId = template.id,
          roleLabel = template.display_name,
          def = it,
        )
      }
    }
    .distinctBy { it.key }

/** The kind a stored [key] names, or null when no installed template declares it. */
fun List<OfferedCertification>.forKey(key: String): OfferedCertification? =
  firstOrNull { it.key == key }

/**
 * The roles [this] holds, derived from the certifications present — never stored (PRD §8.6).
 *
 * Two consequences the design accepts rather than works around:
 *
 * **An uncertified person carries no role, and so no tag.** The neighbor who clears the gutters has
 * no credential to imply a domain, and inventing one would be inventing information the user never
 * gave. The behavior that falls out is the one you would have picked anyway — an uncertified helper
 * is offered on every Thing.
 *
 * **A certification whose template is not installed is untagged rather than an error.** A shared
 * technician can carry a key from a preset this build lacks.
 */
fun Technician.derivedRoles(known: List<OfferedCertification>): List<String> =
  resolvedCertifications()
    .mapNotNull { known.forKey(it.type)?.roleLabel }
    .distinct()
