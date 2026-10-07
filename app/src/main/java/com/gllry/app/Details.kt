package com.gllry.app

import android.content.Context
import android.provider.MediaStore
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import java.time.format.TextStyle as DayStyle

// ---- shared glass pill used under the viewer ---------------------------------------------
@Composable
fun GlassPill(label: String, onClick: () -> Unit) {
    Box(
        Modifier.bounceClick(onClick).glass(RoundedCornerShape(50), Color.White, 0.16f, 0.dp)
            .padding(horizontal = 28.dp, vertical = 13.dp)
    ) { Text(label, color = Color.White, fontFamily = UiSans, fontWeight = FontWeight.Medium, fontSize = 15.sp) }
}

// ---- metadata -----------------------------------------------------------------------------
data class PhotoInfo(
    val name: String, val sizeBytes: Long, val width: Int, val height: Int, val mime: String,
    val taken: Long, val folder: String, val make: String?, val model: String?,
    val iso: String?, val aperture: String?, val exposure: String?, val focal: String?,
    val lat: Double?, val lon: Double?
)

fun loadInfo(ctx: Context, p: Photo): PhotoInfo {
    var name = ""; var size = 0L; var w = 0; var h = 0; var mime = ""; var taken = 0L; var folder = ""
    runCatching {
        ctx.contentResolver.query(
            p.uri,
            arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.RELATIVE_PATH
            ), null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0) ?: ""; size = c.getLong(1); w = c.getInt(2); h = c.getInt(3)
                mime = c.getString(4) ?: ""; taken = c.getLong(5); folder = c.getString(6) ?: ""
            }
        }
    }
    // original (un-redacted) file gives GPS when location permission is granted
    val ex: ExifInterface? = runCatching {
        ctx.contentResolver.openInputStream(MediaStore.setRequireOriginal(p.uri))?.use { ExifInterface(it) }
    }.getOrNull() ?: runCatching {
        ctx.contentResolver.openInputStream(p.uri)?.use { ExifInterface(it) }
    }.getOrNull()

    val ll = ex?.latLong
    val f = ex?.getAttribute(ExifInterface.TAG_F_NUMBER)?.toDoubleOrNull()
    val t = ex?.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull()
    val fl = ex?.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0) ?: 0.0
    return PhotoInfo(
        name, size, w, h, mime, taken, folder,
        ex?.getAttribute(ExifInterface.TAG_MAKE), ex?.getAttribute(ExifInterface.TAG_MODEL),
        ex?.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { "ISO $it" },
        f?.let { "f/%.1f".format(it) },
        t?.let { if (it in 0.0001..0.999) "1/${(1.0 / it).roundToInt()}s" else "%.1fs".format(it) },
        if (fl > 0) "%.1f mm".format(fl) else null,
        ll?.get(0), ll?.get(1)
    )
}

private fun fmtSize(b: Long): String = when {
    b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
    b >= 1024 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

// ---- bottom sheet -------------------------------------------------------------------------
@Composable
fun DetailsSheet(photo: Photo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<PhotoInfo?>(null) }
    LaunchedEffect(photo.id) { info = withContext(Dispatchers.IO) { loadInfo(ctx, photo) } }

    val dens = LocalDensity.current
    val hidden = with(dens) { 900.dp.toPx() }
    val threshold = with(dens) { 140.dp.toPx() }
    val dragY = remember { Animatable(hidden) }
    LaunchedEffect(Unit) { dragY.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
    fun dismiss() = scope.launch {
        dragY.animateTo(hidden, spring(stiffness = Spring.StiffnessMedium)); onDismiss()
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = (1f - dragY.value / hidden).coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(remember { MutableInteractionSource() }, null) { dismiss() }
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .graphicsLayer { translationY = dragY.value.coerceAtLeast(0f) }
                .glass(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), Color.White, 0.86f, 16.dp)
        ) {
            val ms = (info?.taken?.takeIf { it > 0 }) ?: (photo.added * 1000)
            val dt = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
            // header = drag handle area
            Column(
                Modifier.fillMaxWidth().draggable(
                    rememberDraggableState { d -> scope.launch { dragY.snapTo((dragY.value + d).coerceAtLeast(0f)) } },
                    Orientation.Vertical,
                    onDragStopped = { v ->
                        if (dragY.value > threshold || v > 1800f) dismiss()
                        else scope.launch { dragY.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
                    }
                ).padding(top = 12.dp, start = 24.dp, end = 24.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(44.dp, 5.dp).clip(RoundedCornerShape(50)).background(Color(0xFFCFCFCB)))
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(dt.dayOfWeek.getDisplayName(DayStyle.SHORT, Locale.getDefault()),
                        fontFamily = UiSans, fontWeight = FontWeight.Light, fontSize = 30.sp, color = Color(0xFF2B3340))
                    Text(" ${dt.dayOfMonth} ${dt.month.getDisplayName(DayStyle.SHORT, Locale.getDefault())}.",
                        fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 30.sp, color = PoolBlue)
                }
            }
            Column(
                Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp).padding(bottom = 36.dp)
            ) {
                val i = info
                if (i == null) {
                    Text("Loading…", fontFamily = UiSans, color = Color.Gray)
                } else {
                    val full = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy · HH:mm", Locale.getDefault()).format(dt)
                    Section("Photo")
                    InfoRow("Taken", full)
                    InfoRow("Name", i.name)
                    if (i.width > 0) InfoRow("Resolution", "${i.width} × ${i.height}  ·  ${"%.1f".format(i.width.toLong() * i.height / 1e6)} MP")
                    InfoRow("Size", fmtSize(i.sizeBytes))
                    if (i.mime.isNotEmpty()) InfoRow("Type", i.mime.substringAfter('/').uppercase())
                    if (i.folder.isNotEmpty()) InfoRow("Folder", i.folder)

                    val cam = listOfNotNull(i.make, i.model).joinToString(" ").trim()
                    val shot = listOfNotNull(i.aperture, i.exposure, i.iso, i.focal).joinToString("  ·  ")
                    if (cam.isNotEmpty() || shot.isNotEmpty()) {
                        Section("Camera")
                        if (cam.isNotEmpty()) InfoRow("Device", cam)
                        if (shot.isNotEmpty()) InfoRow("Settings", shot)
                    }
                    if (i.lat != null && i.lon != null) {
                        Section("Location")
                        InfoRow("Coordinates", "%.5f, %.5f".format(i.lat, i.lon))
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, Modifier.padding(top = 18.dp, bottom = 6.dp), fontFamily = Display, fontStyle = FontStyle.Italic,
        fontSize = 20.sp, color = PoolBlue)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(label, Modifier.width(104.dp), fontFamily = UiSans, fontSize = 14.sp, color = Color(0xFF9A9A9A))
        Text(value, Modifier.weight(1f), fontFamily = UiSans, fontSize = 15.sp, color = Color(0xFF111111))
    }
}
