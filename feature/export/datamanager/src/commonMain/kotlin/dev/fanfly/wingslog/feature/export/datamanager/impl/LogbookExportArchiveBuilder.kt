package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.core.template.SlotKeys
import dev.fanfly.wingslog.core.template.SpecKeys
import dev.fanfly.wingslog.core.template.allComponentsInSlot
import dev.fanfly.wingslog.core.template.childInSlot
import dev.fanfly.wingslog.core.template.childrenInSlot
import dev.fanfly.wingslog.core.template.readingFor
import dev.fanfly.wingslog.core.template.specValue
import dev.fanfly.wingslog.feature.export.datamanager.ExportDateRange
import dev.fanfly.wingslog.feature.export.datamanager.ExportFormat
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.AttachmentType
import dev.fanfly.wingslog.thing.CertExpireLimit
import dev.fanfly.wingslog.thing.CertificateType
import dev.fanfly.wingslog.thing.ComplianceType
import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.InspectionRule
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.Squawk
import dev.fanfly.wingslog.thing.SquawkDismissReason
import dev.fanfly.wingslog.thing.SquawkPriority
import dev.fanfly.wingslog.thing.Technician
import dev.fanfly.wingslog.thing.Thing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import com.squareup.wire.Instant as WireInstant

/**
 * Builds the CSV entries that make up a SquawkIt logbook export archive.
 */
/**
 * The paper-logbook layout, which is aviation-only by design (`EXPORT_LAYOUT_LOGBOOK`) — it keeps
 * the airframe / engine / propeller hour concepts because that is what the columns are.
 *
 * It reaches them by meter key rather than by field, so a log that recorded only `readings` exports
 * the same numbers as one written before those existed (#761).
 */
class LogbookExportArchiveBuilder(
  private val appVersion: String = GENERATED_EXPORT_APP_VERSION,
  private val readmeTemplate: String = GENERATED_EXPORT_README_TEMPLATE,
  private val xlsxWorkbookWriter: XlsxWorkbookWriter = XlsxWorkbookWriter(),
  private val aircraftPdfWriter: AircraftPdfWriter = PdfExportWriter(),
) {

  /**
   * Creates all ZIP entry payloads for [bundles] using a root README and one directory per thing.
   */
  fun buildEntries(
    request: ExportRequest,
    bundles: List<ThingBundle>,
    attachmentManifests: Map<String, AttachmentExportManifest> = emptyMap(),
    generatedAt: LocalDateTime,
    timeZone: TimeZone,
  ): List<ZipEntryPayload> {
    val entries = mutableListOf<ZipEntryPayload>()
    val aircraftExports = bundles.map { bundle ->
      aircraftExport(
        request = request,
        bundle = bundle,
        attachments = attachmentManifests[bundle.thing.id]
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
      val thingFolder = export.bundle.thing.folderName()
      if (ExportFormat.CSV in formats) {
        export.tables.forEach { table ->
          entries += csvEntry(
            "$thingFolder/csv/${table.csvPath}",
            table.rows
          )
        }
      }
      if (ExportFormat.XLSX in formats) {
        entries += ZipEntryPayload(
          path = "$thingFolder/${
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
          path = "$thingFolder/${export.bundle.thing.folderName()}.pdf",
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
          path = "$thingFolder/${payload.relativePath}",
          bytes = payload.bytes,
        )
      }
    }

    return entries
  }

  private fun aircraftExport(
    request: ExportRequest,
    bundle: ThingBundle,
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
        bundle.thing.allComponentsInSlot(SlotKeys.ENGINE)
          .forEachIndexed { index, engine ->
            add(
              LogbookExportTable(
                csvPath = engineCsvName(bundle.thing, index),
                sheetName = engineSheetName(bundle.thing, index),
                rows = engineRows(bundle, attachments, engine, index, timeZone),
              )
            )
            add(
              LogbookExportTable(
                csvPath = propellerCsvName(bundle.thing, index),
                sheetName = propellerSheetName(bundle.thing, index),
                rows = propellerRows(
                  bundle,
                  attachments,
                  engine.childInSlot(SlotKeys.PROPELLER),
                  index,
                  timeZone
                ),
              )
            )
          }
        if (bundle.thing.allComponentsInSlot(SlotKeys.ENGINE)
            .isEmpty()
        ) {
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
   * Returns the PRD filename for a single-thing or fleet export.
   */
  fun fileName(bundles: List<ThingBundle>, date: LocalDate): String {
    val stamp = date.compact()
    val subject =
      if (bundles.size == 1) bundles.first().thing.safeTailNumber() else "Fleet"
    return "SquawkIt_Logs_${subject}_$stamp.zip"
  }

  private fun workbookFileName(
    bundle: ThingBundle,
    date: LocalDate
  ): String =
    fileName(listOf(bundle), date).removeSuffix(".zip") + ".xlsx"

  private fun aircraftInfoRows(
    bundle: ThingBundle,
    request: ExportRequest,
    generatedAt: LocalDateTime,
    timeZone: TimeZone,
  ): List<List<String>> {
    val thing = bundle.thing
    val openSquawks = bundle.squawks.count { it.statusLabel() == "Open" }
    val latestLog = bundle.logs.maxByOrNull {
      it.timestamp?.getEpochSecond() ?: Long.MIN_VALUE
    }
    val engineTimeLabel =
      if (thing.allComponentsInSlot(SlotKeys.ENGINE).size <= 1) "Current Engine Time"
      else "Current Engine 1 Time"
    val propellerTimeLabel =
      if (thing.allComponentsInSlot(SlotKeys.PROPELLER).size <= 1) {
        "Current Propeller Time"
      } else {
        "Current Propeller 1 Time"
      }
    return listOf(
      listOf("Field", "Value"),
      listOf("Tail Number", thing.specValue(SpecKeys.TAIL_NUMBER)),
      listOf("Make", thing.specValue(SpecKeys.MAKE)),
      listOf("Model", thing.specValue(SpecKeys.MODEL)),
      listOf("Serial Number", thing.specValue(SpecKeys.SERIAL)),
      listOf(
        "Engines",
        thing.allComponentsInSlot(SlotKeys.ENGINE).size.toString()
      ),
      listOf(
        "Propellers",
        thing.allComponentsInSlot(SlotKeys.PROPELLER).size
          .toString()
      ),
      listOf(
        "Current Airframe Time",
        latestLog?.readingFor(MeterKeys.AIRFRAME_HOURS)
          .formatHours()
      ),
      listOf(
        engineTimeLabel,
        latestLog?.readingFor(MeterKeys.ENGINE_HOURS)
          .formatHours()
      ),
      listOf(
        propellerTimeLabel,
        latestLog?.readingFor(MeterKeys.PROP_HOURS)
          .formatHours()
      ),
      listOf("Total Log Entries", bundle.logs.size.toString()),
      listOf("Total Squawks", bundle.squawks.size.toString()),
      listOf("Open Squawks", openSquawks.toString()),
      listOf("Export Generated", generatedAt.exportTimestamp(timeZone)),
      listOf("Export Period", request.dateRange.label()),
      listOf("Export App Version", appVersion),
    )
  }

  private fun airframeRows(
    bundle: ThingBundle,
    attachments: AttachmentExportManifest,
    timeZone: TimeZone,
  ): List<List<String>> =
    buildList {
      val engineTimeHeader =
        if (bundle.thing.allComponentsInSlot(SlotKeys.ENGINE).size <= 1) "Engine Time"
        else "Engine 1 Time"
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
              it.readingFor(MeterKeys.AIRFRAME_HOURS) ?: 0.0,
              it.readingFor(MeterKeys.ENGINE_HOURS) ?: 0.0,
              timeZone
            )
          )
        }
    }

  private fun engineRows(
    bundle: ThingBundle,
    attachments: AttachmentExportManifest,
    engine: Component?,
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
              it.readingFor(MeterKeys.ENGINE_HOURS) ?: 0.0,
              it.readingFor(MeterKeys.AIRFRAME_HOURS) ?: 0.0,
              timeZone
            )
          )
        }
    }

  private fun propellerRows(
    bundle: ThingBundle,
    attachments: AttachmentExportManifest,
    propeller: Component?,
    index: Int,
    timeZone: TimeZone,
  ): List<List<String>> =
    buildList {
      val hub = propeller
      add(listOf("Propeller Position", "${index + 1} (Engine ${index + 1})"))
      add(listOf("Hub Make", hub?.make.orEmpty()))
      add(listOf("Hub Model", hub?.model.orEmpty()))
      add(listOf("Hub Serial", hub?.serial.orEmpty()))
      propeller?.childrenInSlot(SlotKeys.BLADE)
        .orEmpty()
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
              log.readingFor(MeterKeys.PROP_HOURS)
                .formatHours(),
              log.readingFor(MeterKeys.AIRFRAME_HOURS)
                .formatHours(),
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
    bundle: ThingBundle,
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
            task.title,
            task.component.label(),
            task.type.label(),
            task.reference_number,
            task.compliance_authority,
            task.rules.scheduleLabel(bundle),
            (lastLog?.timestamp).date(timeZone),
            lastLog?.componentHours(task.component)
              .formatHours(),
            due?.nextDueDate?.toString()
              .orEmpty(),
            (due?.nextDueEngine).formatHours(),
            if (task.is_one_time) "Yes" else "No",
            task.notes,
            task.compliance_details,
          )
        )
      }
    }

  private fun squawkRows(
    bundle: ThingBundle,
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
    bundle: ThingBundle,
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

  private fun readme(
    bundles: List<ThingBundle>,
    request: ExportRequest,
    attachmentManifests: Map<String, AttachmentExportManifest>,
    generatedAt: LocalDateTime,
    timeZone: TimeZone,
  ): String {
    val attachmentNotes = bundles.mapNotNull { bundle ->
      val notes = attachmentManifests[bundle.thing.id]?.notes.orEmpty()
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "\n") { "- $it" }
      notes?.let { "${bundle.thing.folderName()}\n$it" }
    }
      .takeIf { it.isNotEmpty() }
      ?.joinToString(separator = "\n\n")
      ?.let { "\nAttachment notes\n$it" }
      .orEmpty()
    val scope = if (bundles.size == 1) {
      bundles.first().thing.run {
        "${specValue(SpecKeys.MAKE)} ${specValue(SpecKeys.MODEL)} ${
          specValue(
            SpecKeys.TAIL_NUMBER
          )
        }"
      }
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
    val thing = export.bundle.thing
    return AircraftPdfDocument(
      title = listOf(
        thing.specValue(SpecKeys.MAKE),
        thing.specValue(SpecKeys.MODEL),
        thing.specValue(SpecKeys.TAIL_NUMBER)
      )
        .filter { it.isNotBlank() }
        .joinToString(separator = " ")
        .ifBlank { thing.id.ifBlank { "Aircraft Export" } },
      subtitle = "SquawkIt logbook export PDF",
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
                    thing.specValue(SpecKeys.TAIL_NUMBER)
                      .ifBlank { thing.id }),
                  PdfSummaryRow("Make", thing.specValue(SpecKeys.MAKE)),
                  PdfSummaryRow("Model", thing.specValue(SpecKeys.MODEL)),
                  PdfSummaryRow(
                    "Serial Number",
                    thing.specValue(SpecKeys.SERIAL)
                  ),
                )
              )
            ),
          )
        )
        val componentCards = thing.allComponentsInSlot(SlotKeys.ENGINE)
          .flatMapIndexed { index, engine ->
            buildList {
              add(
                PdfSummaryCard(
                  title = engineCardTitle(thing, index),
                  rows = listOf(
                    PdfSummaryRow("Make", engine.make),
                    PdfSummaryRow("Model", engine.model),
                    PdfSummaryRow("Serial", engine.serial),
                  )
                )
              )
              engine.childInSlot(SlotKeys.PROPELLER)
                ?.let { propeller ->
                  add(
                    PdfSummaryCard(
                      title = propellerCardTitle(thing, index),
                      rows = buildList {
                        add(
                          PdfSummaryRow(
                            "Hub Make",
                            propeller.make.orEmpty()
                          )
                        )
                        add(
                          PdfSummaryRow(
                            "Hub Model",
                            propeller.model.orEmpty()
                          )
                        )
                        add(
                          PdfSummaryRow(
                            "Hub Serial",
                            propeller.serial.orEmpty()
                          )
                        )
                        propeller.childrenInSlot(SlotKeys.BLADE)
                          .forEachIndexed { bladeIndex, blade ->
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
    bundle: ThingBundle,
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

  private fun MaintenanceLog.resolveTechnician(bundle: ThingBundle): Technician? =
    technician?.takeIf { it.name.isNotBlank() }
      ?: bundle.techniciansById[technician_id]

  private fun engineCsvName(thing: Thing, index: Int): String =
    if (thing.allComponentsInSlot(SlotKeys.ENGINE).size <= 1) "02_Engine.csv"
    else "02_Engine_${index + 1}.csv"

  private fun propellerCsvName(thing: Thing, index: Int): String =
    if (thing.allComponentsInSlot(SlotKeys.PROPELLER).size <= 1) "03_Propeller.csv"
    else "03_Propeller_${index + 1}.csv"

  private fun engineSheetName(thing: Thing, index: Int): String =
    if (thing.allComponentsInSlot(SlotKeys.ENGINE).size <= 1) "02 Engine"
    else "02 Engine ${index + 1}"

  private fun propellerSheetName(thing: Thing, index: Int): String =
    if (thing.allComponentsInSlot(SlotKeys.PROPELLER).size <= 1) "03 Prop"
    else "03 Prop ${index + 1}"

  private fun engineCardTitle(thing: Thing, index: Int): String =
    if (thing.allComponentsInSlot(SlotKeys.ENGINE).size <= 1) "Engine"
    else "Engine ${index + 1}"

  private fun propellerCardTitle(thing: Thing, index: Int): String =
    if (thing.allComponentsInSlot(SlotKeys.PROPELLER).size <= 1) "Propeller"
    else "Propeller ${index + 1}"

  private fun MaintenanceLog.inspectionTitles(bundle: ThingBundle): String =
    inspection_ids.joinToString("\n") { id ->
      bundle.tasksById[id]?.title ?: "[deleted]"
    }

  private fun MaintenanceLog.referenceNumbers(bundle: ThingBundle): String =
    inspection_ids.mapNotNull { id ->
      bundle.tasksById[id]?.reference_number?.takeIf(
        String::isNotBlank
      )
    }
      .joinToString("\n")

  private fun MaintenanceLog.squawkTitles(bundle: ThingBundle): String =
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

  private fun List<InspectionRule>.scheduleLabel(bundle: ThingBundle): String =
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

  private fun Thing.folderName(): String =
    "${safeTailNumber()}_${specValue(SpecKeys.MAKE)}_${specValue(SpecKeys.MODEL)}"
      .sanitizePathSegment()

  private fun Thing.safeTailNumber(): String =
    specValue(SpecKeys.TAIL_NUMBER).ifBlank { id.ifBlank { "Aircraft" } }
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
    bundle: ThingBundle,
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
      month.number.toString()
        .padStart(2, '0')
    }${
      day.toString()
        .padStart(2, '0')
    }"

  private fun LocalDateTime.exportTimestamp(timeZone: TimeZone): String =
    "$date ${
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
    val bundle: ThingBundle,
    val attachments: AttachmentExportManifest,
    val sheetPrefix: String,
    val tables: List<LogbookExportTable>,
  )
}
