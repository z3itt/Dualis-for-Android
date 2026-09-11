package com.z3itt.dualis.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.z3itt.dualis.R

private val SplashSquircle = RoundedCornerShape(28)

@Composable
fun DualisSplash(dark: Boolean, modifier: Modifier = Modifier) {
    val background = if (dark) Color(0xFF121212) else Color.White
    val logo = if (dark) R.drawable.splash_overlay_dark else R.drawable.splash_overlay_light
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth, maxHeight) * 0.64f
        Image(
            painter = painterResource(logo),
            contentDescription = "DUΛLIS",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(side.coerceAtMost(340.dp).coerceAtLeast(220.dp))
                .clip(SplashSquircle),
        )
    }
}
