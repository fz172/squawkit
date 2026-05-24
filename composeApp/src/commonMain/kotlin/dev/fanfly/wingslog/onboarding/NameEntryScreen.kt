package dev.fanfly.wingslog.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.AviationBlue10
import dev.fanfly.wingslog.core.ui.theme.AviationBlue80
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.rememberBrandHeadlineFamily
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import wingslog.composeapp.generated.resources.Res
import wingslog.composeapp.generated.resources.onboarding_continue
import wingslog.composeapp.generated.resources.onboarding_name_body
import wingslog.composeapp.generated.resources.onboarding_name_eyebrow
import wingslog.composeapp.generated.resources.onboarding_name_headline
import wingslog.composeapp.generated.resources.onboarding_name_hint
import wingslog.composeapp.generated.resources.onboarding_name_label
import wingslog.core.ui.generated.resources.ic_launcher_foreground
import wingslog.core.ui.generated.resources.Res as UiRes

@Composable
fun NameEntryScreen(
  initialName: String = "",
  onBack: () -> Unit,
  onNext: (name: String) -> Unit,
) {
  var name by remember { mutableStateOf(initialName) }
  val canContinue = name.trim()
    .isNotEmpty()
  val headlineFamily = rememberBrandHeadlineFamily()
  val focusRequester = remember { FocusRequester() }

  val bobTransition = rememberInfiniteTransition(label = "bob")
  val bobY by bobTransition.animateFloat(
    initialValue = 0f,
    targetValue = -6f,
    animationSpec = infiniteRepeatable(
      animation = tween(1700, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "bobY",
  )
  val bobRotation by bobTransition.animateFloat(
    initialValue = -1.5f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1700, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "bobRotation",
  )

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(AviationBlue10),
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            Color(0xFF2A6BC9).copy(alpha = 0.20f),
            Color.Transparent
          ),
          center = Offset(size.width / 2f, size.height * 0.618f),
          radius = size.width * 0.70f,
        ),
      )
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(start = 28.dp, end = 28.dp, top = 70.dp),
    ) {
      IconButton(
        onClick = onBack,
        modifier = Modifier
          .size(40.dp)
          .background(Color.White.copy(alpha = 0.06f), CircleShape)
          .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(18.dp),
        )
      }

      Spacer(Modifier.height(14.dp))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(120.dp),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          painter = painterResource(UiRes.drawable.ic_launcher_foreground),
          contentDescription = null,
          modifier = Modifier
            .size(108.dp)
            .offset(y = bobY.dp)
            .rotate(bobRotation),
          tint = AviationBlue80,
        )
      }

      Spacer(Modifier.height(8.dp))

      Text(
        text = stringResource(Res.string.onboarding_name_eyebrow),
        style = TextStyle(
          fontFamily = FontFamily.SansSerif,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.5.sp,
          color = AviationBlue80,
        ),
      )
      Spacer(Modifier.height(8.dp))

      Text(
        text = stringResource(Res.string.onboarding_name_headline),
        style = TextStyle(
          fontFamily = headlineFamily,
          fontSize = 30.sp,
          fontWeight = FontWeight.Bold,
          lineHeight = 34.sp,
          letterSpacing = (-0.5).sp,
          color = Color.White,
        ),
      )
      Spacer(Modifier.height(10.dp))

      Text(
        text = stringResource(Res.string.onboarding_name_body),
        style = TextStyle(
          fontFamily = FontFamily.SansSerif,
          fontSize = 14.sp,
          lineHeight = 20.sp,
          color = Color.White.copy(alpha = 0.62f),
        ),
      )

      Spacer(Modifier.height(28.dp))

      Text(
        text = stringResource(Res.string.onboarding_name_label),
        style = TextStyle(
          fontFamily = FontFamily.SansSerif,
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 0.8.sp,
          color = Color.White.copy(alpha = 0.42f),
        ),
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
      )

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(
            Color.White.copy(alpha = 0.06f),
            RoundedCornerShape(Spacing.buttonCornerRadius)
          )
          .border(
            1.5.dp,
            if (name.isNotEmpty()) AviationBlue80 else Color.White.copy(alpha = 0.14f),
            RoundedCornerShape(Spacing.buttonCornerRadius),
          )
          .padding(horizontal = Spacing.large),
      ) {
        BasicTextField(
          value = name,
          onValueChange = { if (it.length <= 32) name = it },
          modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
          textStyle = TextStyle(
            fontFamily = headlineFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            letterSpacing = (-0.3).sp,
          ),
          keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
          ),
          keyboardActions = KeyboardActions(
            onDone = { if (canContinue) onNext(name.trim()) },
          ),
          singleLine = true,
          cursorBrush = SolidColor(AviationBlue80),
          decorationBox = { innerTextField ->
            Box(
              modifier = Modifier
                .height(50.dp)
                .fillMaxWidth(),
              contentAlignment = Alignment.CenterStart,
            ) {
              if (name.isEmpty()) {
                Text(
                  text = stringResource(Res.string.onboarding_name_hint),
                  style = TextStyle(
                    fontFamily = headlineFamily,
                    fontSize = 20.sp,
                    color = Color.White.copy(alpha = 0.35f),
                    letterSpacing = (-0.3).sp,
                  ),
                )
              }
              innerTextField()
            }
          },
        )
      }

      Spacer(Modifier.weight(1f))

      Button(
        onClick = { if (canContinue) onNext(name.trim()) },
        enabled = canContinue,
        modifier = Modifier
          .fillMaxWidth()
          .height(Spacing.buttonHeight),
        shape = RoundedCornerShape(Spacing.buttonCornerRadius),
        colors = ButtonDefaults.buttonColors(
          containerColor = AviationBlue80,
          contentColor = AviationBlue10,
          disabledContainerColor = AviationBlue80.copy(alpha = 0.18f),
          disabledContentColor = Color.White.copy(alpha = 0.42f),
        ),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = stringResource(Res.string.onboarding_continue),
            style = TextStyle(
              fontFamily = headlineFamily,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = (-0.2).sp,
            ),
          )
          Spacer(Modifier.width(Spacing.small))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      Spacer(Modifier.height(Spacing.extraLarge))
    }
  }
}
