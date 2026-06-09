package dev.equerry.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.equerry.app.providers.CapabilitySlot

/**
 * One capability slot. Renders three states from [slot.active] + [mappedLabel]:
 *   • active + unmapped → solid card, error "No provider mapped", chevron, clickable
 *   • active + mapped   → primary-ringed card, primary lead tile, "label · model"
 *   • disabled (soon)   → muted card, capability [subtitle], "SOON" pill, not clickable
 */
@Composable
fun SlotRow(
    slot: CapabilitySlot,
    icon: ImageVector,
    mappedLabel: String?,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val isMapped = mappedLabel != null
    val shape = RoundedCornerShape(16.dp)

    val container = if (slot.active) cs.surfaceBright else cs.background
    val border = when {
        !slot.active -> BorderStroke(1.dp, cs.outlineVariant)
        isMapped -> BorderStroke(1.5.dp, cs.primary.copy(alpha = 0.55f))
        else -> BorderStroke(1.dp, cs.outlineVariant)
    }
    val rowModifier = modifier
        .fillMaxWidth()
        .clip(shape)
        .let { if (slot.active) it.clickable(onClick = onClick) else it }

    Surface(color = container, shape = shape, border = border, modifier = rowModifier) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val leadBg = when {
                isMapped -> cs.primary
                slot.active -> cs.secondaryContainer
                else -> cs.surfaceContainer
            }
            val leadFg = when {
                isMapped -> cs.onPrimary
                slot.active -> cs.onSecondaryContainer
                else -> cs.onSurfaceVariant
            }
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(leadBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = leadFg)
            }

            Column(Modifier.weight(1f)) {
                Text(
                    slot.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (slot.active) cs.onSurface else cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(3.dp))
                when {
                    isMapped -> Text(mappedLabel!!, style = MaterialTheme.typography.titleMedium, color = cs.primary)
                    slot.active -> Text("No provider mapped", style = MaterialTheme.typography.bodyMedium, color = cs.error)
                    else -> Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }

            if (!slot.active) SoonPill() else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun SoonPill() {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(cs.surface).padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text("SOON", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
    }
}
