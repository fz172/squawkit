package dev.fanfly.wingslog.core.storage.di

import org.koin.core.module.Module

/**
 * Platform-specific bindings for [dev.fanfly.wingslog.core.storage.DriverFactory]. The Android
 * actual receives the application [android.content.Context] via Koin's `androidContext()`.
 */
expect val platformStorageModule: Module
