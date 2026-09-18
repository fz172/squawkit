package dev.fanfly.wingslog.core.file

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class ZipFileWriterTest {
  @Test
  fun write_createsReadableZipWithUtf8Names() = runTest {
    val bytes = ZipFileWriter().write(
      listOf(
        ZipEntryPayload(
          "N12345/airframe.csv",
          "date,work\r\n".encodeToByteArray()
        ),
        ZipEntryPayload("N12345/cafe.csv", "ok".encodeToByteArray()),
      )
    )

    val entries = readZip(bytes)

    assertThat(entries).containsExactly(
      "N12345/airframe.csv",
      "date,work\r\n",
      "N12345/cafe.csv",
      "ok",
    )
      .inOrder()
  }

  @Test
  fun write_canCreateEmptyZip() = runTest {
    val bytes = ZipFileWriter().write(emptyList())

    assertThat(readZip(bytes)).isEmpty()
  }

  // The three below exercise the common-code archive that iOS and the web use. They run on the JVM,
  // but the only platform-specific part of that path is DeflateCodec, which each platform round
  // trips in its own core/file test.
  @Test
  fun commonArchive_deflatesCompressibleEntriesAndStillReadsBack() = runTest {
    val csv = compressibleCsv()

    val bytes = CommonZipArchive.build(
      listOf(
        ZipEntryPayload(
          "N12345/airframe.csv",
          csv
        )
      )
    )

    assertThat(bytes.size * 5).isLessThan(csv.size)
    assertThat(methodsOf(bytes)).containsExactly(ZipEntry.DEFLATED)
    assertThat(readZip(bytes)).containsExactly(
      "N12345/airframe.csv",
      csv.decodeToString(),
    )
      .inOrder()
  }

  @Test
  fun commonArchive_storesEntriesDeflateWouldNotShrink() = runTest {
    // Two bytes of payload: any deflate stream of it is longer than the payload itself.
    val bytes = CommonZipArchive.build(
      listOf(
        ZipEntryPayload(
          "N12345/cafe.csv",
          "ok".encodeToByteArray()
        )
      )
    )

    assertThat(methodsOf(bytes)).containsExactly(ZipEntry.STORED)
    assertThat(readZip(bytes)).containsExactly("N12345/cafe.csv", "ok")
      .inOrder()
  }

  @Test
  fun commonArchive_mixesMethodsWithinOneArchive() = runTest {
    val csv = compressibleCsv()

    val bytes = CommonZipArchive.build(
      listOf(
        ZipEntryPayload("N12345/cafe.csv", "ok".encodeToByteArray()),
        ZipEntryPayload("N12345/airframe.csv", csv),
        ZipEntryPayload("N12345/empty.csv", ByteArray(0)),
      )
    )

    assertThat(methodsOf(bytes))
      .containsExactly(ZipEntry.STORED, ZipEntry.DEFLATED, ZipEntry.STORED)
      .inOrder()
    assertThat(readZip(bytes)).containsExactly(
      "N12345/cafe.csv", "ok",
      "N12345/airframe.csv", csv.decodeToString(),
      "N12345/empty.csv", "",
    )
      .inOrder()
  }

  private fun compressibleCsv(): ByteArray = buildString {
    append("date,work,technician\r\n")
    repeat(500) { append("2026-05-19,Oil change and filter,Sam Whitfield\r\n") }
  }.encodeToByteArray()

  /**
   * Reads via [ZipFile], so the methods come from the central directory — the copy a real unzipper
   * reads, and the one the local headers have to agree with. [readZip] checks the local headers.
   */
  private fun methodsOf(bytes: ByteArray): List<Int> {
    val file = File.createTempFile("archive", ".zip")
      .apply { deleteOnExit() }
    file.writeBytes(bytes)
    return ZipFile(file).use { zip ->
      zip.entries()
        .toList()
        .map { it.method }
    }
  }

  private fun readZip(bytes: ByteArray): List<String> {
    val out = mutableListOf<String>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        out += entry.name
        out += zip.readBytes()
          .decodeToString()
      }
    }
    return out
  }
}
