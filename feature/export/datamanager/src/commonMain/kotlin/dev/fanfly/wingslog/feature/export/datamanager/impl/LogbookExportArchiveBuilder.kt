package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.aircraft.CertExpireLimit
import dev.fanfly.wingslog.aircraft.CertificateType
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.Engine
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.Propeller
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.SquawkDismissReason
import dev.fanfly.wingslog.aircraft.SquawkPriority
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.feature.export.datamanager.ExportDateRange
import dev.fanfly.wingslog.feature.export.datamanager.ExportFormat
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
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
  private val xlsxWorkbookWriter: XlsxWorkbookWriter = XlsxWorkbookWriter(),
  private val aircraftPdfWriter: AircraftPdfWriter = PdfExportWriter(),
) {

  /**
   * Creates all ZIP entry payloads for [bundles] using a root README and one directory per aircraft.
   */
  fun buildEntries(
    request: ExportRequest,
    bundles: List<AircraftBundle>,
    attachmentManifests: Map<String, AttachmentExportManifest> = emptyMap(),
    generatedAt: LocalDateTime,
    timeZone: TimeZone,
  ): List<ZipEntryPayload> {
    val entries = mutableListOf<ZipEntryPayload>()
    val aircraftExports = bundles.map { bundle ->
      aircraftExport(
        request = request,
        bundle = bundle,
        attachments = attachmentManifests[bundle.aircraft.id]
          ?: AttachmentExportManifest(
            byAttachmentId = emptyMap(),
            notes = emptyList(),
          ),
        generatedAt = generatedAt,
        timeZone = timeZone,
      )
    }
    // Falls back to all formats so callers that omit the field keep the original behaviour.
    val formats = request.formats.ifEmpty { ExportFormat.ALL }
    entries += textEntry(
      "README.txt",
      readme(
        bundles,
        request,
        attachmentManifests,
        generatedAt,
        timeZone
      )
    )
    aircraftExports.forEach { export ->
      val aircraftFolder = export.bundle.aircraft.folderName()
      if (ExportFormat.CSV in formats) {
        export.tables.forEach { table ->
          entries += csvEntry(
            "$aircraftFolder/csv/${table.csvPath}",
            table.rows
          )
        }
      }
      if (ExportFormat.XLSX in formats) {
        entries += ZipEntryPayload(
          path = "$aircraftFolder/${
            workbookFileName(
              export.bundle,
              generatedAt.date
            )
          }",
          bytes = xlsxWorkbookWriter.write(export.tables.map {
            XlsxSheet(
              name = it.sheetName,
              rows = it.rows
            )
          }),
        )
      }
      if (ExportFormat.PDF in formats) {
        entries += ZipEntryPayload(
          path = "$aircraftFolder/${export.bundle.aircraft.folderName()}.pdf",
          bytes = aircraftPdfWriter.write(
            buildPdfDocument(
              export = export,
              request = request,
              generatedAt = generatedAt,
              timeZone = timeZone,
            )
          ),
        )
      }
      export.attachments.byAttachmentId.values.forEach { payload ->
        entries += ZipEntryPayload(
          path = "$aircraftFolder/${payload.relativePath}",
          bytes = payload.bytes,
        )
      }
    }

    return entries
  }

  private fun aircraftExport(
    request: ExportRequest,
    bundle: AircraftBundle,
    attachments: AttachmentExportManifest,
    generatedAt: LocalDateTime,
    timeZone: TimeZone,
  ): AircraftExport {
    return AircraftExport(
      bundle = bundle,
      attachments = attachments,
      sheetPrefix = "",
      tables = buildList {
        add(
          LogbookExportTable(
            csvPath = "00_Aircraft_Info.csv",
            sheetName = "00 Aircraft Info",
            rows = aircraftInfoRows(bundle, request, generatedAt, timeZone),
          )
        )
        add(
          LogbookExportTable(
            csvPath = "01_Airframe.csv",
            sheetName = "01 Airframe",
            rows = airframeRows(bundle, attachments, timeZone),
          )
        )
        bundle.aircraft.engine.forEachIndexed { index, engine ->
          add(
            LogbookExportTable(
              csvPath = engineCsvName(bundle.aircraft, index),
              sheetName = engineSheetName(bundle.aircraft, index),
              rows = engineRows(bundle, attachments, engine, index, timeZone),
            )
          )
          add(
            LogbookExportTable(
              csvPath = propellerCsvName(bundle.aircraft, index),
              sheetName = propellerSheetName(bundle.aircraft, index),
              rows = propellerRows(
                bundle,
                attachments,
                engine.propeller,
                index,
                timeZone
              ),
            )
          )
        }
        if (bundle.aircraft.engine.isEmpty()) {
          add(
            LogbookExportTable(
              csvPath = "02_Engine_Unknown.csv",
              sheetName = "02 Engine Unknown",
              rows = engineRows(bundle, attachments, null, 0, timeZone),
            )
          )
          add(
            LogbookExportTable(
              csvPath = "03_Propeller_Unknown.csv",
              sheetName = "03 Prop Unknown",
              rows = propellerRows(bundle, attachments, null, 0, timeZone),
            )
          )
        }
        add(
          LogbookExportTable(
            csvPath = "10_Tasks.csv",
            sheetName = "10 Tasks",
            rows = complianceRows(bundle, timeZone),
          )
        )
        add(
          LogbookExportTable(
            csvPath = "11_Squawks.csv",
            sheetName = "11 Squawks",
            rows = squawkRows(bundle, timeZone),
          )
        )
        add(
          LogbookExportTable(
            csvPath = "20_Technicians.csv",
            sheetName = "20 Technicians",
            rows = technicianRows(bundle, timeZone),
          )
        )
      },
    )
  }

  /**
   * Returns the PRD filename for a single-aircraft or fleet export.
   */
  fun fileName(bundles: List<AircraftBundle>, date: LocalDate): String {
    val stamp = date.compact()
    val subject =
      if (bundles.size == 1) bundles.first().aircraft.safeTailNumber() else "Fleet"
    return "Hopply_Logs_${subject}_$stamp.zip"
  }

  private fun workbookFileName(
    bundle: AircraftBundle,
    date: LocalDate
  ): String =
    fileName(listOf(bundle), date).removeSuffix(".zip") + ".xlsx"

  private fun aircraftInfoRows(
    bundle: AircraftBundle,
    request: ExportRequest,
    generatedAt: LocalDateTime,
    timeZone: TimeZone,
  ): List<List<String>> {
    val aircraft = bundle.aircraft
    val openSquawks = bundle.squawks.count { it.statusLabel() == "Open" }
    val latestLog = bundle.logs.maxByOrNull {
      it.timestamp?.getEpochSecond() ?: Long.MIN_VALUE
    }
    val engineTimeLabel =
      if (aircraft.engine.size <= 1) "Current Engine Time" else "Current Engine 1 Time"
    val propellerTimeLabel =
      if (aircraft.engine.count { it.propeller != null } <= 1) {
        "Current Propeller Time"
      } else {
        "Current Propeller 1 Time"
      }
    return listOf(
      listOf("Field", "Value"),
      listOf("Tail Number", aircraft.tail_number),
      listOf("Make", aircraft.make),
      listOf("Model", aircraft.model),
      listOf("Serial Number", aircraft.serial),
      listOf("Engines", aircraft.engine.size.toString()),
      listOf(
        "Propellers",
        aircraft.engine.count { it.propeller != null }
          .toString()
      ),
      listOf("Current Airframe Time", latestLog?.airframe_time.formatHours()),
      listOf(engineTimeLabel, latestLog?.engine_hour.formatHours()),
      listOf(propellerTimeLabel, latestLog?.prop_time.formatHours()),
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
      val engineTimeHeader =
        if (bundle.aircraft.engine.size <= 1) "Engine Time" else "Engine 1 Time"
      add(
        listOf(
          "Date",
          "Airframe Time",
          engineTimeHeader,
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
        .forEach {
          add(
            logRow(
              bundle,
              attachments,
              it,
              it.airframe_time,
              it.engine_hour,
              timeZone
            )
          )
        }
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
        .forEach {
          add(
            logRow(
              bundle,
              attachments,
              it,
              it.engine_hour,
              it.airframe_time,
              timeZone
            )
          )
        }
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
      propeller?.blades.orEmpty()
        .forEachIndexed { bladeIndex, blade ->
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

  private fun complianceRows(
    bundle: AircraftBundle,
    timeZone: TimeZone
  ): List<List<String>> =
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
          "One-Time",
          "Notes",
          "Task Details",
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
            lastLog?.componentHours(task.component)
              .formatHours(),
            due?.nextDueDate?.toString()
              .orEmpty(),
            (due?.nextDueEngine).formatHours(),
            if (task.is_one_time) "Yes" else "No",
            task.notes.orEmpty(),
            task.compliance_details.orEmpty(),
          )
        )
      }
    }

  private fun squawkRows(
    bundle: AircraftBundle,
    timeZone: TimeZone
  ): List<List<String>> =
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
          "Action Date",
        )
      )
      bundle.squawks.forEach { squawk ->
        add(
          listOf(
            squawk.created_at.date(timeZone),
            squawk.title,
            squawk.description,
            squawk.priority.label(),
            squawk.component_type.label(),
            squawk.component_serial,
            squawk.statusLabel(),
            squawk.actionDate(bundle, timeZone),
          )
        )
      }
    }

  private fun technicianRows(
    bundle: AircraftBundle,
    timeZone: TimeZone
  ): List<List<String>> =
    buildList {
      add(listOf("Name", "Cert Type", "Cert #", "Cert Expiration"))
      bundle.techniciansById.values.sortedBy { it.name }
        .forEach { technician ->
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
          "Tasks",
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
            bundle.aircraft.engine.count { it.propeller != null }
              .toString(),
            bundle.logs.size.toString(),
            bundle.squawks.count { it.statusLabel() == "Open" }
              .toString(),
            bundle.tasks.size.toString(),
            bundle.aircraft.folderName(),
          )
        )
      }
    }

  private fun readme(
    bundles: List<AircraftBundle>,
    request: ExportRequest,
    attachmentManifests: Map<String, AttachmentExportManifest>,
    generatedAt: LocalDateTime,
    timeZone: TimeZone,
  ): String {
    val attachmentNotes = bundles.mapNotNull { bundle ->
      val notes = attachmentManifests[bundle.aircraft.id]?.notes.orEmpty()
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "\n") { "- $it" }
      notes?.let { "${bundle.aircraft.folderName()}\n$it" }
    }
      .takeIf { it.isNotEmpty() }
      ?.joinToString(separator = "\n\n")
      ?.let { "\nAttachment notes\n$it" }
      .orEmpty()
    val scope = if (bundles.size == 1) {
      bundles.first().aircraft.run { "$make $model $tail_number" }
    } else {
      "${bundles.size} aircraft"
    }
    return ReadmeTemplateRenderer(readmeTemplate).render(
      mapOf(
        "generated_at" to generatedAt.exportTimestamp(timeZone),
        "scope" to scope,
        "period" to request.dateRange.label(),
        "app_version" to appVersion,
        "attachment_notes" to attachmentNotes,
      )
    )
  }

  private fun buildPdfDocument(
    export: AircraftExport,
    request: ExportRequest,
    generatedAt: LocalDateTime,
    timeZone: TimeZone,
  ): AircraftPdfDocument {
    val aircraft = export.bundle.aircraft
    return AircraftPdfDocument(
      title = listOf(aircraft.make, aircraft.model, aircraft.tail_number)
        .filter { it.isNotBlank() }
        .joinToString(separator = " ")
        .ifBlank { aircraft.id.ifBlank { "Aircraft Export" } },
      subtitle = "Hopply logbook export PDF",
      summarySections = buildList {
        add(
          PdfSummarySection(
            title = "Export",
            cards = listOf(
              PdfSummaryCard(
                rows = listOf(
                  PdfSummaryRow(
                    "Generated",
                    generatedAt.exportTimestamp(timeZone)
                  ),
                  PdfSummaryRow("Period", request.dateRange.label()),
                  PdfSummaryRow("App Version", appVersion),
                  PdfSummaryRow(
                    "Attachment Notes",
                    export.attachments.notes.joinToString(separator = "\n")
                      .ifBlank { "None" }),
                )
              )
            ),
          )
        )
        add(
          PdfSummarySection(
            title = "Aircraft",
            cards = listOf(
              PdfSummaryCard(
                rows = listOf(
                  PdfSummaryRow(
                    "Tail Number",
                    aircraft.tail_number.ifBlank { aircraft.id }),
                  PdfSummaryRow("Make", aircraft.make),
                  PdfSummaryRow("Model", aircraft.model),
                  PdfSummaryRow("Serial Number", aircraft.serial),
                )
              )
            ),
          )
        )
        val componentCards = aircraft.engine.flatMapIndexed { index, engine ->
          buildList {
            add(
              PdfSummaryCard(
                title = engineCardTitle(aircraft, index),
                rows = listOf(
                  PdfSummaryRow("Make", engine.make),
                  PdfSummaryRow("Model", engine.model),
                  PdfSummaryRow("Serial", engine.serial),
                )
              )
            )
            engine.propeller?.let { propeller ->
              add(
                PdfSummaryCard(
                  title = propellerCardTitle(aircraft, index),
                  rows = buildList {
                    add(
                      PdfSummaryRow(
                        "Hub Make",
                        propeller.hub?.make.orEmpty()
                      )
                    )
                    add(
                      PdfSummaryRow(
                        "Hub Model",
                        propeller.hub?.model.orEmpty()
                      )
                    )
                    add(
                      PdfSummaryRow(
                        "Hub Serial",
                        propeller.hub?.serial.orEmpty()
                      )
                    )
                    propeller.blades.forEachIndexed { bladeIndex, blade ->
                      add(
                        PdfSummaryRow(
                          "Blade ${bladeIndex + 1}",
                          listOf(
                            blade.make,
                            blade.model,
                            blade.serial
                          ).filter { it.isNotBlank() }
                            .joinToString(" · ")
                        )
                      )
                    }
                  },
                )
              )
            }
          }
        }
        if (componentCards.isNotEmpty()) {
          add(PdfSummarySection(title = "Components", cards = componentCards))
        }
        val technicianCards = export.bundle.techniciansById.values
          .sortedBy { it.name }
          .map { technician ->
            PdfSummaryCard(
              title = technician.name.ifBlank { "Technician" },
              rows = listOf(
                PdfSummaryRow("Cert Type", technician.certTypeLabel()),
                PdfSummaryRow("Cert #", technician.cert_number),
                PdfSummaryRow(
                  "Cert Expiration",
                  if (technician.cert_expire_limit == CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES) {
                    "Never expires"
                  } else {
                    technician.cert_expiration.date(timeZone)
                  }
                ),
              ),
            )
          }
        if (technicianCards.isNotEmpty()) {
          add(PdfSummarySection(title = "Technicians", cards = technicianCards))
        }
      },
      tableSections = export.tables
        .filterNot {
          it.csvPath.endsWith("00_Aircraft_Info.csv") || it.csvPath.endsWith(
            "20_Technicians.csv"
          )
        }
        .map { table ->
          PdfTableSection(
            title = table.sheetName.removePrefix(export.sheetPrefix),
            rows = table.rows.dropMetadataPrelude(),
          )
        },
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

  private fun csvEntry(
    path: String,
    rows: List<List<String>>
  ): ZipEntryPayload =
    textEntry(path, CsvWriter.write(rows))

  private fun textEntry(path: String, text: String): ZipEntryPayload =
    ZipEntryPayload(path = path, bytes = text.encodeToByteArray())

  private fun MaintenanceLog.resolveTechnician(bundle: AircraftBundle): Technician? =
    technician?.takeIf { it.name.isNotBlank() }
      ?: bundle.techniciansById[technician_id]

  private fun engineCsvName(aircraft: Aircraft, index: Int): String =
    if (aircraft.engine.size <= 1) "02_Engine.csv" else "02_Engine_${index + 1}.csv"

  private fun propellerCsvName(aircraft: Aircraft, index: Int): String =
    if (aircraft.engine.count { it.propeller != null } <= 1) "03_Propeller.csv" else "03_Propeller_${index + 1}.csv"

  private fun engineSheetName(aircraft: Aircraft, index: Int): String =
    if (aircraft.engine.size <= 1) "02 Engine" else "02 Engine ${index + 1}"

  private fun propellerSheetName(aircraft: Aircraft, index: Int): String =
    if (aircraft.engine.count { it.propeller != null } <= 1) "03 Prop" else "03 Prop ${index + 1}"

  private fun engineCardTitle(aircraft: Aircraft, index: Int): String =
    if (aircraft.engine.size <= 1) "Engine" else "Engine ${index + 1}"

  private fun propellerCardTitle(aircraft: Aircraft, index: Int): String =
    if (aircraft.engine.count { it.propeller != null } <= 1) "Propeller" else "Propeller ${index + 1}"

  private fun MaintenanceLog.inspectionTitles(bundle: AircraftBundle): String =
    inspection_ids.joinToString("\n") { id ->
      bundle.tasksById[id]?.title ?: "[deleted]"
    }

  private fun MaintenanceLog.referenceNumbers(bundle: AircraftBundle): String =
    inspection_ids.mapNotNull { id ->
      bundle.tasksById[id]?.reference_number?.takeIf(
        String::isNotBlank
      )
    }
      .joinToString("\n")

  private fun MaintenanceLog.squawkTitles(bundle: AircraftBundle): String =
    squawk_ids.joinToString("\n") { id ->
      bundle.squawksById[id]?.title ?: "[deleted]"
    }

  private fun List<Attachment>.attachmentCell(manifest: AttachmentExportManifest): String =
    joinToString("\n") { attachment ->
      val name =
        attachment.name.ifBlank { attachment.id.ifBlank { "Attachment" } }
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
    tail_number.ifBlank { id.ifBlank { "Aircraft" } }
      .sanitizePathSegment()

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
      dismiss_reason != SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN -> "Dismissed - ${dismiss_reason.label()}"
      else -> "Open"
    }

  private fun Squawk.actionDate(
    bundle: AircraftBundle,
    timeZone: TimeZone
  ): String =
    when {
      addressed_by_log_id.isNotBlank() -> bundle.logs.firstOrNull { it.id == addressed_by_log_id }?.timestamp.date(
        timeZone
      )

      dismiss_reason != SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN -> dismissed_at.date(
        timeZone
      )

      else -> ""
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
    this?.toLocalDate(timeZone)
      ?.toString()
      .orEmpty()

  private fun LocalDate.compact(): String =
    "${
      year.toString()
        .padStart(4, '0')
    }${
      monthNumber.toString()
        .padStart(2, '0')
    }${
      day.toString()
        .padStart(2, '0')
    }"

  private fun LocalDateTime.exportTimestamp(timeZone: TimeZone): String =
    "${date} ${
      hour.toString()
        .padStart(2, '0')
    }:${
      minute.toString()
        .padStart(2, '0')
    } ${timeZone.id}"

  private fun Double?.formatHours(): String =
    this?.takeIf { it > 0.0 }
      ?.let { (kotlin.math.round(it * 10.0) / 10.0).toString() }
      .orEmpty()

  private fun Float?.formatHours(): String =
    this?.takeIf { it > 0f }
      ?.let { (kotlin.math.round(it * 10f) / 10f).toString() }
      .orEmpty()

  private fun String.sanitizePathSegment(): String =
    replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_')
      .ifBlank { "Aircraft" }

  private fun List<List<String>>.dropMetadataPrelude(): List<List<String>> {
    val headerIndex =
      indexOfFirst { row -> row.firstOrNull() == "Date" || row.firstOrNull() == "Title" || row.firstOrNull() == "Name" }
    return if (headerIndex > 0) drop(headerIndex) else this
  }

  private data class AircraftExport(
    val bundle: AircraftBundle,
    val attachments: AttachmentExportManifest,
    val sheetPrefix: String,
    val tables: List<LogbookExportTable>,
  )
}
