package dev.fanfly.wingslog.feature.ads.viewing

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import dev.fanfly.wingslog.feature.ads.model.AdSurface
import dev.fanfly.wingslog.feature.ads.model.AdUnitSize
import java.util.concurrent.atomic.AtomicBoolean
import com.google.android.gms.ads.AdView as GmsAdView

/**
 * Initialised on first use rather than at app start.
 *
 * A Heavy subscriber's app must never start an ad SDK at all, and nothing reaches this file unless
 * `AdsManager.showsAds()` already emitted true — so "first ad view composed" is exactly the moment
 * the SDK becomes justified. [AtomicBoolean] because `MobileAds.initialize` is cheap to call twice
 * but not free, and two slots can compose in the same frame.
 */
private val adsInitialized = AtomicBoolean(false)

private fun AdUnitSize.toGmsAdSize(): AdSize = when (this) {
  AdUnitSize.BANNER -> AdSize.BANNER
  AdUnitSize.LARGE_BANNER -> AdSize.LARGE_BANNER
}

/**
 * Fixed-size AdMob banner. Fixed rather than adaptive so the no-billboard guarantee holds by
 * construction: a 320 dp creative cannot stretch to fill a tablet band, and a known height needs no
 * runtime clamping (design §7.2).
 *
 * The view is sized to the creative exactly, so an unfilled unit contributes no height of its own —
 * [AdSlot] collapses the whole card when every unit reports a failure.
 */
@Composable
actual fun AdView(
  size: AdUnitSize,
  surface: AdSurface,
  useTestAds: Boolean,
  onFilled: () -> Unit,
  onFailed: (reason: String) -> Unit,
  onClicked: () -> Unit,
  modifier: Modifier,
) {
  // The listener is created once with the view; these keep it pointing at the current lambdas
  // instead of the ones captured on first composition.
  val currentFilled by rememberUpdatedState(onFilled)
  val currentFailed by rememberUpdatedState(onFailed)
  val currentClicked by rememberUpdatedState(onClicked)
  val gmsSize = remember(size) { size.toGmsAdSize() }

  AndroidView(
    modifier = modifier.size(
      width = size.widthDp.dp,
      height = size.heightDp.dp
    ),
    factory = { context ->
      if (adsInitialized.compareAndSet(false, true)) {
        MobileAds.initialize(context) {}
      }
      GmsAdView(context).apply {
        setAdSize(gmsSize)
        adUnitId = adUnitIdFor(surface, useTestAds)
        adListener = object : AdListener() {
          override fun onAdLoaded() = currentFilled()
          override fun onAdFailedToLoad(error: LoadAdError) {
            // Coarse and non-identifying: this becomes an analytics param, and the pilot is never
            // shown it — ad failures are a normal quiet outcome, not an error state (N3).
            currentFailed(error.code.toString())
          }

          override fun onAdClicked() = currentClicked()
        }
        loadAd(
          AdRequest.Builder()
            .build()
        )
      }
    },
    // Release the native view when the slot leaves composition. The session cap already bounds live
    // views to five, but the two card surfaces are not lazy — nothing recycles for us there — so the
    // ad view has to be destroyed explicitly rather than left to the GC (N5).
    onRelease = { it.destroy() },
  )
}

/**
 * Which inventory to request for [surface].
 *
 * **Developer builds get Google's public test unit, never the real one.** Impressions and clicks
 * generated during development count as invalid traffic, and AdMob suspends accounts for it — so the
 * safe id is the default and the real one is the exception, rather than relying on anyone remembering
 * to register a test device.
 *
 * These ids are Android-only: AdMob issues separate units per platform, so iOS gets its own table
 * under its own app id (still to be created).
 *
 * Not secrets — ad unit ids ship in every APK — but they are live inventory, which is why the
 * developer-build branch exists.
 */
private fun adUnitIdFor(surface: AdSurface, useTestAds: Boolean): String =
  if (useTestAds) {
    GOOGLE_TEST_BANNER_UNIT
  } else {
    when (surface) {
      AdSurface.SQUAWKS -> "ca-app-pub-1367143209408464/3781294453"
      AdSurface.TASKS -> "ca-app-pub-1367143209408464/2468212789"
      AdSurface.LOGS -> "ca-app-pub-1367143209408464/8842049449"
    }
  }

/** Google's public test banner unit. Serves a filled house ad, so it also exercises the filled path. */
private const val GOOGLE_TEST_BANNER_UNIT =
  "ca-app-pub-3940256099942544/6300978111"
