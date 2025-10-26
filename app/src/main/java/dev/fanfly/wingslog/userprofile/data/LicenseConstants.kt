package dev.fanfly.wingslog.userprofile.data

import dev.fanfly.wingslog.R


fun LicenseType.displayResId(): Int {
  return when (this) {
    LicenseType.NONE -> R.string.license_type_none
    LicenseType.REPAIRMAN -> R.string.license_type_repairman
    LicenseType.AMT -> R.string.license_type_amt
    LicenseType.UNRECOGNIZED ->
      R.string.license_type_none
  }
}