package dev.fanfly.wingslog.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.fanfly.wingslog.login.data.AuthManager

@Composable
fun SettingsScreen(authManager: AuthManager, navController: NavController) {
  Scaffold(
    topBar = {
      SettingsTopAppBar(onBackClick = { navController.popBackStack() })
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .padding(innerPadding)
        .fillMaxSize()
        .padding(horizontal = 16.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      UserProfileCard(credential = authManager.getCredential())
    }
  }
}