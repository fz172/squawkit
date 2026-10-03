package dev.fanfly.wingslog.core.connectivity.di

import dev.fanfly.wingslog.core.connectivity.ConnectivityMonitor
import dev.fanfly.wingslog.core.connectivity.WebConnectivityMonitor
import org.koin.dsl.module

internal actual val platformConnectivityModule = module {
  single<ConnectivityMonitor> { WebConnectivityMonitor() }
}
