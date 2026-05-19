Hopply Logbook Export

Generated: 2026-05-18 14:22 PDT
Scope:     Cessna 172N N12345
Period:    All time
App:       Hopply 1.4.0 (147)

How to import into Google Sheets
1. Open https://sheets.google.com and create a new blank spreadsheet.
2. File -> Import -> Upload -> choose 00_Aircraft_Info.csv.
3. Select "Insert new sheet(s)" and click "Import data".
4. Repeat for each CSV in the order they are numbered (00, 01, 02, ...).
   The numeric prefixes keep the tabs in logbook order.
5. After the last CSV, delete the default "Sheet1" tab.

Tab order in a paper logbook
  Aircraft Info  ->  Airframe  ->  Engines  ->  Propellers
  ->  Compliance  ->  Squawks  ->  Technicians

Notes
- Dates are YYYY-MM-DD in the export device's local time zone.
- Times are decimal hours (1247.3, not 1247:18).
- Multi-value cells (Inspections, Reference Numbers, Squawks Addressed,
  Attachments) hold one entry per line within a single quoted cell.
  Google Sheets renders these as multi-line cells. Grow the row to see
  every entry.
- Attachment binaries (photos, PDFs, files) are bundled under the
  attachments/ folder. The CSV "Attachments" column shows
  "<name> -> attachments/<file>" so you can locate each file after
  extracting the zip. LINK-type attachments show the original URL.
- This export is a snapshot. It does not update when logs change in Hopply.
