# ScrollBreak — Android icon & brand files

Logo: **Snap, black & white + one accent.** A video card snapped in half. The top half (the one video you're allowed) stays, the next one breaks away, and the accent marks the break.

## Colors

| Name   | Hex       | Use |
|--------|-----------|-----|
| Black  | `#000000` | Icon background, text |
| White  | `#FFFFFF` | Video card, text on dark |
| Accent | `#FF4A1C` | The break only: sparks and "Break" in the wordmark |

Keep the accent to the break moment. Don't use it for the play button, because a red play button reads as YouTube.

## 1. Drop into your project

Copy everything inside `android/res/` into `app/src/main/res/`, merging the folders and replacing any existing `ic_launcher*` files.

| File | What it is |
|---|---|
| `mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | Adaptive icon (Android 8+) |
| `drawable/ic_launcher_foreground.xml` | The mark, sized inside the 66 dp safe zone |
| `drawable/ic_launcher_background.xml` | Solid black layer |
| `drawable/ic_launcher_monochrome.xml` | Themed icon layer (Android 13+) |
| `mipmap-mdpi … mipmap-xxxhdpi/ic_launcher.png`, `ic_launcher_round.png` | PNG fallback for older Android versions |
| `drawable/ic_stat_scrollbreak.xml` | Notification small icon (white silhouette) |
| `values/brand_colors.xml` | `@color/sb_black`, `sb_white`, `sb_accent` |

## 2. Manifest

```xml
<application
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    ... >
```

## 3. Notification icon

ScrollBreak needs a persistent notification so the service stays alive. Use the small icon there:

```kotlin
NotificationCompat.Builder(context, CHANNEL_ID)
    .setSmallIcon(R.drawable.ic_stat_scrollbreak)
    .setColor(ContextCompat.getColor(context, R.color.sb_accent))
    ...
```

Android draws the small icon as a single-colour silhouette, which is why it has no sparks and uses thicker lines.

## 4. Splash screen (optional, Android 12+ API via androidx)

Add the dependency `implementation("androidx.core:core-splashscreen:1.0.1")`, then add this theme to `res/values/themes.xml`:

```xml
<style name="Theme.ScrollBreak.Starting" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">@color/sb_black</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>
    <item name="postSplashScreenTheme">@style/Theme.ScrollBreak</item>
</style>
```

Set `android:theme="@style/Theme.ScrollBreak.Starting"` on your launcher activity, and call `installSplashScreen()` before `super.onCreate()`.

## 5. Store and brand files

- `store/play-store-icon-512.png` — 512×512 store icon (Play applies its own rounded mask)
- `store/feature-graphic-1024x500.png` — Play Store feature graphic
- `brand/svg/` — the icon, the inverted icon (white tile), the mark alone, and the wordmark lockups for light and dark backgrounds, with the text converted to shapes
- `brand/png/` — PNG exports of the above
- `previews/` — how the icon looks under different launcher masks, and in themed mode

Wordmark typeface: Space Grotesk (Medium "Scroll", Bold "Break"), SIL Open Font License.
