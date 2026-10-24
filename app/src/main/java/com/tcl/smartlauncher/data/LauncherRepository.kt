package com.tcl.smartlauncher.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tcl.smartlauncher.model.InstalledAppInfo
import com.tcl.smartlauncher.model.LastWatchedEntry
import com.tcl.smartlauncher.model.QuickShortcut
import com.tcl.smartlauncher.model.UserProfile
import com.tcl.smartlauncher.model.WallpaperInfo
import com.tcl.smartlauncher.service.LauncherAccessibilityService
import java.io.File
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

/**
 * Repositorio central de datos, configuraciones, wallpapers y operaciones del Launcher.
 */
class LauncherRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tcl_launcher_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_LAST_WATCHED = "key_last_watched"
        private const val KEY_CUSTOM_SHORTCUTS = "key_custom_shortcuts"
        private const val KEY_HIDDEN_PACKAGES = "key_hidden_packages"
        private const val KEY_ACTIVE_PROFILE_ID = "key_active_profile_id"
        private const val KEY_PROFILES = "key_profiles"
        private const val KEY_HOME_INTERCEPTION = "key_home_interception"
        private const val KEY_ACTIVE_WALLPAPER_ID = "key_active_wallpaper_id"
        private const val KEY_CUSTOM_WALLPAPER_PATH = "key_custom_wallpaper_path"

        // Paquetes reales de streaming en Android TV
        const val PKG_NETFLIX = "com.netflix.ninja"
        const val PKG_PRIME_VIDEO = "com.amazon.amazonvideo.livingroom"
        const val PKG_DISNEY_PLUS = "com.disney.disneyplus"
        const val PKG_MAX = "com.wbd.stream"
        const val PKG_YOUTUBE = "com.google.android.youtube.tv"
        const val PKG_VIX = "com.univision.prendetv"
        const val PKG_VIX_ALT = "com.televisaunivision.vix"
        const val PKG_APPLE_TV = "com.apple.atve.androidtv.appletv"
    }

    // ==========================================
    // FONDOS FOTOGRÁFICOS / WALLPAPERS HD
    // ==========================================

    val defaultWallpapers: List<WallpaperInfo> = listOf(
        WallpaperInfo(
            id = "wp_tropical",
            name = "Costa Tropical Turquesa",
            description = "Playa luminosa, aguas turquesa cristalinas y luz de día",
            imageUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1920&q=85",
            fallbackGradientColors = listOf(0xFF0288D1, 0xFF26C6DA, 0xFF00695C)
        ),
        WallpaperInfo(
            id = "wp_alps",
            name = "Alpes Nevados al Amanecer",
            description = "Cumbres alpinas doradas por la primera luz del sol",
            imageUrl = "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=1920&q=85",
            fallbackGradientColors = listOf(0xFFE0E7FF, 0xFF818CF8, 0xFF1E1B4B)
        ),
        WallpaperInfo(
            id = "wp_dunes",
            name = "Dunas Doradas Minimalistas",
            description = "Suaves curvas de arena cálida y sombras serenas",
            imageUrl = "https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?w=1920&q=85",
            fallbackGradientColors = listOf(0xFFFDE68A, 0xFFD97706, 0xFF78350F)
        ),
        WallpaperInfo(
            id = "wp_nordic",
            name = "Lago Nórdico Cristalino",
            description = "Reflejos apacibles entre pinares y cielo diáfano",
            imageUrl = "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=1920&q=85",
            fallbackGradientColors = listOf(0xFFCCFBF1, 0xFF0D9488, 0xFF064E3B)
        ),
        WallpaperInfo(
            id = "wp_sunset",
            name = "Atardecer Violeta Pacífico",
            description = "Horizonte costero con tonos violetas y coral suave",
            imageUrl = "https://images.unsplash.com/photo-1518495973542-4542c06a5843?w=1920&q=85",
            fallbackGradientColors = listOf(0xFFFDE047, 0xFFEC4899, 0xFF4C1D95)
        ),
        WallpaperInfo(
            id = "wp_custom",
            name = "Mis Fotos / Galería Personal",
            description = "Foto propia subida desde el celular o memoria interna",
            imageUrl = "",
            fallbackGradientColors = listOf(0xFF334155, 0xFF1E293B, 0xFF0F172A),
            isCustom = true
        )
    )

    fun getAvailableWallpapers(): List<WallpaperInfo> {
        val customPath = prefs.getString(KEY_CUSTOM_WALLPAPER_PATH, null)
        return defaultWallpapers.map { wp ->
            if (wp.id == "wp_custom") {
                wp.copy(customPathOrUrl = customPath)
            } else {
                wp
            }
        }
    }

    fun getActiveWallpaper(): WallpaperInfo {
        val activeId = prefs.getString(KEY_ACTIVE_WALLPAPER_ID, "wp_tropical") ?: "wp_tropical"
        val customPath = prefs.getString(KEY_CUSTOM_WALLPAPER_PATH, null)
        val list = getAvailableWallpapers()
        val found = list.find { it.id == activeId } ?: list.first()
        return if (found.id == "wp_custom" && !customPath.isNullOrBlank()) {
            found.copy(customPathOrUrl = customPath)
        } else {
            found
        }
    }

    fun setActiveWallpaper(wallpaperId: String, customUriOrPath: String? = null) {
        val editor = prefs.edit()
        editor.putString(KEY_ACTIVE_WALLPAPER_ID, wallpaperId)
        if (customUriOrPath != null) {
            editor.putString(KEY_CUSTOM_WALLPAPER_PATH, customUriOrPath)
        }
        editor.apply()
    }

    fun saveCustomWallpaperFromBase64(base64Data: String): String {
        return try {
            val cleanBase64 = if (base64Data.contains(",")) {
                base64Data.substringAfter(",")
            } else {
                base64Data
            }
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val wallpaperDir = File(context.filesDir, "wallpapers")
            if (!wallpaperDir.exists()) wallpaperDir.mkdirs()

            val targetFile = File(wallpaperDir, "custom_wallpaper_${System.currentTimeMillis()}.jpg")
            FileOutputStream(targetFile).use { fos ->
                fos.write(bytes)
            }
            setActiveWallpaper("wp_custom", targetFile.absolutePath)
            targetFile.absolutePath
        } catch (e: Exception) {
            ""
        }
    }

    // ==========================================
    // IP LOCAL DE LA TV PARA EL CONTROL REMOTO
    // ==========================================

    fun getDeviceIpAddress(): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    (ipInt shr 8) and 0xff,
                    (ipInt shr 16) and 0xff,
                    (ipInt shr 24) and 0xff
                )
            }
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (!host.isNullOrBlank() && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return "192.168.1.100"
    }

    // ==========================================
    // ESTADO DEL BOTÓN HOME
    // ==========================================

    fun isHomeInterceptionEnabled(): Boolean {
        return prefs.getBoolean(KEY_HOME_INTERCEPTION, true)
    }

    fun setHomeInterceptionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HOME_INTERCEPTION, enabled).apply()
        LauncherAccessibilityService.isHomeInterceptionActive = enabled
    }

    // ==========================================
    // GESTIÓN DE PERFILES
    // ==========================================

    fun getUserProfiles(): List<UserProfile> {
        val json = prefs.getString(KEY_PROFILES, null)
        if (json.isNullOrEmpty()) {
            val defaultProfiles = listOf(
                UserProfile(
                    id = "p_papa",
                    name = "Papá",
                    avatarEmoji = "👨",
                    defaultProfileIndexMap = mapOf(
                        PKG_NETFLIX to 1,
                        PKG_PRIME_VIDEO to 1,
                        PKG_DISNEY_PLUS to 1,
                        PKG_MAX to 1,
                        PKG_VIX to 1,
                        PKG_APPLE_TV to 1
                    ),
                    autoSkipWhoIsWatching = true
                ),
                UserProfile(
                    id = "p_mama",
                    name = "Mamá",
                    avatarEmoji = "👩",
                    defaultProfileIndexMap = mapOf(
                        PKG_NETFLIX to 2,
                        PKG_PRIME_VIDEO to 2,
                        PKG_DISNEY_PLUS to 2,
                        PKG_MAX to 2,
                        PKG_VIX to 2,
                        PKG_APPLE_TV to 2
                    ),
                    autoSkipWhoIsWatching = true
                ),
                UserProfile(
                    id = "p_ninos",
                    name = "Niños",
                    avatarEmoji = "🧒",
                    defaultProfileIndexMap = mapOf(
                        PKG_NETFLIX to 3,
                        PKG_PRIME_VIDEO to 3,
                        PKG_DISNEY_PLUS to 3,
                        PKG_MAX to 3,
                        PKG_VIX to 3,
                        PKG_APPLE_TV to 3
                    ),
                    autoSkipWhoIsWatching = true
                )
            )
            saveUserProfiles(defaultProfiles)
            return defaultProfiles
        }
        val type = object : TypeToken<List<UserProfile>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveUserProfiles(profiles: List<UserProfile>) {
        val json = gson.toJson(profiles)
        prefs.edit().putString(KEY_PROFILES, json).apply()
    }

    fun getActiveProfile(): UserProfile {
        val profiles = getUserProfiles()
        val activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, profiles.firstOrNull()?.id ?: "p_papa")
        return profiles.find { it.id == activeId } ?: profiles.firstOrNull() ?: UserProfile("p_papa", "Principal")
    }

    fun setActiveProfile(profileId: String) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, profileId).apply()
    }

    fun updateProfile(updated: UserProfile) {
        val list = getUserProfiles().toMutableList()
        val index = list.indexOfFirst { it.id == updated.id }
        if (index != -1) {
            list[index] = updated
        } else {
            list.add(updated)
        }
        saveUserProfiles(list)
    }

    // ==========================================
    // APPS INSTALADAS
    // ==========================================

    fun getInstalledApps(hideSystemApps: Boolean = false): List<InstalledAppInfo> {
        val pm = context.packageManager
        val hiddenPackages = getHiddenPackages()

        val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val tvApps = pm.queryIntentActivities(tvIntent, 0)
        val tvPackageNames = tvApps.map { it.activityInfo.packageName }.toSet()

        val regularIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val regularApps = pm.queryIntentActivities(regularIntent, 0)

        val combinedResolveInfos = (tvApps + regularApps)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }

        val knownStreamingPackages = setOf(
            PKG_NETFLIX,
            PKG_PRIME_VIDEO,
            PKG_DISNEY_PLUS,
            PKG_MAX,
            PKG_YOUTUBE,
            PKG_VIX,
            PKG_VIX_ALT,
            PKG_APPLE_TV,
            "com.google.android.youtube.tv",
            "com.google.android.youtube.tvunplugged",
            "com.spotify.tv.android",
            "com.spotify.music",
            "tv.twitch.android.app",
            "com.crunchyroll.crunchyroll",
            "com.plexapp.android",
            "com.pluto.tv",
            "com.hulu.livingroomplus",
            "com.paramountplus",
            "com.starz"
        )

        val resultList = mutableListOf<InstalledAppInfo>()

        for (resolveInfo in combinedResolveInfos) {
            val pkg = resolveInfo.activityInfo.packageName
            val label = resolveInfo.loadLabel(pm).toString()
            val icon = resolveInfo.loadIcon(pm)
            val isSystem = try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            } catch (e: Exception) {
                false
            }

            val isHidden = hiddenPackages.contains(pkg)
            if (isHidden) {
                continue
            }

            val hasLeanback = tvPackageNames.contains(pkg)
            val isStreamingOrMedia = knownStreamingPackages.contains(pkg) ||
                pkg.contains("netflix", ignoreCase = true) ||
                pkg.contains("youtube", ignoreCase = true) ||
                pkg.contains("disney", ignoreCase = true) ||
                pkg.contains("primevideo", ignoreCase = true) ||
                pkg.contains("amazonvideo", ignoreCase = true) ||
                pkg.contains("spotify", ignoreCase = true) ||
                pkg.contains("vix", ignoreCase = true) ||
                pkg.contains("paramount", ignoreCase = true) ||
                pkg.contains("media", ignoreCase = true) ||
                pkg.contains("video", ignoreCase = true) ||
                pkg.contains("player", ignoreCase = true)

            if (hideSystemApps && isSystem) {
                if (!isStreamingOrMedia) {
                    if (isTechnicalSystemPackage(pkg) || !hasLeanback) {
                        continue
                    }
                }
            }

            resultList.add(
                InstalledAppInfo(
                    packageName = pkg,
                    label = label,
                    iconDrawable = icon,
                    isSystemApp = isSystem,
                    isHidden = isHidden
                )
            )
        }

        return resultList.sortedBy { it.label.lowercase() }
    }

    private fun isTechnicalSystemPackage(pkg: String): Boolean {
        val technicalPrefixes = listOf(
            "com.android.settings",
            "com.android.tv.settings",
            "com.android.systemui",
            "com.android.providers.",
            "com.android.inputmethod.",
            "com.google.android.inputmethod.",
            "com.google.android.katniss",
            "com.google.android.backdrop",
            "com.google.android.syncadapters.",
            "com.google.android.ext.services",
            "com.google.android.ext.shared",
            "com.tcl.tvmanager",
            "com.tcl.settings",
            "com.tcl.system",
            "com.tcl.factory",
            "com.tcl.sourcemanager",
            "com.tcl.servicemode",
            "com.tcl.commercial",
            "com.tcl.guard",
            "com.tcl.usercenter",
            "com.tcl.device",
            "com.tcl.matrix.",
            "com.tcl.ui."
        )
        if (technicalPrefixes.any { pkg.startsWith(it) }) return true
        if (pkg.startsWith("com.tcl.") && !pkg.contains("media") && !pkg.contains("player") && !pkg.contains("video") && !pkg.contains("gallery")) {
            return true
        }
        return false
    }

    fun getHiddenPackages(): Set<String> {
        return prefs.getStringSet(KEY_HIDDEN_PACKAGES, emptySet()) ?: emptySet()
    }

    fun toggleHidePackage(packageName: String, hide: Boolean) {
        val current = getHiddenPackages().toMutableSet()
        if (hide) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        prefs.edit().putStringSet(KEY_HIDDEN_PACKAGES, current).apply()
    }

    // ==========================================
    // HISTORIAL: CONTINUAR VIENDO (LO ÚLTIMO QUE MIRÉ)
    // ==========================================

    fun getLastWatched(): List<LastWatchedEntry> {
        val json = prefs.getString(KEY_LAST_WATCHED, null)
        if (json.isNullOrEmpty()) {
            return getDefaultLastWatched()
        }
        val type = object : TypeToken<List<LastWatchedEntry>>() {}.type
        val list: List<LastWatchedEntry> = gson.fromJson(json, type) ?: emptyList()
        return list.sortedByDescending { it.timestamp }
    }

    private fun getDefaultLastWatched(): List<LastWatchedEntry> {
        return listOf(
            LastWatchedEntry(
                id = "lw_1",
                title = "Stranger Things",
                subtitle = "Temporada 4 • Episodio 7",
                packageName = PKG_NETFLIX,
                timestamp = System.currentTimeMillis() - 1000 * 60 * 18,
                badgeColorHex = "#E50914",
                profileIndex = 1
            ),
            LastWatchedEntry(
                id = "lw_2",
                title = "The Boys",
                subtitle = "Temporada 3 • Episodio 4",
                packageName = PKG_PRIME_VIDEO,
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 3,
                badgeColorHex = "#00A8E1",
                profileIndex = 1
            ),
            LastWatchedEntry(
                id = "lw_3",
                title = "The Mandalorian",
                subtitle = "Capítulo 16: El Rescate",
                packageName = PKG_DISNEY_PLUS,
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 26,
                badgeColorHex = "#113CCF",
                profileIndex = 1
            )
        )
    }

    fun recordWatchedApp(
        title: String,
        subtitle: String,
        packageName: String,
        deepLinkUri: String? = null,
        badgeColorHex: String = "#2563EB",
        profileIndex: Int = 1
    ) {
        val currentList = getLastWatched().toMutableList()
        currentList.removeAll { it.packageName == packageName && it.title == title }

        val newEntry = LastWatchedEntry(
            id = "lw_${System.currentTimeMillis()}",
            title = title,
            subtitle = subtitle,
            packageName = packageName,
            deepLinkUri = deepLinkUri,
            timestamp = System.currentTimeMillis(),
            badgeColorHex = badgeColorHex,
            profileIndex = profileIndex
        )

        currentList.add(0, newEntry)
        val trimmed = if (currentList.size > 15) currentList.subList(0, 15) else currentList

        val json = gson.toJson(trimmed)
        prefs.edit().putString(KEY_LAST_WATCHED, json).apply()
    }

    // ==========================================
    // ACCESOS RÁPIDOS Y CATEGORÍAS (MOVIES, MUSIC, SPORTS, NEWS)
    // ==========================================

    fun getQuickShortcuts(): List<QuickShortcut> {
        val defaultList = listOf(
            QuickShortcut(
                id = "qs_netflix",
                title = "Netflix",
                subtitle = "Películas y Series",
                appName = "Netflix",
                packageName = PKG_NETFLIX,
                accentColorHex = "#E50914",
                profileIndex = 1
            ),
            QuickShortcut(
                id = "qs_youtube",
                title = "YouTube",
                subtitle = "Videos y Canales TV",
                appName = "YouTube",
                packageName = PKG_YOUTUBE,
                accentColorHex = "#FF0000",
                profileIndex = 1
            ),
            QuickShortcut(
                id = "qs_prime",
                title = "Prime Video",
                subtitle = "Amazon Originals",
                appName = "Prime Video",
                packageName = PKG_PRIME_VIDEO,
                accentColorHex = "#00A8E1",
                profileIndex = 1
            ),
            QuickShortcut(
                id = "qs_disney",
                title = "Disney+",
                subtitle = "Marvel, Star Wars, Pixar",
                appName = "Disney+",
                packageName = PKG_DISNEY_PLUS,
                accentColorHex = "#113CCF",
                profileIndex = 1
            ),
            QuickShortcut(
                id = "qs_max",
                title = "Max",
                subtitle = "HBO & Warner Bros",
                appName = "Max",
                packageName = PKG_MAX,
                accentColorHex = "#002BE7",
                profileIndex = 1
            ),
            QuickShortcut(
                id = "qs_vix",
                title = "ViX",
                subtitle = "Televisión en Vivo y Novelas",
                appName = "ViX",
                packageName = PKG_VIX,
                accentColorHex = "#FF4500",
                profileIndex = 1
            ),
            QuickShortcut(
                id = "qs_apple",
                title = "Apple TV+",
                subtitle = "Series y Películas Exclusivas",
                appName = "Apple TV",
                packageName = PKG_APPLE_TV,
                accentColorHex = "#1A1A1A",
                profileIndex = 1
            )
        )

        val customJson = prefs.getString(KEY_CUSTOM_SHORTCUTS, null)
        val customList: List<QuickShortcut> = if (!customJson.isNullOrEmpty()) {
            val type = object : TypeToken<List<QuickShortcut>>() {}.type
            gson.fromJson(customJson, type) ?: emptyList()
        } else {
            emptyList()
        }

        val activeProfile = getActiveProfile()
        val mappedDefaults = defaultList.map { shortcut ->
            val mappedProfileIndex = activeProfile.defaultProfileIndexMap[shortcut.packageName] ?: shortcut.profileIndex
            shortcut.copy(profileIndex = mappedProfileIndex)
        }

        return mappedDefaults + customList
    }

    fun getCategoryShortcuts(categoryName: String, allInstalled: List<InstalledAppInfo>): List<QuickShortcut> {
        val moviesBase = getQuickShortcuts()
        return when (categoryName.uppercase()) {
            "MOVIES" -> moviesBase
            "MUSIC" -> {
                val musicList = moviesBase.filter {
                    it.packageName == PKG_YOUTUBE ||
                    it.appName.contains("Spotify", ignoreCase = true) ||
                    it.appName.contains("Music", ignoreCase = true)
                }.toMutableList()
                val extraMusic = allInstalled.filter {
                    it.packageName.contains("spotify", ignoreCase = true) ||
                    it.label.contains("music", ignoreCase = true) ||
                    it.label.contains("radio", ignoreCase = true) ||
                    it.label.contains("sound", ignoreCase = true) ||
                    it.label.contains("deezer", ignoreCase = true)
                }.map {
                    QuickShortcut(
                        id = "app_${it.packageName}",
                        title = it.label,
                        subtitle = "Música y Audio",
                        appName = it.label,
                        packageName = it.packageName,
                        accentColorHex = "#10B981"
                    )
                }
                (musicList + extraMusic).distinctBy { it.packageName }.ifEmpty {
                    moviesBase.take(3)
                }
            }
            "SPORTS" -> {
                val sportsList = moviesBase.filter {
                    it.packageName == PKG_VIX || it.appName.contains("ESPN", ignoreCase = true)
                }.toMutableList()
                val extraSports = allInstalled.filter {
                    it.label.contains("sport", ignoreCase = true) ||
                    it.label.contains("dazn", ignoreCase = true) ||
                    it.label.contains("espn", ignoreCase = true) ||
                    it.label.contains("fox", ignoreCase = true) ||
                    it.label.contains("f1", ignoreCase = true) ||
                    it.label.contains("nba", ignoreCase = true) ||
                    it.label.contains("claro", ignoreCase = true)
                }.map {
                    QuickShortcut(
                        id = "app_${it.packageName}",
                        title = it.label,
                        subtitle = "Deportes en Vivo",
                        appName = it.label,
                        packageName = it.packageName,
                        accentColorHex = "#F59E0B"
                    )
                }
                (sportsList + extraSports).distinctBy { it.packageName }.ifEmpty {
                    listOf(
                        QuickShortcut("sp_vix", "ViX Deportes", "Partidos y Liga MX", "ViX", PKG_VIX, accentColorHex = "#FF5722"),
                        QuickShortcut("sp_yt", "YouTube Deportes", "Resúmenes y Goles", "YouTube", PKG_YOUTUBE, accentColorHex = "#FF0000")
                    )
                }
            }
            "NEWS" -> {
                val newsApps = allInstalled.filter {
                    it.label.contains("noticia", ignoreCase = true) ||
                    it.label.contains("news", ignoreCase = true) ||
                    it.label.contains("pluto", ignoreCase = true) ||
                    it.label.contains("tv abierta", ignoreCase = true)
                }.map {
                    QuickShortcut(
                        id = "app_${it.packageName}",
                        title = it.label,
                        subtitle = "Noticias en Vivo",
                        appName = it.label,
                        packageName = it.packageName,
                        accentColorHex = "#06B6D4"
                    )
                }
                (newsApps + listOf(
                    QuickShortcut("nw_yt", "YouTube Noticias", "Canales en Vivo", "YouTube", PKG_YOUTUBE, accentColorHex = "#FF0000"),
                    QuickShortcut("nw_vix", "ViX Noticias", "Noticieros 24/7", "ViX", PKG_VIX, accentColorHex = "#FF5722")
                )).distinctBy { it.packageName }
            }
            else -> moviesBase
        }
    }

    fun addCustomShortcut(shortcut: QuickShortcut) {
        val customJson = prefs.getString(KEY_CUSTOM_SHORTCUTS, null)
        val list: MutableList<QuickShortcut> = if (!customJson.isNullOrEmpty()) {
            val type = object : TypeToken<List<QuickShortcut>>() {}.type
            (gson.fromJson<List<QuickShortcut>>(customJson, type) ?: emptyList()).toMutableList()
        } else {
            mutableListOf()
        }

        list.add(shortcut.copy(isCustom = true))
        prefs.edit().putString(KEY_CUSTOM_SHORTCUTS, gson.toJson(list)).apply()
    }

    fun removeCustomShortcut(shortcutId: String) {
        val customJson = prefs.getString(KEY_CUSTOM_SHORTCUTS, null) ?: return
        val type = object : TypeToken<List<QuickShortcut>>() {}.type
        val list: MutableList<QuickShortcut> = (gson.fromJson<List<QuickShortcut>>(customJson, type) ?: emptyList()).toMutableList()
        list.removeAll { it.id == shortcutId }
        prefs.edit().putString(KEY_CUSTOM_SHORTCUTS, gson.toJson(list)).apply()
    }

    // ==========================================
    // LANZAMIENTO INTELIGENTE DE APPS / DEEP LINKS
    // ==========================================

    fun launchAppOrShortcut(
        packageName: String,
        title: String,
        subtitle: String = "",
        deepLinkUri: String? = null,
        accentColorHex: String = "#2563EB",
        targetProfileIndex: Int = 1
    ): Boolean {
        val activeProfile = getActiveProfile()
        val actualProfileIndex = activeProfile.defaultProfileIndexMap[packageName] ?: targetProfileIndex

        LauncherAccessibilityService.prepareProfileAutoSelection(
            targetPackage = packageName,
            targetProfileIndex = actualProfileIndex,
            targetProfileName = activeProfile.name,
            autoSkipWhoIsWatching = activeProfile.autoSkipWhoIsWatching
        )

        recordWatchedApp(
            title = title,
            subtitle = subtitle.ifEmpty { "Perfil $actualProfileIndex • Abierto recientemente" },
            packageName = packageName,
            deepLinkUri = deepLinkUri,
            badgeColorHex = accentColorHex,
            profileIndex = actualProfileIndex
        )

        if (!deepLinkUri.isNullOrBlank()) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLinkUri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (packageName.isNotBlank()) {
                        setPackage(packageName)
                    }
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }

        val pm = context.packageManager
        var launchIntent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)

        if (launchIntent == null && packageName == PKG_VIX) {
            launchIntent = pm.getLeanbackLaunchIntentForPackage(PKG_VIX_ALT)
                ?: pm.getLaunchIntentForPackage(PKG_VIX_ALT)
        }

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(launchIntent)
                return true
            } catch (_: Exception) {
                return false
            }
        }

        return false
    }

    fun launchSearch(query: String = "") {
        if (query.isNotBlank()) {
            try {
                val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(PKG_YOUTUBE)
                }
                context.startActivity(ytIntent)
                return
            } catch (_: Exception) {}
        }

        val searchIntents = listOf(
            Intent(Intent.ACTION_ASSIST).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            Intent("android.intent.action.VOICE_ASSIST").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            Intent("android.intent.action.SEARCH").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
        for (intent in searchIntents) {
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
        openPlayStore()
    }

    // ==========================================
    // AJUSTES DEL SISTEMA Y CUENTA GOOGLE
    // ==========================================

    fun isDarkTheme(): Boolean {
        return prefs.getBoolean("key_is_dark_theme", true)
    }

    fun setDarkTheme(isDark: Boolean) {
        prefs.edit().putBoolean("key_is_dark_theme", isDark).apply()
    }

    fun openSystemSettings(action: String? = null) {
        val targetAction = action ?: android.provider.Settings.ACTION_SETTINGS
        try {
            val intent = Intent(targetAction).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {}
        }
    }

    fun openGoogleAccounts() {
        val candidates = listOf(
            Intent("android.settings.SYNC_SETTINGS"),
            Intent(android.provider.Settings.ACTION_SYNC_SETTINGS),
            Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.accounts.AccountsActivity"),
            Intent(android.provider.Settings.ACTION_ADD_ACCOUNT),
            Intent(android.provider.Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    fun openPlayStore() {
        try {
            val pm = context.packageManager
            val intent = pm.getLeanbackLaunchIntentForPackage("com.android.vending")
                ?: pm.getLaunchIntentForPackage("com.android.vending")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
