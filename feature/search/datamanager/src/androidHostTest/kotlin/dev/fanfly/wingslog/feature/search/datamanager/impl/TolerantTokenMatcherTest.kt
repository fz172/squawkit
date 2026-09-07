package dev.fanfly.wingslog.feature.search.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.search.datamanager.AviationSynonyms
import dev.fanfly.wingslog.feature.search.datamanager.FieldText
import dev.fanfly.wingslog.feature.search.datamanager.GenericSynonyms
import dev.fanfly.wingslog.feature.search.model.MatchExplanation
import org.junit.Test

class TolerantTokenMatcherTest {

  private val matcher = TolerantTokenMatcher(GenericSynonyms + AviationSynonyms)
  private val field = FieldText("Removed KT-76A transponder, installed GTX 335. Tested per 91.413. Left magneto gasket replaced.")

  private fun grade(token: String) = matcher.match(token, field)?.grade
  private fun why(token: String) = matcher.match(token, field)?.explanation

  @Test
  fun gradesInOrder() {
    assertThat(grade("transponder")).isEqualTo(1.0)
    assertThat(grade("replace")).isEqualTo(0.95)
    assertThat(grade("trans")).isEqualTo(0.8)
    assertThat(grade("xpdr")).isEqualTo(0.7)
    assertThat(grade("trasnponder")).isEqualTo(0.5)
    assertThat(grade("propeller")).isNull()
  }

  @Test
  fun explanationsNameTheMatchedWord() {
    assertThat(why("xpdr")).isEqualTo(MatchExplanation.Synonym("xpdr", "transponder"))
    assertThat(why("trasnponder")).isEqualTo(MatchExplanation.Fuzzy("trasnponder", "transponder"))
    assertThat(why("trans")).isEqualTo(MatchExplanation.Prefix("trans", "transponder"))
    assertThat(why("transponder")).isNull()
    assertThat(why("replace")).isNull()
  }

  @Test
  fun synonymsRunBothWays_andPhrases() {
    val elt = FieldText("Replaced emergency locator transmitter battery. Mags timed.")
    assertThat(matcher.match("elt", elt)?.explanation).isEqualTo(MatchExplanation.Synonym("elt", "emergency locator transmitter"))
    assertThat(matcher.match("magneto", elt)?.grade).isEqualTo(0.7)
    val xpdr = FieldText("XPDR inop")
    assertThat(matcher.match("transponder", xpdr)?.explanation).isEqualTo(MatchExplanation.Synonym("transponder", "xpdr"))
    assertThat(matcher.match("inoperative", xpdr)?.grade).isEqualTo(0.7)
  }

  @Test
  fun numbersMatchExactlyOrByPrefixOnly() {
    assertThat(grade("91.413")).isEqualTo(1.0)
    assertThat(grade("413")).isEqualTo(1.0)
    assertThat(grade("91.4")).isEqualTo(0.8)
    assertThat(grade("91.414")).isNull()
    assertThat(grade("gtx335")).isNull()
  }

  @Test
  fun anyPrefixMatchesWhileTyping() {
    val check = FieldText("Pitot static transponder check")
    assertThat(matcher.match("c", check)?.grade).isEqualTo(0.8)
    assertThat(matcher.match("ch", check)?.explanation).isEqualTo(MatchExplanation.Prefix("ch", "check"))
    assertThat(matcher.match("chk", check)).isNull()
  }

  @Test
  fun shortTokensNeverFuzz() {
    assertThat(grade("gsk")).isNull()
    assertThat(grade("gax")).isNull()
    assertThat(matcher.match("lft", FieldText("left"))).isNull()
  }
}
