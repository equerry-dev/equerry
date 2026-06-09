package dev.equerry.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = EquerryGold,
    secondary = EquerrySlate,
    background = EquerryNavy,
)

private val LightColors = lightColorScheme(
    primary = EquerryNavy,
    secondary = EquerrySlate,
    background = EquerryMist,
)

@Composable
fun EquerryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = EquerryTypography,
        content = content,
    )
}
