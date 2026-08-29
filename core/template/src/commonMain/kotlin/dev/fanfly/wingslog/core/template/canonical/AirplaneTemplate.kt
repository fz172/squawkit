package dev.fanfly.wingslog.core.template.canonical

import dev.fanfly.wingslog.thing.Capabilities
import dev.fanfly.wingslog.thing.ComponentSlot
import dev.fanfly.wingslog.thing.ExportLayout
import dev.fanfly.wingslog.thing.Lexicon
import dev.fanfly.wingslog.thing.MeterDef
import dev.fanfly.wingslog.thing.Noun
import dev.fanfly.wingslog.thing.Section
import dev.fanfly.wingslog.thing.SpecField
import dev.fanfly.wingslog.thing.SquawkPriority
import dev.fanfly.wingslog.thing.ThingTemplate

/**
 * The airplane preset — the only template that exists until Phase 3.
 *
 * **Every value here is derived from the shipped `strings.xml`, not from the PRD's illustrative
 * examples.** That distinction is the whole point: the byte-identical snapshot test (#658) compares
 * what this renders against what the app renders today, so a word invented here becomes a wrong
 * assertion rather than a caught bug. Where the two disagree, the shipped string wins — see the
 * notes on [AIRPLANE_LEXICON].
 *
 * **Built in Kotlin rather than parsed from a `.textproto` asset**, which is a deliberate deviation
 * from `template_system_design.md` §4. That design has baked-in and fetched templates share one
 * decode path, which requires `protoc --encode` to compile a text proto to bytes at build time —
 * and `protoc` is not available to Gradle here (only `grpc_tools_node_protoc`, inside the backend's
 * `node_modules`). The shared-decode-path argument does not bite until the fetch RPC exists, which
 * is Phase 3, and that is also when a publishing script needs `protoc` anyway. Authoring a
 * `.textproto` now that nothing parses would be worse than not having one: it would drift silently
 * while looking authoritative.
 */
object AirplaneTemplate {

  const val ID: String = "airplane"
  const val VERSION: Int = 1

  /**
   * The words the app uses today.
   *
   * Two fields have **no current rendering anywhere**, so the snapshot test cannot check them and
   * they take the PRD's §4.5 values on faith: [Lexicon.ready_status] — the app has no "Airworthy"
   * string, it says "No open squawks" — and [Lexicon.collection_label], which appears only inside
   * `no_fleet_title`, never as a label.
   *
   * One field disagrees with the PRD. §4.5 suggests `task` resolves to "inspection" for airplanes;
   * the app ships `shell_title_tasks = "Maintenance Tasks"`. The shipped string wins.
   */
  val AIRPLANE_LEXICON: Lexicon = Lexicon(
    // "aircraft" is its own plural, and takes "an" — exactly the case that makes `article` a
    // stored field rather than something derived from the first letter.
    thing = Noun(singular = "aircraft", plural = "aircraft", article = "an"),
    squawk = Noun(singular = "squawk", plural = "squawks", article = "a"),
    task = Noun(singular = "maintenance task", plural = "maintenance tasks", article = "a"),
    log = Noun(singular = "work log", plural = "work logs", article = "a"),
    component = Noun(singular = "component", plural = "components", article = "a"),
    technician = Noun(singular = "technician", plural = "technicians", article = "a"),
    ready_status = "Airworthy",
    // These two name the OS notification channel a user sees in system settings (PRD §8.5), which
    // is why they are whole strings rather than substitutions — and why the channel *id* stays
    // "GROUNDED" forever while only the display name comes from here.
    down_status = "AOG",
    down_status_long = "Aircraft on Ground",
    collection_label = "Fleet",
    compliance_mandatory = "Airworthiness Directive",
    compliance_advisory = "Service Bulletin",
    authority_label = "FAA",
  )

  /**
   * Everything on. The airplane column of PRD §4.8 is on for every capability, which is what makes
   * Phase 2's capability wiring invisible: the flags are consulted everywhere and always answer the
   * same way (#659, #660).
   */
  val AIRPLANE_CAPABILITIES: Capabilities = Capabilities(
    components = true,
    meters = true,
    compliance = true,
    technicians = true,
    technician_certificates = true,
    component_serial_prompt = true,
    priorities = listOf(
      SquawkPriority.SQUAWK_PRIORITY_LOW,
      SquawkPriority.SQUAWK_PRIORITY_MEDIUM,
      SquawkPriority.SQUAWK_PRIORITY_HIGH,
      SquawkPriority.SQUAWK_PRIORITY_AOG,
    ),
    // Order matters — this is what the shell renders, in this sequence.
    sections = listOf(
      Section.SECTION_DASHBOARD,
      Section.SECTION_SQUAWKS,
      Section.SECTION_TASKS,
      Section.SECTION_LOGS,
    ),
    export_layout = ExportLayout.EXPORT_LAYOUT_LOGBOOK,
  )

  /**
   * The conventional keys that map onto the legacy `Thing` fields 2–5, which Phase 1 also mirrored
   * into `spec` (#586). They survive as *template-declared* fields rather than a privileged core:
   * a house has no make, model, or serial (PRD §4.2).
   */
  val AIRPLANE_SPEC_FIELDS: List<SpecField> = listOf(
    SpecField(key = "tail_number", label = "Tail Number", is_identifier = true),
    SpecField(key = "make", label = "Make"),
    SpecField(key = "model", label = "Model"),
    SpecField(key = "serial", label = "Serial Number", is_identifier = true),
  )

  /** Mirrors the tree Phase 1's backfill builds: airframe → engine(s) → propeller → hub/blades. */
  val AIRPLANE_COMPONENT_SLOTS: List<ComponentSlot> = listOf(
    ComponentSlot(
      slot_key = "airframe",
      label = "Airframe",
      serial_expected = true,
      children = listOf(
        ComponentSlot(
          slot_key = "engine",
          label = "Engine",
          repeatable = true,
          serial_expected = true,
          children = listOf(
            ComponentSlot(
              slot_key = "propeller",
              label = "Propeller",
              children = listOf(
                ComponentSlot(slot_key = "hub", label = "Hub", serial_expected = true),
                ComponentSlot(
                  slot_key = "blade",
                  label = "Blade",
                  repeatable = true,
                  serial_expected = true,
                ),
              ),
            ),
          ),
        ),
      ),
    ),
  )

  /**
   * The three meters the log form already collects. `component_slot_key` is what makes engine hours
   * the *engine's* rather than the airframe's — a distinction `float interval_hours` could not
   * express, and the reason `MeterRule` is a new message rather than a renamed `EngineHourRule`
   * (`template_system_design.md` §11.1).
   */
  val AIRPLANE_METERS: List<MeterDef> = listOf(
    MeterDef(key = "airframe_hours", label = "Airframe Time", unit_label = "hrs", decimal = true),
    MeterDef(
      key = "engine_hours",
      label = "Engine Time",
      unit_label = "hrs",
      decimal = true,
      component_slot_key = "engine",
    ),
    MeterDef(
      key = "prop_hours",
      label = "Prop Time",
      unit_label = "hrs",
      decimal = true,
      component_slot_key = "propeller",
    ),
  )

  /**
   * `min_app_version = 0`: no floor. This template ships inside every build that can read it, so a
   * client can never encounter it from the future — the floor exists for *fetched* templates
   * (§6), and a baked-in one is by definition never newer than the build carrying it.
   *
   * `starter_tasks` is empty. Starter packs are Phase 3 content (PRD §4.9); the field is declared
   * so the shape is settled, not because Phase 2 has anything to put in it.
   */
  val TEMPLATE: ThingTemplate = ThingTemplate(
    id = ID,
    version = VERSION,
    min_app_version = 0,
    lexicon = AIRPLANE_LEXICON,
    capabilities = AIRPLANE_CAPABILITIES,
    spec_fields = AIRPLANE_SPEC_FIELDS,
    component_slots = AIRPLANE_COMPONENT_SLOTS,
    meters = AIRPLANE_METERS,
    display_name = "Airplane",
    icon = "airplane",
    sort_order = 0,
  )
}
