package dev.fanfly.wingslog.core.ui.common.text

/**
 * Character ranges of [words] in [text], whole-word and case-insensitive. A word may be a phrase;
 * a token containing `.`, `/` or `-` matches whole or by its parts.
 */
fun highlightRanges(text: String, words: Set<String>): List<IntRange> {
  if (words.isEmpty() || text.isEmpty()) return emptyList()
  val lower = CharArray(text.length) { text[it].lowercaseChar() }.concatToString()
  val ranges = ArrayList<IntRange>()
  var i = 0
  while (i < lower.length) {
    if (!lower[i].isTokenChar()) { i++; continue }
    var end = i
    while (end < lower.length && lower[end].isTokenChar()) end++
    val run = lower.substring(i, end).trim('.', '/', '-')
    val runStart = i + (end - i - lower.substring(i, end).trimStart('.', '/', '-').length)
    if (run.isNotEmpty() && run in words) {
      ranges.add(runStart until runStart + run.length)
    } else {
      var partStart = i
      for (k in i..end) {
        if (k == end || lower[k] in INNER) {
          if (k > partStart && lower.substring(partStart, k) in words) ranges.add(partStart until k)
          partStart = k + 1
        }
      }
    }
    i = end
  }
  for (phrase in words) {
    if (' ' !in phrase) continue
    var from = lower.indexOf(phrase)
    while (from >= 0) {
      val to = from + phrase.length
      val bounded = (from == 0 || !lower[from - 1].isLetterOrDigit()) && (to == lower.length || !lower[to].isLetterOrDigit())
      if (bounded) ranges.add(from until to)
      from = lower.indexOf(phrase, to)
    }
  }
  return ranges.sortedBy { it.first }
}

private const val INNER = "./-"
private fun Char.isTokenChar() = isLetterOrDigit() || this in INNER
