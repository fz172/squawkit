package dev.fanfly.wingslog.core.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun platformColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme? {
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return if (dynamicColor && dynamicColorAvailable) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        null
    }
}
