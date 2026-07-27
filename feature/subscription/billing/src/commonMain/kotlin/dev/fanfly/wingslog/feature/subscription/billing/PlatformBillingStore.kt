package dev.fanfly.wingslog.feature.subscription.billing

import dev.fanfly.wingslog.feature.subscription.model.BillingStore

/**
 * The storefront this build transacts with — Google Play on Android, the App Store on iOS.
 *
 * An `expect val` for the same reason [platformProductionApiKey] is one: RevenueCat fronts a
 * different store on each platform, and [dev.fanfly.wingslog.feature.subscription.billing.impl]
 * is a single common implementation shared by both. It says nothing about *whether* purchasing
 * works — a build with no key still runs on Play, it just has no key — so the manager pairs this
 * with its own configured state before reporting a store.
 */
internal expect val platformBillingStore: BillingStore
