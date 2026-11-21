package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PendingStepBatchEntity
import com.example.data.local.SolstrideSettings
import com.example.data.local.SyncLogEntity
import com.example.data.model.ItemRarity
import com.example.data.model.PetRarity
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolstrideColors
import com.example.wear.HealthConnectState
import com.example.wear.HealthConnectSyncStatus
import com.example.wear.WatchLinkStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SolstrideCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    strongBorder: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val ext = LocalSolstrideColors.current
    val borderColor = if (strongBorder) ext.borderStrong else ext.borderSubtle
    val borderWidth = if (strongBorder) 2.dp else 1.5.dp

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        border = BorderStroke(borderWidth, borderColor),
        tonalElevation = if (ext.isOledBatterySaver) 0.dp else 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun TacticalStatBar(
    label: String,
    current: Long,
    maximum: Long,
    barColor: Color,
    modifier: Modifier = Modifier,
    suffix: String = ""
) {
    val safeMax = maximum.coerceAtLeast(1L)
    val fraction = (current.toFloat() / safeMax.toFloat()).coerceIn(0f, 1f)
    val ext = LocalSolstrideColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$current / $safeMax$suffix",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp),
            shape = RoundedCornerShape(7.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.5.dp, ext.borderStrong)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
                drawRect(
                    color = barColor,
                    topLeft = Offset.Zero,
                    size = Size(size.width * fraction, size.height)
                )
            }
        }
    }
}

@Composable
fun StrideGaugeRing(
    weeklySteps: Long,
    encounterPercent: Long,
    strideEnergy: Long,
    maxEnergy: Long,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val border = LocalSolstrideColors.current.borderStrong

    val encounterSweep = ((encounterPercent.coerceIn(0L, 100L) / 100f) * 280f)
    val energySweep = ((strideEnergy.toFloat() / maxEnergy.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f) * 280f)

    Box(
        modifier = modifier.size(196.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(196.dp)) {
            val outerStroke = 16.dp.toPx()
            val innerStroke = 10.dp.toPx()
            val padOuter = outerStroke / 2f + 4.dp.toPx()
            val padInner = padOuter + outerStroke + 8.dp.toPx()

            // Outer track: Encounter Gauge
            drawArc(
                color = track,
                startAngle = 130f,
                sweepAngle = 280f,
                useCenter = false,
                topLeft = Offset(padOuter, padOuter),
                size = Size(size.width - padOuter * 2, size.height - padOuter * 2),
                style = Stroke(width = outerStroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = primary,
                startAngle = 130f,
                sweepAngle = encounterSweep,
                useCenter = false,
                topLeft = Offset(padOuter, padOuter),
                size = Size(size.width - padOuter * 2, size.height - padOuter * 2),
                style = Stroke(width = outerStroke, cap = StrokeCap.Round)
            )

            // Inner track: Solar Stride Energy
            drawArc(
                color = track,
                startAngle = 130f,
                sweepAngle = 280f,
                useCenter = false,
                topLeft = Offset(padInner, padInner),
                size = Size(size.width - padInner * 2, size.height - padInner * 2),
                style = Stroke(width = innerStroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = tertiary,
                startAngle = 130f,
                sweepAngle = energySweep,
                useCenter = false,
                topLeft = Offset(padInner, padInner),
                size = Size(size.width - padInner * 2, size.height - padInner * 2),
                style = Stroke(width = innerStroke, cap = StrokeCap.Round)
            )

            // Subtle crisp ring outline for outdoor sunlight definition
            drawCircle(
                color = border.copy(alpha = 0.25f),
                radius = (size.minDimension / 2f) - 1.dp.toPx(),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "WEEKLY STEPS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format(Locale.US, "%,d", weeklySteps),
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "ENCOUNTER $encounterPercent%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun rarityColorFor(rarity: ItemRarity): Color {
    val ext = LocalSolstrideColors.current
    return when (rarity) {
        ItemRarity.COMMON -> ext.rarityCommon
        ItemRarity.RARE -> ext.rarityRare
        ItemRarity.EPIC -> ext.rarityEpic
        ItemRarity.SOLAR -> ext.raritySolar
        ItemRarity.ULTRA_RARE -> ext.rarityUltraRare
    }
}

@Composable
fun petRarityColorFor(rarity: PetRarity, onDarkSurface: Boolean = false): Color {
    if (onDarkSurface) {
        return when (rarity) {
            PetRarity.COMMON -> com.example.ui.theme.RarityCommonDark
            PetRarity.RARE -> com.example.ui.theme.RarityRareDark
            PetRarity.EPIC -> com.example.ui.theme.RarityEpicDark
            PetRarity.LEGENDARY -> com.example.ui.theme.RaritySolarDark
        }
    }
    val ext = LocalSolstrideColors.current
    return when (rarity) {
        PetRarity.COMMON -> ext.rarityCommon
        PetRarity.RARE -> ext.rarityRare
        PetRarity.EPIC -> ext.rarityEpic
        PetRarity.LEGENDARY -> ext.raritySolar
    }
}

@Composable
fun PetRarityBadge(
    rarity: PetRarity,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onDarkSurface: Boolean = false
) {
    val accent = petRarityColorFor(rarity, onDarkSurface = onDarkSurface)
    val icon = when (rarity) {
        PetRarity.COMMON -> Icons.Filled.Shield
        PetRarity.RARE -> Icons.Filled.Star
        PetRarity.EPIC -> Icons.Filled.AutoAwesome
        PetRarity.LEGENDARY -> Icons.Filled.WorkspacePremium
    }
    val fillAlpha = when {
        onDarkSurface && rarity == PetRarity.LEGENDARY -> 0.28f
        onDarkSurface -> 0.22f
        rarity == PetRarity.LEGENDARY -> 0.18f
        else -> 0.13f
    }
    val borderWidth = if (rarity == PetRarity.LEGENDARY) 1.5.dp else 1.dp

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = fillAlpha),
        border = BorderStroke(borderWidth, accent)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 2.dp else 3.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "${rarity.label} rarity",
                tint = accent,
                modifier = Modifier.size(if (compact) 11.dp else 13.dp)
            )
            Text(
                text = rarity.label.uppercase(Locale.US),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 9.5.sp else 10.5.sp,
                color = accent
            )
        }
    }
}

@Composable
fun SolCoinBadge(
    solCoins: Long,
    modifier: Modifier = Modifier
) {
    val ext = LocalSolstrideColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.5.dp, ext.borderStrong)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.MonetizationOn,
                contentDescription = "Sol Coins balance",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "$solCoins SOL COINS",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun ExpeditionLockBanner(
    remainingMs: Long,
    onJumpToExpedition: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val readyToClaim = remainingMs <= 0L
    SolstrideCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = if (readyToClaim) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        strongBorder = true
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (readyToClaim) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                contentDescription = "Hero expedition lock status",
                tint = if (readyToClaim) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (readyToClaim) "Expedition Complete — Claim Loot!" else "Hero Locked on Expedition",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (readyToClaim) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = if (readyToClaim) {
                        "Return to the Expedition tab to claim your gear and unlock character actions."
                    } else {
                        "Time left: ${com.example.domain.ExpeditionEngine.formatCountdown(remainingMs)} • Character actions locked until return, but phone pedometer & watch steps still count!"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (readyToClaim) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
            }
            if (onJumpToExpedition != null) {
                Button(
                    onClick = onJumpToExpedition,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(if (readyToClaim) "Claim" else "View")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchSyncBottomSheet(
    watchStatus: WatchLinkStatus,
    healthConnectStatus: HealthConnectSyncStatus,
    isOnline: Boolean,
    isSyncing: Boolean,
    pendingBatches: List<PendingStepBatchEntity>,
    syncLogs: List<SyncLogEntity>,
    settings: SolstrideSettings,
    onDismiss: () -> Unit,
    onSyncNow: () -> Unit,
    onRequestHealthConnectPermission: () -> Unit,
    onRequestHealthConnectBackgroundPermission: () -> Unit,
    onRequestBluetoothWatchPermission: () -> Unit,
    onInstallOrOpenHealthConnect: () -> Unit,
    onOpenSamsungHealth: () -> Unit,
    onToggleHealthConnectAllSources: () -> Unit,
    onToggleBatteryBatching: () -> Unit,
    onToggleHaptics: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Watch,
                    contentDescription = "Watch and Health Connect sync status",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "Watch, Pedometer & Cloud Sync",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = if (watchStatus.connected) {
                            "${watchStatus.watchDisplayName} • ${watchStatus.connectionTransport}"
                        } else {
                            watchStatus.watchDisplayName
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Live Bluetooth & Wear OS Watch Link Status Card
            SolstrideCard(
                containerColor = if (watchStatus.connected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                strongBorder = true
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Watch Connection",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = when {
                            watchStatus.connected -> "CONNECTED"
                            !watchStatus.bluetoothEnabled -> "BT OFF"
                            !watchStatus.bluetoothPermissionGranted -> "BT PERMISSION"
                            else -> "DISCONNECTED"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (watchStatus.connected) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when {
                        watchStatus.connected ->
                            "${watchStatus.watchDisplayName} detected via ${watchStatus.connectionTransport}. Phone pedometer is paused automatically so only watch steps count."
                        !watchStatus.bluetoothEnabled ->
                            "Bluetooth is currently OFF on your phone. Phone pedometer is active until Bluetooth is turned on and your watch connects."
                        !watchStatus.bluetoothPermissionGranted ->
                            "Allow Nearby Devices (Bluetooth) access so Solstride can detect Galaxy Watch, Pixel Watch, Garmin, or Fitbit connect/disconnect events immediately."
                        else ->
                            "No Bluetooth or Wear OS watch is currently connected. Built-in Phone Pedometer is actively counting your steps."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!watchStatus.bluetoothPermissionGranted) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRequestBluetoothWatchPermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sheet_grant_bluetooth_watch_button")
                    ) {
                        Icon(Icons.Filled.Watch, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Allow Bluetooth Watch Detection")
                    }
                }
            }

            // Health Connect Watch Bridge Card (Samsung Health / Galaxy Watch / Pixel Watch / Garmin / Fitbit)
            SolstrideCard(
                containerColor = when (healthConnectStatus.state) {
                    HealthConnectState.CONNECTED -> MaterialTheme.colorScheme.secondaryContainer
                    HealthConnectState.PERMISSION_REQUIRED, HealthConnectState.UPDATE_REQUIRED ->
                        MaterialTheme.colorScheme.primaryContainer
                    HealthConnectState.UNAVAILABLE -> MaterialTheme.colorScheme.surfaceVariant
                },
                strongBorder = true
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Watch Bridge (Health Connect)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = when (healthConnectStatus.state) {
                            HealthConnectState.CONNECTED -> "LINKED • 5S"
                            HealthConnectState.PERMISSION_REQUIRED -> "ACTION NEEDED"
                            HealthConnectState.UPDATE_REQUIRED -> "INSTALL NEEDED"
                            HealthConnectState.UNAVAILABLE -> "UNAVAILABLE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (healthConnectStatus.state) {
                        HealthConnectState.CONNECTED -> {
                            val filteredNote = if (healthConnectStatus.phoneStepsIgnoredToday > 0L) {
                                " (${healthConnectStatus.phoneStepsIgnoredToday} phone-only steps filtered to prevent double-counting)."
                            } else {
                                "."
                            }
                            "Source: ${healthConnectStatus.primaryWatchSource} • ${String.format(Locale.US, "%,d", healthConnectStatus.watchStepsToday)} watch steps read today$filteredNote Tip: In Samsung Health -> Settings -> Health Connect, ensure Steps access is ON."
                        }
                        HealthConnectState.PERMISSION_REQUIRED ->
                            "Galaxy Watch (Samsung Health), Pixel Watch (Fitbit), Garmin, and Wear OS watches sync steps through Android Health Connect. Grant Read Steps access so Solstride can pull your watch steps every 5 seconds."
                        HealthConnectState.UPDATE_REQUIRED ->
                            "Health Connect needs to be installed or updated from Google Play to read steps from Samsung Health, Fitbit, or Garmin."
                        HealthConnectState.UNAVAILABLE ->
                            "Health Connect is not available on this Android version. Solstride's built-in Phone Pedometer and Wear OS Data Layer remain active."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(10.dp))
                when (healthConnectStatus.state) {
                    HealthConnectState.PERMISSION_REQUIRED -> {
                        Button(
                            onClick = onRequestHealthConnectPermission,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sheet_grant_health_connect_button")
                        ) {
                            Icon(Icons.Filled.Watch, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant Watch Step Access (Health Connect)")
                        }
                    }
                    HealthConnectState.UPDATE_REQUIRED -> {
                        Button(
                            onClick = onInstallOrOpenHealthConnect,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sheet_install_health_connect_button")
                        ) {
                            Text("Install / Update Health Connect")
                        }
                    }
                    HealthConnectState.CONNECTED -> {
                        if (healthConnectStatus.backgroundReadSupported && !healthConnectStatus.backgroundReadGranted) {
                            Button(
                                onClick = onRequestHealthConnectBackgroundPermission,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("sheet_grant_hc_background_button")
                            ) {
                                Text("Enable Closed-App Health Connect Sync")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        } else if (healthConnectStatus.backgroundReadGranted) {
                            Text(
                                text = "Closed-App Background Health Connect Sync: ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        androidx.compose.material3.OutlinedButton(
                            onClick = onInstallOrOpenHealthConnect,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sheet_open_health_connect_settings_button")
                        ) {
                            Text("Manage Health Connect Permissions")
                        }
                    }
                    HealthConnectState.UNAVAILABLE -> Unit
                }
            }

            // Dedicated Samsung Health Compatibility Card
            SolstrideCard(
                containerColor = if (healthConnectStatus.samsungHealthLinked) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                strongBorder = healthConnectStatus.samsungHealthLinked
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Samsung Health",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = when {
                            healthConnectStatus.samsungHealthLinked -> "SYNCING"
                            healthConnectStatus.samsungHealthInstalled -> "INSTALLED"
                            else -> "COMPATIBLE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (healthConnectStatus.samsungHealthLinked) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when {
                        healthConnectStatus.samsungHealthLinked ->
                            "Samsung Health today: ${String.format(Locale.US, "%,d", healthConnectStatus.samsungHealthStepsToday)} deduplicated steps. Galaxy Watch & Samsung Health steps sync automatically via Health Connect without double-counting."
                        healthConnectStatus.samsungHealthInstalled ->
                            "Samsung Health is installed! To sync Galaxy Watch or Samsung Health steps:\n1. Open Samsung Health → ⋮ Settings → Health Connect\n2. Allow Samsung Health to write Steps\n3. Ensure Solstride has Health Connect Steps access above."
                        else ->
                            "Compatible with Samsung Health & Galaxy Watch via Android Health Connect. Install Samsung Health to sync Galaxy Watch steps directly into Solstride."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                androidx.compose.material3.OutlinedButton(
                    onClick = onOpenSamsungHealth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sheet_open_samsung_health_button")
                ) {
                    Text(
                        if (healthConnectStatus.samsungHealthInstalled) {
                            "Open Samsung Health"
                        } else {
                            "Get Samsung Health on Play Store"
                        }
                    )
                }
            }

            SolstrideCard(strongBorder = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isOnline) Icons.Filled.CheckCircle else Icons.Filled.CloudOff,
                            contentDescription = if (isOnline) "Cloud online" else "Cloud offline",
                            tint = if (isOnline) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isOnline) "Cloud Online" else "Offline — Local Queue Active",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Text(
                        text = "${pendingBatches.size} Queued",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Steps from your phone's built-in pedometer and paired Wear OS / Galaxy Watch (auto-syncing every 5 seconds) cache locally in Room when offline and merge safely into Cloud Firestore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onSyncNow,
                    enabled = !isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sync_watch_cloud_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync Pedometer, Watch & Cloud")
                    }
                }
            }

            // Health Connect Sources, Battery Efficiency & Haptics Toggles
            SolstrideCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Watch,
                            contentDescription = "Include all Health Connect step sources",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Read All Health Connect Sources", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "ON = watch + fitness apps; OFF = watch/wearables only (avoids phone double-count)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = settings.healthConnectAllSources,
                        onCheckedChange = { onToggleHealthConnectAllSources() },
                        modifier = Modifier.testTag("hc_all_sources_switch")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BatterySaver,
                            contentDescription = "Hardware FIFO battery batching",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Hardware Step Batching (5s FIFO)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Minimizes wakeups on extended walking sessions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = settings.batteryBatchingEnabled,
                        onCheckedChange = { onToggleBatteryBatching() },
                        modifier = Modifier.testTag("battery_batching_switch")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Vibration,
                            contentDescription = "Combat and encounter haptic cues",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Gameplay Haptic Alerts", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Distinct pulses for encounters, strikes, and level-ups",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = settings.hapticsEnabled,
                        onCheckedChange = { onToggleHaptics() },
                        modifier = Modifier.testTag("haptics_switch")
                    )
                }
            }

            if (syncLogs.isNotEmpty()) {
                Text(
                    text = "Recent Sync Log",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                syncLogs.take(4).forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${entry.source}: ${entry.summary}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = timeFormatter.format(Date(entry.timestampMs)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
