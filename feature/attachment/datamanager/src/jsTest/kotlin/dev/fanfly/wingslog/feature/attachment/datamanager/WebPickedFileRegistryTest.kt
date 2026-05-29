package dev.fanfly.wingslog.feature.attachment.datamanager

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class WebPickedFileRegistryTest {
  @Test
  fun webFileByteReader_consumesRegisteredBytes() {
    val bytes = byteArrayOf(1, 2, 3, 4)
    val uri = WebPickedFileRegistry.put(bytes)
    val reader = WebFileByteReader()

    assertContentEquals(bytes, reader.readBytes(uri))
    assertNull(reader.readBytes(uri))
  }

  @Test
  fun clear_removesRegisteredBytes() {
    val uri = WebPickedFileRegistry.put(byteArrayOf(9, 8, 7))

    WebPickedFileRegistry.clear(uri)

    assertNull(WebFileByteReader().readBytes(uri))
  }
}
