package dev.fanfly.wingslog.feature.sharing.datamanager.impl

import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.model.sharing.SharedAircraftRef
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.StorageEntity
import dev.fanfly.wingslog.id.ThingId
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

private const val MY_UID = "member-scope-001"
private const val HOST_UID = "host-scope-001"
private val OWN_AC = ThingId("ac-own")
private val SHARED_AC = ThingId("ac-shared")

class ThingScopeResolverImplTest {

  private lateinit var auth: FirebaseAuth
  private lateinit var refStore: EntityStore<SharedAircraftRef>
  private lateinit var resolver: ThingScopeResolverImpl

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

    // Default: no ref for any thing → own.
    every { refStore.observe(any(), any()) } returns flowOf(null)

    resolver = ThingScopeResolverImpl(auth, storeFactory)
  }

  @Test
  fun resolveNow_ownThing_usesMyUidScope() = runTest {
    assertThat(resolver.resolveNow(OWN_AC))
      .isEqualTo(EntityScope.thingChildUnsafe(MY_UID, OWN_AC.value_))
  }

  @Test
  fun resolveNow_sharedThing_usesHostScope() = runTest {
    seedSharedRef()

    assertThat(resolver.resolveNow(SHARED_AC))
      .isEqualTo(EntityScope.thingChildUnsafe(HOST_UID, SHARED_AC.value_))
  }

  @Test
  fun resolveNow_signedOut_throws() = runTest {
    every { auth.currentUser } returns null

    assertThat(runCatching { resolver.resolveNow(OWN_AC) }.isFailure).isTrue()
  }

  @Test
  fun resolve_ownThing_emitsMyUidScope() = runTest {
    assertThat(
      resolver.resolve(OWN_AC)
        .first()
    )
      .isEqualTo(EntityScope.thingChildUnsafe(MY_UID, OWN_AC.value_))
  }

  @Test
  fun resolve_sharedThing_emitsHostScope() = runTest {
    seedSharedRef()

    assertThat(
      resolver.resolve(SHARED_AC)
        .first()
    )
      .isEqualTo(EntityScope.thingChildUnsafe(HOST_UID, SHARED_AC.value_))
  }

  @Test
  fun resolve_signedOut_emitsNull() = runTest {
    every { auth.authStateChanged } returns flowOf(null)

    assertThat(
      resolver.resolve(OWN_AC)
        .first()
    ).isNull()
  }

  private fun seedSharedRef() {
    every {
      refStore.observe(
        SHARED_AC.value_,
        EntityScope.userRoot(MY_UID)
      )
    } returns flowOf(
      StorageEntity(
        id = SHARED_AC.value_,
        value = SharedAircraftRef(aircraft_id = SHARED_AC.value_, host_uid = HOST_UID),
        updatedAt = Instant.DISTANT_PAST,
      ),
    )
  }
}
