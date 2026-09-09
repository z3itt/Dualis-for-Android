package com.z3itt.dualis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
fun StemModeToggle(value: StemMode, onChange: (StemMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .selectableGroup()
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        modes.forEach { (mode, label) ->
            val active = value == mode
            Text(
                text = label,
                fontSize = 12.sp,
                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .selectable(selected = active, onClick = { onChange(mode) }, role = Role.RadioButton)
                    .background(if (active) Color(0xFF171717) else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .height(24.dp),
            )
        }
    }
}
