package com.tcl.smartlauncher.server

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.tcl.smartlauncher.data.LauncherRepository
import com.tcl.smartlauncher.service.LauncherAccessibilityService
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Servidor HTTP embebido ultraligero y de alto rendimiento en el puerto 8888.
 * Permite controlar la TV TCL desde cualquier celular a través del navegador web (PWA)
 * con controles táctiles, control de volumen real, apertura directa a perfiles,
 * y subida de fotos del celular como fondo de pantalla a la TV.
 */
class TvRemoteWebServer(
    private val context: Context,
    private val repository: LauncherRepository,
    private val port: Int = 8888
) {
    companion object {
        private const val TAG = "TvRemoteWebServer"
        @Volatile
        var isServerRunning = false
    }

    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gson = Gson()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // mDNS / NSD para que responda como tcl-tv.local
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    // Callback cuando el wallpaper cambia para refrescar la UI en Compose
    var onWallpaperUpdated: (() -> Unit)? = null
    var onDataUpdated: (() -> Unit)? = null

    fun start() {
        if (isServerRunning) return

        executor.execute {
            try {
                serverSocket = ServerSocket(port)
                isServerRunning = true
                Log.i(TAG, "TvRemoteWebServer iniciado en el puerto $port")

                registerMdnsService()

                while (isServerRunning && serverSocket?.isClosed == false) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        executor.execute { handleClient(clientSocket) }
                    } catch (e: Exception) {
                        if (!isServerRunning) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando TvRemoteWebServer", e)
            } finally {
                isServerRunning = false
            }
        }
    }

    fun stop() {
        isServerRunning = false
        try {
            unregisterMdnsService()
            serverSocket?.close()
        } catch (_: Exception) {}
    }

    private fun registerMdnsService() {
        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "tcl-tv"
                serviceType = "_http._tcp."
                this.port = this@TvRemoteWebServer.port
            }

            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
                    Log.i(TAG, "mDNS registrado exitosamente: tcl-tv.local:${this@TvRemoteWebServer.port}")
                }
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.w(TAG, "Fallo al registrar mDNS, código: $errorCode")
                }
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {}
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
            }

            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.w(TAG, "mDNS no soportado o con permisos restringidos", e)
        }
    }

    private fun unregisterMdnsService() {
        try {
            registrationListener?.let { nsdManager?.unregisterService(it) }
            registrationListener = null
        } catch (_: Exception) {}
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 8000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(input))

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val pathWithQuery = parts[1]
            val path = pathWithQuery.substringBefore("?")

            var contentLength = 0
            var line: String? = reader.readLine()
            while (!line.isNullOrEmpty()) {
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
                line = reader.readLine()
            }

            val body = if (contentLength > 0) {
                val buffer = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val read = reader.read(buffer, readTotal, contentLength - readTotal)
                    if (read == -1) break
                    readTotal += read
                }
                String(buffer, 0, readTotal)
            } else ""

            when {
                method == "GET" && (path == "/" || path == "/index.html") -> {
                    servePwaHtml(output)
                }
                method == "GET" && path == "/manifest.json" -> {
                    serveManifest(output)
                }
                method == "GET" && path == "/api/status" -> {
                    serveStatusJson(output)
                }
                method == "POST" && path == "/api/key" -> {
                    handleKeyCommand(body, output)
                }
                method == "POST" && path == "/api/volume" -> {
                    handleVolumeCommand(body, output)
                }
                method == "POST" && path == "/api/launch" -> {
                    handleLaunchCommand(body, output)
                }
                method == "POST" && path == "/api/deeplink" -> {
                    handleDeepLinkCommand(body, output)
                }
                method == "POST" && path == "/api/profile" -> {
                    handleProfileCommand(body, output)
                }
                method == "POST" && path == "/api/wallpaper" -> {
                    handleWallpaperCommand(body, output)
                }
                else -> {
                    sendResponse(output, 404, "text/plain", "Not Found")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error procesando petición web", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun handleKeyCommand(body: String, output: OutputStream) {
        val json = try { gson.fromJson(body, JsonObject::class.java) } catch (_: Exception) { null }
        val key = json?.get("key")?.asString?.uppercase() ?: ""

        mainHandler.post {
            when (key) {
                "UP" -> LauncherAccessibilityService.sendKeyEvent(KeyEvent.KEYCODE_DPAD_UP)
                "DOWN" -> LauncherAccessibilityService.sendKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN)
                "LEFT" -> LauncherAccessibilityService.sendKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT)
                "RIGHT" -> LauncherAccessibilityService.sendKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT)
                "ENTER", "OK" -> LauncherAccessibilityService.sendKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
                "BACK" -> {
                    if (!LauncherAccessibilityService.triggerBack()) {
                        LauncherAccessibilityService.sendKeyEvent(KeyEvent.KEYCODE_BACK)
                    }
                }
                "HOME" -> {
                    if (!LauncherAccessibilityService.triggerHome()) {
                        val homeIntent = Intent(context, com.tcl.smartlauncher.ui.MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        context.startActivity(homeIntent)
                    }
                }
                "SETTINGS" -> LauncherAccessibilityService.sendKeyEvent(KeyEvent.KEYCODE_SETTINGS)
                "PLAY_PAUSE" -> LauncherAccessibilityService.sendKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                "VOL_UP" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "VOL_DOWN" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "MUTE" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            }
        }

        sendJsonResponse(output, """{"status":"ok","key":"$key"}""")
    }

    private fun handleVolumeCommand(body: String, output: OutputStream) {
        val json = try { gson.fromJson(body, JsonObject::class.java) } catch (_: Exception) { null }
        val action = json?.get("action")?.asString?.lowercase() ?: ""
        val value = json?.get("value")?.asInt

        mainHandler.post {
            when (action) {
                "up" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "down" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "mute" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                "set" -> {
                    if (value != null) {
                        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val clamped = value.coerceIn(0, max)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, AudioManager.FLAG_SHOW_UI)
                    }
                }
            }
        }

        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        sendJsonResponse(output, """{"status":"ok","currentVolume":$cur,"maxVolume":$max}""")
    }

    private fun handleLaunchCommand(body: String, output: OutputStream) {
        val json = try { gson.fromJson(body, JsonObject::class.java) } catch (_: Exception) { null }
        val pkg = json?.get("packageName")?.asString ?: ""
        val title = json?.get("title")?.asString ?: pkg
        val deepLink = json?.get("deepLinkUri")?.asString
        val profileIdx = json?.get("profileIndex")?.asInt ?: 1

        mainHandler.post {
            repository.launchAppOrShortcut(
                packageName = pkg,
                title = title,
                subtitle = "Iniciado desde celular",
                deepLinkUri = deepLink,
                targetProfileIndex = profileIdx
            )
            onDataUpdated?.invoke()
        }

        sendJsonResponse(output, """{"status":"ok","launched":"$title"}""")
    }

    private fun handleDeepLinkCommand(body: String, output: OutputStream) {
        val json = try { gson.fromJson(body, JsonObject::class.java) } catch (_: Exception) { null }
        val query = json?.get("query")?.asString ?: ""
        val url = json?.get("url")?.asString ?: ""

        mainHandler.post {
            if (url.isNotBlank()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            } else if (query.isNotBlank()) {
                repository.launchSearch(query)
            }
            onDataUpdated?.invoke()
        }

        sendJsonResponse(output, """{"status":"ok"}""")
    }

    private fun handleProfileCommand(body: String, output: OutputStream) {
        val json = try { gson.fromJson(body, JsonObject::class.java) } catch (_: Exception) { null }
        val profileId = json?.get("profileId")?.asString ?: ""
        if (profileId.isNotBlank()) {
            mainHandler.post {
                repository.setActiveProfile(profileId)
                onDataUpdated?.invoke()
            }
        }
        sendJsonResponse(output, """{"status":"ok","profileId":"$profileId"}""")
    }

    private fun handleWallpaperCommand(body: String, output: OutputStream) {
        val json = try { gson.fromJson(body, JsonObject::class.java) } catch (_: Exception) { null }
        val action = json?.get("action")?.asString ?: "preset"
        val wallpaperId = json?.get("wallpaperId")?.asString ?: ""
        val base64 = json?.get("base64")?.asString ?: ""

        mainHandler.post {
            if (action == "upload" && base64.isNotBlank()) {
                repository.saveCustomWallpaperFromBase64(base64)
            } else if (wallpaperId.isNotBlank()) {
                repository.setActiveWallpaper(wallpaperId)
            }
            onWallpaperUpdated?.invoke()
        }

        sendJsonResponse(output, """{"status":"ok"}""")
    }

    private fun serveStatusJson(output: OutputStream) {
        val activeProfile = repository.getActiveProfile()
        val allProfiles = repository.getUserProfiles()
        val lastWatched = repository.getLastWatched().take(6)
        val shortcuts = repository.getQuickShortcuts()
        val installed = repository.getInstalledApps(hideSystemApps = true)
        val activeWp = repository.getActiveWallpaper()
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val ip = repository.getDeviceIpAddress()

        val data = mapOf(
            "ip" to ip,
            "port" to port,
            "activeProfile" to mapOf(
                "id" to activeProfile.id,
                "name" to activeProfile.name,
                "emoji" to activeProfile.avatarEmoji
            ),
            "profiles" to allProfiles.map {
                mapOf("id" to it.id, "name" to it.name, "emoji" to it.avatarEmoji)
            },
            "volume" to mapOf("current" to curVol, "max" to maxVol),
            "activeWallpaper" to mapOf("id" to activeWp.id, "name" to activeWp.name, "isCustom" to activeWp.isCustom),
            "wallpapers" to repository.getAvailableWallpapers().map {
                mapOf("id" to it.id, "name" to it.name, "description" to it.description)
            },
            "lastWatched" to lastWatched.map {
                mapOf("id" to it.id, "title" to it.title, "subtitle" to it.subtitle, "packageName" to it.packageName, "profileIndex" to it.profileIndex)
            },
            "shortcuts" to shortcuts.map {
                mapOf("id" to it.id, "title" to it.title, "appName" to it.appName, "packageName" to it.packageName, "profileIndex" to it.profileIndex)
            },
            "installedApps" to installed.map {
                mapOf("packageName" to it.packageName, "label" to it.label)
            }
        )

        sendJsonResponse(output, gson.toJson(data))
    }

    private fun serveManifest(output: OutputStream) {
        val manifest = """
        {
          "name": "TCL TV Remote",
          "short_name": "TCL Remote",
          "start_url": "/",
          "display": "standalone",
          "background_color": "#0B0F17",
          "theme_color": "#0B0F17",
          "icons": [
            {
              "src": "data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><rect width='100' height='100' rx='20' fill='%232563EB'/><text y='65' x='50' text-anchor='middle' font-size='50' fill='white'>📺</text></svg>",
              "sizes": "192x192 512x512",
              "type": "image/svg+xml"
            }
          ]
        }
        """.trimIndent()
        sendResponse(output, 200, "application/manifest+json", manifest)
    }

    private fun servePwaHtml(output: OutputStream) {
        val html = """
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">
  <title>TCL Remote Control</title>
  <link rel="manifest" href="/manifest.json">
  <meta name="theme-color" content="#0B0F17">
  <meta name="apple-mobile-web-app-capable" content="yes">
  <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
  <style>
    * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; margin: 0; padding: 0; }
    body {
      background: #0B0F17;
      color: #F8FAFC;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      overflow-x: hidden;
      padding-bottom: 74px;
    }
    header {
      background: rgba(19, 29, 48, 0.85);
      backdrop-filter: blur(20px);
      -webkit-backdrop-filter: blur(20px);
      padding: 14px 18px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid rgba(255,255,255,0.08);
      position: sticky;
      top: 0;
      z-index: 50;
    }
    .brand { display: flex; align-items: center; gap: 8px; font-weight: 800; font-size: 18px; }
    .brand-dot { width: 10px; height: 10px; border-radius: 50%; background: #2563EB; box-shadow: 0 0 10px #2563EB; }
    .status-badge { font-size: 11px; padding: 4px 10px; border-radius: 20px; background: rgba(37,99,235,0.2); color: #60A5FA; border: 1px solid rgba(96,165,250,0.3); }

    .tab-content { display: none; padding: 16px; flex: 1; }
    .tab-content.active { display: block; }

    /* Bottom Navigation */
    nav.bottom-bar {
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      height: 64px;
      background: rgba(15, 23, 42, 0.92);
      backdrop-filter: blur(24px);
      -webkit-backdrop-filter: blur(24px);
      display: flex;
      justify-content: space-around;
      align-items: center;
      border-top: 1px solid rgba(255,255,255,0.08);
      z-index: 100;
      padding-bottom: env(safe-area-inset-bottom);
    }
    .nav-btn {
      background: transparent;
      border: none;
      color: #94A3B8;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 3px;
      font-size: 11px;
      font-weight: 600;
      cursor: pointer;
      flex: 1;
      padding: 8px 0;
    }
    .nav-btn svg { width: 22px; height: 22px; stroke: currentColor; fill: none; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
    .nav-btn.active { color: #60A5FA; }

    /* Cards & Containers */
    .section-title { font-size: 13px; font-weight: 700; text-transform: uppercase; letter-spacing: 1px; color: #94A3B8; margin: 18px 0 10px 0; }
    .glass-card {
      background: rgba(30, 41, 59, 0.6);
      border: 1px solid rgba(255,255,255,0.08);
      border-radius: 16px;
      padding: 14px;
      margin-bottom: 14px;
    }

    /* Profiles Bar */
    .profile-row { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 4px; }
    .profile-chip {
      background: rgba(255,255,255,0.06);
      border: 1px solid rgba(255,255,255,0.12);
      border-radius: 20px;
      padding: 6px 14px;
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      white-space: nowrap;
    }
    .profile-chip.active { background: #2563EB; border-color: #60A5FA; color: white; }

    /* Streaming Quick Grid */
    .apps-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
    .app-card {
      background: rgba(30, 36, 43, 0.85);
      border: 1px solid rgba(255,255,255,0.12);
      border-radius: 24px;
      height: 64px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 800;
      font-size: 16px;
      cursor: pointer;
      transition: transform 0.1s, border-color 0.1s;
    }
    .app-card:active { transform: scale(0.96); border-color: white; }

    /* D-PAD Touch Controls */
    .dpad-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      margin: 16px 0;
    }
    .dpad {
      width: 240px;
      height: 240px;
      border-radius: 50%;
      background: rgba(30, 41, 59, 0.7);
      border: 2px solid rgba(255,255,255,0.15);
      position: relative;
      box-shadow: 0 10px 30px rgba(0,0,0,0.5);
    }
    .dpad-btn {
      position: absolute;
      background: transparent;
      border: none;
      color: white;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .dpad-btn:active { background: rgba(255,255,255,0.15); border-radius: 50%; }
    .dpad-up { top: 8px; left: 85px; width: 70px; height: 60px; }
    .dpad-down { bottom: 8px; left: 85px; width: 70px; height: 60px; }
    .dpad-left { top: 85px; left: 8px; width: 60px; height: 70px; }
    .dpad-right { top: 85px; right: 8px; width: 60px; height: 70px; }
    .dpad-center {
      top: 75px; left: 75px; width: 90px; height: 90px;
      border-radius: 50%;
      background: #2563EB;
      box-shadow: 0 0 15px rgba(37,99,235,0.5);
      font-weight: 800;
      font-size: 16px;
    }
    .dpad-center:active { background: #1D4ED8; }

    /* Action Buttons Row */
    .action-row { display: flex; justify-content: space-around; width: 100%; margin: 12px 0; gap: 8px; }
    .circle-btn {
      width: 54px;
      height: 54px;
      border-radius: 50%;
      background: rgba(255,255,255,0.08);
      border: 1px solid rgba(255,255,255,0.12);
      color: white;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      font-size: 10px;
      cursor: pointer;
      gap: 2px;
    }
    .circle-btn:active { background: #2563EB; border-color: white; }

    /* Volume Slider */
    .vol-box { display: flex; align-items: center; gap: 12px; margin: 14px 0; }
    .vol-slider {
      flex: 1;
      height: 10px;
      -webkit-appearance: none;
      background: rgba(255,255,255,0.15);
      border-radius: 5px;
      outline: none;
    }
    .vol-slider::-webkit-slider-thumb {
      -webkit-appearance: none;
      width: 24px;
      height: 24px;
      border-radius: 50%;
      background: #2563EB;
      border: 2px solid white;
      cursor: pointer;
    }

    /* Search & Inputs */
    .search-bar { display: flex; gap: 8px; margin-bottom: 14px; }
    .input-box {
      flex: 1;
      background: rgba(255,255,255,0.08);
      border: 1px solid rgba(255,255,255,0.14);
      border-radius: 12px;
      padding: 10px 14px;
      color: white;
      font-size: 14px;
      outline: none;
    }
    .primary-btn {
      background: #2563EB;
      border: none;
      color: white;
      font-weight: 700;
      border-radius: 12px;
      padding: 0 16px;
      cursor: pointer;
    }

    /* Auto-discovery notification banner */
    .reconnect-banner {
      background: #D97706;
      color: white;
      padding: 8px 14px;
      font-size: 12px;
      display: none;
      align-items: center;
      justify-content: space-between;
      border-radius: 8px;
      margin-bottom: 12px;
    }
  </style>
</head>
<body>

  <header>
    <div class="brand">
      <div class="brand-dot"></div>
      <span>TCL Smart Remote</span>
    </div>
    <div class="status-badge" id="tvStatusBadge">Conectado</div>
  </header>

  <div style="padding: 12px 16px 0 16px;">
    <div id="reconnectBanner" class="reconnect-banner">
      <span>Buscando TV en la red Wi-Fi...</span>
      <button onclick="scanForTvIp()" style="background:white; color:#B45309; border:none; padding:3px 8px; border-radius:4px; font-weight:bold; cursor:pointer;">Buscar</button>
    </div>
  </div>

  <!-- TAB 1: APPS Y CONTENIDO -->
  <div id="tabApps" class="tab-content active">
    <div class="section-title">Perfil Activo en la TV</div>
    <div class="profile-row" id="profilesRow">
      <!-- Inyectado por JS -->
    </div>

    <div class="section-title">Buscar o Enviar a la TV</div>
    <div class="search-bar">
      <input type="text" id="queryInput" class="input-box" placeholder="Título, video YouTube o URL...">
      <button class="primary-btn" onclick="sendDeepLink()">Abrir</button>
    </div>

    <div class="section-title">Accesos Rápidos a Streaming</div>
    <div class="apps-grid">
      <div class="app-card" style="color:white;" onclick="launchApp('com.netflix.ninja', 'Netflix', 1)">NETFLIX</div>
      <div class="app-card" style="color:white;" onclick="launchApp('com.google.android.youtube.tv', 'YouTube', 1)">YouTube</div>
      <div class="app-card" style="color:white;" onclick="launchApp('com.amazon.amazonvideo.livingroom', 'Prime Video', 1)">prime video</div>
      <div class="app-card" style="color:white;" onclick="launchApp('com.disney.disneyplus', 'Disney+', 1)">Disney+</div>
      <div class="app-card" style="color:white;" onclick="launchApp('com.wbd.stream', 'Max', 1)">max</div>
      <div class="app-card" style="color:white;" onclick="launchApp('com.univision.prendetv', 'ViX', 1)">ViX</div>
    </div>

    <div class="section-title">Continuar Viendo (Recientes)</div>
    <div id="recentContainer"></div>

    <div class="section-title">Todas las Apps de la TV</div>
    <input type="text" id="filterAppInput" class="input-box" style="margin-bottom:10px; width:100%;" placeholder="Filtrar apps instaladas..." oninput="filterInstalledApps()">
    <div id="installedAppsContainer" style="display:grid; grid-template-columns: repeat(2, 1fr); gap:8px;"></div>
  </div>

  <!-- TAB 2: CONTROL TOUCH Y VOLUMEN -->
  <div id="tabRemote" class="tab-content">
    <div class="section-title">Volumen de la TV</div>
    <div class="vol-box">
      <button class="circle-btn" onclick="sendVol('down')">Vol -</button>
      <input type="range" id="volSlider" class="vol-slider" min="0" max="100" value="15" onchange="setVolumeValue(this.value)">
      <button class="circle-btn" onclick="sendVol('up')">Vol +</button>
      <button class="circle-btn" onclick="sendVol('mute')">Mute</button>
    </div>

    <div class="section-title">Cruceta D-Pad & Control Remoto</div>
    <div class="dpad-container">
      <div class="dpad">
        <button class="dpad-btn dpad-up" onclick="sendKey('UP')">▲</button>
        <button class="dpad-btn dpad-down" onclick="sendKey('DOWN')">▼</button>
        <button class="dpad-btn dpad-left" onclick="sendKey('LEFT')">◀</button>
        <button class="dpad-btn dpad-right" onclick="sendKey('RIGHT')">▶</button>
        <button class="dpad-btn dpad-center" onclick="sendKey('ENTER')">OK</button>
      </div>
    </div>

    <div class="action-row">
      <button class="circle-btn" onclick="sendKey('BACK')">
        <span style="font-size:18px;">↩</span>Atrás
      </button>
      <button class="circle-btn" onclick="sendKey('HOME')">
        <span style="font-size:18px;">🏠</span>Home
      </button>
      <button class="circle-btn" onclick="sendKey('PLAY_PAUSE')">
        <span style="font-size:18px;">⏯</span>Play
      </button>
      <button class="circle-btn" onclick="sendKey('SETTINGS')">
        <span style="font-size:18px;">⚙</span>Ajustes
      </button>
    </div>
  </div>

  <!-- TAB 3: FONDO TV (WALLPAPER) -->
  <div id="tabWallpaper" class="tab-content">
    <div class="section-title">Subir Foto de mi Celular a la Pantalla</div>
    <div class="glass-card" style="text-align:center;">
      <p style="font-size:13px; color:#94A3B8; margin-bottom:12px;">Pon cualquier fotografía de tu galería en pantalla completa en la TV:</p>
      <input type="file" id="phonePhotoPicker" accept="image/*" style="display:none;" onchange="uploadPhonePhoto(this)">
      <button class="primary-btn" style="padding:12px 24px; font-size:14px;" onclick="document.getElementById('phonePhotoPicker').click()">
        📷 Elegir Foto de mi Celular
      </button>
      <p id="uploadStatusText" style="font-size:11px; color:#10B981; margin-top:8px;"></p>
    </div>

    <div class="section-title">Wallpapers Cinematográficos Integrados</div>
    <div id="presetWallpapersContainer" style="display:flex; flex-direction:column; gap:8px;"></div>
  </div>

  <!-- BOTTOM BAR -->
  <nav class="bottom-bar">
    <button class="nav-btn active" onclick="switchTab('tabApps', this)">
      <svg viewBox="0 0 24 24"><rect x="2" y="7" width="20" height="15" rx="2" ry="2"></rect><polyline points="17 2 12 7 7 2"></polyline></svg>
      <span>Apps</span>
    </button>
    <button class="nav-btn" onclick="switchTab('tabRemote', this)">
      <svg viewBox="0 0 24 24"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
      <span>Control</span>
    </button>
    <button class="nav-btn" onclick="switchTab('tabWallpaper', this)">
      <svg viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>
      <span>Fondo TV</span>
    </button>
  </nav>

  <script>
    let appState = null;
    let allInstalledApps = [];

    function vibrate() {
      if (navigator.vibrate) navigator.vibrate(15);
    }

    function switchTab(tabId, btn) {
      vibrate();
      document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));
      document.querySelectorAll('.nav-btn').forEach(el => el.classList.remove('active'));
      document.getElementById(tabId).classList.add('active');
      btn.classList.add('active');
    }

    async function loadStatus() {
      try {
        const res = await fetch('/api/status', { signal: AbortSignal.timeout(3000) });
        if (!res.ok) throw new Error('Status error');
        appState = await res.json();
        renderState();
        document.getElementById('reconnectBanner').style.display = 'none';
        document.getElementById('tvStatusBadge').innerText = 'Conectado';
        document.getElementById('tvStatusBadge').style.background = 'rgba(37,99,235,0.2)';
        localStorage.setItem('last_known_tv_ip', window.location.hostname);
      } catch (e) {
        document.getElementById('tvStatusBadge').innerText = 'Desconectado';
        document.getElementById('tvStatusBadge').style.background = 'rgba(239,68,68,0.2)';
        document.getElementById('reconnectBanner').style.display = 'flex';
      }
    }

    function renderState() {
      if (!appState) return;

      // Perfiles
      const profilesRow = document.getElementById('profilesRow');
      profilesRow.innerHTML = '';
      appState.profiles.forEach(p => {
        const isActive = p.id === appState.activeProfile.id;
        const chip = document.createElement('div');
        chip.className = 'profile-chip' + (isActive ? ' active' : '');
        chip.innerText = p.emoji + ' ' + p.name;
        chip.onclick = () => setProfile(p.id);
        profilesRow.appendChild(chip);
      });

      // Volumen
      const volSlider = document.getElementById('volSlider');
      volSlider.max = appState.volume.max;
      volSlider.value = appState.volume.current;

      // Recientes
      const recentContainer = document.getElementById('recentContainer');
      recentContainer.innerHTML = '';
      (appState.lastWatched || []).forEach(item => {
        const card = document.createElement('div');
        card.className = 'glass-card';
        card.style.display = 'flex';
        card.style.justifyContent = 'space-between';
        card.style.alignItems = 'center';
        card.style.cursor = 'pointer';
        card.onclick = () => launchApp(item.packageName, item.title, item.profileIndex);
        card.innerHTML = '<div><div style="font-weight:700; font-size:14px;">' + item.title + '</div><div style="font-size:11px; color:#94A3B8;">' + item.subtitle + '</div></div><span style="font-size:18px;">▶</span>';
        recentContainer.appendChild(card);
      });

      // Wallpapers
      const wpContainer = document.getElementById('presetWallpapersContainer');
      wpContainer.innerHTML = '';
      (appState.wallpapers || []).forEach(wp => {
        if (wp.id === 'wp_custom') return;
        const isActive = wp.id === appState.activeWallpaper.id;
        const card = document.createElement('div');
        card.className = 'glass-card';
        card.style.display = 'flex';
        card.style.justifyContent = 'space-between';
        card.style.alignItems = 'center';
        card.style.cursor = 'pointer';
        card.onclick = () => setPresetWallpaper(wp.id);
        card.innerHTML = '<div><div style="font-weight:700; font-size:14px;">' + wp.name + (isActive ? ' (Activo)' : '') + '</div><div style="font-size:11px; color:#94A3B8;">' + wp.description + '</div></div><button class="primary-btn" style="padding:6px 12px; font-size:11px;">Aplicar</button>';
        wpContainer.appendChild(card);
      });

      allInstalledApps = appState.installedApps || [];
      renderInstalledApps(allInstalledApps);
    }

    function renderInstalledApps(apps) {
      const container = document.getElementById('installedAppsContainer');
      container.innerHTML = '';
      apps.forEach(app => {
        const item = document.createElement('div');
        item.className = 'glass-card';
        item.style.padding = '10px';
        item.style.margin = '0';
        item.style.cursor = 'pointer';
        item.style.textAlign = 'center';
        item.style.fontSize = '12px';
        item.style.fontWeight = 'bold';
        item.innerText = app.label;
        item.onclick = () => launchApp(app.packageName, app.label, 1);
        container.appendChild(item);
      });
    }

    function filterInstalledApps() {
      const query = document.getElementById('filterAppInput').value.toLowerCase();
      const filtered = allInstalledApps.filter(a => a.label.toLowerCase().includes(query));
      renderInstalledApps(filtered);
    }

    async function sendKey(k) {
      vibrate();
      await fetch('/api/key', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ key: k })
      });
    }

    async function sendVol(action) {
      vibrate();
      await fetch('/api/volume', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: action })
      });
      loadStatus();
    }

    async function setVolumeValue(val) {
      await fetch('/api/volume', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'set', value: parseInt(val) })
      });
    }

    async function launchApp(pkg, title, profileIdx) {
      vibrate();
      await fetch('/api/launch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ packageName: pkg, title: title, profileIndex: profileIdx || 1 })
      });
    }

    async function sendDeepLink() {
      const q = document.getElementById('queryInput').value.trim();
      if (!q) return;
      vibrate();
      await fetch('/api/deeplink', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ query: q, url: q.startsWith('http') ? q : '' })
      });
      document.getElementById('queryInput').value = '';
    }

    async function setProfile(id) {
      vibrate();
      await fetch('/api/profile', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ profileId: id })
      });
      loadStatus();
    }

    async function setPresetWallpaper(wpId) {
      vibrate();
      await fetch('/api/wallpaper', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'preset', wallpaperId: wpId })
      });
      document.getElementById('uploadStatusText').innerText = '¡Fondo cambiado exitosamente en la TV!';
      loadStatus();
    }

    function uploadPhonePhoto(input) {
      if (!input.files || !input.files[0]) return;
      const file = input.files[0];
      document.getElementById('uploadStatusText').innerText = 'Optimizando foto para pantalla de TV...';

      const img = new Image();
      const reader = new FileReader();
      reader.onload = function(e) {
        img.onload = async function() {
          const maxW = 1920, maxH = 1080;
          let w = img.width, h = img.height;
          if (w > maxW || h > maxH) {
            const ratio = Math.min(maxW / w, maxH / h);
            w = Math.round(w * ratio);
            h = Math.round(h * ratio);
          }
          const canvas = document.createElement('canvas');
          canvas.width = w;
          canvas.height = h;
          const ctx = canvas.getContext('2d');
          ctx.drawImage(img, 0, 0, w, h);
          const resizedBase64 = canvas.toDataURL('image/jpeg', 0.85);

          document.getElementById('uploadStatusText').innerText = 'Enviando a la TV...';
          await fetch('/api/wallpaper', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ action: 'upload', base64: resizedBase64 })
          });
          document.getElementById('uploadStatusText').innerText = '¡Fondo de pantalla actualizado en la TV TCL!';
          loadStatus();
        };
        img.src = e.target.result;
      };
      reader.readAsDataURL(file);
    }

    // Auto-descubrimiento en caso de cambio de IP
    async function scanForTvIp() {
      const currentHost = window.location.hostname;
      const parts = currentHost.split('.');
      if (parts.length !== 4) return;
      const subnet = parts[0] + '.' + parts[1] + '.' + parts[2] + '.';

      document.getElementById('reconnectBanner').innerHTML = '<span>Escaneando subred ' + subnet + '* buscando TV...</span>';

      for (let i = 2; i <= 254; i++) {
        const testIp = subnet + i;
        fetch('http://' + testIp + ':8888/api/status', { mode: 'no-cors', signal: AbortSignal.timeout(400) })
          .then(() => {
            window.location.href = 'http://' + testIp + ':8888/';
          })
          .catch(() => {});
      }
    }

    setInterval(loadStatus, 4000);
    loadStatus();
  </script>
</body>
</html>
        """.trimIndent()

        sendResponse(output, 200, "text/html; charset=utf-8", html)
    }

    private fun sendJsonResponse(output: OutputStream, json: String) {
        sendResponse(output, 200, "application/json; charset=utf-8", json)
    }

    private fun sendResponse(output: OutputStream, statusCode: Int, contentType: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $statusCode OK\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type\r\n" +
                "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }
}
