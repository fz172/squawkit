package dev.fanfly.wingslog.feature.notifications.engine

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.createWingsLogDatabase
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class UrgencyWatermarkStoreTest {
  companion object {
    private const val TEST_UID = "user-watermark-001"
    private const val TEST_THING_ID = "thing-watermark-001"
    private val TEST_SCOPE = EntityScope.thingChildUnsafe(
      TEST_UID,
      TEST_THING_ID
    )
    private val TEST_KIND = CollectionKind.MaintenanceTask
  }

  private lateinit var db: WingsLogDatabase
  private lateinit var store: UrgencyWatermarkStore

  @Before
  fun setUp() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    WingsLogDatabase.Schema.synchronous()
      .create(driver)
    db = createWingsLogDatabase(driver)
    store = UrgencyWatermarkStore(db)
  }

  // selectInScopePrefix — empty when nothing has been written

  @Test
  fun selectInScopePrefix_noRows_returnsEmpty() = runTest {
    val watermarks = store.selectInScopePrefix(
      TEST_UID,
      TEST_SCOPE.toPath() + "%"
    )
    assertThat(watermarks).isEmpty()
  }

  // upsert — writes a row readable back at the same key

  @Test
  fun upsert_thenSelect_returnsTheWrittenRank() = runTest {
    store.upsert(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      id = "task-1",
      rank = 2
    )

    val watermarks = store.selectInScopePrefix(
      TEST_UID,
      TEST_SCOPE.toPath() + "%"
    )
    assertThat(watermarks).hasSize(1)
    assertThat(watermarks.single().rank).isEqualTo(2)
    assertThat(watermarks.single().id).isEqualTo("task-1")
    assertThat(watermarks.single().collection).isEqualTo(TEST_KIND)
  }

  @Test
  fun upsert_calledTwiceForSameId_replacesRatherThanDuplicates() = runTest {
    store.upsert(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      id = "task-1",
      rank = 1
    )
    store.upsert(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      id = "task-1",
      rank = 2
    )

    val watermarks = store.selectInScopePrefix(
      TEST_UID,
      TEST_SCOPE.toPath() + "%"
    )
    assertThat(watermarks).hasSize(1)
    assertThat(watermarks.single().rank).isEqualTo(2)
  }

  @Test
  fun upsert_lowerRankThanPrevious_stillOverwrites() = runTest {
    // De-escalations are silent but not forgotten (design §6.3) — the watermark moves down too, so
    // a task that is complied and later comes due again notifies again.
    store.upsert(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      id = "task-1",
      rank = 2
    )
    store.upsert(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      id = "task-1",
      rank = 0
    )

    val watermarks = store.selectInScopePrefix(
      TEST_UID,
      TEST_SCOPE.toPath() + "%"
    )
    assertThat(watermarks.single().rank).isEqualTo(0)
  }

  // pruneNotIn — drops rows for the given (collection, scope) not present in seenIds

  @Test
  fun pruneNotIn_dropsIdsNotInSeenSet() = runTest {
    store.upsert(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      id = "task-1",
      rank = 1
    )
    store.upsert(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      id = "task-2",
      rank = 1
    )

    store.pruneNotIn(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      seenIds = listOf("task-1")
    )

    val watermarks = store.selectInScopePrefix(
      TEST_UID,
      TEST_SCOPE.toPath() + "%"
    )
    assertThat(watermarks.map { it.id }).containsExactly("task-1")
  }

  @Test
  fun pruneNotIn_doesNotTouchOtherScopes() = runTest {
    val otherScope = EntityScope.thingChildUnsafe(
      TEST_UID,
      "other-thing"
    )
    store.upsert(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      id = "task-1",
      rank = 1
    )
    store.upsert(
      TEST_UID,
      TEST_KIND,
      otherScope,
      id = "task-1",
      rank = 1
    )

    // Pruning TEST_SCOPE with an empty seen set must not touch otherScope's watermark, even though
    // it shares the same id — an un-hydrated thing's watermarks must survive untouched.
    store.pruneNotIn(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      seenIds = emptyList()
    )

    val remaining = store.selectInScopePrefix(
      TEST_UID,
      "/users/$TEST_UID/%"
    )
    assertThat(remaining).hasSize(1)
    assertThat(remaining.single().scope).isEqualTo(otherScope)
  }

  @Test
  fun pruneNotIn_doesNotTouchOtherCollections() = runTest {
    store.upsert(
      TEST_UID,
      CollectionKind.MaintenanceTask,
      TEST_SCOPE,
      id = "record-1",
      rank = 1
    )
    store.upsert(
      TEST_UID,
      CollectionKind.Squawk,
      TEST_SCOPE,
      id = "record-1",
      rank = 1
    )

    store.pruneNotIn(
      TEST_UID,
      CollectionKind.MaintenanceTask,
      TEST_SCOPE,
      seenIds = emptyList()
    )

    val remaining = store.selectInScopePrefix(
      TEST_UID,
      TEST_SCOPE.toPath() + "%"
    )
    assertThat(remaining).hasSize(1)
    assertThat(remaining.single().collection).isEqualTo(CollectionKind.Squawk)
  }

  // deleteForUser — clears every watermark for that uid, leaves other users alone

  @Test
  fun deleteForUser_removesAllRowsForThatUid() = runTest {
    store.upsert(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      id = "task-1",
      rank = 1
    )

    store.deleteForUser(TEST_UID)

    val watermarks = store.selectInScopePrefix(
      TEST_UID,
      "/users/$TEST_UID/%"
    )
    assertThat(watermarks).isEmpty()
  }

  @Test
  fun deleteForUser_doesNotTouchOtherUsers() = runTest {
    val otherUid = "user-watermark-002"
    val otherScope = EntityScope.thingChildUnsafe(
      otherUid,
      TEST_THING_ID
    )
    store.upsert(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      id = "task-1",
      rank = 1
    )
    store.upsert(
      otherUid,
      TEST_KIND,
      otherScope,
      id = "task-1",
      rank = 1
    )

    store.deleteForUser(TEST_UID)

    val otherWatermarks = store.selectInScopePrefix(
      otherUid,
      "/users/$otherUid/%"
    )
    assertThat(otherWatermarks).hasSize(1)
  }
}
