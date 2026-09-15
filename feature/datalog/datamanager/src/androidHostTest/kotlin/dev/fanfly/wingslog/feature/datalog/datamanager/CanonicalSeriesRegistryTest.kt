package dev.fanfly.wingslog.feature.datalog.datamanager

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CanonicalSeriesRegistryTest {

  private fun id(short: String) = CanonicalSeriesRegistry.canonicalIdFor(short)

  @Test
  fun theDesignTableRoundTrips() {
    val table = mapOf(
      "E1 RPM" to "engine[1].rpm",
      "E1 MAP" to "engine[1].map",
      "E1 OilP" to "engine[1].oil_press",
      "E1 OilT" to "engine[1].oil_temp",
      "E1 CHT3" to "engine[1].cht[3]",
      "E1 EGT4" to "engine[1].egt[4]",
      "E2 EGT1" to "engine[2].egt[1]",
      "E1 FFlow" to "engine[1].fuel_flow",
      "E1 %Pwr" to "engine[1].power_pct",
      "E1 FPres" to "engine[1].fuel_press",
      "FQty1" to "fuel.qty[1]",
      "FQty2" to "fuel.qty[2]",
      "Volts1" to "elec.volts[1]",
      "Amps2" to "elec.amps[2]",
      "IAS" to "flight.ias",
      "TAS" to "flight.tas",
      "AltGPS" to "flight.alt_gps",
      "AltP" to "flight.alt_pressure",
      "AltInd" to "flight.alt_baro",
      "VSpd" to "flight.vs",
      "GndSpd" to "flight.ground_speed",
      "AGL" to "flight.agl",
      "OAT" to "air.oat",
      "Pitch" to "flight.pitch",
      "Roll" to "flight.roll",
      "NormAc" to "flight.g_normal",
      "LatAc" to "flight.g_lateral",
      "HDG" to "nav.heading",
      "Latitude" to "position.lat",
      "Longitude" to "position.lon",
    )
    table.forEach { (short, canonical) ->
      assertThat(id(short)).isEqualTo(
        canonical
      )
    }
  }

  @Test
  fun theG1000SpellingsReachTheSameIdsAsTheG3XOnes() {
    // Same reading, different name on each recorder. The point of the registry is that a preset or
    // a derived rule written once works on both.
    assertThat(id("AltB")).isEqualTo(id("AltInd"))
    assertThat(id("volt1")).isEqualTo(id("Volts1"))
    assertThat(id("volt2")).isEqualTo("elec.volts[2]")
    assertThat(id("amp1")).isEqualTo(id("Amps1"))
    assertThat(id("FQtyL")).isEqualTo(id("FQty1"))
    assertThat(id("FQtyR")).isEqualTo(id("FQty2"))
  }

  @Test
  fun turbineAndTitColumnsHaveTheirOwnFields() {
    assertThat(id("E1 TIT1")).isEqualTo("engine[1].tit[1]")
    assertThat(id("E1 TIT2")).isEqualTo("engine[1].tit[2]")
    assertThat(id("E1 ITT")).isEqualTo("engine[1].itt")
    assertThat(id("E1 NG")).isEqualTo("engine[1].ng")
    assertThat(id("E1 Torq")).isEqualTo("engine[1].torque")
    assertThat(id("E1 N1")).isEqualTo("engine[1].n1")
    assertThat(id("E2 N2")).isEqualTo("engine[2].n2")
  }

  @Test
  fun unknownColumnsStayUnmapped() {
    assertThat(id("E1 CarbT")).isEmpty()
    assertThat(id("PTrim")).isEmpty()
    assertThat(id("")).isEmpty()
    assertThat(id("Flaps")).isEmpty()
    // A G1000 records three altitudes and only two of them have a canonical id. Guessing which of
    // the two AltMSL is would put two different measurements on one series.
    assertThat(id("AltMSL")).isEmpty()
  }
}
