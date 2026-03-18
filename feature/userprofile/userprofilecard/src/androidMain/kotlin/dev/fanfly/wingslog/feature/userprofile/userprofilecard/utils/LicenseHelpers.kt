package dev.fanfly.wingslog.feature.userprofile.userprofilecard.utils

import dev.fanfly.wingslog.core.model.userprofile.LicenseType
import dev.fanfly.wingslog.feature.userprofile.userprofilecard.R

fun LicenseType.displayResId(): Int {
  return when (this) {
    LicenseType.NONE -> R.string.license_type_none
    LicenseType.REPAIRMAN -> R.string.license_type_repairman
    LicenseType.AMT -> R.string.license_type_amt
    else -> R.string.license_type_none
  }
}
