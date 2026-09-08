package dev.fanfly.wingslog.core.ui.theme.di

import dev.fanfly.wingslog.core.ui.theme.AndroidAppearanceStore
import dev.fanfly.wingslog.core.ui.theme.AppearanceStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformAppearanceStoreModule: Module = module {
  single<AppearanceStore> { AndroidAppearanceStore(androidContext()) }
}
