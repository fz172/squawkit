package dev.fanfly.wingslog.feature.search.datamanager

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TokenizerTest {

  private fun tokens(s: String) = Tokenizer.tokens(Tokenizer.normalize(s))

  @Test
  fun lowercasesAndFoldsAccents() {
    assertThat(Tokenizer.normalize("Réglage Été")).isEqualTo("reglage ete")
    assertThat(tokens("Installed GTX 335")).containsExactly("installed", "gtx", "335").inOrder()
  }

  @Test
  fun keepsReferencesWholeAndSplitsThem() {
    assertThat(tokens("per 91.413")).containsExactly("per", "91.413", "91", "413").inOrder()
    assertThat(tokens("AD 2011-10-09")).containsExactly("ad", "2011-10-09", "2011", "10", "09").inOrder()
    assertThat(tokens("u/s")).containsExactly("u/s", "u", "s").inOrder()
    assertThat(tokens("serial 3AB012345.")).containsExactly("serial", "3ab012345").inOrder()
  }

  @Test
  fun numericAndAlphabeticClasses() {
    assertThat(Tokenizer.isNumeric("3ab012345")).isTrue()
    assertThat(Tokenizer.isNumeric("transponder")).isFalse()
    assertThat(Tokenizer.isAlphabetic("transponder")).isTrue()
    assertThat(Tokenizer.isAlphabetic("u/s")).isFalse()
  }
}
