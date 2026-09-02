package dev.fanfly.wingslog.feature.fleet.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.model.sharing.ShareRole
import dev.fanfly.wingslog.core.model.sharing.SharedAircraftRef
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.StorageEntity
import dev.fanfly.wingslog.core.template.SpecKeys
import dev.fanfly.wingslog.core.template.ThingInflater
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Thing
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

private const val TEST_USER_ID = "test-user-123"
private const val TEST_THING_ID = "thing-456"
private const val HOST_UID = "host-user-999"

class FleetManagerImplTest {

  private lateinit var firebaseAuth: FirebaseAuth
  private lateinit var storeFactory: EntityStoreFactory
  private lateinit var store: EntityStore<Thing>
  private lateinit var refStore: EntityStore<SharedAircraftRef>
  private lateinit var manager: FleetManagerImpl

  @Before
  fun setUp() {
    firebaseAuth = mockk(relaxed = true)
    store = mockk(relaxed = true)
    refStore = mockk(relaxed = true)
    storeFactory = mockk(relaxed = true)

    @Suppress("UNCHECKED_CAST")
    every { storeFactory.create<Thing>(CollectionKind.Thing) } returns store
    @Suppress("UNCHECKED_CAST")
    every {
      storeFactory.create<SharedAircraftRef>(CollectionKind.SharedAircraftRef)
    } returns refStore
    // Default: no shared thing. Individual tests override.
    every { refStore.observeAll(any()) } returns flowOf(emptyList())
    every { refStore.observe(any(), any()) } returns flowOf(null)

    val mockUser = mockk<FirebaseUser>()
    every { mockUser.uid } returns TEST_USER_ID
    every { firebaseAuth.currentUser } returns mockUser
    every { firebaseAuth.authStateChanged } returns flowOf(mockUser)

    manager =
      // appVersionCode is arbitrary here: these tests use the baked-in template, whose floor is 0.
      FleetManagerImpl(
        firebaseAuth,
        BakedInTemplateRegistry(appVersionCode = 1),
        storeFactory,
      )
  }

  @Test
  fun observeFleetDashboard_withoutLoggedInUser_emitsEmptyList() = runTest {
    every { firebaseAuth.currentUser } returns null
    every { firebaseAuth.authStateChanged } returns flowOf(null)

    val result = manager.observeFleetDashboard()
      .first()

    assertThat(result).isEmpty()
  }

  @Test
  fun observeFleetDashboard_loggedIn_delegatesToStoreWithUserRootAndUnwrapsValues() =
    runTest {
      val thing = buildTestThing(id = TEST_THING_ID)
      val entity = StorageEntity(
        id = TEST_THING_ID,
        value = thing,
        updatedAt = Instant.DISTANT_PAST
      )
      every { store.observeAll(EntityScope.userRoot(TEST_USER_ID)) } returns flowOf(
        listOf(entity)
      )

      val result = manager.observeFleetDashboard()
        .first()

      assertThat(result).hasSize(1)
      assertThat(result.first().thing.id).isEqualTo(TEST_THING_ID)
      assertThat(result.first().shared).isFalse()
      assertThat(result.first().role).isEqualTo(ShareRole.SHARE_ROLE_OWNER)
      io.mockk.verify { store.observeAll(EntityScope.userRoot(TEST_USER_ID)) }
    }

  @Test
  fun observeFleetDashboard_withSharedRef_includesHostThingTaggedShared() =
    runTest {
      val own = buildTestThing(id = "own-1")
      val shared =
        buildTestThing(id = "shared-1", make = "Piper", model = "PA-28")
      every { store.observeAll(EntityScope.userRoot(TEST_USER_ID)) } returns flowOf(
        listOf(StorageEntity("own-1", own, Instant.DISTANT_PAST))
      )
      // A ref pointing at the host's thing, plus the live doc under the host's root.
      val ref = SharedAircraftRef(
        aircraft_id = "shared-1",
        host_uid = HOST_UID,
        role = ShareRole.SHARE_ROLE_TECHNICIAN,
      )
      every { refStore.observeAll(EntityScope.userRoot(TEST_USER_ID)) } returns flowOf(
        listOf(StorageEntity("shared-1", ref, Instant.DISTANT_PAST))
      )
      every {
        store.observe(
          "shared-1",
          EntityScope.userRoot(HOST_UID)
        )
      } returns flowOf(
        StorageEntity("shared-1", shared, Instant.DISTANT_PAST)
      )

      val result = manager.observeFleetDashboard()
        .first()

      assertThat(result).hasSize(2)
      val ownEntry = result.first { !it.shared }
      val sharedEntry = result.first { it.shared }
      assertThat(ownEntry.thing.id).isEqualTo("own-1")
      assertThat(ownEntry.role).isEqualTo(ShareRole.SHARE_ROLE_OWNER)
      assertThat(sharedEntry.thing.id).isEqualTo("shared-1")
      assertThat(sharedEntry.role).isEqualTo(ShareRole.SHARE_ROLE_TECHNICIAN)
    }

  @Test
  fun observeFleetDashboard_sharedRefWithUnsyncedDoc_isSkipped() = runTest {
    every { store.observeAll(EntityScope.userRoot(TEST_USER_ID)) } returns flowOf(
      emptyList()
    )
    val ref = SharedAircraftRef(aircraft_id = "shared-1", host_uid = HOST_UID)
    every { refStore.observeAll(EntityScope.userRoot(TEST_USER_ID)) } returns flowOf(
      listOf(StorageEntity("shared-1", ref, Instant.DISTANT_PAST))
    )
    // Thing doc not synced yet → null.
    every {
      store.observe(
        "shared-1",
        EntityScope.userRoot(HOST_UID)
      )
    } returns flowOf(null)

    val result = manager.observeFleetDashboard()
      .first()

    assertThat(result).isEmpty()
  }

  @Test
  fun loadThing_withoutLoggedInUser_emitsNull() = runTest {
    every { firebaseAuth.currentUser } returns null
    every { firebaseAuth.authStateChanged } returns flowOf(null)

    val result = manager.loadThing(TEST_THING_ID)
      .first()

    assertThat(result).isNull()
  }

  @Test
  fun loadThing_loggedIn_delegatesToStoreAndUnwrapsValue() = runTest {
    val thing = buildTestThing(id = TEST_THING_ID)
    val entity = StorageEntity(
      id = TEST_THING_ID,
      value = thing,
      updatedAt = Instant.DISTANT_PAST
    )
    every {
      store.observe(
        TEST_THING_ID,
        EntityScope.userRoot(TEST_USER_ID)
      )
    } returns flowOf(entity)

    val result = manager.loadThing(TEST_THING_ID)
      .first()

    assertThat(result).isEqualTo(thing)
  }

  @Test
  fun loadThing_sharedThing_readsFromHostRoot() = runTest {
    val shared =
      buildTestThing(id = "shared-1", make = "Piper", model = "PA-28")
    // A ref for this id names the host; the doc lives under the host's root.
    every {
      refStore.observe(
        "shared-1",
        EntityScope.userRoot(TEST_USER_ID)
      )
    } returns flowOf(
      StorageEntity(
        "shared-1",
        SharedAircraftRef(aircraft_id = "shared-1", host_uid = HOST_UID),
        Instant.DISTANT_PAST,
      )
    )
    every {
      store.observe(
        "shared-1",
        EntityScope.userRoot(HOST_UID)
      )
    } returns flowOf(
      StorageEntity("shared-1", shared, Instant.DISTANT_PAST)
    )

    val result = manager.loadThing("shared-1")
      .first()

    assertThat(result).isEqualTo(shared)
    io.mockk.verify {
      store.observe(
        "shared-1",
        EntityScope.userRoot(HOST_UID)
      )
    }
  }

  @Test
  fun updateThing_withEmptyId_generatesIdAndCallsStorePut() = runTest {
    val thing = buildTestThing(id = "")

    val result = manager.updateThing(thing)

    assertThat(result.isSuccess).isTrue()
    coVerify {
      store.put(
        match { it.isNotEmpty() },
        match { it.id.isNotEmpty() },
        EntityScope.userRoot(TEST_USER_ID),
      )
    }
  }

  @Test
  fun updateThing_withExistingId_preservesIdAndCallsStorePut() = runTest {
    val thing = buildTestThing(id = TEST_THING_ID)

    val result = manager.updateThing(thing)

    assertThat(result.isSuccess).isTrue()
    coVerify {
      store.put(
        TEST_THING_ID,
        // Inflated on the way out (#717) — these assertions are about id and scope, not payload.
        ThingInflater.inflate(thing, AirplaneTemplate.TEMPLATE),
        EntityScope.userRoot(TEST_USER_ID)
      )
    }
  }

  @Test
  fun updateThing_withoutLoggedInUser_returnsFailure() = runTest {
    every { firebaseAuth.currentUser } returns null

    val result =
      manager.updateThing(buildTestThing(id = TEST_THING_ID))

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun deleteThing_loggedIn_callsStoreDeleteAndReturnsSuccess() = runTest {
    val result = manager.deleteThing(TEST_THING_ID)

    assertThat(result.isSuccess).isTrue()
    coVerify {
      store.delete(
        TEST_THING_ID,
        EntityScope.userRoot(TEST_USER_ID)
      )
    }
  }

  @Test
  fun deleteThing_withoutLoggedInUser_returnsFailure() = runTest {
    every { firebaseAuth.currentUser } returns null

    val result = manager.deleteThing(TEST_THING_ID)

    assertThat(result.isFailure).isTrue()
  }

  private fun buildTestThing(
    id: String = TEST_THING_ID,
    make: String = "Cessna",
    model: String = "172",
  ): Thing = Thing(
    id = id,
    // Spec entries: fields 2-6 are reserved (#668).
    spec = listOf(
      Spec(key = SpecKeys.MAKE, value_ = make),
      Spec(key = SpecKeys.MODEL, value_ = model),
    ),
  )

  // --- Writes must land in the tree the thing actually lives in (#143) ---

  @Test
  fun updateThing_shared_writesToTheHostsTree() = runTest {
    // A co-owner editing a shared thing must write the *host's* row. Writing our own root would
    // not fail — it would silently fork a second copy of the thing into our tree, so the edit
    // vanishes (the read still resolves to the host's doc) and the fork reads back as ours.
    every {
      refStore.observe(
        "shared-1",
        EntityScope.userRoot(TEST_USER_ID)
      )
    } returns flowOf(
      StorageEntity(
        "shared-1",
        SharedAircraftRef(aircraft_id = "shared-1", host_uid = HOST_UID),
        Instant.DISTANT_PAST,
      )
    )
    val edited = Thing(
      id = "shared-1",
      spec = listOf(
        Spec(key = SpecKeys.MAKE, value_ = "Cessna"),
        Spec(key = SpecKeys.MODEL, value_ = "172"),
        Spec(key = SpecKeys.TAIL_NUMBER, value_ = "N999XX"),
      ),
    )

    val result = manager.updateThing(edited)

    assertThat(result.isSuccess).isTrue()
    coVerify {
      store.put(
        "shared-1",
        ThingInflater.inflate(edited, AirplaneTemplate.TEMPLATE),
        EntityScope.userRoot(HOST_UID),
      )
    }
    coVerify(exactly = 0) {
      store.put(
        "shared-1",
        any(),
        EntityScope.userRoot(TEST_USER_ID)
      )
    }
  }

  @Test
  fun updateThing_own_writesToOwnTree() = runTest {
    val mine = Thing(
      id = "own-1",
      spec = listOf(
        Spec(key = SpecKeys.MAKE, value_ = "Cessna"),
        Spec(key = SpecKeys.MODEL, value_ = "172"),
      ),
    )

    manager.updateThing(mine)

    coVerify {
      store.put(
        "own-1",
        ThingInflater.inflate(mine, AirplaneTemplate.TEMPLATE),
        EntityScope.userRoot(TEST_USER_ID),
      )
    }

    // What the inflater still contributes on the way to the store. It no longer *derives* spec or
    // components — the form writes those, and deriving would overwrite them (#668 part 3) — so what
    // is asserted here is that the caller's values reach the store untouched, plus the DNA the
    // inflater does add.
    val stored = slot<Thing>()
    coVerify { store.put("own-1", capture(stored), any()) }
    assertThat(stored.captured.spec.map { it.key }).containsExactly(
      "make",
      "model"
    )
      .inOrder()
    // Everything but the words. The lexicon is app UI resolved by template id at render, so it is
    // stripped before storing rather than forked into every Thing (see LexiconOwnershipTest).
    assertThat(stored.captured.template)
      .isEqualTo(AirplaneTemplate.TEMPLATE.copy(lexicon = null))
    // A name, derived from spec for a Thing that arrived without one.
    assertThat(stored.captured.name).isEqualTo("Cessna 172")
  }

  @Test
  fun deleteThing_shared_isRefused() = runTest {
    // Deleting tears the share down for everyone, so it is the hosting owner's alone. Queuing a
    // tombstone we know the rules will deny is worse than refusing: since #144 the client reads a
    // denied write into a host's tree as its own revocation, and would purge the share over it.
    every {
      refStore.observe(
        "shared-1",
        EntityScope.userRoot(TEST_USER_ID)
      )
    } returns flowOf(
      StorageEntity(
        "shared-1",
        SharedAircraftRef(aircraft_id = "shared-1", host_uid = HOST_UID),
        Instant.DISTANT_PAST,
      )
    )

    val result = manager.deleteThing("shared-1")

    assertThat(result.isFailure).isTrue()
    coVerify(exactly = 0) { store.delete(any(), any()) }
  }
}
