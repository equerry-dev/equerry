package dev.equerry.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Brand-fixed Material 3 theme. We deliberately do NOT opt into dynamicColor — the
 * cream/forest-green identity must stay constant across devices.
 */
@Composable
fun EquerryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) EquerryDarkColors else EquerryLightColors,
        typography = EquerryTypography,
        content = content,
    )
}
