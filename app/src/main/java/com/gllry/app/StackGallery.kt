package com.gllry.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

// Geometry measured from the reference clip (720x960 frames)
private const val STRIP = 0.165f      // visible strip of each stacked card / screen height
private const val SQUASH = 0.64f      // vertical foreshortening of the tilted cards
private const val SIDE = 0.90f        // card width in the stack / screen width
private const val STACK_TOP = 0.36f   // where the focused card's top edge sits in the stack
private val CORNER = 18.dp

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** rounded corners that stay circular even after the card is squashed by scaleY */
private class SquashShape(val rx: Float, val ry: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(rx, ry)))
}

// ======================================================================================
// Hold the "Gllry" title for 3 seconds -> opens the Stack style
// ======================================================================================
@Composable
fun HoldTitle(onTrigger: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var holding by remember { mutableStateOf(false) }
    val prog = remember { Animatable(0f) }
    LaunchedEffect(holding) {
        if (holding) {
            launch { prog.animateTo(1f, tween(3000, easing = LinearEasing)) }
            delay(1000); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            delay(1000); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        } else {
            prog.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Gllry", fontFamily = Display, fontSize = 30.sp, color = Ink, letterSpacing = (-0.5).sp,
            modifier = Modifier
                .graphicsLayer { val sc = 1f + 0.14f * prog.value; scaleX = sc; scaleY = sc }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        holding = true
                        val t0 = System.currentTimeMillis()
                        val up = withTimeoutOrNull(3000L) { waitForUpOrCancellation() }
                        holding = false
                        if (up == null && System.currentTimeMillis() - t0 >= 2950L) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTrigger()
                        }
                    }
                }
        )
        Box(
            Modifier.padding(top = 4.dp).width(54.dp).height(2.dp)
                .graphicsLayer { scaleX = prog.value; alpha = prog.value }
                .background(PoolBlue, RoundedCornerShape(50))
        )
    }
}

// ======================================================================================
// Stack gallery
//  * fullscreen cards, vertical swipe
//  * while scrolling the cards fold back into a tilted, overlapping stack
//  * when scrolling stops the nearest card unfolds to fullscreen
//  * tap = overview (stack stays, tap a card to open it) / drag past the end = pile
// ======================================================================================
@Composable
fun StackGallery(vm: GalleryVM, isDefault: Boolean, onToggleDefault: () -> Unit, onExit: () -> Unit) {
    val photos = vm.photosFor(ALL)
    val n = photos.size
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val ctx = LocalContext.current

    val pos = remember { Animatable(0f) }          // scroll position in cards
    val stackS = remember { Animatable(0f) }       // 0 = fullscreen card, 1 = tilted stack
    val pileS = remember { Animatable(0f) }        // 0 = stack, 1 = pile
    var moving by remember { mutableStateOf(false) }
    var overview by remember { mutableStateOf(false) }
    var intro by remember { mutableStateOf(false) }
    var pileOn by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf(true) }
    val last = (n - 1).coerceAtLeast(0).toFloat()

    fun leavePile() {
        pileOn = false
        scope.launch { pileS.animateTo(0f, spring(0.82f, Spring.StiffnessLow)) }
        scope.launch { pos.animateTo(last, spring(0.8f, Spring.StiffnessLow)) }
    }
    fun enterPile() {
        pileOn = true; overview = false; moving = false
        scope.launch { pos.animateTo(last, spring(0.8f, Spring.StiffnessLow)) }
        scope.launch { pileS.animateTo(1f, spring(0.72f, Spring.StiffnessLow)) }
    }

    BackHandler(enabled = pileOn || overview || !isDefault) {
        when {
            pileOn -> leavePile()
            overview -> overview = false
            else -> onExit()
        }
    }

    // intro: starts fullscreen, folds into the stack, scrolls a little, settles
    LaunchedEffect(Unit) {
        delay(450)
        if (n > 0) {
            intro = true
            if (n > 2) pos.animateTo(2f, spring(0.9f, Spring.StiffnessVeryLow)) else delay(900)
            intro = false
        }
    }
    LaunchedEffect(Unit) { delay(3200); hint = false }
    LaunchedEffect(n) { if (pos.value > last) pos.snapTo(last) }
    LaunchedEffect(moving, overview, intro, pileOn) {
        val on = moving || overview || intro || pileOn
        stackS.animateTo(
            if (on) 1f else 0f,
            spring(if (on) 0.85f else 0.9f, if (on) Spring.StiffnessMedium else Spring.StiffnessLow)
        )
    }
    LaunchedEffect(Unit) {
        snapshotFlow { (pos.value + 0.5f).toInt() }.drop(1)
            .collect { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    val lo by remember(n) {
        derivedStateOf { if (pileOn) maxOf(0, n - 16) else maxOf(0, floor(pos.value - 3.3f).toInt()) }
    }
    val hi by remember(n) {
        derivedStateOf { if (pileOn) n - 1 else minOf(n - 1, ceil(pos.value + 6.2f).toInt()) }
    }
    val current by remember { derivedStateOf { pos.value.roundToInt() } }
    val calm by remember { derivedStateOf { stackS.value < 0.03f && !pileOn && !moving } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val W = constraints.maxWidth.toFloat()
        val H = constraints.maxHeight.toFloat()
        val strip = H * STRIP
        val y0 = H * STACK_TOP
        val cornerPx = with(LocalDensity.current) { CORNER.toPx() }
        val shadowPx = with(LocalDensity.current) { 20.dp.toPx() }

        // light studio background (turns white in the pile)
        Box(
            Modifier.fillMaxSize().drawBehind {
                val pl = pileS.value
                drawRect(
                    Brush.verticalGradient(
                        listOf(lerp(Color(0xFFF3F3F3), Color.White, pl), lerp(Color(0xFFDADADA), Color.White, pl))
                    )
                )
            }
        )

        if (n == 0) {
            Text("No photos yet", Modifier.align(Alignment.Center), color = Color(0xFF666666), fontFamily = UiSans)
        }

        // gestures
        Box(
            Modifier.fillMaxSize()
                .pointerInput(pileOn, overview, n) {
                    detectTapGestures { off ->
                        when {
                            pileOn -> leavePile()
                            overview -> {
                                val idx = floor((off.y - y0) / strip + pos.value).toInt().coerceIn(0, (n - 1).coerceAtLeast(0))
                                overview = false
                                scope.launch { pos.animateTo(idx.toFloat(), spring(0.8f, Spring.StiffnessLow)) }
                            }
                            else -> {
                                overview = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                }
                .draggable(
                    rememberDraggableState { d ->
                        scope.launch {
                            val cur = pos.value
                            val delta = -d / strip
                            val f = if ((cur < 0f && delta < 0f) || (cur > last && delta > 0f)) 0.35f else 1f
                            pos.snapTo((cur + delta * f).coerceIn(-0.6f, last + 1.0f))
                        }
                    },
                    Orientation.Vertical,
                    enabled = !pileOn && n > 0,
                    onDragStarted = { moving = true; hint = false; overview = false },
                    onDragStopped = { v ->
                        scope.launch {
                          try {
                            if (pos.value > last + 0.55f) { enterPile(); return@launch }
                            pos.updateBounds(-0.4f, last + 0.9f)
                            pos.animateDecay(
                                (-v / strip).coerceIn(-14f, 14f),
                                exponentialDecay(frictionMultiplier = 1.4f, absVelocityThreshold = 0.05f)
                            )
                            pos.updateBounds(null, null)
                            if (pos.value > last + 0.55f) { enterPile(); return@launch }
                            val target = pos.value.roundToInt().coerceIn(0, last.toInt())
                            pos.animateTo(target.toFloat(), spring(0.78f, Spring.StiffnessLow))
                            moving = false
                          } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                          } catch (e: Exception) {
                            pos.updateBounds(null, null); moving = false
                          }
                        }
                    }
                )
        ) {
            for (i in lo..hi) {
                val p = photos.getOrNull(i) ?: continue
                key(p.key) {
                    val rnd = remember(p.key) {
                        val r = Random(p.key.hashCode())
                        floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat())
                    }
                    Box(
                        Modifier.fillMaxSize().zIndex(i.toFloat())
                            .graphicsLayer {
                                val s = stackS.value
                                val pl = pileS.value
                                val d = i - pos.value
                                var ty = mix(d * H, y0 + d * strip, s)
                                var sx = mix(1f, SIDE, s)
                                var sy = mix(1f, SQUASH, s)
                                var tx = 0f
                                var al = 1f
                                if (pl > 0f) {
                                    val ps = 0.30f + 0.10f * rnd[2]
                                    ty = mix(ty, H * (0.16f + 0.46f * rnd[1]), pl)
                                    tx = (rnd[0] - 0.5f) * W * 0.62f * pl
                                    sx = mix(sx, ps * (0.75f + 0.5f * rnd[3]), pl)
                                    sy = mix(sy, ps, pl)
                                    if (d < -3.3f || d > 6.2f) al = pl
                                }
                                transformOrigin = TransformOrigin(0.5f, 0f)
                                translationX = tx
                                translationY = ty
                                scaleX = sx
                                scaleY = sy
                                alpha = al
                                val rr = cornerPx * maxOf(s, pl * 0.6f)
                                shape = SquashShape(rr / sx, rr / sy)
                                clip = true
                                shadowElevation = shadowPx * maxOf(s, pl)
                            }
                            .background(Color(0xFF111111))
                    ) {
                        if (p.isVideo && calm && i == current) {
                            VideoPlayer(p, true, Modifier.fillMaxSize(), showControls = false)
                        } else {
                            AsyncImage(
                                remember(p.key) { ImageRequest.Builder(ctx).data(p.uri).size(1080, 1920).crossfade(false).build() },
                                null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                            )
                            if (p.isVideo) PlayBadge(Modifier.align(Alignment.Center), 64)
                        }
                    }
                }
            }
        }

        // minimal chrome: exit + "make default" (shows briefly, and in overview)
        AnimatedVisibility(
            overview || hint, Modifier.align(Alignment.TopCenter).zIndex(1_000_000f),
            enter = fadeIn(), exit = fadeOut()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 40.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                Box(
                    Modifier.bounceClick(onExit).size(42.dp).glass(CircleShape, Color.Black, 0.45f, 0.dp),
                    Alignment.Center
                ) { Text("✕", color = Color.White, fontSize = 15.sp) }
                Box(
                    Modifier.bounceClick(onToggleDefault)
                        .glass(RoundedCornerShape(50), Color.Black, 0.45f, 0.dp)
                        .padding(horizontal = 18.dp, vertical = 11.dp)
                ) {
                    Text(
                        if (isDefault) "✓ Default style" else "Make default",
                        color = Color.White, fontSize = 14.sp, fontFamily = UiSans, fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
