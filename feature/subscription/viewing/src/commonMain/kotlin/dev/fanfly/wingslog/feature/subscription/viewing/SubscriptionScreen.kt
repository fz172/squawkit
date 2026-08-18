package dev.fanfly.wingslog.feature.subscription.viewing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.subscription.viewing.paywall.CustomerCenterHost
import dev.fanfly.wingslog.feature.subscription.viewing.paywall.ProPaywallHost
import dev.fanfly.wingslog.feature.subscription.viewing.viewmodel.SubscriptionViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.subscription.viewing.generated.resources.Res
import wingslog.feature.subscription.viewing.generated.resources.subscription_cta_subscribe
import wingslog.feature.subscription.viewing.generated.resources.subscription_title

/**
 * The subscription page. Non-subscribers see the tier comparison and buy into RevenueCat's hosted
 * paywall; subscribers see their membership and manage it through RevenueCat's Customer Center.
 *
 * The two states are deliberately different pages rather than one page with a swapped button. Before
 * buying, the pilot is deciding, so the screen argues — headline, comparison, one call to action.
 * After buying, they are checking on something they already own, so it reports — status, dates,
 * storage, and a way out to the store.
 *
 * Neither state ever prints a price: the store owns pricing, currency and billing period, and
 * quoting them here would eventually be wrong somewhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
  navController: NavController,
  viewModel: SubscriptionViewModel = koinViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var sheet by remember { mutableStateOf(BillingSheet.None) }

  // Full-screen, replacing the page rather than layering over it: both RevenueCat surfaces bring
  // their own scaffolding and close affordance, so nesting them inside ours would double the chrome.
  when (sheet) {
    BillingSheet.Paywall -> {
      ProPaywallHost(
        onPurchaseCompleted = { viewModel.onPurchaseCompleted() },
        onDismiss = { sheet = BillingSheet.None },
      )
      return
    }

    BillingSheet.CustomerCenter -> {
      CustomerCenterHost(onDismiss = { sheet = BillingSheet.None })
      return
    }

    BillingSheet.None -> Unit
  }

  Scaffold(
    topBar = {
      ConstrainedTopBar {
        WingsLogTopAppBar(
          title = stringResource(Res.string.subscription_title),
          onBackClick = { navController.popBackStack() },
        )
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .padding(innerPadding)
        .fillMaxSize(),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .constrainedContentWidth(ContentWidth.Reading)
          .fillMaxSize()
          .padding(Spacing.screenPadding)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
      ) {
        if (uiState.isPro) {
          ProMembershipContent(
            state = uiState,
            onManage = { sheet = BillingSheet.CustomerCenter },
          )
        } else {
          ProPaywallContent(
            state = uiState,
            onSubscribe = { sheet = BillingSheet.Paywall },
          )
        }
      }
    }
  }
}

/** Which full-screen billing surface, if any, is covering the page. */
private enum class BillingSheet { None, Paywall, CustomerCenter }

/**
 * A bordered panel — the page's one container shape, used for the comparison table, the membership
 * card and the perk tiles so they read as one family rather than three card styles.
 */
@Composable
internal fun SubscriptionPanel(
  modifier: Modifier = Modifier,
  borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = RoundedCornerShape(Spacing.cardCornerRadius)
  Column(
    modifier = modifier
      .clip(shape)
      .background(containerColor)
      .border(Spacing.hairline, borderColor, shape),
    content = content,
  )
}

/**
 * The subscribe CTA — filled, full width, 56dp, UPPERCASE Bold per DESIGN.md's "Uppercase
 * Commitment Rule" ("a button is a decision, not an option").
 *
 * Shared by [ProPaywallContent] and [ProUpsellSheet] so the same action can never render as two
 * different component types depending on which screen shows it.
 */
@Composable
internal fun SubscribeButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(Spacing.buttonCornerRadius),
    colors = ButtonDefaults.buttonColors(),
    modifier = modifier
      .fillMaxWidth()
      .height(Spacing.buttonHeight),
  ) {
    Text(
      text = stringResource(Res.string.subscription_cta_subscribe).uppercase(),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )
  }
}

/**
 * An uppercase section marker ("What's included", "Unlocked with Pro").
 *
 * Uppercased at render rather than in the string resource so translations stay sentence case and a
 * language without letter case is unaffected.
 */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text.uppercase(),
    style = MaterialTheme.typography.labelSmall,
    letterSpacing = SECTION_LABEL_TRACKING,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier,
  )
}

/** Fine print under a button — never the only place a state is communicated. */
@Composable
internal fun SubscriptionCaption(
  text: String,
  modifier: Modifier = Modifier,
  textAlign: TextAlign? = null,
) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = textAlign,
    modifier = modifier.fillMaxWidth(),
  )
}

internal val SECTION_LABEL_TRACKING = 0.9.sp
