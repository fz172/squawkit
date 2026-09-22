package dev.fanfly.wingslog.core.file

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import kotlin.random.Random

class GzipCodecTest {

  @Test
  fun aG3xFileRoundTripsAndShrinksAboutTenfold() = runTest {
    val raw = sampleLog()
    val packed = GzipCodec.compress(raw)
    assertThat(packed[0]).isEqualTo(0x1f.toByte())
    assertThat(packed[1]).isEqualTo(0x8b.toByte())
    assertThat(packed.size * 5).isLessThan(raw.size)
    assertThat(GzipCodec.decompress(packed)).isEqualTo(raw)
  }

  @Test
  fun emptyAndRandomInputsRoundTrip() = runTest {
    assertThat(GzipCodec.decompress(GzipCodec.compress(ByteArray(0)))).isEmpty()
    val random = Random(7).nextBytes(1_000_000)
    assertThat(GzipCodec.decompress(GzipCodec.compress(random))).isEqualTo(
      random
    )
  }

  @Test
  fun garbageIsRefusedNotMisread() = runTest {
    var threw = false
    try {
      GzipCodec.decompress("not gzip at all".encodeToByteArray())
    } catch (e: GzipException) {
      threw = true
    }
    assertThat(threw).isTrue()
    assertThat(GzipCodec.isAvailable()).isTrue()
  }

  /** A G3X CSV from docs/datalog/samples, the kind of text this codec exists for. */
  private fun sampleLog(): ByteArray {
    var dir = File(System.getProperty("user.dir"))
    while (!File(dir, "settings.gradle.kts").exists()) dir =
      requireNotNull(dir.parentFile)
    return File(
      dir,
      "docs/datalog/samples/g3x/log_20260902_144756_XX1.csv"
    ).readBytes()
  }
}
