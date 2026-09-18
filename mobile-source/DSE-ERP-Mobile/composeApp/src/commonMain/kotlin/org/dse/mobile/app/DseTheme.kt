package org.dse.mobile.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Jasvi Industries premium mobile visual authority. Purple is brand/action; semantic colours remain state-only.
internal val DsePurple = DseViolet
internal val DsePurpleStrong = Color(0xFF5B21B6)
internal val DsePurpleSoft = Color(0xFFF3E8FF)
internal val DseSuccess = Color(0xFF0F9F6E)
internal val DseWarning = Color(0xFFF59E0B)
internal val DseDanger = Color(0xFFEF4444)
internal val DseInfo = Color(0xFF3B82F6)

private val DseLightColors = lightColorScheme(
    primary = DseViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0E7FF),
    onPrimaryContainer = Color(0xFF3B0764),
    secondary = DseIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEF2FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = DseSuccess,
    onTertiary = Color.White,
    background = DseCanvas,
    onBackground = DseInk,
    surface = Color.White,
    onSurface = DseInk,
    surfaceVariant = Color(0xFFF5F3FA),
    onSurfaceVariant = DseMuted,
    outline = Color(0xFFE2DDEC),
    outlineVariant = Color(0xFFF0ECF6),
    error = DseDanger,
    onError = Color.White,
    errorContainer = Color(0xFFFFE8E8),
    onErrorContainer = Color(0xFF8F1D1D),
)

private val DseDarkColors = darkColorScheme(
    primary = Color(0xFFC4B5FD),
    onPrimary = Color(0xFF2E1065),
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFF5F3FF),
    secondary = Color(0xFFA5B4FC),
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF252342),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = Color(0xFF6EE7B7),
    background = Color(0xFF0B0B14),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF151522),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF201D2D),
    onSurfaceVariant = Color(0xFFCAC4D8),
    outline = Color(0xFF4B4659),
    outlineVariant = Color(0xFF292536),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5B1E26),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val DseShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)


internal object MobileThemeState {
    var dark by mutableStateOf(platformLoadThemeMode().equals("DARK", true))
    fun setDark(value:Boolean){ dark=value; platformSaveThemeMode(if(value)"DARK" else "LIGHT") }
}

private val DseTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8f).sp),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6f).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4f).sp),
    headlineSmall = TextStyle(fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2f).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun DseErpTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    // Theme selection is persisted on the Android device and supplied by MobileThemeState.
    MaterialTheme(
        colorScheme = if (darkTheme) DseDarkColors else DseLightColors,
        typography = DseTypography,
        shapes = DseShapes,
        content = content,
    )
}
