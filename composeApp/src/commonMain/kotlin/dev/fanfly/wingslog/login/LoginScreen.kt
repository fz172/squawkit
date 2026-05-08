package dev.fanfly.wingslog.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.AviationBlue10
import dev.fanfly.wingslog.core.ui.theme.AviationBlue30
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.login.data.LoginViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.composeapp.generated.resources.Res
import wingslog.composeapp.generated.resources.continue_without_account
import wingslog.composeapp.generated.resources.google_logo
import wingslog.composeapp.generated.resources.ic_google_rd_na
import wingslog.composeapp.generated.resources.ic_launcher_foreground
import wingslog.composeapp.generated.resources.login_prompt
import wingslog.composeapp.generated.resources.mission_statement
import wingslog.composeapp.generated.resources.sign_in_anonymous_error
import wingslog.composeapp.generated.resources.sign_in_error
import wingslog.composeapp.generated.resources.sign_in_with_google
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.app_name

// Deep navy derived from the AviationBlue10 palette token
private val LoginBackground = AviationBlue10          // #001849
private val LoginSurface = AviationBlue30             // #004785 — subtle card lift
private val LoginOnBackground = Color(0xFFF0F4FF)     // near-white with a blue tint
private val LoginOnBackgroundMuted = Color(0xFF8AAAD4) // muted sky — secondary text
private val LoginErrorText = Color(0xFFFF8A80)        // light red readable on dark navy

// Typography for the branded dark login canvas.
// These display-level sizes are intentionally larger than the in-app type scale.
private val LoginHeroStyle = TextStyle(
  fontWeight = FontWeight.Bold,
  fontSize = 40.sp,
  lineHeight = 44.sp,
)
private val LoginSubtitleStyle = TextStyle(
  fontSize = 18.sp,
  lineHeight = 24.sp,
)
private val LoginPromptStyle = TextStyle(
  fontSize = 14.sp,
  lineHeight = 20.sp,
)
private val LoginButtonLabelStyle = TextStyle(
  fontWeight = FontWeight.SemiBold,
  fontSize = 15.sp,
)
private val LoginSecondaryLabelStyle = TextStyle(fontSize = 15.sp)
private val LoginErrorStyle = TextStyle(fontSize = 13.sp)

@Composable
fun LoginScreen(
  loginViewModel: LoginViewModel = koinViewModel(),
  onLoginSuccess: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  var error by remember { mutableStateOf<String?>(null) }
  var isSigningIn by remember { mutableStateOf(false) }
  val signInErrorMessage = stringResource(Res.string.sign_in_error)
  val signInAnonymousErrorMessage = stringResource(Res.string.sign_in_anonymous_error)

  // Try silent sign-in first
  LaunchedEffect(Unit) {
    scope.launch {
      val credential = loginViewModel.silentLogin()
      if (credential != null) {
        onLoginSuccess()
      }
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(LoginBackground)
  ) {
    // Subtle watermark — large app icon desaturated in the background
    Icon(
      painter = painterResource(Res.drawable.ic_launcher_foreground),
      contentDescription = null,
      modifier = Modifier
        .size(380.dp)
        .align(Alignment.TopEnd)
        .padding(top = Spacing.massive),
      tint = LoginSurface,
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = Spacing.huge),
      verticalArrangement = Arrangement.Bottom,
    ) {

      Spacer(Modifier.height(80.dp))

      Text(
        text = stringResource(CoreRes.string.app_name),
        color = LoginOnBackground,
        style = LoginHeroStyle,
      )

      Spacer(Modifier.height(6.dp))

      Text(
        text = stringResource(Res.string.mission_statement),
        color = LoginOnBackground,
        style = LoginSubtitleStyle,
      )

      Spacer(Modifier.height(Spacing.huge))

      Text(
        text = stringResource(Res.string.login_prompt),
        color = LoginOnBackgroundMuted,
        style = LoginPromptStyle,
      )

      Spacer(Modifier.height(Spacing.extraLarge))

      // --- Primary CTA: Google sign-in ---
      Button(
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp),
        enabled = !isSigningIn,
        shape = RoundedCornerShape(Spacing.buttonCornerRadius),
        colors = ButtonDefaults.buttonColors(
          containerColor = LoginOnBackground,
          contentColor = LoginBackground,
          disabledContainerColor = LoginOnBackground.copy(alpha = 0.4f),
          disabledContentColor = LoginBackground.copy(alpha = 0.4f),
        ),
        onClick = {
          scope.launch {
            isSigningIn = true
            try {
              val credential = loginViewModel.login()
              if (credential != null) {
                onLoginSuccess()
              } else {
                error = signInErrorMessage
              }
            } finally {
              isSigningIn = false
            }
          }
        }
      ) {
        if (isSigningIn) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = Spacing.tiny,
            color = LoginBackground,
          )
        } else {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              painter = painterResource(Res.drawable.ic_google_rd_na),
              contentDescription = stringResource(Res.string.google_logo),
              modifier = Modifier.size(20.dp),
              tint = Color.Unspecified,
            )
            Text(
              text = stringResource(Res.string.sign_in_with_google),
              style = LoginButtonLabelStyle,
            )
          }
        }
      }

      Spacer(Modifier.height(Spacing.medium))

      // --- Secondary: anonymous / guest ---
      OutlinedButton(
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp),
        enabled = !isSigningIn,
        shape = RoundedCornerShape(Spacing.buttonCornerRadius),
        colors = ButtonDefaults.outlinedButtonColors(
          contentColor = LoginOnBackgroundMuted,
        ),
        border = BorderStroke(
          1.dp,
          LoginOnBackgroundMuted.copy(alpha = 0.4f)
        ),
        onClick = {
          scope.launch {
            isSigningIn = true
            try {
              val credential = loginViewModel.loginAnonymously()
              if (credential != null) {
                onLoginSuccess()
              } else {
                error = signInAnonymousErrorMessage
              }
            } finally {
              isSigningIn = false
            }
          }
        }
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
          )
          Text(
            text = stringResource(Res.string.continue_without_account),
            style = LoginSecondaryLabelStyle,
          )
        }
      }

      Spacer(Modifier.height(Spacing.massive))

      error?.let {
        Text(
          text = it,
          color = LoginErrorText,
          style = LoginErrorStyle,
          modifier = Modifier.padding(bottom = Spacing.large)
        )
      }
    }
  }
}
