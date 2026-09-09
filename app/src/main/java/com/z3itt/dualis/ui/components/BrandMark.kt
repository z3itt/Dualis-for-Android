package com.z3itt.dualis.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File

private val Squircle = RoundedCornerShape(28)

@Composable
fun BrandMark(dark: Boolean, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val asset = if (dark) "brand/dualis-dark.jpg" else "brand/dualis-light.jpg"
    val context = LocalContext.current
    val bitmap = remember(asset) {
        context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "DUΛLIS",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(Squircle)
            .background(if (dark) Color(0xFF1C1C1C) else Color.White),
    )
}

@Composable
fun CoverArt(path: String?, title: String, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val file = path?.let { File(it) }
    val bitmap = remember(path) {
        if (file != null && file.isFile) BitmapFactory.decodeFile(file.absolutePath) else null
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF26522).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.GraphicEq, contentDescription = title, tint = Color(0xFFF26522))
        }
    }
}
