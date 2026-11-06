package com.assem.scrollbreak.ui.theme

import androidx.compose.ui.graphics.Color

// Brand: black, white and one accent that marks "the break" only.
val SbBlack = Color(0xFF000000)
val SbWhite = Color(0xFFFFFFFF)
val SbAccent = Color(0xFFFF4A1C)

val SbInk = Color(0xFF111111)
val SbSurfaceDark = Color(0xFF141414)
val SbSurfaceHighDark = Color(0xFF1F1F1F)
val SbMutedDark = Color(0xFFA3A3A3)
val SbSurfaceLight = Color(0xFFF4F4F4)
val SbSurfaceHighLight = Color(0xFFE9E9E9)
val SbMutedLight = Color(0xFF5C5C5C)

// "On" state of a per-app tile.
val SbOn = Color(0xFF2ECC71)

// Day/night theme toggle. The day sky is a pale tint of the accent (dawn rather than blue, so the
// one brand colour stays the only warm hue); the sun is the accent itself. Night is the brand black
// with a pale-grey moon, white stars and white clouds.
val SbSkyDay = Color(0xFFFFD6C7)
val SbSkyNight = SbBlack
val SbSun = SbAccent
val SbMoon = SbSurfaceHighLight
val SbMoonCrater = Color(0xFFBDBDBD)
val SbStar = SbWhite
val SbCloud = SbWhite
