package com.gllry.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// ---- Typography -------------------------------------------------------------------
// Pool uses an editorial serif for display + a clean grotesk for UI.
// To match exactly, drop instrument_serif.ttf / inter.ttf into res/font and
// replace these two lines with FontFamily(Font(R.font.instrument_serif)) etc.
val Display: FontFamily = FontFamily.Serif
val UiSans: FontFamily = FontFamily.SansSerif
val PoolBlue = Color(0xFF7D93FF)
val Ink = Color(0xFFF2F2F7)
val Paper = Color(0xFF0B0B12)

// ---- Bounce + haptic press -----------------------------------------------------------
fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.90f else 1f,
        spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium), label = "bounce"
    )
    LaunchedEffect(pressed) { if (pressed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(src, null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }
}

// ---- Springy entrance --------------------------------------------------------------
fun Modifier.popIn(delayMs: Long = 0): Modifier = composed {
    val s = remember { Animatable(0.6f) }
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        launch { a.animateTo(1f) }
        s.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
    }
    graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = a.value }
}

// ---- Glassmorphism ------------------------------------------------------------------
// frosted translucent fill + soft light border + faint blue shadow
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = Color.White,
    alpha: Float = 0.10f,
    elevation: Dp = 10.dp
): Modifier = this
    .shadow(elevation, shape, ambientColor = Color(0x1A1F3FE0), spotColor = Color(0x261F3FE0))
    .clip(shape)
    .background(Brush.linearGradient(listOf(tint.copy(alpha = (alpha + 0.2f).coerceAtMost(1f)), tint.copy(alpha = alpha))))
    .border(
        1.dp,
        Brush.linearGradient(listOf(Color.White.copy(0.95f), Color.White.copy(0.18f), Color.White.copy(0.65f))),
        shape
    )

// ---- Soft aurora background (blurred colour orbs behind the glass) -------------------
@Composable
fun Speckle() {
    Box(Modifier.fillMaxSize().background(Paper)) {
        Orb(Color(0xFF3D5AFE).copy(alpha = 0.45f), 360, Modifier.align(Alignment.TopStart).offset((-110).dp, (-90).dp))
        Orb(Color(0xFF7C4DFF).copy(alpha = 0.40f), 300, Modifier.align(Alignment.CenterEnd).offset(110.dp, (-40).dp))
        Orb(Color(0xFF00B8D4).copy(alpha = 0.22f), 340, Modifier.align(Alignment.BottomStart).offset((-90).dp, 110.dp))
    }
}

@Composable
private fun Orb(c: Color, size: Int, m: Modifier) {
    Box(m.size(size.dp).blur(80.dp, BlurredEdgeTreatment.Unbounded).background(c, CircleShape))
}
