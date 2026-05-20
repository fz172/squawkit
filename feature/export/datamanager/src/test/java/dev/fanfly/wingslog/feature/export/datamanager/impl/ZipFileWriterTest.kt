package dev.fanfly.wingslog.feature.export.datamanager.impl

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.junit.Test

class ZipFileWriterTest {
  @Test
  fun write_createsReadableZipWithUtf8Names() {
    val bytes = ZipFileWriter().write(
      listOf(
        ZipEntryPayload("N12345/airframe.csv", "date,work\r\n".encodeToByteArray()),
        ZipEntryPayload("N12345/cafe.csv", "ok".encodeToByteArray()),
      )
    )

    val entries = readZip(bytes)

    assertThat(entries).containsExactly(
      "N12345/airframe.csv",
      "date,work\r\n",
      "N12345/cafe.csv",
      "ok",
    ).inOrder()
  }

  @Test
  fun write_canCreateEmptyZip() {
    val bytes = ZipFileWriter().write(emptyList())

    assertThat(readZip(bytes)).isEmpty()
  }

  private fun readZip(bytes: ByteArray): List<String> {
    val out = mutableListOf<String>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        out += entry.name
        out += zip.readBytes().decodeToString()
      }
    }
    return out
  }
}
