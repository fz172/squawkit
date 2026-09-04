package dev.fanfly.wingslog.feature.stresstest.fixtures

import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.SquawkPriority.SQUAWK_PRIORITY_AOG
import dev.fanfly.wingslog.thing.SquawkPriority.SQUAWK_PRIORITY_HIGH
import dev.fanfly.wingslog.thing.SquawkPriority.SQUAWK_PRIORITY_LOW
import dev.fanfly.wingslog.thing.SquawkPriority.SQUAWK_PRIORITY_MEDIUM

/**
 * A car pool, scheduled in miles. Until it existed the generator handed a car the aviation pool,
 * so nothing anyone dogfooded ever exercised a keyed rule and every fake log recorded aeroplane
 * hours a car has no meter for.
 */
internal object AutomotiveFixtures {

  private val TASKS = listOf(
    routine(
      "Oil and Filter Change",
      meterRule(MeterKeys.ODOMETER, 5000f),
      "Full synthetic 0W-20. Reset the maintenance minder afterwards.",
      ComponentType.COMPONENT_ENGINE,
    ),
    routine(
      "Tire Rotation",
      meterRule(MeterKeys.ODOMETER, 7500f),
      "Front to back, same side. Check pressures cold and inspect for uneven wear.",
    ),
    routine(
      "Brake Inspection",
      meterRule(MeterKeys.ODOMETER, 15000f),
      "Measure pad thickness and rotor runout. Flush fluid every three years regardless.",
    ),
    routine(
      "Engine Air Filter",
      meterRule(MeterKeys.ODOMETER, 30000f),
      "Sooner in dusty conditions. Inspect the cabin filter at the same time.",
      ComponentType.COMPONENT_ENGINE,
    ),
    routine(
      "State Safety Inspection",
      months(12),
      "Sticker expires at the end of the month. Bring registration and proof of insurance.",
    ),
    routine(
      "Registration Renewal",
      months(12),
      "Renew online. Emissions test required in some counties.",
    ),
  )

  private val SQUAWKS = listOf(
    SquawkTemplate(
      "Check engine light on",
      "Steady check engine light since a cold start on Tuesday. No change in how it drives. Code not read yet.",
      SQUAWK_PRIORITY_MEDIUM,
    ),
    SquawkTemplate(
      "Front left tire losing pressure",
      "Down about 3 PSI a week. No nail or visible damage; slow leak at the bead or valve stem suspected.",
      SQUAWK_PRIORITY_LOW,
    ),
    SquawkTemplate(
      "Brake squeal at low speed",
      "High-pitched squeal in the last few feet of every stop. Pedal feel unchanged. Wear indicators likely touching.",
      SQUAWK_PRIORITY_LOW,
    ),
    SquawkTemplate(
      "Slow crank on cold mornings",
      "Starter turns over slowly below freezing, then catches. Battery is the original one, about five years old.",
      SQUAWK_PRIORITY_MEDIUM,
    ),
    SquawkTemplate(
      "Coolant smell after highway driving",
      "Sweet smell from under the hood after a long drive. No puddle yet, but the reservoir level dropped a little.",
      SQUAWK_PRIORITY_HIGH,
    ),
    SquawkTemplate(
      "Steering wheel shimmy at highway speed",
      "Vibration through the wheel between 60 and 70 mph, smooth above and below. Front balance or a bent wheel.",
      SQUAWK_PRIORITY_LOW,
    ),
    SquawkTemplate(
      "Oil spot under the engine",
      "Fresh drip on the garage floor under the front of the engine each morning. Level still on the dipstick.",
      SQUAWK_PRIORITY_HIGH,
    ),
    SquawkTemplate(
      "Will not start after refuel",
      "Cranks but does not fire after filling up. Towed home. Fuel pump or a flooded evap purge valve suspected.",
      SQUAWK_PRIORITY_AOG,
    ),
  )

  private val LOGS = listOf(
    LogTemplate(
      "Oil and filter changed. Drained 4.8 qt of 0W-20 full synthetic, new OEM filter, drain plug washer replaced. Maintenance minder reset.",
      taskHints = listOf("Oil and Filter"),
    ),
    LogTemplate(
      "Tires rotated front to back. Pressures set to 35 PSI cold all round. Even wear on all four; tread at 6/32 in.",
      taskHints = listOf("Tire Rotation"),
    ),
    LogTemplate(
      "Brake inspection. Front pads 5 mm, rear pads 7 mm, rotors within runout limits. Fluid clear, no flush needed this time.",
      taskHints = listOf("Brake Inspection"),
    ),
    LogTemplate(
      "Engine air filter replaced. Old element dark with fine dust. Cabin filter inspected and replaced at the same time.",
      taskHints = listOf("Engine Air Filter"),
    ),
    LogTemplate(
      "State safety inspection passed. Lights, horn, wipers, brakes, and emissions all checked. New sticker on the windshield.",
      taskHints = listOf("State Safety"),
    ),
    LogTemplate(
      "Registration renewed online for another year. New decal applied to the rear plate.",
      taskHints = listOf("Registration"),
    ),
    LogTemplate(
      "Front left tire dismounted. Corroded bead seat cleaned and sealed, valve stem replaced. Holding pressure after 48 hours.",
    ),
    LogTemplate(
      "Front brake pads and rotors replaced. Caliper slide pins cleaned and greased. Bedded in over ten firm stops.",
      taskHints = listOf("Brake Inspection"),
    ),
    LogTemplate(
      "Battery load tested at 310 CCA against a 600 CCA rating. Replaced with a group 24F AGM. Terminals cleaned and coated.",
    ),
    LogTemplate(
      "Check engine light diagnosed: P0420 catalyst efficiency. Downstream oxygen sensor replaced, code cleared, no return after 200 miles.",
    ),
    LogTemplate(
      "Coolant leak traced to a weeping upper radiator hose clamp. Hose and both clamps replaced, system bled, pressure tested at 16 PSI.",
    ),
    LogTemplate(
      "All four wheels balanced. Front right needed 1.25 oz. Shimmy gone on a highway test drive.",
    ),
    LogTemplate(
      "Valve cover gasket replaced to stop the oil drip. Spark plug tube seals done at the same time. Engine degreased and rechecked dry after a week.",
    ),
    LogTemplate(
      "No-start after refuel traced to a stuck-open evap purge valve flooding the intake. Valve replaced and started first try.",
    ),
  )

  val POOL = FakeDataPool(tasks = TASKS, squawks = SQUAWKS, logs = LOGS)
}
