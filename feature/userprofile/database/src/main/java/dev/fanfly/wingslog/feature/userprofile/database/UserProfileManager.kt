package dev.fanfly.wingslog.feature.userprofile.database

import dev.fanfly.wingslog.core.model.userprofile.LicenseInfo

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