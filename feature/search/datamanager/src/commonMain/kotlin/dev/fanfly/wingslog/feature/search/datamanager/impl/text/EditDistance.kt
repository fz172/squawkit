package dev.fanfly.wingslog.feature.search.datamanager.impl.text

import kotlin.math.abs
import kotlin.math.min

/** Damerau–Levenshtein (a transposition is one edit), returning `cap + 1` as soon as [cap] is exceeded. */
fun editDistance(a: String, b: String, cap: Int): Int {
  if (abs(a.length - b.length) > cap) return cap + 1
  if (a == b) return 0
  var prevPrev: IntArray? = null
  var prev = IntArray(b.length + 1) { it }
  for (i in 1..a.length) {
    val cur = IntArray(b.length + 1)
    cur[0] = i
    var rowMin = i
    for (j in 1..b.length) {
      val cost = if (a[i - 1] == b[j - 1]) 0 else 1
      var d = min(min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + cost)
      if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
        d = min(d, prevPrev!![j - 2] + 1)
      }
      cur[j] = d
      if (d < rowMin) rowMin = d
    }
    if (rowMin > cap) return cap + 1
    prevPrev = prev
    prev = cur
  }
  return if (prev[b.length] > cap) cap + 1 else prev[b.length]
}
