package dev.fanfly.wingslog.feature.attachment.datamanager

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * JVM (Android unit-test) companion to the commonTest [Sha256HexTest].
 * Uses Truth for richer failure messages on the Android actual.
 * The commonTest copy runs the same vectors on every KMP target including iOS.
 */
class Sha256HexAndroidTest {

  @Test
  fun sha256Hex_emptyInput_returnsKnownDigest() {
    assertThat(sha256Hex(ByteArray(0)))
      .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
  }

  @Test
  fun sha256Hex_abc_returnsKnownDigest() {
    assertThat(sha256Hex("abc".toByteArray()))
      .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
  }

  @Test
  fun sha256Hex_helloWorld_returnsKnownDigest() {
    assertThat(sha256Hex("hello world".toByteArray()))
      .isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9")
  }

  @Test
  fun sha256Hex_1024ByteRepeatingPattern_returnsKnownDigest() {
    val payload = ByteArray(1024) { (it % 256).toByte() }
    assertThat(sha256Hex(payload))
      .isEqualTo("785b0751fc2c53dc14a4ce3d800e69ef9ce1009eb327ccf458afe09c242c26c9")
  }

  @Test
  fun sha256Hex_output_is64LowercaseHexChars() {
    val result = sha256Hex("shape check".toByteArray())
    assertThat(result).hasLength(64)
    assertThat(result).matches("[0-9a-f]{64}")
  }

  @Test
  fun sha256Hex_emptyInput_output_is64LowercaseHexChars() {
    val result = sha256Hex(ByteArray(0))
    assertThat(result).hasLength(64)
    assertThat(result).matches("[0-9a-f]{64}")
  }
}
