package dev.fanfly.wingslog.feature.datalog.datamanager

import org.koin.core.module.Module

/** The device-local [ChartLayoutStore]; the Android actual needs Koin's `androidContext()`. */
internal expect val platformChartLayoutStoreModule: Module
