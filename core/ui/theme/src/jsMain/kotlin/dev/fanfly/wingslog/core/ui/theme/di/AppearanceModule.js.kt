package dev.fanfly.wingslog.core.ui.theme.di

import dev.fanfly.wingslog.core.ui.theme.AppearanceStore
import dev.fanfly.wingslog.core.ui.theme.JsAppearanceStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual val appearanceStoreModule: Module = module {
  single<AppearanceStore> { JsAppearanceStore() }
}
