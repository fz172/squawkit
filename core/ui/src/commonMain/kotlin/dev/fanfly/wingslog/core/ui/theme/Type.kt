package dev.fanfly.wingslog.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import wingslog.core.ui.generated.resources.Res
import wingslog.core.ui.generated.resources.jetbrains_mono_bold
import wingslog.core.ui.generated.resources.jetbrains_mono_medium
import wingslog.core.ui.generated.resources.space_grotesk_bold
import wingslog.core.ui.generated.resources.space_grotesk_medium
import wingslog.core.ui.generated.resources.space_grotesk_semibold

@Composable
private fun rememberSpaceGroteskFamily(): FontFamily {
    val medium = Font(Res.font.space_grotesk_medium, weight = FontWeight.Medium)
    val semiBold = Font(Res.font.space_grotesk_semibold, weight = FontWeight.SemiBold)
    val bold = Font(Res.font.space_grotesk_bold, weight = FontWeight.Bold)
    return remember(medium, semiBold, bold) { FontFamily(medium, semiBold, bold) }
}

@Composable
private fun rememberJetBrainsMonoFamily(): FontFamily {
    val medium = Font(Res.font.jetbrains_mono_medium, weight = FontWeight.Medium)
    val bold = Font(Res.font.jetbrains_mono_bold, weight = FontWeight.Bold)
    return remember(medium, bold) { FontFamily(medium, bold) }
}

/**
 * Builds the WingsLog typography system.
 *
 * Headlines and titles use Space Grotesk — a geometric sans that signals precision
 * without coldness, fitting the aviation brand's "trustworthy, modern, professional" tone.
 *
 * Body and label styles use the system sans for readability and native rendering quality
 * in data-dense contexts.
 *
 * Must be called inside a composable to load font resources.
 */
@Composable
fun rememberWingslogTypography(): Typography {
    val spaceGrotesk = rememberSpaceGroteskFamily()
    return Typography(
        headlineLarge = TextStyle(
            fontFamily = spaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = (-0.5).sp  // Tight tracking at large display sizes
        ),
        headlineMedium = TextStyle(
            fontFamily = spaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = (-0.25).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = spaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontFamily = spaceGrotesk,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = spaceGrotesk,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp
        ),
        titleSmall = TextStyle(
            fontFamily = spaceGrotesk,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        // Body and labels: system sans — readability > personality for dense data text
        bodyLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        )
    )
}

/**
 * Custom typography for technical aviation data: tail numbers, serial numbers, tach times.
 *
 * Uses JetBrains Mono — designed specifically for precision data readability.
 * The zero-letterSpacing is intentional: monospace identifiers should not have
 * extra tracking, as character alignment carries semantic meaning.
 *
 * All properties are @Composable to allow font resource loading.
 * Call sites must be inside a composable scope (they already are — these are
 * used as TextStyle arguments inside Text() composables).
 */
object WingslogTypography {
    val dataLarge: TextStyle
        @Composable get() {
            val family = rememberJetBrainsMonoFamily()
            return remember(family) {
                TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    letterSpacing = 0.sp
                )
            }
        }

    val dataMedium: TextStyle
        @Composable get() {
            val family = rememberJetBrainsMonoFamily()
            return remember(family) {
                TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    letterSpacing = 0.sp
                )
            }
        }

    val dataSmall: TextStyle
        @Composable get() {
            val family = rememberJetBrainsMonoFamily()
            return remember(family) {
                TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.sp
                )
            }
        }
}
