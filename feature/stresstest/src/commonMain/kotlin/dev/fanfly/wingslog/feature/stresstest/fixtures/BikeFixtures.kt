package dev.fanfly.wingslog.feature.stresstest.fixtures

import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.thing.SquawkPriority.SQUAWK_PRIORITY_HIGH
import dev.fanfly.wingslog.thing.SquawkPriority.SQUAWK_PRIORITY_LOW
import dev.fanfly.wingslog.thing.SquawkPriority.SQUAWK_PRIORITY_MEDIUM

/** A bike pool, deliberately split across two meters so both get exercised. */
internal object BikeFixtures {

  private val TASKS = listOf(
    routine(
      "Chain Wear Check",
      meterRule(MeterKeys.ODOMETER, 500f),
      "Replace at 0.5% stretch, before it starts eating the cassette.",
    ),
    routine(
      "Drivetrain Service",
      meterRule(MeterKeys.RIDE_HOURS, 100f),
      "Degrease, re-lube, check derailleur alignment.",
    ),
    routine(
      "Brake Pad Inspection",
      meterRule(MeterKeys.ODOMETER, 1000f),
      "Replace under 1mm of pad material. Bed new pads in before the first descent.",
    ),
    routine(
      "Annual Tune-Up",
      months(12),
      "Bearing check, true the wheels, replace cables and housing.",
    ),
  )

  private val SQUAWKS = listOf(
    SquawkTemplate(
      "Chain skips under load",
      "Skips a tooth when standing on the pedals in the middle of the cassette. Chain has about 2,000 km on it.",
      SQUAWK_PRIORITY_MEDIUM,
    ),
    SquawkTemplate(
      "Rear brake lever feels spongy",
      "Lever pulls almost to the bar before the pads bite. Fine on the front. Air in the line or a worn pad.",
      SQUAWK_PRIORITY_HIGH,
    ),
    SquawkTemplate(
      "Creak from the bottom bracket",
      "Rhythmic creak once per pedal stroke, worse when climbing seated. Started after a wet ride.",
      SQUAWK_PRIORITY_LOW,
    ),
    SquawkTemplate(
      "Front wheel out of true",
      "Visible wobble at the rim; rubs the brake pad on one side every revolution.",
      SQUAWK_PRIORITY_LOW,
    ),
    SquawkTemplate(
      "Hesitant shifting into the smallest cog",
      "Rear derailleur takes two clicks to drop into the 11-tooth. Cable stretch or a bent hanger.",
      SQUAWK_PRIORITY_LOW,
    ),
    SquawkTemplate(
      "Play in the headset",
      "Knock felt through the bars when braking hard. Rocks fore and aft with the front brake held.",
      SQUAWK_PRIORITY_MEDIUM,
    ),
    SquawkTemplate(
      "Rear tire sealant dried out",
      "Loses pressure overnight and no longer seals small punctures. Sealant is over six months old.",
      SQUAWK_PRIORITY_LOW,
    ),
  )

  private val LOGS = listOf(
    LogTemplate(
      "Chain measured at 0.75% stretch and replaced along with the cassette; the old chain had worn the teeth to hooks.",
      taskHints = listOf("Chain Wear"),
    ),
    LogTemplate(
      "Drivetrain degreased, dried, and re-lubed with wet lube. Derailleur hanger checked with an alignment gauge and straightened.",
      taskHints = listOf("Drivetrain"),
    ),
    LogTemplate(
      "Brake pads inspected: front 1.8 mm, rear 0.8 mm. Rear pads replaced with sintered pads and bedded in on the road outside.",
      taskHints = listOf("Brake Pad"),
    ),
    LogTemplate(
      "Annual tune-up. Hub and headset bearings regreased, both wheels trued, shift cables and housing replaced, all bolts torque checked.",
      taskHints = listOf("Tune-Up", "Brake Pad", "Chain Wear"),
    ),
    LogTemplate(
      "Rear brake bled with mineral oil. Lever now firm at a third of its travel.",
    ),
    LogTemplate(
      "Bottom bracket removed, cleaned, and reinstalled with fresh grease and threadlocker on the cups. Creak gone.",
    ),
    LogTemplate(
      "Front wheel trued laterally and radially to within 0.3 mm. Spoke tensions evened out on the meter.",
    ),
    LogTemplate(
      "Headset preload adjusted; top cap and stem bolts torqued to 5 Nm. No play under a hard front brake.",
    ),
    LogTemplate(
      "Both tires refreshed with 60 ml of tubeless sealant each. Valve cores cleaned. Pressures holding at 28 PSI.",
    ),
    LogTemplate(
      "Rear derailleur indexed and B-tension set. Shifts clean through every cog on the stand and on the road.",
      taskHints = listOf("Drivetrain"),
    ),
  )

  val POOL = FakeDataPool(tasks = TASKS, squawks = SQUAWKS, logs = LOGS)
}
