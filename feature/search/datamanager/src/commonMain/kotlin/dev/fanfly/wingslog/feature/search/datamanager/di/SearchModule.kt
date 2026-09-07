package dev.fanfly.wingslog.feature.search.datamanager.di

import dev.fanfly.wingslog.feature.search.datamanager.SearchEngine
import dev.fanfly.wingslog.feature.search.datamanager.impl.SearchEngineImpl
import dev.fanfly.wingslog.feature.search.model.SearchTuning
import org.koin.dsl.module

val searchModule = module {
  single<SearchEngine> { SearchEngineImpl() }
  single<SearchTuning> { SearchTuning() }
}
