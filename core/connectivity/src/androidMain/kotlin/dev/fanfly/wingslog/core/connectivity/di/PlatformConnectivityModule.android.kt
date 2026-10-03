package dev.fanfly.wingslog.core.connectivity.di

import dev.fanfly.wingslog.core.connectivity.AndroidConnectivityMonitor
import dev.fanfly.wingslog.core.connectivity.ConnectivityMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

internal actual val platformConnectivityModule = module {
  single<ConnectivityMonitor> { AndroidConnectivityMonitor(androidContext()) }
}
