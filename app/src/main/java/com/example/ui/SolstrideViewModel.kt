package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.SolstrideFirestoreRepository
import com.example.data.local.OfflineSyncDao
import com.example.data.local.PendingStepBatchEntity
import com.example.data.local.SolstridePreferences
import com.example.data.local.SolstrideSettings
import com.example.data.local.SyncLogEntity
import com.example.data.model.DerivedHeroStats
import com.example.data.model.GearPerkType
import com.example.data.model.HeroClass
import com.example.data.model.HeroProfile
import com.example.data.model.InventoryItem
import com.example.data.model.ItemSlot
import com.example.data.model.LeaderboardEntry
import com.example.data.model.LootChest
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
import com.example.domain.BattleMode
import com.example.domain.BattleState
import com.example.domain.ChestOpenResult
import com.example.domain.CombatEngine
import com.example.domain.ExpeditionEngine
import com.example.domain.ExpeditionOutcomeReport
import com.example.domain.ProgressionEngine
import com.example.domain.QuestStatus
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
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.roundToInt

enum class SolstrideTab(val route: String) {
    EXPEDITION("expedition"),
    BATTLE("battle"),
    PETS("pets"),
    INVENTORY("inventory"),
    LEADERBOARD("leaderboard")
}

enum class MasteryAttribute { MIGHT, VIGOR, ENDURANCE }

class SolstrideViewModel(
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

    private val _isForegroundActive = MutableStateFlow(true)

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
     * True whenever a paired smartwatch is currently connected (Wear OS / Galaxy Watch Bluetooth link,
     * or a non-Wear OS watch actively syncing via Health Connect). While true, the phone pedometer
     * stays off in standby and phone steps do NOT count.
     */
    val isWatchConnected: StateFlow<Boolean> = combine(
        watchLinkStatus,
        healthConnectStatus,
        _lastWatchStepReceivedMs,
        _currentTimeMs
    ) { watch, hc, lastWatchMs, nowMs ->
        if (watch.connected) {
            sawWearOsNode = true
            true
        } else if (!sawWearOsNode &&
            hc.state == HealthConnectState.CONNECTED &&
            lastWatchMs > 0L &&
            (nowMs - lastWatchMs) <= WATCH_ACTIVE_GRACE_MS
        ) {
            true
        } else {
            false
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

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

    val chestsState: StateFlow<UiState<List<LootChest>>> = repository.observeChests(currentUserId)
        .map<List<LootChest>, UiState<List<LootChest>>> { UiState.Success(it) }
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
        equippedPet
    ) { pState, iState, activePet ->
        val profile = (pState as? UiState.Success)?.data ?: return@combine null
        val items = (iState as? UiState.Success)?.data ?: emptyList()
        computeDerivedStats(profile, items, activePet)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val questStatuses: StateFlow<List<QuestStatus>> = heroProfileState
        .map { pState ->
            val profile = (pState as? UiState.Success)?.data ?: return@map emptyList()
            ProgressionEngine.evaluateQuests(profile, currentCalendarDateKey())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        viewModelScope.launch {
            repository.ensureInitializedProfile(currentUserId, initialDisplayName)
                .onFailure { err ->
                    _bannerMessage.value = "Offline mode active: ${err.localizedMessage ?: "waiting for network"}"
                }
            _lastWatchStepReceivedMs.value = preferences.readLastWatchStepReceivedMs()
            watchBridge.refreshWatchConnection()
            healthConnectReader.refreshStatus()
            reconcilePedometerWithWatchState()
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
        viewModelScope.launch {
            // Check watch connection FIRST so phone pedometer NEVER turns on while a watch is connected!
            _lastWatchStepReceivedMs.value = preferences.readLastWatchStepReceivedMs()
            watchBridge.refreshWatchConnection()
            healthConnectReader.refreshStatus()
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
        if (allWatchPayloads.isNotEmpty()) {
            val now = System.currentTimeMillis()
            _lastWatchStepReceivedMs.value = now
            preferences.setLastWatchStepReceivedMs(now)
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

            val onExpedition = profile.hasActiveExpedition()
            val striderBonus = if (profile.resolvedClass() == HeroClass.STRIDER) 1.20f else 1.0f
            val energyGain = ((totalDelta / 8.0f) * striderBonus * stats.stepEnergyMultiplier)
                .toLong().coerceAtLeast(1L)
            val encounterGain = if (onExpedition) 0L else (totalDelta / 5L).coerceAtLeast(1L)
            val rawStepXp = (totalDelta / 15L).coerceAtLeast(0L)
            val stepXpGain = (rawStepXp * stats.totalXpMultiplier).roundToInt().toLong()

            val newEnergy = (profile.strideEnergy + energyGain).coerceAtMost(stats.computedMaxEnergy.toLong())
            val prevEncounter = profile.encounterProgress
            val newEncounter = if (onExpedition) {
                prevEncounter
            } else {
                (prevEncounter + encounterGain).coerceAtMost(100L)
            }

            val leveled = applyXpAndLevelUp(profile, stepXpGain, stats)

            val updateMap = mapOf(
                "totalSteps" to newTotalSteps,
                "weeklySteps" to newWeeklySteps,
                "weekKey" to weekNow,
                "strideEnergy" to newEnergy,
                "encounterProgress" to newEncounter,
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
                offlineSyncDao.insertSyncLog(
                    SyncLogEntity(
                        userId = currentUserId,
                        source = sources,
                        stepDelta = totalDelta,
                        summary = if (onExpedition) {
                            "+$totalDelta steps -> +$energyGain Energy (Encounters off on expedition)"
                        } else {
                            "+$totalDelta steps -> +$energyGain Energy, +$encounterGain% Encounter"
                        },
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
                    strideEnergy = newEnergy,
                    encounterProgress = newEncounter,
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

                if (!onExpedition && prevEncounter < 100L && newEncounter >= 100L) {
                    haptics.trigger(GameplayHaptic.ENCOUNTER_ALERT)
                    watchBridge.sendEncounterHapticToWatch("Highland Encounter Ready!")
                    _bannerMessage.value = "Encounter Alert! Your steps awakened a guardian nearby."
                } else if (!silent) {
                    _bannerMessage.value = "Merged +$totalDelta steps (+$energyGain Solar Energy)."
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
        val stats = computeDerivedStats(profile, items, equippedPet.value)
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
        haptics.trigger(GameplayHaptic.GUARD_BLOCK)

        viewModelScope.launch {
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "activeExpeditionStartMs" to 0L,
                    "activeExpeditionDurationMin" to 0L,
                    "activeExpeditionEndMs" to 0L
                )
            ).onSuccess {
                _lastExpeditionReport.value = null
                _bannerMessage.value = "Expedition recalled early — no rewards earned. Hero unlocked!"
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
        val stats = computeDerivedStats(profile, items, equippedPet.value)
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
                val newCompleted = (profile.expeditionsCompleted + 1L).coerceAtMost(1_000_000L)
                val nextDailyExp = (if (sameDay) profile.dailyExpeditionsDone else 0L) + 1L

                haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
                val updates = mutableMapOf<String, Any>(
                    "activeExpeditionStartMs" to 0L,
                    "activeExpeditionDurationMin" to 0L,
                    "activeExpeditionEndMs" to 0L,
                    "expeditionsCompleted" to newCompleted,
                    "dailyQuestDateKey" to todayKey,
                    "dailyExpeditionsDone" to nextDailyExp,
                    "sunshards" to newShards,
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
                    _lastExpeditionReport.value = report.copy(awardedChest = savedChest)
                    if (savedChest != null) {
                        _lastOpenedChestResult.value = null
                        _pendingChestModal.value = savedChest
                    }
                    syncLeaderboard(profile, leveled.level, leveled.heroPower, profile.bossesDefeated)
                    _bannerMessage.value = "Expedition Succeeded! Tap your sealed Expedition Chest to loot it!"
                }
            } else {
                val reducedHp = max(15L, profile.currentHp - report.hpLost)
                val leveled = applyXpAndLevelUp(profile.copy(currentHp = reducedHp), report.xpEarned, stats)
                val newFailed = (profile.expeditionsFailed + 1L).coerceAtMost(1_000_000L)

                haptics.trigger(GameplayHaptic.DAMAGE_TAKEN)
                repository.updateHeroProfile(
                    currentUserId,
                    mapOf(
                        "activeExpeditionStartMs" to 0L,
                        "activeExpeditionDurationMin" to 0L,
                        "activeExpeditionEndMs" to 0L,
                        "expeditionsFailed" to newFailed,
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
                    _bannerMessage.value = "Expedition Failed! Hero returned bruised with no chest. Upgrade armor & weapons for higher survival."
                }
            }
        }
    }

    fun dismissExpeditionReport() {
        _lastExpeditionReport.value = null
    }

    // --- Interactive Loot Chests & Treasure Vault ---

    fun promptOpenChest(chest: LootChest) {
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
        if (chest.id.isBlank()) return

        viewModelScope.launch {
            chestOpenMutex.withLock {
                if (_isOpeningChest.value) return@withLock
                _isOpeningChest.value = true
                try {
                    val rolled = ExpeditionEngine.openLootChest(chest, currentUserId)
                    val addRes = repository.addInventoryItem(currentUserId, rolled.item)
                    if (addRes.isFailure) {
                        _bannerMessage.value = "Could not open chest — check connection and tap again."
                        return@withLock
                    }
                    val createdId = addRes.getOrNull().orEmpty()
                    val persistedItem = rolled.item.copy(id = createdId)

                    repository.deleteLootChest(currentUserId, chest.id)

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
                        profileUpdates["dailyExpeditionsDone"] = 0L
                    }
                    repository.updateHeroProfile(currentUserId, profileUpdates)

                    haptics.trigger(
                        if (rolled.wasUltraRare) GameplayHaptic.SOLAR_CRITICAL else GameplayHaptic.VICTORY_FANFARE
                    )
                    _pendingChestModal.value = null
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

    fun buyPetFood(food: PetFoodSpec) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val activePet = equippedPet.value
        if (activePet == null) {
            _bannerMessage.value = "Equip a companion pet before buying Pet Food."
            return
        }
        if (activePet.level >= PetCatalog.MAX_PET_LEVEL) {
            _bannerMessage.value = "${activePet.resolvedType().displayName} has already reached Max Level ${PetCatalog.MAX_PET_LEVEL}!"
            return
        }
        if (profile.solCoins < food.coinCost) {
            _bannerMessage.value = "Need ${food.coinCost} Sol Coins for ${food.name}. Win Boss Battles (+10 Coins)."
            return
        }

        val leveledPet = PetCatalog.applyFoodXp(activePet, food.xpGain)
        val didLevelUp = leveledPet.level > activePet.level
        val nextCoins = (profile.solCoins - food.coinCost).coerceAtLeast(0L)
        val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
        val updatedProfile = profile.copy(solCoins = nextCoins)
        val stats = computeDerivedStats(updatedProfile, items, leveledPet)

        haptics.trigger(if (didLevelUp) GameplayHaptic.VICTORY_FANFARE else GameplayHaptic.SYNC_CONFIRM)
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
                _bannerMessage.value = "Could not feed ${activePet.resolvedType().displayName}."
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
            _bannerMessage.value = if (didLevelUp) {
                "${activePet.resolvedType().displayName} leveled up to Lv.${leveledPet.level}! (${stats.petBonusSummary})"
            } else {
                "Fed ${food.name} to ${activePet.resolvedType().displayName} (+${food.xpGain} Pet XP)."
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

    // --- Turn-Based Combat Actions (Realm Campaign + Endless Spire) ---

    fun engageEncounter(forceBoss: Boolean = false) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        if (_battleState.value.active) {
            _activeTab.value = SolstrideTab.BATTLE
            return
        }
        val requiredEnergy = if (profile.encounterProgress >= 100L) 0L else 15L
        if (profile.strideEnergy < requiredEnergy) {
            _bannerMessage.value = "Walk with your smart watch to build Encounter Gauge or 15 Stride Energy."
            return
        }

        val spawned = CombatEngine.spawnEncounter(profile, _battleMode.value, forceBoss)
        _battleState.value = spawned
        _activeTab.value = SolstrideTab.BATTLE
        haptics.trigger(GameplayHaptic.ENCOUNTER_ALERT)

        viewModelScope.launch {
            val nextEnergy = (profile.strideEnergy - requiredEnergy).coerceAtLeast(0L)
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
            _bannerMessage.value = "No elixirs left in inventory — open Highland Chests to restock."
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
        val stats = computeDerivedStats(profile, items, equippedPet.value)

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
                val leveled = applyXpAndLevelUp(
                    profile.copy(
                        currentHp = resolution.updatedHp,
                        strideEnergy = resolution.updatedEnergy
                    ),
                    outcome.xpEarned,
                    stats
                )
                val newShards = (profile.sunshards + outcome.sunshardsEarned).coerceAtMost(100_000_000L)
                val newSolCoins = (profile.solCoins + outcome.solCoinsEarned).coerceAtMost(100_000_000L)
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
                        _battleState.value = _battleState.value.copy(
                            outcome = _battleState.value.outcome?.copy(droppedChest = saved)
                        )
                        _lastOpenedChestResult.value = null
                        _pendingChestModal.value = saved
                    }
                }

                if (outcome.solCoinsEarned > 0L) {
                    _bannerMessage.value = "Boss Slain! Earned +${outcome.solCoinsEarned} Sol Coins!"
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
        val stats = computeDerivedStats(updated, items, equippedPet.value)
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
        val stats = computeDerivedStats(updated, items, equippedPet.value)
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)

        viewModelScope.launch {
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
            val stats = computeDerivedStats(profile, projected, equippedPet.value)
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

    fun upgradeItem(item: InventoryItem) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val items = (inventoryState.value as? UiState.Success)?.data ?: return
        if (item.resolvedSlot() == ItemSlot.CONSUMABLE) return
        val cost = item.upgradeCost()
        if (profile.sunshards < cost) {
            _bannerMessage.value = "Need $cost Sunshards to forge ${item.name} (+1)."
            return
        }
        val nextLvl = (item.upgradeLevel + 1L).coerceAtMost(25L)
        val nextAtk = if (item.attackBonus > 0L) item.attackBonus + 3L else 0L
        val nextDef = if (item.defenseBonus > 0L) item.defenseBonus + 2L else 0L
        val nextHp = if (item.hpBonus > 0L) item.hpBonus + 8L else 0L
        val nextEnergy = if (item.energyBonus > 0L) item.energyBonus + 5L else 0L
        val (resolvedPerk, resolvedVal) = item.resolvedPerk()
        val nextPerkVal = if (resolvedPerk != GearPerkType.NONE) {
            (resolvedVal.toLong() + 1L).coerceAtMost(100L)
        } else {
            item.perkValue
        }

        haptics.trigger(GameplayHaptic.VICTORY_FANFARE)
        viewModelScope.launch {
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
            val stats = computeDerivedStats(profile, projected, equippedPet.value)
            repository.updateHeroProfile(
                currentUserId,
                mapOf(
                    "sunshards" to (profile.sunshards - cost).coerceAtLeast(0L),
                    "maxHp" to stats.computedMaxHp.toLong(),
                    "maxStrideEnergy" to stats.computedMaxEnergy.toLong(),
                    "heroPower" to stats.computedHeroPower.toLong()
                )
            )
            syncLeaderboard(profile, profile.level, stats.computedHeroPower.toLong(), profile.bossesDefeated)
            _bannerMessage.value = "Forged ${item.name} to +$nextLvl!"
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
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            repository.deleteInventoryItem(currentUserId, item.id)
            repository.updateHeroProfile(
                currentUserId,
                mapOf("sunshards" to (profile.sunshards + gain).coerceAtMost(100_000_000L))
            )
            _bannerMessage.value = "Salvaged ${item.name} for +$gain Sunshards."
        }
    }

    private fun useConsumableOutOfCombat(elixir: InventoryItem) {
        val profile = (heroProfileState.value as? UiState.Success)?.data ?: return
        if (!requireCharacterAvailable(profile)) return
        val items = (inventoryState.value as? UiState.Success)?.data ?: return
        val stats = derivedStats.value ?: return
        if (profile.currentHp >= stats.computedMaxHp.toLong()) {
            _bannerMessage.value = "HP is already full."
            return
        }
        val healedHp = (profile.currentHp + elixir.healAmount.coerceAtLeast(35L))
            .coerceAtMost(stats.computedMaxHp.toLong())
        haptics.trigger(GameplayHaptic.SYNC_CONFIRM)
        viewModelScope.launch {
            consumeElixirItem(elixir.id, items)
            repository.updateHeroProfile(currentUserId, mapOf("currentHp" to healedHp))
            _bannerMessage.value = "Used ${elixir.name} (HP: $healedHp/${stats.computedMaxHp})."
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
        stepTracker.stopTracking()
    }

    private companion object {
        const val TAG = "SolstrideVM"
        const val WATCH_AUTO_SYNC_INTERVAL_MS = 5_000L
        const val WATCH_ACTIVE_GRACE_MS = 10L * 60L * 1000L
    }
}
