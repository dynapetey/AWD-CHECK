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

private val GitHubColorScheme = darkColorScheme(
  primary = GitHubPrimary,
  onPrimary = GitHubOnPrimary,
  secondary = GitHubSecondary,
  onSecondary = GitHubOnSurface,
  secondaryContainer = GitHubSecondaryContainer,
  onSecondaryContainer = GitHubOnSecondaryContainer,
  background = GitHubBackground,
  onBackground = GitHubOnBackground,
  surface = GitHubSurface,
  onSurface = GitHubOnSurface,
  surfaceVariant = GitHubSurfaceVariant,
  onSurfaceVariant = GitHubOnSurfaceVariant,
  outline = GitHubOutline
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
  darkTheme: Boolean = true, // Force Dark Theme
  dynamicColor: Boolean = false, // Disable dynamic colors to enforce custom palette
  content: @Composable () -> Unit,
) {
  val colorScheme = GitHubColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
