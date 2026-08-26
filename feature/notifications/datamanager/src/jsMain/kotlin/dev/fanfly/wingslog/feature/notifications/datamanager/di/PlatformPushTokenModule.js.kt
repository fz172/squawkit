package dev.fanfly.wingslog.feature.notifications.datamanager.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Empty, and not merely "not yet". Web V1 has no push transport by design (§8): an open tab detects
 * a collaborator's write through the sync engine it already runs, so there is no token to register.
 * Real web push — service worker, VAPID, a `push_devices` entry — is P6, at which point
 * `AppCapability.isPushSupported` flips true on `jsMain` and this gains a binding.
 */
actual val platformPushTokenModule: Module = module { }
