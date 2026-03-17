package dev.fanfly.wingslog.core.model.userprofile

import dev.fanfly.wingslog.core.model.userprofile.LicenseExpireLimit
import dev.fanfly.wingslog.core.model.userprofile.LicenseType
import dev.fanfly.wingslog.core.model.userprofile.licenseInfo

fun newUserLicenseProfile() = licenseInfo {
  licenseType = LicenseType.NONE
  expireLimit = LicenseExpireLimit.NEVER_EXPIRES
}
