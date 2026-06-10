@file:Suppress("AssignedValueIsNeverRead")

package io.github.chimio.inxlocker.ui.activity

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import io.github.chimio.inxlocker.util.HotReloadTrigger
import io.github.chimio.inxlocker.util.PrefsProvider
import io.github.chimio.inxlocker.util.XposedServiceHolder
import io.github.chimio.inxlocker.R
import io.github.chimio.inxlocker.ui.activity.ui.theme.InxLockerTheme
import io.github.chimio.inxlocker.ui.widget.SettingsGroup
import io.github.chimio.inxlocker.ui.widget.SettingsItem
import io.github.chimio.inxlocker.ui.widget.SettingsItemRow
import io.github.chimio.inxlocker.ui.widget.SettingsSwitchRow
import io.github.chimio.inxlocker.ui.widget.StableSettingsList
import io.github.chimio.inxlocker.ui.widget.SwitchItem
import io.github.chimio.inxlocker.ui.theme.Dimensions

@Immutable
class StableDrawable(val value: Drawable)

@Immutable
data class InstallerApp(
    val resolveInfo: ResolveInfo,
    val label: String,
    val packageName: String,
    val icon: StableDrawable
)

@Immutable
data class StableInstallerList(val list: List<InstallerApp>)

@Immutable
data class StableStringSet(val set: Set<String>)

class MainActivity : ComponentActivity() {

    private companion object

     fun getApkInstallerApps(): List<InstallerApp> {
        return try {
            val dummyApkUri = "content://nya.apk".toUri()
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(dummyApkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val resolveInfos = packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_ALL or PackageManager.MATCH_DEFAULT_ONLY
            )

            resolveInfos.filter { resolveInfo ->
                resolveInfo.activityInfo.packageName
                resolveInfo.activityInfo.exported
            }.map { resolveInfo ->
                InstallerApp(
                    resolveInfo = resolveInfo,
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    icon = StableDrawable(resolveInfo.loadIcon(packageManager))
                )
            }.sortedBy { it.label }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveSelectedInstaller(packageName: String) {
        PrefsProvider.putString(PrefsProvider.KEY_SELECTED_INSTALLER_PACKAGE, packageName)
    }

    private fun clearSelectedInstaller() {
        PrefsProvider.remove(PrefsProvider.KEY_SELECTED_INSTALLER_PACKAGE)
    }

    private fun saveForcedInstallerComponents(values: Set<String>) {
        PrefsProvider.putStringSet(PrefsProvider.KEY_FORCED_INSTALLER_COMPONENTS, values)
    }

    private fun setLauncherIconVisible(isVisible: Boolean) {
        val aliasComponent = ComponentName(this, "${packageName}.Home")
        packageManager.setComponentEnabledSetting(
            aliasComponent,
            if (isVisible) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun saveHideIconState(hide: Boolean) {
        PrefsProvider.putBoolean(PrefsProvider.KEY_HIDE_LAUNCHER_ICON, hide)
        setLauncherIconVisible(!hide)
    }

    private fun saveDebugLogEnabled(enabled: Boolean) {
        PrefsProvider.putBoolean(PrefsProvider.KEY_ENABLE_DEBUG_LOG, enabled)
    }

    private fun saveInterceptUninstallEnabled(enabled: Boolean) {
        PrefsProvider.putBoolean(PrefsProvider.KEY_INTERCEPT_UNINSTALL, enabled)
    }

    private fun saveFollowUninstallWithInstaller(enabled: Boolean) {
        PrefsProvider.putBoolean(PrefsProvider.KEY_FOLLOW_UNINSTALL_WITH_INSTALLER, enabled)
    }

    private fun saveSelectedUninstallerPackage(packageName: String) {
        PrefsProvider.putString(PrefsProvider.KEY_SELECTED_UNINSTALLER_PACKAGE, packageName)
    }

    private fun clearSelectedUninstallerPackage() {
        PrefsProvider.remove(PrefsProvider.KEY_SELECTED_UNINSTALLER_PACKAGE)
    }

    private fun getUninstallerApps(): List<InstallerApp> {
        return try {
            val dummyPackageUri = "package:meow".toUri()
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = dummyPackageUri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolveInfos = packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_ALL or PackageManager.MATCH_DEFAULT_ONLY
            )
            resolveInfos.filter { it.activityInfo.exported }.map { resolveInfo ->
                InstallerApp(
                    resolveInfo = resolveInfo,
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    icon = StableDrawable(resolveInfo.loadIcon(packageManager))
                )
            }.distinctBy { it.packageName }.sortedBy { it.label }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveInterceptSessionInstallEnabled(enabled: Boolean) {
        PrefsProvider.putBoolean(PrefsProvider.KEY_INTERCEPT_SESSION_INSTALL, enabled)
    }

    private fun saveFixPermissionsEnabled(enabled: Boolean) {
        PrefsProvider.putBoolean(PrefsProvider.KEY_FIX_PERMISSIONS, enabled)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InxLockerTheme {
                MainScreen()
            }
        }
    }

    @Composable
    private fun MainScreen() {
        var showInstallerDialog by remember { mutableStateOf(false) }
        var showUninstallerDialog by remember { mutableStateOf(false) }
        val installerListWrapper = remember { StableInstallerList(getApkInstallerApps()) }
        val uninstallerListWrapper = remember { StableInstallerList(getUninstallerApps()) }
        val installerList = installerListWrapper.list
        val uninstallerList = uninstallerListWrapper.list
        var selectedPackage by PrefsProvider.selectedInstallerPackage
        var forcedComponentsWrapper by remember {
            mutableStateOf(StableStringSet(PrefsProvider.forcedInstallerComponents.value))
        }
        val forcedComponents = forcedComponentsWrapper.set

        var followUninstallWithInstaller by PrefsProvider.followUninstallWithInstaller
        var selectedUninstallerPackage by PrefsProvider.selectedUninstallerPackage

        var hideIcon by PrefsProvider.hideLauncherIcon
        var debugLogEnabled by PrefsProvider.enableDebugLog
        var interceptUninstallEnabled by PrefsProvider.interceptUninstall
        var interceptSessionInstallEnabled by PrefsProvider.interceptSessionInstall
        var fixPermissionsEnabled by PrefsProvider.fixPermissions

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ModuleStatusCard()
                }

                item {
                    HotReloadCard()
                }

                item {
                    SettingsGroup(
                        title = stringResource(R.string.installer_settings_title),
                        items = StableSettingsList(listOf(
                            SettingsItem(
                                icon = if (selectedPackage == null) Icons.Default.Build else null,
                                drawableIcon = installerList.find { it.packageName == selectedPackage }?.icon?.value,
                                title = installerList.find { it.packageName == selectedPackage }?.label
                                    ?: stringResource(R.string.installer_system_default),
                                subtitle = installerList.find { it.packageName == selectedPackage }?.packageName
                                    ?: stringResource(R.string.installer_system_default_desc),
                                onClick = { showInstallerDialog = true }
                            )
                        ))
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Dimensions.SpaceXS, bottom = Dimensions.SpaceM)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth().animateContentSize(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column {
                            SettingsSwitchRow(
                                item = SwitchItem(
                                    icon = Icons.Default.Info,
                                    title = stringResource(R.string.hide_icon_title),
                                    subtitle = stringResource(R.string.hide_icon_desc),
                                    isChecked = hideIcon,
                                    onCheckedChange = { newState ->
                                        saveHideIconState(newState)
                                    }
                                ),
                                showDivider = true
                            )

                            SettingsSwitchRow(
                                item = SwitchItem(
                                    icon = Icons.Default.DateRange,
                                    title = stringResource(R.string.debug_log_title),
                                    subtitle = stringResource(R.string.debug_log_desc),
                                    isChecked = debugLogEnabled,
                                    onCheckedChange = { newState ->
                                        saveDebugLogEnabled(newState)
                                    }
                                ),
                                showDivider = true
                            )

                            SettingsSwitchRow(
                                item = SwitchItem(
                                    icon = Icons.Default.Delete,
                                    title = stringResource(R.string.intercept_uninstall_title),
                                    subtitle = stringResource(R.string.intercept_uninstall_desc),
                                    isChecked = interceptUninstallEnabled,
                                    onCheckedChange = { newState ->
                                        saveInterceptUninstallEnabled(newState)
                                    }
                                ),
                                showDivider = interceptUninstallEnabled
                            )

                            AnimatedVisibility(
                                visible = interceptUninstallEnabled,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column {
                                    SettingsSwitchRow(
                                        item = SwitchItem(
                                            icon = Icons.Default.Build,
                                            title = stringResource(R.string.uninstall_follow_installer_title),
                                            subtitle = stringResource(R.string.uninstall_follow_installer_desc),
                                            isChecked = followUninstallWithInstaller,
                                        onCheckedChange = { newState ->
                                            saveFollowUninstallWithInstaller(newState)
                                        }
                                        ),
                                        showDivider = !followUninstallWithInstaller
                                    )

                                    AnimatedVisibility(
                                        visible = !followUninstallWithInstaller,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        val selectedUninstaller = uninstallerList
                                            .find { it.packageName == selectedUninstallerPackage }
                                        SettingsItemRow(
                                            item = SettingsItem(
                                                icon = if (selectedUninstaller == null) Icons.Default.Delete else null,
                                                drawableIcon = selectedUninstaller?.icon?.value,
                                                title = selectedUninstaller?.label
                                                    ?: stringResource(R.string.uninstall_default_uninstaller_title),
                                                subtitle = selectedUninstaller?.packageName
                                                    ?: stringResource(R.string.uninstall_default_uninstaller_subtitle_default),
                                                onClick = { showUninstallerDialog = true }
                                            ),
                                            showDivider = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().animateContentSize(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column {
                            SettingsSwitchRow(
                                item = SwitchItem(
                                    icon = Icons.Default.Notifications,
                                    title = stringResource(R.string.intercept_session_install_title),
                                    subtitle = stringResource(R.string.intercept_session_install_desc),
                                    isChecked = interceptSessionInstallEnabled,
                                    onCheckedChange = { newState ->
                                        saveInterceptSessionInstallEnabled(newState)
                                        if (!newState) {
                                            saveFixPermissionsEnabled(false)
                                        }
                                    }
                                ),
                                showDivider = Build.VERSION.SDK_INT >= 34 && interceptSessionInstallEnabled
                            )

                            AnimatedVisibility(
                                visible = Build.VERSION.SDK_INT >= 34 && interceptSessionInstallEnabled,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                SettingsSwitchRow(
                                    item = SwitchItem(
                                        icon = Icons.Default.Lock,
                                        title = stringResource(R.string.fix_permissions_title),
                                        subtitle = stringResource(R.string.fix_permissions_desc),
                                        isChecked = fixPermissionsEnabled,
                                        onCheckedChange = { newState ->
                                            saveFixPermissionsEnabled(newState)
                                        }
                                    ),
                                    showDivider = false
                                )
                            }
                        }
                    }
                }

                item {
                    InstructionCard()
                }
            }
        }

        if (showInstallerDialog) {
            InstallerSelectionDialog(
                installerList = installerListWrapper,
                selectedPackage = selectedPackage,
                forcedComponents = forcedComponentsWrapper,
                onDismiss = { showInstallerDialog = false },
                onInstallerSelected = { packageName ->
                    saveSelectedInstaller(packageName)
                    showInstallerDialog = false
                },
                onClearSelection = {
                    clearSelectedInstaller()
                    showInstallerDialog = false
                },
                onToggleForceComponent = { packageName, className ->
                    val key = "$packageName/$className"
                    val newSet = forcedComponents.toMutableSet().apply {
                        if (!add(key)) remove(key)
                    }.toSet()
                    forcedComponentsWrapper = StableStringSet(newSet)
                    saveForcedInstallerComponents(newSet)
                }
            )
        }

        if (showUninstallerDialog) {
            UninstallerSelectionDialog(
                uninstallerList = uninstallerListWrapper,
                selectedPackage = selectedUninstallerPackage,
                onDismiss = { showUninstallerDialog = false },
                onUninstallerSelected = { packageName ->
                    saveSelectedUninstallerPackage(packageName)
                    showUninstallerDialog = false
                },
                onClearSelection = {
                    clearSelectedUninstallerPackage()
                    showUninstallerDialog = false
                }
            )
        }
    }

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    private fun UninstallerSelectionDialog(
        uninstallerList: StableInstallerList,
        selectedPackage: String?,
        onDismiss: () -> Unit,
        onUninstallerSelected: (String) -> Unit,
        onClearSelection: () -> Unit
    ) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
            ) {

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        InstallerItem(
                            icon = null,
                            label = stringResource(R.string.uninstall_default_uninstaller_system_label),
                            packageName = stringResource(R.string.uninstall_default_uninstaller_system_desc),
                            isSelected = selectedPackage == null,
                            isForced = false,
                            onClick = onClearSelection
                        )
                    }

                    if (uninstallerList.list.isNotEmpty()) {
                        itemsIndexed(uninstallerList.list) { _, app ->
                            InstallerItem(
                                icon = app.icon,
                                label = app.label,
                                packageName = app.packageName,
                                isSelected = selectedPackage == app.packageName,
                                isForced = false,
                                onClick = { onUninstallerSelected(app.packageName) }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    private fun InstallerSelectionDialog(
        installerList: StableInstallerList,
        selectedPackage: String?,
        forcedComponents: StableStringSet,
        onDismiss: () -> Unit,
        onInstallerSelected: (String) -> Unit,
        onClearSelection: () -> Unit,
        onToggleForceComponent: (packageName: String, className: String) -> Unit
    ) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
            ) {

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        InstallerItem(
                            icon = null,
                            label = stringResource(R.string.installer_system_default),
                            packageName = stringResource(R.string.installer_system_default_desc),
                            isSelected = selectedPackage == null,
                            isForced = false,
                            onClick = onClearSelection
                        )
                    }

                    if (installerList.list.isNotEmpty()) {
                        itemsIndexed(installerList.list) { _, installer ->
                            val className = installer.resolveInfo.activityInfo.name
                            val forceKey = "${installer.packageName}/$className"
                            val isForced = forcedComponents.set.contains(forceKey)
                            InstallerItem(
                                icon = installer.icon,
                                label = installer.label,
                                packageName = if (isForced) "${installer.packageName}\n$className" else installer.packageName,
                                isSelected = selectedPackage == installer.packageName,
                                isForced = isForced,
                                onClick = { onInstallerSelected(installer.packageName) },
                                onLongClick = {
                                    onToggleForceComponent(installer.packageName, className)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun InstallerItem(
        icon: StableDrawable?,
        label: String,
        packageName: String,
        isSelected: Boolean,
        isForced: Boolean,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = { onLongClick?.invoke() }
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (icon != null) {
                    val drawable = icon.value
                    val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
                    val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128
                    Image(
                        bitmap = drawable.toBitmap(w, h).asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Build,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        },
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isForced) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.selected),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun ModuleStatusCard() {
        val isActive by PrefsProvider.moduleActive
        val containerColor =
            if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.Info else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isActive) {
                            stringResource(R.string.module_status_active)
                        } else {
                            stringResource(R.string.module_status_inactive)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isActive) {
                            stringResource(R.string.module_status_active_desc)
                        } else {
                            stringResource(R.string.module_status_inactive_desc)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    @Composable
    private fun InstructionCard() {
        val context = this@MainActivity
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.instructions_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(R.string.instructions_content),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.4
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val url = context.getString(R.string.github_url)
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                    context.startActivity(intent)
                                } catch (_: Exception) {

                                }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.view_source_code),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.source_code_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun HotReloadCard() {
        val service by XposedServiceHolder.state
        val cap = remember(service) { HotReloadTrigger.probe() }
        var running by remember { mutableStateOf(false) }
        var summary by remember { mutableStateOf<String?>(null) }
        val available = cap is HotReloadTrigger.Capability.Available
        val textNoTargets = stringResource(R.string.hot_reload_no_targets)
        val textSummary = stringResource(R.string.hot_reload_summary)
        val textRunning = stringResource(R.string.hot_reload_running)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = available && !running) {
                    running = true
                    summary = null
                    if (!HotReloadTrigger.reloadAllStale(
                            onlyStale = true,
                            onFinished = { outcome ->
                                running = false
                                summary = if (outcome.total == 0) textNoTargets
                                else textSummary.format(outcome.success, outcome.failed, outcome.processDied, outcome.total)
                            }
                        )
                    ) {
                        running = false
                    }
                },
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = if (available) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.hot_reload_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = summary ?: when {
                            running -> textRunning
                            !available -> stringResource(R.string.hot_reload_unavailable)
                            else -> stringResource(R.string.hot_reload_initial_subtitle)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}