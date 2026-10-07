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
import androidx.compose.foundation.layout.fillMaxSize
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
val PoolBlue = Color(0xFF1F3FE0)
val Paper = Color(0xFFF7F7F5)

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

// ---- Speckled paper background (as in Pool) ------------------------------------------
@Composable
fun Speckle() {
    val dots = remember { val r = Random(7); List(260) { Offset(r.nextFloat(), r.nextFloat()) to r.nextFloat() } }
    Box(Modifier.fillMaxSize().background(Paper)) {
        Canvas(Modifier.fillMaxSize()) {
            dots.forEach { (o, s) ->
                drawCircle(Color(0xFF8A8A85).copy(alpha = 0.10f), 1.2f + s * 1.4f, Offset(o.x * size.width, o.y * size.height))
            }
        }
    }
}
