package dev.fanfly.wingslog.core.ui.common.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HighlightRangesTest {

  private fun marked(text: String, words: Set<String>): String {
    val ranges = highlightRanges(text, words)
    val sb = StringBuilder()
    var cursor = 0
    for (r in ranges) {
      sb.append(text, cursor, r.first).append('[').append(text, r.first, r.last + 1).append(']')
      cursor = r.last + 1
    }
    return sb.append(text.substring(cursor)).toString()
  }

  @Test
  fun wholeWordsOnly_caseInsensitive() {
    assertThat(marked("Transponder check; checked the transponders.", setOf("transponder", "check")))
      .isEqualTo("[Transponder] [check]; checked the transponders.")
  }

  @Test
  fun referencesWholeOrByPart() {
    assertThat(marked("Tested per 91.413 and 91.217.", setOf("91.413"))).isEqualTo("Tested per [91.413] and 91.217.")
    assertThat(marked("Tested per 91.413 and 91.217.", setOf("413"))).isEqualTo("Tested per 91.[413] and 91.217.")
    assertThat(marked("AD 2011-10-09 complied", setOf("2011-10-09"))).isEqualTo("AD [2011-10-09] complied")
  }

  @Test
  fun phrasesAndNothing() {
    assertThat(marked("Replaced emergency locator transmitter battery", setOf("emergency locator transmitter")))
      .isEqualTo("Replaced [emergency locator transmitter] battery")
    assertThat(marked("Oil change", setOf("magneto"))).isEqualTo("Oil change")
    assertThat(marked("Oil change", emptySet())).isEqualTo("Oil change")
  }
}
