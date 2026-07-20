package com.example.pizzascanner.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class TemaMode { CALDO, SISTEMA, SCURO_FREDDO }

// --- Palette CALDA CHIARA (default) ---
private val WarmLight = lightColorScheme(
    primary = Color(0xFFB5651D),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF8C6A4A),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFBF4EA),
    onBackground = Color(0xFF3D3027),
    surface = Color(0xFFFBF4EA),
    onSurface = Color(0xFF3D3027),
    surfaceVariant = Color(0xFFEDE0CF),
    onSurfaceVariant = Color(0xFF6F5F50),
    surfaceContainerLow = Color(0xFFF5EAD9),
    surfaceContainer = Color(0xFFF0E4D2),
    surfaceContainerHigh = Color(0xFFEBDDC8),
    outline = Color(0xFFA18A73),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

// --- Palette CALDA SCURA (per "adatta al sistema" di notte) ---
private val WarmDark = darkColorScheme(
    primary = Color(0xFFE5A35A),
    onPrimary = Color(0xFF3A2410),
    secondary = Color(0xFFD8B48C),
    onSecondary = Color(0xFF3A2410),
    background = Color(0xFF211A14),
    onBackground = Color(0xFFECE0D2),
    surface = Color(0xFF211A14),
    onSurface = Color(0xFFECE0D2),
    surfaceVariant = Color(0xFF4A3F34),
    onSurfaceVariant = Color(0xFFC9B8A6),
    surfaceContainerLow = Color(0xFF29211A),
    surfaceContainer = Color(0xFF2E251D),
    surfaceContainerHigh = Color(0xFF392E25),
    outline = Color(0xFF8C7B6A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// --- Palette SCURA FREDDA (scuro classico) ---
private val ColdDark = darkColorScheme(
    primary = Color(0xFF9FCAFF),
    onPrimary = Color(0xFF00315B),
    secondary = Color(0xFFB9C7DA),
    onSecondary = Color(0xFF24323F),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE4E2E6),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE4E2E6),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceContainerLow = Color(0xFF1B1B1F),
    surfaceContainer = Color(0xFF1E1E22),
    surfaceContainerHigh = Color(0xFF29292F),
    outline = Color(0xFF8E9099),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun AppTheme(mode: TemaMode, content: @Composable () -> Unit) {
    val scheme = when (mode) {
        TemaMode.CALDO -> WarmLight
        TemaMode.SCURO_FREDDO -> ColdDark
        TemaMode.SISTEMA -> if (isSystemInDarkTheme()) WarmDark else WarmLight
    }
    MaterialTheme(colorScheme = scheme, content = content)
}