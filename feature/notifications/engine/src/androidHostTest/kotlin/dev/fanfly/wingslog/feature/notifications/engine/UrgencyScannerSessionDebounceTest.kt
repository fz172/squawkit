package dev.fanfly.wingslog.feature.notifications.engine

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.model.settings.NotificationSettings
import dev.fanfly.wingslog.core.storage.createWingsLogDatabase
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.feature.notifications.datamanager.NotificationPrefsManager
import dev.fanfly.wingslog.feature.notifications.datamanager.PrefsState
import dev.fanfly.wingslog.feature.notifications.model.ScanTrigger
import dev.fanfly.wingslog.feature.notifications.model.withAllEnabled
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The 2h session debounce (design §6.6). Every case here stops at the `allEnabled == false` exit,
 * which is deliberate: the debounce is checked *before* preferences, so `Disabled` proves the scan
 * got past the debounce without needing a fleet, a notifier, or entity stores.
 */
class UrgencyScannerSessionDebounceTest {
  private companion object {
    const val UID = "user-debounce-001"
    val START = Instant.fromEpochMilliseconds(1_700_000_000_000)
  }

  private class FakeClock(var current: Instant) : Clock {
    override fun now(): Instant = current
    fun advance(by: Duration) {
      current += by
    }
  }

  private val clock = FakeClock(START)
  private lateinit var lastScanStore: LastScanStore
  private lateinit var scanner: UrgencyScanner

  @Before
  fun setUp() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    WingsLogDatabase.Schema.synchronous()
      .create(driver)
    val db: WingsLogDatabase = createWingsLogDatabase(driver)
    lastScanStore = LastScanStore(db)

    val user = mockk<FirebaseUser> { every { uid } returns UID }
    val auth = mockk<FirebaseAuth> { every { currentUser } returns user }
    // Notifications switched off, so every scan that clears the debounce stops right here.
    val prefs = mockk<NotificationPrefsManager> {
      every { observe() } returns flowOf(
        PrefsState.Resolved(
          NotificationSettings().withAllEnabled(false)
        )
      )
    }

    scanner = UrgencyScanner(
      auth = auth,
      prefsManager = prefs,
      permission = mockk(relaxed = true),
      fleetManager = mockk(relaxed = true),
      scopeResolver = mockk(relaxed = true),
      taskDueManager = mockk(relaxed = true),
      logManager = mockk(relaxed = true),
      entityStoreFactory = mockk(relaxed = true),
      watermarkStore = UrgencyWatermarkStore(db),
      notifier = mockk(relaxed = true),
      templateRegistry = BakedInTemplateRegistry(appVersionCode = 1),
      lastScanStore = lastScanStore,
      clock = clock,
    )
  }

  @Test
  fun sessionScan_neverScannedBefore_isNotDebounced() = runTest {
    assertThat(scanner.scan(ScanTrigger.SESSION_BOUNDARY)).isEqualTo(ScanResult.Disabled)
  }

  @Test
  fun sessionScan_insideTheWindow_isDebounced() = runTest {
    lastScanStore.record(UID, scanAt(START))
    clock.advance(119.minutes)

    assertThat(scanner.scan(ScanTrigger.SESSION_BOUNDARY)).isEqualTo(ScanResult.Debounced)
  }

  @Test
  fun sessionScan_atTheWindowBoundary_runs() = runTest {
    lastScanStore.record(UID, scanAt(START))
    clock.advance(2.hours)

    assertThat(scanner.scan(ScanTrigger.SESSION_BOUNDARY)).isEqualTo(ScanResult.Disabled)
  }

  /** Design §6.6: "The scheduled background scan ignores the debounce." */
  @Test
  fun scheduledScan_insideTheWindow_isNotDebounced() = runTest {
    lastScanStore.record(UID, scanAt(START))
    clock.advance(1.minutes)

    assertThat(scanner.scan(ScanTrigger.SCHEDULED)).isEqualTo(ScanResult.Disabled)
  }

  /** Someone tapping "scan now" means it, whatever the watermark says. */
  @Test
  fun manualScan_insideTheWindow_isNotDebounced() = runTest {
    lastScanStore.record(UID, scanAt(START))
    clock.advance(1.minutes)

    assertThat(scanner.scan(ScanTrigger.MANUAL)).isEqualTo(ScanResult.Disabled)
  }

  /** No uid means no key to debounce on, so the signed-out exit must come first. */
  @Test
  fun sessionScan_signedOut_returnsNoUserRatherThanDebounced() = runTest {
    val signedOut = mockk<FirebaseAuth> { every { currentUser } returns null }
    val other = UrgencyScanner(
      auth = signedOut,
      prefsManager = mockk(relaxed = true),
      permission = mockk(relaxed = true),
      fleetManager = mockk(relaxed = true),
      scopeResolver = mockk(relaxed = true),
      taskDueManager = mockk(relaxed = true),
      logManager = mockk(relaxed = true),
      entityStoreFactory = mockk(relaxed = true),
      watermarkStore = mockk(relaxed = true),
      notifier = mockk(relaxed = true),
      templateRegistry = BakedInTemplateRegistry(appVersionCode = 1),
      lastScanStore = lastScanStore,
      clock = clock,
    )

    assertThat(other.scan(ScanTrigger.SESSION_BOUNDARY)).isEqualTo(ScanResult.NoUser)
  }

  /** Only `at` matters to the debounce; the counts are diagnostics. */
  private fun scanAt(at: Instant) = ScanRecord(
    at = at,
    trigger = ScanTrigger.SCHEDULED,
    recordsExamined = 0,
    crossingsFound = 0,
    crossingsSuppressed = 0,
    notificationsPosted = 0,
  )

  /**
   * A scan that exits early must not overwrite the diagnostics of the last scan that did work —
   * otherwise Developer Options reports zeroes for a scan that never looked at anything.
   */
  @Test
  fun anEarlyExit_doesNotOverwriteTheLastRecord() = runTest {
    val real = ScanRecord(
      at = START,
      trigger = ScanTrigger.SCHEDULED,
      recordsExamined = 12,
      crossingsFound = 3,
      crossingsSuppressed = 1,
      notificationsPosted = 2,
    )
    lastScanStore.record(UID, real)
    clock.advance(3.hours)

    // Notifications are off in this fixture, so this exits at Disabled.
    assertThat(scanner.scan(ScanTrigger.MANUAL)).isEqualTo(ScanResult.Disabled)
    assertThat(lastScanStore.lastScan(UID)).isEqualTo(real)
  }
}
