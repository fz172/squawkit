package dev.fanfly.wingslog.feature.userprofile.userprofilecard.utils

import dev.fanfly.wingslog.core.model.userprofile.LicenseType
import org.jetbrains.compose.resources.StringResource
import wingslog.feature.userprofile.userprofilecard.generated.resources.Res as UserProfileCardRes
import wingslog.feature.userprofile.userprofilecard.generated.resources.*

fun LicenseType.displayResId(): StringResource {
  return when (this) {
    LicenseType.NONE -> UserProfileCardRes.string.license_type_none
    LicenseType.REPAIRMAN -> UserProfileCardRes.string.license_type_repairman
    LicenseType.AMT -> UserProfileCardRes.string.license_type_amt
    else -> UserProfileCardRes.string.license_type_none
  }
}
