package com.tcl.smartlauncher.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tcl.smartlauncher.data.LauncherRepository
import com.tcl.smartlauncher.model.InstalledAppInfo
import com.tcl.smartlauncher.model.QuickShortcut
import com.tcl.smartlauncher.model.UserProfile
import com.tcl.smartlauncher.model.WallpaperInfo
import com.tcl.smartlauncher.service.LauncherAccessibilityService

/**
 * Diálogo para seleccionar el Fondo de Pantalla (Wallpaper) o ver QR para subir fotos desde el celular.
 */
@Composable
fun WallpaperPickerDialog(
    repository: LauncherRepository,
    onDismiss: () -> Unit,
    onWallpaperSelected: (WallpaperInfo) -> Unit
) {
    val wallpapers = remember { repository.getAvailableWallpapers() }
    var activeWp by remember { mutableStateOf(repository.getActiveWallpaper()) }
    val tvIp = remember { repository.getDeviceIpAddress() }
    val pwaUrl = "http://$tvIp:8888"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(680.dp)
                .clip(RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131D30)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(modifier = Modifier.padding(26.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AccentElectricBlue.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = AccentElectricBlue)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Fondos Fotográficos de Pantalla",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Elige una foto panorámica o sube tus fotos desde el celular",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Grid de Wallpapers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    wallpapers.take(3).forEach { wp ->
                        val isSelected = wp.id == activeWp.id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1E293B))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    activeWp = wp
                                    repository.setActiveWallpaper(wp.id)
                                    onWallpaperSelected(wp)
                                }
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = wp.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isSelected) Color(0xFF60A5FA) else Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = wp.description,
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    wallpapers.drop(3).take(3).forEach { wp ->
                        val isSelected = wp.id == activeWp.id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1E293B))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    activeWp = wp
                                    repository.setActiveWallpaper(wp.id)
                                    onWallpaperSelected(wp)
                                }
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = wp.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isSelected) Color(0xFF60A5FA) else Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = wp.description,
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Banner QR para subir fotos desde el celular
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF2563EB).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.PhoneAndroid, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Sube tus fotos desde el celular en 1 clic",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Entra en tu teléfono a: $pwaUrl (o tcl-tv.local:8888)",
                                fontSize = 11.sp,
                                color = Color(0xFF60A5FA),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Toca 'Fondo TV' > 'Elegir Foto' y aparecerá al instante en tu TV TCL.",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        QrCodeView(data = pwaUrl, size = 76.dp)
                    }
                }
            }
        }
    }
}

/**
 * Menú rápido al presionar el botón '+' de la barra de categorías inferior.
 */
@Composable
fun QuickAddActionDialog(
    repository: LauncherRepository,
    onDismiss: () -> Unit,
    onOpenWallpapers: () -> Unit,
    onOpenAddShortcut: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val tvIp = remember { repository.getDeviceIpAddress() }
    val pwaUrl = "http://$tvIp:8888"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131D30)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Acciones y Personalización",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Banner de Control Celular PWA con QR
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📱 Control Celular Web (PWA)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = pwaUrl,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF60A5FA)
                            )
                            Text(
                                text = "Escanea con tu cámara o entra en tu navegador",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        QrCodeView(data = pwaUrl, size = 68.dp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Opciones
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenWallpapers()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = Color(0xFF60A5FA))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Cambiar Fondo de Pantalla (6 Wallpapers HD)", color = Color.White)
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            onOpenAddShortcut()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color(0xFF10B981))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Añadir Nuevo Acceso Rápido / Serie", color = Color.White)
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            onOpenProfiles()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color(0xFFF59E0B))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Cambiar Perfil (Papá, Mamá, Niños)", color = Color.White)
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            onOpenSettings()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = Color(0xFFA855F7))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Centro de Control y Ajustes de TV", color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Diálogo para configurar el Botón Home y alternar con el Launcher original de fábrica TCL.
 */
@Composable
fun HomeStatusDialog(
    context: Context,
    repository: LauncherRepository,
    onDismiss: () -> Unit,
    onStatusChanged: () -> Unit
) {
    var isInterceptionActive by remember {
        mutableStateOf(repository.isHomeInterceptionEnabled())
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(540.dp)
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131D30)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isInterceptionActive) AccentElectricBlue.copy(alpha = 0.15f) else StatusOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = if (isInterceptionActive) AccentElectricBlue else StatusOrange
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Ajustes del Botón Home",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Controla si el botón de inicio de tu control remoto abre este Launcher Limpio o si restaura el Launcher original de fábrica de tu TCL Android TV.",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isInterceptionActive) "Launcher Limpio Activo" else "Launcher de Fábrica TCL Activo",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = if (isInterceptionActive) "El botón Home abre este launcher minimalista" else "El botón Home abre la interfaz estándar de TCL",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Switch(
                        checked = isInterceptionActive,
                        onCheckedChange = { checked ->
                            isInterceptionActive = checked
                            repository.setHomeInterceptionEnabled(checked)
                            onStatusChanged()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentElectricBlue
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            LauncherAccessibilityService.openAccessibilitySettings(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentElectricBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Abrir Ajustes de Accesibilidad TV")
                    }

                    OutlinedButton(
                        onClick = {
                            isInterceptionActive = false
                            repository.setHomeInterceptionEnabled(false)
                            onStatusChanged()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Ceder control al Launcher de Fábrica")
                    }
                }
            }
        }
    }
}

/**
 * Diálogo para gestionar los perfiles de usuario y el salto automático en apps de streaming.
 */
@Composable
fun ProfileDialog(
    repository: LauncherRepository,
    onDismiss: () -> Unit,
    onProfileSelected: (UserProfile) -> Unit
) {
    val profiles = remember { repository.getUserProfiles() }
    var activeProfile by remember { mutableStateOf(repository.getActiveProfile()) }
    var autoSkip by remember { mutableStateOf(activeProfile.autoSkipWhoIsWatching) }

    var netflixIdx by remember { mutableIntStateOf(activeProfile.defaultProfileIndexMap[LauncherRepository.PKG_NETFLIX] ?: 1) }
    var primeIdx by remember { mutableIntStateOf(activeProfile.defaultProfileIndexMap[LauncherRepository.PKG_PRIME_VIDEO] ?: 1) }
    var disneyIdx by remember { mutableIntStateOf(activeProfile.defaultProfileIndexMap[LauncherRepository.PKG_DISNEY_PLUS] ?: 1) }
    var maxIdx by remember { mutableIntStateOf(activeProfile.defaultProfileIndexMap[LauncherRepository.PKG_MAX] ?: 1) }
    var vixIdx by remember { mutableIntStateOf(activeProfile.defaultProfileIndexMap[LauncherRepository.PKG_VIX] ?: 1) }
    var appleIdx by remember { mutableIntStateOf(activeProfile.defaultProfileIndexMap[LauncherRepository.PKG_APPLE_TV] ?: 1) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(620.dp)
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131D30)),
            elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
        ) {
            Column(modifier = Modifier.padding(26.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Selector y Ajustes de Perfil",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Selecciona quién está viendo la TV para auto-omitir la pantalla '¿Quién está viendo?':",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    profiles.forEach { profile ->
                        val isSelected = profile.id == activeProfile.id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) AccentElectricBlue.copy(alpha = 0.25f) else Color(0xFF1E293B))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) AccentElectricBlue else Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    activeProfile = profile
                                    autoSkip = profile.autoSkipWhoIsWatching
                                    netflixIdx = profile.defaultProfileIndexMap[LauncherRepository.PKG_NETFLIX] ?: 1
                                    primeIdx = profile.defaultProfileIndexMap[LauncherRepository.PKG_PRIME_VIDEO] ?: 1
                                    disneyIdx = profile.defaultProfileIndexMap[LauncherRepository.PKG_DISNEY_PLUS] ?: 1
                                    maxIdx = profile.defaultProfileIndexMap[LauncherRepository.PKG_MAX] ?: 1
                                    vixIdx = profile.defaultProfileIndexMap[LauncherRepository.PKG_VIX] ?: 1
                                    appleIdx = profile.defaultProfileIndexMap[LauncherRepository.PKG_APPLE_TV] ?: 1
                                    repository.setActiveProfile(profile.id)
                                    onProfileSelected(profile)
                                }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = profile.avatarEmoji, fontSize = 28.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = profile.name,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = if (isSelected) Color(0xFF60A5FA) else Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-entrar directo a mi perfil",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Omite la pantalla de selección de perfil en streaming",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Switch(
                        checked = autoSkip,
                        onCheckedChange = { autoSkip = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentElectricBlue
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val updatedMap = mapOf(
                            LauncherRepository.PKG_NETFLIX to netflixIdx,
                            LauncherRepository.PKG_PRIME_VIDEO to primeIdx,
                            LauncherRepository.PKG_DISNEY_PLUS to disneyIdx,
                            LauncherRepository.PKG_MAX to maxIdx,
                            LauncherRepository.PKG_VIX to vixIdx,
                            LauncherRepository.PKG_APPLE_TV to appleIdx
                        )
                        val updatedProfile = activeProfile.copy(
                            defaultProfileIndexMap = updatedMap,
                            autoSkipWhoIsWatching = autoSkip
                        )
                        repository.updateProfile(updatedProfile)
                        repository.setActiveProfile(updatedProfile.id)
                        onProfileSelected(updatedProfile)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentElectricBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Guardar Preferencias de Perfil")
                }
            }
        }
    }
}

/**
 * Diálogo para crear un nuevo Acceso Rápido con soporte de Deep Link y selección de Perfil.
 */
@Composable
fun AddShortcutDialog(
    repository: LauncherRepository,
    onDismiss: () -> Unit,
    onShortcutAdded: (QuickShortcut) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var selectedPackage by remember { mutableStateOf(LauncherRepository.PKG_NETFLIX) }
    var appName by remember { mutableStateOf("Netflix") }
    var deepLinkUri by remember { mutableStateOf("") }
    var profileIndex by remember { mutableIntStateOf(1) }
    var accentColor by remember { mutableStateOf("#2563EB") }

    val appOptions = listOf(
        Pair("Netflix", LauncherRepository.PKG_NETFLIX) to "#E50914",
        Pair("YouTube", LauncherRepository.PKG_YOUTUBE) to "#FF0000",
        Pair("Prime Video", LauncherRepository.PKG_PRIME_VIDEO) to "#00A8E1",
        Pair("Disney+", LauncherRepository.PKG_DISNEY_PLUS) to "#113CCF",
        Pair("Max", LauncherRepository.PKG_MAX) to "#002BE7",
        Pair("ViX", LauncherRepository.PKG_VIX) to "#FF4500",
        Pair("Apple TV", LauncherRepository.PKG_APPLE_TV) to "#1A1A1A"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(540.dp)
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131D30)),
            elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
        ) {
            Column(modifier = Modifier.padding(26.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nuevo Acceso Rápido",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título (ej: Mi Serie, Playlist, Canal)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Subtítulo (ej: Temporada 2, En Vivo)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Seleccionar Plataforma:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    appOptions.take(4).forEach { (app, color) ->
                        val isSelected = selectedPackage == app.second
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AccentElectricBlue else Color(0xFF1E293B))
                                .clickable {
                                    appName = app.first
                                    selectedPackage = app.second
                                    accentColor = color
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = app.first,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    appOptions.drop(4).forEach { (app, color) ->
                        val isSelected = selectedPackage == app.second
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AccentElectricBlue else Color(0xFF1E293B))
                                .clickable {
                                    appName = app.first
                                    selectedPackage = app.second
                                    accentColor = color
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = app.first,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = deepLinkUri,
                    onValueChange = { deepLinkUri = it },
                    label = { Text("Deep Link URI / Enlace (Opcional)") },
                    placeholder = { Text("https:// o intent://") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val shortcut = QuickShortcut(
                            id = "custom_${System.currentTimeMillis()}",
                            title = title.ifBlank { appName },
                            subtitle = subtitle.ifBlank { "Acceso Rápido" },
                            appName = appName,
                            packageName = selectedPackage,
                            deepLinkUri = deepLinkUri.ifBlank { null },
                            accentColorHex = accentColor,
                            profileIndex = profileIndex,
                            isCustom = true
                        )
                        repository.addCustomShortcut(shortcut)
                        onShortcutAdded(shortcut)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentElectricBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Guardar Acceso Rápido")
                }
            }
        }
    }
}

/**
 * Diálogo de opciones para una app instalada (Ocultar app o crear acceso directo).
 */
@Composable
fun AppOptionsDialog(
    app: InstalledAppInfo,
    repository: LauncherRepository,
    onDismiss: () -> Unit,
    onAppVisibilityChanged: () -> Unit,
    onAddAsShortcut: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(460.dp)
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131D30)),
            elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = app.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                Text(
                    text = app.packageName,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        onAddAsShortcut()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentElectricBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Agregar a Accesos Rápidos")
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        repository.toggleHidePackage(app.packageName, true)
                        onAppVisibilityChanged()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Ocultar de mi lista de apps", color = Color.White)
                }
            }
        }
    }
}

/**
 * Centro de Control y Ajustes de TV (Wi-Fi, Cuenta Google, Imagen, Sonido, Wallpapers, Control Celular PWA).
 */
@Composable
fun TVControlCenterDialog(
    context: Context,
    repository: LauncherRepository,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit,
    onStatusChanged: () -> Unit,
    onOpenWallpapers: () -> Unit
) {
    var isHomeInterceptionActive by remember {
        mutableStateOf(repository.isHomeInterceptionEnabled())
    }

    val tvIp = remember { repository.getDeviceIpAddress() }
    val pwaUrl = "http://$tvIp:8888"

    val cardBg = Color(0xFF131D30)
    val tileNormalBg = Color(0xFF18233A)
    val tileFocusedBg = Color(0xFF223252)
    val tileFocusedBorder = Color(0xFF60A5FA)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(660.dp)
                .clip(RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(modifier = Modifier.padding(26.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(AccentElectricBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = AccentElectricBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Centro de Control & Ajustes TCL",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Text(
                                text = "TV, Entradas HDMI, Red, Celular PWA y Wallpapers",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Fila 0: Control Remoto Móvil PWA & Cambiar Wallpaper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Celular PWA
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(68.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "📱 Control Celular Web (PWA)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = pwaUrl,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color(0xFF60A5FA)
                                )
                                Text(
                                    text = "Volumen, trackpad y subir fotos",
                                    fontSize = 9.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            QrCodeView(data = pwaUrl, size = 52.dp)
                        }
                    }

                    // Cambiar Wallpaper
                    TVFocusCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(68.dp),
                        cornerRadius = 14.dp,
                        normalBg = tileNormalBg,
                        focusedBg = tileFocusedBg,
                        normalBorder = Color.White.copy(alpha = 0.12f),
                        focusedBorder = tileFocusedBorder,
                        onClick = {
                            onDismiss()
                            onOpenWallpapers()
                        }
                    ) { isFocused ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF06B6D4).copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = Color(0xFF06B6D4), modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(text = "Fondos de Pantalla", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text(text = "6 Wallpapers HD o Galería", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Fila 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ControlCenterTile(
                        icon = Icons.Default.Wifi,
                        iconTint = Color(0xFF06B6D4),
                        title = "Wi-Fi y Red",
                        subtitle = "Conexión a internet",
                        isDarkTheme = true,
                        tileNormalBg = tileNormalBg,
                        tileFocusedBg = tileFocusedBg,
                        tileFocusedBorder = tileFocusedBorder,
                        textPrimary = Color.White,
                        textSecondary = Color(0xFF94A3B8),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            repository.openSystemSettings(android.provider.Settings.ACTION_WIFI_SETTINGS)
                            onDismiss()
                        }
                    )

                    ControlCenterTile(
                        icon = Icons.Default.AccountCircle,
                        iconTint = Color(0xFF3B82F6),
                        title = "Cuenta Google TV",
                        subtitle = "Cuentas y perfiles",
                        isDarkTheme = true,
                        tileNormalBg = tileNormalBg,
                        tileFocusedBg = tileFocusedBg,
                        tileFocusedBorder = tileFocusedBorder,
                        textPrimary = Color.White,
                        textSecondary = Color(0xFF94A3B8),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            repository.openGoogleAccounts()
                            onDismiss()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Fila 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ControlCenterTile(
                        icon = Icons.Default.Tune,
                        iconTint = Color(0xFF8B5CF6),
                        title = "Imagen y Pantalla",
                        subtitle = "Entradas HDMI y modo cine",
                        isDarkTheme = true,
                        tileNormalBg = tileNormalBg,
                        tileFocusedBg = tileFocusedBg,
                        tileFocusedBorder = tileFocusedBorder,
                        textPrimary = Color.White,
                        textSecondary = Color(0xFF94A3B8),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            repository.openSystemSettings(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
                            onDismiss()
                        }
                    )

                    ControlCenterTile(
                        icon = Icons.Default.VolumeUp,
                        iconTint = Color(0xFF10B981),
                        title = "Sonido y Audio",
                        subtitle = "Dolby Atmos y volumen",
                        isDarkTheme = true,
                        tileNormalBg = tileNormalBg,
                        tileFocusedBg = tileFocusedBg,
                        tileFocusedBorder = tileFocusedBorder,
                        textPrimary = Color.White,
                        textSecondary = Color(0xFF94A3B8),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            repository.openSystemSettings(android.provider.Settings.ACTION_SOUND_SETTINGS)
                            onDismiss()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Fila 3: Botón Home y Todos los Ajustes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ControlCenterTile(
                        icon = Icons.Default.Home,
                        iconTint = if (isHomeInterceptionActive) StatusGreen else StatusOrange,
                        title = if (isHomeInterceptionActive) "Botón Home: Limpio" else "Botón Home: Fábrica",
                        subtitle = "Alternar launcher de fábrica",
                        isDarkTheme = true,
                        tileNormalBg = tileNormalBg,
                        tileFocusedBg = tileFocusedBg,
                        tileFocusedBorder = tileFocusedBorder,
                        textPrimary = Color.White,
                        textSecondary = Color(0xFF94A3B8),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val newStatus = !isHomeInterceptionActive
                            isHomeInterceptionActive = newStatus
                            repository.setHomeInterceptionEnabled(newStatus)
                            onStatusChanged()
                        }
                    )

                    ControlCenterTile(
                        icon = Icons.Default.Tv,
                        iconTint = Color(0xFF6366F1),
                        title = "Todos los Ajustes TCL",
                        subtitle = "Ajustes de fábrica TV",
                        isDarkTheme = true,
                        tileNormalBg = tileNormalBg,
                        tileFocusedBg = tileFocusedBg,
                        tileFocusedBorder = tileFocusedBorder,
                        textPrimary = Color.White,
                        textSecondary = Color(0xFF94A3B8),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            repository.openSystemSettings()
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlCenterTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    isDarkTheme: Boolean,
    tileNormalBg: Color,
    tileFocusedBg: Color,
    tileFocusedBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    TVFocusCard(
        modifier = modifier.height(58.dp),
        cornerRadius = 14.dp,
        normalBg = tileNormalBg,
        focusedBg = tileFocusedBg,
        normalBorder = Color.White.copy(alpha = 0.08f),
        focusedBorder = tileFocusedBorder,
        elevation = 1.dp,
        focusedElevation = 6.dp,
        onClick = onClick
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = textPrimary,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = textSecondary,
                    maxLines = 1
                )
            }
        }
    }
}
