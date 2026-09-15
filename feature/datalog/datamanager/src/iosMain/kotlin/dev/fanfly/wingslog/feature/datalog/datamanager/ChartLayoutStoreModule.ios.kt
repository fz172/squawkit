package dev.fanfly.wingslog.feature.datalog.datamanager

import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformChartLayoutStoreModule: Module = module {
  single<ChartLayoutStore> { IosChartLayoutStore() }
}
