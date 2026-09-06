package dev.fanfly.wingslog.feature.search.datamanager.di

import dev.fanfly.wingslog.feature.search.datamanager.SearchEngine
import dev.fanfly.wingslog.feature.search.datamanager.impl.SearchEngineImpl
import org.koin.dsl.module

val searchModule = module {
  single<SearchEngine> { SearchEngineImpl() }
}
