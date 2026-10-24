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

        @Volatile
        private var instance: TvRemoteWebServer? = null

        fun getInstance(context: Context, repository: LauncherRepository? = null): TvRemoteWebServer {
            return instance ?: synchronized(this) {
                instance ?: TvRemoteWebServer(
                    context.applicationContext,
                    repository ?: LauncherRepository(context.applicationContext)
                ).also { instance = it }
            }
        }

        fun ensureStarted(context: Context, repository: LauncherRepository? = null): TvRemoteWebServer {
            val server = getInstance(context, repository)
            if (!isServerRunning) {
                server.start()
            }
            return server
        }
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
  <title>TCL TV Remote</title>
  <link rel="manifest" href="/manifest.json">
  <meta name="theme-color" content="#07080B">
  <meta name="apple-mobile-web-app-capable" content="yes">
  <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
  <style>
    :root {
      --bg: #07080B;
      --chassis: #0E1015;
      --card-bg: rgba(22, 24, 31, 0.78);
      --card-border: rgba(255, 255, 255, 0.08);
      --card-bevel: rgba(255, 255, 255, 0.14);
      --titanium: #1C1F28;
      --titanium-light: #282C38;
      --accent: #2563EB;
      --accent-glow: rgba(37, 99, 235, 0.4);
      --text: #F8FAFC;
      --text-muted: #8E95A5;
      --active-green: #10B981;
    }

    * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; margin: 0; padding: 0; user-select: none; }
    
    body {
      background: radial-gradient(circle at 50% 0%, #151922 0%, #07080B 65%);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Segoe UI", Roboto, sans-serif;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      overflow-x: hidden;
      padding-bottom: 84px;
    }

    /* TOP HEADER */
    header {
      background: rgba(14, 16, 21, 0.82);
      backdrop-filter: blur(28px);
      -webkit-backdrop-filter: blur(28px);
      padding: 12px 18px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid var(--card-border);
      position: sticky;
      top: 0;
      z-index: 50;
    }
    .brand-wrap { display: flex; align-items: center; gap: 9px; }
    .led-indicator {
      width: 9px;
      height: 9px;
      border-radius: 50%;
      background: var(--active-green);
      box-shadow: 0 0 10px var(--active-green);
      animation: pulseLed 2s infinite ease-in-out;
    }
    @keyframes pulseLed {
      0%, 100% { opacity: 1; transform: scale(1); }
      50% { opacity: 0.6; transform: scale(0.85); }
    }
    .brand-title { font-weight: 700; font-size: 15px; letter-spacing: -0.3px; }
    .brand-sub { font-size: 11px; color: var(--text-muted); font-weight: 500; }
    
    .status-pill {
      font-size: 11px;
      font-weight: 600;
      padding: 4px 10px;
      border-radius: 20px;
      background: rgba(16, 185, 129, 0.12);
      color: #34D399;
      border: 1px solid rgba(16, 185, 129, 0.28);
      display: flex;
      align-items: center;
      gap: 5px;
    }

    /* QUICK PROFILES STRIP */
    .profiles-strip {
      display: flex;
      gap: 7px;
      overflow-x: auto;
      padding: 10px 18px 4px 18px;
      scrollbar-width: none;
    }
    .profiles-strip::-webkit-scrollbar { display: none; }
    .profile-pill {
      background: rgba(255,255,255,0.05);
      border: 1px solid rgba(255,255,255,0.08);
      border-radius: 18px;
      padding: 5px 12px;
      font-size: 12px;
      font-weight: 600;
      color: var(--text-muted);
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 5px;
      transition: all 0.15s ease;
      white-space: nowrap;
    }
    .profile-pill.active {
      background: rgba(255,255,255,0.14);
      color: white;
      border-color: rgba(255,255,255,0.25);
      box-shadow: 0 2px 8px rgba(0,0,0,0.4);
    }

    /* TAB CONTENTS */
    .tab-content { display: none; padding: 12px 18px; flex: 1; }
    .tab-content.active { display: block; animation: fadeIn 0.2s ease; }
    @keyframes fadeIn { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: translateY(0); } }

    /* SEGMENTED CONTROL (Modo Clickpad vs Cruceta) */
    .segmented-control {
      display: flex;
      background: rgba(0,0,0,0.4);
      border: 1px solid rgba(255,255,255,0.06);
      border-radius: 12px;
      padding: 3px;
      margin-bottom: 16px;
    }
    .seg-btn {
      flex: 1;
      padding: 7px 0;
      font-size: 12px;
      font-weight: 600;
      color: var(--text-muted);
      background: transparent;
      border: none;
      border-radius: 9px;
      cursor: pointer;
      transition: all 0.15s ease;
    }
    .seg-btn.active {
      background: rgba(255,255,255,0.12);
      color: white;
      box-shadow: 0 2px 6px rgba(0,0,0,0.5);
    }

    /* APPLE TV TOUCHPAD / CLICKPAD */
    .touchpad-outer {
      width: 100%;
      max-width: 330px;
      margin: 0 auto 16px auto;
      aspect-ratio: 1 / 1.05;
      background: radial-gradient(circle at 50% 50%, #1E212A 0%, #12141A 100%);
      border: 1px solid var(--card-bevel);
      border-radius: 44px;
      box-shadow: 0 16px 36px rgba(0,0,0,0.65), inset 0 1px 0 rgba(255,255,255,0.12);
      position: relative;
      touch-action: none;
      overflow: hidden;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .touchpad-outer.touch-active {
      border-color: rgba(255,255,255,0.3);
      box-shadow: 0 18px 40px rgba(0,0,0,0.8), inset 0 0 20px rgba(255,255,255,0.05);
    }
    /* Textura de micro-puntos estilo Bang & Olufsen / Apple */
    .touchpad-mesh {
      position: absolute;
      inset: 0;
      background-image: radial-gradient(rgba(255, 255, 255, 0.08) 1px, transparent 0);
      background-size: 14px 14px;
      pointer-events: none;
    }

    /* Zonas de toque en el Clickpad */
    .pad-zone {
      position: absolute;
      background: transparent;
      border: none;
      color: rgba(255,255,255,0.35);
      font-size: 14px;
      font-weight: 700;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: color 0.1s, background 0.1s;
    }
    .pad-zone:active { color: white; background: rgba(255,255,255,0.08); }
    .pad-zone-up { top: 0; left: 0; right: 0; height: 32%; }
    .pad-zone-down { bottom: 0; left: 0; right: 0; height: 32%; }
    .pad-zone-left { top: 32%; bottom: 32%; left: 0; width: 32%; }
    .pad-zone-right { top: 32%; bottom: 32%; right: 0; width: 32%; }

    /* Anillo Central OK */
    .pad-center-ring {
      width: 104px;
      height: 104px;
      border-radius: 50%;
      background: radial-gradient(circle at 50% 30%, #282C38 0%, #1A1C24 100%);
      border: 1px solid rgba(255,255,255,0.18);
      box-shadow: 0 6px 18px rgba(0,0,0,0.5), inset 0 1px 0 rgba(255,255,255,0.2);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 15px;
      font-weight: 800;
      letter-spacing: 0.5px;
      color: white;
      z-index: 5;
      cursor: pointer;
      transition: transform 0.1s, background 0.1s;
    }
    .pad-center-ring:active {
      transform: scale(0.95);
      background: radial-gradient(circle at 50% 30%, #323746 0%, #1F222C 100%);
      box-shadow: 0 2px 8px rgba(0,0,0,0.6);
    }

    /* MODO CRUCETA PRO (Alternativa clásica) */
    .dpad-cross-container {
      display: none;
      width: 240px;
      height: 240px;
      margin: 0 auto 16px auto;
      position: relative;
    }
    .dpad-cross-btn {
      position: absolute;
      background: #1C1F28;
      border: 1px solid var(--card-bevel);
      color: white;
      border-radius: 16px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
      cursor: pointer;
      box-shadow: 0 8px 18px rgba(0,0,0,0.5);
    }
    .dpad-cross-btn:active { background: #2A2E3B; transform: scale(0.96); }
    .cross-up { top: 0; left: 82px; width: 76px; height: 68px; }
    .cross-down { bottom: 0; left: 82px; width: 76px; height: 68px; }
    .cross-left { top: 82px; left: 0; width: 68px; height: 76px; }
    .cross-right { top: 82px; right: 0; width: 68px; height: 76px; }
    .cross-ok {
      top: 82px; left: 82px; width: 76px; height: 76px;
      border-radius: 50%;
      background: var(--accent);
      border: none;
      box-shadow: 0 0 20px var(--accent-glow);
      font-size: 15px;
      font-weight: 800;
    }

    /* HARDWARE CONTROLS GRID (2 Columnas ergonómicas estilo Apple TV Remote) */
    .hardware-controls-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 14px;
      max-width: 330px;
      margin: 0 auto 16px auto;
    }
    .hardware-col { display: flex; flex-direction: column; gap: 12px; }

    /* Botones esculpidos en titanio */
    .titanium-btn {
      height: 58px;
      border-radius: 20px;
      background: linear-gradient(180deg, #1C1F28 0%, #15171E 100%);
      border: 1px solid var(--card-bevel);
      box-shadow: 0 6px 14px rgba(0,0,0,0.4), inset 0 1px 0 rgba(255,255,255,0.12);
      color: white;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      font-size: 14px;
      font-weight: 700;
      cursor: pointer;
      transition: all 0.1s ease;
    }
    .titanium-btn svg { width: 20px; height: 20px; stroke: currentColor; fill: none; stroke-width: 2.2; }
    .titanium-btn:active {
      transform: scale(0.96);
      background: #13151B;
      box-shadow: 0 2px 6px rgba(0,0,0,0.5);
    }

    /* ROCKER VERTICAL DE VOLUMEN (Estilo Apple TV Remote físico) */
    .volume-rocker {
      height: 128px;
      border-radius: 28px;
      background: linear-gradient(180deg, #1C1F28 0%, #15171E 100%);
      border: 1px solid var(--card-bevel);
      box-shadow: 0 8px 18px rgba(0,0,0,0.45), inset 0 1px 0 rgba(255,255,255,0.12);
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
    .rocker-btn {
      flex: 1;
      background: transparent;
      border: none;
      color: white;
      font-size: 20px;
      font-weight: 700;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: background 0.1s;
    }
    .rocker-btn:active { background: rgba(255,255,255,0.1); }
    .rocker-divider {
      height: 1px;
      background: rgba(255,255,255,0.08);
      margin: 0 16px;
    }

    /* SLIDER FINO DE VOLUMEN CON PORCENTAJE */
    .volume-fine-strip {
      max-width: 330px;
      margin: 0 auto 18px auto;
      background: rgba(22, 24, 31, 0.6);
      border: 1px solid var(--card-border);
      border-radius: 16px;
      padding: 10px 14px;
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .vol-label-text { font-size: 11px; font-weight: 700; color: var(--text-muted); min-width: 30px; }
    .vol-fine-slider {
      flex: 1;
      height: 6px;
      -webkit-appearance: none;
      background: rgba(255,255,255,0.12);
      border-radius: 3px;
      outline: none;
    }
    .vol-fine-slider::-webkit-slider-thumb {
      -webkit-appearance: none;
      width: 18px;
      height: 18px;
      border-radius: 50%;
      background: white;
      box-shadow: 0 2px 6px rgba(0,0,0,0.6);
      cursor: pointer;
    }

    /* ONE-TAP DOCK (4 Accesos rápidos fijos estilo Apple TV) */
    .one-tap-dock {
      max-width: 330px;
      margin: 0 auto;
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 8px;
    }
    .dock-btn {
      height: 48px;
      border-radius: 14px;
      background: rgba(255,255,255,0.06);
      border: 1px solid rgba(255,255,255,0.09);
      color: white;
      font-size: 12px;
      font-weight: 800;
      letter-spacing: -0.2px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.1s ease;
    }
    .dock-btn:active { transform: scale(0.94); border-color: white; }
    .dock-netflix { color: #E50914; }
    .dock-youtube { color: #FF0000; }
    .dock-prime { color: #00A8E1; font-size: 11px; }
    .dock-disney { color: #60A5FA; }

    /* TAB 2: APPS & RECIENTES STYLING */
    .section-header {
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.8px;
      text-transform: uppercase;
      color: var(--text-muted);
      margin: 18px 0 10px 0;
    }
    .search-pill-bar {
      display: flex;
      gap: 8px;
      margin-bottom: 16px;
    }
    .search-field {
      flex: 1;
      background: rgba(255,255,255,0.07);
      border: 1px solid rgba(255,255,255,0.12);
      border-radius: 14px;
      padding: 11px 14px;
      color: white;
      font-size: 14px;
      outline: none;
    }
    .search-btn {
      background: var(--accent);
      border: none;
      color: white;
      font-weight: 700;
      border-radius: 14px;
      padding: 0 16px;
      cursor: pointer;
    }
    .apps-stream-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 10px;
      margin-bottom: 16px;
    }
    .stream-card {
      background: linear-gradient(145deg, #1A1D24 0%, #12141A 100%);
      border: 1px solid var(--card-border);
      border-radius: 20px;
      height: 68px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 800;
      font-size: 16px;
      cursor: pointer;
      box-shadow: 0 4px 12px rgba(0,0,0,0.3);
      transition: transform 0.1s, border-color 0.1s;
    }
    .stream-card:active { transform: scale(0.96); border-color: white; }

    /* BOTTOM TAB BAR (Nativa iOS con blur translúcido) */
    nav.ios-tab-bar {
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      height: 68px;
      background: rgba(14, 16, 21, 0.88);
      backdrop-filter: blur(28px);
      -webkit-backdrop-filter: blur(28px);
      display: flex;
      justify-content: space-around;
      align-items: center;
      border-top: 1px solid var(--card-border);
      z-index: 100;
      padding-bottom: env(safe-area-inset-bottom);
    }
    .tab-item {
      background: transparent;
      border: none;
      color: var(--text-muted);
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
      font-size: 11px;
      font-weight: 600;
      cursor: pointer;
      flex: 1;
      padding: 8px 0;
      transition: color 0.15s ease;
    }
    .tab-item svg { width: 22px; height: 22px; stroke: currentColor; fill: none; stroke-width: 2.2; }
    .tab-item.active { color: #60A5FA; }

    /* Tarjetas generales de cristal */
    .glass-tile {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: 18px;
      padding: 14px;
      margin-bottom: 10px;
    }
  </style>
</head>
<body>

  <!-- HEADER CON ESTADO EN TIEMPO REAL -->
  <header>
    <div class="brand-wrap">
      <div class="led-indicator" id="ledStatus"></div>
      <div>
        <div class="brand-title">TCL Smart TV</div>
        <div class="brand-sub" id="tvSubtext">Apple TV Remote Mode</div>
      </div>
    </div>
    <div class="status-pill" id="tvStatusBadge">
      <span>●</span>
      <span id="tvStatusText">Conectado</span>
    </div>
  </header>

  <!-- STRIP DE PERFILES RÁPIDOS EN CRISTAL -->
  <div class="profiles-strip" id="profilesRow">
    <!-- Rellenado dinámicamente -->
  </div>

  <!-- TAB 1: CONTROL REMOTO (VISTA PRINCIPAL ESTILO APPLE TV / BEOREMOTE) -->
  <div id="tabRemote" class="tab-content active">
    
    <!-- SELECTOR DE MODO DUAL -->
    <div class="segmented-control">
      <button class="seg-btn active" id="btnModeTouch" onclick="setRemoteMode('touch')">Touchpad Apple TV</button>
      <button class="seg-btn" id="btnModeCross" onclick="setRemoteMode('cross')">Cruceta Pro</button>
    </div>

    <!-- MODO A: GRAN TOUCHPAD / CLICKPAD TÁCTIL (Deslizar o Tocar) -->
    <div class="touchpad-outer" id="appleTouchpad">
      <div class="touchpad-mesh"></div>
      
      <!-- Indicadores y Zonas direccionales táctiles -->
      <button class="pad-zone pad-zone-up" onclick="sendKey('UP')">▲</button>
      <button class="pad-zone pad-zone-down" onclick="sendKey('DOWN')">▼</button>
      <button class="pad-zone pad-zone-left" onclick="sendKey('LEFT')">◀</button>
      <button class="pad-zone pad-zone-right" onclick="sendKey('RIGHT')">▶</button>
      
      <!-- Anillo central de Clickpad OK -->
      <div class="pad-center-ring" onclick="sendKey('ENTER')">
        <span>OK</span>
      </div>
    </div>

    <!-- MODO B: CRUCETA CLÁSICA ALTERNATIVA -->
    <div class="dpad-cross-container" id="crossContainer">
      <button class="dpad-cross-btn cross-up" onclick="sendKey('UP')">▲</button>
      <button class="dpad-cross-btn cross-down" onclick="sendKey('DOWN')">▼</button>
      <button class="dpad-cross-btn cross-left" onclick="sendKey('LEFT')">◀</button>
      <button class="dpad-cross-btn cross-right" onclick="sendKey('RIGHT')">▶</button>
      <button class="dpad-cross-btn cross-ok" onclick="sendKey('ENTER')">OK</button>
    </div>

    <!-- BOTONES ERGONÓMICOS EN 2 COLUMNAS DE TITANIO -->
    <div class="hardware-controls-grid">
      <!-- Columna Izquierda: Atrás, Play/Pausa, Mute -->
      <div class="hardware-col">
        <button class="titanium-btn" onclick="sendKey('BACK')">
          <svg viewBox="0 0 24 24"><polyline points="15 18 9 12 15 6"></polyline></svg>
          <span>Atrás</span>
        </button>
        <button class="titanium-btn" onclick="sendKey('PLAY_PAUSE')">
          <svg viewBox="0 0 24 24"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
          <span>Play/Pausa</span>
        </button>
        <button class="titanium-btn" onclick="sendVol('mute')">
          <svg viewBox="0 0 24 24"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"></polygon><line x1="23" y1="9" x2="17" y2="15"></line><line x1="17" y1="9" x2="23" y2="15"></line></svg>
          <span>Mute</span>
        </button>
      </div>

      <!-- Columna Derecha: Home TV y Rocker Vertical de Volumen -->
      <div class="hardware-col">
        <button class="titanium-btn" onclick="sendKey('HOME')">
          <svg viewBox="0 0 24 24"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path><polyline points="9 22 9 12 15 12 15 22"></polyline></svg>
          <span>Home TV</span>
        </button>
        <div class="volume-rocker">
          <button class="rocker-btn" onclick="sendVol('up')">＋</button>
          <div class="rocker-divider"></div>
          <button class="rocker-btn" onclick="sendVol('down')">－</button>
        </div>
      </div>
    </div>

    <!-- SLIDER FINO CON PORCENTAJE REAL DE VOLUMEN -->
    <div class="volume-fine-strip">
      <span class="vol-label-text">VOL</span>
      <input type="range" id="volSlider" class="vol-fine-slider" min="0" max="100" value="20" onchange="setVolumeValue(this.value)">
      <span class="vol-label-text" id="volPercentageText" style="text-align:right;">20%</span>
    </div>

    <!-- ONE-TAP DOCK (4 Apps directas sin cambiar de pestaña) -->
    <div class="one-tap-dock">
      <button class="dock-btn dock-netflix" onclick="launchApp('com.netflix.ninja', 'Netflix', 1)">NETFLIX</button>
      <button class="dock-btn dock-youtube" onclick="launchApp('com.google.android.youtube.tv', 'YouTube', 1)">YouTube</button>
      <button class="dock-btn dock-prime" onclick="launchApp('com.amazon.amazonvideo.livingroom', 'Prime Video', 1)">Prime</button>
      <button class="dock-btn dock-disney" onclick="launchApp('com.disney.disneyplus', 'Disney+', 1)">Disney+</button>
    </div>
  </div>

  <!-- TAB 2: APPS & RECIENTES (LANZADOR VISUAL Y BÚSQUEDA) -->
  <div id="tabApps" class="tab-content">
    <div class="section-header">Búsqueda Rápida en la TV</div>
    <div class="search-pill-bar">
      <input type="text" id="queryInput" class="search-field" placeholder="Buscar película, serie o video YouTube...">
      <button class="search-btn" onclick="sendDeepLink()">Abrir</button>
    </div>

    <div class="section-header">Plataformas de Streaming</div>
    <div class="apps-stream-grid">
      <div class="stream-card" style="color:#E50914;" onclick="launchApp('com.netflix.ninja', 'Netflix', 1)">NETFLIX</div>
      <div class="stream-card" style="color:#FF0000;" onclick="launchApp('com.google.android.youtube.tv', 'YouTube', 1)">YouTube</div>
      <div class="stream-card" style="color:#00A8E1;" onclick="launchApp('com.amazon.amazonvideo.livingroom', 'Prime Video', 1)">prime video</div>
      <div class="stream-card" style="color:#60A5FA;" onclick="launchApp('com.disney.disneyplus', 'Disney+', 1)">Disney+</div>
      <div class="stream-card" style="color:#A855F7;" onclick="launchApp('com.wbd.stream', 'Max', 1)">max</div>
      <div class="stream-card" style="color:#F97316;" onclick="launchApp('com.univision.prendetv', 'ViX', 1)">ViX</div>
    </div>

    <div class="section-header">Continuar Viendo (Recientes)</div>
    <div id="recentContainer"></div>

    <div class="section-header">Todas las Apps Instaladas</div>
    <input type="text" id="filterAppInput" class="search-field" style="margin-bottom:10px; width:100%;" placeholder="Filtrar aplicaciones..." oninput="filterInstalledApps()">
    <div id="installedAppsContainer" style="display:grid; grid-template-columns: repeat(2, 1fr); gap:8px;"></div>
  </div>

  <!-- TAB 3: FONDO DE PANTALLA (WALLPAPER STUDIO) -->
  <div id="tabWallpaper" class="tab-content">
    <div class="section-header">Foto Personalizada desde tu Celular</div>
    <div class="glass-tile" style="text-align:center;">
      <p style="font-size:13px; color:var(--text-muted); margin-bottom:14px;">Elige cualquier foto de tu galería para proyectarla en pantalla completa en tu TV TCL:</p>
      <input type="file" id="phonePhotoPicker" accept="image/*" style="display:none;" onchange="uploadPhonePhoto(this)">
      <button class="search-btn" style="padding:13px 26px; font-size:14px; border-radius:16px;" onclick="document.getElementById('phonePhotoPicker').click()">
        📷 Subir Foto de mi Galería
      </button>
      <p id="uploadStatusText" style="font-size:11px; color:#10B981; margin-top:10px;"></p>
    </div>

    <div class="section-header">Colección Panorámica Integrada</div>
    <div id="presetWallpapersContainer" style="display:flex; flex-direction:column; gap:8px;"></div>
  </div>

  <!-- NAVEGACIÓN INFERIOR ESTILO APP NATIVA iOS -->
  <nav class="ios-tab-bar">
    <button class="tab-item active" id="navRemote" onclick="switchTab('tabRemote', this)">
      <svg viewBox="0 0 24 24"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
      <span>Control</span>
    </button>
    <button class="tab-item" id="navApps" onclick="switchTab('tabApps', this)">
      <svg viewBox="0 0 24 24"><rect x="2" y="7" width="20" height="15" rx="2" ry="2"></rect><polyline points="17 2 12 7 7 2"></polyline></svg>
      <span>Apps & TV</span>
    </button>
    <button class="tab-item" id="navWallpaper" onclick="switchTab('tabWallpaper', this)">
      <svg viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>
      <span>Fondo TV</span>
    </button>
  </nav>

  <script>
    let appState = null;
    let allInstalledApps = [];
    let remoteMode = 'touch';

    function vibrate(ms) {
      try {
        if (navigator.vibrate) navigator.vibrate(ms || 18);
      } catch (_) {}
    }

    function switchTab(tabId, btn) {
      vibrate(14);
      document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));
      document.querySelectorAll('.tab-item').forEach(el => el.classList.remove('active'));
      document.getElementById(tabId).classList.add('active');
      btn.classList.add('active');
    }

    function setRemoteMode(mode) {
      vibrate(14);
      remoteMode = mode;
      const touchPad = document.getElementById('appleTouchpad');
      const cross = document.getElementById('crossContainer');
      const btnTouch = document.getElementById('btnModeTouch');
      const btnCross = document.getElementById('btnModeCross');

      if (mode === 'touch') {
        touchPad.style.display = 'flex';
        cross.style.display = 'none';
        btnTouch.classList.add('active');
        btnCross.classList.remove('active');
      } else {
        touchPad.style.display = 'none';
        cross.style.display = 'block';
        btnCross.classList.add('active');
        btnTouch.classList.remove('active');
      }
    }

    // GESTOS TÁCTILES EN EL TOUCHPAD (SWIPE UP / DOWN / LEFT / RIGHT)
    let tStartX = 0;
    let tStartY = 0;
    let tStartTime = 0;
    let isSwiping = false;

    function initTouchpadGestures() {
      const pad = document.getElementById('appleTouchpad');
      if (!pad) return;

      pad.addEventListener('touchstart', (e) => {
        if (e.touches.length === 1) {
          tStartX = e.touches[0].clientX;
          tStartY = e.touches[0].clientY;
          tStartTime = Date.now();
          isSwiping = false;
          pad.classList.add('touch-active');
        }
      }, { passive: true });

      pad.addEventListener('touchmove', (e) => {
        if (e.touches.length === 1) {
          const dx = e.touches[0].clientX - tStartX;
          const dy = e.touches[0].clientY - tStartY;
          if (Math.abs(dx) > 16 || Math.abs(dy) > 16) {
            isSwiping = true;
          }
        }
      }, { passive: true });

      pad.addEventListener('touchend', (e) => {
        pad.classList.remove('touch-active');
        const elapsed = Date.now() - tStartTime;
        const touch = e.changedTouches[0];
        const dx = touch.clientX - tStartX;
        const dy = touch.clientY - tStartY;
        const dist = Math.hypot(dx, dy);

        if (dist > 38 && elapsed < 650) {
          if (Math.abs(dx) > Math.abs(dy)) {
            if (dx > 0) sendKey('RIGHT');
            else sendKey('LEFT');
          } else {
            if (dy > 0) sendKey('DOWN');
            else sendKey('UP');
          }
        }
      }, { passive: true });
    }

    async function loadStatus() {
      try {
        const res = await fetch('/api/status', { signal: AbortSignal.timeout(2800) });
        if (!res.ok) throw new Error('Status error');
        appState = await res.json();
        renderState();
        document.getElementById('ledStatus').style.background = '#10B981';
        document.getElementById('ledStatus').style.boxShadow = '0 0 10px #10B981';
        document.getElementById('tvStatusText').innerText = 'Conectado';
        document.getElementById('tvStatusBadge').style.background = 'rgba(16, 185, 129, 0.12)';
        document.getElementById('tvStatusBadge').style.color = '#34D399';
      } catch (e) {
        document.getElementById('ledStatus').style.background = '#EF4444';
        document.getElementById('ledStatus').style.boxShadow = '0 0 10px #EF4444';
        document.getElementById('tvStatusText').innerText = 'Reconectando...';
        document.getElementById('tvStatusBadge').style.background = 'rgba(239, 68, 68, 0.12)';
        document.getElementById('tvStatusBadge').style.color = '#F87171';
      }
    }

    function renderState() {
      if (!appState) return;

      // Perfiles
      const profilesRow = document.getElementById('profilesRow');
      profilesRow.innerHTML = '';
      (appState.profiles || []).forEach(p => {
        const isActive = p.id === appState.activeProfile?.id;
        const pill = document.createElement('div');
        pill.className = 'profile-pill' + (isActive ? ' active' : '');
        pill.innerText = (p.emoji || '👤') + ' ' + p.name;
        pill.onclick = () => setProfile(p.id);
        profilesRow.appendChild(pill);
      });

      // Volumen
      const volSlider = document.getElementById('volSlider');
      if (volSlider && appState.volume) {
        volSlider.max = appState.volume.max || 100;
        volSlider.value = appState.volume.current || 0;
        const pct = Math.round((appState.volume.current / (appState.volume.max || 100)) * 100);
        document.getElementById('volPercentageText').innerText = pct + '%';
      }

      // Recientes
      const recentContainer = document.getElementById('recentContainer');
      if (recentContainer) {
        recentContainer.innerHTML = '';
        (appState.lastWatched || []).forEach(item => {
          const card = document.createElement('div');
          card.className = 'glass-tile';
          card.style.display = 'flex';
          card.style.justifyContent = 'space-between';
          card.style.alignItems = 'center';
          card.style.cursor = 'pointer';
          card.onclick = () => launchApp(item.packageName, item.title, item.profileIndex);
          card.innerHTML = '<div><div style="font-weight:700; font-size:14px;">' + item.title + '</div><div style="font-size:11px; color:#8E95A5;">' + (item.subtitle || '') + '</div></div><span style="font-size:18px; color:#60A5FA;">▶</span>';
          recentContainer.appendChild(card);
        });
      }

      // Wallpapers
      const wpContainer = document.getElementById('presetWallpapersContainer');
      if (wpContainer) {
        wpContainer.innerHTML = '';
        (appState.wallpapers || []).forEach(wp => {
          if (wp.id === 'wp_custom') return;
          const isActive = wp.id === appState.activeWallpaper?.id;
          const card = document.createElement('div');
          card.className = 'glass-tile';
          card.style.display = 'flex';
          card.style.justifyContent = 'space-between';
          card.style.alignItems = 'center';
          card.style.cursor = 'pointer';
          card.onclick = () => setPresetWallpaper(wp.id);
          card.innerHTML = '<div><div style="font-weight:700; font-size:14px;">' + wp.name + (isActive ? ' <span style="color:#10B981;">(Activo)</span>' : '') + '</div><div style="font-size:11px; color:#8E95A5;">' + (wp.description || '') + '</div></div><button class="search-btn" style="padding:6px 14px; font-size:11px;">Aplicar</button>';
          wpContainer.appendChild(card);
        });
      }

      allInstalledApps = appState.installedApps || [];
      renderInstalledApps(allInstalledApps);
    }

    function renderInstalledApps(apps) {
      const container = document.getElementById('installedAppsContainer');
      if (!container) return;
      container.innerHTML = '';
      apps.forEach(app => {
        const item = document.createElement('div');
        item.className = 'glass-tile';
        item.style.padding = '12px';
        item.style.margin = '0';
        item.style.cursor = 'pointer';
        item.style.textAlign = 'center';
        item.style.fontSize = '12px';
        item.style.fontWeight = '700';
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
      vibrate(20);
      try {
        await fetch('/api/key', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ key: k })
        });
      } catch (_) {}
    }

    async function sendVol(action) {
      vibrate(18);
      try {
        await fetch('/api/volume', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ action: action })
        });
        loadStatus();
      } catch (_) {}
    }

    async function setVolumeValue(val) {
      try {
        await fetch('/api/volume', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ action: 'set', value: parseInt(val) })
        });
        const max = appState?.volume?.max || 100;
        document.getElementById('volPercentageText').innerText = Math.round((val / max) * 100) + '%';
      } catch (_) {}
    }

    async function launchApp(pkg, title, profileIdx) {
      vibrate(24);
      try {
        await fetch('/api/launch', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ packageName: pkg, title: title, profileIndex: profileIdx || 1 })
        });
      } catch (_) {}
    }

    async function sendDeepLink() {
      const q = document.getElementById('queryInput').value.trim();
      if (!q) return;
      vibrate(18);
      try {
        await fetch('/api/deeplink', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ query: q, url: q.startsWith('http') ? q : '' })
        });
        document.getElementById('queryInput').value = '';
      } catch (_) {}
    }

    async function setProfile(id) {
      vibrate(18);
      try {
        await fetch('/api/profile', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ profileId: id })
        });
        loadStatus();
      } catch (_) {}
    }

    async function setPresetWallpaper(wpId) {
      vibrate(18);
      try {
        await fetch('/api/wallpaper', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ action: 'preset', wallpaperId: wpId })
        });
        document.getElementById('uploadStatusText').innerText = '¡Fondo cambiado exitosamente en la TV!';
        loadStatus();
      } catch (_) {}
    }

    function uploadPhonePhoto(input) {
      if (!input.files || !input.files[0]) return;
      const file = input.files[0];
      document.getElementById('uploadStatusText').innerText = 'Optimizando foto para pantalla 4K/FHD...';

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
          try {
            await fetch('/api/wallpaper', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ action: 'upload', base64: resizedBase64 })
            });
            document.getElementById('uploadStatusText').innerText = '¡Fondo de pantalla actualizado en la TV TCL!';
            loadStatus();
          } catch (_) {
            document.getElementById('uploadStatusText').innerText = 'Error al enviar wallpaper';
          }
        };
        img.src = e.target.result;
      };
      reader.readAsDataURL(file);
    }

    window.addEventListener('DOMContentLoaded', () => {
      initTouchpadGestures();
      loadStatus();
      setInterval(loadStatus, 3000);
    });
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
