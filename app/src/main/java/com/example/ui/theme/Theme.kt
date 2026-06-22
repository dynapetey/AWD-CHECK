package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val ElegantColorScheme = darkColorScheme(
  primary = ElegantPrimary,
  onPrimary = ElegantOnPrimary,
  secondary = ElegantSecondary,
  onSecondary = ElegantOnPrimary,
  secondaryContainer = ElegantSecondaryContainer,
  onSecondaryContainer = ElegantOnSecondaryContainer,
  background = ElegantBackground,
  onBackground = ElegantOnBackground,
  surface = ElegantSurface,
  onSurface = ElegantOnSurface,
  surfaceVariant = ElegantSurfaceVariant,
  onSurfaceVariant = ElegantOnSurfaceVariant,
  outline = ElegantOutline
)

private val DarkColorScheme =
  darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
  lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force Dark Theme for Elegant Dark
  dynamicColor: Boolean = false, // Disable dynamic colors to enforce custom palette
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) ElegantColorScheme else ElegantColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
