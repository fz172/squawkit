package dev.fanfly.wingslog.feature.stresstest

import dev.fanfly.wingslog.core.appinfo.APP_VERSION_CODE
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.core.template.SlotKeys
import dev.fanfly.wingslog.core.template.SpecKeys
import dev.fanfly.wingslog.core.template.allComponentsInSlot
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.core.template.specValue
import dev.fanfly.wingslog.core.template.withDerivedComponentIds
import dev.fanfly.wingslog.feature.stresstest.fixtures.FakeDataPool
import dev.fanfly.wingslog.feature.stresstest.fixtures.FakeDataPools
import dev.fanfly.wingslog.feature.stresstest.fixtures.LogTemplate
import dev.fanfly.wingslog.feature.stresstest.fixtures.SampleNames
import dev.fanfly.wingslog.thing.CertExpireLimit
import dev.fanfly.wingslog.core.model.technician.FAA_AMT
import dev.fanfly.wingslog.core.model.technician.FAA_REPAIRMAN
import dev.fanfly.wingslog.thing.Certification
import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.ComponentSlot
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.InspectionRule
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.MeterReading
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.SpecField
import dev.fanfly.wingslog.thing.Squawk
import dev.fanfly.wingslog.thing.SquawkDismissReason
import dev.fanfly.wingslog.thing.Technician
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

data class StressTestData(
  val thing: Thing,
  val technicians: List<Technician>,
  val tasks: List<MaintenanceTask>,
  val squawks: List<Squawk>,
  val logs: List<MaintenanceLog>,
  val addressedSquawks: Map<String, String>,
  val dismissedSquawks: Map<String, SquawkDismissReason>,
)

object FakeDataGenerator {

  fun generate(config: StressTestConfig): StressTestData {
    val now = Clock.System.now()
    val spanDays = (4 * 365).days
    val startInstant = now - spanDays

    val spec = SampleNames.THING_SPECS.random()
    val thingId = generateRandomId()
    val template =
      CanonicalTemplates.ALL.firstOrNull { it.id == config.templateId }
        ?: AirplaneTemplate.TEMPLATE
    // Squawks, tasks and logs all come from the preset's own pool: a car gets check-engine lights
    // and oil changes in miles, not a propeller nick and an annual it has no airframe for.
    val pool = FakeDataPools.forTemplate(template)
    // Airplane keeps its own builder: the aviation fixture below (engine/propeller logs, component
    // squawks) expects the specific airframe -> engine -> propeller -> hub/blade tree, and
    // engineCount / bladesPerEngine configure it. Every other preset is built from its template.
    val thing =
      if (template.id == AirplaneTemplate.ID) buildThing(
        spec,
        thingId,
        config
      )
      else buildFromTemplate(template, thingId)
    val technicians = buildTechnicians(config.technicianCount)
    val tasks = buildTasks(config.taskCount, now, pool)
    val squawks =
      buildSquawks(config.squawkCount, thing, pool, startInstant, now)
    val (logs, addressedSquawks) = buildLogs(
      config.logCount,
      thing,
      template,
      pool,
      technicians,
      tasks,
      squawks,
      startInstant,
      now
    )
    val dismissedSquawks = buildDismissedSquawks(squawks, addressedSquawks)

    return StressTestData(
      thing = thing,
      technicians = technicians,
      tasks = tasks,
      squawks = squawks,
      logs = logs,
      addressedSquawks = addressedSquawks,
      dismissedSquawks = dismissedSquawks,
    )
  }

  private fun buildThing(
    spec: SampleNames.ThingSpec,
    thingId: String,
    config: StressTestConfig
  ): Thing {
    val serialLetters = ('A'..'Z').toList()
    val serial = "S${serialLetters.random()}${(10000..99999).random()}"
    val tailNumber = "N${(1000..9999).random()}${('A'..'Z').random()}"

    // Builds the component tree directly — the same shape the form produces. Engines sit at the
    // root and the propeller carries what the hub used to (#729).
    val engines = (1..config.engineCount).map { engineIndex ->
      val blades = (1..config.bladesPerEngine).map { bladeIndex ->
        Component(
          slot_key = SlotKeys.BLADE,
          make = spec.propMake,
          model = spec.propModel,
          serial = "B${engineIndex}${bladeIndex}-${(1000..9999).random()}",
        )
      }
      Component(
        slot_key = SlotKeys.ENGINE,
        make = spec.engineMake,
        model = spec.engineModel,
        serial = "E${(10000..99999).random()}",
        children = listOf(
          Component(
            slot_key = SlotKeys.PROPELLER,
            make = spec.propMake,
            model = spec.propModel,
            serial = "P${(10000..99999).random()}",
            children = blades,
          ),
        ),
      )
    }

    return Thing(
      id = thingId,
      name = tailNumber,
      spec = listOf(
        Spec(key = SpecKeys.MAKE, value_ = spec.make),
        Spec(key = SpecKeys.MODEL, value_ = spec.model),
        Spec(key = SpecKeys.SERIAL, value_ = serial),
        Spec(key = SpecKeys.TAIL_NUMBER, value_ = tailNumber),
      ),
      // No airframe wrapper: the airframe was the thing, and its make/model/serial are the spec
      // entries above (#729).
      components = engines,
    ).withDerivedComponentIds()
      .let { thing ->
        // ThingInflater writes DNA on save only when the Thing carries none, so DNA set here
        // survives the write and is what makes the thing resolve as Degraded.
        if (!config.dnaFromANewerBuild) thing
        else thing.copy(
          template = AirplaneTemplate.TEMPLATE.copy(
            min_app_version = APP_VERSION_CODE + 1,
          ),
        )
      }
  }

  /**
   * A Thing built from whatever its template declares, for the presets that have no bespoke
   * fixture (#721-#723).
   *
   * **Reads the template rather than hardcoding a shape**, which is the whole reason this is
   * useful before the picker exists: it will render a home with no components and no meters, and a
   * boat with two engines, because that is what those templates say — not because this function
   * knows anything about houses or boats. A preset added later needs no change here.
   */
  private fun buildFromTemplate(
    template: ThingTemplate,
    thingId: String
  ): Thing {
    val spec = template.spec_fields.map { field ->
      Spec(key = field.key, value_ = sampleSpecValue(field.key, field.label))
    }
    return Thing(
      id = thingId,
      // Set explicitly: ThingInflater derives a name from tail_number or make/model, none of which
      // a home declares — so without this a fake house would arrive nameless.
      name = sampleName(template.id, spec),
      spec = spec,
      components = template.component_slots.flatMap { buildSlot(it) },
      template = template,
    ).withDerivedComponentIds()
  }

  /**
   * Instantiates a slot, several times when it repeats — one of anything hides off-by-one bugs.
   *
   * **Fills the slot's own declared fields too**, not just make/model/serial. A fake car whose
   * tyres had no position or pressure was a car that could not show what the template had just
   * been taught to ask for — the fixture has to exercise the whole slot or it proves nothing.
   */
  private fun buildSlot(slot: ComponentSlot): List<Component> =
    (0 until instanceCount(slot)).map { index ->
      Component(
        slot_key = slot.slot_key,
        make = SampleNames.MAKES.random(),
        model = "${('A'..'Z').random()}${(100..999).random()}",
        serial = if (slot.serial_expected) {
          "${
            slot.slot_key.take(2)
              .uppercase()
          }${index + 1}-${(10000..99999).random()}"
        } else {
          ""
        },
        spec = slot.spec_fields.mapNotNull { field ->
          sampleComponentSpec(field, index)
            .takeIf { it.isNotBlank() }
            ?.let { Spec(key = field.key, value_ = it) }
        },
        children = slot.children.flatMap { buildSlot(it) },
      )
    }

  /**
   * How many of a repeating slot to make up.
   *
   * Two by default, capped by `max_instances` — without that cap a fake car arrived with two
   * engines, which the template had just said it cannot have.
   *
   * A slot whose naming field enumerates its positions makes one PER POSITION instead, up to four:
   * a car with four tyres, one at each corner, is the shape the dashboard's grid exists for, and
   * two would have demonstrated half of it. A bike's cap of two trims the same rule to a front
   * and a rear.
   */
  private fun instanceCount(slot: ComponentSlot): Int {
    if (!slot.repeatable) return 1
    val positions =
      slot.spec_fields.firstOrNull { it.title_candidate }?.options.orEmpty()
    val wanted = if (positions.isEmpty()) 2 else positions.size.coerceAtMost(4)
    return if (slot.max_instances > 0) wanted.coerceAtMost(slot.max_instances) else wanted
  }

  /**
   * A value for one of a slot's declared fields.
   *
   * Options are taken IN ORDER by instance, not at random: four tyres want front left, front
   * right, rear left and rear right — four random picks would put two wheels in the same corner,
   * which is exactly the data an owner could never enter.
   */
  private fun sampleComponentSpec(field: SpecField, index: Int): String = when {
    field.options.isNotEmpty() -> field.options.getOrElse(index) { "" }
    field.numeric -> field.placeholder.ifBlank {
      (10..99).random()
        .toString()
    }

    else -> "Sample ${field.label}"
  }

  private fun sampleSpecValue(key: String, label: String): String = when (key) {
    SpecKeys.MAKE -> SampleNames.MAKES.random()
    SpecKeys.MODEL -> "${('A'..'Z').random()}${(100..999).random()}"
    SpecKeys.SERIAL -> "S${(10000..99999).random()}"
    SpecKeys.TAIL_NUMBER -> "N${(1000..9999).random()}${('A'..'Z').random()}"
    "vin" -> "1${('A'..'Z').random()}GBH41JXMN${(100000..999999).random()}"
    "hull_id" -> "${('A'..'Z').random()}BC${(10000..99999).random()}D616"
    "frame_number" -> "WTU${(100..999).random()}K${(1000..9999).random()}Z"
    "year", "year_built" -> (1960..2024).random()
      .toString()

    "address" -> "${(100..9999).random()} ${SampleNames.STREETS.random()}"
    // Deliberately generic: a preset added later gets something readable without editing this.
    else -> "Sample $label"
  }

  private fun sampleName(templateId: String, spec: List<Spec>): String {
    val byKey = spec.associate { it.key to it.value_ }
    return byKey["address"]
      ?: listOfNotNull(byKey[SpecKeys.MAKE], byKey[SpecKeys.MODEL])
        .joinToString(" ")
        .ifBlank { "Sample ${templateId.replaceFirstChar { it.uppercase() }}" }
  }

  private fun buildTechnicians(count: Int): List<Technician> {
    val names = SampleNames.TECHNICIAN_NAMES
    val shuffled = names.shuffled()
      .take(count.coerceAtMost(names.size))
    return shuffled.mapIndexed { index, name ->
      val isAmt = index % 3 != 2
      val certNumber =
        if (isAmt) "A${(1000000..9999999).random()}" else "R${(100000..999999).random()}"
      val expYearsAhead = (1..5).random()
      val expInstant = Clock.System.now() + (expYearsAhead * 365).days
      Technician(
        id = generateRandomId(),
        name = name,
        certifications = listOf(
          Certification(
            type = if (isAmt) FAA_AMT else FAA_REPAIRMAN,
            number = certNumber,
            expire_limit = CertExpireLimit.CERT_EXPIRE_LIMIT_EXPIRES,
            expiration = expInstant.toWireInstant(),
          )
        ),
      )
    }
  }

  private fun buildTasks(
    count: Int,
    now: Instant,
    fixtures: FakeDataPool,
  ): List<MaintenanceTask> {
    val pool = fixtures.tasks.shuffled()
      .take(count.coerceAtMost(fixtures.tasks.size))
    val overdueCreationInstant = now - (4 * 365).days

    return pool.mapIndexed { index, template ->
      val taskId = generateRandomId()
      val rule = template.rule
      val isOnCondition =
        rule.on_condition_rule != null || rule.immediate_rule != null
      // A meter rule's base comes from a logged reading, which does not exist yet at
      // task-creation time — so its due status is driven by an override instead.
      val isMeterBased = rule.meter_rule != null
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
        isMeterBased || isOnCondition -> overdueCreationInstant
        dueGroup == 2 -> overdueCreationInstant
        dueGroup == 0 && intervalDays >= 40 -> {
          val offsetDays =
            (40..minOf(intervalDays, 240L).toInt()).random()
              .toLong()
          now - intervalDays.days + offsetDays.days
        }

        else -> {
          // DUE SOON (also handles short-interval tasks demoted from NORMAL)
          val maxOffset =
            minOf(25L, intervalDays - 1).coerceAtLeast(1L)
          val offsetDays = (1..maxOffset.toInt()).random()
            .toLong()
          now - intervalDays.days + offsetDays.days
        }
      }

      val timeRule = rule.time_rule
      val timeRuleWithDate = when {
        timeRule != null -> InspectionRule(
          time_rule = timeRule.copy(creation_date = creationInstant.toWireInstant())
        )

        else -> rule
      }

      val forceDueDate = when {
        isOnCondition || !isMeterBased -> null
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
    thing: Thing,
    fixtures: FakeDataPool,
    startInstant: Instant,
    now: Instant,
  ): List<Squawk> {
    val pool = fixtures.squawks.shuffled()
      .take(count.coerceAtMost(fixtures.squawks.size))
    val span = now - startInstant
    return pool.mapIndexed { i, template ->
      val fraction =
        if (pool.size == 1) 0.5 else i.toDouble() / (pool.size - 1)
      val squawkInstant = startInstant + (span * fraction)
      Squawk(
        id = generateRandomId(),
        title = template.title,
        description = template.description,
        priority = template.priority,
        component_type = template.component,
        component_serial = componentSerialFor(thing, template.component),
        created_at = squawkInstant.toWireInstant(),
      )
    }
  }

  private fun buildLogs(
    count: Int,
    thing: Thing,
    template: ThingTemplate,
    fixtures: FakeDataPool,
    technicians: List<Technician>,
    tasks: List<MaintenanceTask>,
    squawks: List<Squawk>,
    startInstant: Instant,
    now: Instant,
  ): Pair<List<MaintenanceLog>, Map<String, String>> {

    val span = now - startInstant
    val pool = buildLogPool(count, fixtures.logs)
    val startEngineHours = (800..1200).random()
      .toDouble()
    val totalHoursFlown = (300..600).random()
      .toDouble()

    // Where each of this template's own meters starts and how far it climbs over the log span.
    // Fixed for the run so readings only ever increase — a meter that went backwards would make
    // every due calculation downstream nonsense. The aviation three are handled below instead,
    // so the airplane fixture keeps the exact numbers it always had.
    val meterRuns = template.meters.associate { meter ->
      meter.key to if (meter.decimal) {
        (400..900).random()
          .toDouble() to (200..500).random()
          .toDouble()
      } else {
        (20_000..60_000).random()
          .toDouble() to (30_000..60_000).random()
          .toDouble()
      }
    }

    // Hoisted: the log lambda below binds its own `template`, which is a log fixture, not this one.
    val thingMeters = template.meters

    val openSquawks = squawks.toMutableList()
    val addressedSquawks = mutableMapOf<String, String>()

    val addressedCount = (squawks.size * 0.45).toInt()
      .coerceAtLeast(1)
    val squawksToAddress = openSquawks.shuffled()
      .take(addressedCount)

    val logs = pool.mapIndexed { i, template ->
      val fraction = i.toDouble() / (pool.size - 1).coerceAtLeast(1)
      val logInstant = startInstant + (span * fraction)
      val engineHours = startEngineHours + (fraction * totalHoursFlown)
      val airframeTime = engineHours + (30..80).random()
      val propTime = engineHours - (5..20).random()

      // One reading per meter the template declares. Before this every fake log carried three
      // aeroplane hour fields and nothing else, so a generated car had no odometer to read.
      val readings = thingMeters.map { meter ->
        val value = when (meter.key) {
          MeterKeys.AIRFRAME_HOURS -> airframeTime
          MeterKeys.ENGINE_HOURS -> engineHours
          MeterKeys.PROP_HOURS -> propTime
          else -> {
            val (meterStart, meterSpan) = meterRuns.getValue(meter.key)
            meterStart + (fraction * meterSpan)
          }
        }
        MeterReading(meter_key = meter.key, value_ = value)
      }

      val logId = generateRandomId()
      val technician =
        if (technicians.isNotEmpty()) technicians.random() else null

      val matchedTaskIds = tasks.filter { task ->
        template.taskHints.any { hint ->
          task.title.contains(
            hint,
            ignoreCase = true
          )
        }
      }
        .map { it.id }

      val squawkIds = squawksToAddress
        .filter { sq ->
          sq.component_type == template.component && !addressedSquawks.containsKey(
            sq.id
          )
        }
        .take(1)
        .map { sq ->
          addressedSquawks[sq.id] = logId
          sq.id
        }

      MaintenanceLog(
        id = logId,
        timestamp = logInstant.toWireInstant(),
        work_description = template.description,
        component_type = template.component,
        component_serial = componentSerialFor(thing, template.component),
        readings = readings,
        inspection_ids = matchedTaskIds,
        squawk_ids = squawkIds,
        technician = technician,
        technician_id = technician?.id ?: "",
      )
    }

    val remainingOpenSquawks =
      squawks.filter { !addressedSquawks.containsKey(it.id) }
    val additionalToAddress = remainingOpenSquawks.shuffled()
      .take(
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

    return Pair(
      logs.sortedBy { it.timestamp?.getEpochSecond() ?: 0L },
      addressedSquawks
    )
  }

  private fun buildDismissedSquawks(
    squawks: List<Squawk>,
    addressedSquawks: Map<String, String>,
  ): Map<String, SquawkDismissReason> {
    val openIds = squawks
      .filter { !addressedSquawks.containsKey(it.id) }
      .map { it.id }
    val dismissCount = (openIds.size * 0.35).toInt()
      .coerceAtMost(openIds.size)
    val reasons = listOf(
      SquawkDismissReason.SQUAWK_DISMISS_REASON_OBSOLETE,
      SquawkDismissReason.SQUAWK_DISMISS_REASON_NOT_REPRODUCIBLE,
      SquawkDismissReason.SQUAWK_DISMISS_REASON_DUPLICATE,
    )
    return openIds.shuffled()
      .take(dismissCount)
      .associateWith { reasons.random() }
  }

  /** [count] logs, cycling the fixtures when asked for more than there are. */
  private fun buildLogPool(count: Int, fixtures: List<LogTemplate>): List<LogTemplate> {
    if (count <= fixtures.size) return fixtures.shuffled()
      .take(count)
    val pool = mutableListOf<LogTemplate>()
    while (pool.size < count) pool.addAll(fixtures.shuffled())
    return pool.take(count)
  }

  /**
   * The serial a record filed against [component] carries, mirroring the forms: the first engine
   * or propeller, the thing's own serial for the airframe, and nothing for a preset whose records
   * the type enum does not describe.
   */
  private fun componentSerialFor(thing: Thing, component: ComponentType): String =
    when (component) {
      ComponentType.COMPONENT_ENGINE ->
        thing.allComponentsInSlot(SlotKeys.ENGINE)
          .firstOrNull()?.serial
          ?: thing.specValue(SpecKeys.SERIAL)

      ComponentType.COMPONENT_PROPELLER ->
        thing.allComponentsInSlot(SlotKeys.PROPELLER)
          .firstOrNull()?.serial
          ?: thing.specValue(SpecKeys.SERIAL)

      ComponentType.COMPONENT_AIRFRAME -> thing.specValue(SpecKeys.SERIAL)

      else -> ""
    }
}
