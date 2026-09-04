package dev.fanfly.wingslog.feature.stresstest.fixtures

import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.thing.ComplianceType
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.ImmediateRule
import dev.fanfly.wingslog.thing.InspectionRule
import dev.fanfly.wingslog.thing.OnConditionRule
import dev.fanfly.wingslog.thing.SquawkPriority
import dev.fanfly.wingslog.thing.TimeRule

/** The aviation pool. The one fixture with real Things behind it, so it is also the richest. */
internal object AirplaneFixtures {

  private val TASKS = listOf(
    TaskTemplate(
      "Annual Inspection",
      ComponentType.COMPONENT_AIRFRAME,
      ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
      InspectionRule(time_rule = TimeRule(interval_months = 12)),
      notes = "FAR 43 Appendix D. Must be performed by A&P with IA or certified repair station.",
    ),
    TaskTemplate(
      "100-Hour Inspection",
      ComponentType.COMPONENT_ENGINE,
      ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
      meterRule(MeterKeys.ENGINE_HOURS, 100f),
      notes = "Required for hire operations. FAR 91.409(b). Follows Annual inspection checklist.",
    ),
    TaskTemplate(
      "Engine Oil Change",
      ComponentType.COMPONENT_ENGINE,
      ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
      meterRule(MeterKeys.ENGINE_HOURS, 50f),
      notes = "Replace oil filter and send sample for analysis. Use AeroShell W80 Plus or equivalent.",
    ),
    TaskTemplate(
      "Spark Plug Rotation",
      ComponentType.COMPONENT_ENGINE,
      ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
      meterRule(MeterKeys.ENGINE_HOURS, 100f),
      notes = "Rotate top to bottom. Check gap 0.015–0.019 in. Replace if electrodes worn more than 50%.",
    ),
    TaskTemplate(
      "ELT Battery Replacement",
      ComponentType.COMPONENT_AIRFRAME,
      ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
      InspectionRule(time_rule = TimeRule(interval_months = 24)),
      notes = "Replace when 50% cumulative battery life used OR after any activation. FAR 91.207(c).",
    ),
    TaskTemplate(
      "Pitot-Static System Check",
      ComponentType.COMPONENT_AIRFRAME,
      ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
      InspectionRule(time_rule = TimeRule(interval_months = 24)),
      notes = "FAR 91.411. Required for IFR operations. Altimeter, VSI, and ASI. Log date of test.",
    ),
    TaskTemplate(
      "Transponder Certification",
      ComponentType.COMPONENT_AIRFRAME,
      ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
      InspectionRule(time_rule = TimeRule(interval_months = 24)),
      notes = "FAR 91.413. Modes A, C, and S. Must be performed by certificated repair station.",
    ),
    TaskTemplate(
      "VOR Operational Check",
      ComponentType.COMPONENT_AIRFRAME,
      ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
      InspectionRule(time_rule = TimeRule(interval_days = 30)),
      notes = "FAR 91.171. Required for IFR flight. Max ±4° from ground check or ±6° from airborne.",
    ),
    TaskTemplate(
      "Propeller Overhaul",
      ComponentType.COMPONENT_PROPELLER,
      ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
      InspectionRule(time_rule = TimeRule(interval_years = 5)),
      notes = "Send to FAA-certified prop shop. Factory TBO is 5 years or 2,000 hours, whichever first.",
    ),
    TaskTemplate(
      "Engine TBO",
      ComponentType.COMPONENT_ENGINE,
      ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
      meterRule(MeterKeys.ENGINE_HOURS, 2000f),
      notes = "Manufacturer recommended TBO. Not mandatory for Part 91, but highly recommended.",
    ),
    TaskTemplate(
      "Alternator Belt Inspection",
      ComponentType.COMPONENT_ENGINE,
      ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
      meterRule(MeterKeys.ENGINE_HOURS, 100f),
      notes = "Check tension, fraying, and cracking. Replace if belt deflects more than 1/2 inch.",
    ),
    TaskTemplate(
      "Magneto Timing Check",
      ComponentType.COMPONENT_ENGINE,
      ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
      meterRule(MeterKeys.ENGINE_HOURS, 500f),
      notes = "Check timing at 25° BTC ±1°. Inspect points, condenser, and distributor block.",
    ),
    TaskTemplate(
      "Avionics Database Update",
      ComponentType.COMPONENT_AIRFRAME,
      ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
      InspectionRule(time_rule = TimeRule(interval_days = 28)),
      notes = "Garmin 28-day nav database cycle. Required for IFR approaches. Update terrain/obstacles annually.",
    ),
    TaskTemplate(
      "Fuel System Inspection",
      ComponentType.COMPONENT_AIRFRAME,
      ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
      InspectionRule(time_rule = TimeRule(interval_months = 12)),
      notes = "Inspect tanks, lines, valves, drains, and fuel selector. Clean finger strainer.",
    ),
    TaskTemplate(
      "AD 2019-09-11: Seat Rail Inspection",
      ComponentType.COMPONENT_AIRFRAME,
      ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE,
      InspectionRule(time_rule = TimeRule(interval_months = 12)),
      referenceNumber = "AD 2019-09-11",
      complianceAuthority = "FAA",
      complianceDetails = "Inspect seat tracks and stop bolts for cracks. Replace if crack found.",
      notes = "Mandatory recurrent. Applies to specified S/N range. See AD for applicability.",
    ),
    TaskTemplate(
      "SB 39-2018-01: Carburetor Heat System",
      ComponentType.COMPONENT_ENGINE,
      ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN,
      InspectionRule(on_condition_rule = OnConditionRule(description = "Inspect when carb heat effectiveness is reduced or upon annual inspection.")),
      referenceNumber = "SB-39-2018-01",
      complianceAuthority = "Manufacturer",
      complianceDetails = "Inspect heat muff and duct for cracks or loose clamps. Replace as needed.",
      notes = "Manufacturer service bulletin. Recommended at annual or upon reduced carb heat effectiveness.",
    ),
    TaskTemplate(
      "AD 2022-15-03: Fuel Cap Seal Inspection",
      ComponentType.COMPONENT_AIRFRAME,
      ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE,
      InspectionRule(immediate_rule = ImmediateRule()),
      referenceNumber = "AD 2022-15-03",
      complianceAuthority = "FAA",
      complianceDetails = "Replace fuel cap seal O-ring per kit P/N AN6227-11A. One-time compliance.",
      notes = "One-time AD. Inspect and replace both fuel cap O-rings.",
      isOneTime = true,
    ),
    TaskTemplate(
      "Brake System Service",
      ComponentType.COMPONENT_AIRFRAME,
      ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
      meterRule(MeterKeys.ENGINE_HOURS, 100f),
      notes = "Check fluid level, inspect lines and calipers, measure pad thickness. Flush fluid annually.",
    ),
    TaskTemplate(
      "Control Cable Tension Check",
      ComponentType.COMPONENT_AIRFRAME,
      ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
      InspectionRule(time_rule = TimeRule(interval_months = 12)),
      notes = "Check all primary flight control cable tensions per rigging chart. Adjust as needed.",
    ),
    TaskTemplate(
      "Stall Warning System Check",
      ComponentType.COMPONENT_AIRFRAME,
      ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
      InspectionRule(time_rule = TimeRule(interval_months = 12)),
      notes = "Verify stall warning activates 5–10 kts above stall. Clean vane and check wiring.",
    ),
  )

  private val SQUAWKS = listOf(
    SquawkTemplate(
      "Left landing light inoperative",
      "Landing light bulb burned out on left main gear. Navigation light functioning normally. Right landing light OK.",
      SquawkPriority.SQUAWK_PRIORITY_LOW,
      ComponentType.COMPONENT_AIRFRAME,
    ),
    SquawkTemplate(
      "Oil filler cap O-ring deteriorated",
      "O-ring on oil filler cap is cracked and has lost elasticity. No leakage observed but replacement recommended before next flight.",
      SquawkPriority.SQUAWK_PRIORITY_MEDIUM,
      ComponentType.COMPONENT_ENGINE,
    ),
    SquawkTemplate(
      "Left brake dragging on rollout",
      "Left main wheel brake exhibits slight drag during landing rollout and slow taxi. Consistent across three flights. Brake caliper or shimmy dampener suspected.",
      SquawkPriority.SQUAWK_PRIORITY_LOW,
      ComponentType.COMPONENT_AIRFRAME,
    ),
    SquawkTemplate(
      "COM1 intermittent static above FL080",
      "COM1 radio develops intermittent static and occasional dropout above 8,000 ft MSL. COM2 unaffected. Issue began approximately 20 flight hours ago.",
      SquawkPriority.SQUAWK_PRIORITY_MEDIUM,
      ComponentType.COMPONENT_AIRFRAME,
    ),
    SquawkTemplate(
      "Propeller blade nick – leading edge",
      "Small nick found on leading edge of propeller blade #1 approximately 4 inches from tip during preflight. Estimated depth 0.040 inches. Flight restricted pending inspection.",
      SquawkPriority.SQUAWK_PRIORITY_HIGH,
      ComponentType.COMPONENT_PROPELLER,
    ),
    SquawkTemplate(
      "Engine won't start – fuel issue suspected",
      "Engine fails to start after repeated attempts. Fuel pressure reads normal. Starter engagement confirmed. Vapor lock or possible contaminated fuel suspected.",
      SquawkPriority.SQUAWK_PRIORITY_AOG, ComponentType.COMPONENT_ENGINE,
    ),
    SquawkTemplate(
      "Static wick missing – left aileron",
      "Static wick found missing from left aileron trailing edge. All other static wicks intact. No precipitation static issues reported in flight.",
      SquawkPriority.SQUAWK_PRIORITY_LOW,
      ComponentType.COMPONENT_AIRFRAME,
    ),
    SquawkTemplate(
      "ELT inadvertent activation",
      "ELT activated during hard landing. ATC notified and activation cancelled. Battery replaced. Unit requires inspection, testing, and recertification before return to service.",
      SquawkPriority.SQUAWK_PRIORITY_HIGH,
      ComponentType.COMPONENT_AIRFRAME,
    ),
    SquawkTemplate(
      "Right rear door seal air leak",
      "Right rear passenger door seal leaking air at cruise altitude. Cabin noise level noticeably increased above 100 KIAS. Door closes and latches properly.",
      SquawkPriority.SQUAWK_PRIORITY_LOW,
      ComponentType.COMPONENT_AIRFRAME,
    ),
    SquawkTemplate(
      "Transponder Mode C altitude error",
      "Transponder not squawking correct altitude in Mode C. Altimeter reading matches actual altitude. Altitude encoder is suspected to be out of calibration.",
      SquawkPriority.SQUAWK_PRIORITY_MEDIUM,
      ComponentType.COMPONENT_AIRFRAME,
    ),
    SquawkTemplate(
      "Exhaust stack crack at cylinder #3",
      "Small crack found in exhaust stack at cylinder #3 flange. Carbon deposits visible around crack. Elevated CO risk in cabin. Aircraft grounded pending repair.",
      SquawkPriority.SQUAWK_PRIORITY_HIGH, ComponentType.COMPONENT_ENGINE,
    ),
    SquawkTemplate(
      "Oil temperature consistently high",
      "Oil temperature reaching upper yellow arc at cruise power settings. Oil level checked normal. Oil cooler baffling or thermostat suspected.",
      SquawkPriority.SQUAWK_PRIORITY_HIGH, ComponentType.COMPONENT_ENGINE,
    ),
    SquawkTemplate(
      "Nose gear shimmy on touchdown",
      "Nose gear shimmy onset above 60 KIAS on touchdown. Shimmy diminishes as aircraft slows below 40 KIAS. Shimmy dampener service likely needed.",
      SquawkPriority.SQUAWK_PRIORITY_MEDIUM,
      ComponentType.COMPONENT_AIRFRAME,
    ),
    SquawkTemplate(
      "Avionics master CB intermittent trip",
      "Avionics master circuit breaker trips intermittently during engine start sequence. CB resets and holds after one attempt. Potential wiring short or overloaded circuit.",
      SquawkPriority.SQUAWK_PRIORITY_MEDIUM,
      ComponentType.COMPONENT_AIRFRAME,
    ),
    SquawkTemplate(
      "Fuel cap O-ring pilot side",
      "Pilot-side fuel cap O-ring slightly compressed and beginning to lose seating. No leakage confirmed, but preventive replacement recommended per AD 2022-15-03.",
      SquawkPriority.SQUAWK_PRIORITY_LOW,
      ComponentType.COMPONENT_AIRFRAME,
    ),
  )

  private val LOGS = listOf(
    // AIRFRAME
    LogTemplate(
      "Annual inspection completed per FAR 43 Appendix D. All airframe structures, flight controls, landing gear, and systems inspected. Airworthiness Directive status current. Logbook entries made. Aircraft found airworthy and returned to service.",
      ComponentType.COMPONENT_AIRFRAME,
      taskHints = listOf(
        "Annual",
        "Seat Rail",
        "Fuel System",
        "Control Cable"
      ),
    ),
    LogTemplate(
      "Replaced pilot-side seat belt and shoulder harness assembly. Old hardware showed UV degradation and webbing fraying. New assembly P/N Aero-520-013 installed, torqued, and inspected. Returned to service.",
      ComponentType.COMPONENT_AIRFRAME,
    ),
    LogTemplate(
      "Replaced windshield. Original windshield showed haze, crazing, and minor de-lamination. New windshield installed and sealed per MM 56-10-00. Integrity verified. No leaks.",
      ComponentType.COMPONENT_AIRFRAME,
    ),
    LogTemplate(
      "Control surface lubrication per MM section 12-20. All hinges, bearings, and pivot points lubricated with MIL-G-81322 grease. Cable tensions checked within limits.",
      ComponentType.COMPONENT_AIRFRAME,
      taskHints = listOf("Control Cable"),
    ),
    LogTemplate(
      "Replaced left main gear landing light bulb. P/N GE4596. Bulb tested and verified prior to reinstall. All other lights confirmed operational.",
      ComponentType.COMPONENT_AIRFRAME,
    ),
    LogTemplate(
      "Replaced right rear door seal. Old seal compressed flat and no longer sealing. New seal P/N MC-SE-002 installed. Door verified fully sealed at all airspeeds during test flight.",
      ComponentType.COMPONENT_AIRFRAME,
    ),
    LogTemplate(
      "Replaced missing static wick on left aileron trailing edge. P/N Av-SW-003. All eight static wicks confirmed installed and secure.",
      ComponentType.COMPONENT_AIRFRAME,
    ),
    LogTemplate(
      "Nose gear shimmy dampener serviced. Unit removed, disassembled, seals replaced, fluid replenished, and reassembled per MM 32-40. Reinstalled and taxi tested. No shimmy observed at any speed.",
      ComponentType.COMPONENT_AIRFRAME,
    ),
    LogTemplate(
      "Pitot-static system check per FAR 91.411. Altimeter, VSI, and ASI calibrated and tested. Encoder agrees with altimeter within 75 ft at 10,000 ft. All instruments within IFR tolerances. System tight – no leaks. Good for 24 months.",
      ComponentType.COMPONENT_AIRFRAME,
      taskHints = listOf("Pitot-Static"),
    ),
    LogTemplate(
      "AD 2019-09-11 compliance: Seat rail inspection completed. All four seat rails inspected per AD instructions. No cracks found. Stop bolts present and secure. AD complied with.",
      ComponentType.COMPONENT_AIRFRAME,
      taskHints = listOf("Seat Rail"),
    ),
    LogTemplate(
      "Fuel cap O-ring replacement per AD 2022-15-03. Replaced O-rings on both fuel caps (pilot and co-pilot) with AN6227-11A. Caps tested: no leakage. AD one-time compliance complete.",
      ComponentType.COMPONENT_AIRFRAME,
      taskHints = listOf("Fuel Cap Seal"),
    ),
    LogTemplate(
      "Brake system service. Inspected calipers, brake pads, and lines. Left caliper pistons stuck – rebuilt per MM. Fluid flushed and replaced. Brake action tested and confirmed firm on both sides.",
      ComponentType.COMPONENT_AIRFRAME,
      taskHints = listOf("Brake System"),
    ),
    LogTemplate(
      "Stall warning system check. Vane cleaned and pivot lubricated. System activates at 5 kts above published stall speed. Wiring inspected – no chafing. System functional.",
      ComponentType.COMPONENT_AIRFRAME,
      taskHints = listOf("Stall Warning"),
    ),
    LogTemplate(
      "Control cable tension check per rigging chart. Aileron cables within tolerance. Left elevator cable tension 30 lb – adjusted to 32 lb per spec. Rudder cables nominal.",
      ComponentType.COMPONENT_AIRFRAME,
      taskHints = listOf("Control Cable"),
    ),
    LogTemplate(
      "Fuel system inspection. Tanks sump-drained. Selector valve operated all positions. Finger strainer cleaned. Gascolator drained and bowl cleaned. Fuel lines and vents inspected – no leaks.",
      ComponentType.COMPONENT_AIRFRAME,
      taskHints = listOf("Fuel System"),
    ),
    // ENGINE
    LogTemplate(
      "Engine oil change at 50-hour interval. Drained 12 qt AeroShell W80 Plus. New Tempest AA48109 oil filter installed and safety-wired. Oil analysis sample sent to AVLAB. System refilled.",
      ComponentType.COMPONENT_ENGINE,
      taskHints = listOf("Oil Change"),
    ),
    LogTemplate(
      "100-hour inspection completed. Engine oil changed, spark plugs cleaned/gapped/rotated (top to bottom), magneto timing verified 25° BTC, compression check: 78/80, 76/80, 79/80, 77/80. All within service limits.",
      ComponentType.COMPONENT_ENGINE,
      taskHints = listOf(
        "100-Hour",
        "Oil Change",
        "Spark Plug",
        "Magneto"
      ),
    ),
    LogTemplate(
      "Spark plugs cleaned, gapped to 0.017 in, and rotated top-to-bottom. All four plugs show normal wear patterns. No fouling or lead deposits. Anti-seize applied to threads.",
      ComponentType.COMPONENT_ENGINE,
      taskHints = listOf("Spark Plug"),
    ),
    LogTemplate(
      "Magneto timing check and adjustment. Left mag: 24.5° BTC → adjusted to 25.0° BTC. Right mag: 25.0° BTC – no adjustment needed. Both within ±1° tolerance. Points and condensers inspected serviceable.",
      ComponentType.COMPONENT_ENGINE,
      taskHints = listOf("Magneto"),
    ),
    LogTemplate(
      "Carburetor removed, disassembled, cleaned, and calibrated per MM 73-10-02. Float level set to 7/8 in. All jets cleaned. Bowl O-ring replaced. Reinstalled and ground run performed – smooth idle, no stumble.",
      ComponentType.COMPONENT_ENGINE,
      taskHints = listOf("Carburetor Heat"),
    ),
    LogTemplate(
      "Engine compression check performed. Results: Cyl 1: 76/80, Cyl 2: 78/80, Cyl 3: 79/80, Cyl 4: 75/80. All cylinders within FAA-approved service limits. No cylinder removal required.",
      ComponentType.COMPONENT_ENGINE,
    ),
    LogTemplate(
      "Oil cooler serviced. Removed, back-flushed with MEK, inspected for cracks – none found. Reinstalled with new O-rings and gaskets. Pressure tested to 80 PSI. No leaks.",
      ComponentType.COMPONENT_ENGINE,
    ),
    LogTemplate(
      "Exhaust stack crack repaired. Cylinder #3 exhaust stack removed. Crack welded by certified welder per field approval A-4521. CO inspection post-repair – no leaks. Engine run-up completed without issues.",
      ComponentType.COMPONENT_ENGINE,
    ),
    LogTemplate(
      "Oil temperature issue investigated. Thermostat valve replaced (P/N: 72534). Oil cooler baffling readjusted to increase airflow. Ground test: oil temp stable in green arc at cruise power.",
      ComponentType.COMPONENT_ENGINE,
    ),
    LogTemplate(
      "Alternator belt tension checked and adjusted. Belt deflects 3/8 in under 5 lb force – within spec (1/4 – 1/2 in). No cracks or fraying observed. Alternator output: 28.1 V at 1,200 RPM.",
      ComponentType.COMPONENT_ENGINE,
      taskHints = listOf("Alternator Belt"),
    ),
    LogTemplate(
      "Fuel injector cleaning and flow balance. All injectors removed and ultrasonically cleaned. Flow rates measured and found within 3% of each other. Reinstalled and engine run confirmed smooth at all power settings.",
      ComponentType.COMPONENT_ENGINE,
    ),
    LogTemplate(
      "Engine overhaul completed (TBO). Engine removed and sent to Mattituck Aviation for factory overhaul. Overhauled engine returned and installed per MM 71-00-00. Engine run-in procedure completed. TBO clock reset to 0 hours.",
      ComponentType.COMPONENT_ENGINE,
      taskHints = listOf("Engine TBO"),
    ),
    LogTemplate(
      "SB 39-2018-01 compliance: Carburetor heat system inspection. Heat muff inspected – no cracks. Inlet duct secure. Carb heat operation confirmed effective during ground run (RPM drop >50 RPM). Complied with SB.",
      ComponentType.COMPONENT_ENGINE,
      taskHints = listOf("Carburetor Heat"),
    ),
    // PROPELLER
    LogTemplate(
      "Propeller inspection per manufacturer SL-2021-02. Leading and trailing edges checked for nicks and corrosion. Blade tracking checked – within 1/8 in tolerance. Hub bolts torque-checked. Propeller returned to service.",
      ComponentType.COMPONENT_PROPELLER,
      taskHints = listOf("Propeller Overhaul"),
    ),
    LogTemplate(
      "Propeller dynamic balance. Balanced using DynaVib Smart Balancer II. Initial IPS: 0.24 at 2,300 RPM. Final IPS: 0.06. Vibration eliminated. Weights added at 12 o'clock position.",
      ComponentType.COMPONENT_PROPELLER,
    ),
    LogTemplate(
      "Propeller nick repair. Nick on blade #1 leading edge measured 0.038 in deep and 0.25 in wide. Dressed per MM using file and emery cloth. Nick within manufacturer's allowable limits. Blade limits not exceeded. Returned to service.",
      ComponentType.COMPONENT_PROPELLER,
    ),
    LogTemplate(
      "Propeller hub inspection. Hub removed, disassembled, cleaned, and inspected. New seals and O-rings installed (kit P/N: H-200-K). Hub reassembled to specified torque. Propeller re-installed and safety-wired.",
      ComponentType.COMPONENT_PROPELLER,
    ),
    LogTemplate(
      "Propeller overhaul completed (5-year/2,000-hour TBO). Propeller removed and sent to Hartzell Propeller Service Center. Fully overhauled and returned with new blades. Reinstalled per MM. Dynamic balance performed.",
      ComponentType.COMPONENT_PROPELLER,
      taskHints = listOf("Propeller Overhaul"),
    ),
    // Avionics work (filed under airframe)
    LogTemplate(
      "ELT battery replacement. Replaced battery in ACK E-04 ELT with Panasonic CR123A. Activated briefly (<1 sec) to verify function. Squawk code [unique to aircraft] verified with Unicom. Registration card updated. Next replacement: 24 months.",
      ComponentType.COMPONENT_AIRFRAME,
      taskHints = listOf("ELT Battery"),
    ),
    LogTemplate(
      "Transponder certification per FAR 91.413. Modes A, C, and S tested and certified. Encoder tested – agrees with altimeter within 125 ft at all test altitudes. All within TSO-C74c limits. Certificate issued. Next check: 24 months.",
      ComponentType.COMPONENT_AIRFRAME,
      taskHints = listOf("Transponder"),
    ),
    LogTemplate(
      "Altimeter and pitot-static check per FAR 91.411. Encoder/altimeter agreement: <75 ft error at 10,000 ft. VSI checked. All within limits. IFR certification current for 24 months.",
      ComponentType.COMPONENT_AIRFRAME,
      taskHints = listOf("Pitot-Static"),
    ),
    LogTemplate(
      "VOR operational check per FAR 91.171. Checked on Gainesville VOR 116.2 MHz at 180° radial ground check point. Receiver indication: 179.5°. Error: 0.5°. Within ±4° tolerance. Check logged.",
      ComponentType.COMPONENT_AIRFRAME,
      taskHints = listOf("VOR"),
    ),
    LogTemplate(
      "Garmin G1000 nav/terrain database updated. Navigation database updated to current 28-day cycle. Terrain database updated to current version. GTN 750 approach plates verified current. Both units confirm valid data.",
      ComponentType.COMPONENT_AIRFRAME,
      taskHints = listOf("Database"),
    ),
    LogTemplate(
      "COM1 radio serviced. Internal PCB connector reseated. Antenna connection cleaned. Radio tested on ground and confirmed clear on 121.5, 122.8, and 123.45 MHz. No static above 10,000 ft during test flight.",
      ComponentType.COMPONENT_AIRFRAME,
    ),
    LogTemplate(
      "Avionics master circuit breaker replaced. Failed 5A CB replaced with OEM part (P/N: MS25244-5). Root cause: intermittent short in co-pilot avionics bus traced to chafed wire. Wire repaired and secured. Ground test – no trips.",
      ComponentType.COMPONENT_AIRFRAME,
    ),
    LogTemplate(
      "ELT serviced after inadvertent activation during hard landing. Activation sensor spring replaced. Unit inspected for damage – none. Unit re-certified and returned to service. Battery verified >50% life remaining.",
      ComponentType.COMPONENT_AIRFRAME,
      taskHints = listOf("ELT Battery"),
    ),
    LogTemplate(
      "Altitude encoder replaced. Encoder P/N ENC-A-1000 installed and calibrated. ATC verified mode C encoding correct on ground check. Pitot-static system integrity verified after encoder swap.",
      ComponentType.COMPONENT_AIRFRAME,
      taskHints = listOf("Transponder"),
    ),
  )

  val POOL = FakeDataPool(tasks = TASKS, squawks = SQUAWKS, logs = LOGS)
}
