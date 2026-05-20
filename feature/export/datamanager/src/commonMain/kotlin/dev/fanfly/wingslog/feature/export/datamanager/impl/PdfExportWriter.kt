package dev.fanfly.wingslog.feature.export.datamanager.impl

import kotlin.math.max

/**
 * Pure-Kotlin PDF renderer for the per-aircraft export document.
 *
 * The output intentionally uses a small subset of PDF 1.4 primitives so it remains portable
 * across Android/JVM and iOS/native without introducing a platform PDF dependency.
 */
class PdfExportWriter : AircraftPdfWriter {
  override fun write(document: AircraftPdfDocument): ByteArray {
    val layout = PdfLayoutEngine(document)
    val pages = layout.render()
    return SimplePdfFileWriter.write(pages)
  }
}

private class PdfLayoutEngine(
  private val document: AircraftPdfDocument,
) {
  private val pages = mutableListOf<PdfPageCanvas>()
  private var page = PdfPageCanvas()
  private var cursorY = PAGE_MARGIN

  fun render(): List<String> {
    startNewPage()
    drawDocumentHeader()
    document.summarySections.forEach { drawSummarySection(it) }
    document.tableSections.forEach { drawTableSection(it) }
    finishPage()
    return pages.map { it.commands.toString() }
  }

  private fun drawDocumentHeader() {
    val titleLines = wrapText(document.title, CONTENT_WIDTH, TITLE_FONT_SIZE, bold = true)
    val subtitleLines = wrapText(document.subtitle, CONTENT_WIDTH, BODY_FONT_SIZE, bold = false)
    val requiredHeight = titleLines.height(TITLE_FONT_SIZE) +
      subtitleLines.height(BODY_FONT_SIZE) +
      SECTION_GAP
    ensureSpace(requiredHeight)
    page.drawTextLines(
      x = PAGE_MARGIN,
      top = cursorY,
      lines = titleLines,
      font = PdfFont.BOLD,
      fontSize = TITLE_FONT_SIZE,
    )
    cursorY += titleLines.height(TITLE_FONT_SIZE) + 6f
    page.drawTextLines(
      x = PAGE_MARGIN,
      top = cursorY,
      lines = subtitleLines,
      font = PdfFont.REGULAR,
      fontSize = BODY_FONT_SIZE,
      color = PdfColor.TEXT_MUTED,
    )
    cursorY += subtitleLines.height(BODY_FONT_SIZE) + SECTION_GAP
  }

  private fun drawSummarySection(section: PdfSummarySection) {
    drawSectionTitle(section.title)
    section.cards.forEach { card ->
      val cardHeight = estimateCardHeight(card)
      ensureSpace(cardHeight + CARD_GAP)
      drawSummaryCard(card)
      cursorY += cardHeight + CARD_GAP
    }
    cursorY += SECTION_GAP - CARD_GAP
  }

  private fun drawSummaryCard(card: PdfSummaryCard) {
    val cardHeight = estimateCardHeight(card)
    page.fillRect(
      x = PAGE_MARGIN,
      top = cursorY,
      width = CONTENT_WIDTH,
      height = cardHeight,
      color = PdfColor.CARD_FILL,
    )
    page.strokeRect(
      x = PAGE_MARGIN,
      top = cursorY,
      width = CONTENT_WIDTH,
      height = cardHeight,
      color = PdfColor.BORDER,
      lineWidth = 0.8f,
    )
    var y = cursorY + CARD_PADDING
    card.title?.takeIf { it.isNotBlank() }?.let { title ->
      val lines = wrapText(title, CONTENT_WIDTH - CARD_PADDING * 2, BODY_FONT_SIZE, bold = true)
      page.drawTextLines(
        x = PAGE_MARGIN + CARD_PADDING,
        top = y,
        lines = lines,
        font = PdfFont.BOLD,
        fontSize = BODY_FONT_SIZE,
      )
      y += lines.height(BODY_FONT_SIZE) + 8f
      page.drawLine(
        startX = PAGE_MARGIN + CARD_PADDING,
        startY = y - 3f,
        endX = PAGE_MARGIN + CONTENT_WIDTH - CARD_PADDING,
        endY = y - 3f,
        color = PdfColor.BORDER,
        lineWidth = 0.6f,
      )
    }
    card.rows.forEachIndexed { index, row ->
      y += drawSummaryRow(y, row)
      if (index != card.rows.lastIndex) y += 6f
    }
  }

  private fun drawSummaryRow(top: Float, row: PdfSummaryRow): Float {
    val labelWidth = 152f
    val valueWidth = CONTENT_WIDTH - CARD_PADDING * 3 - labelWidth
    val labelLines = wrapText(row.label, labelWidth, LABEL_FONT_SIZE, bold = true)
    val valueLines = wrapText(row.value.ifBlank { " " }, valueWidth, BODY_FONT_SIZE, bold = false)
    val rowHeight = max(
      labelLines.height(LABEL_FONT_SIZE),
      valueLines.height(BODY_FONT_SIZE),
    )
    page.drawTextLines(
      x = PAGE_MARGIN + CARD_PADDING,
      top = top,
      lines = labelLines,
      font = PdfFont.BOLD,
      fontSize = LABEL_FONT_SIZE,
      color = PdfColor.TEXT_MUTED,
    )
    page.drawTextLines(
      x = PAGE_MARGIN + CARD_PADDING * 2 + labelWidth,
      top = top,
      lines = valueLines,
      font = PdfFont.REGULAR,
      fontSize = BODY_FONT_SIZE,
    )
    return rowHeight
  }

  private fun drawTableSection(section: PdfTableSection) {
    if (section.rows.isEmpty()) return
    val bodyRows = section.rows.drop(1)
    val profile = tableProfile(section.title, section.rows.first())
    val header = profile.headerLabels
    val columnWidths = profile.columnWidths
    val headerHeight = estimateTableRowHeight(header, columnWidths, profile.headerFontSize)
    ensureSpace(sectionTitleHeight(section.title) + headerHeight + ROW_GAP)
    drawSectionTitle(section.title)
    drawTableHeader(header, columnWidths, profile.headerFontSize)
    bodyRows.forEach { row ->
      val rowHeight = estimateTableRowHeight(row, columnWidths, profile.bodyFontSize)
      if (!hasSpace(rowHeight)) {
        startNewPage()
        drawSectionTitle("${section.title} (cont.)")
        drawTableHeader(header, columnWidths, profile.headerFontSize)
      }
      drawTableRow(
        row = row,
        widths = columnWidths,
        height = rowHeight,
        header = false,
        fontSize = profile.bodyFontSize,
      )
    }
    cursorY += SECTION_GAP
  }

  private fun drawTableHeader(header: List<String>, widths: List<Float>, fontSize: Float) {
    val height = estimateTableRowHeight(header, widths, fontSize)
    page.fillRect(PAGE_MARGIN, cursorY, CONTENT_WIDTH, height, PdfColor.HEADER_FILL)
    drawTableRow(header, widths, height, header = true, fontSize = fontSize)
  }

  private fun drawTableRow(
    row: List<String>,
    widths: List<Float>,
    height: Float,
    header: Boolean,
    fontSize: Float,
  ) {
    ensureSpace(height)
    var x = PAGE_MARGIN
    widths.forEachIndexed { index, width ->
      page.strokeRect(x, cursorY, width, height, PdfColor.BORDER, lineWidth = 0.55f)
      val lines = wrapText(
        text = row.getOrElse(index) { "" }.ifBlank { " " },
        maxWidth = width - CELL_PADDING * 2,
        fontSize = fontSize,
        bold = header,
      )
      page.drawTextLines(
        x = x + CELL_PADDING,
        top = cursorY + CELL_PADDING,
        lines = lines,
        font = if (header) PdfFont.BOLD else PdfFont.REGULAR,
        fontSize = fontSize,
        color = if (header) PdfColor.TEXT else PdfColor.TEXT,
      )
      x += width
    }
    cursorY += height + ROW_GAP
  }

  private fun tableProfile(sectionTitle: String, header: List<String>): PdfTableProfile {
    val title = sectionTitle.lowercase()
    val aliases = when {
      title.contains("tasks") || title.contains("compliance") -> listOf(
        "Title", "Comp.", "Type", "Ref #", "Auth.", "Schedule",
        "Last Date", "Last Hrs", "Next Date", "Next Hrs", "One-Time", "Notes", "Details"
      )
      title.contains("squawk") -> listOf(
        "Created", "Title", "Description", "Priority", "Comp.", "Serial", "Status", "Action Date"
      )
      title.contains("airframe") || title.contains("engine") || title.contains("prop") -> listOf(
        "Date", shortenTimeHeader(header.getOrElse(1) { "" }), shortenTimeHeader(header.getOrElse(2) { "" }), "Work Description",
        "Inspections", "Ref #", header.getOrElse(6) { "" }, "Technician", "Cert", "Cert #", "Attachments"
      ).take(header.size)
      else -> header.map { abbreviateHeader(it) }
    }
    val weights = when {
      title.contains("tasks") || title.contains("compliance") -> listOf(2.0f, 1.2f, 1.2f, 1.3f, 1.0f, 1.8f, 1.1f, 1.0f, 1.1f, 1.0f, 0.8f, 1.8f, 2.4f)
      title.contains("squawk") -> listOf(1.0f, 1.5f, 4.0f, 0.9f, 1.0f, 1.1f, 1.6f, 1.1f)
      title.contains("airframe") -> listOf(0.9f, 0.82f, 0.82f, 3.9f, 1.55f, 1.05f, 1.35f, 1.25f, 0.75f, 0.9f, 2.45f)
      title.contains("engine") -> listOf(0.9f, 0.82f, 0.82f, 3.9f, 1.55f, 1.05f, 1.35f, 1.25f, 0.75f, 0.9f, 2.45f)
      title.contains("prop") -> listOf(0.9f, 0.82f, 0.82f, 4.05f, 1.55f, 1.05f, 1.25f, 0.75f, 0.9f, 2.55f)
      else -> header.map(::defaultWeight)
    }.take(header.size)
    val totalWeight = weights.sum().takeIf { it > 0f } ?: 1f
    return PdfTableProfile(
      headerLabels = aliases.take(header.size),
      columnWidths = weights.map { CONTENT_WIDTH * (it / totalWeight) },
      headerFontSize = if (title.contains("tasks") || title.contains("compliance")) 8.1f else HEADER_FONT_SIZE,
      bodyFontSize = if (title.contains("tasks") || title.contains("compliance")) 7.7f else TABLE_FONT_SIZE,
    )
  }

  private fun abbreviateHeader(header: String): String =
    when (header) {
      "Reference Numbers" -> "Ref #"
      "Squawks Addressed" -> "Squawks"
      "Component Serial" -> "Serial"
      "Work Description" -> "Work Description"
      "Compliance Details" -> "Details"
      "Task Details" -> "Details"
      "Cert Type" -> "Cert"
      "Addressed By - Date" -> "Addr. Date"
      "Action Date" -> "Action Date"
      "Last Complied - Date" -> "Last Date"
      "Last Complied - Hours" -> "Last Hrs"
      "Next Due - Date" -> "Next Date"
      "Next Due - Hours" -> "Next Hrs"
      else -> header
    }

  private fun shortenTimeHeader(header: String): String =
    when (header) {
      "Airframe Time" -> "AF Time"
      "Engine 1 Time" -> "Eng 1 Time"
      "Engine Time" -> "Eng Time"
      "Prop Time" -> "Prop Time"
      else -> abbreviateHeader(header)
    }

  private fun defaultWeight(heading: String): Float =
    when {
      heading.contains("Description", ignoreCase = true) -> 4.2f
      heading.contains("Notes", ignoreCase = true) -> 3.2f
      heading.contains("Details", ignoreCase = true) -> 3.2f
      heading.contains("Attachments", ignoreCase = true) -> 3.0f
      heading.contains("Reference", ignoreCase = true) -> 2.0f
      heading.contains("Technician", ignoreCase = true) -> 2.0f
      heading.contains("Title", ignoreCase = true) -> 2.0f
      heading.contains("Date", ignoreCase = true) -> 1.1f
      heading.contains("Time", ignoreCase = true) -> 1.1f
      heading.contains("Hours", ignoreCase = true) -> 1.1f
      heading.contains("Status", ignoreCase = true) -> 1.1f
      heading.contains("Type", ignoreCase = true) -> 1.1f
      heading.contains("Cert", ignoreCase = true) -> 1.2f
      else -> 1.4f
    }

  private fun estimateCardHeight(card: PdfSummaryCard): Float {
    val labelWidth = 134f
    val valueWidth = CONTENT_WIDTH - CARD_PADDING * 3 - labelWidth
    var height = CARD_PADDING * 2
    card.title?.takeIf { it.isNotBlank() }?.let { title ->
      height += wrapText(title, CONTENT_WIDTH - CARD_PADDING * 2, BODY_FONT_SIZE, bold = true)
        .height(BODY_FONT_SIZE) + 8f
    }
    card.rows.forEachIndexed { index, row ->
      val labelHeight = wrapText(row.label, labelWidth, LABEL_FONT_SIZE, bold = true)
        .height(LABEL_FONT_SIZE)
      val valueHeight = wrapText(row.value.ifBlank { " " }, valueWidth, BODY_FONT_SIZE, bold = false)
        .height(BODY_FONT_SIZE)
      height += max(labelHeight, valueHeight)
      if (index != card.rows.lastIndex) height += 6f
    }
    return height
  }

  private fun estimateTableRowHeight(
    row: List<String>,
    widths: List<Float>,
    fontSize: Float,
  ): Float {
    val tallest = widths.mapIndexed { index, width ->
      wrapText(row.getOrElse(index) { "" }.ifBlank { " " }, width - CELL_PADDING * 2, fontSize, bold = fontSize >= HEADER_FONT_SIZE)
        .height(fontSize)
    }.maxOrNull() ?: fontSize
    return tallest + CELL_PADDING * 2
  }

  private fun drawSectionTitle(title: String) {
    val requiredHeight = sectionTitleHeight(title)
    ensureSpace(requiredHeight)
    val lines = wrapText(title, CONTENT_WIDTH, SECTION_FONT_SIZE, bold = true)
    page.drawTextLines(
      x = PAGE_MARGIN,
      top = cursorY,
      lines = lines,
      font = PdfFont.BOLD,
      fontSize = SECTION_FONT_SIZE,
    )
    cursorY += requiredHeight
  }

  private fun sectionTitleHeight(title: String): Float =
    wrapText(title, CONTENT_WIDTH, SECTION_FONT_SIZE, bold = true).height(SECTION_FONT_SIZE) + 8f

  private fun startNewPage() {
    if (page.commands.isNotEmpty()) finishPage()
    page = PdfPageCanvas()
    cursorY = PAGE_MARGIN
  }

  private fun finishPage() {
    if (page.commands.isNotEmpty()) pages += page
  }

  private fun ensureSpace(height: Float) {
    if (!hasSpace(height)) startNewPage()
  }

  private fun hasSpace(height: Float): Boolean =
    cursorY + height <= PAGE_HEIGHT - PAGE_MARGIN

  private fun wrapText(
    text: String,
    maxWidth: Float,
    fontSize: Float,
    bold: Boolean,
  ): List<String> {
    if (text.isBlank()) return listOf(" ")
    val lines = mutableListOf<String>()
    text.split('\n').forEach { paragraph ->
      if (paragraph.isBlank()) {
        lines += " "
        return@forEach
      }
      val words = paragraph.split(Regex("\\s+")).filter { it.isNotBlank() }
      var current = ""
      words.forEach { word ->
        val candidate = if (current.isBlank()) word else "$current $word"
        if (estimateTextWidth(candidate, fontSize, bold) <= maxWidth) {
          current = candidate
        } else if (current.isBlank()) {
          breakWord(word, maxWidth, fontSize, bold).forEach(lines::add)
        } else {
          lines += current
          current = word
          if (estimateTextWidth(current, fontSize, bold) > maxWidth) {
            breakWord(current, maxWidth, fontSize, bold).forEach(lines::add)
            current = ""
          }
        }
      }
      if (current.isNotBlank()) lines += current
    }
    return lines.ifEmpty { listOf(" ") }
  }

  private fun breakWord(
    word: String,
    maxWidth: Float,
    fontSize: Float,
    bold: Boolean,
  ): List<String> {
    val parts = mutableListOf<String>()
    var current = ""
    word.forEach { char ->
      val candidate = current + char
      if (estimateTextWidth(candidate, fontSize, bold) <= maxWidth || current.isBlank()) {
        current = candidate
      } else {
        parts += current
        current = char.toString()
      }
    }
    if (current.isNotBlank()) parts += current
    return parts
  }

  private fun estimateTextWidth(text: String, fontSize: Float, bold: Boolean): Float {
    val boldScale = if (bold) 1.03f else 1f
    return text.sumOf { charWidth(it).toDouble() }.toFloat() * fontSize * boldScale
  }

  private fun charWidth(char: Char): Float =
    when {
      char == ' ' -> 0.27f
      char in '0'..'9' -> 0.56f
      char in 'A'..'Z' -> 0.62f
      char in 'a'..'z' -> 0.53f
      char == '-' || char == '_' || char == '/' || char == ':' || char == '.' || char == ',' -> 0.32f
      else -> 0.58f
    }

  private fun List<String>.height(fontSize: Float): Float =
    size * (fontSize * LINE_HEIGHT_MULTIPLIER)
}

private object SimplePdfFileWriter {
  fun write(pageContents: List<String>): ByteArray {
    val objects = mutableListOf<ByteArray>()
    objects += "<< /Type /Catalog /Pages 2 0 R >>".encodeToByteArray()
    val pageObjectIds = pageContents.indices.map { index -> 5 + (index * 2) }
    val kids = pageObjectIds.joinToString(separator = " ", prefix = "[ ", postfix = " ]") { "$it 0 R" }
    objects += "<< /Type /Pages /Kids $kids /Count ${pageContents.size} >>".encodeToByteArray()
    objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>".encodeToByteArray()
    objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>".encodeToByteArray()

    pageContents.forEachIndexed { index, content ->
      val contentObjectId = 6 + (index * 2)
      objects += buildString {
        append("<< /Type /Page /Parent 2 0 R ")
        append("/MediaBox [0 0 $PAGE_WIDTH $PAGE_HEIGHT] ")
        append("/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> ")
        append("/Contents $contentObjectId 0 R >>")
      }.encodeToByteArray()
      val streamBytes = content.encodeToByteArray()
      objects += buildStreamObject(streamBytes)
    }

    return serialize(objects)
  }

  private fun buildStreamObject(streamBytes: ByteArray): ByteArray =
    buildString {
      append("<< /Length ${streamBytes.size} >>\nstream\n")
      append(streamBytes.decodeToString())
      append("\nendstream")
    }.encodeToByteArray()

  private fun serialize(objects: List<ByteArray>): ByteArray {
    val output = mutableListOf<Byte>()
    fun append(text: String) {
      output += text.encodeToByteArray().toList()
    }
    fun append(bytes: ByteArray) {
      output += bytes.toList()
    }

    append("%PDF-1.4\n")
    val offsets = mutableListOf<Int>()
    objects.forEachIndexed { index, objectBytes ->
      offsets += output.size
      append("${index + 1} 0 obj\n")
      append(objectBytes)
      append("\nendobj\n")
    }
    val xrefOffset = output.size
    append("xref\n")
    append("0 ${objects.size + 1}\n")
    append("0000000000 65535 f \n")
    offsets.forEach { offset ->
      append(offset.toString().padStart(10, '0'))
      append(" 00000 n \n")
    }
    append("trailer\n")
    append("<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
    append("startxref\n")
    append("$xrefOffset\n")
    append("%%EOF")
    return output.toByteArray()
  }
}

private class PdfPageCanvas {
  val commands = StringBuilder()

  fun drawTextLines(
    x: Float,
    top: Float,
    lines: List<String>,
    font: PdfFont,
    fontSize: Float,
    color: PdfColor = PdfColor.TEXT,
  ) {
    lines.forEachIndexed { index, line ->
      val baselineY = PAGE_HEIGHT - (top + fontSize + (index * fontSize * LINE_HEIGHT_MULTIPLIER))
      commands.append("BT ")
      commands.append(font.colorCommand(color))
      commands.append("/${font.resourceName} $fontSize Tf 1 0 0 1 ${format(x)} ${format(baselineY)} Tm ")
      commands.append("(${escape(line)}) Tj ET\n")
    }
  }

  fun fillRect(x: Float, top: Float, width: Float, height: Float, color: PdfColor) {
    val y = PAGE_HEIGHT - top - height
    commands.append("q ${color.fillCommand()} ${format(x)} ${format(y)} ${format(width)} ${format(height)} re f Q\n")
  }

  fun strokeRect(
    x: Float,
    top: Float,
    width: Float,
    height: Float,
    color: PdfColor,
    lineWidth: Float,
  ) {
    val y = PAGE_HEIGHT - top - height
    commands.append("q ${format(lineWidth)} w ${color.strokeCommand()} ${format(x)} ${format(y)} ${format(width)} ${format(height)} re S Q\n")
  }

  fun drawLine(
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    color: PdfColor,
    lineWidth: Float,
  ) {
    val pdfStartY = PAGE_HEIGHT - startY
    val pdfEndY = PAGE_HEIGHT - endY
    commands.append(
      "q ${format(lineWidth)} w ${color.strokeCommand()} ${format(startX)} ${format(pdfStartY)} m " +
        "${format(endX)} ${format(pdfEndY)} l S Q\n"
    )
  }

  private fun escape(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("(", "\\(")
      .replace(")", "\\)")

  private fun format(value: Float): String =
    value.toString().removeSuffix(".0")
}

private enum class PdfFont(val resourceName: String) {
  REGULAR("F1"),
  BOLD("F2"),
  ;

  fun colorCommand(color: PdfColor): String = color.fillCommand()
}

private data class PdfColor(
  val red: Float,
  val green: Float,
  val blue: Float,
) {
  fun fillCommand(): String = "${format(red)} ${format(green)} ${format(blue)} rg"

  fun strokeCommand(): String = "${format(red)} ${format(green)} ${format(blue)} RG"

  private fun format(value: Float): String = value.toString().removeSuffix(".0")

  companion object {
    val TEXT = PdfColor(0.12f, 0.16f, 0.22f)
    val TEXT_MUTED = PdfColor(0.36f, 0.42f, 0.5f)
    val BORDER = PdfColor(0.73f, 0.78f, 0.84f)
    val CARD_FILL = PdfColor(0.98f, 0.99f, 1.0f)
    val HEADER_FILL = PdfColor(0.91f, 0.95f, 1.0f)
  }
}

private const val PAGE_WIDTH = 792f
private const val PAGE_HEIGHT = 612f
private const val PAGE_MARGIN = 28f
private const val CONTENT_WIDTH = PAGE_WIDTH - PAGE_MARGIN * 2
private const val TITLE_FONT_SIZE = 18f
private const val SECTION_FONT_SIZE = 13f
private const val BODY_FONT_SIZE = 10.5f
private const val LABEL_FONT_SIZE = 9.5f
private const val HEADER_FONT_SIZE = 8.8f
private const val TABLE_FONT_SIZE = 8.1f
private const val LINE_HEIGHT_MULTIPLIER = 1.22f
private const val CELL_PADDING = 3.5f
private const val CARD_PADDING = 12f
private const val SECTION_GAP = 12f
private const val CARD_GAP = 8f
private const val ROW_GAP = 1f

private data class PdfTableProfile(
  val headerLabels: List<String>,
  val columnWidths: List<Float>,
  val headerFontSize: Float,
  val bodyFontSize: Float,
)
