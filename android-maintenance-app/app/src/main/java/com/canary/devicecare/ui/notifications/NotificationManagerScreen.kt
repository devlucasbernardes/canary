package com.canary.devicecare.ui.notifications

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.canary.devicecare.R
import com.canary.devicecare.model.MirroredNotification
import com.canary.devicecare.model.NotificationAppEntry
import com.canary.devicecare.util.PreferencesManager
import com.canary.devicecare.viewmodel.NotificationViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationManagerScreen(viewModel: NotificationViewModel = viewModel()) {
    val watchState by viewModel.watchState.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val appList by viewModel.appList.collectAsState()
    val isNotificationAccessEnabled by viewModel.isNotificationAccessEnabled.collectAsState()
    val recentNotifications by viewModel.recentNotifications.collectAsState()

    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Check notification access when screen becomes visible
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkNotificationAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Bluetooth permission launcher
    var hasBluetoothPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasBluetoothPermission = permissions.values.all { it }
        if (hasBluetoothPermission) {
            viewModel.startScan()
        }
    }

    // Split apps into recommended and others
    val recommendedApps = appList.filter { it.packageName in PreferencesManager.RECOMMENDED_PACKAGES }
    val otherApps = appList.filter { it.packageName !in PreferencesManager.RECOMMENDED_PACKAGES }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.notification_manager_title)) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Notification Access Card
            if (!isNotificationAccessEnabled) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.enable_notification_access),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.notification_access_required),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.open_settings))
                            }
                        }
                    }
                }
            }

            // Smartwatch Connection Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (watchState.isConnected)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (watchState.isConnected) Icons.Filled.BluetoothConnected
                                else Icons.Filled.Watch,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = if (watchState.isConnected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.smartwatch_section),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (watchState.isConnected)
                                        stringResource(R.string.smartwatch_connected, watchState.deviceName ?: "")
                                    else stringResource(R.string.smartwatch_disconnected),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        if (watchState.isConnected) {
                            OutlinedButton(
                                onClick = { viewModel.disconnectWatch() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.disconnect))
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            permissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.BLUETOOTH_SCAN,
                                                    Manifest.permission.BLUETOOTH_CONNECT,
                                                    Manifest.permission.ACCESS_FINE_LOCATION
                                                )
                                            )
                                        } else {
                                            permissionLauncher.launch(
                                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !watchState.isScanning
                                ) {
                                    if (watchState.isScanning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.BluetoothSearching,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (watchState.isScanning)
                                            stringResource(R.string.scanning_devices)
                                        else stringResource(R.string.scan_devices)
                                    )
                                }

                                if (watchState.isScanning) {
                                    OutlinedButton(onClick = { viewModel.stopScan() }) {
                                        Text(stringResource(R.string.stop_scan))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Discovered devices
            if (discoveredDevices.isNotEmpty() && !watchState.isConnected) {
                items(discoveredDevices, key = { it.address }) { device ->
                    DiscoveredDeviceItem(
                        device = device,
                        onConnect = { viewModel.connectToDevice(device) }
                    )
                }
            }

            if (watchState.isScanning && discoveredDevices.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.scanning_devices),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Notification Mirror section
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.notification_mirror),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Recommended apps
            if (recommendedApps.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.recommended_apps),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(recommendedApps, key = { it.packageName }) { app ->
                    NotificationAppItem(
                        app = app,
                        onToggle = { enabled -> viewModel.toggleMirror(app.packageName, enabled) }
                    )
                }
            }

            // All other apps
            if (otherApps.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.all_apps),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(otherApps, key = { it.packageName }) { app ->
                    NotificationAppItem(
                        app = app,
                        onToggle = { enabled -> viewModel.toggleMirror(app.packageName, enabled) }
                    )
                }
            }

            // Recent notifications
            if (recentNotifications.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Divider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.recent_notifications),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(recentNotifications.take(20), key = { "${it.id}_${it.timestamp}" }) { notification ->
                    RecentNotificationItem(notification)
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun DiscoveredDeviceItem(device: BluetoothDevice, onConnect: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                val deviceName = try {
                    device.name ?: "Dispositivo desconhecido"
                } catch (e: SecurityException) {
                    "Dispositivo desconhecido"
                }
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilledTonalButton(onClick = onConnect) {
                Text(stringResource(R.string.connect))
            }
        }
    }
}

@Composable
fun NotificationAppItem(
    app: NotificationAppEntry,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            app.icon?.let { drawable ->
                Image(
                    bitmap = drawable.toBitmap(48, 48).asImageBitmap(),
                    contentDescription = app.appName,
                    modifier = Modifier.size(36.dp)
                )
            } ?: Icon(
                Icons.Filled.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (app.isMirrorEnabled)
                        stringResource(R.string.mirror_enabled)
                    else stringResource(R.string.mirror_disabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (app.isMirrorEnabled)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = app.isMirrorEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
fun RecentNotificationItem(notification: MirroredNotification) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale("pt", "BR")) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.NotificationsActive,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = notification.appName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = timeFormat.format(Date(notification.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (notification.title.isNotBlank()) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (notification.text.isNotBlank()) {
                    Text(
                        text = notification.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
