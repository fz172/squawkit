package dev.fanfly.wingslog.feature.search.datamanager

/** A word and what it stands for; a query on either side finds the other. */
data class SynonymEntry(val word: String, val expansion: String)
