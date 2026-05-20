package dev.fanfly.wingslog.feature.export.datamanager.di

import org.koin.core.module.Module

/**
 * Platform-specific bindings for [dev.fanfly.wingslog.feature.export.datamanager.impl.ExportFileStore].
 * The Android actual receives the application [android.content.Context] via Koin's `androidContext()`.
 */
expect val exportPlatformModule: Module
