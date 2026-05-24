package dev.fanfly.wingslog.web

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.AviationBlue10
import dev.fanfly.wingslog.core.ui.theme.AviationBlue80
import dev.fanfly.wingslog.core.ui.theme.rememberBrandHeadlineFamily
import dev.fanfly.wingslog.web.generated.resources.Res
import dev.fanfly.wingslog.web.generated.resources.continue_without_account
import dev.fanfly.wingslog.web.generated.resources.ic_google_rd_na
import dev.fanfly.wingslog.web.generated.resources.legal_disclaimer
import dev.fanfly.wingslog.web.generated.resources.login_prompt
import dev.fanfly.wingslog.web.generated.resources.mission_statement
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val LoginBackground = AviationBlue10
private val LoginOnBackground = Color(0xFFF0F4FF)
private val LoginOnBackgroundMuted = Color(0xFF8AAAD4)
private val DialogBackground = Color(0xFF071840)

@Composable
fun LoginScreen() {
    var showAccountPicker by remember { mutableStateOf(false) }
    val headlineFamily = rememberBrandHeadlineFamily()

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

    Box(
        modifier = Modifier.fillMaxSize().background(LoginBackground),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2A6BC9).copy(alpha = 0.20f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width / 2f, size.height * 0.30f),
                    radius = size.width * 0.70f,
                ),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.height(76.dp))

            Box(
                modifier = Modifier.fillMaxWidth().height(240.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Flight,
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .offset(y = bobY.dp)
                        .rotate(bobRotation - 45f),
                    tint = AviationBlue80,
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = buildAnnotatedString {
                    append("hopply")
                    withStyle(SpanStyle(color = AviationBlue80)) { append(".") }
                },
                style = TextStyle(
                    fontFamily = headlineFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp,
                    lineHeight = 46.sp,
                    letterSpacing = (-1).sp,
                    color = LoginOnBackground,
                ),
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(Res.string.mission_statement),
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = LoginOnBackgroundMuted,
                ),
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = stringResource(Res.string.login_prompt),
                style = TextStyle(
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    color = LoginOnBackgroundMuted.copy(alpha = 0.7f),
                ),
            )

            Spacer(Modifier.height(24.dp))

            Button(
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LoginOnBackground,
                    contentColor = LoginBackground,
                ),
                onClick = { showAccountPicker = true },
            ) {
                Text(
                    text = "Sign in",
                    style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = LoginOnBackgroundMuted,
                ),
                border = BorderStroke(1.dp, LoginOnBackgroundMuted.copy(alpha = 0.4f)),
                onClick = {},
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(Res.string.continue_without_account),
                        style = TextStyle(fontSize = 15.sp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.legal_disclaimer),
                color = LoginOnBackgroundMuted.copy(alpha = 0.6f),
                style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showAccountPicker) {
        AccountPickerDialog(onDismiss = { showAccountPicker = false })
    }
}

@Composable
private fun AccountPickerDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBackground,
        titleContentColor = LoginOnBackground,
        title = {
            Text(
                text = "Sign in to Hopply",
                style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF001849),
                    ),
                    border = BorderStroke(1.dp, Color.White),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_google_rd_na),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Continue with Google",
                            style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                        )
                    }
                }

                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = "Continue with Apple",
                        style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = LoginOnBackgroundMuted)
            }
        },
    )
}
