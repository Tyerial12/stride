package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.signOutFromSolstride
import com.example.data.model.UiState
import com.example.data.model.computeDerivedStats
import com.example.hardware.VoiceCommandParser
import com.example.ui.components.InteractiveLootChestDialog
import com.example.ui.components.SolstrideCard
import com.example.ui.components.WatchSyncBottomSheet
import com.example.ui.screens.BattleScreen
import com.example.ui.screens.ExpeditionScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.PetsScreen
import com.example.ui.theme.LocalSolstrideColors
import com.example.ui.theme.SolstrideDisplayMode
import kotlinx.coroutines.launch

private data class NavItemSpec(
    val tab: SolstrideTab,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolstrideAppShell(
    currentUserId: String,
    viewModel: SolstrideViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val battleMode by viewModel.battleMode.collectAsStateWithLifecycle()
    val profileState by viewModel.heroProfileState.collectAsStateWithLifecycle()
    val inventoryState by viewModel.inventoryState.collectAsStateWithLifecycle()
    val chestsState by viewModel.chestsState.collectAsStateWithLifecycle()
    val petsState by viewModel.petsState.collectAsStateWithLifecycle()
    val equippedPet by viewModel.equippedPet.collectAsStateWithLifecycle()
    val leaderboardState by viewModel.leaderboardState.collectAsStateWithLifecycle()
    val battleState by viewModel.battleState.collectAsStateWithLifecycle()
    val questStatuses by viewModel.questStatuses.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val stepTelemetry by viewModel.stepTelemetry.collectAsStateWithLifecycle()
    val todayPedometerSteps by viewModel.todayPedometerSteps.collectAsStateWithLifecycle()
    val watchStatus by viewModel.watchLinkStatus.collectAsStateWithLifecycle()
    val healthConnectStatus by viewModel.healthConnectStatus.collectAsStateWithLifecycle()
    val isWatchConnected by viewModel.isWatchConnected.collectAsStateWithLifecycle()
    val pendingBatches by viewModel.pendingBatches.collectAsStateWithLifecycle()
    val syncHistory by viewModel.syncHistory.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncingNow.collectAsStateWithLifecycle()
    val watchSheetOpen by viewModel.watchSheetOpen.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val currentTimeMs by viewModel.currentTimeMs.collectAsStateWithLifecycle()
    val lastExpeditionReport by viewModel.lastExpeditionReport.collectAsStateWithLifecycle()
    val pendingChestModal by viewModel.pendingChestModal.collectAsStateWithLifecycle()
    val lastOpenedChestResult by viewModel.lastOpenedChestResult.collectAsStateWithLifecycle()
    val isOpeningChest by viewModel.isOpeningChest.collectAsStateWithLifecycle()

    val chests = (chestsState as? UiState.Success)?.data.orEmpty()
    val claimableQuestCount = remember(questStatuses) { questStatuses.count { it.canClaim } }
    val heroBadgeCount = chests.size + claimableQuestCount

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onForegroundResumed()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        viewModel.onBackgroundPaused()
    }

    BackHandler(enabled = activeTab != SolstrideTab.EXPEDITION) {
        viewModel.selectTab(SolstrideTab.EXPEDITION)
    }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearBanner()
    }

    val stepPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onActivityPermissionGranted()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Physical activity permission declined — grant it anytime in the Pedometer card.")
            }
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onBluetoothConnectPermissionResult(granted)
    }

    fun requestBluetoothWatchDetectionPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            viewModel.onBluetoothConnectPermissionResult(true)
        }
    }

    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        viewModel.onHealthConnectPermissionResult(grantedPermissions)
    }

    fun requestHealthConnectAccess() {
        val perms = viewModel.requiredHealthConnectPermissions()
        if (perms.isEmpty()) {
            scope.launch {
                snackbarHostState.showSnackbar("Health Connect requires Android 8.0 or newer.")
            }
            return
        }
        try {
            healthConnectPermissionLauncher.launch(perms)
        } catch (_: Exception) {
            scope.launch {
                snackbarHostState.showSnackbar("Unable to open Health Connect permissions on this device.")
            }
        }
    }

    fun requestHealthConnectBackgroundAccess() {
        val perms = viewModel.backgroundHealthConnectPermission()
        if (perms.isEmpty()) return
        try {
            healthConnectPermissionLauncher.launch(perms)
        } catch (_: Exception) {
            scope.launch {
                snackbarHostState.showSnackbar("Open Health Connect Settings to enable background step access.")
            }
        }
    }

    fun openHealthConnectInstallerOrSettings() {
        val intent = if (healthConnectStatus.state == com.example.wear.HealthConnectState.UPDATE_REQUIRED) {
            viewModel.buildHealthConnectInstallIntent()
        } else {
            viewModel.buildHealthConnectSettingsIntent()
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            scope.launch {
                snackbarHostState.showSnackbar("Open Health Connect in Android Settings to manage watch step access.")
            }
        }
    }

    fun openSamsungHealthApp() {
        try {
            context.startActivity(viewModel.buildOpenSamsungHealthIntent())
        } catch (_: Exception) {
            scope.launch {
                snackbarHostState.showSnackbar("Install Samsung Health from Google Play or Galaxy Store to link Galaxy Watch steps.")
            }
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                .orEmpty()
            val action = VoiceCommandParser.parseTranscript(matches)
            if (action != null) {
                viewModel.dispatchVoiceCommand(action)
                scope.launch {
                    snackbarHostState.showSnackbar("Voice Command: ${action.spokenHint}")
                }
            } else {
                val heard = matches.firstOrNull().orEmpty()
                scope.launch {
                    snackbarHostState.showSnackbar("Heard \"$heard\" — try Strike, Skill, Guard, Heal, Battle, Pets, or Sync.")
                }
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = VoiceCommandParser.buildSpeechPromptIntent()
            try {
                voiceLauncher.launch(intent)
            } catch (_: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar("No on-device speech recognition service available on this emulator.")
                }
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Microphone permission is needed for hands-free voice commands.")
            }
        }
    }

    fun triggerVoiceCommand() {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            try {
                voiceLauncher.launch(VoiceCommandParser.buildSpeechPromptIntent())
            } catch (_: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar("Speech recognition service unavailable on this device.")
                }
            }
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val navItems = remember {
        listOf(
            NavItemSpec(SolstrideTab.EXPEDITION, "Expedition", Icons.AutoMirrored.Filled.DirectionsWalk),
            NavItemSpec(SolstrideTab.BATTLE, "Battle", Icons.Filled.Shield),
            NavItemSpec(SolstrideTab.PETS, "Pets", Icons.Filled.Pets),
            NavItemSpec(SolstrideTab.INVENTORY, "Hero", Icons.Filled.Backpack),
            NavItemSpec(SolstrideTab.LEADERBOARD, "Ranks", Icons.Filled.EmojiEvents)
        )
    }

    val ext = LocalSolstrideColors.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "SOLSTRIDE",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (settings.displayMode == SolstrideDisplayMode.SUNLIT_PARCHMENT) {
                                    "Sunlit High-Contrast"
                                } else {
                                    "Obsidian OLED Saver"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        Surface(
                            onClick = { viewModel.openWatchSheet(true) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (!isOnline || pendingBatches.isNotEmpty()) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = BorderStroke(1.5.dp, ext.borderStrong),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .testTag("top_watch_sync_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when {
                                        !isOnline -> Icons.Filled.CloudOff
                                        isWatchConnected -> Icons.Filled.Watch
                                        else -> Icons.AutoMirrored.Filled.DirectionsWalk
                                    },
                                    contentDescription = "Open Watch, Pedometer and offline sync status",
                                    tint = if (isWatchConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when {
                                        pendingBatches.isNotEmpty() -> "${pendingBatches.size}Q"
                                        isWatchConnected -> "Watch 5s"
                                        !isOnline -> "Offline"
                                        else -> "Phone Live"
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.toggleDisplayMode() },
                            modifier = Modifier.testTag("display_mode_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (settings.displayMode == SolstrideDisplayMode.SUNLIT_PARCHMENT) {
                                    Icons.Filled.DarkMode
                                } else {
                                    Icons.Filled.LightMode
                                },
                                contentDescription = "Switch Sunlit High-Contrast and OLED Battery Saver mode",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = {
                                signOutFromSolstride(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Sign out of Google account",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        navItems.forEach { nav ->
                            NavigationBarItem(
                                selected = activeTab == nav.tab,
                                onClick = { viewModel.selectTab(nav.tab) },
                                icon = {
                                    if (nav.tab == SolstrideTab.INVENTORY && heroBadgeCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge { Text("$heroBadgeCount") }
                                            }
                                        ) {
                                            Icon(imageVector = nav.icon, contentDescription = nav.label)
                                        }
                                    } else {
                                        Icon(imageVector = nav.icon, contentDescription = nav.label)
                                    }
                                },
                                label = { Text(nav.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("nav_${nav.tab.route}")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { triggerVoiceCommand() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("voice_command_fab"),
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Speak hands-free RPG command"
                        )
                    },
                    text = {
                        Text(
                            text = "Voice Cmd",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                )
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        navItems.forEach { nav ->
                            NavigationRailItem(
                                selected = activeTab == nav.tab,
                                onClick = { viewModel.selectTab(nav.tab) },
                                icon = {
                                    if (nav.tab == SolstrideTab.INVENTORY && heroBadgeCount > 0) {
                                        BadgedBox(badge = { Badge { Text("$heroBadgeCount") } }) {
                                            Icon(nav.icon, contentDescription = nav.label)
                                        }
                                    } else {
                                        Icon(nav.icon, contentDescription = nav.label)
                                    }
                                },
                                label = { Text(nav.label) },
                                modifier = Modifier.testTag("rail_${nav.tab.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp)

                    when (val pState = profileState) {
                        is UiState.Loading -> {
                            Column(
                                modifier = contentModifier.padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.size(14.dp))
                                Text(
                                    text = "Syncing Solstride Expedition…",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                        is UiState.Error -> {
                            Column(
                                modifier = contentModifier.padding(20.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                SolstrideCard(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    strongBorder = true
                                ) {
                                    Text(
                                        text = "Cloud Sync Interrupted",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = pState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                        is UiState.Success -> {
                            val profile = pState.data
                            val inventory = (inventoryState as? UiState.Success)?.data.orEmpty()
                            val ownedPets = (petsState as? UiState.Success)?.data.orEmpty()
                            val stats = computeDerivedStats(profile, inventory, equippedPet)
                            val expeditionLocked = profile.hasActiveExpedition()
                            val expeditionRemainingMs = (profile.activeExpeditionEndMs - currentTimeMs).coerceAtLeast(0L)

                            when (activeTab) {
                                SolstrideTab.EXPEDITION -> ExpeditionScreen(
                                    profile = profile,
                                    stats = stats,
                                    inventory = inventory,
                                    chests = chests,
                                    equippedPet = equippedPet,
                                    telemetry = stepTelemetry,
                                    todayPedometerSteps = todayPedometerSteps,
                                    dailyStepGoal = settings.dailyStepGoal,
                                    pedometerEnabled = settings.pedometerEnabled,
                                    isWatchConnected = isWatchConnected,
                                    watchStatus = watchStatus,
                                    healthConnectStatus = healthConnectStatus,
                                    pendingBatches = pendingBatches,
                                    isOnline = isOnline,
                                    battleActive = battleState.active,
                                    currentTimeMs = currentTimeMs,
                                    lastExpeditionReport = lastExpeditionReport,
                                    onRequestStepPermission = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            stepPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                        } else {
                                            viewModel.onActivityPermissionGranted()
                                        }
                                    },
                                    onTogglePedometer = { viewModel.togglePedometerTracking() },
                                    onResetPedometerSession = { viewModel.resetPedometerSession() },
                                    onCycleDailyStepGoal = { viewModel.cycleDailyStepGoal() },
                                    onRequestHealthConnectPermission = { requestHealthConnectAccess() },
                                    onRequestBluetoothWatchPermission = { requestBluetoothWatchDetectionPermission() },
                                    onInstallOrOpenHealthConnect = { openHealthConnectInstallerOrSettings() },
                                    onOpenSamsungHealth = { openSamsungHealthApp() },
                                    onEngageBattle = { boss -> viewModel.engageEncounter(boss) },
                                    onRestAtCamp = { viewModel.restAtSunlitCamp() },
                                    onOpenWatchSheet = { viewModel.openWatchSheet(true) },
                                    onStartTimedExpedition = { mins -> viewModel.startTimedExpedition(mins) },
                                    onStopTimedExpedition = { viewModel.stopActiveExpedition() },
                                    onClaimExpedition = { viewModel.claimCompletedExpedition() },
                                    onOpenChest = { chest -> viewModel.promptOpenChest(chest) },
                                    onDismissExpeditionReport = { viewModel.dismissExpeditionReport() },
                                    modifier = contentModifier
                                )
                                SolstrideTab.BATTLE -> BattleScreen(
                                    profile = profile,
                                    stats = stats,
                                    inventory = inventory,
                                    equippedPet = equippedPet,
                                    battleMode = battleMode,
                                    battle = battleState,
                                    expeditionLocked = expeditionLocked,
                                    expeditionRemainingMs = expeditionRemainingMs,
                                    onSelectBattleMode = { mode -> viewModel.selectBattleMode(mode) },
                                    onJumpToExpedition = { viewModel.selectTab(SolstrideTab.EXPEDITION) },
                                    onEngageBattle = { boss -> viewModel.engageEncounter(boss) },
                                    onStrike = { viewModel.performStrike() },
                                    onSolarSkill = { viewModel.performSolarSkill() },
                                    onGuard = { viewModel.performGuard() },
                                    onUseElixir = { viewModel.performBattleElixir() },
                                    onRetreat = { viewModel.performRetreat() },
                                    onOpenDroppedChest = { chest -> viewModel.promptOpenChest(chest) },
                                    onDismissOutcome = { viewModel.dismissBattleOutcome() },
                                    modifier = contentModifier
                                )
                                SolstrideTab.PETS -> PetsScreen(
                                    profile = profile,
                                    ownedPets = ownedPets,
                                    equippedPet = equippedPet,
                                    expeditionLocked = expeditionLocked,
                                    expeditionRemainingMs = expeditionRemainingMs,
                                    onJumpToExpedition = { viewModel.selectTab(SolstrideTab.EXPEDITION) },
                                    onAdoptPet = { viewModel.adoptPet(it) },
                                    onEquipPet = { viewModel.equipPet(it) },
                                    onBuyPetFood = { viewModel.buyPetFood(it) },
                                    onBuyPetGear = { viewModel.buyPetGear(it) },
                                    modifier = contentModifier
                                )
                                SolstrideTab.INVENTORY -> InventoryScreen(
                                    profile = profile,
                                    stats = stats,
                                    inventory = inventory,
                                    chests = chests,
                                    equippedPet = equippedPet,
                                    questStatuses = questStatuses,
                                    expeditionLocked = expeditionLocked,
                                    expeditionRemainingMs = expeditionRemainingMs,
                                    battleActive = battleState.active,
                                    onJumpToExpedition = { viewModel.selectTab(SolstrideTab.EXPEDITION) },
                                    onAllocateMastery = { viewModel.allocateMasteryPoint(it) },
                                    onUpgradeSkillNode = { viewModel.upgradeSkillNode(it) },
                                    onClaimQuest = { viewModel.claimQuestReward(it) },
                                    onTriggerPrestige = { viewModel.performPrestigeRebirth() },
                                    onOpenChest = { chest -> viewModel.promptOpenChest(chest) },
                                    onSelectClass = { viewModel.selectHeroClass(it) },
                                    onEquipOrUse = { viewModel.toggleEquipItem(it) },
                                    onUpgradeItem = { viewModel.upgradeItem(it) },
                                    onSalvageItem = { viewModel.salvageItem(it) },
                                    modifier = contentModifier
                                )
                                SolstrideTab.LEADERBOARD -> LeaderboardScreen(
                                    currentUserId = currentUserId,
                                    profile = profile,
                                    leaderboardState = leaderboardState,
                                    modifier = contentModifier
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Interactive Tap-to-Open Loot Chest Modal
    val currentProfileForModal = (profileState as? UiState.Success)?.data
    InteractiveLootChestDialog(
        pendingChest = pendingChestModal,
        openedResult = lastOpenedChestResult,
        isOpening = isOpeningChest,
        expeditionLocked = currentProfileForModal?.hasActiveExpedition() == true,
        onTapOpenChest = { chest -> viewModel.openLootChest(chest) },
        onStoreInVault = { viewModel.storeChestInVault() },
        onEquipRevealed = { item -> viewModel.equipRevealedItem(item) },
        onDismiss = { viewModel.dismissChestModal() }
    )

    if (watchSheetOpen) {
        WatchSyncBottomSheet(
            watchStatus = watchStatus,
            healthConnectStatus = healthConnectStatus,
            isOnline = isOnline,
            isSyncing = isSyncing,
            pendingBatches = pendingBatches,
            syncLogs = syncHistory,
            settings = settings,
            onDismiss = { viewModel.openWatchSheet(false) },
            onSyncNow = { viewModel.manualSyncWatchAndCloud() },
            onRequestHealthConnectPermission = { requestHealthConnectAccess() },
            onRequestHealthConnectBackgroundPermission = { requestHealthConnectBackgroundAccess() },
            onRequestBluetoothWatchPermission = { requestBluetoothWatchDetectionPermission() },
            onInstallOrOpenHealthConnect = { openHealthConnectInstallerOrSettings() },
            onOpenSamsungHealth = { openSamsungHealthApp() },
            onToggleHealthConnectAllSources = { viewModel.toggleHealthConnectAllSources() },
            onToggleBatteryBatching = { viewModel.toggleBatteryBatching() },
            onToggleHaptics = { viewModel.toggleHaptics() }
        )
    }
}
