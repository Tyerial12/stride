package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.SolstrideFirestoreRepository
import com.example.data.local.OfflineSyncDao
import com.example.data.local.PendingStepBatchEntity
import com.example.data.local.SolstridePreferences
import com.example.data.local.SolstrideSettings
import com.example.data.local.SyncLogEntity
import com.example.data.model.ClassSpecialization
import com.example.data.model.DerivedHeroStats
import com.example.data.model.DuelActionKind
import com.example.data.model.ElementalAffinity
import com.example.data.model.GearPerkType
import com.example.data.model.HardcoreRpgState
import com.example.data.model.HeroAvatarConfig
import com.example.data.model.HeroClass
import com.example.data.model.HeroProfile
import com.example.data.model.HeroTitleSpec
import com.example.data.model.InventoryItem
import com.example.data.model.ItemRarity
import com.example.data.model.ItemSlot
import com.example.data.model.LeaderboardEntry
import com.example.data.model.LootChest
import com.example.data.model.LootChestTier
import com.example.data.model.MercenaryContractSpec
import com.example.data.model.MultiplayerChatMessage
import com.example.data.model.MultiplayerDuel
import com.example.data.model.MultiplayerPetPlaydate
import com.example.data.model.MultiplayerSubTab
import com.example.data.model.OwnedPet
import com.example.data.model.PetCatalog
import com.example.data.model.PetFoodSpec
import com.example.data.model.PetGearSlot
import com.example.data.model.PetGearSpec
import com.example.data.model.PetPlayActivity
import com.example.data.model.PetType
import com.example.data.model.StepSource
import com.example.data.model.TownMaterialOffer
import com.example.data.model.TownNpcRole
import com.example.data.model.TownPresencePlayer
import com.example.data.model.UiState
import com.example.data.model.WorldFogState
import com.example.data.model.computeDerivedStats
import com.example.data.model.currentCalendarDateKey
import com.example.data.model.currentIsoWeekKey
import com.example.domain.AlchemistRecipeSpec
import com.example.domain.BattleMode
import com.example.domain.BattleState
import com.example.domain.BlackMarketOfferSpec
import com.example.domain.ChestOpenResult
import com.example.domain.ClassSkillCatalog
import com.example.domain.CombatEngine
import com.example.domain.ExpeditionEngine
import com.example.domain.ExpeditionOutcomeReport
import com.example.domain.ForgeBlueprintTier
import com.example.domain.ProgressionEngine
import com.example.domain.QuestStatus
import com.example.domain.WayfarerEventSpec
import com.example.hardware.BackgroundSyncGuard
import com.example.hardware.ConnectivityObserver
import com.example.hardware.GameplayHaptic
import com.example.hardware.HapticFeedbackManager
import com.example.hardware.StepTrackerManager
import com.example.hardware.StepTrackerTelemetry
import com.example.hardware.VoiceCommandAction
import com.example.ui.theme.SolstrideDisplayMode
import com.example.wear.GalaxyWatchBridge
import com.example.wear.HealthConnectState
import com.example.wear.HealthConnectStepReader
import com.example.wear.HealthConnectSyncStatus
import com.example.wear.WatchLinkStatus
import com.example.wear.WatchSleepSnapshot
import com.example.widget.WildPetBattleEngine
import com.example.data.model.ActivityMasteryType
import com.example.data.model.BrewGrade
import com.example.data.model.CURRENT_MIGRATION_VERSION
import com.example.data.model.CoopRaidRoom
import com.example.data.model.ForgeGrade
import com.example.data.model.FreshJourneyResetBanner
import com.example.data.model.GuildChatMessage
import com.example.data.model.LiveWorldEventSpec
import com.example.data.model.ParryGrade
import com.example.data.model.PetEvolutionBranch
import com.example.data.model.PetEvolutionCatalog
import com.example.data.model.PetEvolutionCatalystOffer
import com.example.data.model.RelicSetSpec
import com.example.data.model.SeasonCatalog
import com.example.data.model.SolstrideGuild
import com.example.data.model.TownBuildingCatalog
import com.example.data.model.TownBuildingId
import com.example.data.model.WeeklyWorldBoss
import com.example.data.model.WeeklyWorldBossCatalog
import com.example.data.model.WildsFieldGenerator
import com.example.data.model.WildsFieldNode
import com.example.data.model.WildsNodeType
import com.example.data.model.WorldBossContribution
import com.example.data.model.ConstellationCatalog
import com.example.data.model.DreamRestTier
import com.example.data.model.DungeonChamberType
import com.example.data.model.GearAffixCatalog
import com.example.data.model.SlayerContractCatalog
import com.example.data.model.StepDungeonCatalog
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

enum class SolstrideTab(val route: String) {
    EXPEDITION("expedition"),
    BATTLE("battle"),
    PETS("pets"),
    SLOTS("slots"),
    INVENTORY("inventory"),
    MULTIPLAYER("multiplayer"),
    LEADERBOARD("leaderboard")
}

enum class MasteryAttribute { MIGHT, VIGOR, ENDURANCE }

class SolstrideViewModel(
    private val appContext: Context,
    private val currentUserId: String,
    private val initialDisplayName: String?,
    private val repository: SolstrideFirestoreRepository,
    private val offlineSyncDao: OfflineSyncDao,
    private val preferences: SolstridePreferences,
    private val stepTracker: StepTrackerManager,
    private val haptics: HapticFeedbackManager,
    private val watchBridge: GalaxyWatchBridge,
    private val healthConnectReader: HealthConnectStepReader,
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val syncMutex = Mutex()
    private val chestOpenMutex = Mutex()
    private val petLedgerSyncMutex = Mutex()
    private val _localConsumedChestIds = MutableStateFlow<Set<String>>(emptySet())
    private val recentlyAwardedChestIds = mutableSetOf<String>()
    private val persistedConsumedChestIds: StateFlow<Set<String>> = preferences.consumedChestIdsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    private val allConsumedChestIds: StateFlow<Set<String>> = combine(
        _localConsumedChestIds,
        persistedConsumedChestIds
    ) { local, persisted -> local + persisted }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _isForegroundActive = MutableStateFlow(true)

    private val _watchSleepState = MutableStateFlow(WatchSleepSnapshot())
    val watchSleepState: StateFlow<WatchSleepSnapshot> = _watchSleepState.asStateFlow()

    private val _manuallyWokenSleepMarkerMs = MutableStateFlow(0L)

    val isHeroSleeping: StateFlow<Boolean> = combine(
        _watchSleepState,
        _manuallyWokenSleepMarkerMs,
        watchBridge.linkStatus
    ) { snap, wokenMarker, watchStatus ->
        watchStatus.connected &&
            snap.isSleepingNow &&
            (snap.activeSessionMarkerMs == 0L || snap.activeSessionMarkerMs != wokenMarker)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    private val _activeTab = MutableStateFlow(SolstrideTab.EXPEDITION)
    val activeTab: StateFlow<SolstrideTab> = _activeTab.asStateFlow()

    private val slotSpinMutex = Mutex()
    private val _slotMachineState = MutableStateFlow(com.example.domain.SlotMachineUiState())
    val slotMachineState: StateFlow<com.example.domain.SlotMachineUiState> = _slotMachineState.asStateFlow()

    private val _battleMode = MutableStateFlow(BattleMode.REALM_CAMPAIGN)
    val battleMode: StateFlow<BattleMode> = _battleMode.asStateFlow()

    private val _battleState = MutableStateFlow(BattleState())
    val battleState: StateFlow<BattleState> = _battleState.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _isSyncingNow = MutableStateFlow(false)
    val isSyncingNow: StateFlow<Boolean> = _isSyncingNow.asStateFlow()

    private val _watchSheetOpen = MutableStateFlow(false)
    val watchSheetOpen: StateFlow<Boolean> = _watchSheetOpen.asStateFlow()

    private val _widgetStudioOpen = MutableStateFlow(false)
    val widgetStudioOpen: StateFlow<Boolean> = _widgetStudioOpen.asStateFlow()

    private val _widgetSnapshot = MutableStateFlow(
        com.example.widget.SolstrideWidgetSnapshotStore.read(appContext)
    )
    val widgetSnapshot: StateFlow<com.example.widget.WidgetSnapshotState> = _widgetSnapshot.asStateFlow()

    private val _widgetAutoSyncConfig = MutableStateFlow(
        com.example.widget.SolstrideWidgetSnapshotStore.readAutoSyncConfig(appContext)
    )
    val widgetAutoSyncConfig: StateFlow<com.example.widget.WidgetAutoSyncConfig> = _widgetAutoSyncConfig.asStateFlow()

    private val _activeWidgetCount = MutableStateFlow(
        com.example.widget.SolstrideWidgetUpdater.activeWidgetCount(appContext)
    )
    val activeWidgetCount: StateFlow<Int> = _activeWidgetCount.asStateFlow()

    private val _currentTimeMs = MutableStateFlow(System.currentTimeMillis())
    val currentTimeMs: StateFlow<Long> = _currentTimeMs.asStateFlow()

    private val _lastExpeditionReport = MutableStateFlow<ExpeditionOutcomeReport?>(null)
    val lastExpeditionReport: StateFlow<ExpeditionOutcomeReport?> = _lastExpeditionReport.asStateFlow()

    private val _pendingChestModal = MutableStateFlow<LootChest?>(null)
    val pendingChestModal: StateFlow<LootChest?> = _pendingChestModal.asStateFlow()

    private val _lastOpenedChestResult = MutableStateFlow<ChestOpenResult?>(null)
    val lastOpenedChestResult: StateFlow<ChestOpenResult?> = _lastOpenedChestResult.asStateFlow()

    private val _isOpeningChest = MutableStateFlow(false)
    val isOpeningChest: StateFlow<Boolean> = _isOpeningChest.asStateFlow()

    // --- Character Creator Studio & Sunhaven Plaza Multiplayer Town ---
    private val _showCharacterStudio = MutableStateFlow(false)
    val showCharacterStudio: StateFlow<Boolean> = _showCharacterStudio.asStateFlow()

    private val _selectedTownNpc = MutableStateFlow<TownNpcRole?>(null)
    val selectedTownNpc: StateFlow<TownNpcRole?> = _selectedTownNpc.asStateFlow()

    private val _inspectedTownPlayer = MutableStateFlow<TownPresencePlayer?>(null)
    val inspectedTownPlayer: StateFlow<TownPresencePlayer?> = _inspectedTownPlayer.asStateFlow()

    private val _localTownPos = MutableStateFlow(0.50f to 0.58f)
    val localTownPos: StateFlow<Pair<Float, Float>> = _localTownPos.asStateFlow()

    private val _localTownFacingRight = MutableStateFlow(true)
    val localTownFacingRight: StateFlow<Boolean> = _localTownFacingRight.asStateFlow()

    private val _localTownEmote = MutableStateFlow("")
    val localTownEmote: StateFlow<String> = _localTownEmote.asStateFlow()

    private val _multiplayerSubTab = MutableStateFlow(MultiplayerSubTab.PLAYERS)
    val multiplayerSubTab: StateFlow<MultiplayerSubTab> = _multiplayerSubTab.asStateFlow()

    // --- Interactive Combat, Pet Evolution & One-Time Fresh Journey Reset State ---
    private val _freshJourneyBanner = MutableStateFlow<FreshJourneyResetBanner?>(null)
    val freshJourneyBanner: StateFlow<FreshJourneyResetBanner?> = _freshJourneyBanner.asStateFlow()

    private val _parryRingProgress = MutableStateFlow(0f)
    val parryRingProgress: StateFlow<Float> = _parryRingProgress.asStateFlow()
    private var parryTimerJob: Job? = null

    private val _recentEvolutionCeremonyPet = MutableStateFlow<OwnedPet?>(null)
    val recentEvolutionCeremonyPet: StateFlow<OwnedPet?> = _recentEvolutionCeremonyPet.asStateFlow()

    private val claimedDuelRewardIds = mutableSetOf<String>()

    private var lastTownPresenceSyncMs: Long = 0L

    val settings: StateFlow<SolstrideSettings> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), SolstrideSettings())

    val hardcoreState: StateFlow<HardcoreRpgState> = preferences.hardcoreRpgStateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), HardcoreRpgState())

    val todayPedometerSteps: StateFlow<Long> = preferences.settingsFlow
        .map { pref ->
            if (pref.pedometerDateKey == currentCalendarDateKey()) pref.pedometerDailySteps else 0L
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    val isOnline: StateFlow<Boolean> = connectivityObserver.observeOnlineStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), connectivityObserver.isCurrentlyOnline())

    private val _lastWatchStepReceivedMs = MutableStateFlow(0L)
    private var sawWearOsNode = false

    val stepTelemetry: StateFlow<StepTrackerTelemetry> = stepTracker.telemetry
    val watchLinkStatus: StateFlow<WatchLinkStatus> = watchBridge.linkStatus
    val healthConnectStatus: StateFlow<HealthConnectSyncStatus> = healthConnectReader.status

    /**
     * True ONLY when a paired smartwatch is currently connected via Bluetooth / Wear OS node link.
     * When the watch is not connected, this is immediately false so the phone pedometer activates
     * right away and never falsely shows "Watch Connected".
     */
    val isWatchConnected: StateFlow<Boolean> = watchLinkStatus
        .map { watch -> watch.connected }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    /**
     * True ONLY when a sleep-detecting smartwatch (e.g. Samsung Galaxy Watch, Pixel Watch, Wear OS,
     * Garmin, Fitbit) is currently connected (`watchStatus.connected == true`).
     * - When true (watch connected): Steps -> Battle Energy, Watch Sleep -> Stamina & Dream Rest.
     * - When false (watch disconnected / no sleep watch active): Real-world Steps count directly
     *   for BOTH Battle Energy AND Hero Stamina!
     */
    private fun computeWatchSleepDetectionActive(
        watchStatus: WatchLinkStatus,
        hc: HealthConnectSyncStatus,
        sleepSnap: WatchSleepSnapshot
    ): Boolean {
        if (!watchStatus.connected) return false
        val connectedSleepCapableWatch =
            com.example.wear.WatchDeviceClassifier.isSleepCapableWatch(watchStatus.watchDisplayName) ||
                watchStatus.watchDisplayName.isNotBlank()
        val hcSleepCapableWatch = (hc.state == HealthConnectState.CONNECTED) &&
            (hc.hasWatchDeviceRecordToday ||
                hc.samsungHealthLinked ||
                com.example.wear.WatchDeviceClassifier.isSleepCapableWatch(hc.primaryWatchSource))
        val hasSleepRecord = sleepSnap.hasWatchSleepDetected ||
            sleepSnap.isSleepingNow ||
            sleepSnap.latestCompletedEndMs > 0L ||
            sleepSnap.activeSessionMarkerMs > 0L
        return connectedSleepCapableWatch || hcSleepCapableWatch || hasSleepRecord
    }

    val hasWatchSleepDetection: StateFlow<Boolean> = combine(
        watchLinkStatus,
        healthConnectStatus,
        _watchSleepState
    ) { watchStatus, hc, sleepSnap ->
        computeWatchSleepDetectionActive(watchStatus, hc, sleepSnap)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    fun hasActiveWatchSleepDetection(): Boolean {
        return computeWatchSleepDetectionActive(
            watchStatus = watchLinkStatus.value,
            hc = healthConnectStatus.value,
            sleepSnap = _watchSleepState.value
        )
    }

    val pendingBatches: StateFlow<List<PendingStepBatchEntity>> =
        offlineSyncDao.observePendingBatches(currentUserId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val syncHistory: StateFlow<List<SyncLogEntity>> =
        offlineSyncDao.observeRecentLogs(currentUserId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val heroProfileState: StateFlow<UiState<HeroProfile>> = repository.observeHeroProfile(currentUserId)
        .map<HeroProfile?, UiState<HeroProfile>> { profile ->
            if (profile != null) UiState.Success(profile) else UiState.Loading
        }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Profile observer error", root)
            emit(UiState.Error(root.message ?: "Unable to sync hero profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val inventoryState: StateFlow<UiState<List<InventoryItem>>> = repository.observeInventory(currentUserId)
        .map<List<InventoryItem>, UiState<List<InventoryItem>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Inventory observer error", root)
            emit(UiState.Error(root.message ?: "Unable to load inventory"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val chestsState: StateFlow<UiState<List<LootChest>>> = combine(
        repository.observeChests(currentUserId),
        allConsumedChestIds
    ) { chests, consumed ->
        UiState.Success(
            chests.filter { chest ->
                chest.id.isNotBlank() && chest.id !in consumed
            }
        ) as UiState<List<LootChest>>
    }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Chests observer error", root)
            emit(UiState.Error(root.message ?: "Unable to load treasure vault"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val petsState: StateFlow<UiState<List<OwnedPet>>> = repository.observePets(currentUserId)
        .map<List<OwnedPet>, UiState<List<OwnedPet>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Pets observer error", root)
            emit(UiState.Error(root.message ?: "Unable to load companions"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val leaderboardState: StateFlow<UiState<List<LeaderboardEntry>>> = repository.observeLeaderboard(currentUserId)
        .map<List<LeaderboardEntry>, UiState<List<LeaderboardEntry>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Leaderboard observer error", root)
            emit(UiState.Error(root.message ?: "Unable to load cloud leaderboard"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val equippedPet: StateFlow<OwnedPet?> = combine(heroProfileState, petsState) { pState, petsUi ->
        val profile = (pState as? UiState.Success)?.data ?: return@combine null
        val owned = (petsUi as? UiState.Success)?.data.orEmpty()
        if (profile.equippedPetId.isBlank()) null
        else owned.firstOrNull { it.petType == profile.equippedPetId || it.id == profile.equippedPetId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val derivedStats: StateFlow<DerivedHeroStats?> = combine(
        heroProfileState,
        inventoryState,
        equippedPet,
        hardcoreState
    ) { pState, iState, activePet, hcState ->
        val profile = (pState as? UiState.Success)?.data ?: return@combine null
        val items = (iState as? UiState.Success)?.data ?: emptyList()
        computeDerivedStats(profile, items, activePet, hcState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val townPlayers: StateFlow<List<TownPresencePlayer>> = repository.observeTownPresence(currentUserId)
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            Log.w(TAG, "Town presence observer error", fsError ?: error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val multiplayerChatMessages: StateFlow<List<MultiplayerChatMessage>> = repository.observeMultiplayerChat(currentUserId)
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            Log.w(TAG, "Multiplayer chat observer error", fsError ?: error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val multiplayerDuels: StateFlow<List<MultiplayerDuel>> = repository.observeMultiplayerDuels(currentUserId)
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            Log.w(TAG, "Multiplayer duels observer error", fsError ?: error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val multiplayerPetPlaydates: StateFlow<List<MultiplayerPetPlaydate>> = repository.observePetPlaydates(currentUserId)
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            Log.w(TAG, "Pet playdates observer error", fsError ?: error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val worldFogState: StateFlow<WorldFogState> = combine(
        heroProfileState,
        hardcoreState,
        todayPedometerSteps,
        isHeroSleeping
    ) { pState, hc, todaySteps, sleeping ->
        val profile = (pState as? UiState.Success)?.data ?: HeroProfile()
        ProgressionEngine.computeWorldFogState(
            profile = profile,
            hardcoreState = hc,
            todaySteps = todaySteps,
            isHeroSleeping = sleeping
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), WorldFogState())

    val questStatuses: StateFlow<List<QuestStatus>> = combine(
        heroProfileState,
        todayPedometerSteps,
        hardcoreState
    ) { pState, todaySteps, hc ->
        val profile = (pState as? UiState.Success)?.data ?: return@combine emptyList()
        ProgressionEngine.evaluateQuests(profile, currentCalendarDateKey(), todaySteps, hc)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeWorldEvent: StateFlow<Pair<LiveWorldEventSpec, Int>> = _currentTimeMs
        .map { nowMs -> LiveWorldEventSpec.currentEventWithRemainingMinutes(nowMs) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000L),
            LiveWorldEventSpec.currentEventWithRemainingMinutes()
        )

    val wildsNodes: StateFlow<List<WildsFieldNode>> = combine(
        heroProfileState,
        hardcoreState
    ) { pState, hc ->
        val profile = (pState as? UiState.Success)?.data ?: return@combine emptyList()
        WildsFieldGenerator.generateNodesForRealm(
            realmNumber = profile.currentRealm.toInt().coerceIn(1, ProgressionEngine.MAX_REALM.toInt()),
            dateKey = currentCalendarDateKey(),
            harvestedKeys = hc.wildsHarvestedKeys
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val weeklyWorldBoss: StateFlow<WeeklyWorldBoss?> = repository
        .observeWeeklyWorldBoss(currentUserId, currentIsoWeekKey())
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "World boss observer error", error)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val worldBossContributions: StateFlow<List<WorldBossContribution>> = repository
        .observeWorldBossContributions(currentUserId, currentIsoWeekKey())
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "World boss contributions error", error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val coopRaids: StateFlow<List<CoopRaidRoom>> = repository
        .observeCoopRaids(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Co-op raids observer error", error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val guilds: StateFlow<List<SolstrideGuild>> = repository
        .observeGuilds(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Guilds observer error", error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val myGuild: StateFlow<SolstrideGuild?> = combine(heroProfileState, guilds) { pState, allGuilds ->
        val gid = (pState as? UiState.Success)?.data?.guildId.orEmpty()
        if (gid.isBlank()) null else allGuilds.firstOrNull { it.id == gid }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val guildChatMessages: StateFlow<List<GuildChatMessage>> = heroProfileState
        .map { (it as? UiState.Success)?.data?.guildId.orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { gid ->
            if (gid.isBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeGuildChat(currentUserId, gid).catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Guild chat observer error", error)
                    emit(emptyList())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        // Lock out the interactive Pet Arena Widget as soon as Solstride loads into foreground
        com.example.widget.SolstrideWidgetSnapshotStore.setAppForegroundLocked(appContext, true)

        viewModelScope.launch {
            _manuallyWokenSleepMarkerMs.value = preferences.readManuallyWokenSleepMs()
            repository.ensureInitializedProfile(currentUserId, initialDisplayName)
                .onSuccess {
                    // Enforce one-time idempotent v2 Fresh Journey reset (preserves 100% of steps!)
                    repository.performOneTimeFreshJourneyReset(currentUserId)
                        .onSuccess { resetBanner ->
                            if (resetBanner != null) {
                                _battleState.value = BattleState()
                                _pendingChestModal.value = null
                                _lastOpenedChestResult.value = null
                                _localConsumedChestIds.value = emptySet()
                                recentlyAwardedChestIds.clear()
                                preferences.resetRpgProgressKeepingSteps()
                                _freshJourneyBanner.value = resetBanner
                            }
                        }
                    repository.ensureWeeklyWorldBoss(currentUserId, currentIsoWeekKey())
                }
                .onFailure { err ->
                    _bannerMessage.value = "Offline mode active: ${err.localizedMessage ?: "waiting for network"}"
                }
            val currentWatchStatus = watchBridge.refreshWatchConnection()
            if (!currentWatchStatus.connected) {
                _lastWatchStepReceivedMs.value = 0L
                preferences.setLastWatchStepReceivedMs(0L)
            } else {
                _lastWatchStepReceivedMs.value = preferences.readLastWatchStepReceivedMs()
            }
            healthConnectReader.refreshStatus()
            refreshWatchSleepState()
            reconcilePedometerWithWatchState()
        }

        // Auto-sync any queued Pet Widget training (Feed / Wild Pet Battles) as soon as profile & pets load
        viewModelScope.launch {
            combine(heroProfileState, petsState, _isForegroundActive) { pState, petsUi, fg ->
                fg && pState is UiState.Success && petsUi is UiState.Success
            }.collect { ready ->
                if (ready) {
                    reconcilePendingPetWidgetLedger()
                }
            }
        }

        // 1-second clock ticker for live Timed Expedition countdowns
        viewModelScope.launch {
            while (isActive) {
                _currentTimeMs.value = System.currentTimeMillis()
                delay(1000L)
            }
        }

        // 5-second automatic watch connection check & step sync loop while foregrounded
        viewModelScope.launch {
            while (isActive) {
                delay(WATCH_AUTO_SYNC_INTERVAL_MS)
                if (_isForegroundActive.value) {
                    syncWatchStepsCycle(refreshNode = true, silent = true)
                }
            }
        }

        // Sync settings side-effects (haptics enabled)
        viewModelScope.launch {
            settings.collect { pref ->
                haptics.enabled = pref.hapticsEnabled
            }
        }

        // Automatically pause phone pedometer when watch connects, resume it when watch disconnects,
        // and immediately refresh watch sleep detection state on connect/disconnect transitions.
        viewModelScope.launch {
            isWatchConnected.collect {
                refreshWatchSleepState()
                if (_isForegroundActive.value) {
                    reconcilePedometerWithWatchState()
                }
            }
        }

        // Auto-flush pending Room step batches whenever connectivity returns or new watch batches arrive
        viewModelScope.launch {
            combine(isOnline, pendingBatches, heroProfileState) { online, batches, pState ->
                Triple(online, batches.isNotEmpty(), pState is UiState.Success)
            }.collect { (online, hasPending, hasProfile) ->
                if (online && hasPending && hasProfile) {
                    flushPendingStepBatches(silent = true)
                }
            }
        }

        // Automatically publish live Hero, Pet, Step, Watch, Sleep, Chests & Gear state to all 4 Home Screen Widgets
        viewModelScope.launch {
            val coreFlow = combine(
                heroProfileState,
                derivedStats,
                equippedPet,
                hardcoreState
            ) { pState, stats, pet, hc ->
                val profile = (pState as? UiState.Success)?.data
                if (profile != null && stats != null) {
                    listOf<Any?>(profile, stats, pet, hc)
                } else {
                    null
                }
            }
            val inventoryAndChestsFlow = combine(
                inventoryState,
                chestsState
            ) { invUi, chestUi ->
                val inv = (invUi as? UiState.Success)?.data.orEmpty()
                val chests = (chestUi as? UiState.Success)?.data.orEmpty()
                inv to chests.size
            }
            val sleepAndFgFlow = combine(
                isHeroSleeping,
                _watchSleepState,
                _isForegroundActive,
                inventoryAndChestsFlow
            ) { sleeping, sleepSnap, fg, (inv, chestCount) ->
                listOf<Any?>(sleeping, sleepSnap, fg, inv, chestCount)
            }
            combine(
                coreFlow,
                todayPedometerSteps,
                settings,
                isWatchConnected,
                sleepAndFgFlow
            ) { core, todaySteps, pref, watchConn, extra ->
                if (core == null) return@combine null
                val profile = core[0] as HeroProfile
                val stats = core[1] as DerivedHeroStats
                val pet = core[2] as OwnedPet?
                val hc = core[3] as HardcoreRpgState
                val sleeping = extra[0] as Boolean
                val sleepSnap = extra[1] as com.example.wear.WatchSleepSnapshot
                val fgActive = extra[2] as Boolean
                @Suppress("UNCHECKED_CAST")
                val inv = extra[3] as List<InventoryItem>
                val chestCount = extra[4] as Int
                com.example.widget.SolstrideWidgetSnapshotStore.buildFromLiveState(
                    context = appContext,
                    profile = profile,
                    stats = stats,
                    equippedPet = pet,
                    hardcoreState = hc,
                    todaySteps = todaySteps,
                    dailyStepGoal = pref.dailyStepGoal,
                    isWatchConnected = watchConn,
                    inventory = inv,
                    unopenedChestCount = chestCount,
                    isHeroSleeping = sleeping,
                    sleepSourceLabel = sleepSnap.sleepSourceLabel,
                    activeSleepMinutes = sleepSnap.activeSleepMinutes,
                    lastCompletedSleepMinutes = sleepSnap.latestCompletedDurationMinutes,
                    isAppForegroundLocked = fgActive
                )
            }.collect { snapshot ->
                if (snapshot != null) {
                    _widgetSnapshot.value = snapshot
                    com.example.widget.SolstrideWidgetSnapshotStore.save(appContext, snapshot)
                    com.example.widget.SolstrideWidgetUpdater.updateAllWidgets(appContext)
                    if (snapshot.hasActiveExpedition && snapshot.expeditionEndMs > System.currentTimeMillis()) {
                        com.example.widget.WidgetAutoSyncScheduler.scheduleExpeditionCompletionRefresh(
                            appContext,
                            snapshot.expeditionEndMs
                        )
                    } else {
                        com.example.widget.WidgetAutoSyncScheduler.scheduleExpeditionCompletionRefresh(
                            appContext,
                            0L
                        )
                    }
                }
            }
        }

        // Foreground 60-second expedition countdown auto-refresh for Home Screen Widgets
        viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                if (_isForegroundActive.value && _widgetAutoSyncConfig.value.enabled) {
                    val snap = _widgetSnapshot.value
                    if (snap.hasActiveExpedition || com.example.widget.SolstrideWidgetUpdater.hasAnyActiveWidgets(appContext)) {
                        val refreshed = snap.copy(lastUpdatedMs = System.currentTimeMillis())
                        _widgetSnapshot.value = refreshed
                        com.example.widget.SolstrideWidgetSnapshotStore.save(appContext, refreshed)
                        com.example.widget.SolstrideWidgetUpdater.updateAllWidgets(appContext)
                    }
                }
            }
        }

        // Live Sunhaven Plaza presence heartbeat every 45s while foregrounded
        viewModelScope.launch {
            while (isActive) {
                if (_isForegroundActive.value) {
                    val profile = (heroProfileState.value as? UiState.Success)?.data
                    if (profile != null && profile.characterCreated) {
                        publishTownPresenceNow(force = true)
                    }
                }
                delay(45_000L)
            }
        }
    }

    fun openWidgetStudio(open: Boolean) {
        if (open) {
            _activeWidgetCount.value = com.example.widget.SolstrideWidgetUpdater.activeWidgetCount(appContext)
            _widgetAutoSyncConfig.value = com.example.widget.SolstrideWidgetSnapshotStore.readAutoSyncConfig(appContext)
        }
        _widgetStudioOpen.value = open
    }

    fun setWidgetAutoSyncEnabled(enabled: Boolean) {
        val next = _widgetAutoSyncConfig.value.copy(enabled = enabled)
        _widgetAutoSyncConfig.value = next
        com.example.widget.SolstrideWidgetSnapshotStore.saveAutoSyncConfig(appContext, next)
        com.example.widget.WidgetAutoSyncScheduler.syncSchedule(appContext)
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        _bannerMessage.value = if (enabled) {
            "Widget Auto-Sync enabled (${next.intervalLabel()}) — widgets update automatically in the background!"
        } else {
            "Widget Auto-Sync paused — tap Refresh on any widget or in Studio to sync manually."
        }
    }

    fun setWidgetAutoSyncInterval(intervalMinutes: Long) {
        val clamped = intervalMinutes.coerceIn(15L, 60L)
        val next = _widgetAutoSyncConfig.value.copy(enabled = true, intervalMinutes = clamped)
        _widgetAutoSyncConfig.value = next
        com.example.widget.SolstrideWidgetSnapshotStore.saveAutoSyncConfig(appContext, next)
        com.example.widget.WidgetAutoSyncScheduler.syncSchedule(appContext)
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        _bannerMessage.value = "Widget Auto-Sync set to ${next.intervalLabel()}."
    }

    fun selectTabByRoute(route: String) {
        val matched = SolstrideTab.entries.firstOrNull { it.route.equals(route, ignoreCase = true) }
        if (matched != null) {
            selectTab(matched)
        }
    }

    fun showBannerMessage(message: String) {
        _bannerMessage.value = message
    }

    fun syncWidgetsNow() {
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        manualSyncWatchAndCloud()
        val snap = _widgetSnapshot.value.copy(lastUpdatedMs = System.currentTimeMillis())
        _widgetSnapshot.value = snap
        _activeWidgetCount.value = com.example.widget.SolstrideWidgetUpdater.activeWidgetCount(appContext)
        com.example.widget.SolstrideWidgetSnapshotStore.save(appContext, snap)
        com.example.widget.SolstrideWidgetUpdater.updateAllWidgets(appContext)
        com.example.widget.WidgetAutoSyncScheduler.syncSchedule(appContext)
        _bannerMessage.value = "All 4 Home Screen Widgets synced with live Hero, Pet & Step data!"
    }

    private fun isHeroLocked(profile: HeroProfile): Boolean = profile.hasActiveExpedition()

    private fun requireCharacterAvailable(profile: HeroProfile): Boolean {
        if (isHeroLocked(profile)) {
            val remaining = (profile.activeExpeditionEndMs - System.currentTimeMillis()).coerceAtLeast(0L)
            _bannerMessage.value = if (remaining > 0L) {
                "Hero is away on expedition (${ExpeditionEngine.formatCountdown(remaining)} left). Stop anytime for no reward, or wait to claim!"
            } else {
                "Expedition complete! Resolve your expedition on the Expedition tab first."
            }
            return false
        }
        return true
    }

    fun togglePedometerTracking() {
        if (isWatchConnected.value) {
            val watchLabel = activeWatchDisplayName()
            _bannerMessage.value = "Phone pedometer stays off while $watchLabel is connected."
            return
        }
        val nextEnabled = !settings.value.pedometerEnabled
        viewModelScope.launch {
            preferences.setPedometerEnabled(nextEnabled)
            if (nextEnabled) {
                val savedBaseline = preferences.readLastHardwareStepCount()
                if (savedBaseline >= 0L) {
                    stepTracker.setPersistedBaseline(savedBaseline, force = true)
                }
                stepTracker.startTracking(settings.value.batteryBatchingEnabled)
                _bannerMessage.value = "Phone Pedometer resumed."
            } else {
                val baseline = stepTracker.latestBaseline()
                if (baseline >= 0L) preferences.setLastHardwareStepCount(baseline)
                stepTracker.stopTracking()
                _bannerMessage.value = "Phone Pedometer paused."
            }
        }
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
    }

    private fun activeWatchDisplayName(): String {
        val watch = watchLinkStatus.value
        val hc = healthConnectStatus.value
        return when {
            watch.connected -> watch.watchDisplayName
            hc.state == HealthConnectState.CONNECTED -> hc.primaryWatchSource
            else -> "Watch"
        }
    }

    private suspend fun reconcilePedometerWithWatchState() {
        if (isWatchConnected.value) {
            // Watch connected: turn phone pedometer OFF and clear baseline so watch-time
            // hardware steps on the phone are never later double-counted.
            stepTracker.pauseForWatch(activeWatchDisplayName())
            preferences.setLastHardwareStepCount(-1L)
        } else {
            val pedometerOn = preferences.readPedometerEnabled()
            if (pedometerOn) {
                val savedBaseline = preferences.readLastHardwareStepCount()
                if (savedBaseline >= 0L) {
                    stepTracker.setPersistedBaseline(savedBaseline, force = true)
                }
                if (stepTelemetry.value.status != com.example.hardware.StepSensorStatus.TRACKING_ACTIVE) {
                    stepTracker.startTracking(settings.value.batteryBatchingEnabled)
                }
                stepTracker.flush()
            } else {
                stepTracker.stopTracking()
            }
        }
    }

    fun onWatchConnectionBroadcastReceived(watchConnected: Boolean, watchName: String?) {
        viewModelScope.launch {
            watchBridge.markWatchConnectionFromBroadcast(
                connected = watchConnected,
                nodeId = null,
                displayName = watchName
            )
            if (!watchConnected) {
                // Watch disconnected: clear grace timer & live sleep state, enable phone pedometer, and switch immediately
                _lastWatchStepReceivedMs.value = 0L
                preferences.setLastWatchStepReceivedMs(0L)
                preferences.setWatchLiveSleeping(false)
                preferences.setPedometerEnabled(true)
                preferences.setLastHardwareStepCount(-1L)
                refreshWatchSleepState()
                reconcilePedometerWithWatchState()
                _bannerMessage.value = "Watch disconnected — switched to Phone Pedometer (Steps now recharge Energy & Stamina)."
            } else {
                val now = System.currentTimeMillis()
                _lastWatchStepReceivedMs.value = now
                preferences.setLastWatchStepReceivedMs(now)
                refreshWatchSleepState()
                reconcilePedometerWithWatchState()
                val label = watchName?.ifBlank { activeWatchDisplayName() } ?: activeWatchDisplayName()
                _bannerMessage.value = "$label connected — Watch Sleep & Step tracking active."
            }
        }
    }

    fun resetPedometerSession() {
        stepTracker.resetSession()
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        _bannerMessage.value = "Walking session counter reset (daily & lifetime steps preserved)."
    }

    fun cycleDailyStepGoal() {
        val goals = listOf(4000L, 6000L, 8000L, 10000L)
        val current = settings.value.dailyStepGoal
        val next = goals.firstOrNull { it > current } ?: goals.first()
        viewModelScope.launch {
            preferences.setDailyStepGoal(next)
        }
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        _bannerMessage.value = "Daily step goal set to ${String.format(java.util.Locale.US, "%,d", next)} steps."
    }

    fun selectTab(tab: SolstrideTab) {
        if (tab == SolstrideTab.LEADERBOARD) {
            _activeTab.value = SolstrideTab.MULTIPLAYER
            _multiplayerSubTab.value = MultiplayerSubTab.RANKS
        } else {
            _activeTab.value = tab
        }
    }

    fun selectBattleMode(mode: BattleMode) {
        if (_battleState.value.active) {
            _bannerMessage.value = "Finish or retreat from your active battle before switching modes."
            return
        }
        _battleMode.value = mode
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
    }

    fun openWatchSheet(open: Boolean) {
        _watchSheetOpen.value = open
        if (open) {
            viewModelScope.launch {
                watchBridge.refreshWatchConnection()
                healthConnectReader.refreshStatus()
            }
        }
    }

    fun requiredHealthConnectPermissions(): Set<String> = healthConnectReader.requestablePermissions()

    fun backgroundHealthConnectPermission(): Set<String> = healthConnectReader.backgroundOnlyPermission()

    fun buildHealthConnectInstallIntent(): android.content.Intent =
        healthConnectReader.buildInstallOrUpdateIntent()

    fun buildHealthConnectSettingsIntent(): android.content.Intent =
        healthConnectReader.buildHealthConnectSettingsIntent()

    fun buildOpenSamsungHealthIntent(): android.content.Intent =
        healthConnectReader.buildOpenSamsungHealthIntent()

    fun toggleHealthConnectAllSources() {
        val next = !settings.value.healthConnectAllSources
        viewModelScope.launch {
            preferences.setHealthConnectAllSources(next)
            syncWatchStepsCycle(refreshNode = false, silent = true)
            _bannerMessage.value = if (next) {
                "Health Connect now reads ALL step sources (watch + fitness apps)."
            } else {
                "Health Connect set to Watch & Wearables only (prevents double-counting phone steps)."
            }
        }
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
    }

    fun onHealthConnectPermissionResult(grantedPermissions: Set<String>) {
        val coreRequired = healthConnectReader.coreStepPermissions()
        viewModelScope.launch {
            val updated = healthConnectReader.refreshStatus()
            if (coreRequired.isNotEmpty() &&
                (grantedPermissions.containsAll(coreRequired) || updated.state == HealthConnectState.CONNECTED)
            ) {
                haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
                syncWatchStepsCycle(refreshNode = true, silent = false)
                val hc = healthConnectStatus.value
                val bgSuffix = if (hc.backgroundReadGranted) " (24/7 closed-app sync ON)" else ""
                _bannerMessage.value = if (hc.watchStepsToday > 0L) {
                    "Health Connect linked via ${hc.primaryWatchSource}$bgSuffix!"
                } else {
                    "Health Connect linked$bgSuffix! Ensure your watch/fitness app (e.g. Samsung Health) shares steps with Health Connect."
                }
            } else {
                _bannerMessage.value = "Health Connect step permission was not granted — built-in pedometer remains active."
            }
        }
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun onForegroundResumed() {
        _isForegroundActive.value = true
        BackgroundSyncGuard.setForeground(true)
        _currentTimeMs.value = System.currentTimeMillis()
        _activeWidgetCount.value = com.example.widget.SolstrideWidgetUpdater.activeWidgetCount(appContext)
        // Lock out Pet Widget immediately when Solstride loads into foreground
        com.example.widget.SolstrideWidgetSnapshotStore.setAppForegroundLocked(appContext, true)
        viewModelScope.launch {
            reconcilePendingPetWidgetLedger()
            // Check watch connection FIRST so phone pedometer NEVER turns on while a watch is connected,
            // and immediately turns ON if no watch is connected!
            val currentWatchStatus = watchBridge.refreshWatchConnection()
            if (!currentWatchStatus.connected) {
                _lastWatchStepReceivedMs.value = 0L
                preferences.setLastWatchStepReceivedMs(0L)
            } else {
                _lastWatchStepReceivedMs.value = preferences.readLastWatchStepReceivedMs()
            }
            healthConnectReader.refreshStatus()
            refreshWatchSleepState()
            reconcilePedometerWithWatchState()
            syncWatchStepsCycle(refreshNode = false, silent = true)
            publishTownPresenceNow(force = true)
        }
    }

    fun onBackgroundPaused() {
        _isForegroundActive.value = false
        BackgroundSyncGuard.setForeground(false)
        viewModelScope.launch {
            repository.leaveTownPresence(currentUserId)
        }
        if (!isWatchConnected.value) {
            val latestBaseline = stepTracker.latestBaseline()
            if (latestBaseline >= 0L) {
                viewModelScope.launch { preferences.setLastHardwareStepCount(latestBaseline) }
            }
        }
        stepTracker.stopTracking()
        // Flush latest steps into Pet Widget stamina/treats, unlock Pet Widget, and arm background auto-sync
        com.example.widget.SolstrideWidgetSnapshotStore.rechargeWildStaminaAndTreats(
            appContext,
            todayPedometerSteps.value
        )
        val snap = _widgetSnapshot.value.copy(
            isAppForegroundLocked = false,
            lastUpdatedMs = System.currentTimeMillis()
        )
        _widgetSnapshot.value = snap
        com.example.widget.SolstrideWidgetSnapshotStore.save(appContext, snap)
        com.example.widget.SolstrideWidgetSnapshotStore.setAppForegroundLocked(appContext, false)
        com.example.widget.WidgetAutoSyncScheduler.syncSchedule(appContext)
    }

    suspend fun cleanupBeforeSignOut() {
        repository.leaveTownPresence(currentUserId)
    }

    fun wakeHeroFromSleep() {
        val marker = _watchSleepState.value.activeSessionMarkerMs.let {
            if (it > 0L) it else System.currentTimeMillis()
        }
        _manuallyWokenSleepMarkerMs.value = marker
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            preferences.setManuallyWokenSleepMs(marker)
            preferences.setWatchLiveSleeping(false)
            _watchSleepState.value = _watchSleepState.value.copy(isSleepingNow = false)
            com.example.widget.SolstrideWidgetSnapshotStore.updateHeroSleepingOnly(appContext, false, "")
            com.example.widget.SolstrideWidgetUpdater.updateAllWidgets(appContext)
            _bannerMessage.value = "Hero awakened from Watch Sleep rest and ready for adventure!"
        }
    }

    private suspend fun refreshWatchSleepState() {
        val watchConnected = watchLinkStatus.value.connected
        val snap = healthConnectReader.readWatchSleepSnapshot(watchConnected = watchConnected)
        _watchSleepState.value = snap
        val wokenMarker = _manuallyWokenSleepMarkerMs.value
        val activeSleeping = watchConnected &&
            snap.isSleepingNow &&
            (snap.activeSessionMarkerMs == 0L || snap.activeSessionMarkerMs != wokenMarker)
        com.example.widget.SolstrideWidgetSnapshotStore.updateHeroSleepingOnly(
            context = appContext,
            isSleeping = activeSleeping,
            sleepSourceLabel = if (activeSleeping) snap.sleepSourceLabel else ""
        )

        // Apply deduplicated Well-Rested recovery + Sleep Sol Coins (% of time slept, max 50) once a real watch sleep finishes
        if (!activeSleeping && snap.latestCompletedEndMs > 0L && snap.latestCompletedDurationMinutes >= ProgressionEngine.MIN_CREDITED_SLEEP_MINUTES) {
            val lastCredited = preferences.readLastCreditedSleepEndMs()
            if (snap.latestCompletedEndMs > lastCredited) {
                val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
                val stats = derivedStats.value ?: return
                preferences.setLastCreditedSleepEndMs(snap.latestCompletedEndMs)

                val durationMin = snap.latestCompletedDurationMinutes
                val dreamTier = DreamRestTier.forSleepMinutes(durationMin.toInt())
                val sleepReward = ProgressionEngine.computeSleepSolCoinReward(durationMin)
                val fraction = (durationMin / 360f).coerceIn(0.25f, 1.0f)
                val hpGain = (stats.computedMaxHp * fraction).roundToInt().toLong().coerceAtLeast(15L)
                val energyGain = (stats.computedMaxEnergy * fraction).roundToInt().toLong().coerceAtLeast(15L)
                val woundsCured = dreamTier.woundsCured
                val newHp = (profile.currentHp + hpGain).coerceAtMost(stats.computedMaxHp.toLong())
                val newEnergy = (profile.strideEnergy + energyGain).coerceAtMost(stats.computedMaxEnergy.toLong())
                val newSolCoins = (profile.solCoins + sleepReward.solCoinsEarned).coerceAtMost(100_000_000L)

                var curedActual = 0
                var restoredSta = 100
                preferences.updateHardcoreRpgState { hc ->
                    val maxSta = hc.ComputedMaxStamina(profile.endurance)
                    val targetSta = ((maxSta * dreamTier.staminaRestorePct) / 100f).roundToInt()
                        .coerceIn(25, (maxSta * 1.25f).roundToInt())
                    restoredSta = max(hc.heroStamina, targetSta)
                    val nextWounds = (hc.battleWoundStacks - woundsCured).coerceAtLeast(0)
                    curedActual = (hc.battleWoundStacks - nextWounds).coerceAtLeast(0)
                    hc.copy(
                        battleWoundStacks = nextWounds,
                        restedClarityBattlesLeft = 5,
                        restedClarityPct = sleepReward.sleepPercent.coerceIn(25, 100),
                        heroStamina = restoredSta,
                        maxHeroStamina = maxSta,
                        activeDreamTierId = dreamTier.id,
                        lastSleepDurationMinutes = durationMin.toInt(),
                        dreamShards = (hc.dreamShards + dreamTier.dreamShardGrant).coerceAtMost(99_999L)
                    )
                }
                repository.updateHeroProfile(
                    currentUserId,
                    mapOf(
                        "currentHp" to newHp,
                        "strideEnergy" to newEnergy,
                        "solCoins" to newSolCoins
                    )
                )
                val hours = durationMin / 60L
                val mins = durationMin % 60L
                val durLabel = if (hours > 0L) "${hours}h ${mins}m" else "${mins}m"
                val woundNote = if (curedActual > 0) " & cured $curedActual Wound(s)" else ""
                haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
                _bannerMessage.value = "🌙 Watch Sleep ($durLabel • ${dreamTier.title})! Stamina -> $restoredSta, +${dreamTier.dreamShardGrant} Dream Shard(s), +${sleepReward.solCoinsEarned} Sol Coins, +$energyGain Energy$woundNote!"
            }
        }
    }

    private suspend fun reconcilePendingPetWidgetLedger() {
        petLedgerSyncMutex.withLock {
            val ledger = com.example.widget.SolstrideWidgetSnapshotStore.readPetLedger(appContext)
            if (!ledger.hasPendingSync()) return

            val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
            val ownedPets = (petsState.value as? UiState.Success)?.data ?: return
            val targetPet = ownedPets.firstOrNull {
                ledger.petId.isNotBlank() && (it.id == ledger.petId || it.petType == ledger.petId)
            } ?: equippedPet.value ?: ownedPets.firstOrNull()

            var leveledPetResult: OwnedPet? = targetPet
            var levelsGained = 0
            val todayKey = currentCalendarDateKey()
            val addedCareToday = if (ledger.pendingCareDateKey == todayKey) {
                ledger.pendingCareActions.coerceAtLeast(0)
            } else {
                0
            }
            if (targetPet != null && (ledger.pendingPetXp > 0L || ledger.pendingBondXp > 0L || addedCareToday > 0)) {
                val leveled = if (ledger.pendingPetXp > 0L) {
                    PetCatalog.applyFoodXp(targetPet, ledger.pendingPetXp)
                } else {
                    targetPet
                }
                levelsGained = (leveled.level - targetPet.level).toInt().coerceAtLeast(0)
                val currentUsedToday = if (targetPet.lastCareDateKey == todayKey) {
                    targetPet.dailyCareActions.toInt()
                } else {
                    0
                }
                val nextDailyCare = (currentUsedToday + addedCareToday)
                    .coerceIn(0, PetEvolutionCatalog.DAILY_CARE_LIMIT)
                    .toLong()
                val nextCareDateKey = if (addedCareToday > 0) todayKey else targetPet.lastCareDateKey
                val nextBondXp = (targetPet.bondXp + ledger.pendingBondXp)
                    .coerceAtMost(PetEvolutionCatalog.MAX_BOND_XP)

                val petDocId = targetPet.id.ifBlank { targetPet.petType }
                val petUpdates = mutableMapOf<String, Any>(
                    "level" to leveled.level,
                    "xp" to leveled.xp,
                    "xpToNextLevel" to leveled.xpToNextLevel,
                    "bondXp" to nextBondXp
                )
                if (addedCareToday > 0) {
                    petUpdates["lastCareDateKey"] = nextCareDateKey
                    petUpdates["dailyCareActions"] = nextDailyCare
                }
                val petRes = repository.updateOwnedPet(
                    currentUserId,
                    petDocId,
                    petUpdates
                )
                if (petRes.isFailure) {
                    // Retry once connection returns — never drop queued widget XP!
                    return
                }
                leveledPetResult = leveled.copy(
                    bondXp = nextBondXp,
                    lastCareDateKey = nextCareDateKey,
                    dailyCareActions = nextDailyCare
                )
            }

            if (ledger.pendingOreWon > 0L || ledger.pendingEssenceWon > 0L || ledger.pendingBonusCareUsed > 0) {
                preferences.updateHardcoreRpgState { hc ->
                    hc.copy(
                        sunsteelOre = (hc.sunsteelOre + ledger.pendingOreWon).coerceAtMost(999_999L),
                        elementalEssence = (hc.elementalEssence + ledger.pendingEssenceWon).coerceAtMost(999_999L),
                        bonusPetCareCharges = (hc.bonusPetCareCharges - ledger.pendingBonusCareUsed).coerceAtLeast(0)
                    )
                }
            }

            val nextCoins = (profile.solCoins - ledger.pendingCoinsSpent).coerceAtLeast(0L)
            val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
            val updatedProfile = profile.copy(solCoins = nextCoins)
            val effectiveActivePet = if (equippedPet.value != null && leveledPetResult != null &&
                (equippedPet.value?.id == leveledPetResult.id || equippedPet.value?.petType == leveledPetResult.petType)
            ) {
                leveledPetResult
            } else {
                equippedPet.value
            }
            val stats = computeDerivedStats(updatedProfile, items, effectiveActivePet, hardcoreState.value)

            val profileRes = repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "solCoins" to nextCoins,
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "maxStrideEnergy" to stats.computedMaxEnergy.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            if (profileRes.isFailure) return

            syncLeaderboard(updatedProfile, updatedProfile.level, stats.computedHeroPower.toLong(), updatedProfile.bossesDefeated)
            com.example.widget.SolstrideWidgetSnapshotStore.clearPendingPetLedgerAfterSync(appContext)

            val petName = leveledPetResult?.resolvedForm()?.formName
                ?: leveledPetResult?.resolvedType()?.displayName
                ?: "Companion"
            val parts = mutableListOf<String>()
            if (ledger.pendingPetXp > 0L) parts.add("+${ledger.pendingPetXp} Pet XP")
            if (ledger.pendingBondXp > 0L) parts.add("+${ledger.pendingBondXp} Bond XP")
            if (levelsGained > 0 && leveledPetResult != null) parts.add("★ Lv.${leveledPetResult.level}!")
            if (addedCareToday > 0) parts.add("$addedCareToday Daily Care used")
            if (ledger.pendingWildWins > 0) parts.add("${ledger.pendingWildWins} Wild Win(s)")
            if (ledger.pendingOreWon > 0L) parts.add("+${ledger.pendingOreWon} Ore")
            if (ledger.pendingEssenceWon > 0L) parts.add("+${ledger.pendingEssenceWon} Essence")
            if (ledger.pendingCoinsSpent > 0L) parts.add("-${ledger.pendingCoinsSpent} Coins")

            haptics.trigger(if (levelsGained > 0) GameplayHaptic.VICTORY_FANFARE else GameplayHaptic.SYNC_CONFIRM)
            _bannerMessage.value = "🐾 Pet Widget Synced ($petName): ${parts.joinToString(", ")} • Widget locked while app is open."
        }
    }

    fun onActivityPermissionGranted() {
        viewModelScope.launch {
            preferences.setPedometerEnabled(true)
            reconcilePedometerWithWatchState()
            haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
            _bannerMessage.value = if (isWatchConnected.value) {
                "Permission saved! Phone pedometer stays on standby while ${activeWatchDisplayName()} is connected."
            } else {
                "Phone Pedometer active! Steps count automatically whenever no watch is connected."
            }
        }
    }

    fun onBluetoothConnectPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            watchBridge.onBluetoothPermissionUpdated()
            val status = watchBridge.refreshWatchConnection()
            reconcilePedometerWithWatchState()
            haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
            _bannerMessage.value = when {
                granted && status.connected ->
                    "${status.watchDisplayName} detected (${status.connectionTransport}) — Phone Pedometer on standby."
                granted ->
                    "Nearby Bluetooth Watch detection enabled! Will auto-switch immediately when your watch connects or disconnects."
                else ->
                    "Bluetooth permission declined — using Wear OS & Audio watch detection fallback."
            }
        }
    }

    fun onHardwareBaselineUpdated(baseline: Long) {
        if (baseline < 0L || isWatchConnected.value) return
        viewModelScope.launch {
            preferences.setLastHardwareStepCount(baseline)
        }
    }

    fun onHardwareStepsReceived(delta: Long) {
        // Strict rule: Phone steps do NOT count while a watch is connected!
        if (delta <= 0L || isWatchConnected.value) return
        val now = System.currentTimeMillis()
        val latestBaseline = stepTracker.latestBaseline()
        viewModelScope.launch {
            if (isWatchConnected.value) return@launch
            preferences.recordDailyPedometerSteps(currentCalendarDateKey(), delta)
            if (latestBaseline >= 0L) {
                preferences.setLastHardwareStepCount(latestBaseline)
            }
            offlineSyncDao.insertBatch(
                PendingStepBatchEntity(
                    batchId = "phone_${now}_${delta}_${java.util.UUID.randomUUID().toString().take(6)}",
                    userId = currentUserId,
                    source = StepSource.PHONE.name,
                    stepDelta = delta,
                    recordedAtMs = now
                )
            )
            if (isOnline.value) {
                flushPendingStepBatches(silent = delta < 25L)
            }
        }
    }

    private suspend fun syncWatchStepsCycle(refreshNode: Boolean, silent: Boolean) {
        if (refreshNode) {
            watchBridge.refreshWatchConnection()
        }
        if (!isWatchConnected.value && settings.value.pedometerEnabled) {
            stepTracker.flush()
        }
        val includeAllHcSources = settings.value.healthConnectAllSources || !settings.value.pedometerEnabled
        val hcPayloads = healthConnectReader.pullNewWatchSteps(
            includeAllSources = includeAllHcSources,
            watchConnected = watchLinkStatus.value.connected
        )
        val wearPayloads = watchBridge.pullPendingWatchSteps()
        val allWatchPayloads = hcPayloads + wearPayloads
        if (watchLinkStatus.value.connected && allWatchPayloads.isNotEmpty()) {
            val now = System.currentTimeMillis()
            _lastWatchStepReceivedMs.value = now
            preferences.setLastWatchStepReceivedMs(now)
        } else if (!watchLinkStatus.value.connected) {
            _lastWatchStepReceivedMs.value = 0L
            preferences.setLastWatchStepReceivedMs(0L)
        }
        reconcilePedometerWithWatchState()
        for (payload in allWatchPayloads) {
            offlineSyncDao.insertBatch(
                PendingStepBatchEntity(
                    batchId = payload.batchId,
                    userId = currentUserId,
                    source = StepSource.GALAXY_WATCH.name,
                    stepDelta = payload.stepDelta,
                    recordedAtMs = payload.recordedAtMs
                )
            )
        }
        claimWatchBatchesAndSync(silent = silent)
        refreshWatchSleepState()
    }

    fun manualSyncWatchAndCloud() {
        viewModelScope.launch {
            _isSyncingNow.value = true
            try {
                syncWatchStepsCycle(refreshNode = true, silent = false)
                val status = watchLinkStatus.value
                val hc = healthConnectStatus.value
                val currentProfile = (heroProfileState.value as? UiState.Success)?.data
                if (currentProfile != null) {
                    watchBridge.pushHeroSnapshotToWatch(currentProfile)
                    preferences.setLastWatchSyncMs(System.currentTimeMillis())
                }
                haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
                _bannerMessage.value = when {
                    hc.state == HealthConnectState.CONNECTED ->
                        "Synced ${hc.primaryWatchSource} (${hc.watchStepsToday} watch steps today) & Phone Pedometer."
                    status.connected ->
                        "Synced phone pedometer & ${status.watchDisplayName} to Cloud."
                    hc.state == HealthConnectState.PERMISSION_REQUIRED ->
                        "Phone pedometer synced. Tap 'Connect Watch (Health Connect)' to import watch steps."
                    else ->
                        "Synced phone pedometer & Cloud progress."
                }
            } finally {
                _isSyncingNow.value = false
            }
        }
    }

    private suspend fun claimWatchBatchesAndSync(silent: Boolean) {
        offlineSyncDao.claimUnboundWatchBatches(currentUserId)
        flushPendingStepBatches(silent)
    }

    private suspend fun flushPendingStepBatches(silent: Boolean) {
        syncMutex.withLock {
            val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
            val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
            val activePet = equippedPet.value
            val stats = derivedStats.value ?: computeDerivedStats(profile, items, activePet)
            val batches = offlineSyncDao.getPendingBatches(currentUserId)
            if (batches.isEmpty()) return

            // If a watch is currently connected, discard any stray PHONE batches so phone steps never count!
            val watchActiveNow = isWatchConnected.value
            val (acceptedBatches, ignoredPhoneBatches) = if (watchActiveNow) {
                batches.partition { it.source != StepSource.PHONE.name }
            } else {
                batches to emptyList()
            }
            if (ignoredPhoneBatches.isNotEmpty()) {
                offlineSyncDao.markBatchesSynced(ignoredPhoneBatches.map { it.batchId })
            }
            if (acceptedBatches.isEmpty()) return

            val totalDelta = acceptedBatches.sumOf { it.stepDelta }.coerceAtLeast(0L)
            if (totalDelta <= 0L) {
                offlineSyncDao.markBatchesSynced(acceptedBatches.map { it.batchId })
                return
            }

            val weekNow = currentIsoWeekKey()
            val prevWeekly = if (profile.weekKey == weekNow) profile.weeklySteps else 0L
            val newTotalSteps = (profile.totalSteps + totalDelta).coerceAtMost(1_000_000_000L)
            val newWeeklySteps = (prevWeekly + totalDelta).coerceAtMost(10_000_000L)

            // Real-world Steps recharge Stride Battle Energy, grant Hero XP, advance Step Dungeons, and fuel Slayer Contracts!
            val stepXpGain = ProgressionEngine.computeHeroStepXp(totalDelta, stats.totalXpMultiplier)
            val stepEnergyGain = ProgressionEngine.computeStepEnergyGain(totalDelta, stats.stepEnergyMultiplier)
            val maxEnergy = stats.computedMaxEnergy.toLong().coerceAtLeast(100L)
            val nextStrideEnergy = (profile.strideEnergy + stepEnergyGain).coerceAtMost(maxEnergy)
            val nextEncounterProgress = (profile.encounterProgress + (totalDelta / 8L).coerceAtLeast(1L)).coerceAtMost(100L)

            val leveled = applyXpAndLevelUp(profile, stepXpGain, stats)
            val leveledUpHero = leveled.level > profile.level

            val seasonStepXpGain = (totalDelta / 25L).coerceAtLeast(1L)
            val newSeasonXp = (profile.seasonXp + seasonStepXpGain).coerceAtMost(999_999L)

            val updateMap = mapOf(
                "totalSteps" to newTotalSteps,
                "weeklySteps" to newWeeklySteps,
                "weekKey" to weekNow,
                "level" to leveled.level,
                "xp" to leveled.xp,
                "xpToNextLevel" to leveled.xpToNextLevel,
                "unspentPoints" to leveled.unspentPoints,
                "maxHp" to leveled.maxHp,
                "currentHp" to leveled.currentHp,
                "strideEnergy" to nextStrideEnergy,
                "maxStrideEnergy" to maxEnergy,
                "encounterProgress" to nextEncounterProgress,
                "heroPower" to leveled.heroPower,
                "seasonXp" to newSeasonXp
            )

            val result = repository.updateHeroProfile(currentUserId, updateMap)
            if (result.isSuccess) {
                offlineSyncDao.markBatchesSynced(acceptedBatches.map { it.batchId })
                val sources = acceptedBatches.map { it.source }.distinct().joinToString(" + ")
                val watchDelta = acceptedBatches.filter { it.source != StepSource.PHONE.name }.sumOf { it.stepDelta }
                if (watchDelta > 0L) {
                    preferences.recordDailyPedometerSteps(currentCalendarDateKey(), watchDelta)
                }

                // Fan out walked steps to Equipped Pet Evolution progress & Step-Powered Guild!
                if (activePet != null) {
                    val eventSpec = activeWorldEvent.value.first
                    val bondGain = ((totalDelta / 60L) * (100L + eventSpec.petBondBonusPct) / 100L).coerceAtLeast(0L)
                    val nextWalked = (activePet.stepsWalkedWithPet + totalDelta).coerceAtMost(100_000_000L)
                    val nextBond = (activePet.bondXp + bondGain).coerceAtMost(PetEvolutionCatalog.MAX_BOND_XP)
                    repository.updateOwnedPet(
                        currentUserId,
                        activePet.petType,
                        mapOf(
                            "stepsWalkedWithPet" to nextWalked,
                            "bondXp" to nextBond
                        )
                    )
                }
                if (profile.guildId.isNotBlank()) {
                    repository.contributeGuildProgress(
                        userId = currentUserId,
                        guildId = profile.guildId,
                        stepsDelta = totalDelta
                    )
                }

                // Real steps dispel World Fog, advance active Step Dungeon corridor, recover Stamina, and track Slayer Contracts!
                // When the user does NOT have a watch that detects sleep, steps count directly for BOTH Energy AND Stamina.
                val watchSleepActive = hasActiveWatchSleepDetection()
                val activeRealmKey = profile.currentRealm.toInt().coerceIn(1, ProgressionEngine.MAX_REALM.toInt()).toString()
                val stepStaminaGain = ProgressionEngine.computeStepStaminaRecovery(
                    stepDelta = totalDelta,
                    hasWatchSleepDetection = watchSleepActive,
                    stepEnergyMultiplier = stats.stepEnergyMultiplier
                )
                preferences.updateHardcoreRpgState { hc ->
                    val fogMap = hc.realmFogStepsMap.toMutableMap()
                    val prevFogSteps = fogMap[activeRealmKey] ?: 0
                    fogMap[activeRealmKey] = (prevFogSteps + totalDelta.toInt())
                        .coerceAtMost(ProgressionEngine.REALM_FOG_FULL_CLEAR_STEPS)

                    val maxSta = hc.ComputedMaxStamina(leveled.endurance)
                    val nextSta = max(hc.heroStamina, (hc.heroStamina + stepStaminaGain).coerceAtMost(maxSta))
                    val nextDungeonSteps = (hc.activeDungeonRoomSteps + totalDelta.toInt()).coerceAtMost(999_999)

                    val nextSlayerSteps = hc.slayerContractStepsMap.toMutableMap()
                    SlayerContractCatalog.contracts.forEach { contract ->
                        val prev = nextSlayerSteps[contract.id] ?: 0
                        nextSlayerSteps[contract.id] = (prev + totalDelta.toInt()).coerceAtMost(999_999)
                    }

                    val nextMastery = hc.activityMasteryXpMap.toMutableMap()
                    val exploreGain = (totalDelta / 120L).toInt().coerceAtLeast(1)
                    nextMastery[ActivityMasteryType.EXPLORATION.id] =
                        (nextMastery[ActivityMasteryType.EXPLORATION.id] ?: 0) + exploreGain

                    hc.copy(
                        realmFogStepsMap = fogMap,
                        heroStamina = nextSta,
                        maxHeroStamina = maxSta,
                        activeDungeonRoomSteps = nextDungeonSteps,
                        slayerContractStepsMap = nextSlayerSteps,
                        activityMasteryXpMap = nextMastery
                    )
                }

                // Feed walked steps directly into Pet Arena Widget Wild Stamina & Step Treats
                val refreshedSettings = preferences.settingsFlow.first()
                val todayNow = if (refreshedSettings.pedometerDateKey == currentCalendarDateKey()) {
                    refreshedSettings.pedometerDailySteps
                } else {
                    0L
                }
                com.example.widget.SolstrideWidgetSnapshotStore.rechargeWildStaminaAndTreats(appContext, todayNow)

                val lvlTag = if (leveledUpHero) " ★ HERO LV.${leveled.level}!" else ""
                val staminaTag = if (watchSleepActive) "" else ", +$stepStaminaGain STA"
                offlineSyncDao.insertSyncLog(
                    SyncLogEntity(
                        userId = currentUserId,
                        source = sources,
                        stepDelta = totalDelta,
                        summary = "+$totalDelta steps -> +$stepEnergyGain Battle EN$staminaTag, +$stepXpGain Hero XP$lvlTag & Dungeon Progress",
                        timestampMs = System.currentTimeMillis()
                    )
                )
                repository.upsertLeaderboard(
                    userId = currentUserId,
                    displayName = profile.displayName,
                    heroClass = profile.heroClass,
                    heroLevel = leveled.level,
                    heroPower = leveled.heroPower,
                    weeklySteps = newWeeklySteps,
                    weekKey = weekNow,
                    bossesDefeated = profile.bossesDefeated,
                    prestigeLevel = profile.prestigeLevel,
                    highestSpireFloor = profile.highestSpireFloor
                )
                val updatedProfile = profile.copy(
                    totalSteps = newTotalSteps,
                    weeklySteps = newWeeklySteps,
                    weekKey = weekNow,
                    level = leveled.level,
                    xp = leveled.xp,
                    xpToNextLevel = leveled.xpToNextLevel,
                    unspentPoints = leveled.unspentPoints,
                    maxHp = leveled.maxHp,
                    currentHp = leveled.currentHp,
                    strideEnergy = nextStrideEnergy,
                    maxStrideEnergy = maxEnergy,
                    encounterProgress = nextEncounterProgress,
                    heroPower = leveled.heroPower
                )
                watchBridge.pushHeroSnapshotToWatch(updatedProfile)
                preferences.setLastWatchSyncMs(System.currentTimeMillis())

                if (leveledUpHero) {
                    haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
                    _bannerMessage.value = if (watchSleepActive) {
                        "★ Hero Level Up (Lv.${leveled.level})! +$totalDelta steps -> +$stepEnergyGain Battle EN & +$stepXpGain Hero XP!"
                    } else {
                        "★ Hero Level Up (Lv.${leveled.level})! +$totalDelta steps -> +$stepEnergyGain Battle EN, +$stepStaminaGain Stamina & +$stepXpGain Hero XP!"
                    }
                } else if (!silent) {
                    _bannerMessage.value = if (watchSleepActive) {
                        "Merged +$totalDelta steps -> +$stepEnergyGain Battle EN, +$stepXpGain XP & Dungeon Stride."
                    } else {
                        "Merged +$totalDelta steps -> +$stepEnergyGain Battle EN, +$stepStaminaGain Stamina, +$stepXpGain XP & Dungeon Stride."
                    }
                }
            }
        }
    }

    // --- Timed Expeditions (Risk/Survival Roll, Early Stop with No Reward, Sealed Loot Chests) ---

    fun startTimedExpedition(durationMinutes: Int) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        if (_battleState.value.active) {
            _bannerMessage.value = "Finish or retreat from your active battle before starting an expedition."
            return
        }
        val safeMinutes = durationMinutes.coerceIn(
            ExpeditionEngine.MIN_DURATION_MINUTES,
            ExpeditionEngine.MAX_DURATION_MINUTES
        )
        val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
        val stats = computeDerivedStats(profile, items, equippedPet.value, hardcoreState.value)
        val forecast = ExpeditionEngine.evaluateRiskForecast(safeMinutes, stats, profile.level.toInt())

        val startMs = System.currentTimeMillis()
        val endMs = startMs + (safeMinutes.toLong() * 60_000L)
        _lastExpeditionReport.value = null
        haptics.trigger(GameplayHaptic.ENCOUNTER_ALERT)

        viewModelScope.launch {
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "activeExpeditionStartMs" to startMs,
                    "activeExpeditionDurationMin" to safeMinutes.toLong(),
                    "activeExpeditionEndMs" to endMs
                )
            ).onSuccess {
                _currentTimeMs.value = startMs
                _bannerMessage.value = "Hero departed (${ExpeditionEngine.formatDuration(safeMinutes)}, ${forecast.survivalChancePct}% survival odds). Watch steps still count!"
            }.onFailure { err ->
                _bannerMessage.value = "Could not launch expedition: ${err.localizedMessage ?: "sync error"}"
            }
        }
    }

    fun stopActiveExpedition() {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!profile.hasActiveExpedition()) return
        val now = System.currentTimeMillis()
        val plannedMin = profile.activeExpeditionDurationMin.toInt()
            .coerceIn(ExpeditionEngine.MIN_DURATION_MINUTES, ExpeditionEngine.MAX_DURATION_MINUTES)
        val elapsedMs = (now - profile.activeExpeditionStartMs).coerceAtLeast(0L)
        val elapsedMin = (elapsedMs / 60_000L).toInt().coerceIn(0, plannedMin)
        val coinReward = ExpeditionEngine.computeSolCoinReward(elapsedMin, plannedMin)
        val newSolCoins = (profile.solCoins + coinReward.earnedSolCoins).coerceAtMost(100_000_000L)
        haptics.trigger(if (coinReward.earnedSolCoins > 0L) GameplayHaptic.VICTORY_FANFARE else GameplayHaptic.GUARD_BLOCK)

        viewModelScope.launch {
            val updates = mutableMapOf<String, Any>(
                "activeExpeditionStartMs" to 0L,
                "activeExpeditionDurationMin" to 0L,
                "activeExpeditionEndMs" to 0L
            )
            if (coinReward.earnedSolCoins > 0L) {
                updates["solCoins"] = newSolCoins
            }
            repository.updateHeroProfile(currentUserId, updates).onSuccess {
                _lastExpeditionReport.value = null
                _bannerMessage.value = if (coinReward.earnedSolCoins > 0L) {
                    "Expedition recalled at ${coinReward.timeSpentPercent}% time spent — earned +${coinReward.earnedSolCoins} Sol Coins (chest & XP forfeited). Hero unlocked!"
                } else {
                    "Expedition recalled early (${coinReward.timeSpentPercent}% time spent, <5m) — 0 Sol Coins earned. Hero unlocked!"
                }
            }.onFailure { err ->
                _bannerMessage.value = "Could not stop expedition: ${err.localizedMessage ?: "sync error"}"
            }
        }
    }

    fun claimCompletedExpedition() {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!profile.hasActiveExpedition()) return
        val now = System.currentTimeMillis()
        if (now < profile.activeExpeditionEndMs) {
            val remaining = profile.activeExpeditionEndMs - now
            _bannerMessage.value = "Expedition still underway (${ExpeditionEngine.formatCountdown(remaining)} remaining)."
            return
        }

        val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
        val stats = computeDerivedStats(profile, items, equippedPet.value, hardcoreState.value)
        val report = ExpeditionEngine.resolveCompletedExpedition(currentUserId, profile, stats)
        val todayKey = currentCalendarDateKey()
        val sameDay = profile.dailyQuestDateKey == todayKey

        viewModelScope.launch {
            if (report.succeeded) {
                val chestToSave = report.awardedChest
                var savedChest: LootChest? = null
                if (chestToSave != null) {
                    val chestRes = repository.addLootChest(currentUserId, chestToSave)
                    if (chestRes.isFailure) {
                        _bannerMessage.value = "Could not store Expedition Chest — please retry."
                        return@launch
                    }
                    savedChest = chestRes.getOrNull()
                }
                val leveled = applyXpAndLevelUp(profile, report.xpEarned, stats)
                val newShards = (profile.sunshards + report.sunshardsEarned).coerceAtMost(100_000_000L)
                val newSolCoins = (profile.solCoins + report.solCoinsEarned).coerceAtMost(100_000_000L)
                val newCompleted = (profile.expeditionsCompleted + 1L).coerceAtMost(1_000_000L)
                val nextDailyExp = (if (sameDay) profile.dailyExpeditionsDone else 0L) + 1L
                val expOre = (3L + report.durationMinutes / 20L).coerceAtLeast(3L)
                val expHerbs = (4L + report.durationMinutes / 15L).coerceAtLeast(4L)
                val expEssence = (1L + report.durationMinutes / 45L).coerceAtLeast(1L)
                preferences.updateHardcoreRpgState { hc ->
                    hc.copy(
                        sunsteelOre = (hc.sunsteelOre + expOre).coerceAtMost(999_999L),
                        aetherHerbs = (hc.aetherHerbs + expHerbs).coerceAtMost(999_999L),
                        elementalEssence = (hc.elementalEssence + expEssence).coerceAtMost(999_999L)
                    )
                }

                haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
                val updates = mutableMapOf<String, Any>(
                    "activeExpeditionStartMs" to 0L,
                    "activeExpeditionDurationMin" to 0L,
                    "activeExpeditionEndMs" to 0L,
                    "expeditionsCompleted" to newCompleted,
                    "dailyQuestDateKey" to todayKey,
                    "dailyExpeditionsDone" to nextDailyExp,
                    "sunshards" to newShards,
                    "solCoins" to newSolCoins,
                    "level" to leveled.level,
                    "xp" to leveled.xp,
                    "xpToNextLevel" to leveled.xpToNextLevel,
                    "unspentPoints" to leveled.unspentPoints,
                    "maxHp" to leveled.maxHp,
                    "currentHp" to leveled.currentHp,
                    "heroPower" to leveled.heroPower
                )
                if (!sameDay) {
                    updates["dailyBattlesWon"] = 0L
                    updates["dailyChestsOpened"] = 0L
                }

                repository.updateHeroProfile(currentUserId, updates).onSuccess {
                    savedChest?.id?.takeIf { it.isNotBlank() }?.let { recentlyAwardedChestIds.add(it) }
                    _lastExpeditionReport.value = report.copy(awardedChest = savedChest)
                    if (savedChest != null) {
                        _lastOpenedChestResult.value = null
                        _pendingChestModal.value = savedChest
                    }
                    syncLeaderboard(profile, leveled.level, leveled.heroPower, profile.bossesDefeated)
                    _bannerMessage.value = "Expedition Succeeded! +${report.solCoinsEarned} Sol Coins, +$expOre Ore, +$expHerbs Herbs, +$expEssence Essence & Sealed Chest!"
                }
            } else {
                val reducedHp = max(15L, profile.currentHp - report.hpLost)
                val leveled = applyXpAndLevelUp(profile.copy(currentHp = reducedHp), report.xpEarned, stats)
                val newFailed = (profile.expeditionsFailed + 1L).coerceAtMost(1_000_000L)
                val newSolCoins = (profile.solCoins + report.solCoinsEarned).coerceAtMost(100_000_000L)

                haptics.trigger(GameplayHaptic.DAMAGE_TAKEN)
                repository.updateHeroProfile(
                    currentUserId,
                    mapOf(
                        "activeExpeditionStartMs" to 0L,
                        "activeExpeditionDurationMin" to 0L,
                        "activeExpeditionEndMs" to 0L,
                        "expeditionsFailed" to newFailed,
                        "solCoins" to newSolCoins,
                        "level" to leveled.level,
                        "xp" to leveled.xp,
                        "xpToNextLevel" to leveled.xpToNextLevel,
                        "unspentPoints" to leveled.unspentPoints,
                        "maxHp" to leveled.maxHp,
                        "currentHp" to leveled.currentHp,
                        "heroPower" to leveled.heroPower
                    )
                ).onSuccess {
                    _lastExpeditionReport.value = report
                    syncLeaderboard(profile, leveled.level, leveled.heroPower, profile.bossesDefeated)
                    _bannerMessage.value = "Expedition Failed! Recovered +${report.solCoinsEarned} Sol Coins (50% time salvage), no chest. Upgrade gear for higher survival."
                }
            }
        }
    }

    fun dismissExpeditionReport() {
        _lastExpeditionReport.value = null
    }

    // --- Interactive Loot Chests & Treasure Vault (Strict Single-Use) ---

    private fun isChestAlreadyConsumed(chestId: String): Boolean {
        if (chestId.isBlank()) return true
        return chestId in _localConsumedChestIds.value || chestId in persistedConsumedChestIds.value
    }

    /**
     * Immediately removes every UI reference to a consumed chest (Vault list, Battle victory card,
     * Expedition report card, and open modal) so it can never be tapped or reused again.
     */
    private fun clearConsumedChestReferences(chestId: String) {
        if (chestId.isNotBlank()) {
            _localConsumedChestIds.value = _localConsumedChestIds.value + chestId
            recentlyAwardedChestIds.remove(chestId)
        }
        if (_pendingChestModal.value?.id == chestId || _pendingChestModal.value?.id.isNullOrBlank()) {
            _pendingChestModal.value = null
        }
        val currentBattleOutcome = _battleState.value.outcome
        val dropped = currentBattleOutcome?.droppedChest
        if (dropped != null && (dropped.id == chestId || dropped.id.isBlank())) {
            _battleState.value = _battleState.value.copy(
                outcome = currentBattleOutcome.copy(droppedChest = null)
            )
        }
        val currentExpReport = _lastExpeditionReport.value
        val awarded = currentExpReport?.awardedChest
        if (awarded != null && (awarded.id == chestId || awarded.id.isBlank())) {
            _lastExpeditionReport.value = currentExpReport.copy(awardedChest = null)
        }
    }

    fun promptOpenChest(chest: LootChest) {
        if (chest.id.isBlank() || isChestAlreadyConsumed(chest.id)) {
            clearConsumedChestReferences(chest.id)
            _bannerMessage.value = "This treasure chest has already been opened and can no longer be used."
            return
        }
        val chestsLoaded = chestsState.value as? UiState.Success
        if (chestsLoaded != null &&
            chestsLoaded.data.none { it.id == chest.id } &&
            chest.id !in recentlyAwardedChestIds
        ) {
            clearConsumedChestReferences(chest.id)
            viewModelScope.launch { preferences.markChestConsumed(chest.id) }
            _bannerMessage.value = "This treasure chest has already been opened and can no longer be used."
            return
        }
        _lastOpenedChestResult.value = null
        _pendingChestModal.value = chest
    }

    fun storeChestInVault() {
        _pendingChestModal.value = null
        _lastOpenedChestResult.value = null
        _bannerMessage.value = "Chest stored safely in your Loot Vault (Hero tab)."
    }

    fun dismissChestModal() {
        _pendingChestModal.value = null
        _lastOpenedChestResult.value = null
    }

    fun openLootChest(chest: LootChest) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (chest.id.isBlank() || isChestAlreadyConsumed(chest.id)) {
            clearConsumedChestReferences(chest.id)
            _bannerMessage.value = "This treasure chest has already been opened and can no longer be used."
            return
        }

        viewModelScope.launch {
            chestOpenMutex.withLock {
                if (_isOpeningChest.value) return@withLock
                val persistedConsumed = preferences.readConsumedChestIds()
                val chestsLoaded = chestsState.value as? UiState.Success
                val missingFromVault = chestsLoaded != null &&
                    chestsLoaded.data.none { it.id == chest.id } &&
                    chest.id !in recentlyAwardedChestIds
                if (isChestAlreadyConsumed(chest.id) || chest.id in persistedConsumed || missingFromVault) {
                    clearConsumedChestReferences(chest.id)
                    preferences.markChestConsumed(chest.id)
                    _bannerMessage.value = "This treasure chest has already been opened and permanently consumed."
                    return@withLock
                }
                _isOpeningChest.value = true
                try {
                    val rolled = ExpeditionEngine.openLootChest(chest, currentUserId)
                    // Atomic Firestore transaction: verifies chest exists, permanently deletes it, and writes loot
                    val consumeRes = repository.openAndConsumeLootChest(currentUserId, chest.id, rolled.item)
                    if (consumeRes.isFailure) {
                        val msg = consumeRes.exceptionOrNull()?.message.orEmpty()
                        if (msg.contains("already", ignoreCase = true) ||
                            msg.contains("CHEST_ALREADY_CONSUMED", ignoreCase = true)
                        ) {
                            clearConsumedChestReferences(chest.id)
                            preferences.markChestConsumed(chest.id)
                            _bannerMessage.value = "This treasure chest was already opened and can no longer be used."
                        } else {
                            _bannerMessage.value = "Could not open chest — check connection and tap again."
                        }
                        return@withLock
                    }
                    // Permanently consume chest across memory, DataStore, Battle card, Expedition card, and Vault
                    clearConsumedChestReferences(chest.id)
                    preferences.markChestConsumed(chest.id)
                    val createdId = consumeRes.getOrNull().orEmpty()
                    val persistedItem = rolled.item.copy(id = createdId)

                    val todayKey = currentCalendarDateKey()
                    val sameDay = profile.dailyQuestDateKey == todayKey
                    val nextDailyChests = (if (sameDay) profile.dailyChestsOpened else 0L) + 1L
                    val nextTotalChests = (profile.chestsOpenedTotal + 1L).coerceAtMost(1_000_000L)

                    val profileUpdates = mutableMapOf<String, Any>(
                        "dailyQuestDateKey" to todayKey,
                        "dailyChestsOpened" to nextDailyChests,
                        "chestsOpenedTotal" to nextTotalChests
                    )
                    if (!sameDay) {
                        profileUpdates["dailyBattlesWon"] = 0L
                        profileUpdates["dailyChestsOpened"] = nextDailyChests
                        profileUpdates["dailyExpeditionsDone"] = 0L
                    }
                    repository.updateHeroProfile(currentUserId, profileUpdates)

                    haptics.trigger(
                        if (rolled.wasUltraRare) GameplayHaptic.SOLAR_CRITICAL else GameplayHaptic.VICTORY_FANFARE
                    )
                    _lastOpenedChestResult.value = rolled.copy(item = persistedItem)
                } finally {
                    _isOpeningChest.value = false
                }
            }
        }
    }

    fun equipRevealedItem(item: InventoryItem) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (profile.hasActiveExpedition()) {
            _bannerMessage.value = "Item saved to Inventory — equip once your hero returns from expedition."
            dismissChestModal()
            return
        }
        if (item.id.isBlank()) {
            dismissChestModal()
            return
        }
        val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
        val withItem = if (items.any { it.id == item.id }) items else (items + item)
        equipItemDirect(profile, withItem, item)
        dismissChestModal()
    }

    // --- Class Skill Mastery (Unlock by Level, Upgrade with Sol Coins), Quests & Prestige Rebirth ---

    fun upgradeSkillNode(nodeIndex: Int) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val classSkills = ClassSkillCatalog.skillsFor(profile.resolvedClass())
        val spec = classSkills.getOrNull(nodeIndex) ?: return

        if (!spec.isUnlockedAt(profile.level.toInt())) {
            _bannerMessage.value = "${spec.name} unlocks at Hero Level ${spec.unlockLevel} (currently Lv.${profile.level})."
            return
        }
        val currentRank = ClassSkillCatalog.effectiveRank(profile, spec)
        if (currentRank >= spec.maxRank) {
            _bannerMessage.value = "${spec.evolvedNameForRank(currentRank)} is already maxed (${com.example.domain.SkillMasteryTier.TIER_4_MYTHIC.roman} ${com.example.domain.SkillMasteryTier.TIER_4_MYTHIC.title} • Rank ${spec.maxRank}/${spec.maxRank})."
            return
        }
        val nextRank = (currentRank + 1).coerceAtMost(spec.maxRank)
        val nextMasteryTier = com.example.domain.SkillMasteryTier.forRank(nextRank)
        val requiredHeroLevel = (spec.unlockLevel + nextMasteryTier.requiredHeroLevelBonus).coerceAtLeast(spec.unlockLevel)
        if (profile.level < requiredHeroLevel && profile.prestigeLevel == 0L) {
            _bannerMessage.value = "${nextMasteryTier.roman} ${nextMasteryTier.title} (Rank $nextRank) for ${spec.name} requires Hero Level $requiredHeroLevel (currently Lv.${profile.level})."
            return
        }
        val coinCost = ClassSkillCatalog.upgradeSolCoinCost(currentRank)
        if (profile.solCoins < coinCost) {
            _bannerMessage.value = "Need $coinCost Sol Coins to advance ${spec.name} to ${nextMasteryTier.roman} Rank $nextRank. Complete Quests, Expeditions, or Watch Sleep!"
            return
        }

        val ranks = profile.parsedSkillRanks().toMutableList()
        ranks[nodeIndex] = nextRank
        val serialized = ProgressionEngine.serializeSkillRanks(ranks)
        val nextCoins = (profile.solCoins - coinCost).coerceAtLeast(0L)
        val updated = profile.copy(
            skillRanks = serialized,
            solCoins = nextCoins
        )
        val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
        val stats = computeDerivedStats(updated, items, equippedPet.value, hardcoreState.value)
        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)

        viewModelScope.launch {
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "skillRanks" to serialized,
                    "solCoins" to nextCoins,
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "currentHp" to updated.currentHp.coerceAtMost(stats.computedMaxHp.toLong()),
                    "maxStrideEnergy" to stats.computedMaxEnergy.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            ).onSuccess {
                syncLeaderboard(updated, updated.level, stats.computedHeroPower.toLong(), updated.bossesDefeated)
                val evolvedName = spec.evolvedNameForRank(nextRank)
                _bannerMessage.value = "Ascended to $evolvedName [${nextMasteryTier.roman} ${nextMasteryTier.title} • Rank $nextRank/${spec.maxRank}] (-$coinCost Sol Coins)!"
            }
        }
    }

    fun claimQuestReward(status: QuestStatus) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!status.canClaim) return
        val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
        val ownedPets = (petsState.value as? UiState.Success)?.data.orEmpty()
        val todayKey = currentCalendarDateKey()
        val nextClaimed = ProgressionEngine.pruneAndAppendClaimedQuest(
            existingClaims = profile.claimedQuestIds,
            newClaimKey = status.claimKey,
            todayKey = todayKey,
            weekKey = profile.weekKey
        )
        val rewardPet = status.spec.companionReward
        val alreadyOwnsRewardPet = rewardPet != null &&
            ownedPets.any { it.petType == rewardPet.name || it.id == rewardPet.name }
        val shouldAdoptPet = rewardPet != null && !alreadyOwnsRewardPet
        val autoEquipRewardPet = shouldAdoptPet && profile.equippedPetId.isBlank()
        val nextEquippedPetId = if (autoEquipRewardPet && rewardPet != null) rewardPet.name else profile.equippedPetId
        val activePetAfter = if (autoEquipRewardPet && rewardPet != null) {
            OwnedPet(id = rewardPet.name, userId = currentUserId, petType = rewardPet.name)
        } else {
            equippedPet.value
        }
        val duplicatePetCoins = if (rewardPet != null && alreadyOwnsRewardPet) rewardPet.coinCost else 0L
        val totalSolCoinsEarned = status.spec.solCoinReward + duplicatePetCoins

        val profileForStats = profile.copy(equippedPetId = nextEquippedPetId)
        val stats = computeDerivedStats(profileForStats, items, activePetAfter, hardcoreState.value)
        val leveled = applyXpAndLevelUp(profileForStats, status.spec.xpReward, stats)
        val nextShards = (profile.sunshards + status.spec.sunshardReward).coerceAtMost(100_000_000L)
        val nextSolCoins = (profile.solCoins + totalSolCoinsEarned).coerceAtMost(100_000_000L)
        val questChest = ProgressionEngine.buildQuestChest(currentUserId, profile, status.spec)

        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            if (shouldAdoptPet && rewardPet != null) {
                val adoptRes = repository.adoptPet(currentUserId, rewardPet)
                if (adoptRes.isFailure) {
                    _bannerMessage.value = "Could not deliver quest companion — please retry."
                    return@launch
                }
            }
            var savedChest: LootChest? = null
            if (questChest != null) {
                val res = repository.addLootChest(currentUserId, questChest)
                if (res.isFailure) {
                    _bannerMessage.value = "Could not deliver quest chest — please retry."
                    return@launch
                }
                savedChest = res.getOrNull()
            }
            val updates = mutableMapOf<String, Any>(
                "claimedQuestIds" to nextClaimed,
                "sunshards" to nextShards,
                "solCoins" to nextSolCoins,
                "level" to leveled.level,
                "xp" to leveled.xp,
                "xpToNextLevel" to leveled.xpToNextLevel,
                "unspentPoints" to leveled.unspentPoints,
                "maxHp" to leveled.maxHp,
                "currentHp" to leveled.currentHp,
                "heroPower" to leveled.heroPower
            )
            if (autoEquipRewardPet) {
                updates["equippedPetId"] = nextEquippedPetId
            }
            repository.updateHeroProfile(currentUserId, updates).onSuccess {
                syncLeaderboard(profileForStats, leveled.level, leveled.heroPower, profile.bossesDefeated)
                val coinTag = if (totalSolCoinsEarned > 0L) "+${totalSolCoinsEarned} Sol Coins, " else ""
                val petTag = when {
                    shouldAdoptPet && rewardPet != null -> {
                        if (autoEquipRewardPet) " & Recruited + Equipped ${rewardPet.displayName}!"
                        else " & Recruited Companion ${rewardPet.displayName}!"
                    }
                    alreadyOwnsRewardPet && rewardPet != null -> {
                        " (Already own ${rewardPet.displayName} → +${duplicatePetCoins} Bonus Coins)"
                    }
                    else -> ""
                }
                if (savedChest != null) {
                    if (savedChest.id.isNotBlank()) recentlyAwardedChestIds.add(savedChest.id)
                    _lastOpenedChestResult.value = null
                    _pendingChestModal.value = savedChest
                    _bannerMessage.value = "Quest Claimed! ${coinTag}+${status.spec.sunshardReward} Sunshards, +${status.spec.xpReward} XP & Sealed Chest$petTag"
                } else {
                    _bannerMessage.value = "Quest Claimed! ${coinTag}+${status.spec.sunshardReward} Sunshards & +${status.spec.xpReward} XP$petTag"
                }
            }
        }
    }

    fun performPrestigeRebirth() {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        if (_battleState.value.active) {
            _bannerMessage.value = "Finish or retreat from your active battle before ascending."
            return
        }
        val prestigeEval = ProgressionEngine.evaluatePrestigeRequirements(profile)
        if (prestigeEval.isMaxPrestigeReached) {
            _bannerMessage.value = "You have reached the maximum Solar Ascension tier (Prestige ${ProgressionEngine.MAX_PRESTIGE_LEVEL})!"
            return
        }
        if (!prestigeEval.allConditionsMet) {
            val missing = prestigeEval.firstUnmetCondition
            val detail = if (missing != null) {
                "${missing.title} (${missing.currentValue}/${missing.targetValue})"
            } else {
                "complete all Trials of Solar Ascension"
            }
            _bannerMessage.value = "Prestige locked (${prestigeEval.metCount}/${prestigeEval.totalCount} trials met) — next required: $detail."
            return
        }
        val nextPrestige = prestigeEval.nextPrestigeLevel.coerceAtMost(ProgressionEngine.MAX_PRESTIGE_LEVEL)
        val bonusPoints = 2L + nextPrestige
        val remainingSolCoins = (profile.solCoins - prestigeEval.solCoinTributeCost).coerceAtLeast(0L)
        val reborn = profile.copy(
            prestigeLevel = nextPrestige,
            level = 1L,
            xp = 0L,
            xpToNextLevel = 120L,
            might = 5L,
            vigor = 5L,
            endurance = 5L,
            unspentPoints = bonusPoints,
            solCoins = remainingSolCoins,
            currentRealm = 1L,
            realmStage = 1L
        )
        val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
        val stats = computeDerivedStats(reborn, items, equippedPet.value)
        haptics.trigger(GameplayHaptic.SOLAR_CRITICAL)

        viewModelScope.launch {
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "prestigeLevel" to nextPrestige,
                    "level" to 1L,
                    "xp" to 0L,
                    "xpToNextLevel" to 120L,
                    "might" to 5L,
                    "vigor" to 5L,
                    "endurance" to 5L,
                    "unspentPoints" to bonusPoints,
                    "solCoins" to remainingSolCoins,
                    "currentRealm" to 1L,
                    "realmStage" to 1L,
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "currentHp" to stats.computedMaxHp.toLong(),
                    "maxStrideEnergy" to stats.computedMaxEnergy.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            ).onSuccess {
                syncLeaderboard(reborn, 1L, stats.computedHeroPower.toLong(), reborn.bossesDefeated)
                val numeral = ProgressionEngine.prestigeRomanNumeral(nextPrestige.toInt())
                _bannerMessage.value = "SOLAR ASCENSION $numeral! Offered ${prestigeEval.solCoinTributeCost} Sol Coins for permanent +${nextPrestige * 15}% All Stats, +${nextPrestige * 10}% XP/Shards & +${nextPrestige * 3}% Survival!"
            }
        }
    }

    // --- Companion Pets & Sol Coin Pet Shop ---

    fun adoptPet(petType: PetType) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val owned = (petsState.value as? UiState.Success)?.data.orEmpty()
        if (owned.any { it.petType == petType.name || it.id == petType.name }) {
            _bannerMessage.value = "You already own ${petType.displayName}."
            return
        }
        val unlock = PetCatalog.evaluateUnlock(petType, profile)
        if (!unlock.unlocked) {
            _bannerMessage.value = "Locked: ${petType.unlockRequirementSummary} (${unlock.progressLabel})."
            return
        }
        if (profile.solCoins < petType.coinCost) {
            _bannerMessage.value = "Need ${petType.coinCost} Sol Coins to adopt ${petType.displayName}. Win Boss Battles (+10 Coins)."
            return
        }

        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            val adoptRes = repository.adoptPet(currentUserId, petType)
            if (adoptRes.isFailure) {
                _bannerMessage.value = "Could not adopt ${petType.displayName}."
                return@launch
            }
            val nextCoins = (profile.solCoins - petType.coinCost).coerceAtLeast(0L)
            val autoEquip = profile.equippedPetId.isBlank()
            val nextEquippedId = if (autoEquip) petType.name else profile.equippedPetId
            val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
            val nextActivePet = if (autoEquip) {
                OwnedPet(id = petType.name, userId = currentUserId, petType = petType.name)
            } else {
                equippedPet.value
            }
            val updatedProfile = profile.copy(solCoins = nextCoins, equippedPetId = nextEquippedId)
            val stats = computeDerivedStats(updatedProfile, items, nextActivePet)

            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "solCoins" to nextCoins,
                    "equippedPetId" to nextEquippedId,
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "currentHp" to updatedProfile.currentHp.coerceAtMost(stats.computedMaxHp.toLong()),
                    "maxStrideEnergy" to stats.computedMaxEnergy.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            syncLeaderboard(updatedProfile, updatedProfile.level, stats.computedHeroPower.toLong(), updatedProfile.bossesDefeated)
            _bannerMessage.value = if (autoEquip) {
                "Adopted & equipped ${petType.displayName}!"
            } else {
                "Adopted ${petType.displayName}! Equip anytime in the Pets tab."
            }
        }
    }

    fun equipPet(petType: PetType) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val owned = (petsState.value as? UiState.Success)?.data.orEmpty()
        val targetPet = owned.firstOrNull { it.petType == petType.name || it.id == petType.name }
        if (targetPet == null) {
            _bannerMessage.value = "Adopt ${petType.displayName} with Sol Coins first."
            return
        }
        val nextEquippedId = if (profile.equippedPetId == petType.name) "" else petType.name
        val nextActivePet = if (nextEquippedId.isBlank()) null else targetPet
        val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
        val updatedProfile = profile.copy(equippedPetId = nextEquippedId)
        val stats = computeDerivedStats(updatedProfile, items, nextActivePet)

        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "equippedPetId" to nextEquippedId,
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "currentHp" to updatedProfile.currentHp.coerceAtMost(stats.computedMaxHp.toLong()),
                    "maxStrideEnergy" to stats.computedMaxEnergy.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            syncLeaderboard(updatedProfile, updatedProfile.level, stats.computedHeroPower.toLong(), updatedProfile.bossesDefeated)
            _bannerMessage.value = if (nextEquippedId.isNotBlank()) {
                "Equipped ${petType.displayName} (${stats.petBonusSummary})."
            } else {
                "Unequipped companion."
            }
        }
    }

    /**
     * Spend Sol Coins at the Town Pet Store or Companion Sanctuary to feed & level up your equipped pet.
     */
    fun buyPetFood(food: PetFoodSpec) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val activePet = equippedPet.value
        if (activePet == null) {
            _bannerMessage.value = "Adopt and equip a companion pet before buying ${food.name}."
            return
        }
        if (activePet.level >= PetCatalog.MAX_PET_LEVEL) {
            _bannerMessage.value = "${activePet.resolvedType().displayName} is already at max Level ${PetCatalog.MAX_PET_LEVEL}!"
            return
        }
        if (profile.solCoins < food.coinCost) {
            _bannerMessage.value = "Need ${food.coinCost} Sol Coins to feed ${food.name}. Earn Sol Coins from Quests, Bosses, or Sleep!"
            return
        }

        val leveledPet = PetCatalog.applyFoodXp(activePet, food.xpGain)
        val leveledUp = leveledPet.level > activePet.level
        val nextCoins = (profile.solCoins - food.coinCost).coerceAtLeast(0L)
        val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
        val updatedProfile = profile.copy(solCoins = nextCoins)
        val stats = computeDerivedStats(updatedProfile, items, leveledPet)

        haptics.trigger(if (leveledUp) GameplayHaptic.VICTORY_FANFARE else GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            val petDocId = activePet.id.ifBlank { activePet.petType }
            val petRes = repository.updateOwnedPet(
                currentUserId,
                petDocId,
                mapOf(
                    "level" to leveledPet.level,
                    "xp" to leveledPet.xp,
                    "xpToNextLevel" to leveledPet.xpToNextLevel
                )
            )
            if (petRes.isFailure) {
                _bannerMessage.value = "Could not feed ${activePet.resolvedType().displayName}: check connection."
                return@launch
            }
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "solCoins" to nextCoins,
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "maxStrideEnergy" to stats.computedMaxEnergy.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            syncLeaderboard(updatedProfile, updatedProfile.level, stats.computedHeroPower.toLong(), updatedProfile.bossesDefeated)
            publishTownPresenceNow(force = true, overrideProfile = updatedProfile)
            val petName = activePet.resolvedType().displayName
            _bannerMessage.value = if (leveledUp) {
                "Fed $petName ${food.name} (-${food.coinCost} Coins) — LEVEL UP to Lv.${leveledPet.level}!"
            } else {
                "Fed $petName ${food.name} (-${food.coinCost} Coins, +${food.xpGain} Pet XP)!"
            }
        }
    }

    fun buyPetGear(gear: PetGearSpec) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val activePet = equippedPet.value
        if (activePet == null) {
            _bannerMessage.value = "Equip a companion pet before buying ${gear.slot.label}."
            return
        }
        val alreadyEquipped = when (gear.slot) {
            PetGearSlot.WEAPON -> activePet.equippedWeaponId == gear.id
            PetGearSlot.ARMOR -> activePet.equippedArmorId == gear.id
        }
        if (alreadyEquipped) {
            _bannerMessage.value = "${activePet.resolvedType().displayName} already has ${gear.name} equipped."
            return
        }
        if (profile.solCoins < gear.coinCost) {
            _bannerMessage.value = "Need ${gear.coinCost} Sol Coins for ${gear.name}. Win Boss Battles (+10 Coins)."
            return
        }

        val updatedPet = when (gear.slot) {
            PetGearSlot.WEAPON -> activePet.copy(equippedWeaponId = gear.id)
            PetGearSlot.ARMOR -> activePet.copy(equippedArmorId = gear.id)
        }
        val nextCoins = (profile.solCoins - gear.coinCost).coerceAtLeast(0L)
        val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
        val updatedProfile = profile.copy(solCoins = nextCoins)
        val stats = computeDerivedStats(updatedProfile, items, updatedPet)

        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            val petDocId = activePet.id.ifBlank { activePet.petType }
            val fieldName = if (gear.slot == PetGearSlot.WEAPON) "equippedWeaponId" else "equippedArmorId"
            val petRes = repository.updateOwnedPet(currentUserId, petDocId, mapOf(fieldName to gear.id))
            if (petRes.isFailure) {
                _bannerMessage.value = "Could not equip ${gear.name} on companion."
                return@launch
            }
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "solCoins" to nextCoins,
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "maxStrideEnergy" to stats.computedMaxEnergy.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            syncLeaderboard(updatedProfile, updatedProfile.level, stats.computedHeroPower.toLong(), updatedProfile.bossesDefeated)
            _bannerMessage.value = "Equipped ${gear.name} on ${activePet.resolvedType().displayName}!"
        }
    }

    // --- Turn-Based Combat Actions (10-Realm Campaign, Endless Spire, Daily Rift, Boss Raid) ---

    fun engageEncounter(forceBoss: Boolean = false) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        if (_battleState.value.active) {
            _activeTab.value = SolstrideTab.BATTLE
            return
        }
        val hc = hardcoreState.value
        val mode = _battleMode.value
        val isBossOrEliteMode = forceBoss ||
            mode == BattleMode.WEEKLY_BOSS_RAID ||
            mode == BattleMode.ENDLESS_SPIRE ||
            mode == BattleMode.DAILY_ELEMENTAL_RIFT ||
            profile.encounterProgress >= 100L

        // Dual-pool real-world energy economy:
        // Bosses/Spire/Rifts draw 10 Sleep Stamina (or 15 Step Battle Energy if Stamina < 10).
        // Regular skirmishes draw 8 Step Battle Energy (or 6 Sleep Stamina if Battle Energy < 8).
        var nextEnergy = profile.strideEnergy
        var nextStamina = hc.heroStamina
        if (isBossOrEliteMode) {
            if (hc.heroStamina >= 10) {
                nextStamina = (hc.heroStamina - 10).coerceAtLeast(0)
            } else if (profile.strideEnergy >= 15L) {
                nextEnergy = (profile.strideEnergy - 15L).coerceAtLeast(0L)
            } else {
                _bannerMessage.value = "Need 10 Sleep Stamina (or 15 Step Battle Energy) for Boss/Spire encounters! Walk real steps or rest in the Sleep Sanctuary."
                return
            }
        } else {
            if (profile.strideEnergy >= 8L) {
                nextEnergy = (profile.strideEnergy - 8L).coerceAtLeast(0L)
            } else if (hc.heroStamina >= 6) {
                nextStamina = (hc.heroStamina - 6).coerceAtLeast(0)
            } else {
                _bannerMessage.value = "Out of Step Battle Energy & Sleep Stamina! Walk real steps or attune rest in the Sleep Sanctuary to fight."
                return
            }
        }

        val battleReadyProfile = profile.copy(strideEnergy = nextEnergy)
        val spawned = CombatEngine.spawnEncounter(
            profile = battleReadyProfile,
            mode = mode,
            forceBoss = forceBoss,
            hardcoreState = hc,
            dateKey = currentCalendarDateKey()
        )
        _battleState.value = spawned
        _activeTab.value = SolstrideTab.BATTLE
        haptics.trigger(GameplayHaptic.ENCOUNTER_ALERT)

        viewModelScope.launch {
            if (nextStamina != hc.heroStamina) {
                preferences.updateHardcoreRpgState { it.copy(heroStamina = nextStamina) }
            }
            val nextEncounter = if (profile.encounterProgress >= 100L) 0L else profile.encounterProgress
            repository.updateHeroProfile(
                currentUserId,
                mapOf("strideEnergy" to nextEnergy, "encounterProgress" to nextEncounter)
            )
            spawned.enemy?.let { watchBridge.sendEncounterHapticToWatch(it.name) }
        }
    }

    fun performStrike() = runBattleTurn { battle, profile, stats ->
        haptics.trigger(GameplayHaptic.PLAYER_STRIKE)
        CombatEngine.executeStrike(battle, profile, stats)
    }

    fun performSolarSkill() = runBattleTurn { battle, profile, stats ->
        haptics.trigger(GameplayHaptic.SOLAR_CRITICAL)
        CombatEngine.executeSolarSkill(battle, profile, stats)
    }

    fun performClassActiveSkill(skillIndex: Int) = runBattleTurn { battle, profile, stats ->
        haptics.trigger(GameplayHaptic.SOLAR_CRITICAL)
        CombatEngine.executeClassActiveSkill(skillIndex, battle, profile, stats)
    }

    fun performUltimateAndPetCombo() = runBattleTurn { battle, profile, stats ->
        haptics.trigger(GameplayHaptic.SOLAR_CRITICAL)
        CombatEngine.executeUltimateAndPetCombo(battle, profile, stats, equippedPet.value)
    }

    fun performPetEvolutionBurst() = runBattleTurn { battle, profile, stats ->
        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        CombatEngine.executePetEvolutionBurst(battle, profile, stats, equippedPet.value)
    }

    fun performGuard() = runBattleTurn { battle, profile, stats ->
        haptics.trigger(GameplayHaptic.GUARD_BLOCK)
        CombatEngine.executeGuard(battle, profile, stats)
    }

    fun performBattleElixir() {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val items = (inventoryState.value as? UiState.Success)?.data ?: return
        val elixir = items.firstOrNull { it.resolvedSlot() == ItemSlot.CONSUMABLE && it.quantity > 0L }
        if (elixir == null) {
            _bannerMessage.value = "No elixirs left in inventory — brew more at the Alchemist or open Chests."
            return
        }
        runBattleTurn { battle, prof, stats ->
            haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
            CombatEngine.executeElixir(battle, prof, stats, elixir)
        }
    }

    fun performRetreat() {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val battle = _battleState.value
        if (!battle.active) return
        parryTimerJob?.cancel()
        _parryRingProgress.value = 0f
        val res = CombatEngine.executeRetreat(battle, profile)
        _battleState.value = res.nextBattle
        haptics.trigger(GameplayHaptic.GUARD_BLOCK)
        val forfeited = res.nextBattle.outcome?.solCoinsLost ?: 0L
        if (forfeited > 0L) {
            viewModelScope.launch {
                val nextCoins = (profile.solCoins - forfeited).coerceAtLeast(0L)
                repository.updateHeroProfile(currentUserId, mapOf("solCoins" to nextCoins))
                _bannerMessage.value = "Retreated! Forfeited -$forfeited Sol Coins from your Blood-Oath Wager."
            }
        }
    }

    fun dismissBattleOutcome() {
        parryTimerJob?.cancel()
        _parryRingProgress.value = 0f
        _battleState.value = BattleState()
    }

    fun triggerParryDodge() {
        val battle = _battleState.value
        if (!battle.active || !battle.pendingEnemyTelegraph) return
        val progress = _parryRingProgress.value
        val relaxed = hardcoreState.value.relaxedTimingAssist
        val perfectMin = if (relaxed) 0.50f else 0.62f
        val perfectMax = if (relaxed) 0.94f else 0.89f
        val glancingMin = if (relaxed) 0.30f else 0.40f
        val grade = when {
            progress in perfectMin..perfectMax -> ParryGrade.PERFECT
            progress in glancingMin..0.97f -> ParryGrade.GLANCING
            else -> ParryGrade.MISSED
        }
        resolveTelegraphWithGrade(grade)
    }

    private fun startParryTelegraphTimer() {
        parryTimerJob?.cancel()
        val durationMs = if (hardcoreState.value.relaxedTimingAssist) 1650L else 1150L
        parryTimerJob = viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            while (isActive && _battleState.value.active && _battleState.value.pendingEnemyTelegraph) {
                val elapsed = System.currentTimeMillis() - startMs
                val p = (elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                _parryRingProgress.value = p
                if (p >= 1f) {
                    resolveTelegraphWithGrade(ParryGrade.MISSED)
                    return@launch
                }
                delay(16L)
            }
            _parryRingProgress.value = 0f
        }
    }

    private fun resolveTelegraphWithGrade(grade: ParryGrade) {
        parryTimerJob?.cancel()
        _parryRingProgress.value = 0f
        val battle = _battleState.value
        if (!battle.active || !battle.pendingEnemyTelegraph) return
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        val items = (inventoryState.value as? UiState.Success)?.data ?: emptyList()
        val hcSnapshot = hardcoreState.value
        val stats = computeDerivedStats(profile, items, equippedPet.value, hcSnapshot)

        when (grade) {
            ParryGrade.PERFECT -> haptics.trigger(GameplayHaptic.SOLAR_CRITICAL)
            ParryGrade.GLANCING -> haptics.trigger(GameplayHaptic.GUARD_BLOCK)
            ParryGrade.MISSED -> haptics.trigger(GameplayHaptic.DAMAGE_TAKEN)
        }
        val resolution = CombatEngine.resolveActiveTelegraph(battle, profile, stats, grade)
        finalizeBattleResolution(resolution, profile, items, hcSnapshot)
    }

    private fun runBattleTurn(
        reducer: (BattleState, HeroProfile, DerivedHeroStats) -> com.example.domain.TurnResolution
    ) {
        val battle = _battleState.value
        if (!battle.active) return
        if (battle.pendingEnemyTelegraph) {
            // Player tapped an action while telegraph is active — let them tap Dodge/Parry first
            return
        }
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val items = (inventoryState.value as? UiState.Success)?.data ?: emptyList()
        val hcSnapshot = hardcoreState.value
        val stats = computeDerivedStats(profile, items, equippedPet.value, hcSnapshot)

        val resolution = reducer(battle, profile, stats)
        finalizeBattleResolution(resolution, profile, items, hcSnapshot)
    }

    private fun finalizeBattleResolution(
        resolution: com.example.domain.TurnResolution,
        profile: HeroProfile,
        items: List<InventoryItem>,
        hcSnapshot: HardcoreRpgState
    ) {
        _battleState.value = resolution.nextBattle

        if (resolution.nextBattle.pendingEnemyTelegraph && resolution.nextBattle.outcome == null) {
            startParryTelegraphTimer()
        }

        if (resolution.tookDamage && !resolution.guardedHit) {
            haptics.trigger(GameplayHaptic.DAMAGE_TAKEN)
        }

        viewModelScope.launch {
            if (resolution.consumedElixirId != null) {
                consumeElixirItem(resolution.consumedElixirId, items)
            }

            val outcome = resolution.nextBattle.outcome
            if (outcome != null) {
                if (outcome.victory) {
                    haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
                }

                // Update Hardcore & RPG state (materials, runes, bestiary, wounds, durability, mercenary/shrine charges)
                var updatedHc = hcSnapshot
                preferences.updateHardcoreRpgState { hc ->
                    val nextRunes = hc.unlockedRunesMap.toMutableMap()
                    outcome.droppedRune?.let { rune ->
                        nextRunes[rune.name] = (nextRunes[rune.name] ?: 0) + 1
                    }
                    val nextBestiary = hc.bestiaryDefeatsMap.toMutableMap()
                    outcome.bestiaryBossKey?.let { key ->
                        nextBestiary[key] = (nextBestiary[key] ?: 0) + 1
                    }
                    val nextDurability = hc.itemDurabilityMap.toMutableMap()
                    if (outcome.gearDurabilityLossPct > 0) {
                        items.filter { it.equipped && it.resolvedSlot().isEquipment }.forEach { eq ->
                            val cur = nextDurability[eq.id] ?: 100
                            nextDurability[eq.id] = (cur - outcome.gearDurabilityLossPct).coerceAtLeast(0)
                        }
                    }
                    val nextWounds = if (outcome.woundInflicted) {
                        (hc.battleWoundStacks + 1).coerceAtMost(3)
                    } else {
                        hc.battleWoundStacks
                    }
                    val nextMercLeft = (hc.mercenaryBattlesLeft - 1).coerceAtLeast(0)
                    val nextShrineLeft = (hc.shrineBattlesLeft - 1).coerceAtLeast(0)
                    val nextRestedLeft = (hc.restedClarityBattlesLeft - 1).coerceAtLeast(0)
                    val nextLanternLeft = (hc.fogLanternCharges - 1).coerceAtLeast(0)

                    val nextMasteryMap = hc.activityMasteryXpMap.toMutableMap()
                    if (outcome.victory) {
                        val combatXpGain = if (outcome.wasBossVictory) 42 else 18
                        nextMasteryMap[ActivityMasteryType.COMBAT.id] =
                            (nextMasteryMap[ActivityMasteryType.COMBAT.id] ?: 0) + combatXpGain
                    }
                    val nextRelics = hc.collectedRelicIds.toMutableSet()
                    if (outcome.victory && (outcome.wasBossVictory || resolution.nextBattle.isDungeonBossFight) && Random.nextInt(100) < 35) {
                        RelicSetSpec.nextUncollectedRelic(nextRelics)?.let { (_, pieceId) ->
                            nextRelics.add(pieceId)
                        }
                    }

                    val nextSlayerKills = hc.slayerContractKillsMap.toMutableMap()
                    if (outcome.victory) {
                        val isBossOrDungeonKill = outcome.wasBossVictory || resolution.nextBattle.isDungeonBossFight
                        SlayerContractCatalog.contracts.forEach { contract ->
                            if (!contract.requiresBossOrDungeon || isBossOrDungeonKill) {
                                val prev = nextSlayerKills[contract.id] ?: 0
                                nextSlayerKills[contract.id] = (prev + 1).coerceAtMost(9_999)
                            }
                        }
                    }

                    val clearedDungeonBoss = outcome.victory && resolution.nextBattle.isDungeonBossFight
                    val nextDungeonClears = if (clearedDungeonBoss) hc.dungeonClearsCount + 1 else hc.dungeonClearsCount
                    val nextDungeonRoomIdx = if (clearedDungeonBoss) 0 else hc.activeDungeonRoomIndex
                    val nextDungeonRoomSteps = if (clearedDungeonBoss) 0 else hc.activeDungeonRoomSteps
                    val nextDreamShards = if (clearedDungeonBoss) (hc.dreamShards + 1L).coerceAtMost(99_999L) else hc.dreamShards

                    hc.copy(
                        sunsteelOre = (hc.sunsteelOre + outcome.oreEarned).coerceAtMost(999_999L),
                        aetherHerbs = (hc.aetherHerbs + outcome.herbsEarned).coerceAtMost(999_999L),
                        elementalEssence = (hc.elementalEssence + outcome.essenceEarned).coerceAtMost(999_999L),
                        battleWoundStacks = nextWounds,
                        mercenaryBattlesLeft = nextMercLeft,
                        mercenaryContractId = if (nextMercLeft == 0) "" else hc.mercenaryContractId,
                        shrineBattlesLeft = nextShrineLeft,
                        shrineBlessingLabel = if (nextShrineLeft == 0) "" else hc.shrineBlessingLabel,
                        restedClarityBattlesLeft = nextRestedLeft,
                        restedClarityPct = if (nextRestedLeft == 0) 0 else hc.restedClarityPct,
                        fogLanternCharges = nextLanternLeft,
                        itemDurabilityMap = nextDurability,
                        bestiaryDefeatsMap = nextBestiary,
                        unlockedRunesMap = nextRunes,
                        activityMasteryXpMap = nextMasteryMap,
                        collectedRelicIds = nextRelics,
                        slayerContractKillsMap = nextSlayerKills,
                        dungeonClearsCount = nextDungeonClears,
                        activeDungeonRoomIndex = nextDungeonRoomIdx,
                        activeDungeonRoomSteps = nextDungeonRoomSteps,
                        dreamShards = nextDreamShards
                    ).also { updatedHc = it }
                }

                val postStats = computeDerivedStats(profile, items, equippedPet.value, updatedHc)
                val leveled = applyXpAndLevelUp(
                    profile.copy(
                        currentHp = resolution.updatedHp.coerceAtMost(postStats.computedMaxHp.toLong()),
                        strideEnergy = resolution.updatedEnergy
                    ),
                    outcome.xpEarned,
                    postStats
                )
                val activeEvent = activeWorldEvent.value.first
                val bonusEventCoins = if (outcome.victory && outcome.wasBossVictory) {
                    (outcome.solCoinsEarned * activeEvent.bossCoinBonusPct / 100L)
                } else 0L
                val newShards = (profile.sunshards + outcome.sunshardsEarned).coerceAtMost(100_000_000L)
                val newSolCoins = (profile.solCoins + outcome.solCoinsEarned + bonusEventCoins - outcome.solCoinsLost)
                    .coerceIn(0L, 100_000_000L)
                val newBosses = if (outcome.victory) profile.bossesDefeated + 1L else profile.bossesDefeated
                val newTrueBossWins = if (outcome.wasBossVictory) profile.trueBossWins + 1L else profile.trueBossWins
                val evoShardGain = if (outcome.victory && outcome.wasBossVictory) {
                    if (activeEvent == LiveWorldEventSpec.SOLAR_ECLIPSE_SURGE) 2L else 1L
                } else 0L
                val seasonBattleXp = if (outcome.victory) (if (outcome.wasBossVictory) 80L else 35L) else 10L
                val newEvoShards = (profile.evolutionShards + evoShardGain).coerceAtMost(99_999L)
                val newSeasonXp = (profile.seasonXp + seasonBattleXp).coerceAtMost(999_999L)

                // Compute Realm Campaign / Endless Spire progression updates
                var nextRealm = profile.currentRealm
                var nextStage = profile.realmStage
                var nextHighestRealm = profile.highestRealmCleared
                var nextSpire = profile.currentSpireFloor
                var nextHighestSpire = profile.highestSpireFloor

                if (outcome.victory) {
                    when (outcome.mode) {
                        BattleMode.ENDLESS_SPIRE -> {
                            if (outcome.spireFloor.toLong() >= profile.currentSpireFloor) {
                                nextSpire = (outcome.spireFloor.toLong() + 1L).coerceAtMost(9999L)
                            }
                            nextHighestSpire = max(profile.highestSpireFloor, outcome.spireFloor.toLong()).coerceAtMost(9999L)
                        }
                        BattleMode.REALM_CAMPAIGN -> {
                            if (outcome.stageNumber >= 5) {
                                nextHighestRealm = max(profile.highestRealmCleared, outcome.realmNumber.toLong())
                                    .coerceAtMost(ProgressionEngine.MAX_REALM)
                                if (outcome.realmNumber.toLong() < ProgressionEngine.MAX_REALM && outcome.realmNumber.toLong() == profile.currentRealm) {
                                    nextRealm = (profile.currentRealm + 1L).coerceAtMost(ProgressionEngine.MAX_REALM)
                                    nextStage = 1L
                                } else {
                                    nextStage = 5L
                                }
                            } else if (outcome.realmNumber.toLong() == profile.currentRealm && outcome.stageNumber.toLong() >= profile.realmStage) {
                                nextStage = (outcome.stageNumber.toLong() + 1L).coerceAtMost(5L)
                            }
                        }
                        BattleMode.DAILY_ELEMENTAL_RIFT,
                        BattleMode.WEEKLY_BOSS_RAID -> Unit
                    }
                }

                val todayKey = currentCalendarDateKey()
                val sameDay = profile.dailyQuestDateKey == todayKey
                val nextDailyBattles = if (outcome.victory) {
                    (if (sameDay) profile.dailyBattlesWon else 0L) + 1L
                } else {
                    if (sameDay) profile.dailyBattlesWon else 0L
                }

                val updates = mutableMapOf<String, Any>(
                    "currentHp" to leveled.currentHp,
                    "maxHp" to leveled.maxHp,
                    "strideEnergy" to resolution.updatedEnergy,
                    "level" to leveled.level,
                    "xp" to leveled.xp,
                    "xpToNextLevel" to leveled.xpToNextLevel,
                    "unspentPoints" to leveled.unspentPoints,
                    "sunshards" to newShards,
                    "solCoins" to newSolCoins,
                    "bossesDefeated" to newBosses,
                    "trueBossWins" to newTrueBossWins,
                    "heroPower" to leveled.heroPower,
                    "currentRealm" to nextRealm,
                    "realmStage" to nextStage,
                    "highestRealmCleared" to nextHighestRealm,
                    "currentSpireFloor" to nextSpire,
                    "highestSpireFloor" to nextHighestSpire,
                    "dailyQuestDateKey" to todayKey,
                    "dailyBattlesWon" to nextDailyBattles,
                    "evolutionShards" to newEvoShards,
                    "seasonXp" to newSeasonXp
                )
                if (!sameDay) {
                    updates["dailyExpeditionsDone"] = 0L
                    updates["dailyChestsOpened"] = 0L
                }

                repository.updateHeroProfile(currentUserId, updates)
                repository.upsertLeaderboard(
                    userId = currentUserId,
                    displayName = profile.displayName,
                    heroClass = profile.heroClass,
                    heroLevel = leveled.level,
                    heroPower = leveled.heroPower,
                    weeklySteps = profile.weeklySteps,
                    weekKey = profile.weekKey,
                    bossesDefeated = newBosses,
                    prestigeLevel = profile.prestigeLevel,
                    highestSpireFloor = nextHighestSpire
                )

                // Persist sealed LootChest and open tap-to-loot modal!
                outcome.droppedChest?.let { dropChest ->
                    repository.addLootChest(currentUserId, dropChest).onSuccess { saved ->
                        if (saved.id.isNotBlank()) recentlyAwardedChestIds.add(saved.id)
                        _battleState.value = _battleState.value.copy(
                            outcome = _battleState.value.outcome?.copy(droppedChest = saved)
                        )
                        _lastOpenedChestResult.value = null
                        _pendingChestModal.value = saved
                    }
                }

                if (outcome.victory && outcome.solCoinsEarned > 0L) {
                    val runeNote = outcome.droppedRune?.let { " + ${it.label} Rune!" } ?: ""
                    _bannerMessage.value = "Victory! +${outcome.solCoinsEarned} Sol Coins, +${outcome.oreEarned} Ore$runeNote"
                } else if (!outcome.victory) {
                    _bannerMessage.value = "Defeat! -${outcome.solCoinsLost} Sol Coins, +1 Battle Wound & -${outcome.gearDurabilityLossPct}% Gear Durability."
                }
            } else {
                repository.updateHeroProfile(
                    currentUserId,
                    mapOf(
                        "currentHp" to resolution.updatedHp,
                        "strideEnergy" to resolution.updatedEnergy
                    )
                )
            }
        }
    }

    // --- 10-Realm World Map Travel, Wayfarer Events & Hardcore Rules ---

    fun selectRealm(realmNumber: Int) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        if (_battleState.value.active) {
            _bannerMessage.value = "Finish or retreat from your active battle before traveling realms."
            return
        }
        val maxUnlocked = (profile.highestRealmCleared + 1L).coerceAtMost(ProgressionEngine.MAX_REALM).toInt()
        if (realmNumber > maxUnlocked) {
            val prevRealm = ProgressionEngine.realmForNumber(realmNumber - 1)
            _bannerMessage.value = "Locked! Defeat ${prevRealm.bossName} in Realm ${realmNumber - 1} first."
            return
        }
        val targetRealm = realmNumber.coerceIn(1, ProgressionEngine.MAX_REALM.toInt())
        val spec = ProgressionEngine.realmForNumber(targetRealm)
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            repository.updateHeroProfile(
                currentUserId,
                mapOf("currentRealm" to targetRealm.toLong(), "realmStage" to 1L)
            )
            _bannerMessage.value = "Traveled to Realm $targetRealm: ${spec.title} (${spec.affinity.label})."
        }
    }

    fun triggerWayfarerEvent(event: WayfarerEventSpec) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        if (profile.solCoins < event.solCoinCost) {
            _bannerMessage.value = "Need ${event.solCoinCost} Sol Coins for ${event.title}."
            return
        }
        if (profile.strideEnergy < event.energyCost) {
            _bannerMessage.value = "Need ${event.energyCost} Stride Energy for ${event.title}. Walk to recharge!"
            return
        }
        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            val nextEnergy = (profile.strideEnergy - event.energyCost).coerceAtLeast(0L)
            var nextCoins = (profile.solCoins - event.solCoinCost).coerceAtLeast(0L)
            var nextShards = profile.sunshards

            when (event.id) {
                "shrine_of_dawn", "shrine_dawn_blessing" -> {
                    preferences.updateHardcoreRpgState { hc ->
                        hc.copy(
                            shrineBlessingLabel = "Dawn Shrine (+15% ATK/DEF)",
                            shrineBattlesLeft = 3
                        )
                    }
                    val stats = derivedStats.value
                    val healGain = ((stats?.computedMaxHp ?: profile.maxHp.toInt()) * 0.35f).toLong().coerceAtLeast(35L)
                    val healedHp = (profile.currentHp + healGain).coerceAtMost((stats?.computedMaxHp?.toLong() ?: profile.maxHp))
                    repository.updateHeroProfile(
                        currentUserId,
                        mapOf(
                            "solCoins" to nextCoins,
                            "strideEnergy" to nextEnergy,
                            "currentHp" to healedHp
                        )
                    )
                    _bannerMessage.value = "Shrine of Dawn blessed you! +$healGain HP & +15% ATK/DEF for 3 battles."
                }
                "sunsteel_vein", "sunsteel_vein_harvest" -> {
                    val oreGain = 18L
                    val herbGain = 14L
                    val essenceGain = 8L
                    preferences.updateHardcoreRpgState { hc ->
                        hc.copy(
                            sunsteelOre = (hc.sunsteelOre + oreGain).coerceAtMost(999_999L),
                            aetherHerbs = (hc.aetherHerbs + herbGain).coerceAtMost(999_999L),
                            elementalEssence = (hc.elementalEssence + essenceGain).coerceAtMost(999_999L)
                        )
                    }
                    repository.updateHeroProfile(
                        currentUserId,
                        mapOf("strideEnergy" to nextEnergy)
                    )
                    _bannerMessage.value = "Excavated Vein! +$oreGain Sunsteel Ore, +$herbGain Aether Herbs & +$essenceGain Essence!"
                }
                "blood_oath_altar", "obsidian_gamble_altar" -> {
                    val roll = Random.nextInt(100)
                    if (roll < 60) {
                        val winCoins = 35L
                        val rune = ElementalAffinity.entries.random()
                        nextCoins = (nextCoins + winCoins).coerceAtMost(100_000_000L)
                        preferences.updateHardcoreRpgState { hc ->
                            val nextRunes = hc.unlockedRunesMap.toMutableMap()
                            nextRunes[rune.name] = (nextRunes[rune.name] ?: 0) + 1
                            hc.copy(unlockedRunesMap = nextRunes)
                        }
                        repository.updateHeroProfile(
                            currentUserId,
                            mapOf("solCoins" to nextCoins, "strideEnergy" to nextEnergy)
                        )
                        _bannerMessage.value = "Altar Jackpot! Won +$winCoins Sol Coins & a ${rune.label} Rune!"
                    } else {
                        nextShards = (nextShards + 40L).coerceAtMost(100_000_000L)
                        val bruisedHp = (profile.currentHp - (profile.maxHp * 0.20f).toLong()).coerceAtLeast(15L)
                        preferences.updateHardcoreRpgState { hc ->
                            hc.copy(elementalEssence = (hc.elementalEssence + 3L).coerceAtMost(999_999L))
                        }
                        repository.updateHeroProfile(
                            currentUserId,
                            mapOf(
                                "solCoins" to nextCoins,
                                "sunshards" to nextShards,
                                "strideEnergy" to nextEnergy,
                                "currentHp" to bruisedHp
                            )
                        )
                        _bannerMessage.value = "Altar Backlash (-20% HP): Claimed +40 Sunshards & +3 Elemental Essence."
                    }
                }
            }
        }
    }

    fun toggleHardcoreOath() {
        if (_battleState.value.active) {
            _bannerMessage.value = "Finish or retreat from your active battle before toggling Ironstride Hardcore Oath."
            return
        }
        val next = !hardcoreState.value.hardcoreOathEnabled
        haptics.trigger(if (next) GameplayHaptic.SOLAR_CRITICAL else GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            preferences.updateHardcoreRpgState { hc -> hc.copy(hardcoreOathEnabled = next) }
            _bannerMessage.value = if (next) {
                "IRONSTRIDE HARDCORE OATH ACTIVE: +35% Enemy Power & Boss Doom Timer, +60% Sol Coins, +50% XP & 2x Rune/Mythic Drop Odds!"
            } else {
                "Ironstride Hardcore Oath disabled — standard RPG difficulty restored."
            }
        }
    }

    fun selectSolCoinWager(wager: Long) {
        if (_battleState.value.active) {
            _bannerMessage.value = "Cannot change Sol Coin wager during an active battle."
            return
        }
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (wager > profile.solCoins) {
            _bannerMessage.value = "You need at least $wager Sol Coins to stake that Blood-Oath Wager."
            return
        }
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            preferences.updateHardcoreRpgState { hc -> hc.copy(activeSolCoinWager = wager) }
            _bannerMessage.value = if (wager > 0L) {
                "Staked $wager Sol Coins per battle! Win 2x wager profit on victory, or lose $wager Coins on defeat/retreat."
            } else {
                "Cleared Blood-Oath Sol Coin wager."
            }
        }
    }

    fun selectClassSpecialization(spec: ClassSpecialization) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        if (!spec.isUnlockedFor(profile)) {
            _bannerMessage.value = "Reach Hero Level ${spec.unlockLevel} to unlock ${spec.tierRoman} (${spec.tierName}): ${spec.title}."
            return
        }
        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            var nextHc = hardcoreState.value
            preferences.updateHardcoreRpgState { hc ->
                hc.copy(specializationId = spec.name).also { nextHc = it }
            }
            val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
            val updatedProfile = profile.copy(heroClass = spec.baseClass.name)
            val stats = computeDerivedStats(updatedProfile, items, equippedPet.value, nextHc)
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "heroClass" to spec.baseClass.name,
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "currentHp" to updatedProfile.currentHp.coerceAtMost(stats.computedMaxHp.toLong()),
                    "maxStrideEnergy" to stats.computedMaxEnergy.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            syncLeaderboard(updatedProfile, updatedProfile.level, stats.computedHeroPower.toLong(), updatedProfile.bossesDefeated)
            _bannerMessage.value = "Ascended to [${spec.tierRoman}] ${spec.title}! Ultimate unlocked: ${spec.ultimateName} (${spec.affinity.label})."
        }
    }

    fun selectHeroTitle(title: HeroTitleSpec) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        val hc = hardcoreState.value
        val unlocked = title.isUnlocked(profile, hc.codexTotalKills())
        if (!unlocked) {
            _bannerMessage.value = "Title Locked: ${title.requirementLabel}."
            return
        }
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            var nextHc = hc
            preferences.updateHardcoreRpgState { cur ->
                cur.copy(equippedTitleId = title.id).also { nextHc = it }
            }
            val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
            val stats = computeDerivedStats(profile, items, equippedPet.value, nextHc)
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            _bannerMessage.value = "Equipped Title: ${title.title} (${title.bonusSummary})!"
        }
    }

    // --- Hero Progression & Inventory Management ---

    fun allocateMasteryPoint(attribute: MasteryAttribute) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val items = (inventoryState.value as? UiState.Success)?.data ?: emptyList()
        if (profile.unspentPoints <= 0L) {
            _bannerMessage.value = "Level up through steps and battles to earn Mastery Points."
            return
        }
        val updated = when (attribute) {
            MasteryAttribute.MIGHT -> profile.copy(might = profile.might + 1L, unspentPoints = profile.unspentPoints - 1L)
            MasteryAttribute.VIGOR -> profile.copy(vigor = profile.vigor + 1L, unspentPoints = profile.unspentPoints - 1L)
            MasteryAttribute.ENDURANCE -> profile.copy(endurance = profile.endurance + 1L, unspentPoints = profile.unspentPoints - 1L)
        }
        val stats = computeDerivedStats(updated, items, equippedPet.value, hardcoreState.value)
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)

        viewModelScope.launch {
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "might" to updated.might,
                    "vigor" to updated.vigor,
                    "endurance" to updated.endurance,
                    "unspentPoints" to updated.unspentPoints,
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "currentHp" to updated.currentHp.coerceAtMost(stats.computedMaxHp.toLong()),
                    "maxStrideEnergy" to stats.computedMaxEnergy.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            syncLeaderboard(updated, updated.level, stats.computedHeroPower.toLong(), updated.bossesDefeated)
        }
    }

    fun selectHeroClass(heroClass: HeroClass) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val items = (inventoryState.value as? UiState.Success)?.data ?: emptyList()
        val updated = profile.copy(heroClass = heroClass.name)
        val defaultSpec = ClassSpecialization.forClass(heroClass).first()
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)

        viewModelScope.launch {
            var nextHc = hardcoreState.value
            if (ClassSpecialization.fromName(nextHc.specializationId, heroClass).baseClass != heroClass) {
                preferences.updateHardcoreRpgState { hc ->
                    hc.copy(specializationId = defaultSpec.name).also { nextHc = it }
                }
            }
            val stats = computeDerivedStats(updated, items, equippedPet.value, nextHc)
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "heroClass" to heroClass.name,
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            syncLeaderboard(updated, updated.level, stats.computedHeroPower.toLong(), updated.bossesDefeated)
        }
    }

    // Camp Heal uses Sol Coins (5 Sol Coins for full HP restore)
    fun restAtSunlitCamp() {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val stats = derivedStats.value ?: return
        if (profile.currentHp >= stats.computedMaxHp.toLong()) {
            _bannerMessage.value = "Hero HP is already full."
            return
        }
        val cost = ExpeditionEngine.CAMP_HEAL_SOL_COIN_COST
        if (profile.solCoins < cost) {
            _bannerMessage.value = "Need $cost Sol Coins for Camp Heal. Win a Boss Battle to earn +10 Sol Coins."
            return
        }
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "currentHp" to stats.computedMaxHp.toLong(),
                    "solCoins" to (profile.solCoins - cost).coerceAtLeast(0L)
                )
            )
            _bannerMessage.value = "Restored full HP at Sunlit Camp (-$cost Sol Coins)."
        }
    }

    fun toggleEquipItem(item: InventoryItem) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val items = (inventoryState.value as? UiState.Success)?.data ?: return
        if (item.resolvedSlot() == ItemSlot.CONSUMABLE) {
            useConsumableOutOfCombat(item)
            return
        }
        equipItemDirect(profile, items, item, toggle = true)
    }

    private fun equipItemDirect(
        profile: HeroProfile,
        items: List<InventoryItem>,
        item: InventoryItem,
        toggle: Boolean = false
    ) {
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            val targetEquip = if (toggle) !item.equipped else true
            if (targetEquip) {
                items.filter { it.slot == item.slot && it.equipped && it.id != item.id }
                    .forEach { prev ->
                        repository.updateInventoryItem(currentUserId, prev.id, mapOf("equipped" to false))
                    }
            }
            repository.updateInventoryItem(currentUserId, item.id, mapOf("equipped" to targetEquip))

            val projected = items.map {
                when {
                    it.id == item.id -> it.copy(equipped = targetEquip)
                    targetEquip && it.slot == item.slot -> it.copy(equipped = false)
                    else -> it
                }
            }
            val stats = computeDerivedStats(profile, projected, equippedPet.value, hardcoreState.value)
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "currentHp" to profile.currentHp.coerceAtMost(stats.computedMaxHp.toLong()),
                    "maxStrideEnergy" to stats.computedMaxEnergy.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            syncLeaderboard(profile, profile.level, stats.computedHeroPower.toLong(), profile.bossesDefeated)
            if (!toggle) {
                _bannerMessage.value = "Equipped ${item.name}!"
            }
        }
    }

    // --- Blacksmith Forge: Custom Weapon/Armor Forging, Tempering, Repair, Perk Reroll, Ascension & Runes ---

    fun forgeCustomEquipment(
        slot: ItemSlot,
        tier: ForgeBlueprintTier,
        chosenElement: ElementalAffinity? = null,
        useStarforgedIngot: Boolean = false
    ) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        if (!slot.isEquipment) return
        if (profile.level < tier.minHeroLevel.toLong()) {
            _bannerMessage.value = "${tier.label} Blueprint unlocks at Hero Level ${tier.minHeroLevel}."
            return
        }
        val hc = hardcoreState.value
        if (useStarforgedIngot && hc.starforgedIngots <= 0L) {
            _bannerMessage.value = "No Starforged Masterwork Ingots owned. Buy one from the Rare Materials Market in Sunhaven Plaza!"
            return
        }
        val essenceCost = tier.essenceCost + if (chosenElement != null) 4L else 0L
        if (hc.sunsteelOre < tier.oreCost ||
            hc.aetherHerbs < tier.herbCost ||
            hc.elementalEssence < essenceCost ||
            profile.sunshards < tier.sunshardCost
        ) {
            _bannerMessage.value = "Need ${tier.oreCost} Ore, ${tier.herbCost} Herbs, $essenceCost Essence & ${tier.sunshardCost} Sunshards to forge ${tier.label} ${slot.label}."
            return
        }

        val forged = ExpeditionEngine.forgeCustomEquipment(
            userId = currentUserId,
            slot = slot,
            tier = tier,
            heroLevel = profile.level.toInt(),
            chosenElement = chosenElement,
            guaranteedMasterwork = useStarforgedIngot
        )

        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            val addRes = repository.addInventoryItem(currentUserId, forged)
            if (addRes.isFailure) {
                _bannerMessage.value = "Forge failed to save item — check connection and try again."
                return@launch
            }
            preferences.updateHardcoreRpgState { cur ->
                cur.copy(
                    sunsteelOre = (cur.sunsteelOre - tier.oreCost).coerceAtLeast(0L),
                    aetherHerbs = (cur.aetherHerbs - tier.herbCost).coerceAtLeast(0L),
                    elementalEssence = (cur.elementalEssence - essenceCost).coerceAtLeast(0L),
                    starforgedIngots = if (useStarforgedIngot) (cur.starforgedIngots - 1L).coerceAtLeast(0L) else cur.starforgedIngots
                )
            }
            repository.updateHeroProfile(
                currentUserId,
                mapOf("sunshards" to (profile.sunshards - tier.sunshardCost).coerceAtLeast(0L))
            )
            val critNote = when {
                useStarforgedIngot -> " ★ STARFORGED MASTERWORK!"
                forged.resolvedRarity() != tier.baseRarity -> " ★ MASTERWORK CRIT!"
                else -> ""
            }
            _bannerMessage.value = "🔨 Forged ${forged.name} (${forged.resolvedRarity().label} ${slot.label})$critNote — Equip in Gear Bag!"
        }
    }

    fun upgradeItem(item: InventoryItem, useStabilizer: Boolean = false) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val items = (inventoryState.value as? UiState.Success)?.data ?: return
        if (item.resolvedSlot() == ItemSlot.CONSUMABLE) return
        if (item.upgradeLevel >= 15L) {
            _bannerMessage.value = "${item.name} has reached Masterwork Max (+15)!"
            return
        }
        val shardCost = item.upgradeCost()
        val baseCoinCost = item.temperSolCoinCost()
        val stabilizerCost = if (useStabilizer && item.upgradeLevel >= 5L) ExpeditionEngine.FORGE_STABILIZER_SOL_COIN_COST else 0L
        val totalCoinCost = baseCoinCost + stabilizerCost

        if (profile.sunshards < shardCost) {
            _bannerMessage.value = "Need $shardCost Sunshards to temper ${item.name}."
            return
        }
        if (profile.solCoins < totalCoinCost) {
            _bannerMessage.value = "Need $totalCoinCost Sol Coins to temper ${item.name}."
            return
        }

        val chancePct = if (useStabilizer) 100 else item.temperSuccessChancePct()
        val succeeded = Random.nextInt(100) < chancePct

        viewModelScope.launch {
            val nextShards = (profile.sunshards - shardCost).coerceAtLeast(0L)
            val nextCoins = (profile.solCoins - totalCoinCost).coerceAtLeast(0L)

            if (succeeded) {
                val nextLvl = (item.upgradeLevel + 1L).coerceAtMost(15L)
                val nextAtk = if (item.attackBonus > 0L) item.attackBonus + 4L else 0L
                val nextDef = if (item.defenseBonus > 0L) item.defenseBonus + 3L else 0L
                val nextHp = if (item.hpBonus > 0L) item.hpBonus + 10L else 0L
                val nextEnergy = if (item.energyBonus > 0L) item.energyBonus + 5L else 0L
                val (resolvedPerk, resolvedVal) = item.resolvedPerk()
                val nextPerkVal = if (resolvedPerk != GearPerkType.NONE) {
                    (resolvedVal.toLong() + 1L).coerceAtMost(100L)
                } else {
                    item.perkValue
                }

                haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
                val itemUpdates = mutableMapOf<String, Any>(
                    "upgradeLevel" to nextLvl,
                    "attackBonus" to nextAtk,
                    "defenseBonus" to nextDef,
                    "hpBonus" to nextHp,
                    "energyBonus" to nextEnergy
                )
                if (item.perkType != GearPerkType.NONE.name && resolvedPerk != GearPerkType.NONE) {
                    itemUpdates["perkValue"] = nextPerkVal
                }
                repository.updateInventoryItem(currentUserId, item.id, itemUpdates)

                val projected = items.map {
                    if (it.id == item.id) {
                        it.copy(
                            upgradeLevel = nextLvl,
                            attackBonus = nextAtk,
                            defenseBonus = nextDef,
                            hpBonus = nextHp,
                            energyBonus = nextEnergy,
                            perkValue = if (it.perkType != GearPerkType.NONE.name) nextPerkVal else it.perkValue
                        )
                    } else it
                }
                val stats = computeDerivedStats(profile, projected, equippedPet.value, hardcoreState.value)
                repository.updateHeroProfile(
                    currentUserId,
                    mapOf(
                        "sunshards" to nextShards,
                        "solCoins" to nextCoins,
                        "maxHp" to stats.computedMaxHp.toLong(),
                        "maxStrideEnergy" to stats.computedMaxEnergy.toLong(),
                        "heroPower" to stats.computedHeroPower.toLong()
                    )
                )
                syncLeaderboard(profile, profile.level, stats.computedHeroPower.toLong(), profile.bossesDefeated)
                _bannerMessage.value = "Masterwork Temper Success! ${item.name} forged to +$nextLvl!"
            } else {
                haptics.trigger(GameplayHaptic.DAMAGE_TAKEN)
                var nextHc = hardcoreState.value
                preferences.updateHardcoreRpgState { hc ->
                    val map = hc.itemDurabilityMap.toMutableMap()
                    val cur = map[item.id] ?: 100
                    map[item.id] = (cur - 20).coerceAtLeast(10)
                    hc.copy(itemDurabilityMap = map).also { nextHc = it }
                }
                val stats = computeDerivedStats(profile, items, equippedPet.value, nextHc)
                repository.updateHeroProfile(
                    currentUserId,
                    mapOf(
                        "sunshards" to nextShards,
                        "solCoins" to nextCoins,
                        "heroPower" to stats.computedHeroPower.toLong()
                    )
                )
                _bannerMessage.value = "Temper Instability ($chancePct% odds)! ${item.name} lost -20% durability. Use a Stabilizer Seal (+15 Coins) for 100% guarantee!"
            }
        }
    }

    fun repairItem(item: InventoryItem) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        val hc = hardcoreState.value
        val curDur = hc.durabilityFor(item.id)
        if (curDur >= 100) {
            _bannerMessage.value = "${item.name} is already at 100% Durability."
            return
        }
        val coinCost = ExpeditionEngine.REPAIR_ITEM_SOL_COIN_COST
        val oreCost = ExpeditionEngine.REPAIR_ITEM_ORE_COST
        if (profile.solCoins < coinCost || hc.sunsteelOre < oreCost) {
            _bannerMessage.value = "Need $coinCost Sol Coins & $oreCost Sunsteel Ore to repair ${item.name}."
            return
        }
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            var nextHc = hc
            preferences.updateHardcoreRpgState { cur ->
                val map = cur.itemDurabilityMap.toMutableMap()
                map.remove(item.id)
                cur.copy(
                    sunsteelOre = (cur.sunsteelOre - oreCost).coerceAtLeast(0L),
                    itemDurabilityMap = map
                ).also { nextHc = it }
            }
            val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
            val stats = computeDerivedStats(profile, items, equippedPet.value, nextHc)
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "solCoins" to (profile.solCoins - coinCost).coerceAtLeast(0L),
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            _bannerMessage.value = "Repaired ${item.name} to 100% Durability!"
        }
    }

    fun repairAllEquippedGear() {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
        val hc = hardcoreState.value
        val damaged = items.filter { it.equipped && it.resolvedSlot().isEquipment && hc.durabilityFor(it.id) < 100 }
        if (damaged.isEmpty()) {
            _bannerMessage.value = "All equipped gear is already at 100% Durability."
            return
        }
        val coinCost = damaged.size * ExpeditionEngine.REPAIR_ITEM_SOL_COIN_COST
        val oreCost = damaged.size * ExpeditionEngine.REPAIR_ITEM_ORE_COST
        if (profile.solCoins < coinCost || hc.sunsteelOre < oreCost) {
            _bannerMessage.value = "Need $coinCost Sol Coins & $oreCost Sunsteel Ore to repair ${damaged.size} worn items."
            return
        }
        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            var nextHc = hc
            preferences.updateHardcoreRpgState { cur ->
                val map = cur.itemDurabilityMap.toMutableMap()
                damaged.forEach { map.remove(it.id) }
                cur.copy(
                    sunsteelOre = (cur.sunsteelOre - oreCost).coerceAtLeast(0L),
                    itemDurabilityMap = map
                ).also { nextHc = it }
            }
            val stats = computeDerivedStats(profile, items, equippedPet.value, nextHc)
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "solCoins" to (profile.solCoins - coinCost).coerceAtLeast(0L),
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            _bannerMessage.value = "Repaired ${damaged.size} equipped items to 100% Durability (-$coinCost Sol Coins, -$oreCost Ore)!"
        }
    }

    fun rerollItemPerk(item: InventoryItem) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val items = (inventoryState.value as? UiState.Success)?.data ?: return
        if (!item.resolvedSlot().isEquipment) return
        val coinCost = ExpeditionEngine.PERK_REROLL_SOL_COIN_COST
        val shardCost = ExpeditionEngine.PERK_REROLL_SUNSHARD_COST
        if (profile.solCoins < coinCost || profile.sunshards < shardCost) {
            _bannerMessage.value = "Need $coinCost Sol Coins & $shardCost Sunshards to reroll perk on ${item.name}."
            return
        }
        val (newPerk, newVal) = ExpeditionEngine.rerollItemPerk(item)
        val newValLong = newVal.toLong()
        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            repository.updateInventoryItem(
                currentUserId,
                item.id,
                mapOf("perkType" to newPerk.name, "perkValue" to newValLong)
            )
            val projected = items.map {
                if (it.id == item.id) it.copy(perkType = newPerk.name, perkValue = newValLong) else it
            }
            val stats = computeDerivedStats(profile, projected, equippedPet.value, hardcoreState.value)
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "solCoins" to (profile.solCoins - coinCost).coerceAtLeast(0L),
                    "sunshards" to (profile.sunshards - shardCost).coerceAtLeast(0L),
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "maxStrideEnergy" to stats.computedMaxEnergy.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            _bannerMessage.value = "Forged New Perk on ${item.name}: ${newPerk.formatSummary(newVal)}!"
        }
    }

    fun ascendItemRarity(item: InventoryItem) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val items = (inventoryState.value as? UiState.Success)?.data ?: return
        if (!item.resolvedSlot().isEquipment) return
        val curRarity = item.resolvedRarity()
        val nextRarity = curRarity.nextRarityOrNull()
        if (nextRarity == null) {
            _bannerMessage.value = "${item.name} is already at Ultra Rare Mythic Ascension!"
            return
        }
        val hc = hardcoreState.value
        val coinCost = ExpeditionEngine.RARITY_ASCEND_SOL_COIN_COST
        val oreCost = ExpeditionEngine.RARITY_ASCEND_ORE_COST
        val essenceCost = ExpeditionEngine.RARITY_ASCEND_ESSENCE_COST
        if (profile.solCoins < coinCost || hc.sunsteelOre < oreCost || hc.elementalEssence < essenceCost) {
            _bannerMessage.value = "Need $coinCost Sol Coins, $oreCost Ore & $essenceCost Essence to ascend ${item.name} to ${nextRarity.label}."
            return
        }

        val nextAtk = if (item.attackBonus > 0L) (item.attackBonus * 135L / 100L).coerceAtLeast(item.attackBonus + 5L) else 0L
        val nextDef = if (item.defenseBonus > 0L) (item.defenseBonus * 135L / 100L).coerceAtLeast(item.defenseBonus + 4L) else 0L
        val nextHp = if (item.hpBonus > 0L) (item.hpBonus * 135L / 100L).coerceAtLeast(item.hpBonus + 14L) else 0L
        val nextEnergy = if (item.energyBonus > 0L) (item.energyBonus * 135L / 100L).coerceAtLeast(item.energyBonus + 8L) else 0L

        haptics.trigger(GameplayHaptic.SOLAR_CRITICAL)
        viewModelScope.launch {
            preferences.updateHardcoreRpgState { cur ->
                cur.copy(
                    sunsteelOre = (cur.sunsteelOre - oreCost).coerceAtLeast(0L),
                    elementalEssence = (cur.elementalEssence - essenceCost).coerceAtLeast(0L)
                )
            }
            repository.updateInventoryItem(
                currentUserId,
                item.id,
                mapOf(
                    "rarity" to nextRarity.name,
                    "attackBonus" to nextAtk,
                    "defenseBonus" to nextDef,
                    "hpBonus" to nextHp,
                    "energyBonus" to nextEnergy
                )
            )
            val projected = items.map {
                if (it.id == item.id) {
                    it.copy(
                        rarity = nextRarity.name,
                        attackBonus = nextAtk,
                        defenseBonus = nextDef,
                        hpBonus = nextHp,
                        energyBonus = nextEnergy
                    )
                } else it
            }
            val stats = computeDerivedStats(profile, projected, equippedPet.value, hardcoreState.value)
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "solCoins" to (profile.solCoins - coinCost).coerceAtLeast(0L),
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "maxStrideEnergy" to stats.computedMaxEnergy.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            syncLeaderboard(profile, profile.level, stats.computedHeroPower.toLong(), profile.bossesDefeated)
            _bannerMessage.value = "ASCENDED ${item.name} to ${nextRarity.label} (+35% Core Stats)!"
        }
    }

    fun socketElementalRune(item: InventoryItem, affinity: ElementalAffinity) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val items = (inventoryState.value as? UiState.Success)?.data ?: return
        if (!item.resolvedSlot().isEquipment) return
        val hc = hardcoreState.value
        val availableCount = hc.runeCount(affinity)
        if (availableCount <= 0) {
            _bannerMessage.value = "No ${affinity.label} Runes in pouch — win Daily Elemental Rifts, Bosses, or visit the Black-Market."
            return
        }
        val newDesc = item.descriptionWithRune(affinity)
        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            var nextHc = hc
            preferences.updateHardcoreRpgState { cur ->
                val map = cur.unlockedRunesMap.toMutableMap()
                val left = (map[affinity.name] ?: 1) - 1
                if (left <= 0) map.remove(affinity.name) else map[affinity.name] = left
                cur.copy(unlockedRunesMap = map).also { nextHc = it }
            }
            repository.updateInventoryItem(currentUserId, item.id, mapOf("description" to newDesc))
            val projected = items.map {
                if (it.id == item.id) it.copy(description = newDesc) else it
            }
            val stats = computeDerivedStats(profile, projected, equippedPet.value, nextHc)
            repository.updateHeroProfile(
                currentUserId,
                mapOf("heroPower" to stats.computedHeroPower.toLong())
            )
            _bannerMessage.value = "Socketed ${affinity.label} Rune into ${item.name}!"
        }
    }

    // --- Sanctuary Town Hub: Alchemist Apothecary, Black-Market Merchant & Mercenaries ---

    fun brewAlchemistRecipe(recipe: AlchemistRecipeSpec) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val hc = hardcoreState.value
        if (profile.solCoins < recipe.solCoinCost ||
            hc.aetherHerbs < recipe.herbCost ||
            hc.sunsteelOre < recipe.oreCost ||
            hc.elementalEssence < recipe.essenceCost
        ) {
            _bannerMessage.value = "Insufficient ingredients for ${recipe.name} (${recipe.herbCost} Herbs, ${recipe.oreCost} Ore, ${recipe.essenceCost} Essence, ${recipe.solCoinCost} Sol Coins)."
            return
        }

        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            val nextCoins = (profile.solCoins - recipe.solCoinCost).coerceAtLeast(0L)
            var nextHc = hc
            preferences.updateHardcoreRpgState { cur ->
                val nextRunes = cur.unlockedRunesMap.toMutableMap()
                if (recipe.grantsRandomRune) {
                    val rune = ElementalAffinity.entries.random()
                    nextRunes[rune.name] = (nextRunes[rune.name] ?: 0) + 1
                }
                cur.copy(
                    aetherHerbs = (cur.aetherHerbs - recipe.herbCost).coerceAtLeast(0L),
                    sunsteelOre = (cur.sunsteelOre - recipe.oreCost).coerceAtLeast(0L),
                    elementalEssence = (cur.elementalEssence - recipe.essenceCost).coerceAtLeast(0L),
                    battleWoundStacks = if (recipe.curesWounds) 0 else cur.battleWoundStacks,
                    unlockedRunesMap = nextRunes
                ).also { nextHc = it }
            }

            if (recipe.healAmount > 0L) {
                val brewedItem = InventoryItem(
                    userId = currentUserId,
                    name = recipe.name,
                    slot = ItemSlot.CONSUMABLE.name,
                    rarity = if (recipe.curesWounds) ItemRarity.EPIC.name else ItemRarity.RARE.name,
                    healAmount = recipe.healAmount,
                    quantity = 1L,
                    description = recipe.summary
                )
                repository.addInventoryItem(currentUserId, brewedItem)
            }

            val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
            val stats = computeDerivedStats(profile, items, equippedPet.value, nextHc)
            val nextHp = if (recipe.curesWounds) stats.computedMaxHp.toLong() else profile.currentHp.coerceAtMost(stats.computedMaxHp.toLong())
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "solCoins" to nextCoins,
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "currentHp" to nextHp,
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            _bannerMessage.value = when {
                recipe.curesWounds -> "Brewed ${recipe.name}! All Battle Wounds cured & potion added to Satchel!"
                recipe.grantsRandomRune -> "Transmuted an Elemental Rune Crystal & added to your Rune Pouch!"
                else -> "Brewed ${recipe.name} (+${recipe.healAmount} HP Elixir added to Inventory)!"
            }
        }
    }

    fun buyBlackMarketOffer(offer: BlackMarketOfferSpec) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        if (profile.solCoins < offer.solCoinCost) {
            _bannerMessage.value = "Need ${offer.solCoinCost} Sol Coins for ${offer.title}."
            return
        }
        val nextCoins = (profile.solCoins - offer.solCoinCost).coerceAtLeast(0L)
        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)

        viewModelScope.launch {
            when (offer.id) {
                "mystery_mythic_cache", "bm_mystery_sovereign_cache" -> {
                    val item = ExpeditionEngine.rollBlackMarketMysteryItem(currentUserId, profile.level.toInt())
                    val addRes = repository.addInventoryItem(currentUserId, item)
                    val savedId = addRes.getOrNull().orEmpty()
                    repository.updateHeroProfile(currentUserId, mapOf("solCoins" to nextCoins))
                    _lastOpenedChestResult.value = ChestOpenResult(
                        item = item.copy(id = savedId),
                        rolledRarity = item.resolvedRarity(),
                        wasUltraRare = item.resolvedRarity() == ItemRarity.ULTRA_RARE
                    )
                    _bannerMessage.value = "Black-Market Cache opened: ${item.name} (${item.resolvedRarity().label})!"
                }
                "elemental_rune_coffer", "bm_elemental_rune_coffer" -> {
                    val r1 = ElementalAffinity.entries.random()
                    val r2 = ElementalAffinity.entries.random()
                    preferences.updateHardcoreRpgState { cur ->
                        val map = cur.unlockedRunesMap.toMutableMap()
                        map[r1.name] = (map[r1.name] ?: 0) + 1
                        map[r2.name] = (map[r2.name] ?: 0) + 1
                        cur.copy(
                            unlockedRunesMap = map,
                            elementalEssence = (cur.elementalEssence + 12L).coerceAtMost(999_999L)
                        )
                    }
                    repository.updateHeroProfile(currentUserId, mapOf("solCoins" to nextCoins))
                    _bannerMessage.value = "Rune Coffer opened: +1 ${r1.label} Rune, +1 ${r2.label} Rune & +12 Essence!"
                }
                "blacksmith_supply_crate", "bm_forge_material_crate" -> {
                    preferences.updateHardcoreRpgState { cur ->
                        cur.copy(
                            sunsteelOre = (cur.sunsteelOre + 35L).coerceAtMost(999_999L),
                            aetherHerbs = (cur.aetherHerbs + 28L).coerceAtMost(999_999L),
                            elementalEssence = (cur.elementalEssence + 10L).coerceAtMost(999_999L)
                        )
                    }
                    repository.updateHeroProfile(
                        currentUserId,
                        mapOf(
                            "solCoins" to nextCoins,
                            "sunshards" to (profile.sunshards + 50L).coerceAtMost(100_000_000L)
                        )
                    )
                    _bannerMessage.value = "Unlocked Supply Crate (+35 Ore, +28 Herbs, +10 Essence & +50 Sunshards)!"
                }
                "royal_physician_cure", "bm_physician_full_cure" -> {
                    var nextHc = hardcoreState.value
                    preferences.updateHardcoreRpgState { cur ->
                        cur.copy(battleWoundStacks = 0).also { nextHc = it }
                    }
                    val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
                    val stats = computeDerivedStats(profile, items, equippedPet.value, nextHc)
                    repository.updateHeroProfile(
                        currentUserId,
                        mapOf(
                            "solCoins" to nextCoins,
                            "maxHp" to stats.computedMaxHp.toLong(),
                            "currentHp" to stats.computedMaxHp.toLong(),
                            "heroPower" to stats.computedHeroPower.toLong()
                        )
                    )
                    _bannerMessage.value = "Sanctuary Physician cured all Battle Wounds & restored 100% HP!"
                }
            }
        }
    }

    fun hireMercenaryContract(contract: MercenaryContractSpec) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        if (profile.solCoins < contract.solCoinCost) {
            _bannerMessage.value = "Need ${contract.solCoinCost} Sol Coins to hire ${contract.name}."
            return
        }
        val nextCoins = (profile.solCoins - contract.solCoinCost).coerceAtLeast(0L)
        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            var nextHc = hardcoreState.value
            preferences.updateHardcoreRpgState { cur ->
                cur.copy(
                    mercenaryContractId = contract.id,
                    mercenaryBattlesLeft = contract.durationBattles
                ).also { nextHc = it }
            }
            val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
            val stats = computeDerivedStats(profile, items, equippedPet.value, nextHc)
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "solCoins" to nextCoins,
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            _bannerMessage.value = "Hired ${contract.name} for ${contract.durationBattles} battles (${contract.summary})!"
        }
    }

    // --- Character Creator Studio & Sunhaven Plaza Multiplayer Town ---

    fun openCharacterMirror() {
        _showCharacterStudio.value = true
    }

    fun dismissCharacterStudio() {
        val profile = (heroProfileState.value as? UiState.Success)?.data
        if (profile != null && profile.characterCreated) {
            _showCharacterStudio.value = false
        }
    }

    fun completeCharacterCreation(
        rawName: String,
        heroClass: HeroClass,
        avatarConfig: HeroAvatarConfig,
        isInitialCreation: Boolean
    ) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        val cleanName = rawName.trim().replace(Regex("\\s+"), " ").take(24)
        if (cleanName.length < 2) {
            _bannerMessage.value = "Enter a Hero Name (2 to 24 characters)."
            return
        }
        val serializedAvatar = avatarConfig.normalized().serialize()
        val defaultSpec = ClassSpecialization.forClass(heroClass).first()
        val ranks = profile.parsedSkillRanks().toMutableList()
        if (ranks[0] < 1) ranks[0] = 1
        val serializedRanks = ProgressionEngine.serializeSkillRanks(ranks)

        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            var nextHc = hardcoreState.value
            if (ClassSpecialization.fromName(nextHc.specializationId, heroClass).baseClass != heroClass) {
                preferences.updateHardcoreRpgState { hc ->
                    hc.copy(specializationId = defaultSpec.name).also { nextHc = it }
                }
            }
            val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
            val updated = profile.copy(
                displayName = cleanName,
                heroClass = heroClass.name,
                avatarConfig = serializedAvatar,
                characterCreated = true,
                skillRanks = serializedRanks
            )
            val stats = computeDerivedStats(updated, items, equippedPet.value, nextHc)
            val res = repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "displayName" to cleanName,
                    "heroClass" to heroClass.name,
                    "avatarConfig" to serializedAvatar,
                    "characterCreated" to true,
                    "skillRanks" to serializedRanks,
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "currentHp" to updated.currentHp.coerceAtMost(stats.computedMaxHp.toLong()),
                    "maxStrideEnergy" to stats.computedMaxEnergy.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            if (res.isSuccess) {
                // Rename starter weapon cleanly on initial creation to match chosen class
                if (isInitialCreation) {
                    val starter = items.firstOrNull { it.id == "starter_sunblade" }
                    if (starter != null) {
                        val starterName = when (heroClass) {
                            HeroClass.VANGUARD -> "Sunforged Practice Blade"
                            HeroClass.ARCANIST -> "Weathered Ember Staff"
                            HeroClass.RANGER -> "Highland Yew Shortbow"
                            HeroClass.SHADOWBLADE -> "Twin Eclipse Daggers"
                        }
                        repository.updateInventoryItem(currentUserId, starter.id, mapOf("name" to starterName))
                    }
                }
                syncLeaderboard(updated, updated.level, stats.computedHeroPower.toLong(), updated.bossesDefeated)
                _showCharacterStudio.value = false
                publishTownPresenceNow(force = true, overrideProfile = updated)
                _bannerMessage.value = if (isInitialCreation) {
                    "Welcome to Sunhaven, $cleanName the ${heroClass.title}!"
                } else {
                    "Updated hero appearance & identity for $cleanName!"
                }
            } else {
                _bannerMessage.value = "Could not save hero profile — check connection and try again."
            }
        }
    }

    fun openTownNpc(role: TownNpcRole?) {
        _selectedTownNpc.value = role
        if (role != null) {
            haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        }
    }

    fun visitTownNpc(role: TownNpcRole) {
        selectTab(SolstrideTab.EXPEDITION)
        openTownNpc(role)
    }

    fun inspectTownPlayer(player: TownPresencePlayer?) {
        _inspectedTownPlayer.value = player
        if (player != null) {
            haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        }
    }

    fun walkToTownPosition(targetX: Float, targetY: Float) {
        val clampedX = targetX.coerceIn(0.08f, 0.92f)
        val clampedY = targetY.coerceIn(0.24f, 0.88f)
        val prevX = _localTownPos.value.first
        if (kotlin.math.abs(clampedX - prevX) > 0.01f) {
            _localTownFacingRight.value = clampedX >= prevX
        }
        _localTownPos.value = clampedX to clampedY
        viewModelScope.launch {
            publishTownPresenceNow(force = false)
        }
    }

    fun sendTownEmote(emote: String) {
        val clean = emote.trim().take(30)
        _localTownEmote.value = clean
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            publishTownPresenceNow(force = true)
            delay(5_000L)
            if (_localTownEmote.value == clean) {
                _localTownEmote.value = ""
                publishTownPresenceNow(force = true)
            }
        }
    }

    private suspend fun publishTownPresenceNow(
        force: Boolean,
        overrideProfile: HeroProfile? = null
    ) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastTownPresenceSyncMs) < 600L) return
        val profile = overrideProfile ?: (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!profile.characterCreated) return
        lastTownPresenceSyncMs = now
        val (px, py) = _localTownPos.value
        val presence = TownPresencePlayer(
            id = currentUserId,
            userId = currentUserId,
            displayName = profile.displayName,
            heroClass = profile.resolvedClass().name,
            heroLevel = profile.level.coerceAtLeast(1L),
            heroPower = profile.heroPower.coerceAtLeast(1L),
            prestigeLevel = profile.prestigeLevel.coerceAtLeast(0L),
            avatarConfig = profile.resolvedAvatar().serialize(),
            equippedPetId = profile.equippedPetId,
            equippedPetLevel = equippedPet.value?.level?.coerceIn(1L, 50L) ?: 1L,
            equippedPetStage = equippedPet.value?.resolvedStage()?.toLong() ?: 1L,
            equippedPetBranch = equippedPet.value?.resolvedBranch()?.name ?: PetEvolutionBranch.NONE.name,
            posX = px.toDouble(),
            posY = py.toDouble(),
            facingRight = _localTownFacingRight.value,
            emote = _localTownEmote.value,
            inTown = true
        )
        repository.upsertTownPresence(currentUserId, presence)
    }

    // --- Dedicated Multiplayer Tab Actions (Chat, PvP Duels, Pet Playdates) ---

    fun selectMultiplayerSubTab(tab: MultiplayerSubTab) {
        _multiplayerSubTab.value = tab
    }

    fun sendMultiplayerChat(rawMessage: String) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        val clean = rawMessage.trim().take(240)
        if (clean.isEmpty()) return
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            repository.sendMultiplayerChatMessage(currentUserId, profile, clean)
                .onFailure { err ->
                    _bannerMessage.value = "Could not send message: ${err.localizedMessage ?: "check connection"}"
                }
        }
    }

    fun deleteMultiplayerChat(messageId: String) {
        if (messageId.isBlank()) return
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            repository.deleteMultiplayerChatMessage(currentUserId, messageId)
                .onFailure { err ->
                    _bannerMessage.value = "Unable to delete message: ${err.localizedMessage ?: "retry"}"
                }
        }
    }

    fun challengePlayerToDuel(opponent: TownPresencePlayer) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        val oppUid = opponent.userId.ifBlank { opponent.id }
        if (oppUid.isBlank() || oppUid == currentUserId) return

        val existing = multiplayerDuels.value.firstOrNull { duel ->
            duel.status in setOf("PENDING", "ACTIVE") &&
                oppUid in duel.participantUids &&
                currentUserId in duel.participantUids
        }
        if (existing != null) {
            _activeTab.value = SolstrideTab.MULTIPLAYER
            _multiplayerSubTab.value = MultiplayerSubTab.DUELS
            _bannerMessage.value = "You already have a ${existing.status.lowercase()} duel with ${opponent.displayName}!"
            return
        }

        val stats = derivedStats.value
        val challengerMaxHp = (stats?.computedMaxHp?.toLong() ?: profile.maxHp).coerceIn(100L, 1_000_000L)
        val challengerPower = (stats?.computedHeroPower?.toLong() ?: profile.heroPower).coerceIn(1L, 10_000_000L)
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)

        viewModelScope.launch {
            repository.createDuelChallenge(
                userId = currentUserId,
                challenger = profile,
                challengerMaxHp = challengerMaxHp,
                challengerPower = challengerPower,
                opponent = opponent
            ).onSuccess {
                _inspectedTownPlayer.value = null
                _activeTab.value = SolstrideTab.MULTIPLAYER
                _multiplayerSubTab.value = MultiplayerSubTab.DUELS
                _bannerMessage.value = "Duel challenge sent to ${opponent.displayName}!"
            }.onFailure { err ->
                _bannerMessage.value = "Failed to send duel challenge: ${err.localizedMessage ?: "retry"}"
            }
        }
    }

    fun acceptDuel(duel: MultiplayerDuel) {
        if (duel.id.isBlank() || duel.status != "PENDING" || duel.opponentUid != currentUserId) return
        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            val updates = mapOf<String, Any>(
                "status" to "ACTIVE",
                "turnUid" to duel.challengerUid,
                "turnNumber" to 1L,
                "lastActionLog" to "${duel.opponentName} accepted the Solar Duel! ${duel.challengerName}'s turn.".take(240)
            )
            repository.updateDuelState(currentUserId, duel.id, updates)
                .onSuccess {
                    _bannerMessage.value = "Duel started against ${duel.challengerName}!"
                }
                .onFailure { err ->
                    _bannerMessage.value = "Could not accept duel: ${err.localizedMessage ?: "retry"}"
                }
        }
    }

    fun declineOrCancelDuel(duel: MultiplayerDuel) {
        if (duel.id.isBlank() || duel.status != "PENDING") return
        val isChallenger = duel.challengerUid == currentUserId
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            val actorName = if (isChallenger) duel.challengerName else duel.opponentName
            val updates = mapOf<String, Any>(
                "status" to "DECLINED",
                "lastActionLog" to if (isChallenger) {
                    "$actorName withdrew the duel challenge."
                } else {
                    "$actorName declined the duel challenge."
                }.take(240)
            )
            repository.updateDuelState(currentUserId, duel.id, updates)
                .onFailure { err ->
                    _bannerMessage.value = "Could not update duel: ${err.localizedMessage ?: "retry"}"
                }
        }
    }

    fun performDuelAction(duel: MultiplayerDuel, action: DuelActionKind) {
        if (duel.id.isBlank() || duel.status != "ACTIVE") return
        if (duel.turnUid != currentUserId) {
            _bannerMessage.value = "Wait for your opponent to take their turn!"
            return
        }

        val isChallengerTurn = currentUserId == duel.challengerUid
        val attackerName = if (isChallengerTurn) duel.challengerName else duel.opponentName
        val defenderName = if (isChallengerTurn) duel.opponentName else duel.challengerName
        val attackerPower = if (isChallengerTurn) duel.challengerPower else duel.opponentPower
        val attackerLevel = if (isChallengerTurn) duel.challengerLevel else duel.opponentLevel
        val attackerPet = if (isChallengerTurn) duel.resolvedChallengerPet() else duel.resolvedOpponentPet()
        val attackerHp = if (isChallengerTurn) duel.challengerHp else duel.opponentHp
        val attackerMaxHp = (if (isChallengerTurn) duel.challengerMaxHp else duel.opponentMaxHp).coerceAtLeast(1L)

        val defenderHp = if (isChallengerTurn) duel.opponentHp else duel.challengerHp
        val defenderMaxHp = (if (isChallengerTurn) duel.opponentMaxHp else duel.challengerMaxHp).coerceAtLeast(1L)
        val defenderGuarding = if (isChallengerTurn) duel.opponentGuard else duel.challengerGuard

        val nextTurnUid = if (isChallengerTurn) duel.opponentUid else duel.challengerUid
        val nextTurnNumber = (duel.turnNumber + 1L).coerceAtMost(500L)

        // Balanced PvP scaling so duels last an exciting 4-8 tactical turns
        val baseAttack = (18.0 + attackerLevel * 3.8 + attackerPower * 0.085)
        val petBonus = if (attackerPet != null) 1.08 else 1.0
        val variance = Random.nextDouble(0.92, 1.09)

        var newAttackerHp = attackerHp
        var newDefenderHp = defenderHp
        var newAttackerGuard = false
        val newDefenderGuard = false // Defender's guard is consumed or expires when attacked/passed
        val logText: String

        when (action) {
            DuelActionKind.STRIKE -> {
                var dmg = (baseAttack * petBonus * variance).roundToInt().toLong().coerceAtLeast(12L)
                if (defenderGuarding) dmg = (dmg * 0.55).roundToInt().toLong().coerceAtLeast(6L)
                newDefenderHp = (defenderHp - dmg).coerceAtLeast(0L)
                val petNote = if (attackerPet != null) " (${attackerPet.displayName} assisted!)" else ""
                val guardNote = if (defenderGuarding) " [Guarded]" else ""
                logText = "$attackerName used Solar Strike on $defenderName for $dmg dmg$guardNote!$petNote"
            }
            DuelActionKind.CLASS_ART -> {
                var dmg = (baseAttack * 1.38 * petBonus * variance).roundToInt().toLong().coerceAtLeast(18L)
                if (defenderGuarding) dmg = (dmg * 0.55).roundToInt().toLong().coerceAtLeast(9L)
                val leech = (attackerMaxHp * 0.08).roundToInt().toLong().coerceAtLeast(6L)
                newDefenderHp = (defenderHp - dmg).coerceAtLeast(0L)
                newAttackerHp = (attackerHp + leech).coerceAtMost(attackerMaxHp)
                val guardNote = if (defenderGuarding) " [Guarded]" else ""
                logText = "$attackerName unleashed Class Art for $dmg dmg$guardNote and recovered +$leech HP!"
            }
            DuelActionKind.GUARD -> {
                val heal = (attackerMaxHp * 0.14).roundToInt().toLong().coerceAtLeast(10L)
                newAttackerHp = (attackerHp + heal).coerceAtMost(attackerMaxHp)
                newAttackerGuard = true
                logText = "$attackerName raised Aegis Guard (-45% next hit) and restored +$heal HP!"
            }
        }

        val won = newDefenderHp <= 0L
        val nextStatus = if (won) "COMPLETED" else "ACTIVE"
        val winnerUid = if (won) currentUserId else ""
        val finalLog = if (won) {
            "$logText $attackerName won the Solar Duel!"
        } else {
            logText
        }.take(240)

        val nextChallengerHp = (if (isChallengerTurn) newAttackerHp else newDefenderHp).coerceIn(0L, duel.challengerMaxHp)
        val nextOpponentHp = (if (isChallengerTurn) newDefenderHp else newAttackerHp).coerceIn(0L, duel.opponentMaxHp)
        val nextChallengerGuard = if (isChallengerTurn) newAttackerGuard else newDefenderGuard
        val nextOpponentGuard = if (isChallengerTurn) newDefenderGuard else newAttackerGuard

        haptics.trigger(if (won) GameplayHaptic.VICTORY_FANFARE else GameplayHaptic.PLAYER_STRIKE)

        viewModelScope.launch {
            val updates = mapOf<String, Any>(
                "challengerHp" to nextChallengerHp,
                "challengerGuard" to nextChallengerGuard,
                "opponentHp" to nextOpponentHp,
                "opponentGuard" to nextOpponentGuard,
                "status" to nextStatus,
                "turnUid" to nextTurnUid,
                "turnNumber" to nextTurnNumber,
                "winnerUid" to winnerUid,
                "lastActionLog" to finalLog
            )
            repository.updateDuelState(currentUserId, duel.id, updates)
                .onSuccess {
                    if (won && claimedDuelRewardIds.add(duel.id)) {
                        awardFriendlyDuelHonor(defenderName)
                    }
                }
                .onFailure { err ->
                    _bannerMessage.value = "Duel action failed: ${err.localizedMessage ?: "retry"}"
                }
        }
    }

    private suspend fun awardFriendlyDuelHonor(defeatedName: String) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        val stats = derivedStats.value ?: return
        val leveled = applyXpAndLevelUp(profile, 35L, stats)
        val nextCoins = (profile.solCoins + 5L).coerceAtMost(100_000_000L)
        repository.updateHeroProfile(
            currentUserId,
            mapOf(
                "solCoins" to nextCoins,
                "level" to leveled.level,
                "xp" to leveled.xp,
                "xpToNextLevel" to leveled.xpToNextLevel,
                "unspentPoints" to leveled.unspentPoints,
                "maxHp" to leveled.maxHp,
                "currentHp" to leveled.currentHp,
                "heroPower" to leveled.heroPower
            )
        )
        _bannerMessage.value = "Victory over $defeatedName! Friendly Duel Honor: +5 Sol Coins & +35 XP!"
    }

    fun forfeitDuel(duel: MultiplayerDuel) {
        if (duel.id.isBlank() || duel.status != "ACTIVE") return
        val isChallenger = currentUserId == duel.challengerUid
        val otherUid = if (isChallenger) duel.opponentUid else duel.challengerUid
        val actorName = if (isChallenger) duel.challengerName else duel.opponentName
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            val updates = mapOf<String, Any>(
                "status" to "COMPLETED",
                "winnerUid" to otherUid,
                "lastActionLog" to "$actorName yielded the Solar Duel with honor.".take(240)
            )
            repository.updateDuelState(currentUserId, duel.id, updates)
                .onFailure { err ->
                    _bannerMessage.value = "Could not forfeit duel: ${err.localizedMessage ?: "retry"}"
                }
        }
    }

    fun dismissDuel(duel: MultiplayerDuel) {
        if (duel.id.isBlank() || duel.status !in setOf("COMPLETED", "DECLINED")) return
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            repository.deleteDuel(currentUserId, duel.id)
                .onFailure { err ->
                    _bannerMessage.value = "Could not dismiss duel: ${err.localizedMessage ?: "retry"}"
                }
        }
    }

    fun invitePetPlaydate(guestPlayer: TownPresencePlayer) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        val myPet = equippedPet.value
        if (myPet == null) {
            _bannerMessage.value = "Equip a Companion Pet in the Pets tab before starting a Pet Playdate!"
            return
        }
        val guestPet = guestPlayer.resolvedPetType()
        if (guestPet == null) {
            _bannerMessage.value = "${guestPlayer.displayName} doesn't have an equipped companion pet right now."
            return
        }
        val guestUid = guestPlayer.userId.ifBlank { guestPlayer.id }
        if (guestUid.isBlank() || guestUid == currentUserId) return

        val existing = multiplayerPetPlaydates.value.firstOrNull { playdate ->
            playdate.status in setOf("PENDING", "PLAYING") &&
                guestUid in playdate.participantUids &&
                currentUserId in playdate.participantUids
        }
        if (existing != null) {
            _activeTab.value = SolstrideTab.MULTIPLAYER
            _multiplayerSubTab.value = MultiplayerSubTab.PET_PLAY
            _bannerMessage.value = "Your pets already have an active playdate with ${guestPlayer.displayName}!"
            return
        }

        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            repository.createPetPlaydate(
                userId = currentUserId,
                hostProfile = profile,
                hostPet = myPet,
                guestPlayer = guestPlayer
            ).onSuccess {
                _inspectedTownPlayer.value = null
                _activeTab.value = SolstrideTab.MULTIPLAYER
                _multiplayerSubTab.value = MultiplayerSubTab.PET_PLAY
                _bannerMessage.value = "Invited ${guestPlayer.displayName}'s ${guestPet.displayName} to play in the Pet Park!"
            }.onFailure { err ->
                _bannerMessage.value = "Could not send playdate invite: ${err.localizedMessage ?: "retry"}"
            }
        }
    }

    fun acceptPetPlaydate(playdate: MultiplayerPetPlaydate) {
        if (playdate.id.isBlank() || playdate.status != "PENDING" || playdate.guestUid != currentUserId) return
        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            val hostPet = playdate.resolvedHostPet().displayName
            val guestPet = playdate.resolvedGuestPet().displayName
            val updates = mapOf<String, Any>(
                "status" to "PLAYING",
                "activity" to PetPlayActivity.CHASE.id,
                "playCount" to 1L,
                "happinessBonus" to 12L,
                "lastPlayLog" to "$guestPet joined $hostPet in the Sunhaven Pet Park!".take(240)
            )
            repository.updatePetPlaydate(currentUserId, playdate.id, updates)
                .onSuccess {
                    _bannerMessage.value = "$guestPet and $hostPet are now playing together!"
                }
                .onFailure { err ->
                    _bannerMessage.value = "Could not accept playdate: ${err.localizedMessage ?: "retry"}"
                }
        }
    }

    fun declineOrCancelPetPlaydate(playdate: MultiplayerPetPlaydate) {
        if (playdate.id.isBlank() || playdate.status != "PENDING") return
        val isHost = playdate.hostUid == currentUserId
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            val actor = if (isHost) playdate.hostName else playdate.guestName
            val updates = mapOf<String, Any>(
                "status" to "DECLINED",
                "lastPlayLog" to if (isHost) "$actor cancelled the playdate invite." else "$actor declined the playdate invite."
            )
            repository.updatePetPlaydate(currentUserId, playdate.id, updates)
                .onFailure { err ->
                    _bannerMessage.value = "Could not update playdate: ${err.localizedMessage ?: "retry"}"
                }
        }
    }

    fun triggerPetPlayActivity(playdate: MultiplayerPetPlaydate, activity: PetPlayActivity) {
        if (playdate.id.isBlank() || playdate.status != "PLAYING") return
        val nextCount = (playdate.playCount + 1L).coerceAtMost(50L)
        val nextHappiness = (playdate.happinessBonus + 12L).coerceAtMost(100L)
        val hostPet = playdate.resolvedHostPet().displayName
        val guestPet = playdate.resolvedGuestPet().displayName
        val log = "$hostPet & $guestPet ${activity.verbSummary}".take(240)
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            val updates = mapOf<String, Any>(
                "activity" to activity.id,
                "playCount" to nextCount,
                "happinessBonus" to nextHappiness,
                "lastPlayLog" to log
            )
            repository.updatePetPlaydate(currentUserId, playdate.id, updates)
                .onFailure { err ->
                    _bannerMessage.value = "Could not update play activity: ${err.localizedMessage ?: "retry"}"
                }
        }
    }

    fun finishPetPlaydate(playdate: MultiplayerPetPlaydate) {
        if (playdate.id.isBlank() || playdate.status != "PLAYING") return
        val hostPet = playdate.resolvedHostPet().displayName
        val guestPet = playdate.resolvedGuestPet().displayName
        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            val updates = mapOf<String, Any>(
                "status" to "FINISHED",
                "lastPlayLog" to "$hostPet & $guestPet wrapped up their playdate with +${playdate.happinessBonus}% joyful spirit!".take(240)
            )
            repository.updatePetPlaydate(currentUserId, playdate.id, updates)
                .onSuccess {
                    _bannerMessage.value = "Playdate finished! Pets bonded (+${playdate.happinessBonus}% Happiness)."
                }
                .onFailure { err ->
                    _bannerMessage.value = "Could not finish playdate: ${err.localizedMessage ?: "retry"}"
                }
        }
    }

    fun dismissPetPlaydate(playdate: MultiplayerPetPlaydate) {
        if (playdate.id.isBlank() || playdate.status !in setOf("FINISHED", "DECLINED")) return
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            repository.deletePetPlaydate(currentUserId, playdate.id)
                .onFailure { err ->
                    _bannerMessage.value = "Could not dismiss playdate: ${err.localizedMessage ?: "retry"}"
                }
        }
    }

    fun buyTownMaterialOffer(offer: TownMaterialOffer) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val todayKey = currentCalendarDateKey()
        val hc = hardcoreState.value
        val marketExtraStock = TownBuildingCatalog.computeBonuses(profile).extraMerchantStock
        val effectiveLimit = offer.dailyStockLimit + marketExtraStock
        val purchasedToday = hc.townPurchasesToday(offer.id, todayKey)
        if (purchasedToday >= effectiveLimit) {
            _bannerMessage.value = "${offer.title} is sold out for today ($effectiveLimit/$effectiveLimit). Restocks at midnight!"
            return
        }
        if (profile.solCoins < offer.solCoinCost) {
            _bannerMessage.value = "Need ${offer.solCoinCost} Sol Coins for ${offer.title}. Complete Quests, Expeditions, or Watch Sleep!"
            return
        }

        val nextCoins = (profile.solCoins - offer.solCoinCost).coerceAtLeast(0L)
        val grantedRune = if (offer.randomRuneGrant) ElementalAffinity.entries.random() else null
        val grantedRelic = if (offer.relicPieceGrant) RelicSetSpec.nextUncollectedRelic(hc.collectedRelicIds) else null
        val nextCrests = (profile.guildCrestsOwned + offer.guildCrestGrant).coerceAtMost(99L)
        val nextSeasonXp = (profile.seasonXp + 20L).coerceAtMost(999_999L)
        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)

        viewModelScope.launch {
            preferences.updateHardcoreRpgState { cur ->
                val resetDaily = cur.townMerchantDateKey != todayKey
                val nextPurchasedMap = if (resetDaily) mutableMapOf() else cur.townMerchantPurchasedMap.toMutableMap()
                nextPurchasedMap[offer.id] = (nextPurchasedMap[offer.id] ?: 0) + 1

                val nextRunes = cur.unlockedRunesMap.toMutableMap()
                if (grantedRune != null) {
                    nextRunes[grantedRune.name] = (nextRunes[grantedRune.name] ?: 0) + 1
                }
                val nextRelics = cur.collectedRelicIds.toMutableSet()
                if (grantedRelic != null) {
                    nextRelics.add(grantedRelic.second)
                }
                cur.copy(
                    sunsteelOre = (cur.sunsteelOre + offer.oreGrant).coerceAtMost(999_999L),
                    aetherHerbs = (cur.aetherHerbs + offer.herbGrant).coerceAtMost(999_999L),
                    elementalEssence = (cur.elementalEssence + offer.essenceGrant).coerceAtMost(999_999L),
                    starforgedIngots = (cur.starforgedIngots + offer.starforgedIngotGrant).coerceAtMost(999L),
                    fogLanternCharges = (cur.fogLanternCharges + offer.fogLanternGrant).coerceAtMost(99),
                    unlockedRunesMap = nextRunes,
                    collectedRelicIds = nextRelics,
                    townMerchantDateKey = todayKey,
                    townMerchantPurchasedMap = nextPurchasedMap
                )
            }
            val updates = mutableMapOf<String, Any>(
                "solCoins" to nextCoins,
                "seasonXp" to nextSeasonXp
            )
            if (offer.guildCrestGrant > 0) {
                updates["guildCrestsOwned"] = nextCrests
            }
            repository.updateHeroProfile(currentUserId, updates)
            val extraNote = buildString {
                if (grantedRune != null) append(" (+1 ${grantedRune.label} Rune!)")
                if (grantedRelic != null) append(" (+Relic: ${RelicSetSpec.pieceDisplayName(grantedRelic.second)}!)")
                if (offer.guildCrestGrant > 0) append(" (+${offer.guildCrestGrant} Guild Charter Crest!)")
            }
            _bannerMessage.value = "Purchased ${offer.title} at Sunhaven Market (-${offer.solCoinCost} Sol Coins)$extraNote"
        }
    }

    // =========================================================================
    // INTERACTIVE GAMEPLAY, PET EVOLUTION, TOWN BUILDINGS, SEASONS, RAIDS & GUILDS
    // =========================================================================

    fun dismissFreshJourneyBanner() {
        _freshJourneyBanner.value = null
    }

    fun dismissEvolutionCeremony() {
        _recentEvolutionCeremonyPet.value = null
    }

    fun setRelaxedTimingAssist(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateHardcoreRpgState { it.copy(relaxedTimingAssist = enabled) }
        }
        _bannerMessage.value = if (enabled) {
            "Relaxed Timing Assist ON — wider Dodge/Parry, Forge & Brew sweet spots."
        } else {
            "Standard Precision Timing restored."
        }
    }

    fun performPetCareAction(pet: OwnedPet, actionType: String) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val todayKey = currentCalendarDateKey()
        val usedToday = if (pet.lastCareDateKey == todayKey) pet.dailyCareActions.toInt() else 0
        val dailyCap = PetEvolutionCatalog.DAILY_CARE_LIMIT
        val availableBonusCharges = hardcoreState.value.bonusPetCareCharges.coerceAtLeast(0)
        val consumingBonusCharge = usedToday >= dailyCap
        if (consumingBonusCharge && availableBonusCharges <= 0) {
            _bannerMessage.value = "${pet.resolvedForm().formName} is happily rested! Daily Care resets tomorrow ($dailyCap/$dailyCap)."
            com.example.widget.SolstrideWidgetUpdater.updateAllWidgets(appContext)
            return
        }

        val townBonus = TownBuildingCatalog.computeBonuses(profile)
        val eventSpec = activeWorldEvent.value.first
        val bondMultiplier = 100L + townBonus.bondXpBonusPct + eventSpec.petBondBonusPct
        val baseBond = when (actionType) {
            "TREAT_TOSS" -> 26L
            "SPAR_DRILL" -> 32L
            else -> 18L
        }
        val basePetXp = when (actionType) {
            "TREAT_TOSS" -> 22L
            "SPAR_DRILL" -> 28L
            else -> 14L
        }
        val bondGain = (baseBond * bondMultiplier / 100L).coerceAtLeast(12L)
        val newBond = (pet.bondXp + bondGain).coerceAtMost(PetEvolutionCatalog.MAX_BOND_XP)

        var newLvl = pet.level
        var newXp = pet.xp + basePetXp
        var needed = pet.xpToNextLevel.coerceAtLeast(85L)
        while (newXp >= needed && newLvl < PetCatalog.MAX_PET_LEVEL) {
            newXp -= needed
            newLvl += 1L
            needed = PetCatalog.xpRequiredForLevel(newLvl)
        }
        if (newLvl >= PetCatalog.MAX_PET_LEVEL) newXp = 0L

        val nextDailyCount = if (consumingBonusCharge) usedToday.toLong() else (usedToday + 1).toLong()
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            repository.updateOwnedPet(
                currentUserId,
                pet.id.ifBlank { pet.petType },
                mapOf(
                    "level" to newLvl,
                    "xp" to newXp,
                    "xpToNextLevel" to needed,
                    "bondXp" to newBond,
                    "lastCareDateKey" to todayKey,
                    "dailyCareActions" to nextDailyCount
                )
            )
            preferences.updateHardcoreRpgState { hc ->
                val map = hc.activityMasteryXpMap.toMutableMap()
                map[ActivityMasteryType.COMPANION.id] = (map[ActivityMasteryType.COMPANION.id] ?: 0) + 18
                val nextBonus = if (consumingBonusCharge) (hc.bonusPetCareCharges - 1).coerceAtLeast(0) else hc.bonusPetCareCharges
                hc.copy(
                    activityMasteryXpMap = map,
                    bonusPetCareCharges = nextBonus
                )
            }
            repository.updateHeroProfile(
                currentUserId,
                mapOf("seasonXp" to (profile.seasonXp + 18L).coerceAtMost(999_999L))
            )
            val verb = when (actionType) {
                "TREAT_TOSS" -> "Tossed a sunberry treat"
                "SPAR_DRILL" -> "Completed a Spar Drill"
                else -> "Bonded with"
            }
            if (consumingBonusCharge) {
                val bonusLeft = (availableBonusCharges - 1).coerceAtLeast(0)
                _bannerMessage.value = "✨ Used Extra Pet Care! $verb ${pet.resolvedForm().formName}! +$bondGain Bond XP & +$basePetXp Pet XP ($bonusLeft bonus left)."
            } else {
                _bannerMessage.value = "$verb ${pet.resolvedForm().formName}! +$bondGain Bond XP & +$basePetXp Pet XP (${usedToday + 1}/$dailyCap today)."
            }
        }
    }

    fun triggerSlotWheelTick() {
        haptics.trigger(GameplayHaptic.GUARD_BLOCK)
    }

    fun onSlotSpinRevealCompleted(result: com.example.domain.SlotSpinResult) {
        haptics.trigger(
            when {
                result.isUltraRare -> GameplayHaptic.VICTORY_FANFARE
                result.isJackpot -> GameplayHaptic.SOLAR_CRITICAL
                else -> GameplayHaptic.SYNC_CONFIRM
            }
        )
        _bannerMessage.value = "${result.prize.symbol} ${result.headline} — ${result.detailLine}"
    }

    fun spinSlotMachine() {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val cost = com.example.domain.SlotMachineCatalog.SPIN_COST_SOL_COINS
        if (profile.solCoins < cost) {
            _bannerMessage.value = "Need $cost Sol Coins to spin the Fortune Wheel (you have ${profile.solCoins})."
            return
        }

        viewModelScope.launch {
            slotSpinMutex.withLock {
                val latestProfile = (heroProfileState.value as? UiState.Success)?.data ?: return@withLock
                if (latestProfile.solCoins < cost) {
                    _bannerMessage.value = "Need $cost Sol Coins to spin the Fortune Wheel."
                    return@withLock
                }

                val (wedgeIndex, prize) = com.example.domain.SlotMachineCatalog.rollPrize()
                val coinsAfterCost = (latestProfile.solCoins - cost).coerceAtLeast(0L)
                val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
                val activePet = equippedPet.value
                val derived = computeDerivedStats(latestProfile, items, activePet, hardcoreState.value)

                var headline = prize.title
                var detailLine = prize.rewardSummary
                var awardedItemName: String? = null
                var awardedChestLabel: String? = null

                val profileUpdates = mutableMapOf<String, Any>(
                    "solCoins" to coinsAfterCost,
                    "seasonXp" to (latestProfile.seasonXp + 20L).coerceAtMost(999_999L)
                )

                when (prize.id) {
                    "ultra_pet_care_double", "ultra_pet_care_single" -> {
                        val extraCharges = prize.bonusPetCareCharges.coerceAtLeast(1)
                        val profileRes = repository.updateHeroProfile(currentUserId, profileUpdates)
                        if (profileRes.isFailure) {
                            _bannerMessage.value = "Could not complete spin — check connection and try again."
                            return@withLock
                        }
                        preferences.updateHardcoreRpgState { hc ->
                            hc.copy(bonusPetCareCharges = (hc.bonusPetCareCharges + extraCharges).coerceIn(0, 99))
                        }
                        com.example.widget.SolstrideWidgetSnapshotStore.grantBonusWildStamina(appContext, extraCharges)
                        headline = "ULTRA RARE! +$extraCharges Extra Pet Care!"
                        detailLine = "Added +$extraCharges Bonus Pet Care (bypasses 4/4 daily limit in Sanctuary & Widget) + $extraCharges Wild Stamina!"
                    }
                    "sunshard_coin_pouch" -> {
                        profileUpdates["solCoins"] = coinsAfterCost + 20L
                        profileUpdates["sunshards"] = (latestProfile.sunshards + 75L).coerceAtMost(999_999_999L)
                        val profileRes = repository.updateHeroProfile(currentUserId, profileUpdates)
                        if (profileRes.isFailure) {
                            _bannerMessage.value = "Could not complete spin — check connection and try again."
                            return@withLock
                        }
                        preferences.updateHardcoreRpgState { hc ->
                            hc.copy(sunsteelOre = (hc.sunsteelOre + 8L).coerceAtMost(999_999L))
                        }
                        detailLine = "+75 Sunshards, +20 Sol Coins rebate & +8 Sunsteel Ore added to your Vault."
                    }
                    "starforged_cache" -> {
                        val profileRes = repository.updateHeroProfile(currentUserId, profileUpdates)
                        if (profileRes.isFailure) {
                            _bannerMessage.value = "Could not complete spin — check connection and try again."
                            return@withLock
                        }
                        preferences.updateHardcoreRpgState { hc ->
                            hc.copy(
                                starforgedIngots = (hc.starforgedIngots + 1L).coerceAtMost(999_999L),
                                sunsteelOre = (hc.sunsteelOre + 12L).coerceAtMost(999_999L),
                                elementalEssence = (hc.elementalEssence + 6L).coerceAtMost(999_999L)
                            )
                        }
                        detailLine = "+1 Starforged Masterwork Ingot, +12 Sunsteel Ore & +6 Elemental Essence!"
                    }
                    "boss_vault_chest" -> {
                        val chest = LootChest(
                            userId = currentUserId,
                            chestTier = LootChestTier.BOSS_VAULT.name,
                            sourceLabel = "Fortune Wheel Boss Vault",
                            heroLevelSnapshot = latestProfile.level.coerceAtLeast(1L),
                            durationMinutes = 0L,
                            ultraRareChancePct = 18L
                        )
                        val profileRes = repository.updateHeroProfile(currentUserId, profileUpdates)
                        if (profileRes.isFailure) {
                            _bannerMessage.value = "Could not complete spin — check connection and try again."
                            return@withLock
                        }
                        repository.addLootChest(currentUserId, chest)
                        awardedChestLabel = "Fortune Wheel Boss Vault"
                        detailLine = "A Boss Vault Chest (18% Ultra Rare chance) is waiting in your Hero Vault!"
                    }
                    "sol_coin_jackpot" -> {
                        profileUpdates["solCoins"] = coinsAfterCost + 150L
                        profileUpdates["sunshards"] = (latestProfile.sunshards + 50L).coerceAtMost(999_999_999L)
                        val profileRes = repository.updateHeroProfile(currentUserId, profileUpdates)
                        if (profileRes.isFailure) {
                            _bannerMessage.value = "Could not complete spin — check connection and try again."
                            return@withLock
                        }
                        headline = "SOLAR JACKPOT! +150 Sol Coins!"
                        detailLine = "Won +150 Sol Coins (net +100 profit!) & +50 Sunshards!"
                    }
                    "alchemist_dream_satchel" -> {
                        val nextEnergy = (latestProfile.strideEnergy + 30L).coerceAtMost(derived.computedMaxEnergy.toLong())
                        profileUpdates["sunshards"] = (latestProfile.sunshards + 40L).coerceAtMost(999_999_999L)
                        profileUpdates["strideEnergy"] = nextEnergy
                        val profileRes = repository.updateHeroProfile(currentUserId, profileUpdates)
                        if (profileRes.isFailure) {
                            _bannerMessage.value = "Could not complete spin — check connection and try again."
                            return@withLock
                        }
                        preferences.updateHardcoreRpgState { hc ->
                            hc.copy(
                                aetherHerbs = (hc.aetherHerbs + 10L).coerceAtMost(999_999L),
                                dreamShards = (hc.dreamShards + 4L).coerceAtMost(999_999L)
                            )
                        }
                        detailLine = "+10 Aether Herbs, +4 Dream Shards, +30 Stride Energy & +40 Sunshards!"
                    }
                    "companion_bond_feast" -> {
                        profileUpdates["evolutionShards"] = (latestProfile.evolutionShards + 1L).coerceAtMost(99999L)
                        if (activePet == null) {
                            profileUpdates["sunshards"] = (latestProfile.sunshards + 120L).coerceAtMost(999_999_999L)
                        }
                        val profileRes = repository.updateHeroProfile(currentUserId, profileUpdates)
                        if (profileRes.isFailure) {
                            _bannerMessage.value = "Could not complete spin — check connection and try again."
                            return@withLock
                        }
                        if (activePet != null) {
                            val leveled = PetCatalog.applyFoodXp(activePet, 85L)
                            val newBond = (activePet.bondXp + 60L).coerceAtMost(PetEvolutionCatalog.MAX_BOND_XP)
                            repository.updateOwnedPet(
                                currentUserId,
                                activePet.id.ifBlank { activePet.petType },
                                mapOf(
                                    "level" to leveled.level,
                                    "xp" to leveled.xp,
                                    "xpToNextLevel" to leveled.xpToNextLevel,
                                    "bondXp" to newBond
                                )
                            )
                            detailLine = "${activePet.resolvedForm().formName} gained +85 Pet XP, +60 Bond XP & +1 Evolution Shard!"
                        } else {
                            detailLine = "+1 Evolution Shard & +120 Sunshards (adopt a pet to feast on Bond XP)!"
                        }
                    }
                    "forged_epic_gear" -> {
                        val equipmentSlots = listOf(
                            ItemSlot.WEAPON,
                            ItemSlot.ARMOR,
                            ItemSlot.HELM,
                            ItemSlot.BOOTS,
                            ItemSlot.RING,
                            ItemSlot.RELIC
                        )
                        val chosenSlot = equipmentSlots[Random.nextInt(equipmentSlots.size)]
                        val forged = ExpeditionEngine.forgeCustomEquipment(
                            userId = currentUserId,
                            slot = chosenSlot,
                            tier = ForgeBlueprintTier.OBSIDIAN_MASTERWORK,
                            heroLevel = latestProfile.level.toInt()
                        )
                        val profileRes = repository.updateHeroProfile(currentUserId, profileUpdates)
                        if (profileRes.isFailure) {
                            _bannerMessage.value = "Could not complete spin — check connection and try again."
                            return@withLock
                        }
                        repository.addInventoryItem(currentUserId, forged)
                        awardedItemName = forged.name
                        headline = "Forged ${forged.name}!"
                        detailLine = "${forged.resolvedRarity().label} ${chosenSlot.label} added straight to your Gear Bag!"
                    }
                    "mythic_evolution_relic" -> {
                        profileUpdates["mythicCores"] = (latestProfile.mythicCores + 1L).coerceAtMost(9999L)
                        profileUpdates["evolutionShards"] = (latestProfile.evolutionShards + 2L).coerceAtMost(99999L)
                        val profileRes = repository.updateHeroProfile(currentUserId, profileUpdates)
                        if (profileRes.isFailure) {
                            _bannerMessage.value = "Could not complete spin — check connection and try again."
                            return@withLock
                        }
                        detailLine = "+1 Mythic Core & +2 Evolution Shards added for Stage 2/3 Pet Evolution!"
                    }
                    else -> {
                        val profileRes = repository.updateHeroProfile(currentUserId, profileUpdates)
                        if (profileRes.isFailure) return@withLock
                    }
                }

                haptics.trigger(GameplayHaptic.PLAYER_STRIKE)
                val result = com.example.domain.SlotSpinResult(
                    spinId = System.currentTimeMillis(),
                    wedgeIndex = wedgeIndex,
                    prize = prize,
                    headline = headline,
                    detailLine = detailLine,
                    bonusPetCareGranted = prize.bonusPetCareCharges,
                    awardedItemName = awardedItemName,
                    awardedChestLabel = awardedChestLabel
                )
                val cur = _slotMachineState.value
                _slotMachineState.value = cur.copy(
                    lastResult = result,
                    recentHistory = (listOf(result) + cur.recentHistory).take(10),
                    totalSpinsThisSession = cur.totalSpinsThisSession + 1,
                    ultraRareHitsThisSession = cur.ultraRareHitsThisSession + (if (result.isUltraRare) 1 else 0)
                )
            }
        }
    }

    fun evolvePet(pet: OwnedPet, chosenBranch: PetEvolutionBranch) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val curStage = pet.resolvedStage()
        val req = PetEvolutionCatalog.requirementForNextStage(curStage)
        if (req == null) {
            _bannerMessage.value = "${pet.resolvedForm().formName} has already reached Stage 3 Apex Mythic form!"
            return
        }
        val menagerieLevel = TownBuildingCatalog.levelOf(profile, TownBuildingId.MENAGERIE)
        if (pet.level < req.minLevel ||
            pet.bondXp < req.minBondXp ||
            pet.stepsWalkedWithPet < req.minStepsWalked ||
            profile.trueBossWins < req.minTrueBossWins ||
            profile.currentRealm < req.minRealmReached ||
            menagerieLevel < req.minMenagerieLevel ||
            profile.evolutionShards < req.evolutionShardsNeeded ||
            profile.mythicCores < req.mythicCoresNeeded ||
            profile.solCoins < req.solCoinTribute ||
            profile.sunshards < req.sunshardTribute
        ) {
            _bannerMessage.value = "Stage ${req.targetStage} Evolution Trial incomplete — fulfill all Level, Bond, Steps, Boss, Realm, Menagerie, Catalyst & Tribute gates!"
            return
        }
        val finalBranch = if (curStage == 1) {
            if (chosenBranch == PetEvolutionBranch.NONE) PetEvolutionBranch.VANGUARD else chosenBranch
        } else {
            pet.resolvedBranch().takeIf { it != PetEvolutionBranch.NONE } ?: PetEvolutionBranch.VANGUARD
        }

        val evolvedPet = pet.copy(
            evolutionStage = req.targetStage.toLong(),
            evolutionBranch = finalBranch.name
        )
        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)

        viewModelScope.launch {
            repository.updateOwnedPet(
                currentUserId,
                pet.petType,
                mapOf(
                    "evolutionStage" to req.targetStage.toLong(),
                    "evolutionBranch" to finalBranch.name
                )
            )
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "evolutionShards" to (profile.evolutionShards - req.evolutionShardsNeeded).coerceAtLeast(0L),
                    "mythicCores" to (profile.mythicCores - req.mythicCoresNeeded).coerceAtLeast(0L),
                    "solCoins" to (profile.solCoins - req.solCoinTribute).coerceAtLeast(0L),
                    "sunshards" to (profile.sunshards - req.sunshardTribute).coerceAtLeast(0L),
                    "seasonXp" to (profile.seasonXp + 240L).coerceAtMost(999_999L)
                )
            )
            preferences.updateHardcoreRpgState { hc ->
                val map = hc.activityMasteryXpMap.toMutableMap()
                map[ActivityMasteryType.COMPANION.id] = (map[ActivityMasteryType.COMPANION.id] ?: 0) + 180
                hc.copy(activityMasteryXpMap = map)
            }
            _recentEvolutionCeremonyPet.value = evolvedPet
            publishTownPresenceNow(force = true)
            _bannerMessage.value = "★ PRESTIGE EVOLUTION COMPLETE! ${evolvedPet.resolvedForm().formName} awakened!"
        }
    }

    fun buyPetEvolutionCatalyst(offer: PetEvolutionCatalystOffer) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val menagerieLevel = TownBuildingCatalog.levelOf(profile, TownBuildingId.MENAGERIE)
        if (menagerieLevel < offer.requiredMenagerieLevel) {
            _bannerMessage.value = "Upgrade Pip's Menagerie to Lv.${offer.requiredMenagerieLevel} in Sunhaven Town to unlock ${offer.name}!"
            return
        }
        if (profile.solCoins < offer.coinCost) {
            _bannerMessage.value = "Need ${offer.coinCost} Sol Coins for ${offer.name}."
            return
        }
        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "solCoins" to (profile.solCoins - offer.coinCost).coerceAtLeast(0L),
                    "evolutionShards" to (profile.evolutionShards + offer.grantsEvolutionShards).coerceAtMost(99_999L),
                    "mythicCores" to (profile.mythicCores + offer.grantsMythicCores).coerceAtMost(9_999L),
                    "seasonXp" to (profile.seasonXp + 25L).coerceAtMost(999_999L)
                )
            )
            _bannerMessage.value = "Purchased ${offer.name} at Pip's Pet Store (-${offer.coinCost} Sol Coins)!"
        }
    }

    fun upgradeTownBuilding(buildingId: TownBuildingId) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val curLevels = TownBuildingCatalog.parseLevels(profile.townBuildingLevels).toMutableList()
        val curLevel = curLevels[buildingId.index]
        val cost = TownBuildingCatalog.upgradeCostFor(buildingId, curLevel)
        if (cost == null) {
            _bannerMessage.value = "${buildingId.title} is already Max Level 5!"
            return
        }
        val hc = hardcoreState.value
        if (profile.solCoins < cost.solCoins || profile.sunshards < cost.sunshards || hc.sunsteelOre < cost.oreNeeded) {
            _bannerMessage.value = "Need ${cost.solCoins} Coins, ${cost.sunshards} Shards & ${cost.oreNeeded} Ore to upgrade ${buildingId.title}."
            return
        }
        curLevels[buildingId.index] = cost.nextLevel
        val serialized = TownBuildingCatalog.serializeLevels(curLevels)
        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)

        viewModelScope.launch {
            preferences.updateHardcoreRpgState { cur ->
                cur.copy(sunsteelOre = (cur.sunsteelOre - cost.oreNeeded).coerceAtLeast(0L))
            }
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "solCoins" to (profile.solCoins - cost.solCoins).coerceAtLeast(0L),
                    "sunshards" to (profile.sunshards - cost.sunshards).coerceAtLeast(0L),
                    "townBuildingLevels" to serialized,
                    "seasonXp" to (profile.seasonXp + 110L).coerceAtMost(999_999L)
                )
            )
            _bannerMessage.value = "★ Upgraded ${buildingId.title} to Lv.${cost.nextLevel}! Permanent Town buff increased."
        }
    }

    fun interactWildsNode(node: WildsFieldNode) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        if (node.harvested) return

        if (node.type == WildsNodeType.ROAMING_FOE || node.type == WildsNodeType.GUARDIAN) {
            engageEncounter(forceBoss = node.type == WildsNodeType.GUARDIAN)
            viewModelScope.launch {
                preferences.updateHardcoreRpgState { cur ->
                    val keys = cur.wildsHarvestedKeys.toMutableSet().apply { add(node.id) }
                    cur.copy(wildsHarvestedKeys = keys)
                }
            }
            return
        }

        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        val realmMult = profile.currentRealm.coerceIn(1L, 10L)
        val activeEvent = activeWorldEvent.value.first

        viewModelScope.launch {
            var summaryMsg = ""
            when (node.type) {
                WildsNodeType.CHEST -> {
                    val coins = 8L + realmMult * 3L
                    val shards = 24L + realmMult * 8L
                    repository.updateHeroProfile(
                        currentUserId,
                        mapOf(
                            "solCoins" to (profile.solCoins + coins).coerceAtMost(100_000_000L),
                            "sunshards" to (profile.sunshards + shards).coerceAtMost(100_000_000L),
                            "seasonXp" to (profile.seasonXp + 35L).coerceAtMost(999_999L)
                        )
                    )
                    summaryMsg = "Opened ${node.label}! +$coins Sol Coins & +$shards Sunshards."
                }
                WildsNodeType.ORE_VEIN -> {
                    val ore = 5L + realmMult * 2L
                    preferences.updateHardcoreRpgState { it.copy(sunsteelOre = (it.sunsteelOre + ore).coerceAtMost(999_999L)) }
                    repository.updateHeroProfile(currentUserId, mapOf("seasonXp" to (profile.seasonXp + 25L).coerceAtMost(999_999L)))
                    summaryMsg = "Mined ${node.label}! +$ore Sunsteel Ore."
                }
                WildsNodeType.HERB_GROVE -> {
                    val herbs = 5L + realmMult * 2L
                    val ess = 2L + realmMult
                    preferences.updateHardcoreRpgState {
                        it.copy(
                            aetherHerbs = (it.aetherHerbs + herbs).coerceAtMost(999_999L),
                            elementalEssence = (it.elementalEssence + ess).coerceAtMost(999_999L)
                        )
                    }
                    repository.updateHeroProfile(currentUserId, mapOf("seasonXp" to (profile.seasonXp + 25L).coerceAtMost(999_999L)))
                    summaryMsg = "Foraged ${node.label}! +$herbs Aether Herbs & +$ess Essence."
                }
                WildsNodeType.EVOLUTION_CRYSTAL -> {
                    val gain = if (activeEvent == LiveWorldEventSpec.SOLAR_ECLIPSE_SURGE) 2L else 1L
                    repository.updateHeroProfile(
                        currentUserId,
                        mapOf(
                            "evolutionShards" to (profile.evolutionShards + gain).coerceAtMost(99_999L),
                            "seasonXp" to (profile.seasonXp + 45L).coerceAtMost(999_999L)
                        )
                    )
                    summaryMsg = "Attuned ${node.label}! +$gain Pet Evolution Shard!"
                }
                WildsNodeType.SHRINE -> {
                    val stats = derivedStats.value
                    val maxHp = (stats?.computedMaxHp ?: profile.maxHp.toInt()).toLong()
                    repository.updateHeroProfile(
                        currentUserId,
                        mapOf(
                            "currentHp" to maxHp,
                            "seasonXp" to (profile.seasonXp + 30L).coerceAtMost(999_999L)
                        )
                    )
                    preferences.updateHardcoreRpgState {
                        it.copy(
                            battleWoundStacks = 0,
                            shrineBlessingLabel = "Wilds Dawn Blessing (+15% ATK)",
                            shrineBattlesLeft = 3
                        )
                    }
                    summaryMsg = "Restored full HP, cured wounds & gained +15% ATK Shrine Blessing (3 battles)!"
                }
                else -> Unit
            }

            preferences.updateHardcoreRpgState { cur ->
                val keys = cur.wildsHarvestedKeys.toMutableSet().apply { add(node.id) }
                val mastery = cur.activityMasteryXpMap.toMutableMap()
                mastery[ActivityMasteryType.EXPLORATION.id] = (mastery[ActivityMasteryType.EXPLORATION.id] ?: 0) + 28
                cur.copy(wildsHarvestedKeys = keys, activityMasteryXpMap = mastery)
            }
            if (summaryMsg.isNotBlank()) _bannerMessage.value = summaryMsg
        }
    }

    fun completeAnvilForgeUpgrade(item: InventoryItem, grade: ForgeGrade) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val cost = item.upgradeCost()
        if (profile.sunshards < cost) {
            _bannerMessage.value = "Need $cost Sunshards to temper ${item.name}."
            return
        }
        val forgeBonusPct = TownBuildingCatalog.computeBonuses(profile).masterworkBonusPct
        val mult = grade.statBonusMultiplier + (forgeBonusPct / 100f)
        val atkGain = max(1L, ((3L + item.resolvedRarity().tier * 2L) * mult).roundToInt().toLong())
        val defGain = max(1L, ((2L + item.resolvedRarity().tier) * mult).roundToInt().toLong())
        val hpGain = if (item.hpBonus > 0L) max(4L, (8L * mult).roundToInt().toLong()) else 0L

        haptics.trigger(if (grade == ForgeGrade.MASTERWORK) GameplayHaptic.VICTORY_FANFARE else GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            repository.updateInventoryItem(
                currentUserId,
                item.id,
                mapOf(
                    "upgradeLevel" to (item.upgradeLevel + 1L).coerceAtMost(25L),
                    "attackBonus" to (item.attackBonus + atkGain),
                    "defenseBonus" to (item.defenseBonus + defGain),
                    "hpBonus" to (item.hpBonus + hpGain)
                )
            )
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "sunshards" to (profile.sunshards - cost).coerceAtLeast(0L),
                    "seasonXp" to (profile.seasonXp + 30L).coerceAtMost(999_999L)
                )
            )
            preferences.updateHardcoreRpgState { hc ->
                val map = hc.activityMasteryXpMap.toMutableMap()
                map[ActivityMasteryType.SMITHING.id] = (map[ActivityMasteryType.SMITHING.id] ?: 0) + grade.masteryXp
                hc.copy(activityMasteryXpMap = map)
            }
            _bannerMessage.value = "${grade.name} FORGE! ${item.name} +${item.upgradeLevel + 1} (+$atkGain ATK, +$defGain DEF) • +${grade.masteryXp} Smithing Mastery!"
        }
    }

    fun completeAlchemistBrew(recipe: AlchemistRecipeSpec, grade: BrewGrade) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val hc = hardcoreState.value
        if (hc.aetherHerbs < recipe.herbCost ||
            hc.elementalEssence < recipe.essenceCost ||
            hc.sunsteelOre < recipe.oreCost ||
            profile.solCoins < recipe.solCoinCost
        ) {
            _bannerMessage.value = "Need ${recipe.herbCost} Herbs, ${recipe.essenceCost} Essence, ${recipe.oreCost} Ore & ${recipe.solCoinCost} Coins to brew ${recipe.name}."
            return
        }

        val spireLevel = TownBuildingCatalog.levelOf(profile, TownBuildingId.ALCHEMIST_SPIRE)
        val spireBonusMult = 1f + ((spireLevel - 1) * 0.04f)
        val finalHeal = (recipe.baseHealAmount * grade.potencyMultiplier * spireBonusMult).roundToInt().toLong().coerceAtLeast(40L)
        val qty = (recipe.quantityProduced + grade.extraQuantity).coerceAtLeast(1L)

        haptics.trigger(if (grade == BrewGrade.DOUBLE_BREW) GameplayHaptic.VICTORY_FANFARE else GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            preferences.updateHardcoreRpgState { cur ->
                val map = cur.activityMasteryXpMap.toMutableMap()
                map[ActivityMasteryType.ALCHEMY.id] = (map[ActivityMasteryType.ALCHEMY.id] ?: 0) + grade.masteryXp
                val nextWounds = if (recipe.curesWounds) 0 else cur.battleWoundStacks
                val nextRunes = cur.unlockedRunesMap.toMutableMap()
                if (recipe.grantsRune) {
                    val rune = ElementalAffinity.entries.random()
                    nextRunes[rune.name] = (nextRunes[rune.name] ?: 0) + 1
                }
                cur.copy(
                    aetherHerbs = (cur.aetherHerbs - recipe.herbCost).coerceAtLeast(0L),
                    elementalEssence = (cur.elementalEssence - recipe.essenceCost).coerceAtLeast(0L),
                    sunsteelOre = (cur.sunsteelOre - recipe.oreCost).coerceAtLeast(0L),
                    battleWoundStacks = nextWounds,
                    unlockedRunesMap = nextRunes,
                    activityMasteryXpMap = map
                )
            }
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "solCoins" to (profile.solCoins - recipe.solCoinCost).coerceAtLeast(0L),
                    "seasonXp" to (profile.seasonXp + 28L).coerceAtMost(999_999L)
                )
            )
            if (!recipe.grantsRune) {
                val brewed = InventoryItem(
                    name = if (grade == BrewGrade.DOUBLE_BREW) "Potent ${recipe.name}" else recipe.name,
                    slot = ItemSlot.CONSUMABLE.name,
                    rarity = if (grade == BrewGrade.DOUBLE_BREW) ItemRarity.EPIC.name else ItemRarity.RARE.name,
                    healAmount = finalHeal,
                    quantity = qty,
                    equipped = false,
                    description = "${recipe.description} (${grade.label})"
                )
                repository.addInventoryItem(currentUserId, brewed)
                _bannerMessage.value = "${grade.label}! Created ${qty}x ${brewed.name} (+$finalHeal HP) • +${grade.masteryXp} Alchemy Mastery!"
            } else {
                _bannerMessage.value = "${grade.label}! Distilled a random Elemental Rune • +${grade.masteryXp} Alchemy Mastery!"
            }
        }
    }

    fun claimSeasonTier(tier: Int) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        val spec = SeasonCatalog.tiers.firstOrNull { it.tier == tier } ?: return
        if (profile.seasonXp < spec.xpRequired) {
            _bannerMessage.value = "Need ${spec.xpRequired} Season XP to unlock Tier $tier."
            return
        }
        if (profile.claimedSeasonTiers.contains(tier.toLong())) return

        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        val nextClaimed = (profile.claimedSeasonTiers + tier.toLong()).distinct().sorted()
        viewModelScope.launch {
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "claimedSeasonTiers" to nextClaimed,
                    "solCoins" to (profile.solCoins + spec.solCoins).coerceAtMost(100_000_000L),
                    "sunshards" to (profile.sunshards + spec.sunshards).coerceAtMost(100_000_000L),
                    "evolutionShards" to (profile.evolutionShards + spec.evolutionShards).coerceAtMost(99_999L),
                    "mythicCores" to (profile.mythicCores + spec.mythicCores).coerceAtMost(9_999L)
                )
            )
            if (spec.oreGrant > 0L) {
                preferences.updateHardcoreRpgState { it.copy(sunsteelOre = (it.sunsteelOre + spec.oreGrant).coerceAtMost(999_999L)) }
            }
            _bannerMessage.value = "Claimed Season Tier $tier: ${spec.rewardLabel}!"
        }
    }

    fun strikeWeeklyWorldBoss() {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val energyCost = 20L
        if (profile.strideEnergy < energyCost) {
            _bannerMessage.value = "Need $energyCost Stride Energy to strike the Weekly World Boss! Walk steps to recharge."
            return
        }
        val stats = derivedStats.value ?: return
        val critMult = if (Random.nextInt(100) < stats.critChancePercent) 1.65 else 1.0
        val dmg = ((stats.totalAttack * 4.5 + profile.level * 18) * critMult).toLong().coerceAtLeast(150L)
        val weekKey = currentIsoWeekKey()
        haptics.trigger(GameplayHaptic.SOLAR_CRITICAL)

        viewModelScope.launch {
            repository.ensureWeeklyWorldBoss(currentUserId, weekKey)
            repository.strikeWeeklyWorldBoss(currentUserId, weekKey, profile.displayName, dmg)
                .onSuccess {
                    repository.updateHeroProfile(
                        currentUserId,
                        mapOf(
                            "strideEnergy" to (profile.strideEnergy - energyCost).coerceAtLeast(0L),
                            "seasonXp" to (profile.seasonXp + 55L).coerceAtMost(999_999L)
                        )
                    )
                    if (profile.guildId.isNotBlank()) {
                        repository.contributeGuildProgress(currentUserId, profile.guildId, stepsDelta = 0L, bossDamageDelta = dmg)
                    }
                    _bannerMessage.value = "Struck the Weekly World Boss for $dmg DMG! (+55 Season XP)"
                }
                .onFailure { err ->
                    _bannerMessage.value = "World Boss strike failed: ${err.localizedMessage ?: "retry"}"
                }
        }
    }

    fun claimWorldBossMilestone(tier: Int) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        val myContrib = worldBossContributions.value.firstOrNull { it.userId == currentUserId } ?: return
        val idx = (tier - 1).coerceIn(0, WeeklyWorldBossCatalog.milestoneThresholds.lastIndex)
        val (dmgReq, label) = WeeklyWorldBossCatalog.milestoneThresholds[idx]
        if (myContrib.totalDamage < dmgReq || myContrib.claimedMilestoneTier >= tier.toLong()) return

        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        val coinGain = 20L * tier
        val evoGain = if (tier >= 2) 1L else 0L
        val coreGain = if (tier >= 4) 1L else 0L

        viewModelScope.launch {
            repository.updateWorldBossClaimedMilestone(
                userId = currentUserId,
                weekKey = currentIsoWeekKey(),
                displayName = profile.displayName,
                totalDamage = myContrib.totalDamage,
                strikesCount = myContrib.strikesCount,
                claimedTier = tier.toLong()
            ).onSuccess {
                repository.updateHeroProfile(
                    currentUserId,
                    mapOf(
                        "solCoins" to (profile.solCoins + coinGain).coerceAtMost(100_000_000L),
                        "evolutionShards" to (profile.evolutionShards + evoGain).coerceAtMost(99_999L),
                        "mythicCores" to (profile.mythicCores + coreGain).coerceAtMost(9_999L),
                        "seasonXp" to (profile.seasonXp + 90L).coerceAtMost(999_999L)
                    )
                )
                _bannerMessage.value = "Claimed World Boss Milestone #$tier ($label)!"
            }
        }
    }

    fun createCoopRaid() {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val template = WeeklyWorldBossCatalog.templateForWeek("${currentIsoWeekKey()}_${profile.currentRealm}")
        val scaledHp = (3_500L + profile.level * 220L).coerceAtMost(18_000L)
        haptics.trigger(GameplayHaptic.ENCOUNTER_ALERT)

        viewModelScope.launch {
            repository.createCoopRaid(
                userId = currentUserId,
                hostName = profile.displayName,
                bossName = template.name,
                bossAffinity = template.affinity.name,
                bossMaxHp = scaledHp
            ).onSuccess {
                _bannerMessage.value = "Opened Co-op Raid vs ${template.name}! Up to 4 real players can join."
            }.onFailure { err ->
                _bannerMessage.value = "Could not create raid: ${err.localizedMessage ?: "retry"}"
            }
        }
    }

    fun joinCoopRaid(raidId: String) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            repository.joinCoopRaid(currentUserId, profile.displayName, raidId)
                .onSuccess { _bannerMessage.value = "Joined Co-op Raid party!" }
                .onFailure { err -> _bannerMessage.value = "Could not join raid: ${err.localizedMessage ?: "retry"}" }
        }
    }

    fun strikeCoopRaid(raidId: String, useSurge: Boolean) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val energyCost = if (useSurge) 25L else 10L
        if (profile.strideEnergy < energyCost) {
            _bannerMessage.value = "Need $energyCost Stride Energy for this raid strike!"
            return
        }
        val stats = derivedStats.value ?: return
        val baseDmg = (stats.totalAttack * (if (useSurge) 5.2 else 2.8) + profile.level * 12).toLong().coerceAtLeast(100L)
        haptics.trigger(if (useSurge) GameplayHaptic.SOLAR_CRITICAL else GameplayHaptic.PLAYER_STRIKE)

        viewModelScope.launch {
            repository.strikeCoopRaid(
                userId = currentUserId,
                displayName = profile.displayName,
                raidId = raidId,
                damage = baseDmg,
                actionLabel = if (useSurge) "Solar Surge" else "Team Strike"
            ).onSuccess { defeatedNow ->
                repository.updateHeroProfile(
                    currentUserId,
                    mapOf(
                        "strideEnergy" to (profile.strideEnergy - energyCost).coerceAtLeast(0L),
                        "seasonXp" to (profile.seasonXp + 40L).coerceAtMost(999_999L)
                    )
                )
                _bannerMessage.value = if (defeatedNow) {
                    "★ CO-OP RAID VICTORY! Dealt $baseDmg DMG — claim your Raid Spoils!"
                } else {
                    "Dealt $baseDmg DMG to the Co-op Raid Boss!"
                }
            }.onFailure { err ->
                _bannerMessage.value = "Raid strike failed: ${err.localizedMessage ?: "retry"}"
            }
        }
    }

    fun claimCoopRaidReward(raidId: String) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            repository.markCoopRaidRewardClaimed(currentUserId, raidId)
                .onSuccess {
                    repository.updateHeroProfile(
                        currentUserId,
                        mapOf(
                            "solCoins" to (profile.solCoins + 45L).coerceAtMost(100_000_000L),
                            "sunshards" to (profile.sunshards + 140L).coerceAtMost(100_000_000L),
                            "evolutionShards" to (profile.evolutionShards + 1L).coerceAtMost(99_999L),
                            "seasonXp" to (profile.seasonXp + 120L).coerceAtMost(999_999L)
                        )
                    )
                    preferences.updateHardcoreRpgState { hc ->
                        val nextRelics = hc.collectedRelicIds.toMutableSet()
                        RelicSetSpec.nextUncollectedRelic(nextRelics)?.let { (_, pieceId) -> nextRelics.add(pieceId) }
                        hc.copy(collectedRelicIds = nextRelics)
                    }
                    _bannerMessage.value = "Claimed Co-op Raid Spoils! +45 Coins, +140 Shards, +1 Evolution Shard & Relic Progress!"
                }
        }
    }

    fun closeCoopRaid(raidId: String) {
        viewModelScope.launch {
            repository.deleteCoopRaid(currentUserId, raidId)
        }
    }

    fun createGuild(rawName: String, rawMotto: String) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (profile.guildId.isNotBlank()) {
            _bannerMessage.value = "Leave your current guild before founding a new one."
            return
        }
        if (profile.guildCrestsOwned < 1L) {
            _bannerMessage.value = "Buy a Guild Charter Crest from Zari's Market in Sunhaven Town first!"
            return
        }
        val cleanName = rawName.trim().take(28)
        if (cleanName.length < 3) {
            _bannerMessage.value = "Guild name must be at least 3 characters."
            return
        }
        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            repository.createGuild(
                userId = currentUserId,
                leaderName = profile.displayName,
                guildName = cleanName,
                motto = rawMotto.trim().ifBlank { "Every step lights the dawn." }.take(64),
                emblemHex = 0xFFF59E0BL,
                initialWeeklySteps = profile.weeklySteps
            ).onSuccess { newGuildId ->
                repository.updateHeroProfile(
                    currentUserId,
                    mapOf(
                        "guildId" to newGuildId,
                        "guildCrestsOwned" to (profile.guildCrestsOwned - 1L).coerceAtLeast(0L),
                        "seasonXp" to (profile.seasonXp + 150L).coerceAtMost(999_999L)
                    )
                )
                _bannerMessage.value = "Founded Guild \"$cleanName\"! Walk together to grow your Guild Step Vault."
            }.onFailure { err ->
                _bannerMessage.value = "Could not create guild: ${err.localizedMessage ?: "retry"}"
            }
        }
    }

    fun joinGuild(guildId: String) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (profile.guildId.isNotBlank()) {
            _bannerMessage.value = "Leave your current guild first."
            return
        }
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            repository.joinGuild(currentUserId, profile.displayName, guildId)
                .onSuccess {
                    repository.updateHeroProfile(currentUserId, mapOf("guildId" to guildId))
                    _bannerMessage.value = "Joined Guild! Your daily steps now fuel the Guild Vault."
                }
                .onFailure { err ->
                    _bannerMessage.value = "Could not join guild: ${err.localizedMessage ?: "retry"}"
                }
        }
    }

    fun leaveGuild() {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (profile.guildId.isBlank()) return
        viewModelScope.launch {
            repository.leaveGuild(currentUserId, profile.guildId)
            repository.updateHeroProfile(currentUserId, mapOf("guildId" to ""))
            _bannerMessage.value = "Left your guild."
        }
    }

    fun sendGuildChatMessage(rawText: String) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (profile.guildId.isBlank()) return
        val clean = rawText.trim().take(240)
        if (clean.isEmpty()) return
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            repository.sendGuildChatMessage(currentUserId, profile.guildId, profile.displayName, profile.level, clean)
        }
    }

    // --- Destructive Reset All Progress (with explicit UI confirmation modal) ---

    fun resetAllProgress() {
        haptics.trigger(GameplayHaptic.DAMAGE_TAKEN)
        viewModelScope.launch {
            _isSyncingNow.value = true
            try {
                _battleState.value = BattleState()
                _lastExpeditionReport.value = null
                _pendingChestModal.value = null
                _lastOpenedChestResult.value = null
                _battleMode.value = BattleMode.REALM_CAMPAIGN
                _localConsumedChestIds.value = emptySet()
                recentlyAwardedChestIds.clear()
                _manuallyWokenSleepMarkerMs.value = 0L

                offlineSyncDao.deleteAllBatchesForUser(currentUserId)
                offlineSyncDao.deleteAllLogsForUser(currentUserId)
                preferences.resetAllRpgAndStepProgress()
                preferences.setManuallyWokenSleepMs(0L)
                preferences.setWatchLiveSleeping(false)
                com.example.widget.SolstrideWidgetSnapshotStore.resetPetLedgerForNewGame(appContext)
                stepTracker.resetSession()

                val res = repository.resetAllUserProgress(currentUserId, initialDisplayName)
                if (res.isSuccess) {
                    _bannerMessage.value = "All progress wiped. Reborn as a Level 1 Novice with a starter weapon, zero armor/resources, and 20 Sol Coins."
                } else {
                    _bannerMessage.value = "Local data reset; cloud reset error: ${res.exceptionOrNull()?.localizedMessage ?: "retry when online"}"
                }
            } finally {
                _isSyncingNow.value = false
            }
        }
    }

    fun salvageItem(item: InventoryItem) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        if (item.equipped) {
            _bannerMessage.value = "Unequip ${item.name} before salvaging."
            return
        }
        val gain = item.salvageSunshards()
        val oreGain = (1L + item.resolvedRarity().tier).toLong()
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            repository.deleteInventoryItem(currentUserId, item.id)
            preferences.updateHardcoreRpgState { cur ->
                val map = cur.itemDurabilityMap.toMutableMap()
                map.remove(item.id)
                cur.copy(
                    sunsteelOre = (cur.sunsteelOre + oreGain).coerceAtMost(999_999L),
                    itemDurabilityMap = map
                )
            }
            repository.updateHeroProfile(
                currentUserId,
                mapOf("sunshards" to (profile.sunshards + gain).coerceAtMost(100_000_000L))
            )
            _bannerMessage.value = "Salvaged ${item.name} for +$gain Sunshards & +$oreGain Sunsteel Ore."
        }
    }

    private fun useConsumableOutOfCombat(elixir: InventoryItem) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val items = (inventoryState.value as? UiState.Success)?.data ?: return
        val stats = derivedStats.value ?: return
        val curesWound = elixir.name.contains("Panacea", ignoreCase = true)
        if (profile.currentHp >= stats.computedMaxHp.toLong() && (!curesWound || hardcoreState.value.battleWoundStacks == 0)) {
            _bannerMessage.value = "HP is already full."
            return
        }
        val healedHp = (profile.currentHp + elixir.healAmount.coerceAtLeast(35L))
            .coerceAtMost(stats.computedMaxHp.toLong())
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            consumeElixirItem(elixir.id, items)
            if (curesWound) {
                preferences.updateHardcoreRpgState { cur -> cur.copy(battleWoundStacks = 0) }
            }
            repository.updateHeroProfile(currentUserId, mapOf("currentHp" to healedHp))
            _bannerMessage.value = if (curesWound) {
                "Used ${elixir.name} — Battle Wounds cured & restored HP ($healedHp/${stats.computedMaxHp})!"
            } else {
                "Used ${elixir.name} (HP: $healedHp/${stats.computedMaxHp})."
            }
        }
    }

    private suspend fun consumeElixirItem(itemId: String, items: List<InventoryItem>) {
        val target = items.firstOrNull { it.id == itemId } ?: return
        if (target.quantity > 1L) {
            repository.updateInventoryItem(currentUserId, itemId, mapOf("quantity" to target.quantity - 1L))
        } else {
            repository.deleteInventoryItem(currentUserId, itemId)
        }
    }

    private suspend fun syncLeaderboard(
        profile: HeroProfile,
        heroLevel: Long,
        heroPower: Long,
        bossesDefeated: Long
    ) {
        repository.upsertLeaderboard(
            userId = currentUserId,
            displayName = profile.displayName,
            heroClass = profile.heroClass,
            heroLevel = heroLevel,
            heroPower = heroPower,
            weeklySteps = profile.weeklySteps,
            weekKey = profile.weekKey,
            bossesDefeated = bossesDefeated,
            prestigeLevel = profile.prestigeLevel,
            highestSpireFloor = profile.highestSpireFloor
        )
    }

    // --- Voice Command Dispatcher & Display / Battery Preferences ---

    fun dispatchVoiceCommand(action: VoiceCommandAction) {
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        when (action) {
            VoiceCommandAction.STRIKE -> {
                if (_battleState.value.active) performStrike() else engageEncounter()
            }
            VoiceCommandAction.SOLAR_SKILL -> {
                if (_battleState.value.active) performSolarSkill() else _bannerMessage.value = "Voice: Start a battle first to unleash Solar Skill."
            }
            VoiceCommandAction.GUARD -> {
                if (_battleState.value.active) performGuard() else _bannerMessage.value = "Voice: Guard is available during active combat."
            }
            VoiceCommandAction.HEAL -> {
                if (_battleState.value.active) performBattleElixir() else {
                    val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
                    val elixir = items.firstOrNull { it.resolvedSlot() == ItemSlot.CONSUMABLE }
                    if (elixir != null) useConsumableOutOfCombat(elixir) else restAtSunlitCamp()
                }
            }
            VoiceCommandAction.RETREAT -> {
                if (_battleState.value.active) performRetreat()
            }
            VoiceCommandAction.ENGAGE_BATTLE -> engageEncounter()
            VoiceCommandAction.OPEN_EXPEDITION -> selectTab(SolstrideTab.EXPEDITION)
            VoiceCommandAction.OPEN_PETS -> selectTab(SolstrideTab.PETS)
            VoiceCommandAction.OPEN_INVENTORY -> selectTab(SolstrideTab.INVENTORY)
            VoiceCommandAction.OPEN_LEADERBOARD -> selectTab(SolstrideTab.LEADERBOARD)
            VoiceCommandAction.SYNC_WATCH -> manualSyncWatchAndCloud()
            VoiceCommandAction.TOGGLE_DISPLAY_MODE -> toggleDisplayMode()
        }
    }

    fun toggleDisplayMode() {
        val next = if (settings.value.displayMode == SolstrideDisplayMode.SUNLIT_PARCHMENT) {
            SolstrideDisplayMode.OBSIDIAN_OLED
        } else {
            SolstrideDisplayMode.SUNLIT_PARCHMENT
        }
        viewModelScope.launch { preferences.setDisplayMode(next) }
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
    }

    fun toggleHaptics() {
        val next = !settings.value.hapticsEnabled
        viewModelScope.launch { preferences.setHapticsEnabled(next) }
    }

    fun toggleBatteryBatching() {
        val next = !settings.value.batteryBatchingEnabled
        viewModelScope.launch {
            preferences.setBatteryBatchingEnabled(next)
            if (settings.value.pedometerEnabled && !isWatchConnected.value) {
                stepTracker.startTracking(next)
            }
        }
    }

    private fun applyXpAndLevelUp(
        profile: HeroProfile,
        xpGain: Long,
        currentStats: DerivedHeroStats
    ): HeroProfile {
        var lvl = profile.level
        var xp = profile.xp + xpGain
        var needed = profile.xpToNextLevel.coerceAtLeast(100L)
        var points = profile.unspentPoints
        var maxHp = currentStats.computedMaxHp.toLong()
        var hp = profile.currentHp
        var power = currentStats.computedHeroPower.toLong()

        while (xp >= needed && lvl < 999L) {
            xp -= needed
            lvl += 1L
            needed = (120L + (lvl - 1L) * 65L)
            points += 2L
            maxHp += 12L
            hp = maxHp // Full heal on level-up
            power += 35L
        }
        return profile.copy(
            level = lvl,
            xp = xp,
            xpToNextLevel = needed,
            unspentPoints = points,
            maxHp = maxHp,
            currentHp = hp.coerceAtMost(maxHp),
            heroPower = power
        )
    }

    override fun onCleared() {
        super.onCleared()
        BackgroundSyncGuard.setForeground(false)
        watchBridge.release()
        stepTracker.unregisterWatchDisconnectReceiver()
        stepTracker.stopTracking()
    }

    // --- Step & Sleep RPG Longevity Systems: Sleep Sanctuary, Multi-Room Step Dungeons, Constellations, 5-Stat Attributes, Gear Affixes & Slayer Contracts ---

    fun attuneSleepSanctuaryRest(sleepHours: Float) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        val stats = derivedStats.value ?: return
        val hc = hardcoreState.value
        val todayKey = currentCalendarDateKey()
        if (hc.lastSanctuaryRestDateKey == todayKey) {
            _bannerMessage.value = "Sleep Sanctuary already attuned today ($todayKey)! Walk real steps or sync new Watch Sleep."
            return
        }
        val durationMin = (sleepHours.coerceIn(1f, 12f) * 60f).roundToInt()
        val dreamTier = DreamRestTier.forSleepMinutes(durationMin)
        val maxSta = hc.ComputedMaxStamina(profile.endurance)
        val targetSta = ((maxSta * dreamTier.staminaRestorePct) / 100f).roundToInt()
            .coerceIn(30, (maxSta * 1.25f).roundToInt())
        val restoredSta = max(hc.heroStamina, targetSta)
        val fraction = (durationMin / 450f).coerceIn(0.30f, 1.0f)
        val energyGrant = (stats.computedMaxEnergy * fraction * 0.65f).roundToInt().toLong().coerceAtLeast(18L)
        val nextEnergy = (profile.strideEnergy + energyGrant).coerceAtMost(stats.computedMaxEnergy.toLong())
        val nextHp = stats.computedMaxHp.toLong()
        val nextWounds = (hc.battleWoundStacks - dreamTier.woundsCured).coerceAtLeast(0)
        val curedCount = (hc.battleWoundStacks - nextWounds).coerceAtLeast(0)

        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            preferences.updateHardcoreRpgState { cur ->
                cur.copy(
                    heroStamina = restoredSta,
                    maxHeroStamina = maxSta,
                    activeDreamTierId = dreamTier.id,
                    lastSleepDurationMinutes = durationMin,
                    lastSanctuaryRestDateKey = todayKey,
                    dreamShards = (cur.dreamShards + dreamTier.dreamShardGrant).coerceAtMost(99_999L),
                    battleWoundStacks = nextWounds,
                    restedClarityBattlesLeft = 5,
                    restedClarityPct = (fraction * 100f).roundToInt().coerceIn(35, 100)
                )
            }
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "currentHp" to nextHp,
                    "strideEnergy" to nextEnergy
                )
            )
            val woundMsg = if (curedCount > 0) ", cured $curedCount Battle Wound(s)" else ""
            _bannerMessage.value = "🌙 Attuned ${dreamTier.title} (${String.format("%.1f", sleepHours)}h)! Stamina -> $restoredSta/$maxSta, +$energyGrant Battle EN, +${dreamTier.dreamShardGrant} Dream Shard(s)$woundMsg!"
        }
    }

    fun selectStepDungeon(dungeonId: String) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        val dungeon = StepDungeonCatalog.byId(dungeonId)
        if (profile.currentRealm < dungeon.recommendedRealm && profile.level < dungeon.recommendedRealm * 3L) {
            _bannerMessage.value = "Requires Realm ${dungeon.recommendedRealm} (or Hero Lv.${dungeon.recommendedRealm * 3}) to enter ${dungeon.name}!"
            return
        }
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            preferences.updateHardcoreRpgState { hc ->
                if (hc.activeDungeonId == dungeon.id) hc
                else hc.copy(
                    activeDungeonId = dungeon.id,
                    activeDungeonRoomIndex = 0,
                    activeDungeonRoomSteps = 0
                )
            }
            _bannerMessage.value = "Entered Step Dungeon: ${dungeon.name}! Walk real steps to traverse Chamber 1."
        }
    }

    fun sprintDungeonCorridorWithEnergy() {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val hc = hardcoreState.value
        val energyCost = 18L
        val staminaCost = 12
        val stepBoost = 400
        val reqSteps = hc.effectiveChamberStepsRequired()
        if (hc.activeDungeonRoomSteps >= reqSteps) {
            _bannerMessage.value = "Chamber step threshold reached! Tap Resolve Chamber to claim or fight."
            return
        }
        var nextEnergy = profile.strideEnergy
        var nextStamina = hc.heroStamina
        if (profile.strideEnergy >= energyCost) {
            nextEnergy = (profile.strideEnergy - energyCost).coerceAtLeast(0L)
        } else if (hc.heroStamina >= staminaCost) {
            nextStamina = (hc.heroStamina - staminaCost).coerceAtLeast(0)
        } else {
            _bannerMessage.value = "Need $energyCost Step Battle Energy (or $staminaCost Sleep Stamina) to force-march +$stepBoost Dungeon Steps! Walk real steps or rest."
            return
        }

        haptics.trigger(GameplayHaptic.PLAYER_STRIKE)
        viewModelScope.launch {
            if (nextEnergy != profile.strideEnergy) {
                repository.updateHeroProfile(currentUserId, mapOf("strideEnergy" to nextEnergy))
            }
            preferences.updateHardcoreRpgState { cur ->
                cur.copy(
                    heroStamina = nextStamina,
                    activeDungeonRoomSteps = (cur.activeDungeonRoomSteps + stepBoost).coerceAtMost(reqSteps)
                )
            }
            _bannerMessage.value = "⚡ Dungeon Pace Surge! Advanced +$stepBoost corridor steps in ${hc.activeDungeon().name}."
        }
    }

    fun advanceOrResolveDungeonChamber() {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val hc = hardcoreState.value
        val dungeon = hc.activeDungeon()
        val chamber = hc.activeDungeonChamber()
        val reqSteps = hc.effectiveChamberStepsRequired()
        if (hc.activeDungeonRoomSteps < reqSteps) {
            val remaining = (reqSteps - hc.activeDungeonRoomSteps).coerceAtLeast(1)
            _bannerMessage.value = "Walk $remaining more real steps (or use Pace Surge) to reach ${chamber.title}!"
            return
        }

        when (chamber.type) {
            DungeonChamberType.FINAL_BOSS_VAULT -> {
                if (_battleState.value.active) {
                    _activeTab.value = SolstrideTab.BATTLE
                    return
                }
                if (hc.heroStamina < chamber.staminaCost && profile.strideEnergy < 18L) {
                    _bannerMessage.value = "Need ${chamber.staminaCost} Sleep Stamina (or 18 Step Battle Energy) to challenge ${dungeon.bossName}!"
                    return
                }
                val useStamina = hc.heroStamina >= chamber.staminaCost
                val nextStamina = if (useStamina) (hc.heroStamina - chamber.staminaCost).coerceAtLeast(0) else hc.heroStamina
                val nextEnergy = if (!useStamina) (profile.strideEnergy - 18L).coerceAtLeast(0L) else profile.strideEnergy

                val battleReadyProfile = profile.copy(strideEnergy = nextEnergy)
                val spawned = CombatEngine.spawnDungeonBossEncounter(
                    profile = battleReadyProfile,
                    dungeon = dungeon,
                    hardcoreState = hc
                )
                _battleState.value = spawned
                _activeTab.value = SolstrideTab.BATTLE
                haptics.trigger(GameplayHaptic.ENCOUNTER_ALERT)

                viewModelScope.launch {
                    preferences.updateHardcoreRpgState { it.copy(heroStamina = nextStamina) }
                    if (nextEnergy != profile.strideEnergy) {
                        repository.updateHeroProfile(currentUserId, mapOf("strideEnergy" to nextEnergy))
                    }
                    _bannerMessage.value = "⚔️ Dungeon Sovereign Engaged: ${dungeon.bossName}! Defeat the boss to unlock the Pinnacle Dungeon Chest!"
                }
            }

            DungeonChamberType.SUNLIT_ALTAR -> {
                haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
                val maxSta = hc.ComputedMaxStamina(profile.endurance)
                val nextSta = (hc.heroStamina + 25).coerceAtMost(maxSta)
                val nextRoom = (hc.activeDungeonRoomIndex + 1).coerceAtMost(dungeon.chambers.lastIndex)
                viewModelScope.launch {
                    preferences.updateHardcoreRpgState { cur ->
                        cur.copy(
                            heroStamina = nextSta,
                            battleWoundStacks = (cur.battleWoundStacks - 1).coerceAtLeast(0),
                            activeDungeonRoomIndex = nextRoom,
                            activeDungeonRoomSteps = 0
                        )
                    }
                    repository.updateHeroProfile(
                        currentUserId,
                        mapOf("currentHp" to profile.maxHp)
                    )
                    _bannerMessage.value = "💧 Cleared ${chamber.title}! Restored +25 Sleep Stamina, Full HP & advanced to Chamber ${nextRoom + 1}."
                }
            }

            DungeonChamberType.RELIC_CHAMBER -> {
                haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
                val nextRoom = (hc.activeDungeonRoomIndex + 1).coerceAtMost(dungeon.chambers.lastIndex)
                val shardGrant = 140L + dungeon.recommendedRealm * 55L
                val oreGrant = 3L + (dungeon.recommendedRealm / 2)
                val herbGrant = 2L + (dungeon.recommendedRealm / 3)
                viewModelScope.launch {
                    preferences.updateHardcoreRpgState { cur ->
                        cur.copy(
                            sunsteelOre = (cur.sunsteelOre + oreGrant).coerceAtMost(999_999L),
                            aetherHerbs = (cur.aetherHerbs + herbGrant).coerceAtMost(999_999L),
                            elementalEssence = (cur.elementalEssence + 2L).coerceAtMost(999_999L),
                            activeDungeonRoomIndex = nextRoom,
                            activeDungeonRoomSteps = 0
                        )
                    }
                    repository.updateHeroProfile(
                        currentUserId,
                        mapOf("sunshards" to (profile.sunshards + shardGrant).coerceAtMost(100_000_000L))
                    )
                    _bannerMessage.value = "🏺 Salvaged ${chamber.title}! +$shardGrant Sunshards, +$oreGrant Ore, +$herbGrant Herbs (Boss Chest awaits at Final Chamber!)."
                }
            }

            DungeonChamberType.ENTRANCE_GATE,
            DungeonChamberType.STEP_CORRIDOR,
            DungeonChamberType.TRAP_GAUNTLET,
            DungeonChamberType.ELITE_WARDEN -> {
                if (hc.heroStamina < chamber.staminaCost && profile.strideEnergy < 10L) {
                    _bannerMessage.value = "Need ${chamber.staminaCost} Sleep Stamina (or 10 Step Battle Energy) to clear ${chamber.title}!"
                    return
                }
                val useSta = hc.heroStamina >= chamber.staminaCost
                val nextSta = if (useSta) (hc.heroStamina - chamber.staminaCost).coerceAtLeast(0) else hc.heroStamina
                val nextEn = if (!useSta) (profile.strideEnergy - 10L).coerceAtLeast(0L) else profile.strideEnergy
                val nextRoom = (hc.activeDungeonRoomIndex + 1).coerceAtMost(dungeon.chambers.lastIndex)
                val stats = derivedStats.value
                val xpGrant = (95L + dungeon.recommendedRealm * 45L)
                val shardGrant = (110L + dungeon.recommendedRealm * 40L)
                val coinGrant = (25L + dungeon.recommendedRealm * 12L)
                haptics.trigger(GameplayHaptic.PLAYER_STRIKE)

                viewModelScope.launch {
                    preferences.updateHardcoreRpgState { cur ->
                        val nextKills = cur.slayerContractKillsMap.toMutableMap()
                        SlayerContractCatalog.contracts.filter { !it.requiresBossOrDungeon }.forEach { c ->
                            nextKills[c.id] = ((nextKills[c.id] ?: 0) + 1).coerceAtMost(9_999)
                        }
                        cur.copy(
                            heroStamina = nextSta,
                            sunsteelOre = (cur.sunsteelOre + 2L).coerceAtMost(999_999L),
                            activeDungeonRoomIndex = nextRoom,
                            activeDungeonRoomSteps = 0,
                            slayerContractKillsMap = nextKills
                        )
                    }
                    val baseProfile = profile.copy(
                        strideEnergy = nextEn,
                        sunshards = (profile.sunshards + shardGrant).coerceAtMost(100_000_000L),
                        solCoins = (profile.solCoins + coinGrant).coerceAtMost(100_000_000L)
                    )
                    val leveled = if (stats != null) applyXpAndLevelUp(baseProfile, xpGrant, stats) else baseProfile
                    repository.updateHeroProfile(
                        currentUserId,
                        mapOf(
                            "strideEnergy" to leveled.strideEnergy,
                            "sunshards" to leveled.sunshards,
                            "solCoins" to leveled.solCoins,
                            "level" to leveled.level,
                            "xp" to leveled.xp,
                            "xpToNextLevel" to leveled.xpToNextLevel,
                            "unspentPoints" to leveled.unspentPoints,
                            "maxHp" to leveled.maxHp,
                            "currentHp" to leveled.currentHp,
                            "heroPower" to leveled.heroPower
                        )
                    )
                    _bannerMessage.value = "⚔️ Conquered ${chamber.title}! +$xpGrant XP, +$shardGrant Sunshards, +$coinGrant Sol Coins -> Advanced to Chamber ${nextRoom + 1}/${dungeon.chambers.size}!"
                }
            }
        }
    }

    fun upgradeConstellationNode(nodeId: String) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        val node = ConstellationCatalog.byId(nodeId) ?: return
        val hc = hardcoreState.value
        val curRank = hc.constellationRank(nodeId)
        if (curRank >= node.maxRank) {
            _bannerMessage.value = "${node.title} is already at Maximum Constellation Rank (${node.maxRank}/${node.maxRank})!"
            return
        }
        val shardCost = node.dreamShardCost * (curRank + 1)
        val sunshardCost = node.sunshardCost * (curRank + 1)
        if (hc.dreamShards < shardCost) {
            _bannerMessage.value = "Need $shardCost Dream Shards (have ${hc.dreamShards})! Attune sleep in the Sleep Sanctuary or clear Step Dungeons."
            return
        }
        if (profile.sunshards < sunshardCost) {
            _bannerMessage.value = "Need $sunshardCost Sunshards (have ${profile.sunshards}) to ignite ${node.title}!"
            return
        }

        haptics.trigger(GameplayHaptic.SOLAR_CRITICAL)
        viewModelScope.launch {
            var updatedHc = hc
            preferences.updateHardcoreRpgState { cur ->
                val nextRanks = cur.constellationRanksMap.toMutableMap()
                nextRanks[nodeId] = (curRank + 1).coerceAtMost(node.maxRank)
                cur.copy(
                    dreamShards = (cur.dreamShards - shardCost).coerceAtLeast(0L),
                    constellationRanksMap = nextRanks
                ).also { updatedHc = it }
            }
            val items = (inventoryState.value as? UiState.Success)?.data ?: emptyList()
            val stats = computeDerivedStats(profile, items, equippedPet.value, updatedHc)
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "sunshards" to (profile.sunshards - sunshardCost).coerceAtLeast(0L),
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "maxStrideEnergy" to stats.computedMaxEnergy.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            _bannerMessage.value = "✨ Ignited Constellation ${node.title} (Rank ${curRank + 1}/${node.maxRank})! ${node.summary}"
        }
    }

    fun allocateExtendedAttribute(isAgility: Boolean) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (profile.unspentPoints <= 0L) {
            _bannerMessage.value = "No unspent Hero Attribute Points! Walk steps or clear battles to level up."
            return
        }
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            var updatedHc = hardcoreState.value
            preferences.updateHardcoreRpgState { cur ->
                if (isAgility) {
                    cur.copy(agilityStat = (cur.agilityStat + 1).coerceAtMost(999)).also { updatedHc = it }
                } else {
                    cur.copy(perceptionStat = (cur.perceptionStat + 1).coerceAtMost(999)).also { updatedHc = it }
                }
            }
            val items = (inventoryState.value as? UiState.Success)?.data ?: emptyList()
            val stats = computeDerivedStats(profile, items, equippedPet.value, updatedHc)
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "unspentPoints" to (profile.unspentPoints - 1L).coerceAtLeast(0L),
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "maxStrideEnergy" to stats.computedMaxEnergy.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            val label = if (isAgility) "Agility (+ATK, +Crit & Dodge Reflex)" else "Perception (+Armor Pierce, +Crit & Step Loot)"
            _bannerMessage.value = "Allocated +1 $label!"
        }
    }

    fun reforgeItemAffixes(item: InventoryItem) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!item.resolvedSlot().isEquipment) {
            _bannerMessage.value = "Only Weapons, Armor, and Relics can be reforged with Prefix & Suffix Affixes."
            return
        }
        val hc = hardcoreState.value
        val oreCost = 3L
        val essenceCost = 2L
        val shardCost = 220L
        if (hc.sunsteelOre < oreCost || hc.elementalEssence < essenceCost || profile.sunshards < shardCost) {
            _bannerMessage.value = "Need $oreCost Sunsteel Ore, $essenceCost Elemental Essence & $shardCost Sunshards to reforge Gear Affixes!"
            return
        }
        val seed = (System.currentTimeMillis() xor item.id.hashCode().toLong()).toInt()
        val (prefix, suffix) = GearAffixCatalog.rollRandomPair(seed)

        haptics.trigger(GameplayHaptic.SOLAR_CRITICAL)
        viewModelScope.launch {
            var updatedHc = hc
            preferences.updateHardcoreRpgState { cur ->
                val nextAffixes = cur.itemAffixMap.toMutableMap()
                nextAffixes[item.id] = "${prefix.id}|${suffix.id}"
                val nextMastery = cur.activityMasteryXpMap.toMutableMap()
                nextMastery[ActivityMasteryType.SMITHING.id] =
                    (nextMastery[ActivityMasteryType.SMITHING.id] ?: 0) + 25
                cur.copy(
                    sunsteelOre = (cur.sunsteelOre - oreCost).coerceAtLeast(0L),
                    elementalEssence = (cur.elementalEssence - essenceCost).coerceAtLeast(0L),
                    itemAffixMap = nextAffixes,
                    activityMasteryXpMap = nextMastery
                ).also { updatedHc = it }
            }
            val items = (inventoryState.value as? UiState.Success)?.data ?: emptyList()
            val stats = computeDerivedStats(profile, items, equippedPet.value, updatedHc)
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "sunshards" to (profile.sunshards - shardCost).coerceAtLeast(0L),
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "maxStrideEnergy" to stats.computedMaxEnergy.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            _bannerMessage.value = "🔨 Reforged [${prefix.label} ${item.name} ${suffix.label}]! (${prefix.summary()} • ${suffix.summary()})"
        }
    }

    fun claimSlayerContract(contractId: String) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        val contract = SlayerContractCatalog.contracts.firstOrNull { it.id == contractId } ?: return
        val hc = hardcoreState.value
        val curSteps = hc.slayerContractStepsMap[contract.id] ?: 0
        val curKills = hc.slayerContractKillsMap[contract.id] ?: 0
        if (curSteps < contract.stepsRequired || curKills < contract.killsRequired) {
            _bannerMessage.value = "Contract incomplete! Need ${contract.stepsRequired} steps ($curSteps) & ${contract.killsRequired} kills ($curKills)."
            return
        }

        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            preferences.updateHardcoreRpgState { cur ->
                val nextSteps = cur.slayerContractStepsMap.toMutableMap()
                val nextKills = cur.slayerContractKillsMap.toMutableMap()
                nextSteps[contract.id] = 0
                nextKills[contract.id] = 0
                cur.copy(
                    slayerContractStepsMap = nextSteps,
                    slayerContractKillsMap = nextKills,
                    slayerContractsCompleted = cur.slayerContractsCompleted + 1,
                    dreamShards = (cur.dreamShards + contract.dreamShardReward).coerceAtMost(99_999L),
                    sunsteelOre = (cur.sunsteelOre + contract.oreReward).coerceAtMost(999_999L)
                )
            }
            val stats = derivedStats.value
            val baseProfile = profile.copy(
                sunshards = (profile.sunshards + contract.sunshardReward).coerceAtMost(100_000_000L),
                solCoins = (profile.solCoins + contract.solCoinReward).coerceAtMost(100_000_000L),
                evolutionShards = (profile.evolutionShards + contract.evolutionShardReward).coerceAtMost(99_999L)
            )
            val leveled = if (stats != null) applyXpAndLevelUp(baseProfile, contract.xpReward, stats) else baseProfile
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "sunshards" to leveled.sunshards,
                    "solCoins" to leveled.solCoins,
                    "evolutionShards" to leveled.evolutionShards,
                    "level" to leveled.level,
                    "xp" to leveled.xp,
                    "xpToNextLevel" to leveled.xpToNextLevel,
                    "unspentPoints" to leveled.unspentPoints,
                    "maxHp" to leveled.maxHp,
                    "currentHp" to leveled.currentHp,
                    "heroPower" to leveled.heroPower
                )
            )
            _bannerMessage.value = "📜 Claimed Slayer Contract [${contract.title}]! +${contract.xpReward} XP, +${contract.solCoinReward} Sol Coins, +${contract.dreamShardReward} Dream Shard(s)!"
        }
    }

    private companion object {
        const val TAG = "SolstrideVM"
        const val WATCH_AUTO_SYNC_INTERVAL_MS = 5_000L
        const val WATCH_ACTIVE_GRACE_MS = 10L * 60L * 1000L
    }
}
