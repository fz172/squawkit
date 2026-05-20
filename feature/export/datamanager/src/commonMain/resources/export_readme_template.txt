Hopply Logbook Export

Generated: {{generated_at}}
Scope:     {{scope}}
Period:    {{period}}
App:       {{app_version}}

How to import into Google Sheets
1. Extract this ZIP and locate the included Hopply_Logs_*.xlsx workbook.
2. Open https://sheets.google.com and create a new blank spreadsheet.
3. File -> Import -> Upload -> choose the Hopply_Logs_*.xlsx workbook.
4. Select "Replace spreadsheet" and click "Import data".
   The workbook already contains one tab for each exported table.

CSV fallback
1. Open https://sheets.google.com and create a new blank spreadsheet.
2. File -> Import -> Upload -> choose 00_Aircraft_Info.csv.
3. Select "Insert new sheet(s)" and click "Import data".
   This adds the CSV as a new tab instead of replacing the current sheet.
4. Repeat for each CSV in the order they are numbered (00, 01, 02, ...).
   The numeric prefixes keep the tabs in logbook order.
5. After the last CSV, delete the default "Sheet1" tab.

Tab order in a paper logbook
  Aircraft Info -> Airframe -> Engines -> Propellers
  -> Compliance -> Squawks -> Technicians

Notes
- Dates are YYYY-MM-DD in the export device's local time zone.
- Times are decimal hours (1247.3, not 1247:18).
- Multi-value cells hold one entry per line within a single quoted cell.
- Attachment binaries are bundled under the attachments/ folder when available.
  They are not embedded in the XLSX workbook.
  LINK-type attachments show the original URL.
- This export is a snapshot. It does not update when logs change in Hopply.
{{attachment_notes}}
