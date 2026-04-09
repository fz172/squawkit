@file:OptIn(ExperimentalMaterial3Api::class)

package dev.fanfly.wingslog.feature.userprofile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.model.userprofile.LicenseExpireLimit
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.technician.sharedassets.compose.CertificateInputFields
import dev.fanfly.wingslog.feature.userprofile.data.EditProfileViewModel
import dev.fanfly.wingslog.feature.userprofile.userprofilecard.compose.UserProfileCard
import dev.fanfly.wingslog.feature.userprofile.userprofilecard.compose.UserProfileCardData
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.ui.generated.resources.edit_profile
import kotlin.time.Instant
import wingslog.core.ui.generated.resources.Res as CoreUiRes


@Composable
fun EditProfileScreen(
  viewModel: EditProfileViewModel = koinViewModel(),
  navController: NavController,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // State for the dropdown menu
  var expanded by remember { mutableStateOf(false) }

  // This effect will run when isSaved becomes true
  LaunchedEffect(uiState.isSaved) {
    if (uiState.isSaved) {
      // Navigate back when save is successful
      navController.popBackStack()
    }
  }

  Scaffold(topBar = {
    WingsLogTopAppBar(
      title = stringResource(CoreUiRes.string.edit_profile),
      onBackClick = { navController.popBackStack() })
  }) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
          .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.columnGap)
      ) {
        UserProfileCard(
          data = UserProfileCardData(
            displayName = uiState.displayName,
            photoUri = uiState.photoUri,
          )
        )

        CertificateInputFields(
          licenseType = uiState.licenceInfo.license_type,
          onLicenseTypeChanged = { viewModel.onLicenseTypeChanged(it) },
          licenseNumber = uiState.licenceInfo.license_number,
          onLicenseNumberChanged = { viewModel.onLicenseNumberChanged(it) },
          expireLimit = uiState.licenceInfo.expireLimit,
          onExpireLimitChanged = { viewModel.onExpirationNeverFlagChanged(it == LicenseExpireLimit.NEVER_EXPIRES) },
          expirationDate = uiState.licenceInfo.expiration_date?.let {
            Instant.fromEpochSeconds(it.getEpochSecond(), it.getNano())
          },
          onExpirationDateChanged = { viewModel.onExpirationDateChanged(it) })

        Spacer(Modifier.height(88.dp))
      }
      BottomButtons(
        modifier = Modifier.align(Alignment.BottomCenter),
        primaryEnabled = !uiState.isLoading,
        onPrimaryClick = { viewModel.saveChanges() },
        onSecondaryClick = { navController.popBackStack() })
    }
  }
}
