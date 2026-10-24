package com.tcl.smartlauncher.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.tcl.smartlauncher.ui.MainActivity

/**
 * Servicio de accesibilidad para:
 * 1. Interceptar KEYCODE_HOME para mantener el launcher activo o ceder el control al launcher de fábrica TCL.
 * 2. Automatizar la selección del perfil en Netflix, Prime Video, Disney+, Max, ViX y Apple TV ("¿Quién está viendo?").
 * 3. Ejecutar acciones globales (Back, Home, D-Pad) solicitadas desde el Control Remoto Web móvil PWA.
 */
class LauncherAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "LauncherAccessService"

        // Instancia activa para control remoto web y atajos globales
        var activeInstance: LauncherAccessibilityService? = null

        // Bandera global para controlar si el botón HOME se intercepta
        var isHomeInterceptionActive: Boolean = true

        // Datos del último lanzamiento para auto-selección de perfil
        private var targetPackage: String? = null
        private var targetProfileIndex: Int = 1
        private var targetProfileName: String = ""
        private var autoSkipEnabled: Boolean = false
        private var launchTimestamp: Long = 0L
        private var profileHandled: Boolean = false

        // Instancia activa para verificar estado
        val isServiceRunning: Boolean
            get() = activeInstance != null

        /**
         * Dispara la acción global BACK desde el control remoto del celular.
         */
        fun triggerBack(): Boolean {
            return activeInstance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
        }

        /**
         * Dispara la acción global HOME desde el control remoto del celular.
         */
        fun triggerHome(): Boolean {
            val inst = activeInstance
            return if (inst != null) {
                inst.performGlobalAction(GLOBAL_ACTION_HOME)
            } else {
                false
            }
        }

        /**
         * Dispara la acción global NOTIFICACIONES o AJUSTES RÁPIDOS.
         */
        fun triggerNotifications(): Boolean {
            return activeInstance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) ?: false
        }

        /**
         * Envía un evento de tecla (D-pad Up, Down, Left, Right, Center, etc.)
         */
        fun sendKeyEvent(keyCode: Int): Boolean {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("input", "keyevent", keyCode.toString()))
                process.waitFor()
                true
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo emitir input keyevent $keyCode", e)
                false
            }
        }

        /**
         * Notifica al servicio que una app de streaming fue lanzada y debe automatizarse su perfil.
         */
        fun prepareProfileAutoSelection(
            targetPackage: String,
            targetProfileIndex: Int,
            targetProfileName: String,
            autoSkipWhoIsWatching: Boolean
        ) {
            Companion.targetPackage = targetPackage
            Companion.targetProfileIndex = targetProfileIndex
            Companion.targetProfileName = targetProfileName
            Companion.autoSkipEnabled = autoSkipWhoIsWatching
            Companion.launchTimestamp = System.currentTimeMillis()
            Companion.profileHandled = false

            Log.d(TAG, "Preparando auto-perfil para $targetPackage, perfil: $targetProfileName (índice $targetProfileIndex)")
        }

        /**
         * Abre los Ajustes de Accesibilidad del sistema para que el usuario active o desactive el servicio.
         */
        fun openAccessibilitySettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo abrir Ajustes de Accesibilidad", e)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        Log.i(TAG, "LauncherAccessibilityService conectado exitosamente.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance == this) {
            activeInstance = null
        }
        Log.i(TAG, "LauncherAccessibilityService destruido.")
    }

    override fun onInterrupt() {
        Log.w(TAG, "LauncherAccessibilityService interrumpido.")
    }

    /**
     * Intercepción del Botón Home (KEYCODE_HOME).
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_HOME) {
            if (isHomeInterceptionActive) {
                // Solo reaccionar en ACTION_UP para evitar disparos múltiples
                if (event.action == KeyEvent.ACTION_UP) {
                    Log.d(TAG, "KEYCODE_HOME interceptado. Abriendo TCL Smart Launcher.")
                    val intent = Intent(this, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                    }
                    startActivity(intent)
                }
                return true // Evento consumido: no llega al launcher de fábrica
            } else {
                Log.d(TAG, "Interceptación Home desactivada. Pasando evento al Launcher de Fábrica TCL.")
                return super.onKeyEvent(event)
            }
        }

        return super.onKeyEvent(event)
    }

    /**
     * Detección de pantallas y auto-selección de perfil en Streaming ("¿Quién está viendo?").
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !autoSkipEnabled || profileHandled) return

        val now = System.currentTimeMillis()
        // Solo actuar dentro de los 15 segundos posteriores al lanzamiento
        if (now - launchTimestamp > 15000) return

        val pkg = event.packageName?.toString() ?: return
        if (targetPackage != null && pkg != targetPackage) return

        // Procesar cambios de estado de ventana o contenido
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            handler.postDelayed({
                tryAutoSelectProfile()
            }, 300)
        }
    }

    private fun tryAutoSelectProfile() {
        if (profileHandled) return
        val rootNode = rootInActiveWindow ?: return

        try {
            // Estrategia 1: Buscar directamente por el nombre del perfil (ej. "Papá", "Mamá", "Niños")
            if (targetProfileName.isNotBlank()) {
                val nodesByName = rootNode.findAccessibilityNodeInfosByText(targetProfileName)
                for (node in nodesByName) {
                    if (clickNodeOrParent(node)) {
                        Log.i(TAG, "Perfil seleccionado automáticamente por nombre: $targetProfileName")
                        profileHandled = true
                        return
                    }
                }
            }

            // Estrategia 2: Detectar si estamos en la pantalla "¿Quién está viendo?" o títulos similares
            val profileScreenKeywords = listOf(
                "¿Quién está viendo?",
                "Who's Watching?",
                "Who is watching?",
                "Selecciona un perfil",
                "Elegir perfil",
                "¿Quién va a ver ahora?",
                "Perfiles"
            )

            var isProfilePickerScreen = false
            for (keyword in profileScreenKeywords) {
                val detected = rootNode.findAccessibilityNodeInfosByText(keyword)
                if (detected.isNotEmpty()) {
                    isProfilePickerScreen = true
                    break
                }
            }

            if (isProfilePickerScreen) {
                // Buscar botones o avatares seleccionables
                val clickableNodes = mutableListOf<AccessibilityNodeInfo>()
                findClickableProfileNodes(rootNode, clickableNodes)

                if (clickableNodes.isNotEmpty()) {
                    val targetIndex = (targetProfileIndex - 1).coerceIn(0, clickableNodes.size - 1)
                    val selectedNode = clickableNodes[targetIndex]
                    if (clickNodeOrParent(selectedNode)) {
                        Log.i(TAG, "Perfil seleccionado automáticamente por índice: $targetProfileIndex")
                        profileHandled = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en auto-selección de perfil", e)
        }
    }

    private fun findClickableProfileNodes(
        node: AccessibilityNodeInfo,
        outList: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.isClickable && node.isVisibleToUser) {
            // Filtrar botones que digan "Añadir perfil", "Administrar", "Salir", "Editar"
            val text = (node.text?.toString() ?: "") + (node.contentDescription?.toString() ?: "")
            val isManagementBtn = text.contains("Añadir", ignoreCase = true) ||
                                  text.contains("Agregar", ignoreCase = true) ||
                                  text.contains("Administrar", ignoreCase = true) ||
                                  text.contains("Editar", ignoreCase = true) ||
                                  text.contains("Manage", ignoreCase = true)

            if (!isManagementBtn) {
                outList.add(node)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findClickableProfileNodes(child, outList)
        }
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                val success = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) return true
            }
            current = current.parent
        }
        return false
    }
}
