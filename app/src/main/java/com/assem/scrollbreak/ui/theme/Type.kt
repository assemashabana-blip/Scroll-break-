package com.assem.scrollbreak.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.assem.scrollbreak.R

/** Brand typeface (SIL Open Font License), a variable font so one file covers every weight. */
@OptIn(ExperimentalTextApi::class)
val SpaceGrotesk = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(
            R.font.space_grotesk,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)

private val base = Typography()

// Headings and numbers use the brand face; body text stays on the system font for legibility.
val Typography = base.copy(
    headlineLarge = base.headlineLarge.copy(fontFamily = SpaceGrotesk),
    headlineMedium = base.headlineMedium.copy(fontFamily = SpaceGrotesk),
    headlineSmall = base.headlineSmall.copy(fontFamily = SpaceGrotesk),
    titleLarge = base.titleLarge.copy(fontFamily = SpaceGrotesk),
    titleMedium = base.titleMedium.copy(fontFamily = SpaceGrotesk),
    labelMedium = base.labelMedium.copy(fontFamily = SpaceGrotesk),
)
