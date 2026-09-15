package dev.fanfly.wingslog.feature.datalog.datamanager.impl

import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.template.specValue
import dev.fanfly.wingslog.feature.datalog.datamanager.ThingIdentifierLookup
import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.core.template.displayLabel
import dev.fanfly.wingslog.feature.datalog.datamanager.OtherThing
import dev.fanfly.wingslog.feature.datalog.datamanager.OtherThingLookup
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.id.ThingId
import kotlinx.coroutines.flow.first

/** The value of the template's `is_identifier` spec field — the tail number on an aeroplane. */
class TemplateThingIdentifierLookup(
  private val fleet: FleetManager,
  private val templates: TemplateRegistry,
) : ThingIdentifierLookup, OtherThingLookup {

  /** Every Thing the user can see, own or shared (design §7); the first whose identifier matches. */
  override suspend fun thingWithIdentifier(identity: String, excluding: ThingId): OtherThing? {
    if (identity.isBlank()) return null
    return fleet.observeFleetDashboard().first()
      .asSequence()
      .map { it.thing }
      .filter { it.id != excluding.value }
      .firstNotNullOfOrNull { thing ->
        val template = templates.forThingWithFallback(thing)
        val key = template.spec_fields.firstOrNull { it.is_identifier }?.key ?: return@firstNotNullOfOrNull null
        if (thing.specValue(key).equals(identity, ignoreCase = true)) OtherThing(ThingId(thing.id), thing.displayLabel(template))
        else null
      }
  }

  override suspend fun identifierOf(thingId: ThingId): String? {
    val thing = fleet.loadThing(thingId.value)
      .first() ?: return null
    val key =
      templates.forThingWithFallback(thing).spec_fields.firstOrNull { it.is_identifier }?.key
        ?: return null
    return thing.specValue(key)
      .takeIf { it.isNotBlank() }
  }
}
