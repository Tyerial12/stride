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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Widgets
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.signOutFromSolstride
import com.example.data.model.TownNpcRole
import com.example.data.model.UiState
import com.example.data.model.computeDerivedStats
import com.example.hardware.VoiceCommandParser
import com.example.ui.components.CharacterCreatorStudio
import com.example.ui.components.FreshJourneyMigrationDialog
import com.example.ui.components.InspectTownPlayerDialog
import com.example.ui.components.InteractiveLootChestDialog
import com.example.ui.components.PetEvolutionCeremonyDialog
import com.example.ui.components.SolstrideCard
import com.example.ui.components.TownNpcModalSheet
import com.example.ui.components.WatchSyncBottomSheet
import com.example.ui.components.WidgetStudioDialog
import com.example.ui.screens.BattleScreen
import com.example.ui.screens.ExpeditionScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.MultiplayerScreen
import com.example.ui.screens.PetsScreen
import com.example.ui.theme.LocalSolstrideColors
import com.example.ui.theme.SolstrideDisplayMode
import com.example.widget.SolstrideWidgetType
import com.example.widget.SolstrideWidgetUpdater
import com.example.widget.WidgetPinResult
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class NavItemSpec(
    val tab: SolstrideTab,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolstrideAppShell(
    currentUserId: String,
    viewModel: SolstrideViewModel,
    pendingWidgetTab: String? = null,
    onWidgetTabConsumed: () -> Unit = {}
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
    val hasWatchSleepDetection by viewModel.hasWatchSleepDetection.collectAsStateWithLifecycle()
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
    val hardcoreState by viewModel.hardcoreState.collectAsStateWithLifecycle()
    val widgetStudioOpen by viewModel.widgetStudioOpen.collectAsStateWithLifecycle()
    val widgetSnapshot by viewModel.widgetSnapshot.collectAsStateWithLifecycle()
    val widgetAutoSyncConfig by viewModel.widgetAutoSyncConfig.collectAsStateWithLifecycle()
    val activeWidgetCount by viewModel.activeWidgetCount.collectAsStateWithLifecycle()
    val isHeroSleeping by viewModel.isHeroSleeping.collectAsStateWithLifecycle()
    val watchSleepState by viewModel.watchSleepState.collectAsStateWithLifecycle()
    val worldFogState by viewModel.worldFogState.collectAsStateWithLifecycle()
    val townPlayers by viewModel.townPlayers.collectAsStateWithLifecycle()
    val localTownPos by viewModel.localTownPos.collectAsStateWithLifecycle()
    val localTownFacingRight by viewModel.localTownFacingRight.collectAsStateWithLifecycle()
    val localTownEmote by viewModel.localTownEmote.collectAsStateWithLifecycle()
    val showCharacterStudio by viewModel.showCharacterStudio.collectAsStateWithLifecycle()
    val selectedTownNpc by viewModel.selectedTownNpc.collectAsStateWithLifecycle()
    val inspectedTownPlayer by viewModel.inspectedTownPlayer.collectAsStateWithLifecycle()
    val multiplayerSubTab by viewModel.multiplayerSubTab.collectAsStateWithLifecycle()
    val multiplayerChatMessages by viewModel.multiplayerChatMessages.collectAsStateWithLifecycle()
    val multiplayerDuels by viewModel.multiplayerDuels.collectAsStateWithLifecycle()
    val multiplayerPetPlaydates by viewModel.multiplayerPetPlaydates.collectAsStateWithLifecycle()
    val freshJourneyBanner by viewModel.freshJourneyBanner.collectAsStateWithLifecycle()
    val parryRingProgress by viewModel.parryRingProgress.collectAsStateWithLifecycle()
    val recentEvolutionCeremonyPet by viewModel.recentEvolutionCeremonyPet.collectAsStateWithLifecycle()
    val activeWorldEvent by viewModel.activeWorldEvent.collectAsStateWithLifecycle()
    val wildsNodes by viewModel.wildsNodes.collectAsStateWithLifecycle()
    val weeklyWorldBoss by viewModel.weeklyWorldBoss.collectAsStateWithLifecycle()
    val worldBossContributions by viewModel.worldBossContributions.collectAsStateWithLifecycle()
    val coopRaids by viewModel.coopRaids.collectAsStateWithLifecycle()
    val guilds by viewModel.guilds.collectAsStateWithLifecycle()
    val myGuild by viewModel.myGuild.collectAsStateWithLifecycle()
    val guildChatMessages by viewModel.guildChatMessages.collectAsStateWithLifecycle()

    LaunchedEffect(pendingWidgetTab) {
        if (!pendingWidgetTab.isNullOrBlank()) {
            viewModel.selectTabByRoute(pendingWidgetTab)
            onWidgetTabConsumed()
        }
    }

    val chests = (chestsState as? UiState.Success)?.data.orEmpty()
    val claimableQuestCount = remember(questStatuses) { questStatuses.count { it.canClaim } }
    val heroBadgeCount = chests.size + claimableQuestCount
    val multiplayerBadgeCount = remember(multiplayerDuels, multiplayerPetPlaydates, currentUserId) {
        val duelAlerts = multiplayerDuels.count {
            (it.status == "PENDING" && it.opponentUid == currentUserId) ||
                (it.status == "ACTIVE" && it.turnUid == currentUserId)
        }
        val playAlerts = multiplayerPetPlaydates.count {
            it.status == "PENDING" && it.guestUid == currentUserId
        }
        duelAlerts + playAlerts
    }

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
            NavItemSpec(SolstrideTab.EXPEDITION, "Town", Icons.Filled.Storefront),
            NavItemSpec(SolstrideTab.BATTLE, "Combat", Icons.Filled.Shield),
            NavItemSpec(SolstrideTab.INVENTORY, "Hero", Icons.Filled.Backpack),
            NavItemSpec(SolstrideTab.PETS, "Pets", Icons.Filled.Pets),
            NavItemSpec(SolstrideTab.MULTIPLAYER, "Social", Icons.Filled.Groups)
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
                                text = when (activeTab) {
                                    SolstrideTab.EXPEDITION -> "Sunhaven Town, Shops & Trackers"
                                    SolstrideTab.BATTLE -> "Tactical Combat Arena"
                                    SolstrideTab.INVENTORY -> "Hero Gear, Craft, Build & Quests"
                                    SolstrideTab.PETS -> "Companion Roster & Management"
                                    SolstrideTab.MULTIPLAYER, SolstrideTab.LEADERBOARD -> "Live Players, Chat, Duels & Ranks"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                            onClick = { viewModel.openWidgetStudio(true) },
                            modifier = Modifier.testTag("top_widget_studio_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Widgets,
                                contentDescription = "Open Home Screen Widget Command Studio",
                                tint = MaterialTheme.colorScheme.primary
                            )
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
                                scope.launch {
                                    withContext(NonCancellable) {
                                        viewModel.cleanupBeforeSignOut()
                                    }
                                    signOutFromSolstride(
                                        credentialManager = credentialManager,
                                        scope = scope
                                    )
                                }
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
                            val badgeCount = when (nav.tab) {
                                SolstrideTab.INVENTORY -> heroBadgeCount
                                SolstrideTab.MULTIPLAYER -> multiplayerBadgeCount
                                else -> 0
                            }
                            NavigationBarItem(
                                selected = activeTab == nav.tab,
                                onClick = { viewModel.selectTab(nav.tab) },
                                icon = {
                                    if (badgeCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge { Text("$badgeCount") }
                                            }
                                        ) {
                                            Icon(imageVector = nav.icon, contentDescription = nav.label)
                                        }
                                    } else {
                                        Icon(imageVector = nav.icon, contentDescription = nav.label)
                                    }
                                },
                                label = {
                                    Text(
                                        text = nav.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("nav_${nav.tab.route}")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (activeTab != SolstrideTab.MULTIPLAYER) {
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
                            val badgeCount = when (nav.tab) {
                                SolstrideTab.INVENTORY -> heroBadgeCount
                                SolstrideTab.MULTIPLAYER -> multiplayerBadgeCount
                                else -> 0
                            }
                            NavigationRailItem(
                                selected = activeTab == nav.tab,
                                onClick = { viewModel.selectTab(nav.tab) },
                                icon = {
                                    if (badgeCount > 0) {
                                        BadgedBox(badge = { Badge { Text("$badgeCount") } }) {
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
                            val stats = computeDerivedStats(profile, inventory, equippedPet, hardcoreState)
                            val expeditionLocked = profile.hasActiveExpedition()
                            val expeditionRemainingMs = (profile.activeExpeditionEndMs - currentTimeMs).coerceAtLeast(0L)

                            when (activeTab) {
                                SolstrideTab.EXPEDITION -> ExpeditionScreen(
                                    profile = profile,
                                    stats = stats,
                                    inventory = inventory,
                                    chests = chests,
                                    equippedPet = equippedPet,
                                    hardcoreState = hardcoreState,
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
                                    onSelectRealm = { viewModel.selectRealm(it) },
                                    onTriggerWayfarerEvent = { viewModel.triggerWayfarerEvent(it) },
                                    widgetSnapshot = widgetSnapshot,
                                    widgetAutoSyncConfig = widgetAutoSyncConfig,
                                    onOpenWidgetStudio = { viewModel.openWidgetStudio(true) },
                                    onSyncWidgetsNow = { viewModel.syncWidgetsNow() },
                                    isHeroSleeping = isHeroSleeping,
                                    sleepSourceLabel = watchSleepState.sleepSourceLabel,
                                    activeSleepMinutes = watchSleepState.activeSleepMinutes,
                                    onWakeHero = { viewModel.wakeHeroFromSleep() },
                                    worldFog = worldFogState,
                                    townPlayers = townPlayers,
                                    localTownPos = localTownPos,
                                    localTownFacingRight = localTownFacingRight,
                                    localTownEmote = localTownEmote,
                                    unclaimedQuestCount = claimableQuestCount,
                                    onWalkToTown = { x, y -> viewModel.walkToTownPosition(x, y) },
                                    onSendTownEmote = { emote -> viewModel.sendTownEmote(emote) },
                                    onOpenTownNpc = { npc -> viewModel.openTownNpc(npc) },
                                    onInspectTownPlayer = { p -> viewModel.inspectTownPlayer(p) },
                                    onOpenCharacterMirror = { viewModel.openCharacterMirror() },
                                    activeWorldEvent = activeWorldEvent,
                                    wildsNodes = wildsNodes,
                                    onInteractWildsNode = { node -> viewModel.interactWildsNode(node) },
                                    onToggleRelaxedTiming = { enabled -> viewModel.setRelaxedTimingAssist(enabled) },
                                    onAttuneSleepSanctuary = { hours -> viewModel.attuneSleepSanctuaryRest(hours) },
                                    onSelectStepDungeon = { id -> viewModel.selectStepDungeon(id) },
                                    onSprintDungeonCorridor = { viewModel.sprintDungeonCorridorWithEnergy() },
                                    onResolveDungeonChamber = { viewModel.advanceOrResolveDungeonChamber() },
                                    onClaimSlayerContract = { id -> viewModel.claimSlayerContract(id) },
                                    hasWatchSleepDetection = hasWatchSleepDetection,
                                    modifier = contentModifier
                                )
                                SolstrideTab.BATTLE -> BattleScreen(
                                    profile = profile,
                                    stats = stats,
                                    inventory = inventory,
                                    equippedPet = equippedPet,
                                    hardcoreState = hardcoreState,
                                    battleMode = battleMode,
                                    battle = battleState,
                                    expeditionLocked = expeditionLocked,
                                    expeditionRemainingMs = expeditionRemainingMs,
                                    onSelectBattleMode = { mode -> viewModel.selectBattleMode(mode) },
                                    onJumpToExpedition = { viewModel.selectTab(SolstrideTab.EXPEDITION) },
                                    onEngageBattle = { boss -> viewModel.engageEncounter(boss) },
                                    onStrike = { viewModel.performStrike() },
                                    onSolarSkill = { viewModel.performSolarSkill() },
                                    onClassActiveSkill = { idx -> viewModel.performClassActiveSkill(idx) },
                                    onGuard = { viewModel.performGuard() },
                                    onUltimateCombo = { viewModel.performUltimateAndPetCombo() },
                                    onUseElixir = { viewModel.performBattleElixir() },
                                    onRetreat = { viewModel.performRetreat() },
                                    onToggleHardcoreOath = { viewModel.toggleHardcoreOath() },
                                    onSelectSolCoinWager = { viewModel.selectSolCoinWager(it) },
                                    onOpenDroppedChest = { chest -> viewModel.promptOpenChest(chest) },
                                    onDismissOutcome = { viewModel.dismissBattleOutcome() },
                                    isHeroSleeping = isHeroSleeping,
                                    sleepSourceLabel = watchSleepState.sleepSourceLabel,
                                    activeSleepMinutes = watchSleepState.activeSleepMinutes,
                                    onWakeHero = { viewModel.wakeHeroFromSleep() },
                                    worldFog = worldFogState,
                                    parryRingProgress = parryRingProgress,
                                    onTriggerParryDodge = { viewModel.triggerParryDodge() },
                                    onPetEvolutionBurst = { viewModel.performPetEvolutionBurst() },
                                    weeklyWorldBoss = weeklyWorldBoss,
                                    worldBossContributions = worldBossContributions,
                                    onStrikeWorldBoss = { viewModel.strikeWeeklyWorldBoss() },
                                    onClaimWorldBossMilestone = { tier -> viewModel.claimWorldBossMilestone(tier) },
                                    onSelectStepDungeon = { id -> viewModel.selectStepDungeon(id) },
                                    onSprintDungeonCorridor = { viewModel.sprintDungeonCorridorWithEnergy() },
                                    onResolveDungeonChamber = { viewModel.advanceOrResolveDungeonChamber() },
                                    onClaimSlayerContract = { id -> viewModel.claimSlayerContract(id) },
                                    hasWatchSleepDetection = hasWatchSleepDetection,
                                    modifier = contentModifier
                                )
                                SolstrideTab.PETS -> PetsScreen(
                                    profile = profile,
                                    ownedPets = ownedPets,
                                    equippedPet = equippedPet,
                                    expeditionLocked = expeditionLocked,
                                    expeditionRemainingMs = expeditionRemainingMs,
                                    onJumpToExpedition = { viewModel.selectTab(SolstrideTab.EXPEDITION) },
                                    onEquipPet = { viewModel.equipPet(it) },
                                    onVisitTownPetStore = { viewModel.visitTownNpc(TownNpcRole.PET_STORE) },
                                    onPetCareAction = { pet, action -> viewModel.performPetCareAction(pet, action) },
                                    onEvolvePet = { pet, branch -> viewModel.evolvePet(pet, branch) },
                                    widgetSnapshot = widgetSnapshot,
                                    onPinPetWidget = {
                                        val result = SolstrideWidgetUpdater.requestPinWidget(
                                            context,
                                            SolstrideWidgetType.COMPANION_PET
                                        )
                                        if (result == WidgetPinResult.PIN_REQUESTED) {
                                            viewModel.showBannerMessage("Pin prompt sent for Pet Arena Widget! Close Solstride to Feed & Fight Wild Pets.")
                                        } else {
                                            viewModel.showBannerMessage("Long-press your Home Screen → Widgets → Solstride → Pet Arena Widget.")
                                        }
                                    },
                                    modifier = contentModifier
                                )
                                SolstrideTab.INVENTORY -> InventoryScreen(
                                    profile = profile,
                                    stats = stats,
                                    inventory = inventory,
                                    chests = chests,
                                    equippedPet = equippedPet,
                                    hardcoreState = hardcoreState,
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
                                    onSelectSpecialization = { viewModel.selectClassSpecialization(it) },
                                    onSelectHeroTitle = { viewModel.selectHeroTitle(it) },
                                    onEquipOrUse = { viewModel.toggleEquipItem(it) },
                                    onUpgradeItem = { item, useStab -> viewModel.upgradeItem(item, useStab) },
                                    onRepairItem = { viewModel.repairItem(it) },
                                    onRepairAllEquipped = { viewModel.repairAllEquippedGear() },
                                    onRerollItemPerk = { viewModel.rerollItemPerk(it) },
                                    onAscendItemRarity = { viewModel.ascendItemRarity(it) },
                                    onSocketRune = { item, aff -> viewModel.socketElementalRune(item, aff) },
                                    onForgeCustomEquipment = { slot, tier, elem, useIngot ->
                                        viewModel.forgeCustomEquipment(slot, tier, elem, useIngot)
                                    },
                                    onSalvageItem = { viewModel.salvageItem(it) },
                                    onBrewAlchemistRecipe = { viewModel.brewAlchemistRecipe(it) },
                                    onVisitTownMarket = { viewModel.visitTownNpc(TownNpcRole.MATERIALS_MERCHANT) },
                                    onVisitTownSkillTrainer = { viewModel.visitTownNpc(TownNpcRole.SKILL_MENTOR) },
                                    onResetAllProgress = { viewModel.resetAllProgress() },
                                    onOpenCharacterMirror = { viewModel.openCharacterMirror() },
                                    onCompleteAnvilForge = { item, grade -> viewModel.completeAnvilForgeUpgrade(item, grade) },
                                    onCompleteAlchemistBrew = { recipe, grade -> viewModel.completeAlchemistBrew(recipe, grade) },
                                    onClaimSeasonTier = { tier -> viewModel.claimSeasonTier(tier) },
                                    onAllocateExtendedAttribute = { isAgility -> viewModel.allocateExtendedAttribute(isAgility) },
                                    onUpgradeConstellationNode = { nodeId -> viewModel.upgradeConstellationNode(nodeId) },
                                    onReforgeItemAffixes = { item -> viewModel.reforgeItemAffixes(item) },
                                    onClaimSlayerContract = { id -> viewModel.claimSlayerContract(id) },
                                    worldFog = worldFogState,
                                    isHeroSleeping = isHeroSleeping,
                                    sleepSourceLabel = watchSleepState.sleepSourceLabel,
                                    modifier = contentModifier
                                )
                                SolstrideTab.MULTIPLAYER, SolstrideTab.LEADERBOARD -> MultiplayerScreen(
                                    currentUserId = currentUserId,
                                    profile = profile,
                                    equippedPet = equippedPet,
                                    selectedSubTab = multiplayerSubTab,
                                    townPlayers = townPlayers,
                                    chatMessages = multiplayerChatMessages,
                                    duels = multiplayerDuels,
                                    petPlaydates = multiplayerPetPlaydates,
                                    onSelectSubTab = { viewModel.selectMultiplayerSubTab(it) },
                                    onSendChat = { viewModel.sendMultiplayerChat(it) },
                                    onDeleteChat = { viewModel.deleteMultiplayerChat(it) },
                                    onChallengeDuel = { viewModel.challengePlayerToDuel(it) },
                                    onAcceptDuel = { viewModel.acceptDuel(it) },
                                    onDeclineOrCancelDuel = { viewModel.declineOrCancelDuel(it) },
                                    onPerformDuelAction = { duel, act -> viewModel.performDuelAction(duel, act) },
                                    onForfeitDuel = { viewModel.forfeitDuel(it) },
                                    onDismissDuel = { viewModel.dismissDuel(it) },
                                    onInvitePetPlaydate = { viewModel.invitePetPlaydate(it) },
                                    onAcceptPetPlaydate = { viewModel.acceptPetPlaydate(it) },
                                    onDeclineOrCancelPetPlaydate = { viewModel.declineOrCancelPetPlaydate(it) },
                                    onTriggerPetPlayActivity = { pd, act -> viewModel.triggerPetPlayActivity(pd, act) },
                                    onFinishPetPlaydate = { viewModel.finishPetPlaydate(it) },
                                    onDismissPetPlaydate = { viewModel.dismissPetPlaydate(it) },
                                    leaderboardState = leaderboardState,
                                    coopRaids = coopRaids,
                                    guilds = guilds,
                                    myGuild = myGuild,
                                    guildChatMessages = guildChatMessages,
                                    onCreateRaid = { viewModel.createCoopRaid() },
                                    onJoinRaid = { raidId -> viewModel.joinCoopRaid(raidId) },
                                    onStrikeRaid = { raidId, surge -> viewModel.strikeCoopRaid(raidId, surge) },
                                    onClaimRaidReward = { raidId -> viewModel.claimCoopRaidReward(raidId) },
                                    onCloseRaid = { raidId -> viewModel.closeCoopRaid(raidId) },
                                    onCreateGuild = { name, motto -> viewModel.createGuild(name, motto) },
                                    onJoinGuild = { guildId -> viewModel.joinGuild(guildId) },
                                    onLeaveGuild = { viewModel.leaveGuild() },
                                    onSendGuildMessage = { msg -> viewModel.sendGuildChatMessage(msg) },
                                    onVisitTownMarketForCrest = { viewModel.visitTownNpc(TownNpcRole.MATERIALS_MERCHANT) },
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

    if (widgetStudioOpen) {
        WidgetStudioDialog(
            snapshot = widgetSnapshot,
            autoSyncConfig = widgetAutoSyncConfig,
            activeWidgetCount = activeWidgetCount,
            currentTimeMs = currentTimeMs,
            onDismiss = { viewModel.openWidgetStudio(false) },
            onPinWidget = { widgetType ->
                val result = SolstrideWidgetUpdater.requestPinWidget(context, widgetType)
                if (result == WidgetPinResult.PIN_REQUESTED) {
                    viewModel.showBannerMessage("Pin prompt sent for ${widgetType.title}! Confirm on your launcher.")
                } else {
                    viewModel.showBannerMessage("Long-press your Home Screen → Widgets → Solstride to add ${widgetType.title}.")
                }
            },
            onSyncAllWidgets = { viewModel.syncWidgetsNow() },
            onToggleAutoSync = { enabled -> viewModel.setWidgetAutoSyncEnabled(enabled) },
            onSelectAutoSyncInterval = { mins -> viewModel.setWidgetAutoSyncInterval(mins) },
            onNavigateTab = { tab -> viewModel.selectTab(tab) }
        )
    }

    // Full-Screen Character Creator (First Launch / New Game) & Sanctuary Mirror (Anytime Customization)
    if (currentProfileForModal != null && (!currentProfileForModal.characterCreated || showCharacterStudio)) {
        val isInitial = !currentProfileForModal.characterCreated
        CharacterCreatorStudio(
            profile = currentProfileForModal,
            isInitialCreation = isInitial,
            onConfirm = { name, heroClass, avatar ->
                viewModel.completeCharacterCreation(
                    rawName = name,
                    heroClass = heroClass,
                    avatarConfig = avatar,
                    isInitialCreation = isInitial
                )
            },
            onDismiss = { viewModel.dismissCharacterStudio() }
        )
    }

    // Sunhaven Plaza Interactive Town NPC Modal Sheet (Quests, Pet Store, Material Vendor, Skill Trainer, Blacksmith)
    val activeTownNpc = selectedTownNpc
    if (activeTownNpc != null && currentProfileForModal != null) {
        val currentOwnedPets = (petsState as? UiState.Success)?.data.orEmpty()
        TownNpcModalSheet(
            npcRole = activeTownNpc,
            profile = currentProfileForModal,
            hardcoreState = hardcoreState,
            quests = questStatuses,
            ownedPets = currentOwnedPets,
            equippedPet = equippedPet,
            onDismiss = { viewModel.openTownNpc(null) },
            onClaimQuest = { quest -> viewModel.claimQuestReward(quest) },
            onBuyMaterialOffer = { offer -> viewModel.buyTownMaterialOffer(offer) },
            onUpgradeClassSkill = { skillIdx -> viewModel.upgradeSkillNode(skillIdx) },
            onOpenForgeTab = { viewModel.selectTab(SolstrideTab.INVENTORY) },
            onAdoptPet = { petType -> viewModel.adoptPet(petType) },
            onEquipPet = { petType -> viewModel.equipPet(petType) },
            onBuyPetFood = { food -> viewModel.buyPetFood(food) },
            onBuyPetGear = { gear -> viewModel.buyPetGear(gear) },
            onOpenPetsTab = { viewModel.selectTab(SolstrideTab.PETS) },
            onBuyBlackMarketOffer = { offer -> viewModel.buyBlackMarketOffer(offer) },
            onHireMercenaryContract = { contract -> viewModel.hireMercenaryContract(contract) },
            onUpgradeBuilding = { bldg -> viewModel.upgradeTownBuilding(bldg) },
            onBuyPetCatalyst = { cat -> viewModel.buyPetEvolutionCatalyst(cat) },
            onBrewAlchemistInteractive = { recipe, grade -> viewModel.completeAlchemistBrew(recipe, grade) }
        )
    }

    // Live Multiplayer Adventurer Inspection Dialog in Sunhaven Plaza
    val inspectedPlayer = inspectedTownPlayer
    if (inspectedPlayer != null) {
        InspectTownPlayerDialog(
            player = inspectedPlayer,
            hasLocalPet = equippedPet != null,
            onChallengeDuel = { target -> viewModel.challengePlayerToDuel(target) },
            onInvitePetPlaydate = { target -> viewModel.invitePetPlaydate(target) },
            onDismiss = { viewModel.inspectTownPlayer(null) }
        )
    }

    // One-Time Fresh Journey v2 Step-Preserving Reset Celebration Dialog
    val migrationBanner = freshJourneyBanner
    if (migrationBanner != null) {
        FreshJourneyMigrationDialog(
            banner = migrationBanner,
            onDismiss = { viewModel.dismissFreshJourneyBanner() }
        )
    }

    // Pet Evolution Awakening Ceremony Celebration Dialog
    val evolvedCeremonyPet = recentEvolutionCeremonyPet
    if (evolvedCeremonyPet != null) {
        PetEvolutionCeremonyDialog(
            evolvedPet = evolvedCeremonyPet,
            onDismiss = { viewModel.dismissEvolutionCeremony() }
        )
    }
}
