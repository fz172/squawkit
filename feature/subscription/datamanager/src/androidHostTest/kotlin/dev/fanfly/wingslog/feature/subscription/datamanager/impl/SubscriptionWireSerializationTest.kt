package dev.fanfly.wingslog.feature.subscription.datamanager.impl

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.serializer
import org.junit.Test

/**
 * Proves every callable wire type in this module actually has a generated serializer.
 *
 * ## The bug this exists for
 *
 * The module applied `@Serializable` to its request/response types but **never applied the
 * kotlinx-serialization compiler plugin**, so nothing was generated. That is invisible at every
 * point you would normally look:
 *
 * - it compiles, because `@Serializable` is just an annotation without the plugin;
 * - `serializer<T>()` still resolves — to a *runtime reflective* lookup;
 * - the lookup only fails when the call is actually made, as
 *   `SerializationException: Serializer for class 'X' is not found`;
 * - and the one client that exercised it, [FirebaseEntitlementReconciler], swallows every exception
 *   by design, so it reported the breakage as a single warning line and carried on.
 *
 * That first client got away with it: its callable takes no payload, so only the *response* decode
 * threw — the reconcile itself still ran server-side, and neither call site reads the return value.
 * Promo redemption does send a payload, so the same defect killed the request before it left the
 * device, and surfaced to the pilot as "couldn't reach SquawkIt".
 *
 * ## Why this test catches it
 *
 * The plugin is applied per module, so a test compilation in a module without it resolves
 * `serializer<T>()` the same reflective way production does, and throws here instead of in the
 * pilot's hands. Delete the plugin from `build.gradle.kts` and these go red.
 *
 * Asserting on `descriptor` rather than round-tripping JSON is deliberate: the descriptor is what
 * the reflective path fails to produce, and it needs no serialization-format dependency to inspect.
 */
class SubscriptionWireSerializationTest {

  @Test
  fun `the promo redemption request has a generated serializer`() {
    val descriptor = serializer<RedeemPromoRequest>().descriptor

    assertThat(descriptor.serialName).contains("RedeemPromoRequest")
    // The field the server reads. A descriptor that knows its elements is a generated one.
    assertThat(descriptor.elementNames.toList()).containsExactly("code")
  }

  @Test
  fun `the promo redemption response has a generated serializer`() {
    val descriptor = serializer<RedeemPromoResponse>().descriptor

    assertThat(descriptor.elementNames.toList())
      .containsExactly("durationDays", "currentPeriodEndMillis")
  }

  @Test
  fun `the entitlement reconcile response has a generated serializer`() {
    // The one that was broken in production without ever saying so.
    val descriptor = serializer<ReconcileResponseData>().descriptor

    assertThat(descriptor.elementNames.toList()).containsExactly("reconciled", "reason")
  }
}
