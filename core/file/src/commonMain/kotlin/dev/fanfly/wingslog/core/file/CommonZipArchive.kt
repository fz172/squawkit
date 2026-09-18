package dev.fanfly.wingslog.core.file

import kotlin.experimental.and

/**
 * Pure-Kotlin ZIP archive builder, shared by the platforms with no `java.util.zip` — currently iOS
 * and the web (JS) target. Android keeps its own `java.util.zip`-backed [ZipFileWriter].
 *
 * Entries are deflated through [DeflateCodec] where the platform has it and stored uncompressed
 * where it does not, or where deflating an entry would not make it smaller. The method is recorded
 * per entry, so a single archive may mix the two.
 */
@OptIn(ExperimentalUnsignedTypes::class)
internal object CommonZipArchive {
  private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50
  private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50
  private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50
  private const val VERSION_NEEDED_TO_EXTRACT = 20
  private const val GENERAL_PURPOSE_UTF8_FLAG = 1 shl 11
  private const val STORE_METHOD = 0
  private const val DEFLATE_METHOD = 8

  suspend fun build(entries: List<ZipEntryPayload>): ByteArray {
    val out = LittleEndianByteSink()
    val centralDirectoryEntries = mutableListOf<CentralDirectoryEntry>()

    entries.forEach { entry ->
      val nameBytes = entry.path.encodeToByteArray()
      // The CRC covers the original bytes whichever method the entry ends up using.
      val crc = Crc32.compute(entry.bytes)
      val packed = pack(entry.bytes)
      val localHeaderOffset = out.size
      out.int(LOCAL_FILE_HEADER_SIGNATURE)
      out.short(VERSION_NEEDED_TO_EXTRACT)
      out.short(GENERAL_PURPOSE_UTF8_FLAG)
      out.short(packed.method)
      out.short(0)
      out.short(0)
      out.int(crc.toInt())
      out.int(packed.bytes.size)
      out.int(entry.bytes.size)
      out.short(nameBytes.size)
      out.short(0)
      out.bytes(nameBytes)
      out.bytes(packed.bytes)

      centralDirectoryEntries += CentralDirectoryEntry(
        pathBytes = nameBytes,
        crc = crc,
        method = packed.method,
        compressedSize = packed.bytes.size,
        size = entry.bytes.size,
        localHeaderOffset = localHeaderOffset,
      )
    }

    val centralDirectoryOffset = out.size
    centralDirectoryEntries.forEach { entry ->
      out.int(CENTRAL_DIRECTORY_SIGNATURE)
      out.short(VERSION_NEEDED_TO_EXTRACT)
      out.short(VERSION_NEEDED_TO_EXTRACT)
      out.short(GENERAL_PURPOSE_UTF8_FLAG)
      out.short(entry.method)
      out.short(0)
      out.short(0)
      out.int(entry.crc.toInt())
      out.int(entry.compressedSize)
      out.int(entry.size)
      out.short(entry.pathBytes.size)
      out.short(0)
      out.short(0)
      out.short(0)
      out.short(0)
      out.int(0)
      out.int(entry.localHeaderOffset)
      out.bytes(entry.pathBytes)
    }
    val centralDirectorySize = out.size - centralDirectoryOffset

    out.int(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
    out.short(0)
    out.short(0)
    out.short(centralDirectoryEntries.size)
    out.short(centralDirectoryEntries.size)
    out.int(centralDirectorySize)
    out.int(centralDirectoryOffset)
    out.short(0)
    return out.toByteArray()
  }

  /**
   * Deflates [bytes], falling back to STORE when the codec is missing, fails, or gives back
   * something no smaller than the input — which is what already-compressed attachments do.
   */
  private suspend fun pack(bytes: ByteArray): PackedEntry {
    if (bytes.isEmpty() || !DeflateCodec.isAvailable()) return PackedEntry(
      STORE_METHOD,
      bytes
    )
    val deflated = try {
      DeflateCodec.compress(bytes)
    } catch (e: DeflateException) {
      return PackedEntry(STORE_METHOD, bytes)
    }
    return if (deflated.size < bytes.size) {
      PackedEntry(DEFLATE_METHOD, deflated)
    } else {
      PackedEntry(STORE_METHOD, bytes)
    }
  }

  private class PackedEntry(val method: Int, val bytes: ByteArray)

  private class CentralDirectoryEntry(
    val pathBytes: ByteArray,
    val crc: UInt,
    val method: Int,
    val compressedSize: Int,
    val size: Int,
    val localHeaderOffset: Int,
  )
}

/** Grows by doubling; an export archive with attachments in it is megabytes of bytes. */
private class LittleEndianByteSink {
  private var bytes = ByteArray(INITIAL_CAPACITY)

  var size: Int = 0
    private set

  fun short(value: Int) {
    reserve(2)
    bytes[size++] = (value and 0xff).toByte()
    bytes[size++] = ((value ushr 8) and 0xff).toByte()
  }

  fun int(value: Int) {
    reserve(4)
    bytes[size++] = (value and 0xff).toByte()
    bytes[size++] = ((value ushr 8) and 0xff).toByte()
    bytes[size++] = ((value ushr 16) and 0xff).toByte()
    bytes[size++] = ((value ushr 24) and 0xff).toByte()
  }

  fun bytes(value: ByteArray) {
    reserve(value.size)
    value.copyInto(bytes, size)
    size += value.size
  }

  fun toByteArray(): ByteArray = bytes.copyOf(size)

  private fun reserve(count: Int) {
    if (size + count <= bytes.size) return
    var capacity = bytes.size
    while (capacity < size + count) capacity *= 2
    bytes = bytes.copyOf(capacity)
  }

  private companion object {
    const val INITIAL_CAPACITY = 4096
  }
}

@OptIn(ExperimentalUnsignedTypes::class)
private object Crc32 {
  private val table: UIntArray = UIntArray(256) { index ->
    var crc = index.toUInt()
    repeat(8) {
      crc = if ((crc and 1u) != 0u) {
        0xedb88320u xor (crc shr 1)
      } else {
        crc shr 1
      }
    }
    crc
  }

  fun compute(bytes: ByteArray): UInt {
    var crc = 0xffffffffu
    bytes.forEach { byte ->
      val index =
        ((crc xor (byte and 0xff.toByte()).toUInt()) and 0xffu).toInt()
      crc = table[index] xor (crc shr 8)
    }
    return crc xor 0xffffffffu
  }
}
