package dev.fanfly.wingslog.core.file

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import kotlin.random.Random

class DeflateCodecTest {

  @Test
  fun aG3xFileRoundTripsAndShrinksAboutTenfold() = runTest {
    val raw = sampleLog()
    val packed = DeflateCodec.compress(raw)
    assertThat(packed.size * 5).isLessThan(raw.size)
    assertThat(DeflateCodec.decompress(packed)).isEqualTo(raw)
  }

  @Test
  fun emptyAndRandomInputsRoundTrip() = runTest {
    assertThat(DeflateCodec.decompress(DeflateCodec.compress(ByteArray(0)))).isEmpty()
    val random = Random(7).nextBytes(1_000_000)
    assertThat(DeflateCodec.decompress(DeflateCodec.compress(random))).isEqualTo(
      random
    )
  }

  /** Raw means raw: no gzip magic, no zlib header, because a ZIP entry frames the payload itself. */
  @Test
  fun theOutputCarriesNoFraming() = runTest {
    val packed = DeflateCodec.compress(sampleLog())

    assertThat(packed[0]).isNotEqualTo(0x1f.toByte())
    // A zlib header's first byte is CMF: low nibble 8 for deflate, and CMF*256+FLG divides by 31.
    val looksLikeZlib = (packed[0].toInt() and 0x0f) == 8 &&
      ((packed[0].toInt() and 0xff) * 256 + (packed[1].toInt() and 0xff)) % 31 == 0
    assertThat(looksLikeZlib).isFalse()
  }

  @Test
  fun garbageIsRefusedNotMisread() = runTest {
    var threw = false
    try {
      DeflateCodec.decompress(Random(11).nextBytes(64))
    } catch (e: DeflateException) {
      threw = true
    }
    assertThat(threw).isTrue()
    assertThat(DeflateCodec.isAvailable()).isTrue()
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
