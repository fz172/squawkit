package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.core.storage.blob.sha256Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the output of [sha256Hex] against authoritative NIST/RFC 6234 test vectors.
 * Runs on every KMP target (Android JVM, iosSimulatorArm64, …) so any divergence
 * between the java.security and CommonCrypto actuals trips a clear failure.
 */
class Sha256HexTest {

  // ---- known-vector pin tests ----

  @Test
  fun sha256Hex_emptyInput_returnsKnownDigest() {
    assertEquals(
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      sha256Hex(ByteArray(0)),
    )
  }

  @Test
  fun sha256Hex_abc_returnsKnownDigest() {
    assertEquals(
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
      sha256Hex("abc".encodeToByteArray()),
    )
  }

  @Test
  fun sha256Hex_helloWorld_returnsKnownDigest() {
    assertEquals(
      "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
      sha256Hex("hello world".encodeToByteArray()),
    )
  }

  @Test
  fun sha256Hex_1024ByteRepeatingPattern_returnsKnownDigest() {
    // 1024 bytes: byte[i] = (i % 256).toByte()
    val payload = ByteArray(1024) { (it % 256).toByte() }
    assertEquals(
      "785b0751fc2c53dc14a4ce3d800e69ef9ce1009eb327ccf458afe09c242c26c9",
      sha256Hex(payload),
    )
  }

  // ---- output-shape assertions ----

  @Test
  fun sha256Hex_output_is64LowercaseHexChars() {
    val result = sha256Hex("shape check".encodeToByteArray())
    assertEquals(64, result.length, "digest must be exactly 64 hex chars")
    assertTrue(
      result.all { it in '0'..'9' || it in 'a'..'f' },
      "digest must be lowercase hex, got: $result"
    )
  }

  @Test
  fun sha256Hex_emptyInput_output_is64LowercaseHexChars() {
    val result = sha256Hex(ByteArray(0))
    assertEquals(
      64,
      result.length,
      "empty-input digest must be exactly 64 hex chars"
    )
    assertTrue(
      result.all { it in '0'..'9' || it in 'a'..'f' },
      "empty-input digest must be lowercase hex"
    )
  }
}
