package com.assem.scrollbreak.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.assem.scrollbreak.ui.theme.SbAccent

/** The launcher icon of the first of [packages] that is installed, or null. */
private fun appIcon(context: Context, packages: List<String>): ImageBitmap? {
    for (pkg in packages) {
        try {
            return context.packageManager.getApplicationIcon(pkg).toBitmap(144, 144).asImageBitmap()
        } catch (_: Throwable) {
            // Not installed, or hidden from this app: try the next one.
        }
    }
    return null
}

/**
 * One watched app: its own icon, in colour when blocking is on for it and greyed out when off.
 * The whole tile is the switch; it springs and gives a tick when toggled.
 */
@Composable
fun AppTile(
    name: String,
    packages: List<String>,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val icon = remember(packages) { appIcon(context, packages) }

    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(if (on) scheme.surfaceContainerHigh else scheme.surfaceContainer, label = "tile")
    val border by animateColorAsState(if (on) scheme.onSurface else scheme.surfaceContainer, label = "border")
    val lift by animateFloatAsState(
        if (on) 1f else 0.94f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "lift",
    )
    val iconAlpha by animateFloatAsState(if (on) 1f else 0.4f, label = "alpha")
    val grey = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }

    Column(
        modifier
            .scale(lift)
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .border(2.dp, border, RoundedCornerShape(20.dp))
            .semantics { contentDescription = "$name, blocking ${if (on) "on" else "off"}" }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle(!on)
            }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(52.dp).alpha(iconAlpha), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)),
                    colorFilter = if (on) null else grey,
                )
            } else {
                // Icon not readable (not installed, or an imported rule for another app): initial letter.
                Box(Modifier.size(52.dp).clip(CircleShape).background(scheme.surfaceContainerHigh), Alignment.Center) {
                    Text(name.take(1), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (on) scheme.onSurface else scheme.onSurfaceVariant,
        )
        Box(
            Modifier.size(width = 28.dp, height = 6.dp).clip(CircleShape)
                .background(if (on) SbAccent else scheme.outline.copy(alpha = 0.35f)),
        )
    }
}
