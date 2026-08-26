package dev.fanfly.wingslog.feature.notifications.datamanager.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Empty until P5 (#506). iOS receives its token through APNs, which needs the certificates,
 * entitlements and `remote-notification` background mode of #503 before there is a token to
 * register at all — so binding a registrar here now would be a class with nothing to feed it.
 */
actual val platformPushTokenModule: Module = module { }
