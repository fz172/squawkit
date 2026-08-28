package dev.fanfly.wingslog.feature.export.datamanager.impl

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CsvWriterTest {
  @Test
  fun write_usesCrLfLineEndings() {
    val csv = CsvWriter.write(
      listOf(
        listOf("A", "B"),
        listOf("1", "2"),
      )
    )

    assertThat(csv).isEqualTo("A,B\r\n1,2\r\n")
  }

  @Test
  fun write_quotesCommasQuotesAndNewlines() {
    val csv = CsvWriter.write(
      listOf(
        listOf("plain", "comma,value", "quote \"value\"", "line\nbreak"),
      )
    )

    assertThat(csv).isEqualTo("plain,\"comma,value\",\"quote \"\"value\"\"\",\"line\nbreak\"\r\n")
  }
}
