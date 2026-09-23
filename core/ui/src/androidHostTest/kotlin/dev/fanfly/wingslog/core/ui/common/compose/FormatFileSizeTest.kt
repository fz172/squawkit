package dev.fanfly.wingslog.core.ui.common.compose

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wingslog.core.sharedassets.generated.resources.Res
import wingslog.core.sharedassets.generated.resources.file_size_bytes
import wingslog.core.sharedassets.generated.resources.file_size_kb
import wingslog.core.sharedassets.generated.resources.file_size_mb
import wingslog.core.sharedassets.generated.resources.file_size_zero_kb

class FormatFileSizeTest {

  @Test
  fun zeroOrNegative_usesZeroKb() {
    assertThat(fileSizeParts(0L))
      .isEqualTo(FileSizeParts(Res.string.file_size_zero_kb, null))
    assertThat(fileSizeParts(-5L))
      .isEqualTo(FileSizeParts(Res.string.file_size_zero_kb, null))
  }

  @Test
  fun belowOneKb_showsBytes() {
    assertThat(fileSizeParts(1L)).isEqualTo(FileSizeParts(Res.string.file_size_bytes, "1"))
    assertThat(fileSizeParts(999L)).isEqualTo(FileSizeParts(Res.string.file_size_bytes, "999"))
  }

  @Test
  fun kbRange_roundsUp() {
    assertThat(fileSizeParts(1_000L)).isEqualTo(FileSizeParts(Res.string.file_size_kb, "1"))
    assertThat(fileSizeParts(1_001L)).isEqualTo(FileSizeParts(Res.string.file_size_kb, "2"))
    assertThat(fileSizeParts(999_999L)).isEqualTo(FileSizeParts(Res.string.file_size_kb, "1000"))
  }

  @Test
  fun mbRange_oneDecimalPlace() {
    assertThat(fileSizeParts(1_000_000L)).isEqualTo(FileSizeParts(Res.string.file_size_mb, "1.0"))
    assertThat(fileSizeParts(25_960_000L)).isEqualTo(FileSizeParts(Res.string.file_size_mb, "26.0"))
    assertThat(fileSizeParts(26_214_400L)).isEqualTo(FileSizeParts(Res.string.file_size_mb, "26.2"))
  }
}
