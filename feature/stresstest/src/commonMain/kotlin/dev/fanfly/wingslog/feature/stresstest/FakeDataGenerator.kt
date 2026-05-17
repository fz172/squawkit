package dev.fanfly.wingslog.feature.stresstest

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.CertExpireLimit
import dev.fanfly.wingslog.aircraft.CertificateType
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.Engine
import dev.fanfly.wingslog.aircraft.EngineHourRule
import dev.fanfly.wingslog.aircraft.ImmediateRule
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.aircraft.OnConditionRule
import dev.fanfly.wingslog.aircraft.PropellerBlade
import dev.fanfly.wingslog.aircraft.PropellerHub
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.SquawkDismissReason
import dev.fanfly.wingslog.aircraft.SquawkPriority
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.aircraft.TimeRule
import dev.fanfly.wingslog.aircraft.Propeller
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.model.id.generateRandomId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

data class StressTestData(
    val aircraft: Aircraft,
    val technicians: List<Technician>,
    val tasks: List<MaintenanceTask>,
    val squawks: List<Squawk>,
    val logs: List<MaintenanceLog>,
    val addressedSquawks: Map<String, String>,
    val dismissedSquawks: Map<String, SquawkDismissReason>,
)

object FakeDataGenerator {

    private data class AircraftSpec(
        val make: String,
        val model: String,
        val engineMake: String,
        val engineModel: String,
        val propMake: String,
        val propModel: String,
    )

    private val AIRCRAFT_SPECS = listOf(
        AircraftSpec("Incom", "T-65B", "Lycoming", "O-320-E2D", "Sensenich", "76EM8S5-0-62"),
        AircraftSpec("Corellian Engineering", "YT-1300F", "Continental", "O-470-U", "McCauley", "1C172/ATM7553"),
        AircraftSpec("MandalMotors", "Kom'rk 452", "Lycoming", "O-360-A4M", "Sensenich", "74DM6S5-0-58"),
        AircraftSpec("Kuat Systems", "RZ-1 A-wing", "Continental", "IO-520-BB", "Hartzell", "HC-C2YK-1BF"),
        AircraftSpec("SoroSuub", "N-1 Scout", "Continental", "IO-550-N", "Hartzell", "HC-E2YR-2ALTUF"),
        AircraftSpec("Incom", "Z-95-AF4", "Lycoming", "IO-360-M1A", "MT-Propeller", "MTV-6-A-200"),
        AircraftSpec("Kuat Systems", "BTL-B", "Lycoming", "IO-360-A3B6D", "McCauley", "2A34C82/82NCA"),
        AircraftSpec("Corellian Engineering", "G9 Rigger", "Lycoming", "O-360-A1H6", "Hartzell", "HC-C2YK-1BF"),
    )

    private val TECHNICIAN_NAMES = listOf(
        "Anakin Skywalker", "Han Solo", "Rey Palpatine",
        "Poe Dameron", "Ahsoka Tano", "Bodhi Rook",
        "Cassian Andor", "Hera Syndulla",
    )

    private data class TaskTemplate(
        val title: String,
        val component: ComponentType,
        val type: ComplianceType,
        val rule: InspectionRule,
        val notes: String = "",
        val referenceNumber: String = "",
        val complianceAuthority: String = "",
        val complianceDetails: String = "",
        val isOneTime: Boolean = false,
    )

    private val TASK_TEMPLATES = listOf(
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
            InspectionRule(engine_hour_rule = EngineHourRule(interval_hours = 100f)),
            notes = "Required for hire operations. FAR 91.409(b). Follows Annual inspection checklist.",
        ),
        TaskTemplate(
            "Engine Oil Change",
            ComponentType.COMPONENT_ENGINE,
            ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
            InspectionRule(engine_hour_rule = EngineHourRule(interval_hours = 50f)),
            notes = "Replace oil filter and send sample for analysis. Use AeroShell W80 Plus or equivalent.",
        ),
        TaskTemplate(
            "Spark Plug Rotation",
            ComponentType.COMPONENT_ENGINE,
            ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
            InspectionRule(engine_hour_rule = EngineHourRule(interval_hours = 100f)),
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
            ComponentType.COMPONENT_AVIONICS,
            ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
            InspectionRule(time_rule = TimeRule(interval_months = 24)),
            notes = "FAR 91.413. Modes A, C, and S. Must be performed by certificated repair station.",
        ),
        TaskTemplate(
            "VOR Operational Check",
            ComponentType.COMPONENT_AVIONICS,
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
            InspectionRule(engine_hour_rule = EngineHourRule(interval_hours = 2000f)),
            notes = "Manufacturer recommended TBO. Not mandatory for Part 91, but highly recommended.",
        ),
        TaskTemplate(
            "Alternator Belt Inspection",
            ComponentType.COMPONENT_ENGINE,
            ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
            InspectionRule(engine_hour_rule = EngineHourRule(interval_hours = 100f)),
            notes = "Check tension, fraying, and cracking. Replace if belt deflects more than 1/2 inch.",
        ),
        TaskTemplate(
            "Magneto Timing Check",
            ComponentType.COMPONENT_ENGINE,
            ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
            InspectionRule(engine_hour_rule = EngineHourRule(interval_hours = 500f)),
            notes = "Check timing at 25° BTC ±1°. Inspect points, condenser, and distributor block.",
        ),
        TaskTemplate(
            "Avionics Database Update",
            ComponentType.COMPONENT_AVIONICS,
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
            InspectionRule(engine_hour_rule = EngineHourRule(interval_hours = 100f)),
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
            ComponentType.COMPONENT_AVIONICS,
            ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
            InspectionRule(time_rule = TimeRule(interval_months = 12)),
            notes = "Verify stall warning activates 5–10 kts above stall. Clean vane and check wiring.",
        ),
    )

    private data class SquawkTemplate(
        val title: String,
        val description: String,
        val priority: SquawkPriority,
        val component: ComponentType,
    )

    private val SQUAWK_TEMPLATES = listOf(
        SquawkTemplate(
            "Left landing light inoperative",
            "Landing light bulb burned out on left main gear. Navigation light functioning normally. Right landing light OK.",
            SquawkPriority.SQUAWK_PRIORITY_LOW, ComponentType.COMPONENT_AIRFRAME,
        ),
        SquawkTemplate(
            "Oil filler cap O-ring deteriorated",
            "O-ring on oil filler cap is cracked and has lost elasticity. No leakage observed but replacement recommended before next flight.",
            SquawkPriority.SQUAWK_PRIORITY_MEDIUM, ComponentType.COMPONENT_ENGINE,
        ),
        SquawkTemplate(
            "Left brake dragging on rollout",
            "Left main wheel brake exhibits slight drag during landing rollout and slow taxi. Consistent across three flights. Brake caliper or shimmy dampener suspected.",
            SquawkPriority.SQUAWK_PRIORITY_LOW, ComponentType.COMPONENT_AIRFRAME,
        ),
        SquawkTemplate(
            "COM1 intermittent static above FL080",
            "COM1 radio develops intermittent static and occasional dropout above 8,000 ft MSL. COM2 unaffected. Issue began approximately 20 flight hours ago.",
            SquawkPriority.SQUAWK_PRIORITY_MEDIUM, ComponentType.COMPONENT_AVIONICS,
        ),
        SquawkTemplate(
            "Propeller blade nick – leading edge",
            "Small nick found on leading edge of propeller blade #1 approximately 4 inches from tip during preflight. Estimated depth 0.040 inches. Flight restricted pending inspection.",
            SquawkPriority.SQUAWK_PRIORITY_HIGH, ComponentType.COMPONENT_PROPELLER,
        ),
        SquawkTemplate(
            "Engine won't start – fuel issue suspected",
            "Engine fails to start after repeated attempts. Fuel pressure reads normal. Starter engagement confirmed. Vapor lock or possible contaminated fuel suspected.",
            SquawkPriority.SQUAWK_PRIORITY_AOG, ComponentType.COMPONENT_ENGINE,
        ),
        SquawkTemplate(
            "Static wick missing – left aileron",
            "Static wick found missing from left aileron trailing edge. All other static wicks intact. No precipitation static issues reported in flight.",
            SquawkPriority.SQUAWK_PRIORITY_LOW, ComponentType.COMPONENT_AIRFRAME,
        ),
        SquawkTemplate(
            "ELT inadvertent activation",
            "ELT activated during hard landing. ATC notified and activation cancelled. Battery replaced. Unit requires inspection, testing, and recertification before return to service.",
            SquawkPriority.SQUAWK_PRIORITY_HIGH, ComponentType.COMPONENT_AVIONICS,
        ),
        SquawkTemplate(
            "Right rear door seal air leak",
            "Right rear passenger door seal leaking air at cruise altitude. Cabin noise level noticeably increased above 100 KIAS. Door closes and latches properly.",
            SquawkPriority.SQUAWK_PRIORITY_LOW, ComponentType.COMPONENT_AIRFRAME,
        ),
        SquawkTemplate(
            "Transponder Mode C altitude error",
            "Transponder not squawking correct altitude in Mode C. Altimeter reading matches actual altitude. Altitude encoder is suspected to be out of calibration.",
            SquawkPriority.SQUAWK_PRIORITY_MEDIUM, ComponentType.COMPONENT_AVIONICS,
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
            SquawkPriority.SQUAWK_PRIORITY_MEDIUM, ComponentType.COMPONENT_AIRFRAME,
        ),
        SquawkTemplate(
            "Avionics master CB intermittent trip",
            "Avionics master circuit breaker trips intermittently during engine start sequence. CB resets and holds after one attempt. Potential wiring short or overloaded circuit.",
            SquawkPriority.SQUAWK_PRIORITY_MEDIUM, ComponentType.COMPONENT_AVIONICS,
        ),
        SquawkTemplate(
            "Fuel cap O-ring pilot side",
            "Pilot-side fuel cap O-ring slightly compressed and beginning to lose seating. No leakage confirmed, but preventive replacement recommended per AD 2022-15-03.",
            SquawkPriority.SQUAWK_PRIORITY_LOW, ComponentType.COMPONENT_AIRFRAME,
        ),
    )

    private data class LogTemplate(
        val description: String,
        val component: ComponentType,
        val taskHints: List<String> = emptyList(),
    )

    private val LOG_TEMPLATES = listOf(
        // AIRFRAME
        LogTemplate(
            "Annual inspection completed per FAR 43 Appendix D. All airframe structures, flight controls, landing gear, and systems inspected. Airworthiness Directive status current. Logbook entries made. Aircraft found airworthy and returned to service.",
            ComponentType.COMPONENT_AIRFRAME,
            taskHints = listOf("Annual", "Seat Rail", "Fuel System", "Control Cable"),
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
            taskHints = listOf("100-Hour", "Oil Change", "Spark Plug", "Magneto"),
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
        // AVIONICS
        LogTemplate(
            "ELT battery replacement. Replaced battery in ACK E-04 ELT with Panasonic CR123A. Activated briefly (<1 sec) to verify function. Squawk code [unique to aircraft] verified with Unicom. Registration card updated. Next replacement: 24 months.",
            ComponentType.COMPONENT_AVIONICS,
            taskHints = listOf("ELT Battery"),
        ),
        LogTemplate(
            "Transponder certification per FAR 91.413. Modes A, C, and S tested and certified. Encoder tested – agrees with altimeter within 125 ft at all test altitudes. All within TSO-C74c limits. Certificate issued. Next check: 24 months.",
            ComponentType.COMPONENT_AVIONICS,
            taskHints = listOf("Transponder"),
        ),
        LogTemplate(
            "Altimeter and pitot-static check per FAR 91.411. Encoder/altimeter agreement: <75 ft error at 10,000 ft. VSI checked. All within limits. IFR certification current for 24 months.",
            ComponentType.COMPONENT_AVIONICS,
            taskHints = listOf("Pitot-Static"),
        ),
        LogTemplate(
            "VOR operational check per FAR 91.171. Checked on Gainesville VOR 116.2 MHz at 180° radial ground check point. Receiver indication: 179.5°. Error: 0.5°. Within ±4° tolerance. Check logged.",
            ComponentType.COMPONENT_AVIONICS,
            taskHints = listOf("VOR"),
        ),
        LogTemplate(
            "Garmin G1000 nav/terrain database updated. Navigation database updated to current 28-day cycle. Terrain database updated to current version. GTN 750 approach plates verified current. Both units confirm valid data.",
            ComponentType.COMPONENT_AVIONICS,
            taskHints = listOf("Database"),
        ),
        LogTemplate(
            "COM1 radio serviced. Internal PCB connector reseated. Antenna connection cleaned. Radio tested on ground and confirmed clear on 121.5, 122.8, and 123.45 MHz. No static above 10,000 ft during test flight.",
            ComponentType.COMPONENT_AVIONICS,
        ),
        LogTemplate(
            "Avionics master circuit breaker replaced. Failed 5A CB replaced with OEM part (P/N: MS25244-5). Root cause: intermittent short in co-pilot avionics bus traced to chafed wire. Wire repaired and secured. Ground test – no trips.",
            ComponentType.COMPONENT_AVIONICS,
        ),
        LogTemplate(
            "ELT serviced after inadvertent activation during hard landing. Activation sensor spring replaced. Unit inspected for damage – none. Unit re-certified and returned to service. Battery verified >50% life remaining.",
            ComponentType.COMPONENT_AVIONICS,
            taskHints = listOf("ELT Battery"),
        ),
        LogTemplate(
            "Altitude encoder replaced. Encoder P/N ENC-A-1000 installed and calibrated. ATC verified mode C encoding correct on ground check. Pitot-static system integrity verified after encoder swap.",
            ComponentType.COMPONENT_AVIONICS,
            taskHints = listOf("Transponder"),
        ),
    )

    fun generate(config: StressTestConfig): StressTestData {
        val now = Clock.System.now()
        val spanDays = (4 * 365).days
        val startInstant = now - spanDays

        val spec = AIRCRAFT_SPECS.random()
        val aircraftId = generateRandomId()
        val aircraft = buildAircraft(spec, aircraftId, config)
        val technicians = buildTechnicians(config.technicianCount)
        val tasks = buildTasks(config.taskCount, now)
        val squawks = buildSquawks(config.squawkCount, aircraft, startInstant, now)
        val (logs, addressedSquawks) = buildLogs(config.logCount, aircraft, technicians, tasks, squawks, startInstant, now)
        val dismissedSquawks = buildDismissedSquawks(squawks, addressedSquawks)

        return StressTestData(
            aircraft = aircraft,
            technicians = technicians,
            tasks = tasks,
            squawks = squawks,
            logs = logs,
            addressedSquawks = addressedSquawks,
            dismissedSquawks = dismissedSquawks,
        )
    }

    private fun buildAircraft(spec: AircraftSpec, aircraftId: String, config: StressTestConfig): Aircraft {
        val serialLetters = ('A'..'Z').toList()
        val serial = "S${serialLetters.random()}${(10000..99999).random()}"
        val tailNumber = "N${(1000..9999).random()}${('A'..'Z').random()}"

        val engines = (1..config.engineCount).map { engineIndex ->
            val engineSerial = "E${(10000..99999).random()}"
            val propSerial = "P${(10000..99999).random()}"
            val blades = (1..config.bladesPerEngine).map { bladeIndex ->
                val bladeSerial = "B${engineIndex}${bladeIndex}-${(1000..9999).random()}"
                PropellerBlade(make = spec.propMake, model = spec.propModel, serial = bladeSerial)
            }
            Engine(
                make = spec.engineMake,
                model = spec.engineModel,
                serial = engineSerial,
                propeller = Propeller(
                    hub = PropellerHub(make = spec.propMake, model = spec.propModel, serial = propSerial),
                    blades = blades,
                ),
            )
        }

        return Aircraft(
            id = aircraftId,
            make = spec.make,
            model = spec.model,
            serial = serial,
            tail_number = tailNumber,
            engine = engines,
        )
    }

    private fun buildTechnicians(count: Int): List<Technician> {
        val shuffled = TECHNICIAN_NAMES.shuffled().take(count.coerceAtMost(TECHNICIAN_NAMES.size))
        return shuffled.mapIndexed { index, name ->
            val isAmt = index % 3 != 2
            val certNumber = if (isAmt) "A${(1000000..9999999).random()}" else "R${(100000..999999).random()}"
            val expYearsAhead = (1..5).random()
            val expInstant = Clock.System.now() + (expYearsAhead * 365).days
            Technician(
                id = generateRandomId(),
                name = name,
                certificate_type = if (isAmt) CertificateType.CERTIFICATE_TYPE_AMT else CertificateType.CERTIFICATE_TYPE_REPAIRMAN,
                cert_number = certNumber,
                cert_expire_limit = CertExpireLimit.CERT_EXPIRE_LIMIT_EXPIRES,
                cert_expiration = expInstant.toWireInstant(),
            )
        }
    }

    private fun buildTasks(count: Int, now: Instant): List<MaintenanceTask> {
        val pool = TASK_TEMPLATES.shuffled().take(count.coerceAtMost(TASK_TEMPLATES.size))
        val overdueCreationInstant = now - (4 * 365).days

        return pool.mapIndexed { index, template ->
            val taskId = generateRandomId()
            val rule = template.rule
            val isOnCondition = rule.on_condition_rule != null || rule.immediate_rule != null
            val isEngineHour = rule.engine_hour_rule != null
            val dueGroup = if (isOnCondition) -1 else index % 3

            // Approximate the rule interval in days for creation_date arithmetic.
            val intervalDays: Long = rule.time_rule?.let {
                when {
                    it.interval_days > 0 -> it.interval_days.toLong()
                    it.interval_years > 0 -> it.interval_years.toLong() * 365L
                    else -> it.interval_months.toLong() * 30L
                }
            } ?: 0L

            // Time-based tasks: drive due status purely via creation_date so the
            // TaskDueManager's rule-based path does all the work.
            //
            //   NORMAL   → creation_date = now − interval + 40..240 days
            //              → next due = now + 40..240 days  (well outside 30-day window)
            //   DUE SOON → creation_date = now − interval + 1..25 days
            //              → next due = now + 1..25 days    (inside 30-day window)
            //   OVERDUE  → creation_date = 4 years ago
            //              → next due = long in the past
            //
            // Short-interval tasks (< 40 days, e.g. VOR/DB update) can never reach NORMAL
            // status since the whole interval is less than the DUE_SOON threshold; they
            // fall through to the DUE_SOON branch instead.
            //
            // Engine-hour tasks can't have their base controlled at task-creation time
            // (the base comes from logged engine hours, which aren't known yet), so
            // NORMAL/DUE_SOON use force_due_date and OVERDUE is natural (base=0,
            // interval=100, current≈1200 → OVERDUE without any override).
            val creationInstant: Instant = when {
                isEngineHour || isOnCondition -> overdueCreationInstant
                dueGroup == 2 -> overdueCreationInstant
                dueGroup == 0 && intervalDays >= 40 -> {
                    val offsetDays = (40..minOf(intervalDays, 240L).toInt()).random().toLong()
                    now - intervalDays.days + offsetDays.days
                }
                else -> {
                    // DUE SOON (also handles short-interval tasks demoted from NORMAL)
                    val maxOffset = minOf(25L, intervalDays - 1).coerceAtLeast(1L)
                    val offsetDays = (1..maxOffset.toInt()).random().toLong()
                    now - intervalDays.days + offsetDays.days
                }
            }

            val timeRuleWithDate = when {
                rule.time_rule != null -> InspectionRule(
                    time_rule = rule.time_rule.copy(creation_date = creationInstant.toWireInstant())
                )
                else -> rule
            }

            val forceDueDate = when {
                isOnCondition || !isEngineHour -> null
                dueGroup == 0 -> (now + (60..240).random().days).toWireInstant()
                dueGroup == 1 -> (now + (1..25).random().days).toWireInstant()
                else -> null
            }

            MaintenanceTask(
                id = taskId,
                title = template.title,
                component = template.component,
                type = template.type,
                rules = listOf(timeRuleWithDate),
                notes = template.notes,
                reference_number = template.referenceNumber,
                compliance_authority = template.complianceAuthority,
                compliance_details = template.complianceDetails,
                is_one_time = template.isOneTime,
                force_due_date = forceDueDate,
            )
        }
    }

    private fun buildSquawks(
        count: Int,
        aircraft: Aircraft,
        startInstant: Instant,
        now: Instant,
    ): List<Squawk> {
        val pool = SQUAWK_TEMPLATES.shuffled().take(count.coerceAtMost(SQUAWK_TEMPLATES.size))
        val span = now - startInstant
        return pool.mapIndexed { i, template ->
            val fraction = if (pool.size == 1) 0.5 else i.toDouble() / (pool.size - 1)
            val squawkInstant = startInstant + (span * fraction)
            val serial = when (template.component) {
                ComponentType.COMPONENT_ENGINE -> aircraft.engine.firstOrNull()?.serial ?: aircraft.serial
                ComponentType.COMPONENT_PROPELLER -> aircraft.engine.firstOrNull()?.propeller?.hub?.serial ?: aircraft.serial
                else -> aircraft.serial
            }
            Squawk(
                id = generateRandomId(),
                title = template.title,
                description = template.description,
                priority = template.priority,
                component_type = template.component,
                component_serial = serial,
                created_at = squawkInstant.toWireInstant(),
            )
        }
    }

    private fun buildLogs(
        count: Int,
        aircraft: Aircraft,
        technicians: List<Technician>,
        tasks: List<MaintenanceTask>,
        squawks: List<Squawk>,
        startInstant: Instant,
        now: Instant,
    ): Pair<List<MaintenanceLog>, Map<String, String>> {

        val span = now - startInstant
        val pool = buildLogPool(count)
        val startEngineHours = (800..1200).random().toDouble()
        val totalHoursFlown = (300..600).random().toDouble()

        val taskMap = tasks.associateBy { it.title }

        val openSquawks = squawks.toMutableList()
        val addressedSquawks = mutableMapOf<String, String>()

        val addressedCount = (squawks.size * 0.45).toInt().coerceAtLeast(1)
        val squawksToAddress = openSquawks.shuffled().take(addressedCount)

        val logs = pool.mapIndexed { i, template ->
            val fraction = i.toDouble() / (pool.size - 1).coerceAtLeast(1)
            val logInstant = startInstant + (span * fraction)
            val engineHours = startEngineHours + (fraction * totalHoursFlown)
            val airframeTime = engineHours + (30..80).random()
            val propTime = engineHours - (5..20).random()

            val logId = generateRandomId()
            val technician = if (technicians.isNotEmpty()) technicians.random() else null

            val matchedTaskIds = tasks.filter { task ->
                template.taskHints.any { hint -> task.title.contains(hint, ignoreCase = true) }
            }.map { it.id }

            val squawkIds = squawksToAddress
                .filter { sq ->
                    sq.component_type == template.component && !addressedSquawks.containsKey(sq.id)
                }
                .take(1)
                .map { sq ->
                    addressedSquawks[sq.id] = logId
                    sq.id
                }

            val componentSerial = when (template.component) {
                ComponentType.COMPONENT_ENGINE -> aircraft.engine.firstOrNull()?.serial ?: aircraft.serial
                ComponentType.COMPONENT_PROPELLER -> aircraft.engine.firstOrNull()?.propeller?.hub?.serial ?: aircraft.serial
                ComponentType.COMPONENT_AVIONICS -> aircraft.serial
                else -> aircraft.serial
            }

            MaintenanceLog(
                id = logId,
                timestamp = logInstant.toWireInstant(),
                work_description = template.description,
                component_type = template.component,
                component_serial = componentSerial,
                engine_hour = engineHours,
                airframe_time = airframeTime,
                prop_time = propTime,
                inspection_ids = matchedTaskIds,
                squawk_ids = squawkIds,
                technician = technician,
                technician_id = technician?.id ?: "",
            )
        }

        val remainingOpenSquawks = squawks.filter { !addressedSquawks.containsKey(it.id) }
        val additionalToAddress = remainingOpenSquawks.shuffled().take(
            (remainingOpenSquawks.size * 0.3).toInt()
        )
        for (sq in additionalToAddress) {
            val matchingLog = logs.firstOrNull { log ->
                log.component_type == sq.component_type && log.squawk_ids.isEmpty()
            } ?: logs.random()
            if (!addressedSquawks.containsKey(sq.id)) {
                addressedSquawks[sq.id] = matchingLog.id
            }
        }

        return Pair(logs.sortedBy { it.timestamp?.getEpochSecond() ?: 0L }, addressedSquawks)
    }

    private fun buildDismissedSquawks(
        squawks: List<Squawk>,
        addressedSquawks: Map<String, String>,
    ): Map<String, SquawkDismissReason> {
        val openIds = squawks
            .filter { !addressedSquawks.containsKey(it.id) }
            .map { it.id }
        val dismissCount = (openIds.size * 0.35).toInt().coerceAtMost(openIds.size)
        val reasons = listOf(
            SquawkDismissReason.SQUAWK_DISMISS_REASON_OBSOLETE,
            SquawkDismissReason.SQUAWK_DISMISS_REASON_NOT_REPRODUCIBLE,
            SquawkDismissReason.SQUAWK_DISMISS_REASON_DUPLICATE,
        )
        return openIds.shuffled().take(dismissCount).associateWith { reasons.random() }
    }

    private fun buildLogPool(count: Int): List<LogTemplate> {
        if (count <= LOG_TEMPLATES.size) return LOG_TEMPLATES.shuffled().take(count)
        val pool = mutableListOf<LogTemplate>()
        while (pool.size < count) pool.addAll(LOG_TEMPLATES.shuffled())
        return pool.take(count)
    }
}
