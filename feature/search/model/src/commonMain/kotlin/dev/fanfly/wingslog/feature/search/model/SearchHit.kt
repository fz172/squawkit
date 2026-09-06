package dev.fanfly.wingslog.feature.search.model

/** One record that survived the filters, with the score the query gave it (0 for a blank query). */
data class SearchHit<T>(val item: T, val score: Double = 0.0)
