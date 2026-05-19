package dev.fanfly.wingslog.feature.export.datamanager.impl

import kotlin.experimental.and

@OptIn(ExperimentalUnsignedTypes::class)
internal actual class ZipFileWriter {
  actual fun write(entries: List<ZipEntryPayload>): ByteArray = StoredZipArchive.build(entries)
}

@OptIn(ExperimentalUnsignedTypes::class)
private object StoredZipArchive {
  private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50
  private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50
  private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50
  private const val VERSION_NEEDED_TO_EXTRACT = 20
  private const val GENERAL_PURPOSE_UTF8_FLAG = 1 shl 11
  private const val STORE_METHOD = 0

  fun build(entries: List<ZipEntryPayload>): ByteArray {
    val out = LittleEndianByteSink()
    val centralDirectoryEntries = mutableListOf<CentralDirectoryEntry>()

    entries.forEach { entry ->
      val nameBytes = entry.path.encodeToByteArray()
      val crc = Crc32.compute(entry.bytes)
      val localHeaderOffset = out.size
      out.int(LOCAL_FILE_HEADER_SIGNATURE)
      out.short(VERSION_NEEDED_TO_EXTRACT)
      out.short(GENERAL_PURPOSE_UTF8_FLAG)
      out.short(STORE_METHOD)
      out.short(0)
      out.short(0)
      out.int(crc.toInt())
      out.int(entry.bytes.size)
      out.int(entry.bytes.size)
      out.short(nameBytes.size)
      out.short(0)
      out.bytes(nameBytes)
      out.bytes(entry.bytes)

      centralDirectoryEntries += CentralDirectoryEntry(
        pathBytes = nameBytes,
        crc = crc,
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
      out.short(STORE_METHOD)
      out.short(0)
      out.short(0)
      out.int(entry.crc.toInt())
      out.int(entry.size)
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

  private data class CentralDirectoryEntry(
    val pathBytes: ByteArray,
    val crc: UInt,
    val size: Int,
    val localHeaderOffset: Int,
  )
}

private class LittleEndianByteSink {
  private val bytes = mutableListOf<Byte>()

  val size: Int get() = bytes.size

  fun short(value: Int) {
    bytes += (value and 0xff).toByte()
    bytes += ((value ushr 8) and 0xff).toByte()
  }

  fun int(value: Int) {
    bytes += (value and 0xff).toByte()
    bytes += ((value ushr 8) and 0xff).toByte()
    bytes += ((value ushr 16) and 0xff).toByte()
    bytes += ((value ushr 24) and 0xff).toByte()
  }

  fun bytes(value: ByteArray) {
    value.forEach { bytes += it }
  }

  fun toByteArray(): ByteArray = bytes.toByteArray()
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
      val index = ((crc xor (byte and 0xff.toByte()).toUInt()) and 0xffu).toInt()
      crc = table[index] xor (crc shr 8)
    }
    return crc xor 0xffffffffu
  }
}
