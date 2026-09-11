package dev.fanfly.wingslog.feature.subscription.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The client half of the promo-code format (#750). Its whole job is to agree with `promoCodes.ts`,
 * so these cases are deliberately the same ones the server suite asserts.
 */
class PromoCodeTest {

  @Test
  fun `accepts what a human types — lowercase, spaces and the displayed grouping`() {
    assertThat(normalizePromoCode("PRQK-8H3M-XTVB")).isEqualTo("PRQK8H3MXTVB")
    assertThat(normalizePromoCode(" prqk 8h3m-xtvb ")).isEqualTo("PRQK8H3MXTVB")
  }

  @Test
  fun `rejects rather than filters a character outside the alphabet`() {
    // The bug this rule exists for: dropping unknown characters turns junk into a well-formed code.
    assertThat(normalizePromoCode("PRQK-8H3M-XTV0")).isNull()
    assertThat(normalizePromoCode("https://squawkit.fanfly.dev/promo")).isNull()
  }

  @Test
  fun `rejects anything that is not exactly the code length`() {
    assertThat(normalizePromoCode("PRQK8H3MXTV")).isNull()
    assertThat(normalizePromoCode("PRQK8H3MXTVBB")).isNull()
    assertThat(normalizePromoCode("")).isNull()
  }

  @Test
  fun `formats in three groups, and only when the code is whole`() {
    assertThat(formatPromoCode("PRQK8H3MXTVB")).isEqualTo("PRQK-8H3M-XTVB")
    // A partial code is left alone — the field's visual transformation groups as you type.
    assertThat(formatPromoCode("PRQK8H3M")).isEqualTo("PRQK8H3M")
  }

  @Test
  fun `formatting round-trips back through normalization`() {
    val code = "AAAABBBBCCCC"
    assertThat(normalizePromoCode(formatPromoCode(code))).isEqualTo(code)
  }
}
