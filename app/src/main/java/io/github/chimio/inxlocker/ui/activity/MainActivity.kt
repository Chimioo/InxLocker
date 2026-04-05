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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.factory.prefs
import io.github.chimio.inxlocker.R
import io.github.chimio.inxlocker.ui.activity.ui.theme.InxLockerTheme
import io.github.chimio.inxlocker.ui.widget.SettingsGroup
import io.github.chimio.inxlocker.ui.widget.SettingsItem
import io.github.chimio.inxlocker.ui.widget.SettingsItemRow
import io.github.chimio.inxlocker.ui.widget.SettingsSwitchRow
import io.github.chimio.inxlocker.ui.widget.SwitchItem
import io.github.chimio.inxlocker.ui.theme.Dimensions
import io.github.chimio.inxlocker.util.PrefsProvider

data class InstallerApp(
    val resolveInfo: ResolveInfo,
    val label: String,
    val packageName: String,
    val icon: Drawable
)

class MainActivity : ComponentActivity() {

    private companion object {
        private const val PREFS_NAME = "selected_installer_package"
        private const val KEY_FORCED_INSTALLER_COMPONENTS = "forced_installer_components"
        private const val KEY_FOLLOW_UNINSTALL_WITH_INSTALLER = "follow_uninstall_with_installer"
        private const val KEY_SELECTED_UNINSTALLER_PACKAGE = "selected_uninstaller_package"
    }

    private fun getApkInstallerApps(): List<InstallerApp> {
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
                    icon = resolveInfo.loadIcon(packageManager)
                )
            }.sortedBy { it.label }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveSelectedInstaller(packageName: String) {
        prefs(PREFS_NAME).edit {
            putString("selected_installer_package", packageName)
        }
    }

    private fun getSavedInstallerPackage(): String? {
        return try {
            prefs(PREFS_NAME).getString("selected_installer_package")
        } catch (_: Exception) {
            null
        }
    }

    private fun clearSelectedInstaller() {
        prefs(PREFS_NAME).edit {
            remove("selected_installer_package")
        }
    }

    private fun getForcedInstallerComponents(): Set<String> {
        return try {
            prefs(PREFS_NAME).getStringSet(KEY_FORCED_INSTALLER_COMPONENTS, emptySet())
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun saveForcedInstallerComponents(values: Set<String>) {
        try {
            prefs(PREFS_NAME).edit {
                putStringSet(KEY_FORCED_INSTALLER_COMPONENTS, values)
            }
        } catch (_: Exception) {
        }
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
        prefs().edit {
            putBoolean("hide_launcher_icon", hide)
        }
        setLauncherIconVisible(!hide)
    }

    private fun getHideIconState(): Boolean {
        return try {
            prefs().getBoolean("hide_launcher_icon", false)
        } catch (_: Exception) {
            false
        }
    }

    private fun saveDebugLogEnabled(enabled: Boolean) {
        try {
            prefs(PREFS_NAME).edit {
                putBoolean("enable_debug_log", enabled)
            }
        } catch (_: Exception) {
        }
     
    }

    private fun getDebugLogEnabled(): Boolean {
        return try {
            prefs(PREFS_NAME).getBoolean("enable_debug_log", true)
        } catch (_: Exception) {
            true
        }
    }

    private fun saveInterceptUninstallEnabled(enabled: Boolean) {
        try {
            prefs(PREFS_NAME).edit {
                putBoolean("intercept_uninstall", enabled)
            }
        } catch (_: Exception) {
        }
       
    }

    private fun getInterceptUninstallEnabled(): Boolean {
        return try {
            prefs(PREFS_NAME).getBoolean("intercept_uninstall", false)
        } catch (_: Exception) {
            false
        }
    }

    private fun saveFollowUninstallWithInstaller(enabled: Boolean) {
        try {
            prefs(PREFS_NAME).edit {
                putBoolean(KEY_FOLLOW_UNINSTALL_WITH_INSTALLER, enabled)
            }
        } catch (_: Exception) {
        }
      
    }

    private fun getFollowUninstallWithInstaller(): Boolean {
        return try {
            prefs(PREFS_NAME).getBoolean(KEY_FOLLOW_UNINSTALL_WITH_INSTALLER, true)
        } catch (_: Exception) {
            true
        }
    }

    private fun saveSelectedUninstallerPackage(packageName: String) {
        try {
            prefs(PREFS_NAME).edit {
                putString(KEY_SELECTED_UNINSTALLER_PACKAGE, packageName)
            }
        } catch (_: Exception) {
        }
       
    }

    private fun clearSelectedUninstallerPackage() {
        try {
            prefs(PREFS_NAME).edit {
                remove(KEY_SELECTED_UNINSTALLER_PACKAGE)
            }
        } catch (_: Exception) {
        }
    }

    private fun getSelectedUninstallerPackage(): String? {
        return try {
            prefs(PREFS_NAME).getString(KEY_SELECTED_UNINSTALLER_PACKAGE)
        } catch (_: Exception) {
            null
        }
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
                    icon = resolveInfo.loadIcon(packageManager)
                )
            }.distinctBy { it.packageName }.sortedBy { it.label }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveInterceptSessionInstallEnabled(enabled: Boolean) {
        try {
            prefs(PREFS_NAME).edit {
                putBoolean("intercept_session_install", enabled)
            }
        } catch (_: Exception) {
        }
    
    }

    private fun getInterceptSessionInstallEnabled(): Boolean {
        return try {
            prefs(PREFS_NAME).getBoolean("intercept_session_install", false)
        } catch (_: Exception) {
            false
        }
    }

    private fun saveFixPermissionsEnabled(enabled: Boolean) {
        try {
            prefs(PREFS_NAME).edit {
                putBoolean("fix_permissions", enabled)
            }
        } catch (_: Exception) {
        }
     
    }

    private fun getFixPermissionsEnabled(): Boolean {
        return try {
            prefs(PREFS_NAME).getBoolean("fix_permissions", false)
        } catch (_: Exception) {
            false
        }
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
        val installerList = remember { getApkInstallerApps() }
        val uninstallerList = remember { getUninstallerApps() }
        var selectedPackage by remember {
            mutableStateOf(getSavedInstallerPackage())
        }
        var forcedComponents by remember { mutableStateOf(getForcedInstallerComponents()) }

        var followUninstallWithInstaller by remember {
            mutableStateOf(
                getFollowUninstallWithInstaller()
            )
        }
        var selectedUninstallerPackage by remember { mutableStateOf(getSelectedUninstallerPackage()) }

        var hideIcon by remember { mutableStateOf(getHideIconState()) }
        var debugLogEnabled by remember { mutableStateOf(getDebugLogEnabled()) }
        var interceptUninstallEnabled by remember { mutableStateOf(getInterceptUninstallEnabled()) }
        var interceptSessionInstallEnabled by remember {
            mutableStateOf(
                getInterceptSessionInstallEnabled()
            )
        }
        var fixPermissionsEnabled by remember { mutableStateOf(getFixPermissionsEnabled()) }

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
                    SettingsGroup(
                        title = stringResource(R.string.installer_settings_title),
                        items = listOf(
                            SettingsItem(
                                icon = if (selectedPackage == null) Icons.Default.Build else null,
                                drawableIcon = installerList.find { it.packageName == selectedPackage }?.icon,
                                title = installerList.find { it.packageName == selectedPackage }?.label
                                    ?: stringResource(R.string.installer_system_default),
                                subtitle = installerList.find { it.packageName == selectedPackage }?.packageName
                                    ?: stringResource(R.string.installer_system_default_desc),
                                onClick = { showInstallerDialog = true }
                            )
                        )
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
                        modifier = Modifier.fillMaxWidth(),
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
                                        hideIcon = newState
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
                                        debugLogEnabled = newState
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
                                        interceptUninstallEnabled = newState
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
                                                followUninstallWithInstaller = newState
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
                                                drawableIcon = selectedUninstaller?.icon,
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
                        modifier = Modifier.fillMaxWidth(),
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
                                        interceptSessionInstallEnabled = newState
                                        saveInterceptSessionInstallEnabled(newState)
                                        if (!newState) {
                                            fixPermissionsEnabled = false
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
                                            fixPermissionsEnabled = newState
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
                installerList = installerList,
                selectedPackage = selectedPackage,
                forcedComponents = forcedComponents,
                onDismiss = { showInstallerDialog = false },
                onInstallerSelected = { packageName ->
                    saveSelectedInstaller(packageName)
                    selectedPackage = packageName
                    showInstallerDialog = false
                },
                onClearSelection = {
                    clearSelectedInstaller()
                    selectedPackage = null
                    showInstallerDialog = false
                },
                onToggleForceComponent = { packageName, className ->
                    val key = "$packageName/$className"
                    val newSet = forcedComponents.toMutableSet().apply {
                        if (!add(key)) remove(key)
                    }.toSet()
                    forcedComponents = newSet
                    saveForcedInstallerComponents(newSet)
                }
            )
        }

        if (showUninstallerDialog) {
            UninstallerSelectionDialog(
                uninstallerList = uninstallerList,
                selectedPackage = selectedUninstallerPackage,
                onDismiss = { showUninstallerDialog = false },
                onUninstallerSelected = { packageName ->
                    saveSelectedUninstallerPackage(packageName)
                    selectedUninstallerPackage = packageName
                    showUninstallerDialog = false
                },
                onClearSelection = {
                    clearSelectedUninstallerPackage()
                    selectedUninstallerPackage = null
                    showUninstallerDialog = false
                }
            )
        }
    }

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    private fun UninstallerSelectionDialog(
        uninstallerList: List<InstallerApp>,
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

                    if (uninstallerList.isNotEmpty()) {
                        itemsIndexed(uninstallerList) { _, app ->
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
        installerList: List<InstallerApp>,
        selectedPackage: String?,
        forcedComponents: Set<String>,
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

                    if (installerList.isNotEmpty()) {
                        itemsIndexed(installerList) { _, installer ->
                            val className = installer.resolveInfo.activityInfo.name
                            val forceKey = "${installer.packageName}/$className"
                            val isForced = forcedComponents.contains(forceKey)
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
        icon: Drawable?,
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
                    val w = if (icon.intrinsicWidth > 0) icon.intrinsicWidth else 128
                    val h = if (icon.intrinsicHeight > 0) icon.intrinsicHeight else 128
                    Image(
                        bitmap = icon.toBitmap(w, h).asImageBitmap(),
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
        val isActive = runCatching { YukiHookAPI.Status.isXposedModuleActive }.getOrDefault(false)
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
}