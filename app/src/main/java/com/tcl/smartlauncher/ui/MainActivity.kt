package com.tcl.smartlauncher.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcl.smartlauncher.data.LauncherRepository
import com.tcl.smartlauncher.model.InstalledAppInfo
import com.tcl.smartlauncher.model.LastWatchedEntry
import com.tcl.smartlauncher.model.QuickShortcut
import com.tcl.smartlauncher.model.UserProfile
import com.tcl.smartlauncher.model.WallpaperInfo
import com.tcl.smartlauncher.server.TvRemoteWebServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var repository: LauncherRepository
    private var webServer: TvRemoteWebServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = LauncherRepository(this)

        // Iniciar el servidor web HTTP para el control remoto del celular
        webServer = TvRemoteWebServer(this, repository).apply {
            start()
        }

        setContent {
            TclLauncherTheme(darkTheme = true) {
                LauncherMainScreen(
                    repository = repository,
                    webServer = webServer,
                    onLaunchApp = { pkg, title, subtitle, deepLink, color, profileIdx ->
                        val success = repository.launchAppOrShortcut(
                            packageName = pkg,
                            title = title,
                            subtitle = subtitle,
                            deepLinkUri = deepLink,
                            accentColorHex = color,
                            targetProfileIndex = profileIdx
                        )
                        if (!success) {
                            Toast.makeText(
                                this,
                                "No se pudo abrir $title. Comprueba que esté instalada.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webServer?.stop()
    }
}

@Composable
fun LauncherMainScreen(
    repository: LauncherRepository,
    webServer: TvRemoteWebServer?,
    onLaunchApp: (String, String, String, String?, String, Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeProfile by remember { mutableStateOf(repository.getActiveProfile()) }
    var activeWallpaper by remember { mutableStateOf(repository.getActiveWallpaper()) }
    var lastWatchedList by remember { mutableStateOf(repository.getLastWatched()) }
    var quickShortcuts by remember { mutableStateOf(repository.getQuickShortcuts()) }
    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf("MOVIES") }

    // Diálogos modales
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showQuickAddDialog by remember { mutableStateOf(false) }
    var showControlCenterDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showAddShortcutDialog by remember { mutableStateOf(false) }
    var selectedAppForOptions by remember { mutableStateOf<InstalledAppInfo?>(null) }

    // Reloj y fecha en formato exacto de la fotografía de referencia
    var currentTime by remember { mutableStateOf("6:36 PM") }
    var currentDate by remember { mutableStateOf("23 OCT '26") }

    val firstCapsuleFocusRequester = remember { FocusRequester() }

    val refreshData = {
        activeProfile = repository.getActiveProfile()
        activeWallpaper = repository.getActiveWallpaper()
        lastWatchedList = repository.getLastWatched()
        quickShortcuts = repository.getQuickShortcuts()
        coroutineScope.launch(Dispatchers.IO) {
            val apps = repository.getInstalledApps(hideSystemApps = true)
            withContext(Dispatchers.Main) {
                installedApps = apps
            }
        }
    }

    // Vincular callbacks del servidor web para refrescar en vivo la pantalla
    LaunchedEffect(webServer) {
        webServer?.onWallpaperUpdated = {
            mainCoroutine { refreshData() }
        }
        webServer?.onDataUpdated = {
            mainCoroutine { refreshData() }
        }
    }

    // Cargar aplicaciones instaladas
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val apps = repository.getInstalledApps(hideSystemApps = true)
            withContext(Dispatchers.Main) {
                installedApps = apps
            }
        }
    }

    // Foco automático inicial en la primera cápsula (NETFLIX)
    LaunchedEffect(Unit) {
        delay(250)
        try {
            firstCapsuleFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Reloj y fecha en vivo
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
        val dateFormat = SimpleDateFormat("dd MMM ''yy", Locale("es", "ES"))
        while (true) {
            val now = Date()
            currentTime = timeFormat.format(now)
            currentDate = dateFormat.format(now).uppercase()
            delay(1000)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ==========================================
        // 1. FONDO FOTOGRÁFICO A PANTALLA COMPLETA
        // ==========================================
        ScenicWallpaperBackground(
            wallpaper = activeWallpaper,
            modifier = Modifier.fillMaxSize()
        )

        // ==========================================
        // 2. ESQUINA SUPERIOR IZQUIERDA: RELOJ Y FECHA MINIMALISTAS
        // ==========================================
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 36.dp, start = 48.dp)
        ) {
            Text(
                text = currentTime,
                fontSize = 42.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.45f),
                        blurRadius = 8f
                    )
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = currentDate,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                color = Color.White.copy(alpha = 0.9f),
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.45f),
                        blurRadius = 6f
                    )
                )
            )
        }

        // ==========================================
        // 3. ESQUINA SUPERIOR DERECHA: LOS 3 ICONOS MINIMALISTAS
        // ==========================================
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 36.dp, end = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono 1: Entradas TV / Selector de Perfil
            TopActionIconButton(
                icon = Icons.Default.Input,
                label = "",
                onClick = { showProfileDialog = true }
            )

            // Icono 2: Ajustes TV (Centro de Control)
            TopActionIconButton(
                icon = Icons.Default.Settings,
                label = "",
                onClick = { showControlCenterDialog = true }
            )

            // Icono 3: Search (Con fondo blanco sólido como en la foto de referencia)
            TopActionIconButton(
                icon = Icons.Default.Search,
                label = "Search",
                isAlwaysHighlighted = true,
                onClick = { repository.launchSearch() }
            )
        }

        // ==========================================
        // 4. PARTE INFERIOR: FILA ÚNICA DE CÁPSULAS Y BARRA DE CATEGORÍAS
        // ==========================================
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // FILA ÚNICA DE CÁPSULAS OVALADAS FROSTED GLASS (178x88dp)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 48.dp)
            ) {
                when (selectedCategory.uppercase()) {
                    "RECIENTES" -> {
                        if (lastWatchedList.isEmpty()) {
                            itemsIndexed(quickShortcuts.take(6)) { index, shortcut ->
                                FrostedCapsuleCard(
                                    modifier = if (index == 0) Modifier.focusRequester(firstCapsuleFocusRequester) else Modifier,
                                    onClick = {
                                        onLaunchApp(shortcut.packageName, shortcut.title, shortcut.subtitle, shortcut.deepLinkUri, shortcut.accentColorHex, shortcut.profileIndex)
                                        refreshData()
                                    }
                                ) {
                                    BrandCapsuleLogo(
                                        packageName = shortcut.packageName,
                                        title = shortcut.title
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(lastWatchedList) { index, item ->
                                FrostedCapsuleCard(
                                    modifier = if (index == 0) Modifier.focusRequester(firstCapsuleFocusRequester) else Modifier,
                                    onClick = {
                                        onLaunchApp(item.packageName, item.title, item.subtitle, item.deepLinkUri, item.badgeColorHex, item.profileIndex)
                                        refreshData()
                                    }
                                ) {
                                    BrandCapsuleLogo(
                                        packageName = item.packageName,
                                        title = item.title,
                                        showRecentBadge = true,
                                        timestamp = item.timestamp,
                                        profileIndex = item.profileIndex
                                    )
                                }
                            }
                        }
                    }

                    "APPS" -> {
                        itemsIndexed(installedApps) { index, app ->
                            FrostedCapsuleCard(
                                modifier = if (index == 0) Modifier.focusRequester(firstCapsuleFocusRequester) else Modifier,
                                onClick = {
                                    onLaunchApp(app.packageName, app.label, "Abierto desde apps", null, "#2563EB", 1)
                                    refreshData()
                                }
                            ) {
                                BrandCapsuleLogo(
                                    packageName = app.packageName,
                                    title = app.label,
                                    iconDrawable = app.iconDrawable
                                )
                            }
                        }
                    }

                    else -> {
                        // "MOVIES", "MUSIC", "SPORTS", "NEWS"
                        val shortcuts = repository.getCategoryShortcuts(selectedCategory, installedApps)
                        itemsIndexed(shortcuts) { index, shortcut ->
                            FrostedCapsuleCard(
                                modifier = if (index == 0) Modifier.focusRequester(firstCapsuleFocusRequester) else Modifier,
                                onClick = {
                                    onLaunchApp(shortcut.packageName, shortcut.title, shortcut.subtitle, shortcut.deepLinkUri, shortcut.accentColorHex, shortcut.profileIndex)
                                    refreshData()
                                }
                            ) {
                                BrandCapsuleLogo(
                                    packageName = shortcut.packageName,
                                    title = shortcut.title
                                )
                            }
                        }
                    }
                }
            }

            // BARRA INFERIOR DE CATEGORÍAS (RECIENTES, MOVIES, APPS, MUSIC, SPORTS, NEWS, +)
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryTabPill(
                    title = "RECIENTES",
                    isSelected = selectedCategory == "RECIENTES",
                    onFocused = { selectedCategory = "RECIENTES" },
                    onClick = { selectedCategory = "RECIENTES" }
                )

                CategoryTabPill(
                    title = "MOVIES",
                    isSelected = selectedCategory == "MOVIES",
                    onFocused = { selectedCategory = "MOVIES" },
                    onClick = { selectedCategory = "MOVIES" }
                )

                CategoryTabPill(
                    title = "APPS",
                    isSelected = selectedCategory == "APPS",
                    onFocused = { selectedCategory = "APPS" },
                    onClick = { selectedCategory = "APPS" }
                )

                CategoryTabPill(
                    title = "MUSIC",
                    isSelected = selectedCategory == "MUSIC",
                    onFocused = { selectedCategory = "MUSIC" },
                    onClick = { selectedCategory = "MUSIC" }
                )

                CategoryTabPill(
                    title = "SPORTS",
                    isSelected = selectedCategory == "SPORTS",
                    onFocused = { selectedCategory = "SPORTS" },
                    onClick = { selectedCategory = "SPORTS" }
                )

                CategoryTabPill(
                    title = "NEWS",
                    isSelected = selectedCategory == "NEWS",
                    onFocused = { selectedCategory = "NEWS" },
                    onClick = { selectedCategory = "NEWS" }
                )

                // Botón circular '+' (Wallpapers, Celular PWA, Accesos Rápidos)
                CategoryAddButton(
                    onClick = { showQuickAddDialog = true }
                )
            }
        }

        // ==========================================
        // DIÁLOGOS
        // ==========================================

        if (showWallpaperDialog) {
            WallpaperPickerDialog(
                repository = repository,
                onDismiss = { showWallpaperDialog = false },
                onWallpaperSelected = { newWp ->
                    activeWallpaper = newWp
                }
            )
        }

        if (showQuickAddDialog) {
            QuickAddActionDialog(
                repository = repository,
                onDismiss = { showQuickAddDialog = false },
                onOpenWallpapers = { showWallpaperDialog = true },
                onOpenAddShortcut = { showAddShortcutDialog = true },
                onOpenProfiles = { showProfileDialog = true },
                onOpenSettings = { showControlCenterDialog = true }
            )
        }

        if (showControlCenterDialog) {
            TVControlCenterDialog(
                context = context,
                repository = repository,
                isDarkTheme = true,
                onDismiss = { showControlCenterDialog = false },
                onStatusChanged = { refreshData() },
                onOpenWallpapers = { showWallpaperDialog = true }
            )
        }

        if (showProfileDialog) {
            ProfileDialog(
                repository = repository,
                onDismiss = { showProfileDialog = false },
                onProfileSelected = { newProfile ->
                    activeProfile = newProfile
                    refreshData()
                }
            )
        }

        if (showAddShortcutDialog) {
            AddShortcutDialog(
                repository = repository,
                onDismiss = { showAddShortcutDialog = false },
                onShortcutAdded = { refreshData() }
            )
        }

        selectedAppForOptions?.let { app ->
            AppOptionsDialog(
                app = app,
                repository = repository,
                onDismiss = { selectedAppForOptions = null },
                onAppVisibilityChanged = { refreshData() },
                onAddAsShortcut = {
                    val newShortcut = QuickShortcut(
                        id = "app_${app.packageName}",
                        title = app.label,
                        subtitle = "Acceso Rápido",
                        appName = app.label,
                        packageName = app.packageName,
                        isCustom = true
                    )
                    repository.addCustomShortcut(newShortcut)
                    refreshData()
                }
            )
        }
    }
}

private fun mainCoroutine(block: () -> Unit) {
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        block()
    }
}
