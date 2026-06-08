package io.github.chimio.inxlocker.ui.widget

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

@Immutable
data class SettingsItem(
    val icon: ImageVector? = null,
    val drawableIcon: Drawable? = null,
    val title: String,
    val subtitle: String = "",
    val onClick: () -> Unit
)