package com.gllry.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val aspects = listOf<Float?>(null, 1f, 4f / 5f, 16f / 9f)
private val aspectNames = listOf("Original", "1:1", "4:5", "16:9")

private data class Preset(val name: String, val b: Float, val c: Float, val s: Float, val w: Float)

private val presets = listOf(
    Preset("Original", 0f, 1f, 1f, 0f),
    Preset("Vivid", 0f, 1.12f, 1.5f, 0.1f),
    Preset("Warm", 0.05f, 1.05f, 1.1f, 0.6f),
    Preset("Cool", 0f, 1.05f, 1f, -0.6f),
    Preset("Mono", 0f, 1.15f, 0f, 0f),
    Preset("Fade", 0.12f, 0.85f, 0.8f, 0.1f)
)

// ---------------------------------------------------------------------------------------
// image helpers
// ---------------------------------------------------------------------------------------
private fun decode(ctx: Context, uri: Uri, maxSide: Int): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
        dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val s = maxOf(info.size.width, info.size.height)
        if (s > maxSide) {
            val f = maxSide.toFloat() / s
            dec.setTargetSize(
                (info.size.width * f).toInt().coerceAtLeast(1),
                (info.size.height * f).toInt().coerceAtLeast(1)
            )
        }
    }

private fun transform(b: Bitmap, rot: Int, flip: Boolean): Bitmap {
    if (rot == 0 && !flip) return b
    val m = Matrix()
    if (flip) m.postScale(-1f, 1f)
    m.postRotate(rot * 90f)
    return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
}

private fun cropAspect(b: Bitmap, aspect: Float?): Bitmap {
    if (aspect == null) return b
    val cur = b.width.toFloat() / b.height
    return if (cur > aspect) {
        val nw = (b.height * aspect).toInt().coerceAtLeast(1)
        Bitmap.createBitmap(b, (b.width - nw) / 2, 0, nw, b.height)
    } else {
        val nh = (b.width / aspect).toInt().coerceAtLeast(1)
        Bitmap.createBitmap(b, 0, (b.height - nh) / 2, b.width, nh)
    }
}

/** brightness (-1..1), contrast (0.5..1.5), saturation (0..2), warmth (-1..1) -> 4x5 colour matrix */
private fun buildMatrix(b: Float, c: Float, s: Float, w: Float): FloatArray {
    val cm = android.graphics.ColorMatrix()
    cm.setSaturation(s)
    val t = (1f - c) * 128f + b * 100f
    cm.postConcat(
        android.graphics.ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    val wv = w * 28f
    cm.postConcat(
        android.graphics.ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, wv,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, -wv,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    return cm.array
}

private fun applyEdits(src: Bitmap, rot: Int, flip: Boolean, aspect: Float?, matrix: FloatArray): Bitmap {
    val b = cropAspect(transform(src, rot, flip), aspect)
    val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    paint.colorFilter = ColorMatrixColorFilter(android.graphics.ColorMatrix(matrix))
    Canvas(out).drawBitmap(b, 0f, 0f, paint)
    return out
}

/** Saves as a NEW photo in Pictures/Gllry – the original is never touched. */
private fun saveCopy(ctx: Context, bmp: Bitmap): Uri? {
    val v = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Gllry_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Gllry")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return null
    ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    v.clear()
    v.put(MediaStore.Images.Media.IS_PENDING, 0)
    ctx.contentResolver.update(uri, v, null, null)
    return uri
}

// ---------------------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------------------
@Composable
fun Editor(photo: Photo, onClose: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photo.id) {
        preview = withContext(Dispatchers.IO) { runCatching { decode(ctx, photo.uri, 1600) }.getOrNull() }
    }

    var rot by remember { mutableIntStateOf(0) }
    var flip by remember { mutableStateOf(false) }
    var aspect by remember { mutableIntStateOf(0) }
    var bright by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var sat by remember { mutableFloatStateOf(1f) }
    var warm by remember { mutableFloatStateOf(0f) }
    var tab by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }

    val geo = remember(preview, rot, flip) { preview?.let { transform(it, rot, flip) } }
    val mArr = remember(bright, contrast, sat, warm) { buildMatrix(bright, contrast, sat, warm) }
    val cf = remember(mArr) { ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(mArr)) }

    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val full = decode(ctx, photo.uri, 4096)
                    val out = applyEdits(full, rot, flip, aspects[aspect], mArr)
                    saveCopy(ctx, out) != null
                }.getOrDefault(false)
            }
            saving = false
            if (ok) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                Toast.makeText(ctx, "Saved a copy to Pictures/Gllry", Toast.LENGTH_SHORT).show()
                onSaved()
            } else {
                Toast.makeText(ctx, "Couldn't save the photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0E0E10)).padding(top = 40.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Box(
                Modifier.bounceClick(onClose).size(42.dp).clip(CircleShape).background(Color.White.copy(0.18f)),
                Alignment.Center
            ) { Text("✕", color = Color.White, fontSize = 15.sp) }
            Text("Edit", fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 28.sp, color = Color.White)
            Box(
                Modifier.bounceClick { save() }.clip(RoundedCornerShape(50)).background(Color.White)
                    .padding(horizontal = 22.dp, vertical = 11.dp)
            ) {
                Text(if (saving) "Saving…" else "Save", fontFamily = UiSans, fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111111), fontSize = 15.sp)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), Alignment.Center) {
            val g = geo
            if (g == null) {
                Text("Loading…", color = Color.White, fontFamily = UiSans)
            } else {
                val ar = aspects[aspect] ?: (g.width.toFloat() / g.height)
                Box(
                    Modifier.animateContentSize(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
                        .aspectRatio(ar).clip(RoundedCornerShape(22.dp))
                ) {
                    Image(g.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = cf)
                }
            }
        }

        Column(
            Modifier.fillMaxWidth()
                .animateContentSize(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Paper).padding(16.dp).padding(bottom = 18.dp)
        ) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                listOf("Crop", "Adjust", "Filters").forEachIndexed { i, n -> Chip(n, tab == i) { tab = i } }
            }
            Spacer(Modifier.height(16.dp))
            when (tab) {
                0 -> {
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                        Tool("↺  Left") { rot = (rot + 3) % 4 }
                        Tool("Right  ↻") { rot = (rot + 1) % 4 }
                        Tool("Flip  ⇋") { flip = !flip }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
                        aspectNames.forEachIndexed { i, n -> Chip(n, aspect == i) { aspect = i } }
                    }
                }
                1 -> {
                    SliderRow("Brightness", bright, -1f..1f) { bright = it }
                    SliderRow("Contrast", contrast, 0.5f..1.5f) { contrast = it }
                    SliderRow("Saturation", sat, 0f..2f) { sat = it }
                    SliderRow("Warmth", warm, -1f..1f) { warm = it }
                    Box(Modifier.fillMaxWidth(), Alignment.Center) {
                        Tool("Reset") { bright = 0f; contrast = 1f; sat = 1f; warm = 0f }
                    }
                }
                else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(presets) { p ->
                        Column(
                            Modifier.bounceClick { bright = p.b; contrast = p.c; sat = p.s; warm = p.w },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val g = geo
                            if (g != null) {
                                Image(
                                    g.asImageBitmap(), null,
                                    Modifier.size(68.dp).clip(RoundedCornerShape(20.dp)),
                                    contentScale = ContentScale.Crop,
                                    colorFilter = ColorFilter.colorMatrix(
                                        androidx.compose.ui.graphics.ColorMatrix(buildMatrix(p.b, p.c, p.s, p.w))
                                    )
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(p.name, fontFamily = UiSans, fontSize = 12.sp, color = Color(0xFF333333))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.bounceClick(onClick).clip(RoundedCornerShape(50))
            .background(if (selected) PoolBlue else Color(0xFFEAE8E6))
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(label, fontFamily = UiSans, fontWeight = FontWeight.Medium, fontSize = 14.sp,
            color = if (selected) Color.White else Color(0xFF333333))
    }
}

@Composable
private fun Tool(label: String, onClick: () -> Unit) {
    Box(
        Modifier.bounceClick(onClick).clip(RoundedCornerShape(18.dp)).background(Color(0xFFEAE8E6))
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) { Text(label, fontFamily = UiSans, fontSize = 14.sp, color = Color(0xFF111111)) }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(92.dp), fontFamily = UiSans, fontSize = 14.sp, color = Color(0xFF444444))
        Slider(
            value, onChange, Modifier.weight(1f), valueRange = range,
            onValueChangeFinished = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
            colors = SliderDefaults.colors(thumbColor = PoolBlue, activeTrackColor = PoolBlue)
        )
    }
}
