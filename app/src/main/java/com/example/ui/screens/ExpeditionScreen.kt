package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.data.local.PendingStepBatchEntity
import com.example.data.model.DerivedHeroStats
import com.example.data.model.HardcoreRpgState
import com.example.data.model.HeroProfile
import com.example.data.model.InventoryItem
import com.example.data.model.LootChest
import com.example.data.model.OwnedPet
import com.example.data.model.TownNpcRole
import com.example.data.model.TownPresencePlayer
import com.example.data.model.WorldFogState
import com.example.domain.ExpeditionEngine
import com.example.domain.ExpeditionOutcomeReport
import com.example.domain.ProgressionEngine
import com.example.domain.WayfarerEventSpec
import com.example.hardware.StepSensorStatus
import com.example.hardware.StepTrackerTelemetry
import com.example.ui.components.HeroShowcaseCard
import com.example.ui.components.HeroSleepingBanner
import com.example.ui.components.SolCoinBadge
import com.example.ui.components.SolstrideCard
import com.example.ui.components.StrideGaugeRing
import com.example.ui.components.SunhavenTownPlazaCard
import com.example.ui.components.TacticalStatBar
import com.example.ui.components.TimedExpeditionCard
import com.example.ui.components.WidgetHubQuickCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolstrideColors
import com.example.wear.HealthConnectState
import com.example.wear.HealthConnectSyncStatus
import com.example.wear.WatchLinkStatus
import com.example.widget.WidgetSnapshotState
import java.util.Locale
import kotlin.math.roundToInt

private enum class TownHubSubTab(val label: String) {
    PLAZA("Plaza"),
    JOURNEY("Journey"),
    TRACKERS("Trackers")
}

@Composable
fun ExpeditionScreen(
    profile: HeroProfile,
    stats: DerivedHeroStats,
    hardcoreState: HardcoreRpgState,
    inventory: List<InventoryItem>,
    chests: List<LootChest>,
    equippedPet: OwnedPet?,
    telemetry: StepTrackerTelemetry,
    todayPedometerSteps: Long,
    dailyStepGoal: Long,
    pedometerEnabled: Boolean,
    isWatchConnected: Boolean,
    watchStatus: WatchLinkStatus,
    healthConnectStatus: HealthConnectSyncStatus,
    pendingBatches: List<PendingStepBatchEntity>,
    isOnline: Boolean,
    battleActive: Boolean,
    currentTimeMs: Long,
    lastExpeditionReport: ExpeditionOutcomeReport?,
    onRequestStepPermission: () -> Unit,
    onTogglePedometer: () -> Unit,
    onResetPedometerSession: () -> Unit,
    onCycleDailyStepGoal: () -> Unit,
    onRequestHealthConnectPermission: () -> Unit,
    onRequestBluetoothWatchPermission: () -> Unit,
    onInstallOrOpenHealthConnect: () -> Unit,
    onOpenSamsungHealth: () -> Unit,
    onEngageBattle: (Boolean) -> Unit,
    onRestAtCamp: () -> Unit,
    onOpenWatchSheet: () -> Unit,
    onStartTimedExpedition: (Int) -> Unit,
    onStopTimedExpedition: () -> Unit,
    onClaimExpedition: () -> Unit,
    onOpenChest: (LootChest) -> Unit,
    onDismissExpeditionReport: () -> Unit,
    onSelectRealm: (Int) -> Unit = {},
    onTriggerWayfarerEvent: (WayfarerEventSpec) -> Unit = {},
    widgetSnapshot: WidgetSnapshotState = WidgetSnapshotState(),
    widgetAutoSyncConfig: com.example.widget.WidgetAutoSyncConfig = com.example.widget.WidgetAutoSyncConfig(),
    onOpenWidgetStudio: () -> Unit = {},
    onSyncWidgetsNow: () -> Unit = {},
    isHeroSleeping: Boolean = false,
    sleepSourceLabel: String = "",
    activeSleepMinutes: Long = 0L,
    onWakeHero: () -> Unit = {},
    worldFog: WorldFogState = WorldFogState(),
    townPlayers: List<TownPresencePlayer> = emptyList(),
    localTownPos: Pair<Float, Float> = 0.50f to 0.58f,
    localTownFacingRight: Boolean = true,
    localTownEmote: String = "",
    unclaimedQuestCount: Int = 0,
    onWalkToTown: (Float, Float) -> Unit = { _, _ -> },
    onSendTownEmote: (String) -> Unit = {},
    onOpenTownNpc: (TownNpcRole) -> Unit = {},
    onInspectTownPlayer: (TownPresencePlayer) -> Unit = {},
    onOpenCharacterMirror: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val encounterReady = profile.encounterProgress >= 100L
    val expeditionLocked = profile.hasActiveExpedition()
    val ext = LocalSolstrideColors.current
    val realm = ProgressionEngine.realmSpec(profile.currentRealm.toInt())
    var activeTownTab by rememberSaveable { mutableStateOf(TownHubSubTab.PLAZA) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isHeroSleeping) {
            item {
                HeroSleepingBanner(
                    sleepSourceLabel = sleepSourceLabel,
                    activeSleepMinutes = activeSleepMinutes,
                    onWakeHero = onWakeHero
                )
            }
        }

        // Purpose-Sorted 3-Way Town Switcher: Plaza | Journey | Trackers
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TownHubSubTab.entries.forEachIndexed { idx, tab ->
                    val label = when (tab) {
                        TownHubSubTab.PLAZA -> tab.label
                        TownHubSubTab.JOURNEY -> if (encounterReady || battleActive) "${tab.label} •" else tab.label
                        TownHubSubTab.TRACKERS -> tab.label
                    }
                    SegmentedButton(
                        selected = activeTownTab == tab,
                        onClick = { activeTownTab = tab },
                        shape = SegmentedButtonDefaults.itemShape(idx, TownHubSubTab.entries.size),
                        modifier = Modifier.testTag("town_subtab_${tab.name.lowercase()}"),
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    TownHubSubTab.PLAZA -> Icons.Filled.Storefront
                                    TownHubSubTab.JOURNEY -> Icons.AutoMirrored.Filled.DirectionsWalk
                                    TownHubSubTab.TRACKERS -> Icons.Filled.Watch
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }

        when (activeTownTab) {
            TownHubSubTab.PLAZA -> {
                // Compact Purse & Hero Quick Summary Strip
                item {
                    SolstrideCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${profile.displayName} • Lv.${profile.level} ${profile.resolvedClass().title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Realm ${realm.realmNumber}: ${realm.name} • Power ${stats.computedHeroPower}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            SolCoinBadge(solCoins = profile.solCoins)
                        }
                    }
                }

                // Walkable Real-Time Multiplayer Sunhaven Plaza + 4 Town NPCs (Pet Store, Material Vendor, Skill Trainer, Blacksmith)
                item {
                    SunhavenTownPlazaCard(
                        profile = profile,
                        worldFog = worldFog,
                        townPlayers = townPlayers,
                        localPos = localTownPos,
                        localFacingRight = localTownFacingRight,
                        localEmote = localTownEmote,
                        unclaimedQuestCount = unclaimedQuestCount,
                        onWalkTo = onWalkToTown,
                        onSendEmote = onSendTownEmote,
                        onOpenNpc = onOpenTownNpc,
                        onInspectPlayer = onInspectTownPlayer,
                        onOpenCharacterMirror = onOpenCharacterMirror
                    )
                }

                // Unopened Loot Vault Quick Callout
                if (chests.isNotEmpty()) {
                    item {
                        val topChest = chests.first()
                        SolstrideCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            strongBorder = true
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Inventory2,
                                        contentDescription = "Unopened treasure chests",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "${chests.size} Sealed Chest${if (chests.size == 1) "" else "s"} Waiting!",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "${topChest.resolvedTier().title} (${topChest.ultraRareChancePct}% Ultra Rare odds)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onOpenChest(topChest) },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("expedition_quick_open_chest_button")
                                ) {
                                    Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Loot")
                                }
                            }
                        }
                    }
                }

                // Quick Navigation Row into Journey & Step Trackers
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { activeTownTab = TownHubSubTab.JOURNEY },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("plaza_jump_journey_button"),
                            border = BorderStroke(1.5.dp, ext.borderStrong)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (encounterReady || battleActive) "Journey • Ready" else "Realms & Quests")
                        }
                        OutlinedButton(
                            onClick = { activeTownTab = TownHubSubTab.TRACKERS },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("plaza_jump_trackers_button"),
                            border = BorderStroke(1.5.dp, ext.borderStrong)
                        ) {
                            Icon(Icons.Filled.Watch, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Steps & Watch")
                        }
                    }
                }
            }

            TownHubSubTab.JOURNEY -> {
                // Visible 2D Hero Character Showcase with Custom Avatar, 6-Slot Equipped Gear, World Fog & Pet
                item {
                    HeroShowcaseCard(
                        profile = profile,
                        stats = stats,
                        inventory = inventory,
                        equippedPet = equippedPet,
                        compact = true,
                        isSleeping = isHeroSleeping,
                        sleepSourceLabel = sleepSourceLabel,
                        worldFog = worldFog
                    )
                }

        // Unopened Loot Vault Quick Callout
        if (chests.isNotEmpty()) {
            item {
                val topChest = chests.first()
                SolstrideCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    strongBorder = true
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Inventory2,
                                contentDescription = "Unopened treasure chests",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "${chests.size} Sealed Chest${if (chests.size == 1) "" else "s"} Waiting!",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${topChest.resolvedTier().title} (${topChest.ultraRareChancePct}% Ultra Rare odds)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onOpenChest(topChest) },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("expedition_quick_open_chest_button")
                        ) {
                            Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Loot")
                        }
                    }
                }
            }
        }

        // Timed Expeditions (5m to 23h) with Survival Risk, Early Stop & Loot Chests
        item {
            TimedExpeditionCard(
                profile = profile,
                stats = stats,
                currentTimeMs = currentTimeMs,
                battleActive = battleActive,
                lastReport = lastExpeditionReport,
                onStartExpedition = onStartTimedExpedition,
                onStopExpedition = onStopTimedExpedition,
                onClaimExpedition = onClaimExpedition,
                onOpenAwardedChest = onOpenChest,
                onDismissReport = onDismissExpeditionReport
            )
        }

        // Encounter Alert CTA Banner (disabled while hero is away on an expedition)
        if (!expeditionLocked && (encounterReady || battleActive)) {
            item {
                SolstrideCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    strongBorder = true
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = "Encounter alert ready",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (battleActive) "Combat In Progress" else "Encounter Alert Triggered!",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = when {
                                    expeditionLocked -> "Locked while hero is away on expedition. Stop early or wait to engage."
                                    battleActive -> "Return to the 2D arena to command your hero."
                                    else -> "Your steps awakened a guardian in ${realm.name} (Stage ${profile.realmStage}/5)."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onEngageBattle(false) },
                        enabled = !expeditionLocked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("engage_encounter_alert_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Filled.Shield, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                expeditionLocked -> "Locked on Expedition"
                                battleActive -> "Resume Tactical Battle"
                                else -> "Engage Encounter (0 Energy)"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        // Core Stride Gauge & Live Energy HUD
        item {
            SolstrideCard(
                modifier = Modifier.fillMaxWidth(),
                strongBorder = true
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Expedition Stride HUD",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Realm ${realm.realmNumber}: ${realm.name} • Spire Best F${profile.highestSpireFloor}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    SolCoinBadge(solCoins = profile.solCoins)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    StrideGaugeRing(
                        weeklySteps = profile.weeklySteps,
                        encounterPercent = profile.encounterProgress,
                        strideEnergy = profile.strideEnergy,
                        maxEnergy = stats.computedMaxEnergy.toLong()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                TacticalStatBar(
                    label = "Hero Vitality (HP)",
                    current = profile.currentHp,
                    maximum = stats.computedMaxHp.toLong(),
                    barColor = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(10.dp))
                TacticalStatBar(
                    label = "Solar Stride Energy",
                    current = profile.strideEnergy,
                    maximum = stats.computedMaxEnergy.toLong(),
                    barColor = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(10.dp))
                TacticalStatBar(
                    label = "Level ${profile.level} XP Progress",
                    current = profile.xp,
                    maximum = profile.xpToNextLevel,
                    barColor = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onEngageBattle(false) },
                        enabled = !expeditionLocked,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("scout_battle_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            when {
                                expeditionLocked -> "Encounters Off"
                                encounterReady -> "Battle Now"
                                else -> "Scout (15 EN)"
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = onRestAtCamp,
                        enabled = !expeditionLocked,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("camp_rest_button"),
                        border = BorderStroke(1.5.dp, ext.borderStrong)
                    ) {
                        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Camp (${ExpeditionEngine.CAMP_HEAL_SOL_COIN_COST} Coins)")
                    }
                }
            }
        }

                // 10-Realm World Map & Interactive Realm Selector
                item {
                    WorldMapTenRealmsCard(
                        profile = profile,
                        expeditionLocked = expeditionLocked,
                        battleActive = battleActive,
                        onSelectRealm = onSelectRealm,
                        onChallengeBoss = { onEngageBattle(true) }
                    )
                }

                // Wayfarer Shrines, Step Resource Veins & Obsidian Blood-Oath Gamble Altar
                item {
                    WayfarerEventsCard(
                        profile = profile,
                        hardcoreState = hardcoreState,
                        expeditionLocked = expeditionLocked,
                        onTriggerEvent = onTriggerWayfarerEvent
                    )
                }
            }

            TownHubSubTab.TRACKERS -> {
                // Built-In On-Device Hardware Pedometer Card (Active ONLY when no watch is connected)
                item {
                    OnDevicePedometerCard(
                        telemetry = telemetry,
                        todaySteps = todayPedometerSteps,
                        dailyGoal = dailyStepGoal,
                        pedometerEnabled = pedometerEnabled,
                        isWatchConnected = isWatchConnected,
                        onRequestPermission = onRequestStepPermission,
                        onTogglePedometer = onTogglePedometer,
                        onResetSession = onResetPedometerSession,
                        onCycleDailyGoal = onCycleDailyStepGoal
                    )
                }

                // Smart Watch & Health Connect Auto-Sync Card (5s Cadence)
                item {
            SolstrideCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Watch,
                            contentDescription = "Smart watch Health Connect auto sync",
                            tint = if (isWatchConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isWatchConnected) {
                                "Watch Connected (5s Sync)"
                            } else {
                                "Watch Disconnected (Phone Active)"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isWatchConnected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, ext.borderStrong)
                    ) {
                        Text(
                            text = when {
                                isWatchConnected -> "WATCH LIVE"
                                healthConnectStatus.state == HealthConnectState.PERMISSION_REQUIRED -> "CONNECT"
                                else -> "NO WATCH"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = if (isWatchConnected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when {
                        isWatchConnected ->
                            "Watch Connected (${watchStatus.watchDisplayName} • ${watchStatus.connectionTransport}) • Phone pedometer is OFF so only watch steps count • Auto-syncing every 5s • Lifetime ${String.format(Locale.US, "%,d", profile.totalSteps)} steps."
                        !watchStatus.bluetoothEnabled ->
                            "Bluetooth is OFF — Built-In Phone Pedometer is counting your steps automatically. Turn Bluetooth ON to auto-detect your watch."
                        !watchStatus.bluetoothPermissionGranted ->
                            "Grant Nearby Devices (Bluetooth) permission below so Solstride detects your Galaxy Watch / smartwatch connecting or disconnecting immediately."
                        healthConnectStatus.state == HealthConnectState.CONNECTED ->
                            "No watch connected right now — Built-In Phone Pedometer is counting your steps. Health Connect & Samsung Health are ready and will auto-switch as soon as your watch connects."
                        healthConnectStatus.state == HealthConnectState.PERMISSION_REQUIRED ->
                            "No watch connected — Built-In Phone Pedometer is active. Grant Health Connect Read Steps access if you want Samsung Health / Galaxy Watch steps to sync when your watch connects."
                        healthConnectStatus.state == HealthConnectState.UPDATE_REQUIRED ->
                            "No watch connected — Built-In Phone Pedometer is active. Install or update Android Health Connect from Google Play for watch sync."
                        else ->
                            "No watch connected — Built-In Phone Pedometer is counting your steps (${String.format(Locale.US, "%,d", profile.totalSteps)} lifetime steps)."
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
                            .testTag("enable_bluetooth_watch_detection_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Filled.Watch, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enable Bluetooth Watch Detection")
                    }
                }

                if (healthConnectStatus.state == HealthConnectState.PERMISSION_REQUIRED) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRequestHealthConnectPermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("connect_health_connect_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Filled.Watch, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect Samsung Health & Watch")
                    }
                } else if (healthConnectStatus.state == HealthConnectState.UPDATE_REQUIRED) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onInstallOrOpenHealthConnect,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("install_health_connect_button")
                    ) {
                        Text("Install Health Connect")
                    }
                }

                if (healthConnectStatus.samsungHealthInstalled && healthConnectStatus.state == HealthConnectState.CONNECTED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenSamsungHealth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("expedition_open_samsung_health_button"),
                        border = BorderStroke(1.5.dp, ext.borderStrong)
                    ) {
                        Text(
                            if (healthConnectStatus.samsungHealthLinked) {
                                "Samsung Health Linked • Open App"
                            } else {
                                "Open Samsung Health (Enable Health Connect)"
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (pendingBatches.isEmpty()) {
                            if (isOnline) "Cloud Firestore synced" else "Offline — caching locally"
                        } else {
                            "${pendingBatches.size} batch(es) queued (${pendingBatches.sumOf { it.stepDelta }} steps)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onOpenWatchSheet,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("open_watch_sync_button"),
                        border = BorderStroke(1.5.dp, ext.borderStrong)
                    ) {
                        Text("Watch Sync")
                    }
                }
            }
        }

                // Step-Harvested Crafting Material Satchel & Survival Status Bar
                item {
                    StepHarvestedMaterialSatchelCard(
                        hardcoreState = hardcoreState,
                        sunshards = profile.sunshards,
                        solCoins = profile.solCoins
                    )
                }

                // In-App Live Widget Hub & Pin-to-Home Studio Launcher
                item {
                    WidgetHubQuickCard(
                        snapshot = widgetSnapshot,
                        autoSyncConfig = widgetAutoSyncConfig,
                        currentTimeMs = currentTimeMs,
                        onOpenWidgetStudio = onOpenWidgetStudio,
                        onSyncWidgetsNow = onSyncWidgetsNow
                    )
                }
            }
        }
    }
}

@Composable
private fun StepHarvestedMaterialSatchelCard(
    hardcoreState: HardcoreRpgState,
    sunshards: Long,
    solCoins: Long
) {
    val ext = LocalSolstrideColors.current
    val totalRunes = hardcoreState.unlockedRunesMap.values.sum()
    SolstrideCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Forge Satchel & Ironstride Oaths",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Steps level Hero & Pet • Loot Ore/Herbs/Essence in Battles & Expeditions • Watch Sleep grants up to 50 Sol Coins",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (hardcoreState.hardcoreOathEnabled) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        text = "IRONSTRIDE OATH",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PedometerMetricPill(
                label = "ORE",
                value = "${hardcoreState.sunsteelOre}",
                modifier = Modifier.weight(1f)
            )
            PedometerMetricPill(
                label = "HERBS",
                value = "${hardcoreState.aetherHerbs}",
                modifier = Modifier.weight(1f)
            )
            PedometerMetricPill(
                label = "ESSENCE",
                value = "${hardcoreState.elementalEssence}",
                modifier = Modifier.weight(1f)
            )
            PedometerMetricPill(
                label = "RUNES",
                value = "$totalRunes",
                modifier = Modifier.weight(1f)
            )
        }
        if (hardcoreState.battleWoundStacks > 0 || hardcoreState.shrineBattlesLeft > 0 || hardcoreState.mercenaryBattlesLeft > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (hardcoreState.battleWoundStacks > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text(
                            text = "Battle Wounds x${hardcoreState.battleWoundStacks} (-${hardcoreState.battleWoundStacks * 10}% HP/ATK) • Cure at Town",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (hardcoreState.shrineBattlesLeft > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, ext.borderStrong)
                    ) {
                        Text(
                            text = "${hardcoreState.shrineBlessingLabel} (${hardcoreState.shrineBattlesLeft}B)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (hardcoreState.mercenaryBattlesLeft > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        border = BorderStroke(1.dp, ext.borderStrong)
                    ) {
                        Text(
                            text = "Mercenary Active (${hardcoreState.mercenaryBattlesLeft}B)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorldMapTenRealmsCard(
    profile: HeroProfile,
    expeditionLocked: Boolean,
    battleActive: Boolean,
    onSelectRealm: (Int) -> Unit,
    onChallengeBoss: () -> Unit
) {
    val ext = LocalSolstrideColors.current
    val currentRealmSpec = ProgressionEngine.realmForNumber(profile.currentRealm.toInt())
    val maxUnlocked = (profile.highestRealmCleared + 1L).coerceAtMost(ProgressionEngine.MAX_REALM).toInt()

    SolstrideCard(modifier = Modifier.fillMaxWidth(), strongBorder = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("10-Realm World Atlas", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Active: Realm ${currentRealmSpec.realmNumber} • ${currentRealmSpec.title} (${currentRealmSpec.affinity.symbol} ${currentRealmSpec.affinity.label})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = JetBrainsMonoFontFamily
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(1.dp, ext.borderStrong)
            ) {
                Text(
                    text = "STAGE ${profile.realmStage}/5",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${currentRealmSpec.subtitle} • Boss: ${currentRealmSpec.bossName} (Rec. Lv.${currentRealmSpec.recommendedLevel})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProgressionEngine.realms.forEach { rSpec ->
                val isSelected = rSpec.realmNumber == profile.currentRealm.toInt()
                val isUnlocked = rSpec.realmNumber <= maxUnlocked
                val isCleared = rSpec.realmNumber <= profile.highestRealmCleared.toInt()
                Surface(
                    onClick = { onSelectRealm(rSpec.realmNumber) },
                    enabled = !expeditionLocked && !battleActive,
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        isCleared -> MaterialTheme.colorScheme.secondaryContainer
                        isUnlocked -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else ext.borderSubtle
                    ),
                    modifier = Modifier.testTag("select_realm_${rSpec.realmNumber}")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "R${rSpec.realmNumber} ${rSpec.affinity.symbol}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = rSpec.title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                        Text(
                            text = when {
                                isCleared -> "CLEARED"
                                isUnlocked -> "Lv.${rSpec.recommendedLevel}"
                                else -> "LOCKED"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = if (isCleared) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
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
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = "Boss encounter",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("${currentRealmSpec.bossName} (Realm ${currentRealmSpec.realmNumber} Boss)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Boss Wins: ${profile.trueBossWins} • +${ExpeditionEngine.BOSS_VICTORY_SOL_COINS} Sol Coins, Rune & Boss Chest",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onChallengeBoss,
                enabled = !expeditionLocked,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("challenge_boss_button"),
                border = BorderStroke(1.5.dp, ext.borderStrong)
            ) {
                Text("Challenge")
            }
        }
    }
}

@Composable
private fun WayfarerEventsCard(
    profile: HeroProfile,
    hardcoreState: HardcoreRpgState,
    expeditionLocked: Boolean,
    onTriggerEvent: (WayfarerEventSpec) -> Unit
) {
    val ext = LocalSolstrideColors.current
    SolstrideCard(modifier = Modifier.fillMaxWidth()) {
        Text("Wayfarer Shrines & Blood-Oath Altars", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Spend Stride Energy or Sol Coins on world events for blessings, forge materials & high-roller jackpots",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgressionEngine.wayfarerEvents.forEach { ev ->
                val canAfford = profile.solCoins >= ev.solCoinCost && profile.strideEnergy >= ev.energyCost
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, ext.borderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ev.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = ev.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val costText = buildList {
                                if (ev.solCoinCost > 0L) add("${ev.solCoinCost} Sol Coins")
                                if (ev.energyCost > 0L) add("${ev.energyCost} Stride Energy")
                            }.joinToString(" + ")
                            Text(
                                text = "Cost: $costText",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onTriggerEvent(ev) },
                            enabled = !expeditionLocked && canAfford,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("wayfarer_event_${ev.id}")
                        ) {
                            Text(ev.actionLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnDevicePedometerCard(
    telemetry: StepTrackerTelemetry,
    todaySteps: Long,
    dailyGoal: Long,
    pedometerEnabled: Boolean,
    isWatchConnected: Boolean,
    onRequestPermission: () -> Unit,
    onTogglePedometer: () -> Unit,
    onResetSession: () -> Unit,
    onCycleDailyGoal: () -> Unit
) {
    val ext = LocalSolstrideColors.current
    val safeGoal = dailyGoal.coerceAtLeast(1000L)
    val goalProgress = (todaySteps.toFloat() / safeGoal.toFloat()).coerceIn(0f, 1f)
    val goalPct = (goalProgress * 100f).roundToInt()
    val distanceKm = todaySteps * 0.00076
    val distanceLabel = if (distanceKm >= 1.0) {
        String.format(Locale.US, "%.2f km", distanceKm)
    } else {
        "${(todaySteps * 0.76).roundToInt()} m"
    }
    val estKcal = (todaySteps * 0.04).roundToInt()
    val standbyForWatch = isWatchConnected

    val badgeLabel = when {
        standbyForWatch -> "WATCH ONLY"
        telemetry.status == StepSensorStatus.TRACKING_ACTIVE -> "LIVE"
        telemetry.status == StepSensorStatus.PERMISSION_NEEDED -> "PERMISSION"
        telemetry.status == StepSensorStatus.PAUSED -> "PAUSED"
        else -> "NO SENSOR"
    }

    SolstrideCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pedometer_card"),
        strongBorder = true
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (standbyForWatch) Icons.Filled.Watch else Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = if (standbyForWatch) "Watch active, phone pedometer on standby" else "Built-in phone pedometer",
                    tint = if (standbyForWatch || telemetry.status == StepSensorStatus.TRACKING_ACTIVE) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (standbyForWatch) "Step Counter (Watch Active)" else "Built-In Phone Pedometer",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = when {
                            standbyForWatch -> "Phone steps OFF while watch is connected"
                            telemetry.status == StepSensorStatus.TRACKING_ACTIVE ->
                                "${telemetry.sensorName} • Counts even when closed"
                            else -> telemetry.sensorName
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (standbyForWatch || telemetry.status == StepSensorStatus.TRACKING_ACTIVE) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                border = BorderStroke(1.dp, ext.borderStrong)
            ) {
                Text(
                    text = badgeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "TODAY'S STEPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format(Locale.US, "%,d", todaySteps),
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("pedometer_today_steps_text")
                )
            }

            Surface(
                onClick = onCycleDailyGoal,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, ext.borderStrong),
                modifier = Modifier.testTag("pedometer_goal_chip")
            ) {
                Text(
                    text = "Goal: ${String.format(Locale.US, "%,d", safeGoal)} ($goalPct%)",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { goalProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PedometerMetricPill(
                label = "SESSION",
                value = String.format(Locale.US, "%,d", telemetry.sessionSteps),
                modifier = Modifier.weight(1f)
            )
            PedometerMetricPill(
                label = "DISTANCE",
                value = distanceLabel,
                modifier = Modifier.weight(1f)
            )
            PedometerMetricPill(
                label = "PACE / KCAL",
                value = if (telemetry.cadenceSpm > 0) "${telemetry.cadenceSpm} spm" else "$estKcal kcal",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (standbyForWatch) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, ext.borderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pedometer_watch_standby_notice")
            ) {
                Text(
                    text = "Watch Connected — phone pedometer is automatically off so only your watch steps count. If your watch disconnects, the phone pedometer activates automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        } else if (telemetry.status == StepSensorStatus.PERMISSION_NEEDED) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("enable_pedometer_permission_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enable Phone Pedometer")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onTogglePedometer,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("toggle_pedometer_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pedometerEnabled) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        contentColor = if (pedometerEnabled) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        }
                    )
                ) {
                    Icon(
                        imageVector = if (pedometerEnabled) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (pedometerEnabled) "Pause" else "Resume")
                }

                OutlinedButton(
                    onClick = onResetSession,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("reset_pedometer_session_button"),
                    border = BorderStroke(1.5.dp, ext.borderStrong)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Session")
                }
            }
        }
    }
}

@Composable
private fun PedometerMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val ext = LocalSolstrideColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, ext.borderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
