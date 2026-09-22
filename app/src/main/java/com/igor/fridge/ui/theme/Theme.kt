package com.igor.fridge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    secondary = TealSecondary,
    error = ExpiredRed,
)

private val DarkColors = darkColorScheme(
    primary = GreenPrimaryDark,
    secondary = TealSecondaryDark,
    error = ExpiredRedDark,
)

/** Colori degli stati di scadenza, coerenti fra tema chiaro e scuro. */
data class ExpiryColors(
    val expired: Color,
    val warning: Color,
    val fresh: Color,
)

@Composable
fun expiryColors(darkTheme: Boolean = isSystemInDarkTheme()): ExpiryColors =
    if (darkTheme) {
        ExpiryColors(expired = ExpiredRedDark, warning = WarningAmberDark, fresh = FreshGreenDark)
    } else {
        ExpiryColors(expired = ExpiredRed, warning = WarningAmber, fresh = FreshGreen)
    }

@Composable
fun IgorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // I colori dinamici (Material You) sono disponibili da Android 12.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = IgorTypography,
        content = content,
    )
}
