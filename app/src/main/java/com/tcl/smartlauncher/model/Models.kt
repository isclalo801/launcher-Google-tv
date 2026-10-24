package com.tcl.smartlauncher.model

import android.graphics.drawable.Drawable

/**
 * Representa una aplicación instalada en el dispositivo Android TV.
 */
data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val iconDrawable: Drawable? = null,
    val isSystemApp: Boolean = false,
    val isHidden: Boolean = false
)

/**
 * Entrada en la fila de "Continuar viendo (Lo último que miré)".
 */
data class LastWatchedEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val packageName: String,
    val deepLinkUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val badgeColorHex: String = "#2563EB",
    val profileIndex: Int = 1
)

/**
 * Acceso directo preconfigurado o personalizado para streaming o contenido.
 */
data class QuickShortcut(
    val id: String,
    val title: String,
    val subtitle: String,
    val appName: String,
    val packageName: String,
    val deepLinkUri: String? = null,
    val accentColorHex: String = "#2563EB",
    val profileIndex: Int = 1,
    val isCustom: Boolean = false
)

/**
 * Perfil de usuario para el launcher y para la automatización de perfiles en streaming.
 */
data class UserProfile(
    val id: String,
    val name: String,
    val avatarEmoji: String = "👤",
    val defaultProfileIndexMap: Map<String, Int> = emptyMap(),
    val autoSkipWhoIsWatching: Boolean = true
)

/**
 * Información de fondo fotográfico / wallpaper para la pantalla principal.
 */
data class WallpaperInfo(
    val id: String,
    val name: String,
    val description: String,
    val imageUrl: String,
    val fallbackGradientColors: List<Long>,
    val isCustom: Boolean = false,
    val customPathOrUrl: String? = null
)
