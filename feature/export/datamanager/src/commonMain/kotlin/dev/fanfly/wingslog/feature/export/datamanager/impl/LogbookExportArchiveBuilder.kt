package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.aircraft.CertExpireLimit
import dev.fanfly.wingslog.aircraft.CertificateType
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.Engine
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.aircraft.Propeller
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.SquawkDismissReason
import dev.fanfly.wingslog.aircraft.SquawkPriority
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.feature.export.datamanager.ExportDateRange
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import com.squareup.wire.Instant as WireInstant

/**
 * Builds the CSV entries that make up a Hopply logbook export archive.
 */
class LogbookExportArchiveBuilder(
  private val appVersion: String = GENERATED_EXPORT_APP_VERSION,
  private val readmeTemplate: String = GENERATED_EXPORT_README_TEMPLATE,
) {

  /**
   * Creates all ZIP entry payloads for [bundles], including fleet summary and README files.
   */
  fun buildEntries(
    request: ExportRequest,
    bundles: List<AircraftBundle>,
    attachmentManifests: Map<String, AttachmentExportManifest> = emptyMap(),
    generatedAt: LocalDateTime,
    timeZone: TimeZone,
  ): List<ZipEntryPayload> {
    val multiAircraft = bundles.size > 1
    val entries = mutableListOf<ZipEntryPayload>()

    if (multiAircraft) {
      entries += csvEntry(
        path = "00_Fleet_Summary.csv",
        rows = fleetSummaryRows(bundles),
      )
    }

    bundles.forEach { bundle ->
      val folder = if (multiAircraft) "${bundle.aircraft.folderName()}/" else ""
      val attachments = attachmentManifests[bundle.aircraft.id] ?: AttachmentExportManifest(
        byAttachmentId = emptyMap(),
        notes = emptyList(),
      )
      entries += csvEntry("${folder}00_Aircraft_Info.csv", aircraftInfoRows(bundle, request, generatedAt, timeZone))
      entries += csvEntry("${folder}01_Airframe.csv", airframeRows(bundle, attachments, timeZone))
      bundle.aircraft.engine.forEachIndexed { index, engine ->
        entries += csvEntry("${folder}02_Engine_${index + 1}.csv", engineRows(bundle, attachments, engine, index, timeZone))
        entries += csvEntry(
          "${folder}03_Propeller_${index + 1}.csv",
          propellerRows(bundle, attachments, engine.propeller, index, timeZone),
        )
      }
      if (bundle.aircraft.engine.isEmpty()) {
        entries += csvEntry("${folder}02_Engine_Unknown.csv", engineRows(bundle, attachments, null, 0, timeZone))
        entries += csvEntry("${folder}03_Propeller_Unknown.csv", propellerRows(bundle, attachments, null, 0, timeZone))
      }
      entries += csvEntry("${folder}10_Compliance.csv", complianceRows(bundle, timeZone))
      entries += csvEntry("${folder}11_Squawks.csv", squawkRows(bundle, timeZone))
      entries += csvEntry("${folder}20_Technicians.csv", technicianRows(bundle, timeZone))
      entries += textEntry("${folder}README.txt", readme(bundle, request, attachments, generatedAt, timeZone))
      attachments.byAttachmentId.values.forEach { payload ->
        entries += ZipEntryPayload(
          path = "$folder${payload.relativePath}",
          bytes = payload.bytes,
        )
      }
    }

    return entries
  }

  /**
   * Returns the PRD filename for a single-aircraft or fleet export.
   */
  fun fileName(bundles: List<AircraftBundle>, date: LocalDate): String {
    val stamp = date.compact()
    val subject = if (bundles.size == 1) bundles.first().aircraft.safeTailNumber() else "Fleet"
    return "Hopply_Logs_${subject}_$stamp.zip"
  }

  private fun aircraftInfoRows(
    bundle: AircraftBundle,
    request: ExportRequest,
    generatedAt: LocalDateTime,
    timeZone: TimeZone,
  ): List<List<String>> {
    val aircraft = bundle.aircraft
    val openSquawks = bundle.squawks.count { it.statusLabel() == "Open" }
    val latestLog = bundle.logs.maxByOrNull { it.timestamp?.getEpochSecond() ?: Long.MIN_VALUE }
    return listOf(
      listOf("Field", "Value"),
      listOf("Tail Number", aircraft.tail_number),
      listOf("Make", aircraft.make),
      listOf("Model", aircraft.model),
      listOf("Serial Number", aircraft.serial),
      listOf("Engines", aircraft.engine.size.toString()),
      listOf("Propellers", aircraft.engine.count { it.propeller != null }.toString()),
      listOf("Current Airframe Time", latestLog?.airframe_time.formatHours()),
      listOf("Current Engine 1 Time", latestLog?.engine_hour.formatHours()),
      listOf("Current Propeller 1 Time", latestLog?.prop_time.formatHours()),
      listOf("Total Log Entries", bundle.logs.size.toString()),
      listOf("Total Squawks", bundle.squawks.size.toString()),
      listOf("Open Squawks", openSquawks.toString()),
      listOf("Export Generated", generatedAt.exportTimestamp(timeZone)),
      listOf("Export Period", request.dateRange.label()),
      listOf("Export App Version", appVersion),
    )
  }

  private fun airframeRows(
    bundle: AircraftBundle,
    attachments: AttachmentExportManifest,
    timeZone: TimeZone,
  ): List<List<String>> =
    buildList {
      add(
        listOf(
          "Date",
          "Airframe Time",
          "Engine 1 Time",
          "Work Description",
          "Inspections",
          "Reference Numbers",
          "Squawks Addressed",
          "Technician",
          "Cert Type",
          "Cert #",
          "Attachments",
        )
      )
      bundle.logs
        .filter { it.component_type == ComponentType.COMPONENT_AIRFRAME }
        .forEach { add(logRow(bundle, attachments, it, it.airframe_time, it.engine_hour, timeZone)) }
    }

  private fun engineRows(
    bundle: AircraftBundle,
    attachments: AttachmentExportManifest,
    engine: Engine?,
    index: Int,
    timeZone: TimeZone,
  ): List<List<String>> =
    buildList {
      add(listOf("Engine Position", (index + 1).toString()))
      add(listOf("Make", engine?.make.orEmpty()))
      add(listOf("Model", engine?.model.orEmpty()))
      add(listOf("Serial", engine?.serial.orEmpty()))
      add(emptyList())
      add(
        listOf(
          "Date",
          "Engine Time",
          "Airframe Time",
          "Work Description",
          "Inspections",
          "Reference Numbers",
          "Squawks Addressed",
          "Technician",
          "Cert Type",
          "Cert #",
          "Attachments",
        )
      )
      val serial = engine?.serial.orEmpty()
      bundle.logs
        .filter {
          it.component_type == ComponentType.COMPONENT_ENGINE &&
            (serial.isBlank() || it.component_serial == serial)
        }
        .forEach { add(logRow(bundle, attachments, it, it.engine_hour, it.airframe_time, timeZone)) }
    }

  private fun propellerRows(
    bundle: AircraftBundle,
    attachments: AttachmentExportManifest,
    propeller: Propeller?,
    index: Int,
    timeZone: TimeZone,
  ): List<List<String>> =
    buildList {
      val hub = propeller?.hub
      add(listOf("Propeller Position", "${index + 1} (Engine ${index + 1})"))
      add(listOf("Hub Make", hub?.make.orEmpty()))
      add(listOf("Hub Model", hub?.model.orEmpty()))
      add(listOf("Hub Serial", hub?.serial.orEmpty()))
      propeller?.blades.orEmpty().forEachIndexed { bladeIndex, blade ->
        add(listOf("Blade ${bladeIndex + 1} Make", blade.make))
        add(listOf("Blade ${bladeIndex + 1} Model", blade.model))
        add(listOf("Blade ${bladeIndex + 1} Serial", blade.serial))
      }
      add(emptyList())
      add(
        listOf(
          "Date",
          "Prop Time",
          "Airframe Time",
          "Work Description",
          "Inspections",
          "Reference Numbers",
          "Technician",
          "Cert Type",
          "Cert #",
          "Attachments",
        )
      )
      val serial = hub?.serial.orEmpty()
      bundle.logs
        .filter {
          it.component_type == ComponentType.COMPONENT_PROPELLER &&
            (serial.isBlank() || it.component_serial == serial)
        }
        .forEach { log ->
          val technician = log.resolveTechnician(bundle)
          add(
            listOf(
              log.timestamp.date(timeZone),
              log.prop_time.formatHours(),
              log.airframe_time.formatHours(),
              log.work_description,
              log.inspectionTitles(bundle),
              log.referenceNumbers(bundle),
              technician?.name.orEmpty(),
              technician.certTypeLabel(),
              technician?.cert_number.orEmpty(),
              log.attachments.attachmentCell(attachments),
            )
          )
        }
    }

  private fun complianceRows(bundle: AircraftBundle, timeZone: TimeZone): List<List<String>> =
    buildList {
      add(
        listOf(
          "Title",
          "Component",
          "Type",
          "Reference #",
          "Authority",
          "Schedule",
          "Last Complied - Date",
          "Last Complied - Hours",
          "Next Due - Date",
          "Next Due - Hours",
          "Status",
          "One-Time",
          "Notes",
          "Compliance Details",
        )
      )
      bundle.tasks.forEach { task ->
        val due = bundle.dueByTaskId[task.id]
        val lastLog = bundle.lastCompliedByTaskId[task.id]
        add(
          listOf(
            task.title.orEmpty(),
            task.component.label(),
            task.type.label(),
            task.reference_number.orEmpty(),
            task.compliance_authority.orEmpty(),
            task.rules.scheduleLabel(bundle),
            (lastLog?.timestamp).date(timeZone),
            lastLog?.componentHours(task.component).formatHours(),
            due?.nextDueDate?.toString().orEmpty(),
            (due?.nextDueEngine).formatHours(),
            due?.status?.label(task.is_one_time).orEmpty(),
            if (task.is_one_time) "Yes" else "No",
            task.notes.orEmpty(),
            task.compliance_details.orEmpty(),
          )
        )
      }
    }

  private fun squawkRows(bundle: AircraftBundle, timeZone: TimeZone): List<List<String>> =
    buildList {
      add(
        listOf(
          "Created",
          "Title",
          "Description",
          "Priority",
          "Component",
          "Component Serial",
          "Status",
          "Addressed By - Date",
          "Dismiss Reason",
          "Dismissed",
        )
      )
      bundle.squawks.forEach { squawk ->
        val addressedLog = bundle.logs.firstOrNull { it.id == squawk.addressed_by_log_id }
        add(
          listOf(
            squawk.created_at.date(timeZone),
            squawk.title,
            squawk.description,
            squawk.priority.label(),
            squawk.component_type.label(),
            squawk.component_serial,
            squawk.statusLabel(),
            addressedLog?.timestamp.date(timeZone),
            squawk.dismiss_reason.label(),
            squawk.dismissed_at.date(timeZone),
          )
        )
      }
    }

  private fun technicianRows(bundle: AircraftBundle, timeZone: TimeZone): List<List<String>> =
    buildList {
      add(listOf("Name", "Cert Type", "Cert #", "Cert Expiration"))
      bundle.techniciansById.values.sortedBy { it.name }.forEach { technician ->
        add(
          listOf(
            technician.name,
            technician.certTypeLabel(),
            technician.cert_number,
            if (technician.cert_expire_limit == CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES) {
              "Never expires"
            } else {
              technician.cert_expiration.date(timeZone)
            },
          )
        )
      }
    }

  private fun fleetSummaryRows(bundles: List<AircraftBundle>): List<List<String>> =
    buildList {
      add(
        listOf(
          "Tail Number",
          "Make",
          "Model",
          "Serial Number",
          "Engines",
          "Propellers",
          "Log Entries (in export)",
          "Open Squawks",
          "Compliance Tasks",
          "Folder",
        )
      )
      bundles.forEach { bundle ->
        add(
          listOf(
            bundle.aircraft.tail_number,
            bundle.aircraft.make,
            bundle.aircraft.model,
            bundle.aircraft.serial,
            bundle.aircraft.engine.size.toString(),
            bundle.aircraft.engine.count { it.propeller != null }.toString(),
            bundle.logs.size.toString(),
            bundle.squawks.count { it.statusLabel() == "Open" }.toString(),
            bundle.tasks.size.toString(),
            bundle.aircraft.folderName(),
          )
        )
      }
    }

  private fun readme(
    bundle: AircraftBundle,
    request: ExportRequest,
    attachments: AttachmentExportManifest,
    generatedAt: LocalDateTime,
    timeZone: TimeZone,
  ): String {
    val attachmentNotes = attachments.notes
      .takeIf { it.isNotEmpty() }
      ?.joinToString(separator = "\n") { "- $it" }
      ?.let { "\nAttachment notes\n$it" }
      .orEmpty()
    return ReadmeTemplateRenderer(readmeTemplate).render(
      mapOf(
        "generated_at" to generatedAt.exportTimestamp(timeZone),
        "scope" to "${bundle.aircraft.make} ${bundle.aircraft.model} ${bundle.aircraft.tail_number}",
        "period" to request.dateRange.label(),
        "app_version" to appVersion,
        "attachment_notes" to attachmentNotes,
      )
    )
  }

  private fun logRow(
    bundle: AircraftBundle,
    attachments: AttachmentExportManifest,
    log: MaintenanceLog,
    primaryHours: Double,
    secondaryHours: Double,
    timeZone: TimeZone,
  ): List<String> {
    val technician = log.resolveTechnician(bundle)
    return listOf(
      log.timestamp.date(timeZone),
      primaryHours.formatHours(),
      secondaryHours.formatHours(),
      log.work_description,
      log.inspectionTitles(bundle),
      log.referenceNumbers(bundle),
      log.squawkTitles(bundle),
      technician?.name.orEmpty(),
      technician.certTypeLabel(),
      technician?.cert_number.orEmpty(),
      log.attachments.attachmentCell(attachments),
    )
  }

  private fun csvEntry(path: String, rows: List<List<String>>): ZipEntryPayload =
    textEntry(path, CsvWriter.write(rows))

  private fun textEntry(path: String, text: String): ZipEntryPayload =
    ZipEntryPayload(path = path, bytes = text.encodeToByteArray())

  private fun MaintenanceLog.resolveTechnician(bundle: AircraftBundle): Technician? =
    technician?.takeIf { it.name.isNotBlank() } ?: bundle.techniciansById[technician_id]

  private fun MaintenanceLog.inspectionTitles(bundle: AircraftBundle): String =
    inspection_ids.joinToString("\n") { id -> bundle.tasksById[id]?.title ?: "[deleted]" }

  private fun MaintenanceLog.referenceNumbers(bundle: AircraftBundle): String =
    inspection_ids.mapNotNull { id -> bundle.tasksById[id]?.reference_number?.takeIf(String::isNotBlank) }
      .joinToString("\n")

  private fun MaintenanceLog.squawkTitles(bundle: AircraftBundle): String =
    squawk_ids.joinToString("\n") { id -> bundle.squawksById[id]?.title ?: "[deleted]" }

  private fun List<Attachment>.attachmentCell(manifest: AttachmentExportManifest): String =
    joinToString("\n") { attachment ->
      val name = attachment.name.ifBlank { attachment.id.ifBlank { "Attachment" } }
      if (attachment.type == AttachmentType.ATTACHMENT_TYPE_LINK) {
        "$name -> ${attachment.url.ifBlank { attachment.download_url }}"
      } else {
        val payload = manifest.byAttachmentId[attachment.id]
        if (payload != null) "$name -> ${payload.relativePath}" else "$name -> [attachment unavailable]"
      }
    }

  private fun List<InspectionRule>.scheduleLabel(bundle: AircraftBundle): String =
    joinToString("\n") { rule ->
      when {
        rule.time_rule != null -> rule.time_rule!!.run {
          when {
            interval_days > 0 -> "Every $interval_days days"
            interval_years > 0 -> "Every $interval_years years"
            interval_months > 0 -> "Every $interval_months months"
            else -> "Every 12 months"
          }
        }
        rule.engine_hour_rule != null -> "Every ${rule.engine_hour_rule!!.interval_hours.formatHours()} engine hours"
        rule.on_condition_rule != null -> rule.on_condition_rule!!.description.ifBlank { "On condition" }
        rule.linked_rule != null -> "Linked to ${
          bundle.tasksById[rule.linked_rule!!.parent_inspection_id]?.title ?: "[deleted]"
        }"
        rule.immediate_rule != null -> "Immediate"
        else -> "Unknown"
      }
    }

  private fun MaintenanceLog.componentHours(componentType: ComponentType): Double =
    when (componentType) {
      ComponentType.COMPONENT_ENGINE -> engine_hour
      ComponentType.COMPONENT_PROPELLER -> prop_time
      ComponentType.COMPONENT_AIRFRAME,
      ComponentType.COMPONENT_UNKNOWN,
      -> airframe_time
    }

  private fun ExportDateRange.label(): String =
    when (this) {
      ExportDateRange.AllTime -> "All time"
      is ExportDateRange.LastNMonths -> "Last $months months"
      is ExportDateRange.Custom -> "$start -> $endInclusive"
    }

  private fun Aircraft.folderName(): String =
    "${safeTailNumber()}_${make}_${model}".sanitizePathSegment()

  private fun Aircraft.safeTailNumber(): String =
    tail_number.ifBlank { id.ifBlank { "Aircraft" } }.sanitizePathSegment()

  private fun ComponentType.label(): String =
    when (this) {
      ComponentType.COMPONENT_AIRFRAME -> "Airframe"
      ComponentType.COMPONENT_ENGINE -> "Engine"
      ComponentType.COMPONENT_PROPELLER -> "Propeller"
      ComponentType.COMPONENT_UNKNOWN -> "Unknown"
    }

  private fun ComplianceType.label(): String =
    when (this) {
      ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION -> "Routine Inspection"
      ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN -> "Service Bulletin"
      ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE -> "Airworthiness Directive"
    }

  private fun DueStatus.label(oneTime: Boolean): String =
    when (this) {
      DueStatus.NORMAL -> "OK"
      DueStatus.DUE_SOON -> "Due Soon"
      DueStatus.OVERDUE -> "Overdue"
      DueStatus.COMPLIED -> if (oneTime) "Complied (one-time)" else "Complied"
    }

  private fun SquawkPriority.label(): String =
    when (this) {
      SquawkPriority.SQUAWK_PRIORITY_LOW -> "Low"
      SquawkPriority.SQUAWK_PRIORITY_MEDIUM -> "Medium"
      SquawkPriority.SQUAWK_PRIORITY_HIGH -> "High"
      SquawkPriority.SQUAWK_PRIORITY_AOG -> "AOG"
      SquawkPriority.SQUAWK_PRIORITY_UNKNOWN -> "Unknown"
    }

  private fun SquawkDismissReason.label(): String =
    when (this) {
      SquawkDismissReason.SQUAWK_DISMISS_REASON_OBSOLETE -> "Obsolete"
      SquawkDismissReason.SQUAWK_DISMISS_REASON_NOT_REPRODUCIBLE -> "Not Reproducible"
      SquawkDismissReason.SQUAWK_DISMISS_REASON_DUPLICATE -> "Duplicate"
      SquawkDismissReason.SQUAWK_DISMISS_REASON_INTENDED_BEHAVIOR -> "Intended Behavior"
      SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN -> ""
    }

  private fun Squawk.statusLabel(): String =
    when {
      addressed_by_log_id.isNotBlank() -> "Addressed"
      dismiss_reason != SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN -> "Dismissed"
      else -> "Open"
    }

  private fun Technician?.certTypeLabel(): String =
    when (this?.certificate_type) {
      CertificateType.CERTIFICATE_TYPE_REPAIRMAN -> "Repairman"
      CertificateType.CERTIFICATE_TYPE_AMT -> this.cert_type.ifBlank { "A&P" }
      CertificateType.CERTIFICATE_TYPE_NONE,
      null,
      -> this?.cert_type.orEmpty()
    }

  private fun WireInstant?.date(timeZone: TimeZone): String =
    this?.toLocalDate(timeZone)?.toString().orEmpty()

  private fun LocalDate.compact(): String =
    "${year.toString().padStart(4, '0')}${monthNumber.toString().padStart(2, '0')}${
      day.toString().padStart(2, '0')
    }"

  private fun LocalDateTime.exportTimestamp(timeZone: TimeZone): String =
    "${date} ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} ${timeZone.id}"

  private fun Double?.formatHours(): String =
    this?.takeIf { it > 0.0 }?.let { (kotlin.math.round(it * 10.0) / 10.0).toString() }.orEmpty()

  private fun Float?.formatHours(): String =
    this?.takeIf { it > 0f }?.let { (kotlin.math.round(it * 10f) / 10f).toString() }.orEmpty()

  private fun String.sanitizePathSegment(): String =
    replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "Aircraft" }
}
