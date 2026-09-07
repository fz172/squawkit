package dev.fanfly.wingslog.feature.search.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.search.datamanager.impl.text.Stemmer
import dev.fanfly.wingslog.feature.search.datamanager.impl.text.editDistance
import org.junit.Test

class StemmerAndDistanceTest {

  @Test
  fun stemmer_rules() {
    assertThat(Stemmer.stem("mags")).isEqualTo("mag")
    assertThat(Stemmer.stem("leaking")).isEqualTo("leak")
    assertThat(Stemmer.stem("replaced")).isEqualTo("replac")
    assertThat(Stemmer.stem("inspections")).isEqualTo("inspection")
    assertThat(Stemmer.stem("batteries")).isEqualTo("battery")
    assertThat(Stemmer.stem("boxes")).isEqualTo("box")
  }

  @Test
  fun stemmer_leavesTechnicalWordsAlone() {
    for (w in listOf(
      "magneto",
      "bulletin",
      "annual",
      "ads",
      "pass",
      "gas",
      "elt"
    )) {
      assertThat(Stemmer.stem(w)).isEqualTo(w)
    }
    assertThat(Stemmer.stem("replace")).isEqualTo(Stemmer.stem("replaced"))
    assertThat(Stemmer.stem("services")).isEqualTo(Stemmer.stem("serviced"))
  }

  @Test
  fun distance_transpositionIsOneEdit() {
    assertThat(editDistance("trasnponder", "transponder", 2)).isEqualTo(1)
    assertThat(editDistance("transponder", "transponder", 2)).isEqualTo(0)
    assertThat(editDistance("magnto", "magneto", 1)).isEqualTo(1)
    assertThat(editDistance("altimeter", "alternator", 2)).isEqualTo(3)
  }

  @Test
  fun distance_bailsOutAtTheCap() {
    assertThat(editDistance("oil", "transponder", 2)).isEqualTo(3)
    assertThat(editDistance("abcdefgh", "abxdxfxh", 2)).isEqualTo(3)
  }
}
