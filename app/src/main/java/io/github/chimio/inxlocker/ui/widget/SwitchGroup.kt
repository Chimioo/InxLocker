package io.github.chimio.inxlocker.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.chimio.inxlocker.ui.theme.Dimensions
import androidx.compose.material3.Switch as M3Switch

@Composable
fun SwitchGroup(
    title: String,
    items: List<SwitchItem>
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimensions.SpaceXS, bottom = Dimensions.SpaceM)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            items.forEachIndexed { index, item ->
                SettingsSwitchRow(
                    item = item,
                    showDivider = index < items.size - 1
                )
            }
        }
    }
}

@Composable
fun SettingsSwitchRow(
    item: SwitchItem,
    showDivider: Boolean
) {
    var currentChecked by remember { mutableStateOf(item.isChecked) }
    val onToggle: (Boolean) -> Unit = { newValue ->
        currentChecked = newValue
        item.onCheckedChange(newValue)
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimensions.ListItem.M)
                .toggleable(
                    value = currentChecked,
                    onValueChange = onToggle,
                    role = Role.Switch
                )
                .padding(Dimensions.SpaceXXL),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(Dimensions.IconSize.L + Dimensions.SpaceXS)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.IconSize.S),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(Dimensions.SpaceL))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (item.subtitle.isNotEmpty()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimensions.SpaceXXS)
                    )
                }
            }

            Spacer(modifier = Modifier.width(Dimensions.SpaceM))

            M3Switch(
                checked = currentChecked,
                onCheckedChange = onToggle
            )
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Dimensions.SpaceXXL),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                thickness = Dimensions.Divider.Thin
            )
        }
    }
}