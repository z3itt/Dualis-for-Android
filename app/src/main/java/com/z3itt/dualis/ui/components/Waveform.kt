package com.z3itt.dualis.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.z3itt.dualis.ui.theme.DualisOrange

@Composable
fun Waveform(
    bars: List<Float>,
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = bars.ifEmpty { List(64) { 0.12f } }
    val idle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        val gap = 2f
        val w = (size.width - gap * (data.size - 1)) / data.size
        data.forEachIndexed { i, value ->
            val h = (value.coerceIn(0.08f, 1f) * size.height)
            val x = i * (w + gap)
            val y = (size.height - h) / 2
            val played = i.toFloat() / data.size <= progress
            drawRoundRect(
                color = if (played) DualisOrange else idle,
                topLeft = Offset(x, y),
                size = Size(w, h),
                cornerRadius = CornerRadius(2f, 2f),
            )
        }
    }
}
