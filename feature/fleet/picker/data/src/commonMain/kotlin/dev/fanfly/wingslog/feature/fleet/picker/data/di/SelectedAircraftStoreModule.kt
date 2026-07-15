package dev.fanfly.wingslog.feature.fleet.picker.data.di

import org.koin.core.module.Module

/**
 * Platform-provided device-local
 * [dev.fanfly.wingslog.feature.fleet.picker.data.SelectedAircraftStore]. The Android actual receives
 * the application [android.content.Context] via Koin's `androidContext()`, mirroring the other
 * platform preference stores.
 */
expect val selectedAircraftStoreModule: Module
