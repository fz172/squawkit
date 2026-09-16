package dev.fanfly.wingslog.feature.datalog.datamanager.impl

import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.template.displayLabel
import dev.fanfly.wingslog.core.template.specValue
import dev.fanfly.wingslog.feature.datalog.datamanager.OtherThing
import dev.fanfly.wingslog.feature.datalog.datamanager.OtherThingLookup
import dev.fanfly.wingslog.feature.datalog.datamanager.ThingIdentifierLookup
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.thing.ThingTemplate
import kotlinx.coroutines.flow.first

/** The value of the spec field that names the Thing — the tail number on an aeroplane. */
class TemplateThingIdentifierLookup(
  private val fleet: FleetManager,
  private val templates: TemplateRegistry,
) : ThingIdentifierLookup, OtherThingLookup {

  /** Every Thing the user can see, own or shared (design §7); the first whose identifier matches. */
  override suspend fun thingWithIdentifier(
    identity: String,
    excluding: ThingId
  ): OtherThing? {
    if (identity.isBlank()) return null
    return fleet.observeFleetDashboard()
      .first()
      .asSequence()
      .map { it.thing }
      .filter { it.id != excluding.value }
      .firstNotNullOfOrNull { thing ->
        val template = templates.forThingWithFallback(thing)
        val key = template.identifierKey() ?: return@firstNotNullOfOrNull null
        if (thing.specValue(key)
            .equals(identity, ignoreCase = true)
        ) OtherThing(ThingId(thing.id), thing.displayLabel(template))
        else null
      }
  }

  override suspend fun identifierOf(thingId: ThingId): String? {
    val thing = fleet.loadThing(thingId.value)
      .first() ?: return null
    val key = templates.forThingWithFallback(thing)
      .identifierKey() ?: return null
    return thing.specValue(key)
      .takeIf { it.isNotBlank() }
  }

  /**
   * The field a recorder's `aircraft_ident` is compared against: the template's `title_candidate`
   * first, and only then whichever field happens to be marked `is_identifier`.
   *
   * **`is_identifier` is a typography flag, not an identity.** Its own proto comment calls it a
   * readability hint — it means "render this in a monospace face", and an airplane sets it on both
   * the serial number and the tail number. Taking the first one declared compared the tail number a
   * Garmin records against the *airframe serial*, so a log from the aeroplane it was recorded on
   * raised a mismatch. `title_candidate` is the flag that says which value the owner calls the
   * thing by, and it exists because the Thing switcher hit this same trap first.
   */
  private fun ThingTemplate.identifierKey(): String? =
    spec_fields.firstOrNull { it.title_candidate }?.key
      ?: spec_fields.firstOrNull { it.is_identifier }?.key
}
