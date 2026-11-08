package com.assem.scrollbreak.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assem.scrollbreak.R
import com.assem.scrollbreak.ui.theme.SbAccent

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
    "com.whatsapp" -> R.drawable.ic_app_whatsapp
    "com.whatsapp.w4b" -> R.drawable.ic_app_whatsapp_business
    "org.telegram.messenger" -> R.drawable.ic_app_telegram
    "com.facebook.orca" -> R.drawable.ic_app_messenger
    "org.thoughtcrime.securesms" -> R.drawable.ic_app_signal
    "com.google.android.gm" -> R.drawable.ic_app_gmail
    else -> null
}

/**
 * Outline glyph by package name, for lists of any app (Time in apps). Same drawing style as
 * [appGlyph]; null when there is none, and the caller draws the first letter in a ring instead.
 */
@DrawableRes
fun packageGlyph(pkg: String): Int? = when (pkg) {
    "com.instagram.android" -> R.drawable.ic_app_instagram
    "com.google.android.youtube" -> R.drawable.ic_app_youtube
    "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> R.drawable.ic_app_tiktok
    "com.facebook.katana", "com.facebook.lite" -> R.drawable.ic_app_facebook
    "com.snapchat.android" -> R.drawable.ic_app_snapchat
    "com.android.chrome", "org.mozilla.firefox", "com.brave.browser", "com.microsoft.emmx",
    "com.sec.android.app.sbrowser", "com.opera.browser" -> R.drawable.ic_app_browser
    "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer" -> R.drawable.ic_app_phone
    "com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage" -> R.drawable.ic_app_clock
    "com.duolingo" -> R.drawable.ic_app_duolingo
    "com.anthropic.claude" -> R.drawable.ic_app_claude
    "com.block.juggle" -> R.drawable.ic_app_blocks
    "com.google.android.apps.maps" -> R.drawable.ic_app_maps
    "com.discord" -> R.drawable.ic_app_discord
    "com.google.android.GoogleCamera", "com.android.camera", "com.sec.android.app.camera" -> R.drawable.ic_app_camera
    "com.google.android.apps.photos" -> R.drawable.ic_app_photos
    "com.twitter.android" -> R.drawable.ic_app_x
    "com.spotify.music" -> R.drawable.ic_app_music
    "com.mmmoussa.iqra" -> R.drawable.ic_app_book
    "com.android.vending" -> R.drawable.ic_app_store
    // Messaging apps (WhatsApp, Telegram...) are already keyed by package in appGlyph.
    else -> appGlyph(pkg)
}

/** The first letter of [name] in a thin ring, for an app with no glyph of its own. */
@Composable
fun LetterGlyph(name: String, tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).border(1.5.dp, tint, CircleShape), Alignment.Center) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.labelSmall, color = tint)
    }
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
    /** Spoken state, for tiles that mean something other than blocking. */
    stateText: (Boolean) -> String = { "blocking ${if (it) "on" else "off"}" },
) {
    val buzz = rememberBuzz()
    val scheme = MaterialTheme.colorScheme
    // Animates on/off only; a theme change swaps the colours at once instead of fading every tile.
    val t by animateFloatAsState(if (on) 1f else 0f, label = "tileOn")
    val container = lerp(scheme.surfaceContainer, scheme.surfaceContainerHigh, t)
    val border = lerp(scheme.surfaceContainer, scheme.onSurface, t)
    val tint = lerp(scheme.outline.copy(alpha = 0.55f), scheme.onSurface, t)
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
            .semantics { contentDescription = "$name, ${stateText(on)}" }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
            ) {
                buzz(Buzz.CLICK)
                onToggle(!on)
            }
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
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
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Box(
            Modifier.size(width = 28.dp, height = 6.dp).clip(CircleShape)
                .background(if (on) SbAccent else scheme.outline.copy(alpha = 0.35f)),
        )
    }
}
