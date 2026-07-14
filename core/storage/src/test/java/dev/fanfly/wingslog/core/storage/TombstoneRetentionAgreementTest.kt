package dev.fanfly.wingslog.core.storage

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.time.ExperimentalTime
import org.junit.Test

/**
 * The client and the server must forget a tombstone at the same age.
 *
 * They purge the same delete from two ends: [TombstoneGc] here, the scheduled sweep's
 * `TOMBSTONE_RETENTION_DAYS` in `backend/firebase/functions/.env`. Let them drift and a delete comes
 * back from the dead — a device that dropped its tombstone early re-uploads a record the server
 * still considers deleted, or keeps re-applying one the server has already forgotten. It is a
 * correctness constraint, so it is a test and not a comment on two files nobody reads together.
 *
 * See docs/storage/deletion_gc_design.html §5.3.
 */
@OptIn(ExperimentalTime::class)
class TombstoneRetentionAgreementTest {

  @Test
  fun clientRetentionMatchesTheServerSweep() {
    val serverDays = requireNotNull(functionsEnv()["TOMBSTONE_RETENTION_DAYS"]?.toLongOrNull()) {
      "TOMBSTONE_RETENTION_DAYS is missing from backend/firebase/functions/.env — the sweep " +
        "requires it and refuses to deploy without it."
    }

    assertThat(TombstoneGc.RETENTION.inWholeDays).isEqualTo(serverDays)
  }

  private fun functionsEnv(): Map<String, String> {
    val env = generateSequence(File(".").absoluteFile) { it.parentFile }
      .map { File(it, "backend/firebase/functions/.env") }
      .firstOrNull { it.isFile }
    requireNotNull(env) { "could not find backend/firebase/functions/.env above ${File(".").absolutePath}" }

    return env.readLines()
      .asSequence()
      .map { it.trim() }
      .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
      .map { it.substringBefore('=') to it.substringAfter('=') }
      .toMap()
  }
}
