package cash.z.ecc.android.ui.compose

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * DragonX wallet design system.
 *
 * A single dark, modern Material3 theme used by every Compose screen. The DragonX brand leans on a
 * deep navy background with an emerald/teal accent (the dragon mark is green). Screens are hosted
 * inside the existing Fragments via ComposeView, so this theme is applied per-screen by wrapping the
 * content in [DragonXTheme].
 */

// Brand palette ---------------------------------------------------------------------------------
val DragonXGreen = Color(0xFF1FD2A4)      // primary accent (send / call to action)
val DragonXGreenDark = Color(0xFF12A07E)
val DragonXBlue = Color(0xFF4C8DFF)       // secondary accent (receive)
val DragonXPurple = Color(0xFF8B7BFF)     // tertiary accent

val BgDeep = Color(0xFF0B0E1A)            // app background (top of gradient)
val BgDeep2 = Color(0xFF11152A)           // app background (bottom of gradient)
val SurfaceCard = Color(0xFF181C30)       // card surface
val SurfaceCard2 = Color(0xFF1F2440)      // elevated / inner surface
val StrokeSubtle = Color(0xFF2A2F4A)      // hairline borders

val TextPrimary = Color(0xFFF2F4FF)
val TextSecondary = Color(0xFF9AA1C4)
val TextDim = Color(0xFF6B7299)

val PositiveGreen = Color(0xFF2BD98A)     // incoming amount
val NegativeRed = Color(0xFFFF6E7C)       // outgoing amount
val WarnAmber = Color(0xFFF5B642)

private val DragonXColors = darkColorScheme(
    primary = DragonXGreen,
    onPrimary = Color(0xFF04150F),
    secondary = DragonXBlue,
    onSecondary = Color(0xFF071226),
    tertiary = DragonXPurple,
    background = BgDeep,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard2,
    onSurfaceVariant = TextSecondary,
    outline = StrokeSubtle,
    error = NegativeRed,
)

private val DragonXShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val DragonXTypography = Typography(
    headlineLarge = Typography().headlineLarge.copy(fontSize = 34.sp),
    titleLarge = Typography().titleLarge.copy(fontSize = 20.sp),
    bodyLarge = Typography().bodyLarge.copy(fontSize = 16.sp),
    bodyMedium = Typography().bodyMedium.copy(fontSize = 14.sp),
    labelLarge = Typography().labelLarge.copy(fontSize = 14.sp),
)

@Composable
fun DragonXTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DragonXColors,
        shapes = DragonXShapes,
        typography = DragonXTypography,
        content = content,
    )
}
