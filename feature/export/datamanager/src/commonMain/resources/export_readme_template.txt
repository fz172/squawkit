SquawkIt Logbook Export

Generated: {{generated_at}}
Scope:     {{scope}}
Period:    {{period}}
App:       {{app_version}}

Archive layout
- This ZIP contains one folder per aircraft.
- Inside each aircraft folder:
  - `csv/` contains the per-table CSV files.
  - `*.pdf` is the aircraft PDF reference export.
  - `attachments/` contains bundled binary attachments when available.
  - `SquawkIt_Logs_*.xlsx` contains that aircraft's workbook export.

How to import into Google Sheets
1. Extract this ZIP and open the aircraft folder you want to import.
2. Open https://sheets.google.com and create a new blank spreadsheet.
3. File -> Import -> Upload -> choose that folder's SquawkIt_Logs_*.xlsx workbook.
4. Select "Replace spreadsheet" and click "Import data".
   The workbook already contains one tab for each exported table for that aircraft.

PDF reference
- A per-aircraft PDF summary is included directly inside each aircraft folder.
- PDFs mirror the same export data as the CSV/XLSX tables, but format aircraft metadata,
  component metadata, and technician details as summary cards for easier review.

CSV fallback
1. Open https://sheets.google.com and create a new blank spreadsheet.
2. Open the aircraft folder you want to import, then choose `csv/00_Aircraft_Info.csv`.
3. Select "Insert new sheet(s)" and click "Import data".
   This adds the CSV as a new tab instead of replacing the current sheet.
4. Repeat for each CSV in that aircraft folder's `csv/` directory in the order they are numbered
   (00, 01, 02, ...).
   The numeric prefixes keep the tabs in logbook order.
5. After the last CSV, delete the default "Sheet1" tab.

Tab order in a paper logbook
  Aircraft Info -> Airframe -> Engines -> Propellers
  -> Tasks -> Squawks -> Technicians

Notes
- Dates are YYYY-MM-DD in the export device's local time zone.
- Times are decimal hours (1247.3, not 1247:18).
- Multi-value cells hold one entry per line within a single quoted cell.
- Each aircraft folder is self-contained. CSV, PDF, attachments, and XLSX stay together.
- Attachment binaries are bundled under each aircraft folder's `attachments/` directory when available.
  They are not embedded in the XLSX workbook.
  LINK-type attachments show the original URL.
- This export is a snapshot. It does not update when logs change in SquawkIt.
{{attachment_notes}}
