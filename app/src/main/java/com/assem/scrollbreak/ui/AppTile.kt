package com.assem.scrollbreak.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.assem.scrollbreak.R
import com.assem.scrollbreak.ui.theme.SbOn

/**
 * Simple outline glyph per watched app, drawn for ScrollBreak (not the apps' own logos), so it
 * shows whether or not the app is installed. Null for an imported rule with no glyph.
 */
@DrawableRes
fun appGlyph(app: String): Int? = when (app) {
    "browsers" -> R.drawable.ic_app_browser
    "facebook" -> R.drawable.ic_app_facebook
    "instagram" -> R.drawable.ic_app_instagram
    "snapchat" -> R.drawable.ic_app_snapchat
    "tiktok" -> R.drawable.ic_app_tiktok
    "youtube" -> R.drawable.ic_app_youtube
    else -> null
}

/**
 * One watched app: its outline glyph, bright when blocking is on for it and dim when off.
 * The whole tile is the switch; it springs and gives a tick when toggled.
 */
@Composable
fun AppTile(
    name: String,
    @DrawableRes glyph: Int?,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(if (on) scheme.surfaceContainerHigh else scheme.surfaceContainer, label = "tile")
    val border by animateColorAsState(if (on) scheme.onSurface else scheme.surfaceContainer, label = "border")
    val tint by animateColorAsState(if (on) scheme.onSurface else scheme.outline.copy(alpha = 0.55f), label = "tint")
    val lift by animateFloatAsState(
        if (on) 1f else 0.94f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "lift",
    )

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
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            if (glyph != null) {
                Icon(painterResource(glyph), contentDescription = null, modifier = Modifier.size(40.dp), tint = tint)
            } else {
                Box(Modifier.size(40.dp).clip(CircleShape).border(1.7.dp, tint, CircleShape), Alignment.Center) {
                    Text(name.take(1), style = MaterialTheme.typography.titleMedium, color = tint)
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
                .background(if (on) SbOn else scheme.outline.copy(alpha = 0.35f)),
        )
    }
}
