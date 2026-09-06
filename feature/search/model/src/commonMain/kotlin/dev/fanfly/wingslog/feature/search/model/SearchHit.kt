package dev.fanfly.wingslog.feature.search.model

/** A record that passed the filters; [score] is 0 for a blank query. */
data class SearchHit<T>(val item: T, val score: Double = 0.0)
