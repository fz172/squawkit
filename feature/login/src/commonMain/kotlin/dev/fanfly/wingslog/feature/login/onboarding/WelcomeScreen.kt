package dev.fanfly.wingslog.feature.login.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.AviationBlue10
import dev.fanfly.wingslog.core.ui.theme.AviationBlue80
import dev.fanfly.wingslog.core.ui.theme.rememberBrandHeadlineFamily
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.onboarding_welcome_aboard
import wingslog.feature.login.generated.resources.onboarding_welcome_tagline
import wingslog.core.ui.generated.resources.ic_launcher_foreground
import wingslog.core.ui.generated.resources.Res as UiRes

@Composable
fun WelcomeScreen(
  name: String,
  onDone: () -> Unit,
) {
  var showText by remember { mutableStateOf(false) }
  val headlineFamily = rememberBrandHeadlineFamily()

  LaunchedEffect(Unit) {
    delay(800)
    showText = true
    delay(2100)
    onDone()
  }

  val textAlpha by animateFloatAsState(
    targetValue = if (showText) 1f else 0f,
    animationSpec = tween(500, easing = FastOutSlowInEasing),
    label = "textAlpha",
  )
  val planeAlpha by animateFloatAsState(
    targetValue = if (showText) 0f else 1f,
    animationSpec = tween(400),
    label = "planeAlpha",
  )

  val waveTransition = rememberInfiniteTransition(label = "wave")
  val waveAngle by waveTransition.animateFloat(
    initialValue = -12f,
    targetValue = 18f,
    animationSpec = infiniteRepeatable(
      animation = tween(600, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "waveAngle",
  )

  val dotTransition = rememberInfiniteTransition(label = "dots")
  val dot0Alpha by dotTransition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(550, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
      initialStartOffset = StartOffset(0),
    ),
    label = "dot0",
  )
  val dot1Alpha by dotTransition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(550, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
      initialStartOffset = StartOffset(150),
    ),
    label = "dot1",
  )
  val dot2Alpha by dotTransition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(550, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
      initialStartOffset = StartOffset(300),
    ),
    label = "dot2",
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(AviationBlue10),
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(Color(0xFF2A6BC9).copy(alpha = 0.20f), Color.Transparent),
          center = Offset(size.width / 2f, size.height * 0.641f),
          radius = size.width * 0.70f,
        ),
      )
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .alpha(planeAlpha),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        painter = painterResource(UiRes.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier.size(150.dp),
        tint = AviationBlue80,
      )
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .alpha(textAlpha)
        .padding(horizontal = 28.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = stringResource(Res.string.onboarding_welcome_aboard),
        style = TextStyle(
          fontFamily = headlineFamily,
          fontWeight = FontWeight.SemiBold,
          fontSize = 17.sp,
          color = AviationBlue80,
          letterSpacing = 0.5.sp,
        ),
      )
      Spacer(Modifier.height(10.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        Text(
          text = name.ifBlank { "aboard" },
          style = TextStyle(
            fontFamily = headlineFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 44.sp,
            lineHeight = 48.sp,
            letterSpacing = (-1).sp,
            color = Color.White,
          ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
          text = "👋",
          style = TextStyle(fontSize = 40.sp),
          modifier = Modifier.rotate(waveAngle),
        )
      }

      Spacer(Modifier.height(14.dp))
      Text(
        text = stringResource(Res.string.onboarding_welcome_tagline),
        style = TextStyle(
          fontFamily = headlineFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 15.sp,
          color = Color.White.copy(alpha = 0.62f),
        ),
      )
    }

    Row(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 64.dp)
        .alpha(textAlpha),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box(
        modifier = Modifier
          .size(6.dp)
          .alpha(dot0Alpha)
          .background(AviationBlue80, CircleShape),
      )
      Box(
        modifier = Modifier
          .size(6.dp)
          .alpha(dot1Alpha)
          .background(AviationBlue80, CircleShape),
      )
      Box(
        modifier = Modifier
          .size(6.dp)
          .alpha(dot2Alpha)
          .background(AviationBlue80, CircleShape),
      )
    }
  }
}
