package dev.equerry.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Equerry colour roles — derived from the "Field Guide" web palette.
 * Brand stays fixed across devices: we deliberately do NOT opt into dynamicColor.
 * Both schemes meet WCAG AA for body text on their surfaces.
 */

val EquerryLightColors = lightColorScheme(
    primary              = Color(0xFF3A7D5C),
    onPrimary            = Color(0xFFFFFFFF),
    primaryContainer     = Color(0xFFCFE6D8),
    onPrimaryContainer   = Color(0xFF0C3322),
    secondary            = Color(0xFF4F6354),
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = Color(0xFFEAF1EA),
    onSecondaryContainer = Color(0xFF2A6347),
    background           = Color(0xFFF5F3EC),
    onBackground         = Color(0xFF20211C),
    surface              = Color(0xFFFBFAF5),
    onSurface            = Color(0xFF20211C),
    surfaceVariant       = Color(0xFFE2E5DC),
    onSurfaceVariant     = Color(0xFF736E60),
    surfaceContainer     = Color(0xFFF1EEE4),
    surfaceContainerHigh = Color(0xFFECE8DC),
    surfaceBright        = Color(0xFFFFFFFF),
    outline              = Color(0xFF8B8576),
    outlineVariant       = Color(0xFFE8E3D7),
    error                = Color(0xFFB0452A),
    onError              = Color(0xFFFFFFFF),
    errorContainer       = Color(0xFFF6DCD4),
    onErrorContainer     = Color(0xFF5A1B0E),
    scrim                = Color(0xFF14120C),
)

val EquerryDarkColors = darkColorScheme(
    primary              = Color(0xFF62B98C),
    onPrimary            = Color(0xFF0A150F),
    primaryContainer     = Color(0xFF1B3A2A),
    onPrimaryContainer   = Color(0xFF86D2AC),
    secondary            = Color(0xFFB6CCBC),
    onSecondary          = Color(0xFF21352A),
    secondaryContainer   = Color(0xFF1B2A20),
    onSecondaryContainer = Color(0xFF86D2AC),
    background           = Color(0xFF141611),
    onBackground         = Color(0xFFF1EFE7),
    surface              = Color(0xFF1C1F18),
    onSurface            = Color(0xFFF1EFE7),
    surfaceVariant       = Color(0xFF43483F),
    onSurfaceVariant     = Color(0xFF9C978A),
    surfaceContainer     = Color(0xFF232720),
    surfaceContainerHigh = Color(0xFF2B2F26),
    surfaceBright        = Color(0xFF2B2F26),
    outline              = Color(0xFF736E60),
    outlineVariant       = Color(0xFF2D3128),
    error                = Color(0xFFE0795C),
    onError              = Color(0xFF3A1206),
    errorContainer       = Color(0xFF5A1B0E),
    onErrorContainer     = Color(0xFFF6DCD4),
    scrim                = Color(0xFF000000),
)

/** Per-provider-type accent swatches (used by TypeBadge). Light/dark pairs. */
object ProviderHue {
    val OllamaLight     = Color(0xFF3A7D5C); val OllamaDark     = Color(0xFF62B98C)
    val AnthropicLight  = Color(0xFFC07A33); val AnthropicDark  = Color(0xFFD6A559)
    val OpenAiLight     = Color(0xFF2A6347); val OpenAiDark     = Color(0xFF86D2AC)
    val OpenRouterLight = Color(0xFF6470C8); val OpenRouterDark = Color(0xFF8C98E0)
}
