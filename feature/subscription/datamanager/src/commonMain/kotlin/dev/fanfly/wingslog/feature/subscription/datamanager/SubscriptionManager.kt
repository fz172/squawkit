package dev.fanfly.wingslog.feature.subscription.datamanager

import dev.fanfly.wingslog.core.model.settings.Subscription
import kotlinx.coroutines.flow.Flow

/**
 * The single reactive subscription gate. Reads the server-authoritative entitlement from the local
 * store (mirrored there by the sync layer), resolves the effective tier, and exposes the per-feature
 * gates the app enforces. See docs/subscription/subscription_design.html §4.
 *
 * Gating is **default-open**: while `AppCapability.isSubscriptionSupported` is off there is no
 * paywall, so every capability reads available and [aircraftLimit] is unlimited.
 *
 * The types are the `Subscription` proto and its enums — the same model the Cloud Functions and
 * Firestore share — never a forked Kotlin copy.
 */
interface SubscriptionManager {

  /** The effective tier in force now (entitlement resolved, dev override applied). Page + comparison. */
  fun status(): Flow<Subscription.Status>

  /** The raw entitlement for the status page (member-since, period end, storage usage). */
  fun entitlement(): Flow<Subscription>

  /** Photo/file attachment upload (links stay free). */
  fun canUploadAttachments(): Flow<Boolean>

  /** Emailing a copy of an export (export-to-device stays free). */
  fun canEmailExports(): Flow<Boolean>

  /** Hosting a shared aircraft / sending invites (accepting an invite is never gated). */
  fun canHostShare(): Flow<Boolean>

  /** Max aircraft the account may own; `null` = unlimited. Enforced against the owned count. */
  fun aircraftLimit(): Flow<Int?>
}
