package com.assem.scrollbreak.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Title bar with a back arrow, shown on every screen except Home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreenBar(title: String, onBack: () -> Unit) {
    val buzz = rememberBuzz()
    TopAppBar(
        title = {
            // Fades between titles when moving from one sub-screen to another.
            AnimatedContent(title, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "title") {
                Text(it, fontWeight = FontWeight.SemiBold)
            }
        },
        navigationIcon = {
            IconButton(onClick = { buzz(Buzz.TICK); onBack() }) {
                Text(
                    "←",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { contentDescription = "Back" },
                )
            }
        },
        // The page already sits below the status bar (Scaffold padding), so the bar adds no inset of its own.
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Small all-caps label above a group of cards. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 4.dp, top = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A tappable row with a chevron, for navigating to another screen. */
@Composable
fun NavRow(label: String, hint: String, onClick: () -> Unit) {
    val buzz = rememberBuzz()
    Row(
        Modifier.fillMaxWidth().clickable { buzz(Buzz.TICK); onClick() }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** App names as people write them; the rule id is lower case. */
fun appDisplayName(app: String): String = when (app) {
    "tiktok" -> "TikTok"
    "youtube" -> "YouTube"
    else -> app.replaceFirstChar { it.uppercase() }
}
