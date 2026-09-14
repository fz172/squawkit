package dev.fanfly.wingslog.feature.datalog.datamanager

import java.io.File

/** The anonymised G3X samples in `docs/datalog/samples/g3x` (design §6.4). */
object Fixtures {
  const val GROUND_RUN = "log_20260902_144756_XX1.csv"

  fun bytes(name: String): ByteArray = File(sampleDir(), name).readBytes()

  fun sampleDir(): File = File(repoRoot(), "docs/datalog/samples/g3x")

  private fun repoRoot(): File {
    var dir = File(System.getProperty("user.dir"))
    while (!File(dir, "settings.gradle.kts").exists()) dir = requireNotNull(dir.parentFile)
    return dir
  }

  /** A minimal G3X-shaped file with the given short-name columns and rows of cells. */
  fun synthetic(
    columns: List<Triple<String, String, String>>, // long name, unit, short name
    rows: List<List<String>>,
    product: String = "GDU 460",
  ): ByteArray {
    val header = "#airframe_info,log_version=\"1.00\",product=\"$product\",aircraft_ident=\"N1234X\"," +
      "system_id=\"6000ABCD01234\",unit=\"PFD1\""
    val longs = listOf("Date (yyyy-mm-dd)", "Time (hh:mm:ss)", "UTC Offset (hh:mm)") +
      columns.map { (n, u, _) -> if (u.isEmpty()) n else "$n ($u)" }
    val shorts = listOf("Lcl Date", "Lcl Time", "UTCOfst") + columns.map { it.third }
    val body = rows.joinToString("\n") { it.joinToString(",") }
    return (header + "\n" + longs.joinToString(",") + "\n" + shorts.joinToString(",") + "\n" + body + "\n")
      .encodeToByteArray()
  }

  fun syntheticRows(count: Int, cells: (Int) -> List<String>): List<List<String>> =
    (0 until count).map { i ->
      val h = 10 + i / 3600
      val m = (i / 60) % 60
      val s = i % 60
      listOf("2026-09-02", "%02d:%02d:%02d".format(h, m, s), "-07:00") + cells(i)
    }
}
