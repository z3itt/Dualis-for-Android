package com.z3itt.dualis.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.z3itt.dualis.domain.model.StemMode

private val modes = listOf(
    StemMode.ORIGINAL to "Normal",
    StemMode.VOCALS to "Vocal",
    StemMode.INSTRUMENTAL to "Inst",
)

@Composable
fun StemModeToggle(
    value: StemMode,
    onChange: (StemMode) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val index = modes.indexOfFirst { it.first == value }.coerceAtLeast(0)
    val height = if (compact) 32.dp else 36.dp
    BoxWithConstraints(
        modifier = modifier
            .selectableGroup()
            .height(height)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp),
    ) {
        val segment = maxWidth / modes.size
        val offset by animateDpAsState(
            targetValue = segment * index,
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            label = "stemOffset",
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            modes.forEach { (mode, label) ->
                val active = value == mode
                val color by animateColorAsState(
                    targetValue = if (active) {
                        MaterialTheme.colorScheme.background
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    animationSpec = tween(180),
                    label = "stemLabel",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .selectable(selected = active, onClick = { onChange(mode) }, role = Role.RadioButton),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = if (compact) 11.sp else 12.sp,
                        color = color,
                    )
                }
            }
        }
    }
}
