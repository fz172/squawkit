package dev.fanfly.wingslog.core.analytics.di

import org.koin.core.module.Module

/** Provides `single<AnalyticsManager>` bound to the platform's analytics backend. */
expect val platformAnalyticsModule: Module
