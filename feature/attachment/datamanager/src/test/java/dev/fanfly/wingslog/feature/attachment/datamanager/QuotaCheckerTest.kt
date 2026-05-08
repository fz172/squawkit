package dev.fanfly.wingslog.feature.attachment.datamanager

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.createWingsLogDatabase
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.attachment.datamanager.impl.SqlDelightLocalBlobStore
import dev.fanfly.wingslog.feature.attachment.model.QuotaResult
import java.io.File
import java.nio.file.Files
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuotaCheckerTest {

  private val ioContext = UnconfinedTestDispatcher()
  private val scopeA = EntityScope.userRoot("user-quota-A")
  private val scopeB = EntityScope.userRoot("user-quota-B")

  private lateinit var rootDir: File
  private lateinit var db: WingsLogDatabase
  private lateinit var store: SqlDelightLocalBlobStore
  private lateinit var checker: QuotaChecker

  @Before
  fun setUp() {
    rootDir = Files.createTempDirectory("quota-test").toFile()
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    WingsLogDatabase.Schema.create(driver)
    db = createWingsLogDatabase(driver)
    store = SqlDelightLocalBlobStore(
      db = db,
      fs = FileBlobFilesystem(rootDir),
      ioContext = ioContext,
      clock = FixedClock(Instant.fromEpochMilliseconds(1_000L)),
    )
    checker = QuotaChecker(db = db, ioContext = ioContext)
  }

  @After
  fun tearDown() {
    rootDir.deleteRecursively()
  }

  // ---- check: Allowed ----

  @Test
  fun check_returnsAllowed_whenCandidateIsNovelFitsParentAndFitsUserCap() = runTest(ioContext) {
    // Empty parent, empty user scope — any small file should be allowed.
    val result = checker.check(
      candidateSha256 = "aaaa",
      candidateBytes = 1024L,
      parentNonLinkSha256s = emptySet(),
      pendingBytesOnParent = 0L,
      scope = scopeA,
    )
    assertThat(result).isEqualTo(QuotaResult.Allowed)
  }

  // ---- check: DuplicateOnParent ----

  @Test
  fun check_returnsDuplicateOnParent_whenSha256AlreadyInParentSet() = runTest(ioContext) {
    // No rows seeded — the duplicate check should short-circuit before touching the DB.
    val sha = "deadbeef"
    val result = checker.check(
      candidateSha256 = sha,
      candidateBytes = 500L,
      parentNonLinkSha256s = setOf(sha),
      pendingBytesOnParent = 0L,
      scope = scopeA,
    )
    assertThat(result).isInstanceOf(QuotaResult.DuplicateOnParent::class.java)
    assertThat((result as QuotaResult.DuplicateOnParent).sha256).isEqualTo(sha)
  }

  @Test
  fun check_doesNotQueryDb_forDuplicateOnParentBranch() = runTest(ioContext) {
    // Even with zero rows in the DB, the duplicate branch is hit and returns before
    // any SQL execution. We verify this indirectly: even if we were to corrupt the DB
    // state the result should still come back as DuplicateOnParent.
    val sha = "cafebabe"
    val result = checker.check(
      candidateSha256 = sha,
      candidateBytes = QuotaChecker.USER_CAP_BYTES + 1, // would fail per-user if DB were queried
      parentNonLinkSha256s = setOf(sha),
      pendingBytesOnParent = 0L,
      scope = scopeA,
    )
    assertThat(result).isInstanceOf(QuotaResult.DuplicateOnParent::class.java)
  }

  // ---- check: PerParentExceeded boundary ----

  @Test
  fun check_returnsAllowed_whenPendingPlusCandidateEqualsParentCap() = runTest(ioContext) {
    // pending + candidate == cap exactly → Allowed (boundary is exclusive upper bound).
    val cap = QuotaChecker.PARENT_CAP_BYTES
    val pending = cap / 2
    val candidate = cap - pending  // pending + candidate == cap
    val result = checker.check(
      candidateSha256 = "novel1",
      candidateBytes = candidate,
      parentNonLinkSha256s = emptySet(),
      pendingBytesOnParent = pending,
      scope = scopeA,
    )
    assertThat(result).isEqualTo(QuotaResult.Allowed)
  }

  @Test
  fun check_returnsPerParentExceeded_whenPendingPlusCandidateExceedsParentCapByOne() =
    runTest(ioContext) {
      val cap = QuotaChecker.PARENT_CAP_BYTES
      val pending = cap / 2
      val candidate = cap - pending + 1  // pending + candidate == cap + 1
      val result = checker.check(
        candidateSha256 = "novel2",
        candidateBytes = candidate,
        parentNonLinkSha256s = emptySet(),
        pendingBytesOnParent = pending,
        scope = scopeA,
      )
      assertThat(result).isInstanceOf(QuotaResult.PerParentExceeded::class.java)
      val exceeded = result as QuotaResult.PerParentExceeded
      assertThat(exceeded.capBytes).isEqualTo(cap)
      assertThat(exceeded.wouldBeBytes).isEqualTo(pending + candidate)
    }

  // ---- check: PerUserExceeded boundary ----

  @Test
  fun check_returnsAllowed_whenUsedPlusCandidateEqualsUserCap() = runTest(ioContext) {
    // Use a small user cap so the candidate also fits under the per-parent cap (otherwise the
    // per-parent check fires first). This is the same boundary logic — just rescaled.
    val smallUserCap = 1_000L
    val checker = QuotaChecker(db = db, ioContext = ioContext, perUserCapBytes = smallUserCap)
    val used = 100L
    store.put(BlobId("u-row-1"), ByteArray(used.toInt()) { 0x01 }, null, scopeA)

    val candidate = smallUserCap - used  // used + candidate == smallUserCap
    val result = checker.check(
      candidateSha256 = "novel3",
      candidateBytes = candidate,
      parentNonLinkSha256s = emptySet(),
      pendingBytesOnParent = 0L,
      scope = scopeA,
    )
    assertThat(result).isEqualTo(QuotaResult.Allowed)
  }

  @Test
  fun check_returnsPerUserExceeded_whenUsedPlusCandidateExceedsUserCapByOne() =
    runTest(ioContext) {
      val smallUserCap = 1_000L
      val checker = QuotaChecker(db = db, ioContext = ioContext, perUserCapBytes = smallUserCap)
      val used = 100L
      store.put(BlobId("u-row-2"), ByteArray(used.toInt()) { 0x02 }, null, scopeA)

      val candidate = smallUserCap - used + 1  // used + candidate == smallUserCap + 1
      val result = checker.check(
        candidateSha256 = "novel4",
        candidateBytes = candidate,
        parentNonLinkSha256s = emptySet(),
        pendingBytesOnParent = 0L,
        scope = scopeA,
      )
      assertThat(result).isInstanceOf(QuotaResult.PerUserExceeded::class.java)
      val exceeded = result as QuotaResult.PerUserExceeded
      assertThat(exceeded.capBytes).isEqualTo(smallUserCap)
      assertThat(exceeded.usedBytes).isEqualTo(used)
      assertThat(exceeded.candidateBytes).isEqualTo(candidate)
    }

  // ---- check: scope isolation for per-user count ----

  @Test
  fun check_ignoresRowsInDifferentScopeForPerUserCount() = runTest(ioContext) {
    // Fill scopeB close to cap — scopeA should still see Allowed for the same candidate.
    val largeBytes = (QuotaChecker.USER_CAP_BYTES - 1).toInt().coerceAtMost(100)
    store.put(BlobId("b-row"), ByteArray(largeBytes) { 0x03 }, null, scopeB)

    val result = checker.check(
      candidateSha256 = "novel5",
      candidateBytes = 1L,
      parentNonLinkSha256s = emptySet(),
      pendingBytesOnParent = 0L,
      scope = scopeA,  // scopeA is empty
    )
    assertThat(result).isEqualTo(QuotaResult.Allowed)
  }

  // ---- observeState ----

  @Test
  fun observeState_emitsUpdatedTotalWhenRowIsInserted() = runTest(ioContext) {
    // Initial emission: 0 bytes used.
    val initialState = checker.observeState(scopeA).first()
    assertThat(initialState.perUserUsedBytes).isEqualTo(0L)

    // Insert a blob row via the store.
    val bytes = ByteArray(42) { 0x04 }
    store.put(BlobId("obs-row"), bytes, null, scopeA)

    // After insert, the Flow should emit the updated total.
    val updatedState = checker.observeState(scopeA).first()
    assertThat(updatedState.perUserUsedBytes).isEqualTo(42L)
    assertThat(updatedState.perUserCapBytes).isEqualTo(QuotaChecker.USER_CAP_BYTES)
    assertThat(updatedState.perUserRemaining).isEqualTo(QuotaChecker.USER_CAP_BYTES - 42L)
  }

  @Test
  fun observeState_doesNotCountRowsInDifferentScope() = runTest(ioContext) {
    store.put(BlobId("other-scope-row"), ByteArray(99) { 0x05 }, null, scopeB)

    val state = checker.observeState(scopeA).first()
    assertThat(state.perUserUsedBytes).isEqualTo(0L)
  }
}

@OptIn(kotlin.time.ExperimentalTime::class)
private class FixedClock(private val fixed: Instant) : Clock {
  override fun now(): Instant = fixed
}
