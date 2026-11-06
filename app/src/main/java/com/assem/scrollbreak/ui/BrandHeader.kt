package com.assem.scrollbreak.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.assem.scrollbreak.R
import com.assem.scrollbreak.ui.theme.SbAccent

/** The mark on its black tile plus the wordmark: "Scroll" plain, "Break" bold in the accent. */
@Composable
fun BrandHeader(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // The launcher foreground keeps a wide safe-zone margin, so it is enlarged inside its tile.
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(13.dp)).background(Color.Black)) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().scale(1.9f),
            )
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append("Scroll") }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = SbAccent)) { append("Break") }
            },
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}
