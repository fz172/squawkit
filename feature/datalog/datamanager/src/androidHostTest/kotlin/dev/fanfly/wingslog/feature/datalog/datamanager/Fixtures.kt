package dev.fanfly.wingslog.feature.datalog.datamanager

import java.io.File

/** The anonymised Garmin samples in `docs/datalog/samples` (design §6.4). */
object Fixtures {
  const val GROUND_RUN = "log_20260902_144756_XX1.csv"

  /** A piston G1000: a `#` units row, CHT/EGT/TIT banks, and tanks named by side. */
  const val G1000_PISTON = "log_150513_081128_XX1.csv"

  /** A turbine G1000 from power-up: four opening rows carry no clock at all. */
  const val G1000_TURBINE = "log_240810_104802_XX2.csv"

  /** The same turbine in the climb: ITT and the spool speeds are alive, and it is airborne. */
  const val G1000_CRUISE = "log_240810_110536_XX3.csv"

  /** One SkyView power-on, every row dated. The file name is not the exporter's own. */
  const val DYNON_SINGLE = "skyview_single_session.csv"

  /** Four power-on sessions in one download; the last two never got a GPS fix. */
  const val DYNON_SESSIONS = "2019-04-28-N1234X-SN0001-15_3_4_4867-USER_LOG_DATA.csv"

  /** An older 27-column Avidyne: no software id, no turbo columns. */
  const val AVIDYNE_PLAIN = "Engine_060212_152326_out.log"

  /** The same unit, running past midnight — the only date in the file is on line 2. */
  const val AVIDYNE_DATE_WRAP = "Engine_050911_233509_out.log"

  /** A 32-column turbo unit: pressure and density altitude, turbine inlet, discrete channels. */
  const val AVIDYNE_TURBO = "Engine_120313_184105_out.log"

  /** A turbo unit whose clock steps back 84 seconds four rows in. */
  const val AVIDYNE_TIME_JUMP = "Engine_090121_191809_out.log"

  fun bytes(name: String): ByteArray = File(sampleDir(), name).readBytes()

  fun avidyneBytes(name: String): ByteArray = File(avidyneDir(), name).readBytes()

  fun avidyneDir(): File = File(repoRoot(), "docs/datalog/samples/avidyne")

  fun dynonBytes(name: String): ByteArray = File(dynonDir(), name).readBytes()

  fun dynonDir(): File = File(repoRoot(), "docs/datalog/samples/dynon")

  fun g1000Bytes(name: String): ByteArray = File(g1000Dir(), name).readBytes()

  fun sampleDir(): File = File(repoRoot(), "docs/datalog/samples/g3x")

  fun g1000Dir(): File = File(repoRoot(), "docs/datalog/samples/g1000")

  private fun repoRoot(): File {
    var dir = File(System.getProperty("user.dir"))
    while (!File(dir, "settings.gradle.kts").exists()) dir =
      requireNotNull(dir.parentFile)
    return dir
  }

  /** A minimal G3X-shaped file with the given short-name columns and rows of cells. */
  fun synthetic(
    columns: List<Triple<String, String, String>>, // long name, unit, short name
    rows: List<List<String>>,
    product: String = "GDU 460",
  ): ByteArray {
    val header =
      "#airframe_info,log_version=\"1.00\",product=\"$product\",aircraft_ident=\"N1234X\"," +
        "system_id=\"6000ABCD01234\",unit=\"PFD1\""
    val longs =
      listOf("Date (yyyy-mm-dd)", "Time (hh:mm:ss)", "UTC Offset (hh:mm)") +
        columns.map { (n, u, _) -> if (u.isEmpty()) n else "$n ($u)" }
    val shorts =
      listOf("Lcl Date", "Lcl Time", "UTCOfst") + columns.map { it.third }
    val body = rows.joinToString("\n") { it.joinToString(",") }
    return (header + "\n" + longs.joinToString(",") + "\n" + shorts.joinToString(
      ","
    ) + "\n" + body + "\n")
      .encodeToByteArray()
  }

  fun syntheticRows(
    count: Int,
    cells: (Int) -> List<String>
  ): List<List<String>> =
    (0 until count).map { i ->
      val h = 10 + i / 3600
      val m = (i / 60) % 60
      val s = i % 60
      listOf(
        "2026-09-02",
        "%02d:%02d:%02d".format(h, m, s),
        "-07:00"
      ) + cells(i)
    }
}
