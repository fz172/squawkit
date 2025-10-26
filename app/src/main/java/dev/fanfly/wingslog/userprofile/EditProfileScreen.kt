@file:OptIn(ExperimentalMaterial3Api::class)

package dev.fanfly.wingslog.dev.fanfly.wingslog.userprofile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.R
import dev.fanfly.wingslog.common.WingsLogTopAppBar
import dev.fanfly.wingslog.dev.fanfly.wingslog.common.BottomButtons
import dev.fanfly.wingslog.userprofile.data.EditProfileUiState
import dev.fanfly.wingslog.userprofile.data.EditProfileViewModel
import dev.fanfly.wingslog.userprofile.data.LicenseType
import dev.fanfly.wingslog.userprofile.data.displayResId


@Composable
fun EditProfileScreen(
  viewModel: EditProfileViewModel = hiltViewModel(),
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
      title = stringResource(R.string.edit_profile),
      onBackClick = { navController.popBackStack() })
  }, bottomBar = {
    // This composable holds the buttons pinned to the bottom
    BottomButtons(
      onSaveClick = { viewModel.saveChanges() }, // Call ViewModel to save
      onCancelClick = { navController.popBackStack() })
  }) { innerPadding ->
    Column(
      modifier = Modifier
        .padding(innerPadding)
        .fillMaxSize()
        .padding(horizontal = 16.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      EditProfileNameCard(uiState = uiState)

      // --- License Type (Dropdown) ---
      ExposedDropdownMenuBox(
        expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
          value = stringResource(uiState.licenseType.displayResId()),
          onValueChange = {},
          readOnly = true,
          label = { Text(text = stringResource(R.string.license_type)) },
          trailingIcon = {
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
          },
          modifier = Modifier
            .fillMaxWidth()
            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
          shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
          expanded = expanded, onDismissRequest = { expanded = false }) {
          LicenseType.entries.filter { it != LicenseType.UNRECOGNIZED }
            .forEach { type ->
              DropdownMenuItem(
                text = { Text(stringResource(id = type.displayResId())) },
                onClick = {
                  viewModel.onLicenseTypeChanged(type) // Update ViewModel
                  expanded = false
                })
            }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // --- License Number ---
      OutlinedTextField(
        value = uiState.licenseNumber, // Read from ViewModel
        onValueChange = { viewModel.onLicenseNumberChanged(it) }, // Update ViewModel
        label = { Text(stringResource(R.string.license_number)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        enabled = uiState.licenseType != LicenseType.NONE
      )

      Spacer(modifier = Modifier.height(20.dp))

      // --- Expiration Date ---
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedTextField(
          value = if (!uiState.licenseNeverExpires) {
            uiState.expirationDate
          } else "", // Read from ViewModel
          onValueChange = { viewModel.onExpirationDateChanged(it) }, // Update ViewModel
          label = { Text(stringResource(R.string.license_expiration_date)) },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.CalendarToday,
              contentDescription = stringResource(R.string.select_date)
            )
          },
          enabled = !uiState.licenseNeverExpires && uiState.licenseType != LicenseType.NONE,
          modifier = Modifier.weight(1f),
          singleLine = true,
          shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = stringResource(R.string.never))
        Checkbox(
          checked = uiState.licenseNeverExpires,
          onCheckedChange = { viewModel.onExpirationNeverFlagChanged(it) },
          enabled = uiState.licenseType != LicenseType.NONE
        )
      }
    }
  }
}

@Composable
fun EditProfileNameCard(uiState: EditProfileUiState) {
  Card(
    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally

    ) {
      ProfileImage(uiState.photoUri)
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = uiState.displayName,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )
      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}


@Preview
@Composable
fun EditProfileNameCardPreview() {
  EditProfileNameCard(
    uiState = EditProfileUiState(
      displayName = "John Doe",
      photoUri = "https://lh3.googleusercontent.com/a/ACg8ocKs05V94HHEYWWMl5EvFT2WO6g8tvrtOFpi4AhLRfZNRbRa0SsP=s512".toUri(),
    )
  )
}