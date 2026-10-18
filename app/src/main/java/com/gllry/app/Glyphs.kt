package com.gllry.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlyphPlus(size: Dp = 22.dp, color: Color = Color.White) = Canvas(Modifier.size(size)) {
    val s = this.size.minDimension; val w = 2.2.dp.toPx(); val m = s * 0.2f
    drawLine(color, Offset(m, s / 2), Offset(s - m, s / 2), w, StrokeCap.Round)
    drawLine(color, Offset(s / 2, m), Offset(s / 2, s - m), w, StrokeCap.Round)
}

@Composable
fun GlyphClose(size: Dp = 18.dp, color: Color = Color.White) = Canvas(Modifier.size(size)) {
    val s = this.size.minDimension; val w = 2.4.dp.toPx(); val m = s * 0.18f
    drawLine(color, Offset(m, m), Offset(s - m, s - m), w, StrokeCap.Round)
    drawLine(color, Offset(s - m, m), Offset(m, s - m), w, StrokeCap.Round)
}

@Composable
fun GlyphSearch(size: Dp = 24.dp, color: Color = Color.White) = Canvas(Modifier.size(size)) {
    val s = this.size.minDimension; val w = 2.4.dp.toPx()
    drawCircle(color, radius = s * 0.27f, center = Offset(s * 0.43f, s * 0.43f), style = Stroke(w))
    drawLine(color, Offset(s * 0.63f, s * 0.63f), Offset(s * 0.86f, s * 0.86f), w, StrokeCap.Round)
}

@Composable
fun GlyphHome(size: Dp = 24.dp, color: Color = Color.White) = Canvas(Modifier.size(size)) {
    val s = this.size.minDimension
    val p = Path().apply {
        moveTo(s * 0.5f, s * 0.12f); lineTo(s * 0.92f, s * 0.5f); lineTo(s * 0.8f, s * 0.5f)
        lineTo(s * 0.8f, s * 0.88f); lineTo(s * 0.6f, s * 0.88f); lineTo(s * 0.6f, s * 0.62f)
        lineTo(s * 0.4f, s * 0.62f); lineTo(s * 0.4f, s * 0.88f); lineTo(s * 0.2f, s * 0.88f)
        lineTo(s * 0.2f, s * 0.5f); lineTo(s * 0.08f, s * 0.5f); close()
    }
    drawPath(p, color, style = Fill)
}

@Composable
fun GlyphCalendar(size: Dp = 24.dp, color: Color = Color.White) = Canvas(Modifier.size(size)) {
    val s = this.size.minDimension; val w = 1.8.dp.toPx()
    drawRoundRect(color, Offset(s * 0.12f, s * 0.2f), Size(s * 0.76f, s * 0.68f), CornerRadius(s * 0.14f), style = Stroke(w))
    drawLine(color, Offset(s * 0.12f, s * 0.4f), Offset(s * 0.88f, s * 0.4f), w)
    drawLine(color, Offset(s * 0.32f, s * 0.12f), Offset(s * 0.32f, s * 0.28f), w, StrokeCap.Round)
    drawLine(color, Offset(s * 0.68f, s * 0.12f), Offset(s * 0.68f, s * 0.28f), w, StrokeCap.Round)
    for (r in 0..1) for (c in 0..2) {
        drawCircle(color, s * 0.045f, Offset(s * (0.28f + 0.22f * c), s * (0.54f + 0.15f * r)))
    }
}

@Composable
fun GlyphFolder(size: Dp = 20.dp, color: Color = Color.White) = Canvas(Modifier.size(size)) {
    val s = this.size.minDimension
    drawRoundRect(color, Offset(s * 0.06f, s * 0.18f), Size(s * 0.4f, s * 0.2f), CornerRadius(s * 0.08f))
    drawRoundRect(color, Offset(s * 0.06f, s * 0.28f), Size(s * 0.88f, s * 0.58f), CornerRadius(s * 0.14f))
}
