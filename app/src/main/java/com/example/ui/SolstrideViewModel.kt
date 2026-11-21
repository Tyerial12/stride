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
import com.example.data.model.ElementalAffinity
import com.example.data.model.GearPerkType
import com.example.data.model.HardcoreRpgState
import com.example.data.model.HeroClass
import com.example.data.model.HeroProfile
import com.example.data.model.HeroTitleSpec
import com.example.data.model.InventoryItem
import com.example.data.model.ItemRarity
import com.example.data.model.ItemSlot
import com.example.data.model.LeaderboardEntry
import com.example.data.model.LootChest
import com.example.data.model.MercenaryContractSpec
import com.example.data.model.OwnedPet
import com.example.data.model.PetCatalog
import com.example.data.model.PetFoodSpec
import com.example.data.model.PetGearSlot
import com.example.data.model.PetGearSpec
import com.example.data.model.PetType
import com.example.data.model.StepSource
import com.example.data.model.UiState
import com.example.data.model.computeDerivedStats
import com.example.data.model.currentCalendarDateKey
import com.example.data.model.currentIsoWeekKey
import com.example.domain.AlchemistRecipeSpec
import com.example.domain.BattleMode
import com.example.domain.BattleState
import com.example.domain.BlackMarketOfferSpec
import com.example.domain.ChestOpenResult
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
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    INVENTORY("inventory"),
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

    val isHeroSleeping: StateFlow<Boolean> = combine(_watchSleepState, _manuallyWokenSleepMarkerMs) { snap, wokenMarker ->
        snap.isSleepingNow && (snap.activeSessionMarkerMs == 0L || snap.activeSessionMarkerMs != wokenMarker)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    private val _activeTab = MutableStateFlow(SolstrideTab.EXPEDITION)
    val activeTab: StateFlow<SolstrideTab> = _activeTab.asStateFlow()

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

    val questStatuses: StateFlow<List<QuestStatus>> = heroProfileState
        .map { pState ->
            val profile = (pState as? UiState.Success)?.data ?: return@map emptyList()
            ProgressionEngine.evaluateQuests(profile, currentCalendarDateKey())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        // Lock out the interactive Pet Arena Widget as soon as Solstride loads into foreground
        com.example.widget.SolstrideWidgetSnapshotStore.setAppForegroundLocked(appContext, true)

        viewModelScope.launch {
            _manuallyWokenSleepMarkerMs.value = preferences.readManuallyWokenSleepMs()
            repository.ensureInitializedProfile(currentUserId, initialDisplayName)
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

        // Automatically pause phone pedometer when watch connects and resume it when watch disconnects
        viewModelScope.launch {
            isWatchConnected.collect {
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
            _activeTab.value = matched
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
                // Watch disconnected: clear grace timer, enable phone pedometer, and switch immediately
                _lastWatchStepReceivedMs.value = 0L
                preferences.setLastWatchStepReceivedMs(0L)
                preferences.setPedometerEnabled(true)
                preferences.setLastHardwareStepCount(-1L)
                reconcilePedometerWithWatchState()
                _bannerMessage.value = "Watch disconnected — automatically switched to Phone Pedometer."
            } else {
                val now = System.currentTimeMillis()
                _lastWatchStepReceivedMs.value = now
                preferences.setLastWatchStepReceivedMs(now)
                reconcilePedometerWithWatchState()
                val label = watchName?.ifBlank { activeWatchDisplayName() } ?: activeWatchDisplayName()
                _bannerMessage.value = "$label connected — Phone Pedometer on standby."
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
        _activeTab.value = tab
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
        }
    }

    fun onBackgroundPaused() {
        _isForegroundActive.value = false
        BackgroundSyncGuard.setForeground(false)
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
        val snap = healthConnectReader.readWatchSleepSnapshot()
        _watchSleepState.value = snap
        val wokenMarker = _manuallyWokenSleepMarkerMs.value
        val activeSleeping = snap.isSleepingNow && (snap.activeSessionMarkerMs == 0L || snap.activeSessionMarkerMs != wokenMarker)
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
                val sleepReward = ProgressionEngine.computeSleepSolCoinReward(durationMin)
                val fraction = (durationMin / 360f).coerceIn(0.25f, 1.0f)
                val hpGain = (stats.computedMaxHp * fraction).roundToInt().toLong().coerceAtLeast(15L)
                val energyGain = (stats.computedMaxEnergy * fraction).roundToInt().toLong().coerceAtLeast(10L)
                val woundsCured = when {
                    durationMin >= 360L -> 3
                    durationMin >= 180L -> 2
                    else -> 1
                }
                val newHp = (profile.currentHp + hpGain).coerceAtMost(stats.computedMaxHp.toLong())
                val newEnergy = (profile.strideEnergy + energyGain).coerceAtMost(stats.computedMaxEnergy.toLong())
                val newSolCoins = (profile.solCoins + sleepReward.solCoinsEarned).coerceAtMost(100_000_000L)

                var curedActual = 0
                preferences.updateHardcoreRpgState { hc ->
                    val nextWounds = (hc.battleWoundStacks - woundsCured).coerceAtLeast(0)
                    curedActual = (hc.battleWoundStacks - nextWounds).coerceAtLeast(0)
                    hc.copy(battleWoundStacks = nextWounds)
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
                _bannerMessage.value = "🌙 Watch Sleep ($durLabel • ${sleepReward.sleepPercent}%)! +${sleepReward.solCoinsEarned} Sol Coins (max 50), +$hpGain HP, +$energyGain Energy$woundNote!"
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
            if (targetPet != null && ledger.pendingPetXp > 0L) {
                val leveled = PetCatalog.applyFoodXp(targetPet, ledger.pendingPetXp)
                levelsGained = (leveled.level - targetPet.level).toInt().coerceAtLeast(0)
                val petDocId = targetPet.id.ifBlank { targetPet.petType }
                val petRes = repository.updateOwnedPet(
                    currentUserId,
                    petDocId,
                    mapOf(
                        "level" to leveled.level,
                        "xp" to leveled.xp,
                        "xpToNextLevel" to leveled.xpToNextLevel
                    )
                )
                if (petRes.isFailure) {
                    // Retry once connection returns — never drop queued widget XP!
                    return
                }
                leveledPetResult = leveled
            }

            if (ledger.pendingOreWon > 0L || ledger.pendingEssenceWon > 0L) {
                preferences.updateHardcoreRpgState { hc ->
                    hc.copy(
                        sunsteelOre = (hc.sunsteelOre + ledger.pendingOreWon).coerceAtMost(999_999L),
                        elementalEssence = (hc.elementalEssence + ledger.pendingEssenceWon).coerceAtMost(999_999L)
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

            val petName = leveledPetResult?.resolvedType()?.displayName ?: "Companion"
            val parts = mutableListOf<String>()
            if (ledger.pendingPetXp > 0L) parts.add("+${ledger.pendingPetXp} Pet XP")
            if (levelsGained > 0 && leveledPetResult != null) parts.add("★ Lv.${leveledPetResult.level}!")
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

            // Steps are ONLY used to level the Hero (Hero XP) and fuel Pet Arena Widget leveling (Wild Stamina & Step Treats)
            val stepXpGain = ProgressionEngine.computeHeroStepXp(totalDelta, stats.totalXpMultiplier)
            val leveled = applyXpAndLevelUp(profile, stepXpGain, stats)
            val leveledUpHero = leveled.level > profile.level

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
                "heroPower" to leveled.heroPower
            )

            val result = repository.updateHeroProfile(currentUserId, updateMap)
            if (result.isSuccess) {
                offlineSyncDao.markBatchesSynced(acceptedBatches.map { it.batchId })
                val sources = acceptedBatches.map { it.source }.distinct().joinToString(" + ")
                val watchDelta = acceptedBatches.filter { it.source != StepSource.PHONE.name }.sumOf { it.stepDelta }
                if (watchDelta > 0L) {
                    preferences.recordDailyPedometerSteps(currentCalendarDateKey(), watchDelta)
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
                offlineSyncDao.insertSyncLog(
                    SyncLogEntity(
                        userId = currentUserId,
                        source = sources,
                        stepDelta = totalDelta,
                        summary = "+$totalDelta steps -> +$stepXpGain Hero XP$lvlTag & Pet Training Fuel",
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
                    heroPower = leveled.heroPower
                )
                watchBridge.pushHeroSnapshotToWatch(updatedProfile)
                preferences.setLastWatchSyncMs(System.currentTimeMillis())

                if (leveledUpHero) {
                    haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
                    _bannerMessage.value = "★ Hero Level Up (Lv.${leveled.level})! +$totalDelta steps -> +$stepXpGain Hero XP & Pet Stamina!"
                } else if (!silent) {
                    _bannerMessage.value = "Merged +$totalDelta steps -> +$stepXpGain Hero XP & Pet Training Stamina."
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

    // --- Class Skill Tree, Quests & Prestige Rebirth ---

    fun upgradeSkillNode(nodeIndex: Int) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val spec = ProgressionEngine.skillNodes.getOrNull(nodeIndex) ?: return
        if (profile.unspentPoints <= 0L) {
            _bannerMessage.value = "Level up through battles, quests, or watch steps to earn Mastery Points."
            return
        }
        val ranks = profile.parsedSkillRanks().toMutableList()
        val currentRank = ranks.getOrElse(nodeIndex) { 0 }
        if (currentRank >= spec.maxRank) {
            _bannerMessage.value = "${spec.name} is already maxed (${spec.maxRank}/${spec.maxRank})."
            return
        }
        ranks[nodeIndex] = currentRank + 1
        val serialized = ProgressionEngine.serializeSkillRanks(ranks)
        val updated = profile.copy(
            skillRanks = serialized,
            unspentPoints = (profile.unspentPoints - 1L).coerceAtLeast(0L)
        )
        val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
        val stats = computeDerivedStats(updated, items, equippedPet.value)
        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)

        viewModelScope.launch {
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "skillRanks" to serialized,
                    "unspentPoints" to updated.unspentPoints,
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "currentHp" to updated.currentHp.coerceAtMost(stats.computedMaxHp.toLong()),
                    "maxStrideEnergy" to stats.computedMaxEnergy.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            ).onSuccess {
                syncLeaderboard(updated, updated.level, stats.computedHeroPower.toLong(), updated.bossesDefeated)
                _bannerMessage.value = "Upgraded ${spec.name} to Rank ${currentRank + 1}/${spec.maxRank}!"
            }
        }
    }

    fun claimQuestReward(status: QuestStatus) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!status.canClaim) return
        val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
        val stats = computeDerivedStats(profile, items, equippedPet.value)
        val todayKey = currentCalendarDateKey()
        val nextClaimed = ProgressionEngine.pruneAndAppendClaimedQuest(
            existingClaims = profile.claimedQuestIds,
            newClaimKey = status.claimKey,
            todayKey = todayKey,
            weekKey = profile.weekKey
        )
        val leveled = applyXpAndLevelUp(profile, status.spec.xpReward, stats)
        val nextShards = (profile.sunshards + status.spec.sunshardReward).coerceAtMost(100_000_000L)
        val questChest = ProgressionEngine.buildQuestChest(currentUserId, profile, status.spec)

        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
            var savedChest: LootChest? = null
            if (questChest != null) {
                val res = repository.addLootChest(currentUserId, questChest)
                if (res.isFailure) {
                    _bannerMessage.value = "Could not deliver quest chest — please retry."
                    return@launch
                }
                savedChest = res.getOrNull()
            }
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "claimedQuestIds" to nextClaimed,
                    "sunshards" to nextShards,
                    "level" to leveled.level,
                    "xp" to leveled.xp,
                    "xpToNextLevel" to leveled.xpToNextLevel,
                    "unspentPoints" to leveled.unspentPoints,
                    "maxHp" to leveled.maxHp,
                    "currentHp" to leveled.currentHp,
                    "heroPower" to leveled.heroPower
                )
            ).onSuccess {
                syncLeaderboard(profile, leveled.level, leveled.heroPower, profile.bossesDefeated)
                if (savedChest != null) {
                    if (savedChest.id.isNotBlank()) recentlyAwardedChestIds.add(savedChest.id)
                    _lastOpenedChestResult.value = null
                    _pendingChestModal.value = savedChest
                    _bannerMessage.value = "Quest Claimed! +${status.spec.sunshardReward} Sunshards, +${status.spec.xpReward} XP & Sealed Chest!"
                } else {
                    _bannerMessage.value = "Quest Claimed! +${status.spec.sunshardReward} Sunshards & +${status.spec.xpReward} XP."
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
        if (!ProgressionEngine.canPrestige(profile)) {
            _bannerMessage.value = "Reach Level ${ProgressionEngine.PRESTIGE_MIN_LEVEL} or clear Realm ${ProgressionEngine.PRESTIGE_ALT_REALM_CLEARED} to trigger Solar Ascension."
            return
        }
        val nextPrestige = (profile.prestigeLevel + 1L).coerceAtMost(ProgressionEngine.MAX_PRESTIGE_LEVEL)
        val bonusPoints = 2L + nextPrestige
        val reborn = profile.copy(
            prestigeLevel = nextPrestige,
            level = 1L,
            xp = 0L,
            xpToNextLevel = 120L,
            might = 5L,
            vigor = 5L,
            endurance = 5L,
            unspentPoints = bonusPoints,
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
                _bannerMessage.value = "SOLAR ASCENSION $numeral! Permanent +${nextPrestige * 15}% All Stats, +${nextPrestige * 10}% XP/Shards & +${nextPrestige * 3}% Survival!"
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
     * Pets can ONLY be fed and leveled up via the interactive Home Screen Pet Arena Widget
     * while Solstride is closed. When the app opens, widget progress auto-syncs and the widget locks.
     */
    fun buyPetFood(@Suppress("UNUSED_PARAMETER") food: PetFoodSpec) {
        haptics.trigger(GameplayHaptic.GUARD_BLOCK)
        _bannerMessage.value = "Pets only level up on the Home Screen Pet Arena Widget while Solstride is closed! Pin the widget & close the app to Feed & Fight Wild Pets."
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
        val stats = derivedStats.value
        val maxEn = (stats?.computedMaxEnergy ?: profile.maxStrideEnergy.toInt()).toLong().coerceAtLeast(100L)
        val combatReadyEnergy = max(profile.strideEnergy, min(35L, maxEn))
        val battleReadyProfile = profile.copy(strideEnergy = combatReadyEnergy)

        val spawned = CombatEngine.spawnEncounter(
            profile = battleReadyProfile,
            mode = _battleMode.value,
            forceBoss = forceBoss,
            hardcoreState = hardcoreState.value,
            dateKey = currentCalendarDateKey()
        )
        _battleState.value = spawned
        _activeTab.value = SolstrideTab.BATTLE
        haptics.trigger(GameplayHaptic.ENCOUNTER_ALERT)

        viewModelScope.launch {
            val nextEncounter = if (profile.encounterProgress >= 100L) 0L else profile.encounterProgress
            repository.updateHeroProfile(
                currentUserId,
                mapOf("strideEnergy" to combatReadyEnergy, "encounterProgress" to nextEncounter)
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

    fun performUltimateAndPetCombo() = runBattleTurn { battle, profile, stats ->
        haptics.trigger(GameplayHaptic.SOLAR_CRITICAL)
        CombatEngine.executeUltimateAndPetCombo(battle, profile, stats, equippedPet.value)
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
        _battleState.value = BattleState()
    }

    private fun runBattleTurn(
        reducer: (BattleState, HeroProfile, DerivedHeroStats) -> com.example.domain.TurnResolution
    ) {
        val battle = _battleState.value
        if (!battle.active) return
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val items = (inventoryState.value as? UiState.Success)?.data ?: emptyList()
        val hcSnapshot = hardcoreState.value
        val stats = computeDerivedStats(profile, items, equippedPet.value, hcSnapshot)

        val resolution = reducer(battle, profile, stats)
        _battleState.value = resolution.nextBattle

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

                    hc.copy(
                        sunsteelOre = (hc.sunsteelOre + outcome.oreEarned).coerceAtMost(999_999L),
                        aetherHerbs = (hc.aetherHerbs + outcome.herbsEarned).coerceAtMost(999_999L),
                        elementalEssence = (hc.elementalEssence + outcome.essenceEarned).coerceAtMost(999_999L),
                        battleWoundStacks = nextWounds,
                        mercenaryBattlesLeft = nextMercLeft,
                        mercenaryContractId = if (nextMercLeft == 0) "" else hc.mercenaryContractId,
                        shrineBattlesLeft = nextShrineLeft,
                        shrineBlessingLabel = if (nextShrineLeft == 0) "" else hc.shrineBlessingLabel,
                        itemDurabilityMap = nextDurability,
                        bestiaryDefeatsMap = nextBestiary,
                        unlockedRunesMap = nextRunes
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
                val newShards = (profile.sunshards + outcome.sunshardsEarned).coerceAtMost(100_000_000L)
                val newSolCoins = (profile.solCoins + outcome.solCoinsEarned - outcome.solCoinsLost)
                    .coerceIn(0L, 100_000_000L)
                val newBosses = if (outcome.victory) profile.bossesDefeated + 1L else profile.bossesDefeated
                val newTrueBossWins = if (outcome.wasBossVictory) profile.trueBossWins + 1L else profile.trueBossWins

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
                    "dailyBattlesWon" to nextDailyBattles
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
                        nextShards = (nextShards + 95L).coerceAtMost(100_000_000L)
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
                        _bannerMessage.value = "Altar Backlash (-20% HP): Claimed +95 Sunshards & +3 Elemental Essence."
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
        if (profile.level < 3L && profile.prestigeLevel == 0L) {
            _bannerMessage.value = "Reach Hero Level 3 to unlock Class Specializations & Ultimates."
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
            _bannerMessage.value = "Specialized as ${spec.title}! Ultimate unlocked: ${spec.ultimateName} (${spec.affinity.label})."
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
        chosenElement: ElementalAffinity? = null
    ) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        if (!slot.isEquipment) return
        if (profile.level < tier.minHeroLevel.toLong()) {
            _bannerMessage.value = "${tier.label} Blueprint unlocks at Hero Level ${tier.minHeroLevel}."
            return
        }
        val hc = hardcoreState.value
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
            chosenElement = chosenElement
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
                    elementalEssence = (cur.elementalEssence - essenceCost).coerceAtLeast(0L)
                )
            }
            repository.updateHeroProfile(
                currentUserId,
                mapOf("sunshards" to (profile.sunshards - tier.sunshardCost).coerceAtLeast(0L))
            )
            val critNote = if (forged.resolvedRarity() != tier.baseRarity) " ★ MASTERWORK CRIT!" else ""
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
                            "sunshards" to (profile.sunshards + 120L).coerceAtMost(100_000_000L)
                        )
                    )
                    _bannerMessage.value = "Unlocked Supply Crate (+35 Ore, +28 Herbs, +10 Essence & +120 Sunshards)!"
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
                    _bannerMessage.value = "All progress wiped. Reborn as a Level 1 Novice with a basic starter blade and zero armor."
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

    private companion object {
        const val TAG = "SolstrideVM"
        const val WATCH_AUTO_SYNC_INTERVAL_MS = 5_000L
        const val WATCH_ACTIVE_GRACE_MS = 10L * 60L * 1000L
    }
}
