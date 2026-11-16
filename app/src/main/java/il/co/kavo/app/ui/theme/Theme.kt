package il.co.kavo.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = KavoBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = KavoBlueDeep,
    secondary = KavoCyan,
    background = LightBg,
    onBackground = LightOnBg,
    surface = LightSurface,
    onSurface = LightOnBg,
    surfaceVariant = LightSurfaceAlt,
    onSurfaceVariant = LightOnSurfaceMuted,
    outline = LightOutline,
    outlineVariant = LightOutline
)

private val DarkColors = darkColorScheme(
    primary = KavoCyan,
    onPrimary = Color(0xFF042038),
    primaryContainer = KavoBlueDeep,
    onPrimaryContainer = Color.White,
    secondary = KavoBlue,
    background = DarkBg,
    onBackground = DarkOnBg,
    surface = DarkSurface,
    onSurface = DarkOnBg,
    surfaceVariant = DarkSurfaceAlt,
    onSurfaceVariant = DarkOnSurfaceMuted,
    outline = DarkOutline,
    outlineVariant = DarkOutline
)

val KavoShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun KavoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val ctl = WindowCompat.getInsetsController(window, view)
            ctl.isAppearanceLightStatusBars = !darkTheme
            ctl.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        shapes = KavoShapes,
        content = content
    )
}
