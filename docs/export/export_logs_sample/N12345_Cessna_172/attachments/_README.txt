Each file referenced in a CSV "Attachments" column lives in this folder.

Filename scheme: <short_id>_<sanitized_name>.<ext>
  short_id        = first 4 hex characters of attachment.id
  sanitized_name  = attachment.name with [^A-Za-z0-9._-] replaced by _
  ext             = inferred from mime_type if name has none

For the sample export above, the following files would normally be present
(omitted here because the reference is text-only):

  8f3a_annual_signoff_2024.pdf       — PDF, from log-001-annual-2024
  9b21_logbook_page.jpg              — IMAGE, from log-001-annual-2024
  c40e_pitot_static_cert.pdf         — PDF, from log-003-pitot-2025
  3d17_annual_signoff_2025.pdf       — PDF, from log-005-annual-2025
  a2c9_compression_check.pdf         — PDF, from log-006-100hr-2024
  b73f_ad_compliance.pdf             — PDF, from log-007-magneto-2025
  e801_magneto_before.jpg            — IMAGE, from log-007-magneto-2025
  f44a_prop_balance_report.pdf       — PDF, from log-009-propbalance-2025

LINK-type attachments are NOT written to this folder. They appear in
the CSV as "<name> -> <url>" and have no on-disk file.
