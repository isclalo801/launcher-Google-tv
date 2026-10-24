package com.tcl.smartlauncher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ==========================================
// PALETA CYBER OBSIDIAN GLASS (Modo Oscuro Futurista)
// ==========================================
val CyberBgDeep = Color(0xFF080B11)
val CyberBgMesh = Color(0xFF101726)
val CyberCardGlass = Color(0xFF131D30)
val CyberCardGlassFocused = Color(0xFF1C2A44)
val CyberBorderNormal = Color(0xFF2B3A55)
val CyberBorderFocused = Color(0xFF60A5FA)
val CyberTextPrimary = Color(0xFFF8FAFC)
val CyberTextSecondary = Color(0xFF94A3B8)
val CyberTextMuted = Color(0xFF64748B)

// ==========================================
// PALETA PERLA SATINADO (Modo Claro de Alto Contraste)
// ==========================================
val PearlBgStart = Color(0xFFD8E2EE)
val PearlBgEnd = Color(0xFFC5D3E6)
val PearlCardWhite = Color(0xFFFFFFFF)
val PearlCardFocused = Color(0xFFEFF6FF)
val PearlBorderNormal = Color(0xFF94A3B8)
val PearlBorderFocused = Color(0xFF1D4ED8)
val PearlTextPrimary = Color(0xFF0F172A)
val PearlTextSecondary = Color(0xFF334155)
val PearlTextMuted = Color(0xFF64748B)

// Acentos Comunes y Estado
val AccentElectricBlue = Color(0xFF2563EB)
val AccentCyan = Color(0xFF06B6D4)
val AccentPurple = Color(0xFF8B5CF6)
val StatusGreen = Color(0xFF10B981)
val StatusOrange = Color(0xFFF59E0B)

// Nombres de compatibilidad directa
val PrimaryIndigo = AccentElectricBlue
val PrimaryDark = Color(0xFF1D4ED8)
val CardWhite = Color(0xFFFFFFFF)
val TextCharcoal = Color(0xFF0F172A)
val TextSlate = Color(0xFF334155)
val TextMuted = Color(0xFF64748B)
val BgLight = Color(0xFFD8E2EE)
val BgLightSubtle = Color(0xFFE2E8F0)
val BorderNormal = Color(0xFF94A3B8)
val BorderFocused = Color(0xFF1D4ED8)

// Gradientes de Marcas Cinematográficas
val BrandNetflixStart = Color(0xFF7F0909)
val BrandNetflixEnd = Color(0xFFE50914)

val BrandPrimeStart = Color(0xFF003E6B)
val BrandPrimeEnd = Color(0xFF00A8E1)

val BrandDisneyStart = Color(0xFF040E31)
val BrandDisneyEnd = Color(0xFF113CCF)

val BrandMaxStart = Color(0xFF000E54)
val BrandMaxEnd = Color(0xFF002BE7)

val BrandYouTubeStart = Color(0xFF8B0000)
val BrandYouTubeEnd = Color(0xFFFF0000)

val BrandVixStart = Color(0xFF942200)
val BrandVixEnd = Color(0xFFFF5722)

val BrandAppleStart = Color(0xFF18181B)
val BrandAppleEnd = Color(0xFF3F3F46)

private val DarkColorScheme = darkColorScheme(
    primary = AccentElectricBlue,
    onPrimary = Color.White,
    secondary = AccentCyan,
    onSecondary = Color.White,
    background = CyberBgDeep,
    onBackground = CyberTextPrimary,
    surface = CyberCardGlass,
    onSurface = CyberTextPrimary,
    surfaceVariant = CyberCardGlassFocused,
    onSurfaceVariant = CyberTextSecondary,
    outline = CyberBorderNormal
)

private val LightColorScheme = lightColorScheme(
    primary = AccentElectricBlue,
    onPrimary = Color.White,
    secondary = PearlBorderFocused,
    onSecondary = Color.White,
    background = PearlBgStart,
    onBackground = PearlTextPrimary,
    surface = PearlCardWhite,
    onSurface = PearlTextPrimary,
    surfaceVariant = PearlCardFocused,
    onSurfaceVariant = PearlTextSecondary,
    outline = PearlBorderNormal
)

@Composable
fun TclLauncherTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}
