package dev.fanfly.wingslog.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import wingslog.core.ui.theme.generated.resources.Res
import wingslog.core.ui.theme.generated.resources.space_grotesk_bold
import wingslog.core.ui.theme.generated.resources.space_grotesk_medium
import wingslog.core.ui.theme.generated.resources.space_grotesk_semibold

@Composable
actual fun rememberBrandHeadlineFamily(): FontFamily {
  val medium = Font(
    Res.font.space_grotesk_medium, weight = FontWeight.Medium
  )
  val semiBold =
    Font(Res.font.space_grotesk_semibold, weight = FontWeight.SemiBold)
  val bold = Font(Res.font.space_grotesk_bold, weight = FontWeight.Bold)
  return remember(medium, semiBold, bold) { FontFamily(medium, semiBold, bold) }
}
