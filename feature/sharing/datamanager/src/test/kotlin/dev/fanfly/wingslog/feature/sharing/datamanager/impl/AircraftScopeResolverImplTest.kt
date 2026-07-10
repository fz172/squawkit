package dev.fanfly.wingslog.feature.sharing.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.model.sharing.SharedAircraftRef
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.StorageEntity
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

private const val MY_UID = "member-scope-001"
private const val HOST_UID = "host-scope-001"
private const val OWN_AC = "ac-own"
private const val SHARED_AC = "ac-shared"

class AircraftScopeResolverImplTest {

  private lateinit var auth: FirebaseAuth
  private lateinit var refStore: EntityStore<SharedAircraftRef>
  private lateinit var resolver: AircraftScopeResolverImpl

  @Before
  fun setUp() {
    auth = mockk(relaxed = true)
    refStore = mockk(relaxed = true)
    val storeFactory = mockk<EntityStoreFactory>(relaxed = true)

    @Suppress("UNCHECKED_CAST")
    every {
      storeFactory.create<SharedAircraftRef>(CollectionKind.SharedAircraftRef)
    } returns refStore

    val user = mockk<FirebaseUser>()
    every { user.uid } returns MY_UID
    every { auth.currentUser } returns user
    every { auth.authStateChanged } returns flowOf(user)

    // Default: no ref for any aircraft → own.
    every { refStore.observe(any(), any()) } returns flowOf(null)

    resolver = AircraftScopeResolverImpl(auth, storeFactory)
  }

  @Test
  fun resolveNow_ownAircraft_usesMyUidScope() = runTest {
    assertThat(resolver.resolveNow(OWN_AC))
      .isEqualTo(EntityScope.aircraftChild(MY_UID, OWN_AC))
  }

  @Test
  fun resolveNow_sharedAircraft_usesHostScope() = runTest {
    seedSharedRef()

    assertThat(resolver.resolveNow(SHARED_AC))
      .isEqualTo(EntityScope.aircraftChild(HOST_UID, SHARED_AC))
  }

  @Test
  fun resolveNow_signedOut_throws() = runTest {
    every { auth.currentUser } returns null

    assertThat(runCatching { resolver.resolveNow(OWN_AC) }.isFailure).isTrue()
  }

  @Test
  fun resolve_ownAircraft_emitsMyUidScope() = runTest {
    assertThat(resolver.resolve(OWN_AC).first())
      .isEqualTo(EntityScope.aircraftChild(MY_UID, OWN_AC))
  }

  @Test
  fun resolve_sharedAircraft_emitsHostScope() = runTest {
    seedSharedRef()

    assertThat(resolver.resolve(SHARED_AC).first())
      .isEqualTo(EntityScope.aircraftChild(HOST_UID, SHARED_AC))
  }

  @Test
  fun resolve_signedOut_emitsNull() = runTest {
    every { auth.authStateChanged } returns flowOf(null)

    assertThat(resolver.resolve(OWN_AC).first()).isNull()
  }

  private fun seedSharedRef() {
    every { refStore.observe(SHARED_AC, EntityScope.userRoot(MY_UID)) } returns flowOf(
      StorageEntity(
        id = SHARED_AC,
        value = SharedAircraftRef(aircraft_id = SHARED_AC, host_uid = HOST_UID),
        updatedAt = Instant.DISTANT_PAST,
      ),
    )
  }
}
