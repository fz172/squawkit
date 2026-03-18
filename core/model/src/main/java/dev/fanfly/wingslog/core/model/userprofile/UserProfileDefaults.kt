package dev.fanfly.wingslog.core.model.userprofile

fun newUserLicenseProfile() = LicenseInfo(
  license_type = LicenseType.NONE,
  expireLimit = LicenseExpireLimit.NEVER_EXPIRES
)
