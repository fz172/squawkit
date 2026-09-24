package dev.fanfly.wingslog.feature.subscription.viewing.plan

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector
import dev.fanfly.wingslog.feature.subscription.model.PurchasePlatform
import org.jetbrains.compose.resources.StringResource
import wingslog.feature.subscription.viewing.generated.resources.Res
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_amazon
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_app_store
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_mac_app_store
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_play_store
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_test_store
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_web

/**
 * The store's display name.
 *
 * Lives here rather than on the enum because [PurchasePlatform] is a `model` type shared with the
 * billing layer, which has no Compose resources — and a store's *name* is a presentation concern in
 * a way its identity is not.
 */
internal val PurchasePlatform.labelRes: StringResource
  get() = when (this) {
    PurchasePlatform.APP_STORE -> Res.string.subscription_platform_app_store
    PurchasePlatform.MAC_APP_STORE -> Res.string.subscription_platform_mac_app_store
    PurchasePlatform.PLAY_STORE -> Res.string.subscription_platform_play_store
    PurchasePlatform.AMAZON -> Res.string.subscription_platform_amazon
    PurchasePlatform.WEB -> Res.string.subscription_platform_web
    PurchasePlatform.TEST_STORE -> Res.string.subscription_platform_test_store
  }

/** The glyph for the store that billed the subscription — the mark the pilot will recognise there. */
internal val PurchasePlatform.icon: ImageVector
  get() = when (this) {
    PurchasePlatform.APP_STORE -> Icons.Default.PhoneIphone
    PurchasePlatform.MAC_APP_STORE -> Icons.Default.Laptop
    PurchasePlatform.PLAY_STORE -> Icons.Default.Android
    PurchasePlatform.AMAZON -> Icons.Default.ShoppingBag
    PurchasePlatform.WEB -> Icons.Default.Language
    PurchasePlatform.TEST_STORE -> Icons.Default.Science
  }
