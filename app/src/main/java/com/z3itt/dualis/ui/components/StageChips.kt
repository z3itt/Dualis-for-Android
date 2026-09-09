package com.z3itt.dualis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.z3itt.dualis.domain.library.LibraryRules
import com.z3itt.dualis.ui.theme.DualisOrange

private val LABELS = mapOf(
    "download" to "Download",
    "decode" to "Decode",
    "infer" to "Infer",
    "export" to "Export",
)

@Composable
fun StageChips(stage: String?) {
    val active = LibraryRules.stageIndex(stage ?: "download")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
        LibraryRules.STAGES.forEachIndexed { index, item ->
            val current = index == active
            val on = index <= active
            val bg = when {
                current -> DualisOrange
                on -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.background
            }
            val fg = when {
                current -> Color.White
                on -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = LABELS[item] ?: item,
                color = fg,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}
