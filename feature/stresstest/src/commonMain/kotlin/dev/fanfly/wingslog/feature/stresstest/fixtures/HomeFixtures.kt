package dev.fanfly.wingslog.feature.stresstest.fixtures

import dev.fanfly.wingslog.thing.SquawkPriority.SQUAWK_PRIORITY_HIGH
import dev.fanfly.wingslog.thing.SquawkPriority.SQUAWK_PRIORITY_LOW
import dev.fanfly.wingslog.thing.SquawkPriority.SQUAWK_PRIORITY_MEDIUM

/** A home pool. No meters at all, so everything here is calendar-driven. */
internal object HomeFixtures {

  private val TASKS = listOf(
    routine(
      "HVAC Filter Replacement",
      months(3),
      "MERV 11. Note the size on the filter housing so the next one is right.",
    ),
    routine(
      "Water Heater Flush",
      months(12),
      "Drain the sediment and check the anode rod while the tank is empty.",
    ),
    routine(
      "Gutter Cleaning",
      months(6),
      "Spring and late autumn. Check the downspout runs clear of the foundation.",
    ),
    routine(
      "Smoke Detector Batteries",
      months(12),
      "Test every unit. Replace the detector itself after ten years.",
    ),
    routine(
      "Dryer Vent Cleaning",
      months(12),
      "The whole run, not just the trap. Longer drying times are the tell.",
    ),
  )

  private val SQUAWKS = listOf(
    SquawkTemplate(
      "Kitchen faucet drips",
      "Steady drip from the spout with the handle fully off. Worn cartridge, most likely.",
      SQUAWK_PRIORITY_LOW,
    ),
    SquawkTemplate(
      "Furnace short-cycling",
      "Runs for two or three minutes, shuts off, restarts a few minutes later. House never quite reaches the set point.",
      SQUAWK_PRIORITY_HIGH,
    ),
    SquawkTemplate(
      "Garage GFCI will not reset",
      "The outlet by the workbench trips as soon as the reset button is pressed, with nothing plugged in.",
      SQUAWK_PRIORITY_MEDIUM,
    ),
    SquawkTemplate(
      "Water stain on the upstairs ceiling",
      "Brown ring about the size of a dinner plate under the bathroom, spreading after heavy rain.",
      SQUAWK_PRIORITY_HIGH,
    ),
    SquawkTemplate(
      "Garage door reverses before closing",
      "Door comes down most of the way, then reverses and the opener light blinks. Safety sensors misaligned or dirty.",
      SQUAWK_PRIORITY_MEDIUM,
    ),
    SquawkTemplate(
      "Sump pump runs continuously",
      "Pump has not shut off since the storm. Pit is nearly empty. Float switch stuck or the check valve failed.",
      SQUAWK_PRIORITY_HIGH,
    ),
    SquawkTemplate(
      "Bathroom exhaust fan rattles",
      "Loud rattle on start-up that settles into a hum. Louder than it used to be; motor bearing or a loose grille.",
      SQUAWK_PRIORITY_LOW,
    ),
  )

  private val LOGS = listOf(
    LogTemplate(
      "HVAC filter replaced with a 16x25x1 MERV 11. Old filter grey through. Return grille vacuumed.",
      taskHints = listOf("HVAC Filter"),
    ),
    LogTemplate(
      "Water heater flushed: about a cup of sediment out of the drain. Anode rod at half its diameter, replaced with a magnesium rod. Pressure relief valve tested.",
      taskHints = listOf("Water Heater"),
    ),
    LogTemplate(
      "Gutters cleared of leaves and shingle grit on all four sides. Downspouts flushed and the rear one re-secured to the fascia.",
      taskHints = listOf("Gutter"),
    ),
    LogTemplate(
      "All six smoke detectors tested; batteries replaced. Hallway unit past its ten-year date and replaced with a sealed ten-year model.",
      taskHints = listOf("Smoke Detector"),
    ),
    LogTemplate(
      "Dryer vent cleaned end to end with a rotary brush. Removed roughly two gallons of lint. Exterior flap freed and moving.",
      taskHints = listOf("Dryer Vent"),
    ),
    LogTemplate(
      "Kitchen faucet cartridge replaced. Aerator descaled while it was apart. No drip after 24 hours.",
    ),
    LogTemplate(
      "Furnace flame sensor cleaned with emery cloth and the condensate trap cleared. Ran a full 15-minute cycle without shutting down.",
      taskHints = listOf("HVAC Filter"),
    ),
    LogTemplate(
      "Garage GFCI replaced. Downstream outdoor outlet found full of water behind a cracked cover; cover replaced and load re-tested.",
    ),
    LogTemplate(
      "Ceiling stain traced to a failed boot around the bathroom vent stack. New boot and flashing installed, ceiling patched and painted.",
    ),
    LogTemplate(
      "Garage door safety sensors cleaned and re-aligned; bracket tightened. Door closes fully on the first try.",
    ),
    LogTemplate(
      "Sump pump check valve replaced and the float arm freed. Pump now cycles normally and shuts off with the pit empty.",
    ),
    LogTemplate(
      "Bathroom exhaust fan motor replaced and the grille clips reseated. Quiet at start-up.",
    ),
  )

  val POOL = FakeDataPool(tasks = TASKS, squawks = SQUAWKS, logs = LOGS)
}
