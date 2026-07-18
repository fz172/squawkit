package dev.fanfly.wingslog.feature.sharing.update

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import dev.fanfly.wingslog.feature.sharing.datamanager.AircraftShareDeepLinks
import dev.fanfly.wingslog.feature.sharing.viewing.EnterInviteCodeScreen

/**
 * Hosts [EnterInviteCodeScreen] on the shell nav graph (#209). Submitting parks the code on the same
 * channel a `/share#{code}` deep link fills, then pops back: the app-root RedeemHost picks the parked
 * code up and drives preview → confirm → (guest) park-and-resume → redeem. So this route owns no
 * redeem logic of its own — it is a second mouth for the one pipe.
 */
@Composable
fun EnterInviteCodeRoute(navController: NavController) {
  EnterInviteCodeScreen(
    onSubmit = { rawCode ->
      AircraftShareDeepLinks.deliverCode(rawCode)
      // Return to the fleet; the confirmation sheet floats over it via RedeemHost. Popping first
      // means dismissing/accepting the sheet leaves the user on the fleet, not back on this form.
      navController.popBackStack()
    },
    onDismiss = { navController.popBackStack() },
  )
}
