package dev.fanfly.wingslog.feature.userprofile.database.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.model.userprofile.LicenseInfo
import dev.fanfly.wingslog.core.model.userprofile.newUserLicenseProfile
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.StorageEntity
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

private const val TEST_USER_ID = "test-user-123"
private const val LICENSE_INFO_ID = "main"

class UserProfileManagerImplTest {

  private lateinit var firebaseAuth: FirebaseAuth
  private lateinit var storeFactory: EntityStoreFactory
  private lateinit var store: EntityStore<LicenseInfo>
  private lateinit var manager: UserProfileManagerImpl

  @Before
  fun setUp() {
    firebaseAuth = mockk(relaxed = true)
    store = mockk(relaxed = true)
    storeFactory = mockk(relaxed = true)

    @Suppress("UNCHECKED_CAST")
    every { storeFactory.create<LicenseInfo>(CollectionKind.LicenseInfo) } returns store

    val mockUser = mockk<FirebaseUser>()
    every { mockUser.uid } returns TEST_USER_ID
    every { firebaseAuth.currentUser } returns mockUser
    every { firebaseAuth.authStateChanged } returns flowOf(mockUser)

    manager = UserProfileManagerImpl(firebaseAuth, storeFactory)
  }

  @Test
  fun observeLicenseInfo_withoutLoggedInUser_emitsNull() = runTest {
    every { firebaseAuth.currentUser } returns null
    every { firebaseAuth.authStateChanged } returns flowOf(null)

    val result = manager.observeLicenseInfo().first()

    assertThat(result).isNull()
  }

  @Test
  fun observeLicenseInfo_loggedIn_storeHasRow_emitsStoredValue() = runTest {
    val licenseInfo = buildTestLicenseInfo()
    val entity = StorageEntity(id = LICENSE_INFO_ID, value = licenseInfo, updatedAt = Instant.DISTANT_PAST)
    every { store.observe(LICENSE_INFO_ID, EntityScope.userRoot(TEST_USER_ID)) } returns flowOf(entity)

    val result = manager.observeLicenseInfo().first()

    assertThat(result).isEqualTo(licenseInfo)
  }

  @Test
  fun observeLicenseInfo_loggedIn_storeRowIsNull_emitsNewUserLicenseProfile() = runTest {
    every { store.observe(LICENSE_INFO_ID, EntityScope.userRoot(TEST_USER_ID)) } returns flowOf(null)

    val result = manager.observeLicenseInfo().first()

    assertThat(result).isNotNull()
    assertThat(result).isEqualTo(newUserLicenseProfile())
  }

  @Test
  fun updateLicenseInfo_loggedIn_callsStorePutWithMainIdAndReturnsSuccess() = runTest {
    val licenseInfo = buildTestLicenseInfo()

    val result = manager.updateLicenseInfo(licenseInfo)

    assertThat(result.isSuccess).isTrue()
    coVerify { store.put(LICENSE_INFO_ID, licenseInfo, EntityScope.userRoot(TEST_USER_ID)) }
  }

  @Test
  fun updateLicenseInfo_withoutLoggedInUser_returnsFailure() = runTest {
    every { firebaseAuth.currentUser } returns null

    val result = manager.updateLicenseInfo(buildTestLicenseInfo())

    assertThat(result.isFailure).isTrue()
  }

  private fun buildTestLicenseInfo(): LicenseInfo = LicenseInfo()
}
