package dev.fanfly.wingslog.feature.subscription.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.StorageEntity
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

private const val TEST_USER_ID = "user-1"

class SubscriptionManagerImplTest {

  private lateinit var firebaseAuth: FirebaseAuth
  private lateinit var storeFactory: EntityStoreFactory
  private lateinit var store: EntityStore<Subscription>

  @Before
  fun setUp() {
    firebaseAuth = mockk(relaxed = true)
    store = mockk(relaxed = true)
    storeFactory = mockk(relaxed = true)
    every { storeFactory.create<Subscription>(CollectionKind.Subscription) } returns store

    val user = mockk<FirebaseUser>()
    every { user.uid } returns TEST_USER_ID
    every { firebaseAuth.authStateChanged } returns flowOf(user)
    // Default: no entitlement doc → the manager reads FREE.
    every { store.observe(any(), any()) } returns flowOf(null)
  }

  private fun capability(subscription: Boolean, devBuild: Boolean = false) = AppCapability(
    isDeveloperOptionsSupported = devBuild,
    isAircraftSharingSupported = false,
    isStressTestSupported = false,
    isCameraCaptureSupported = false,
    isAnonymousLoginSupported = false,
    isAppleSignInSupported = false,
    isSubscriptionSupported = subscription,
  )

  private fun manager(
    capability: AppCapability,
    forceStatus: Flow<Subscription.Status?> = flowOf(null),
  ) = SubscriptionManagerImpl(
    firebaseAuth = firebaseAuth,
    storeFactory = storeFactory,
    appCapability = capability,
    forceStatus = forceStatus,
  )

  private fun entitle(subscription: Subscription) {
    every { store.observe(any(), any()) } returns
      flowOf(StorageEntity("main", subscription, Instant.fromEpochMilliseconds(0)))
  }

  private val proActive = Subscription(
    status = Subscription.Status.STATUS_PRO,
    lifecycle = Subscription.Lifecycle.LIFECYCLE_ACTIVE,
  )

  @Test
  fun `capability off - every gate is open regardless of entitlement`() = runTest {
    // FREE entitlement (default stub), but the capability is off, so there is no paywall.
    val m = manager(capability(subscription = false))
    assertThat(m.canUploadAttachments().first()).isTrue()
    assertThat(m.canEmailExports().first()).isTrue()
    assertThat(m.canHostShare().first()).isTrue()
    assertThat(m.aircraftLimit().first()).isNull()
  }

  @Test
  fun `capability on with PRO active - gates open and aircraft unlimited`() = runTest {
    entitle(proActive)
    val m = manager(capability(subscription = true))
    assertThat(m.status().first()).isEqualTo(Subscription.Status.STATUS_PRO)
    assertThat(m.canUploadAttachments().first()).isTrue()
    assertThat(m.aircraftLimit().first()).isNull()
  }

  @Test
  fun `capability on with FREE - gates closed and one aircraft`() = runTest {
    val m = manager(capability(subscription = true)) // default FREE stub
    assertThat(m.status().first()).isEqualTo(Subscription.Status.STATUS_FREE)
    assertThat(m.canUploadAttachments().first()).isFalse()
    assertThat(m.canEmailExports().first()).isFalse()
    assertThat(m.canHostShare().first()).isFalse()
    assertThat(m.aircraftLimit().first()).isEqualTo(1)
  }

  @Test
  fun `dev override forces PRO in a developer build`() = runTest {
    // FREE entitlement, but a developer build forces PRO.
    val m = manager(
      capability(subscription = true, devBuild = true),
      forceStatus = flowOf(Subscription.Status.STATUS_PRO),
    )
    assertThat(m.status().first()).isEqualTo(Subscription.Status.STATUS_PRO)
    assertThat(m.canUploadAttachments().first()).isTrue()
  }

  @Test
  fun `dev override is ignored in a release build`() = runTest {
    val m = manager(
      capability(subscription = true, devBuild = false),
      forceStatus = flowOf(Subscription.Status.STATUS_PRO),
    )
    assertThat(m.status().first()).isEqualTo(Subscription.Status.STATUS_FREE)
    assertThat(m.canUploadAttachments().first()).isFalse()
  }
}
