package dev.fanfly.wingslog.userprofile.manager

import dev.fanfly.wingslog.userprofile.data.LicenseInfo

interface UserProfileManager {
  /**
   * Loads the current user's license info from the data source.
   */
  fun observeLicenseInfo(): kotlinx.coroutines.flow.Flow<LicenseInfo?>

  /**
   * Updates the current user's profile in the data source.

   */
  suspend fun updateLicenseInfo(licenseInfo: LicenseInfo): Result<Boolean>
}