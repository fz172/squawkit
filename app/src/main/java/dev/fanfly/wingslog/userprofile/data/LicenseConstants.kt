package dev.fanfly.wingslog.dev.fanfly.wingslog.userprofile.data

import androidx.annotation.StringRes
import dev.fanfly.wingslog.R

enum class LicenseType(@StringRes val displayResId: Int) {
  NONE(R.string.license_type_none),
  REPAIRMAN(R.string.license_type_repairman),
  AMT(R.string.license_type_amt),
}