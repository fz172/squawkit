package dev.fanfly.wingslog.feature.stresstest.fixtures

import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.SquawkPriority.SQUAWK_PRIORITY_AOG
import dev.fanfly.wingslog.thing.SquawkPriority.SQUAWK_PRIORITY_HIGH
import dev.fanfly.wingslog.thing.SquawkPriority.SQUAWK_PRIORITY_LOW
import dev.fanfly.wingslog.thing.SquawkPriority.SQUAWK_PRIORITY_MEDIUM

/** A boat pool. Engine hours, which is the same shape as aviation but under a keyed rule. */
internal object BoatFixtures {

  private val TASKS = listOf(
    routine(
      "Engine Oil Change",
      meterRule(MeterKeys.ENGINE_HOURS, 100f),
      "Change the gear oil at the same interval. Check for water intrusion in the sample.",
      ComponentType.COMPONENT_ENGINE,
    ),
    routine(
      "Impeller Replacement",
      meterRule(MeterKeys.ENGINE_HOURS, 200f),
      "Annually regardless of hours. Count the vanes — a missing one is downstream.",
      ComponentType.COMPONENT_ENGINE,
    ),
    routine(
      "Zinc Anode Inspection",
      months(6),
      "Replace at 50% erosion. Never paint them.",
    ),
    routine(
      "Hull Cleaning and Inspection",
      months(6),
      "Check through-hulls and the running gear while she is out.",
    ),
  )

  private val SQUAWKS = listOf(
    SquawkTemplate(
      "Bilge pump cycling at the dock",
      "Pump runs for a few seconds every ten minutes with the boat sitting still. Water coming in from somewhere slow.",
      SQUAWK_PRIORITY_MEDIUM,
    ),
    SquawkTemplate(
      "Overheat alarm at cruise",
      "Temperature alarm sounds after twenty minutes above 3,000 RPM. Fine at idle. Weak raw-water flow at the exhaust.",
      SQUAWK_PRIORITY_HIGH,
    ),
    SquawkTemplate(
      "Navigation lights intermittent",
      "Bow light flickers and drops out in a chop. Stern light fine. Corroded socket or a chafed wire in the pulpit.",
      SQUAWK_PRIORITY_MEDIUM,
    ),
    SquawkTemplate(
      "Steering stiff to starboard",
      "Wheel takes real effort turning right, easy turning left. Cable end or the engine tilt tube binding.",
      SQUAWK_PRIORITY_MEDIUM,
    ),
    SquawkTemplate(
      "Fuel smell in the engine compartment",
      "Strong gasoline smell when the hatch is opened. No visible pooling. Do not run until found.",
      SQUAWK_PRIORITY_AOG,
    ),
    SquawkTemplate(
      "Vibration above 3,000 RPM",
      "Hull buzz that builds with revs and eases when the throttle comes back. Prop ding or a bent blade suspected.",
      SQUAWK_PRIORITY_LOW,
    ),
    SquawkTemplate(
      "House battery not holding charge",
      "Reads 12.6 V after charging and is down to 11.9 V by morning with only the anchor light on.",
      SQUAWK_PRIORITY_MEDIUM,
    ),
  )

  private val LOGS = listOf(
    LogTemplate(
      "Engine oil and filter changed at 100 hours, 4.5 qt 10W-30. Gear lube drained: clean, no milkiness. Refilled and vented.",
      taskHints = listOf("Oil Change"),
    ),
    LogTemplate(
      "Raw-water impeller replaced. Old one had two cracked vanes, both recovered from the heat exchanger inlet. Cover gasket replaced.",
      taskHints = listOf("Impeller"),
    ),
    LogTemplate(
      "Zinc anodes inspected. Shaft zinc at 60% eroded and replaced; trim tab zincs still serviceable.",
      taskHints = listOf("Zinc Anode"),
    ),
    LogTemplate(
      "Hauled for hull cleaning. Pressure washed, two coats of ablative antifouling. Through-hulls exercised, cutless bearing checked for play.",
      taskHints = listOf("Hull Cleaning", "Zinc Anode"),
    ),
    LogTemplate(
      "Bilge water traced to a weeping stuffing box. Packing adjusted to two drips a minute while running. Float switch tested.",
    ),
    LogTemplate(
      "Overheat traced to a scaled heat exchanger. Descaled with Barnacle Buster, thermostat replaced. Holds 165°F at cruise.",
      taskHints = listOf("Impeller"),
    ),
    LogTemplate(
      "Bow navigation light socket replaced and wiring re-run through the pulpit with heat-shrink butt connectors. Tested on all circuits.",
    ),
    LogTemplate(
      "Steering cable pulled from the tilt tube, tube cleaned of corrosion and greased. Cable end lubricated. Wheel even both ways.",
    ),
    LogTemplate(
      "Fuel smell traced to a cracked primer bulb. Bulb and both fuel line clamps replaced, lines pressure tested. Compartment vented and clear.",
    ),
    LogTemplate(
      "Propeller removed and sent out for reconditioning after a dinged blade. Reinstalled with a new thrust washer and cotter pin. Vibration gone.",
    ),
    LogTemplate(
      "House battery load tested and failed. Replaced with a Group 31 deep-cycle. Charger output verified at 14.4 V absorption.",
    ),
  )

  val POOL = FakeDataPool(tasks = TASKS, squawks = SQUAWKS, logs = LOGS)
}
