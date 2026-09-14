package dev.fanfly.wingslog.feature.datalog.datamanager.impl

import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.template.specValue
import dev.fanfly.wingslog.feature.datalog.datamanager.ThingIdentifierLookup
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.id.ThingId
import kotlinx.coroutines.flow.first

/** The value of the template's `is_identifier` spec field — the tail number on an aeroplane. */
class TemplateThingIdentifierLookup(
  private val fleet: FleetManager,
  private val templates: TemplateRegistry,
) : ThingIdentifierLookup {
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
