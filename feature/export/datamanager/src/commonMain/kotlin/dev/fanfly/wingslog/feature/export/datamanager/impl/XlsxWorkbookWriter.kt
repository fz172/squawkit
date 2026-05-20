package dev.fanfly.wingslog.feature.export.datamanager.impl

/**
 * Writes a minimal Office Open XML workbook with one worksheet per export table.
 */
class XlsxWorkbookWriter(
  private val zipFileWriter: ZipFileWriter = ZipFileWriter(),
) {

  /**
   * Serializes [sheets] into an `.xlsx` workbook.
   */
  fun write(sheets: List<XlsxSheet>): ByteArray {
    require(sheets.isNotEmpty()) { "XLSX workbook must contain at least one sheet." }
    val safeSheets = sheets.withUniqueNames()
    val entries = buildList {
      add(xmlEntry("[Content_Types].xml", contentTypes(safeSheets.size)))
      add(xmlEntry("_rels/.rels", rootRelationships()))
      add(xmlEntry("xl/workbook.xml", workbook(safeSheets)))
      add(xmlEntry("xl/_rels/workbook.xml.rels", workbookRelationships(safeSheets.size)))
      add(xmlEntry("xl/styles.xml", styles()))
      safeSheets.forEachIndexed { index, sheet ->
        add(xmlEntry("xl/worksheets/sheet${index + 1}.xml", worksheet(sheet.rows)))
      }
    }
    return zipFileWriter.write(entries)
  }

  private fun List<XlsxSheet>.withUniqueNames(): List<XlsxSheet> {
    val used = mutableSetOf<String>()
    return map { sheet ->
      val base = sheet.name.safeSheetName()
      var candidate = base
      var suffix = 2
      while (!used.add(candidate)) {
        val tail = " ($suffix)"
        candidate = base.take(MAX_SHEET_NAME_LENGTH - tail.length) + tail
        suffix += 1
      }
      sheet.copy(name = candidate)
    }
  }

  private fun xmlEntry(path: String, xml: String) =
    ZipEntryPayload(path = path, bytes = xml.encodeToByteArray())

  private fun contentTypes(sheetCount: Int) = buildString {
    appendXmlHeader()
    append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
    append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
    append("""<Default Extension="xml" ContentType="application/xml"/>""")
    append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
    append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
    repeat(sheetCount) { index ->
      append("""<Override PartName="/xl/worksheets/sheet${index + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
    }
    append("</Types>")
  }

  private fun rootRelationships() = buildString {
    appendXmlHeader()
    append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
    append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""")
    append("</Relationships>")
  }

  private fun workbook(sheets: List<XlsxSheet>) = buildString {
    appendXmlHeader()
    append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """)
    append("""xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
    append("<sheets>")
    sheets.forEachIndexed { index, sheet ->
      append("""<sheet name="${sheet.name.escapeXml()}" sheetId="${index + 1}" r:id="rId${index + 1}"/>""")
    }
    append("</sheets></workbook>")
  }

  private fun workbookRelationships(sheetCount: Int) = buildString {
    appendXmlHeader()
    append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
    repeat(sheetCount) { index ->
      append("""<Relationship Id="rId${index + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${index + 1}.xml"/>""")
    }
    append("""<Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
    append("</Relationships>")
  }

  private fun styles() = buildString {
    appendXmlHeader()
    append("""<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
    append("""<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>""")
    append("""<fills count="1"><fill><patternFill patternType="none"/></fill></fills>""")
    append("""<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""")
    append("""<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""")
    append("""<cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>""")
    append("</styleSheet>")
  }

  private fun worksheet(rows: List<List<String>>) = buildString {
    appendXmlHeader()
    append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
    append("<sheetData>")
    rows.forEachIndexed { rowIndex, row ->
      append("""<row r="${rowIndex + 1}">""")
      row.forEachIndexed { columnIndex, value ->
        append("""<c r="${columnName(columnIndex)}${rowIndex + 1}" t="inlineStr"><is><t xml:space="preserve">""")
        append(value.escapeXml())
        append("</t></is></c>")
      }
      append("</row>")
    }
    append("</sheetData></worksheet>")
  }

  private fun StringBuilder.appendXmlHeader() {
    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
  }

  private fun String.safeSheetName(): String =
    replace(Regex("""[\[\]:*?/\\]"""), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
      .ifBlank { "Sheet" }
      .take(MAX_SHEET_NAME_LENGTH)

  private fun String.escapeXml(): String =
    buildString {
      this@escapeXml.forEach { char ->
        when (char) {
          '&' -> append("&amp;")
          '<' -> append("&lt;")
          '>' -> append("&gt;")
          '"' -> append("&quot;")
          '\'' -> append("&apos;")
          else -> append(char)
        }
      }
    }

  private fun columnName(index: Int): String {
    var value = index + 1
    val chars = mutableListOf<Char>()
    while (value > 0) {
      value -= 1
      chars += ('A'.code + value % 26).toChar()
      value /= 26
    }
    return chars.asReversed().joinToString("")
  }

  private companion object {
    const val MAX_SHEET_NAME_LENGTH = 31
  }
}
