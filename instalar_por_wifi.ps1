<#
.SYNOPSIS
    Script interactivo para conectar, compilar, instalar y configurar el Smart TV TCL por Wi-Fi (ADB).
#>

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Host.UI.RawUI.WindowTitle = "TCL Smart Launcher - Asistente de Instalación Wi-Fi"

function Write-Header {
    Clear-Host
    Write-Host "=================================================================" -ForegroundColor Cyan
    Write-Host "         TCL SMART LAUNCHER - INSTALADOR POR WI-FI (ADB)         " -ForegroundColor Yellow -BackgroundColor DarkBlue
    Write-Host "=================================================================" -ForegroundColor Cyan
    Write-Host ""
}

function Find-Adb {
    $adbCmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCmd) {
        return $adbCmd.Source
    }
    
    $candidates = @(
        "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe",
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:ProgramFiles\Android\platform-tools\adb.exe",
        "$env:ProgramFiles(x86)\Android\android-sdk\platform-tools\adb.exe",
        "C:\Android\platform-tools\adb.exe"
    )
    
    foreach ($cand in $candidates) {
        if (Test-Path $cand) {
            return $cand
        }
    }
    return $null
}

$AdbExe = Find-Adb

if (-not $AdbExe) {
    Write-Header
    Write-Host "[ERROR] No se encontró el ejecutable 'adb.exe'." -ForegroundColor Red
    Write-Host "Por favor instala Android Platform-Tools o ejecuta la configuración del SDK." -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Presione Enter para salir"
    exit 1
}

$CacheIpFile = Join-Path $PSScriptRoot ".last_tv_ip.txt"

function Get-SavedIp {
    if (Test-Path $CacheIpFile) {
        $saved = (Get-Content $CacheIpFile -Raw).Trim()
        if ($saved -match '^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$') {
            return $saved
        }
    }
    return $null
}

function Save-Ip ($ip) {
    Set-Content -Path $CacheIpFile -Value $ip -Force
}

function Scan-NetworkForTcl {
    Write-Host "`n[+] Buscando dispositivos en la red local con puerto ADB (5555) abierto..." -ForegroundColor Cyan
    $adapters = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { 
        $_.IPAddress -notmatch '^(127\.|169\.254\.)' -and $_.InterfaceAlias -notmatch 'vEthernet|Loopback|Virtual' 
    }
    
    if (-not $adapters) {
        Write-Host "[-] No se detectaron interfaces de red activas para escanear." -ForegroundColor Yellow
        return $null
    }
    
    $foundIps = [System.Collections.Generic.List[string]]::new()
    
    foreach ($adapter in $adapters) {
        $ipParts = $adapter.IPAddress.Split('.')
        if ($ipParts.Count -ne 4) { continue }
        $subnetPrefix = "$($ipParts[0]).$($ipParts[1]).$($ipParts[2])"
        Write-Host "   -> Escaneando subred: $subnetPrefix.* (puerto 5555)..." -ForegroundColor DarkGray
        
        # Test common device ranges in parallel
        1..254 | ForEach-Object -Parallel {
            $target = "$($using:subnetPrefix).$_"
            $tcp = New-Object System.Net.Sockets.TcpClient
            $connect = $tcp.BeginConnect($target, 5555, $null, $null)
            $success = $connect.AsyncWaitHandle.WaitOne(150, $false)
            if ($success) {
                try {
                    $tcp.EndConnect($connect)
                    $target
                } catch {}
            }
            $tcp.Close()
        } -ThrottleLimit 60 | ForEach-Object {
            if ($_ -and -not $foundIps.Contains($_)) {
                $foundIps.Add($_)
            }
        }
    }
    
    return $foundIps
}

function Connect-Tv {
    param ([string]$TargetIp)
    
    if (-not $TargetIp) {
        $savedIp = Get-SavedIp
        Write-Host "`n--- SELECCIÓN DE IP DE LA TV TCL ---" -ForegroundColor Yellow
        Write-Host "1. Escribir IP manualmente"
        Write-Host "2. Escanear red automáticamente buscando la TV (puerto 5555)"
        if ($savedIp) {
            Write-Host "3. Usar última IP guardada: [$savedIp]" -ForegroundColor Green
        }
        $ipChoice = Read-Host "`nSelecciona una opción (1-3)"
        
        switch ($ipChoice) {
            "2" {
                $discovered = Scan-NetworkForTcl
                if ($discovered -and $discovered.Count -gt 0) {
                    Write-Host "`n[✓] Dispositivos encontrados con ADB habilitado:" -ForegroundColor Green
                    for ($i = 0; $i -lt $discovered.Count; $i++) {
                        Write-Host "  [$($i+1)] $($discovered[$i])" -ForegroundColor Cyan
                    }
                    $sel = Read-Host "`nElige el número de tu TV TCL (1-$($discovered.Count))"
                    $idx = [int]$sel - 1
                    if ($idx -ge 0 -and $idx -lt $discovered.Count) {
                        $TargetIp = $discovered[$idx]
                    }
                } else {
                    Write-Host "[-] No se encontró ninguna TV con el puerto 5555 abierto automáticamente." -ForegroundColor Yellow
                    Write-Host "    Asegúrate de que la TV tenga activada la 'Depuración por USB/Red' en Opciones de Desarrollador." -ForegroundColor DarkGray
                }
            }
            "3" {
                if ($savedIp) { $TargetIp = $savedIp }
            }
        }
        
        if (-not $TargetIp) {
            $prompt = if ($savedIp) { "Ingresa la IP de tu TV TCL [$savedIp]: " } else { "Ingresa la IP de tu TV TCL: " }
            $inputIp = Read-Host $prompt
            if (-not $inputIp -and $savedIp) {
                $TargetIp = $savedIp
            } else {
                $TargetIp = $inputIp.Trim()
            }
        }
    }
    
    if (-not $TargetIp) {
        Write-Host "[!] No se especificó ninguna dirección IP." -ForegroundColor Red
        return $false
    }
    
    Save-Ip $TargetIp
    
    Write-Host "`n[+] Conectando a $TargetIp:5555..." -ForegroundColor Cyan
    $out = & $AdbExe connect "$TargetIp`:5555"
    Write-Host $out
    
    # Check authorization state
    $devices = & $AdbExe devices
    Write-Host $devices -ForegroundColor DarkGray
    
    if ($devices -match "$TargetIp.*unauthorized") {
        Write-Host "`n==================================================================" -ForegroundColor Yellow
        Write-Host " [ATENCIÓN] Tu TV TCL pide confirmación de depuración en pantalla " -ForegroundColor Magenta
        Write-Host "==================================================================" -ForegroundColor Yellow
        Write-Host "Mira la pantalla de tu TV TCL ahora mismo:" -ForegroundColor Cyan
        Write-Host "  1. Aparecerá un mensaje: '¿Permitir depuración USB?'"
        Write-Host "  2. Marca la casilla: 'Permitir siempre desde esta computadora'"
        Write-Host "  3. Presiona 'Aceptar' o 'Permitir' con tu control remoto."
        Write-Host "==================================================================" -ForegroundColor Yellow
        Read-Host "Cuando lo hayas aceptado en la TV, presiona ENTER para continuar"
        & $AdbExe connect "$TargetIp`:5555"
    }
    
    $checkAgain = & $AdbExe devices
    if ($checkAgain -match "$TargetIp.*device\b") {
        Write-Host "`n[✓] ¡Conexión establecida exitosamente con la TV TCL!" -ForegroundColor Green
        return $true
    } else {
        Write-Host "`n[!] No se pudo confirmar la conexión activa con la TV ($TargetIp)." -ForegroundColor Red
        Write-Host "    Verifica que la TV y tu PC estén en la misma red Wi-Fi y que las 'Opciones de desarrollador' tengan 'Depuración USB' activada." -ForegroundColor Yellow
        return $false
    }
}

function Build-Project {
    Write-Host "`n[+] Compilando APK de TCL Launcher..." -ForegroundColor Cyan
    $gradlew = Join-Path $PSScriptRoot "gradlew.bat"
    
    if (-not (Test-Path $gradlew)) {
        Write-Host "[-] No se encontró gradlew.bat en $PSScriptRoot" -ForegroundColor Red
        return $false
    }
    
    Push-Location $PSScriptRoot
    try {
        & $gradlew assembleDebug
        if ($LASTEXITCODE -eq 0) {
            Write-Host "`n[✓] ¡Compilación finalizada con éxito!" -ForegroundColor Green
            return $true
        } else {
            Write-Host "`n[!] Error durante la compilación Gradle." -ForegroundColor Red
            return $false
        }
    } finally {
        Pop-Location
    }
}

function Install-Apk {
    $apkPath = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apkPath)) {
        $releaseApk = Join-Path $PSScriptRoot "releases\app-debug.apk"
        if (Test-Path $releaseApk) {
            $apkPath = $releaseApk
        }
    }

    if (-not (Test-Path $apkPath)) {
        Write-Host "`n[!] No se encontró el APK compilado en releases\ ni en app\build\outputs\..." -ForegroundColor Yellow
        $comp = Read-Host "¿Deseas compilar el APK ahora mismo? (S/n)"
        if ($comp -match '^n' -or $comp -match '^N') {
            return $false
        }
        $built = Build-Project
        if (-not $built -or -not (Test-Path $apkPath)) {
            Write-Host "[-] No se puede continuar sin el APK compilado." -ForegroundColor Red
            return $false
        }
    }
    
    Write-Host "`n[+] Instalando APK en la TV TCL vía Wi-Fi..." -ForegroundColor Cyan
    Write-Host "    Archivo: $apkPath" -ForegroundColor DarkGray
    
    $installOutput = & $AdbExe install -r -d "$apkPath"
    Write-Host $installOutput
    
    if ($installOutput -match "Success") {
        Write-Host "`n[✓] ¡TCL Smart Launcher se instaló correctamente en la TV!" -ForegroundColor Green
        return $true
    } else {
        Write-Host "`n[!] Error instalando el APK. Revisa la salida de ADB arriba." -ForegroundColor Red
        return $false
    }
}

function Enable-Accessibility {
    Write-Host "`n[+] Configurando Servicio de Accesibilidad para captura instantánea del botón Home..." -ForegroundColor Cyan
    $service = "com.tcl.smartlauncher/.service.LauncherAccessibilityService"
    
    # Get current enabled services
    $current = (& $AdbExe shell settings get secure enabled_accessibility_services).Trim()
    
    if ($current -eq "null" -or -not $current) {
        $newServices = $service
    } elseif ($current -notmatch [regex]::Escape($service)) {
        $newServices = "$current`:$service"
    } else {
        $newServices = $current
    }
    
    & $AdbExe shell settings put secure enabled_accessibility_services $newServices
    & $AdbExe shell settings put secure accessibility_enabled 1
    
    Write-Host "[✓] Servicio de accesibilidad activado en el sistema de la TV:" -ForegroundColor Green
    Write-Host "    $service" -ForegroundColor DarkGray
    Write-Host "    Ahora, cuando presiones el botón HOME en el control remoto, se abrirá tu launcher sin publicidad." -ForegroundColor Yellow
}

function Restore-FactoryLauncher {
    Write-Host "`n==================================================================" -ForegroundColor Yellow
    Write-Host "          RESTAURAR LAUNCHER DE FÁBRICA DE LA TV TCL             " -ForegroundColor White -BackgroundColor DarkRed
    Write-Host "==================================================================" -ForegroundColor Yellow
    Write-Host "Esta opción quitará el servicio de accesibilidad y devolverá" -ForegroundColor DarkGray
    Write-Host "la TV TCL a su estado original de fábrica de inmediato." -ForegroundColor DarkGray
    Write-Host ""
    $confirm = Read-Host "¿Confirmas restaurar el launcher de fábrica? (S/n)"
    if ($confirm -match '^n' -or $confirm -match '^N') {
        Write-Host "Operación cancelada." -ForegroundColor Yellow
        return
    }
    
    $service = "com.tcl.smartlauncher/.service.LauncherAccessibilityService"
    $current = (& $AdbExe shell settings get secure enabled_accessibility_services).Trim()
    
    if ($current -and $current -ne "null") {
        $cleaned = ($current.Split(':') | Where-Object { $_ -ne $service -and $_.Trim() -ne "" }) -join ':'
        if (-not $cleaned) { $cleaned = "empty" }
        & $AdbExe shell settings put secure enabled_accessibility_services $cleaned
    }
    
    Write-Host "`n[+] Enviando comando HOME para abrir el launcher de fábrica..." -ForegroundColor Cyan
    & $AdbExe shell am start -c android.intent.category.HOME -a android.intent.action.MAIN
    
    $uninst = Read-Host "`n¿Deseas también desinstalar completamente TCL Smart Launcher de la TV? (s/N)"
    if ($uninst -match '^s' -or $uninst -match '^S') {
        & $AdbExe uninstall com.tcl.smartlauncher
        Write-Host "[✓] Aplicación desinstalada de la TV." -ForegroundColor Green
    }
    
    Write-Host "`n[✓] ¡Launcher de fábrica restaurado con éxito!" -ForegroundColor Green
}

function Launch-AppOnTv {
    Write-Host "`n[+] Abriendo TCL Smart Launcher en la TV..." -ForegroundColor Cyan
    & $AdbExe shell am start -n com.tcl.smartlauncher/.ui.MainActivity
    Write-Host "[✓] Señal de inicio enviada a la TV." -ForegroundColor Green
}

function Full-Auto-Setup {
    Write-Host "`n>>> INICIANDO CONFIGURACIÓN AUTOMÁTICA COMPLETA <<<" -ForegroundColor Yellow
    $connected = Connect-Tv
    if (-not $connected) {
        Write-Host "[!] No se pudo conectar a la TV. Proceso detenido." -ForegroundColor Red
        return
    }
    
    $installed = Install-Apk
    if (-not $installed) {
        Write-Host "[!] Falló la instalación del APK." -ForegroundColor Red
        return
    }
    
    Enable-Accessibility
    Launch-AppOnTv
    
    Write-Host "`n=================================================================" -ForegroundColor Green
    Write-Host "       ¡FELICIDADES! INSTALACIÓN Y CONFIGURACIÓN COMPLETAS       " -ForegroundColor Yellow -BackgroundColor DarkGreen
    Write-Host "=================================================================" -ForegroundColor Green
    Write-Host "Tu TV TCL ya tiene activo el nuevo launcher minimalista sin publicidad."
    Write-Host "Presiona el botón HOME en el control remoto para comprobarlo."
}

# --- Bucle de Menú Principal ---
while ($true) {
    Write-Header
    $saved = Get-SavedIp
    if ($saved) {
        Write-Host "TV TCL Configurada: [$saved]" -ForegroundColor Green
    } else {
        Write-Host "TV TCL: [No configurada]" -ForegroundColor DarkGray
    }
    Write-Host ""
    Write-Host "1. Configuración completa automática (Conectar + Instalar + Activar)" -ForegroundColor Cyan
    Write-Host "2. Conectar a Smart TV TCL por Wi-Fi (IP o Escaneo)"
    Write-Host "3. Compilar APK (assembleDebug)"
    Write-Host "4. Instalar APK existente en la TV"
    Write-Host "5. Activar Servicio de Accesibilidad (Auto-interceptar botón Home)"
    Write-Host "6. Abrir TCL Smart Launcher en la TV"
    Write-Host "7. Restaurar Launcher de Fábrica TCL (1 clic)" -ForegroundColor Yellow
    Write-Host "8. Ver dispositivos ADB conectados"
    Write-Host "9. Salir"
    Write-Host ""
    
    $opt = Read-Host "Elige una opción (1-9)"
    
    switch ($opt) {
        "1" { Full-Auto-Setup; Read-Host "`nPresione Enter para volver al menú" }
        "2" { Connect-Tv; Read-Host "`nPresione Enter para volver al menú" }
        "3" { Build-Project; Read-Host "`nPresione Enter para volver al menú" }
        "4" { Install-Apk; Read-Host "`nPresione Enter para volver al menú" }
        "5" { Enable-Accessibility; Read-Host "`nPresione Enter para volver al menú" }
        "6" { Launch-AppOnTv; Read-Host "`nPresione Enter para volver al menú" }
        "7" { Restore-FactoryLauncher; Read-Host "`nPresione Enter para volver al menú" }
        "8" { 
            Write-Host "`n[+] Dispositivos ADB:" -ForegroundColor Cyan
            & $AdbExe devices -l
            Read-Host "`nPresione Enter para volver al menú" 
        }
        "9" { 
            Write-Host "`n¡Hasta luego!" -ForegroundColor Cyan
            break 
        }
        default {
            Write-Host "Opción inválida." -ForegroundColor Red
            Start-Sleep -Seconds 1
        }
    }
}
