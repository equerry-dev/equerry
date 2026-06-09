package dev.equerry.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.equerry.app.providers.ProviderType
import dev.equerry.app.ui.theme.ProviderHue

/**
 * Compact mono chip naming a provider type. Colour-keyed but never hue-only — a glyph
 * swatch plus the text label keep it legible for colour-blind users.
 */
@Composable
fun TypeBadge(type: ProviderType, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val hue = when (type) {
        ProviderType.OLLAMA -> if (dark) ProviderHue.OllamaDark else ProviderHue.OllamaLight
        ProviderType.ANTHROPIC -> if (dark) ProviderHue.AnthropicDark else ProviderHue.AnthropicLight
        ProviderType.OPENAI_COMPATIBLE -> if (dark) ProviderHue.OpenAiDark else ProviderHue.OpenAiLight
        ProviderType.OPENROUTER -> if (dark) ProviderHue.OpenRouterDark else ProviderHue.OpenRouterLight
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(hue.copy(alpha = if (dark) 0.18f else 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(6.dp).clip(RoundedCornerShape(2.dp)).background(hue),
        )
        Text(
            text = type.displayName.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
