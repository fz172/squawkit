package dev.fanfly.wingslog.core.file

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs on every platform, which is the point: the JVM tests beside this one only ever exercise the
 * `java.util.zip` actuals, so zlib on iOS and `CompressionStream` on the web are covered here or
 * nowhere. Read the archive back by hand — a native target has no unzipper to borrow.
 */
class ZipDeflateCommonTest {

  @Test
  fun deflateRoundTripsAndShrinksCompressibleText() = runTest {
    if (!DeflateCodec.isAvailable()) return@runTest
    val raw = compressibleCsv()

    val packed = DeflateCodec.compress(raw)

    assertTrue(packed.size * 5 < raw.size, "expected 5x, got ${raw.size} -> ${packed.size}")
    assertContentEquals(raw, DeflateCodec.decompress(packed))
  }

  /**
   * A round trip cannot tell raw deflate from gzip, because a mismatched pair still agrees with
   * itself — flipping both ends of this codec to gzip framing passes every test above. These two
   * pin the framing against something outside the codec: a vector zlib produced with `wbits = -15`,
   * which only a raw inflater accepts, and the absence of a header on the way out.
   */
  @Test
  fun deflateReadsAStreamWrittenWithNoFraming() = runTest {
    if (!DeflateCodec.isAvailable()) return@runTest
    val expected = "date,work\r\n2026-05-19,Oil change\r\n".repeat(4)
      .encodeToByteArray()

    val inflated = DeflateCodec.decompress(RAW_DEFLATE_VECTOR)

    assertContentEquals(expected, inflated)
  }

  @Test
  fun deflateWritesAStreamWithNoFraming() = runTest {
    if (!DeflateCodec.isAvailable()) return@runTest
    val packed = DeflateCodec.compress(compressibleCsv())

    assertTrue(
      !(packed[0] == GZIP_MAGIC_0 && packed[1] == GZIP_MAGIC_1),
      "output is gzip framed",
    )
    // A zlib header's first byte is CMF — low nibble 8 for deflate — and CMF*256+FLG divides by 31.
    val cmf = packed[0].toInt() and 0xff
    val flg = packed[1].toInt() and 0xff
    assertTrue(!(cmf and 0x0f == 8 && (cmf * 256 + flg) % 31 == 0), "output is zlib framed")
  }

  @Test
  fun deflateRoundTripsEmptyAndIncompressibleInput() = runTest {
    if (!DeflateCodec.isAvailable()) return@runTest
    assertEquals(0, DeflateCodec.decompress(DeflateCodec.compress(ByteArray(0))).size)
    val random = Random(7).nextBytes(256 * 1024)
    assertContentEquals(random, DeflateCodec.decompress(DeflateCodec.compress(random)))
  }

  @Test
  fun theArchiveDeflatesWhatItCanAndStoresTheRest() = runTest {
    val csv = compressibleCsv()

    val archive = CommonZipArchive.build(
      listOf(
        ZipEntryPayload("N12345/airframe.csv", csv),
        ZipEntryPayload("N12345/cafe.csv", "ok".encodeToByteArray()),
      )
    )

    val entries = readLocalHeaders(archive)
    assertEquals(listOf("N12345/airframe.csv", "N12345/cafe.csv"), entries.map { it.path })
    // Two bytes of payload: no deflate stream of it is shorter, so it has to stay stored.
    assertEquals(STORE_METHOD, entries[1].method)
    assertContentEquals("ok".encodeToByteArray(), entries[1].payload)

    val csvEntry = entries[0]
    if (DeflateCodec.isAvailable()) {
      assertEquals(DEFLATE_METHOD, csvEntry.method)
      assertTrue(csvEntry.payload.size < csv.size)
      assertContentEquals(csv, DeflateCodec.decompress(csvEntry.payload))
    } else {
      assertEquals(STORE_METHOD, csvEntry.method)
      assertContentEquals(csv, csvEntry.payload)
    }
    // Whichever method won, the CRC is over the original bytes and the sizes describe both forms.
    assertEquals(csv.size, csvEntry.uncompressedSize)
    assertEquals(csvEntry.payload.size, csvEntry.compressedSize)
  }

  private fun compressibleCsv(): ByteArray = buildString {
    append("date,work,technician\r\n")
    repeat(500) { append("2026-05-19,Oil change and filter,Sam Whitfield\r\n") }
  }.encodeToByteArray()

  private class LocalEntry(
    val path: String,
    val method: Int,
    val compressedSize: Int,
    val uncompressedSize: Int,
    val payload: ByteArray,
  )

  /** Walks the local file headers until the signature stops matching — the central directory. */
  private fun readLocalHeaders(archive: ByteArray): List<LocalEntry> {
    val entries = mutableListOf<LocalEntry>()
    var offset = 0
    while (int(archive, offset) == LOCAL_FILE_HEADER_SIGNATURE) {
      val method = short(archive, offset + 8)
      val compressedSize = int(archive, offset + 18)
      val uncompressedSize = int(archive, offset + 22)
      val nameLength = short(archive, offset + 26)
      val extraLength = short(archive, offset + 28)
      val nameStart = offset + 30
      val dataStart = nameStart + nameLength + extraLength
      entries += LocalEntry(
        path = archive.decodeToString(nameStart, nameStart + nameLength),
        method = method,
        compressedSize = compressedSize,
        uncompressedSize = uncompressedSize,
        payload = archive.copyOfRange(dataStart, dataStart + compressedSize),
      )
      offset = dataStart + compressedSize
    }
    return entries
  }

  private fun short(bytes: ByteArray, at: Int): Int =
    (bytes[at].toInt() and 0xff) or ((bytes[at + 1].toInt() and 0xff) shl 8)

  private fun int(bytes: ByteArray, at: Int): Int =
    short(bytes, at) or (short(bytes, at + 2) shl 16)

  private companion object {
    const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50
    const val STORE_METHOD = 0
    const val DEFLATE_METHOD = 8
    const val GZIP_MAGIC_0 = 0x1f.toByte()
    const val GZIP_MAGIC_1 = 0x8b.toByte()

    /** `zlib.compressobj(9, DEFLATED, -15)` over the text [deflateReadsAStreamWrittenWithNoFraming] expects. */
    val RAW_DEFLATE_VECTOR = byteArrayOf(
      75, 73, 44, 73, -43, 41, -49, 47, -54, -26, -27, 50, 50, 48, 50, -45, 53, 48, -43, 53,
      -76, -44, -15, -49, -52, 81, 72, -50, 72, -52, 75, 79, -27, -27, 74, -95, -117, 10, 0,
    )
  }
}
