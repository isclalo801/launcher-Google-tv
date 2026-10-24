package com.tcl.smartlauncher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.tcl.smartlauncher.data.LauncherRepository
import com.tcl.smartlauncher.model.WallpaperInfo
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Fondo fotográfico a pantalla completa con soporte de imagen local / web HD
 * y degradado procedural de reserva instantáneo sin pantallas negras.
 */
@Composable
fun ScenicWallpaperBackground(
    wallpaper: WallpaperInfo,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val fallbackBrush = remember(wallpaper) {
        val colors = wallpaper.fallbackGradientColors.map { Color(it) }
        Brush.verticalGradient(
            listOf(
                colors.firstOrNull() ?: Color(0xFF0288D1),
                colors.getOrNull(1) ?: Color(0xFF26C6DA),
                colors.lastOrNull() ?: Color(0xFF004D40)
            )
        )
    }

    val imageModel = remember(wallpaper) {
        val custom = wallpaper.customPathOrUrl
        if (!custom.isNullOrBlank()) {
            if (custom.startsWith("http://") || custom.startsWith("https://")) {
                custom
            } else {
                File(custom)
            }
        } else {
            wallpaper.imageUrl
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Gradiente procedural de respaldo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(fallbackBrush)
        )

        // 2. Imagen HD de Unsplash / almacenamiento
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageModel)
                .crossfade(true)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Degradado muy suave y sutil solo en la parte inferior para que las píldoras se lean nítidas
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.60f to Color.Transparent,
                        0.78f to Color.Black.copy(alpha = 0.22f),
                        1.0f to Color.Black.copy(alpha = 0.50f)
                    )
                )
        )
    }
}

/**
 * Tarjeta interactiva para Android TV con soporte de elevación y bordes dinámicos.
 */
@Composable
fun TVFocusCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    normalBg: Color = Color.Transparent,
    focusedBg: Color = Color.Transparent,
    normalBorder: Color = Color.White.copy(alpha = 0.15f),
    focusedBorder: Color = Color.White,
    borderWidth: Dp = 1.dp,
    focusedBorderWidth: Dp = 2.5.dp,
    elevation: Dp = 2.dp,
    focusedElevation: Dp = 12.dp,
    scaleTarget: Float = 1.06f,
    backgroundBrush: Brush? = null,
    focusedBackgroundBrush: Brush? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) scaleTarget else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardScale"
    )
    val currentBorderColor by animateColorAsState(
        targetValue = if (isFocused) focusedBorder else normalBorder,
        label = "borderColor"
    )
    val currentBgColor by animateColorAsState(
        targetValue = if (isFocused) focusedBg else normalBg,
        label = "bgColor"
    )
    val currentElevation = if (isFocused) focusedElevation else elevation
    val currentBorderWidth = if (isFocused) focusedBorderWidth else borderWidth
    val activeBrush = if (isFocused) (focusedBackgroundBrush ?: backgroundBrush) else backgroundBrush

    val shape = RoundedCornerShape(cornerRadius)

    val baseModifier = modifier
        .scale(scale)
        .shadow(currentElevation, shape, clip = false)
        .clip(shape)
        .border(BorderStroke(currentBorderWidth, currentBorderColor), shape)
        .onFocusChanged {
            isFocused = it.isFocused
            onFocusChanged?.invoke(it.isFocused)
        }
        .focusable()
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )

    val styledModifier = if (activeBrush != null) {
        baseModifier.background(activeBrush)
    } else {
        baseModifier.background(currentBgColor)
    }

    Surface(
        modifier = styledModifier,
        shape = shape,
        color = Color.Transparent
    ) {
        Box {
            content(isFocused)
        }
    }
}

/**
 * Cápsula Frosted Glass Ovalada Grande (178x88dp) - IDÉNTICA A LA FOTO DE REFERENCIA.
 */
@Composable
fun FrostedCapsuleCard(
    modifier: Modifier = Modifier,
    width: Dp = 178.dp,
    height: Dp = 88.dp,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "capsuleScale"
    )

    val currentBorderColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else Color.White.copy(alpha = 0.18f),
        label = "capsuleBorder"
    )

    val currentBgColor by animateColorAsState(
        targetValue = if (isFocused) Color(0xFF1E242B).copy(alpha = 0.88f) else Color(0xFF1E242B).copy(alpha = 0.68f),
        label = "capsuleBg"
    )

    val borderWidth = if (isFocused) 2.5.dp else 1.dp
    val elevation = if (isFocused) 12.dp else 2.dp
    val shape = RoundedCornerShape(44.dp) // Forma de píldora / estadio perfecta como en la foto

    Surface(
        modifier = modifier
            .size(width = width, height = height)
            .scale(scale)
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(currentBgColor)
            .border(BorderStroke(borderWidth, currentBorderColor), shape)
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChanged?.invoke(it.isFocused)
            }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = shape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content(isFocused)
        }
    }
}

/**
 * Renderiza el logotipo tipográfico oficial en blanco puro de cada plataforma
 * tal como aparece en la fotografía de referencia.
 */
@Composable
fun BrandCapsuleLogo(
    packageName: String,
    title: String,
    subtitle: String = "",
    iconDrawable: Drawable? = null,
    showRecentBadge: Boolean = false,
    timestamp: Long = 0L,
    profileIndex: Int = 1
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (packageName) {
            LauncherRepository.PKG_NETFLIX -> {
                Text(
                    text = "NETFLIX",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    letterSpacing = 2.sp,
                    color = Color.White
                )
            }
            LauncherRepository.PKG_YOUTUBE, "com.google.android.youtube.tvunplugged" -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 22.dp, height = 16.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(7.dp)) {
                            val path = Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width, size.height / 2f)
                                lineTo(0f, size.height)
                                close()
                            }
                            drawPath(path, color = Color(0xFF1E242B))
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "YouTube",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )
                }
            }
            LauncherRepository.PKG_PRIME_VIDEO -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "prime video",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // La firma de la flecha curva de sonrisa de Prime Video
                    Canvas(modifier = Modifier.size(width = 54.dp, height = 6.dp)) {
                        val smilePath = Path().apply {
                            moveTo(3f, 1f)
                            quadraticBezierTo(
                                size.width * 0.55f, size.height + 1f,
                                size.width - 3f, 2f
                            )
                        }
                        drawPath(
                            path = smilePath,
                            color = Color.White,
                            style = Stroke(width = 2.4f, cap = StrokeCap.Round)
                        )
                        val arrowPath = Path().apply {
                            moveTo(size.width - 7f, 0f)
                            lineTo(size.width - 2f, 2f)
                            lineTo(size.width - 4.5f, 5.5f)
                        }
                        drawPath(
                            path = arrowPath,
                            color = Color.White,
                            style = Stroke(width = 2.0f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }
            LauncherRepository.PKG_DISNEY_PLUS -> {
                Text(
                    text = "Disney+",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
            LauncherRepository.PKG_MAX -> {
                Text(
                    text = "max",
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    color = Color.White,
                    letterSpacing = (-1).sp
                )
            }
            LauncherRepository.PKG_VIX, LauncherRepository.PKG_VIX_ALT -> {
                Text(
                    text = "ViX",
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
            LauncherRepository.PKG_APPLE_TV -> {
                Text(
                    text = " tv+",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
            }
            else -> {
                // Apps genéricas instaladas
                if (iconDrawable != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        AppIconView(drawable = iconDrawable, size = 32.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Subtítulo discreto si está en la pestaña RECIENTES
        if (showRecentBadge && timestamp > 0L) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${TimeAgoHelper.format(timestamp)} • Perfil $profileIndex",
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1
            )
        }
    }
}

/**
 * Los 3 botones de acción de la esquina superior derecha (Perfil/Inputs, Ajustes, Search).
 */
@Composable
fun TopActionIconButton(
    icon: ImageVector,
    label: String,
    isAlwaysHighlighted: Boolean = false, // Para el botón Search destacado de la foto
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val isSolid = isFocused || isAlwaysHighlighted
    val iconColor = if (isSolid) Color(0xFF0F172A) else Color.White
    val bgColor = if (isSolid) Color.White else Color.Transparent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(bgColor)
                .border(
                    BorderStroke(
                        width = if (isFocused) 2.5.dp else (if (isAlwaysHighlighted) 0.dp else 1.dp),
                        color = if (isFocused) Color.White else (if (isAlwaysHighlighted) Color.Transparent else Color.White.copy(alpha = 0.25f))
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }

        if (label.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = if (isFocused) 1.0f else 0.85f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Pestaña de categoría de la barra inferior (MOVIES, APPS, MUSIC, SPORTS, NEWS, etc.)
 */
@Composable
fun CategoryTabPill(
    title: String,
    isSelected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val pillBg = if (isSelected) {
        Color.White.copy(alpha = 0.85f)
    } else if (isFocused) {
        Color.White.copy(alpha = 0.25f)
    } else {
        Color.Transparent
    }

    val textColor = if (isSelected) {
        Color(0xFF0F172A)
    } else {
        Color.White.copy(alpha = 0.85f)
    }

    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(pillBg)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = textColor
        )
    }
}

/**
 * Botón circular '+' de la barra inferior para Wallpapers y Acciones Rápidas.
 */
@Composable
fun CategoryAddButton(
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
        label = "addScale"
    )

    Box(
        modifier = Modifier
            .size(38.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.25f))
            .border(
                BorderStroke(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = Color.White
                ),
                CircleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Más opciones",
            tint = if (isFocused) Color(0xFF0F172A) else Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Generador y visor nativo de Códigos QR para el Control Remoto Web en Compose.
 */
@Composable
fun QrCodeView(
    data: String,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp
) {
    val bitmap = remember(data) {
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, 256, 256)
            val w = bitMatrix.width
            val h = bitMatrix.height
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
            for (x in 0 until w) {
                for (y in 0 until h) {
                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            painter = BitmapPainter(bitmap.asImageBitmap()),
            contentDescription = "Código QR: $data",
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
        )
    }
}

/**
 * Renderiza de forma segura cualquier Drawable de Android (AdaptiveIcon, Vector, Bitmap)
 */
@Composable
fun AppIconView(
    drawable: Drawable?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val bitmap = remember(drawable) {
        drawable?.let { d ->
            try {
                if (d is BitmapDrawable && d.bitmap != null) {
                    d.bitmap
                } else {
                    val width = if (d.intrinsicWidth > 0) d.intrinsicWidth else 96
                    val height = if (d.intrinsicHeight > 0) d.intrinsicHeight else 96
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    d.setBounds(0, 0, canvas.width, canvas.height)
                    d.draw(canvas)
                    bmp
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    if (bitmap != null) {
        Image(
            painter = BitmapPainter(bitmap.asImageBitmap()),
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.6f)
            )
        }
    }
}

/**
 * Convierte un timestamp en una cadena relativa para TV (ej: "Hace 15 min", "Hace 2 h", "Ayer").
 */
object TimeAgoHelper {
    fun format(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        if (diff < 0) return "Ahora mismo"

        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        if (minutes < 1) return "Hace un momento"
        if (minutes < 60) return "Hace $minutes min"

        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        if (hours < 24) return "Hace $hours h"

        val days = TimeUnit.MILLISECONDS.toDays(diff)
        if (days == 1L) return "Ayer"
        if (days < 7) return "Hace $days días"

        return "Hace más de 1 sem"
    }
}
