package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ElementalAffinity
import com.example.data.model.HardcoreRpgState
import com.example.data.model.HeroTitleSpec
import com.example.data.model.MercenaryContractSpec
import com.example.ui.theme.SolstrideDisplayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.solstrideDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "solstride_preferences"
)

data class SolstrideSettings(
    val displayMode: SolstrideDisplayMode = SolstrideDisplayMode.SUNLIT_PARCHMENT,
    val hapticsEnabled: Boolean = true,
    val batteryBatchingEnabled: Boolean = true,
    val lastHardwareStepCount: Long = -1L,
    val lastWatchSyncMs: Long = 0L,
    val watchOnlyWarningAcknowledged: Boolean = true,
    val pedometerEnabled: Boolean = true,
    val pedometerDailySteps: Long = 0L,
    val pedometerDateKey: String = "",
    val dailyStepGoal: Long = 6000L,
    val healthConnectAllSources: Boolean = false
)

class SolstridePreferences(private val context: Context) {
    private object Keys {
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val BATTERY_BATCHING = booleanPreferencesKey("battery_batching")
        val LAST_HW_STEP_COUNT = longPreferencesKey("last_hw_step_count")
        val LAST_WATCH_SYNC_MS = longPreferencesKey("last_watch_sync_ms")
        val WATCH_ONLY_WARNING_ACKNOWLEDGED = booleanPreferencesKey("watch_only_warning_acknowledged")
        val PEDOMETER_ENABLED = booleanPreferencesKey("pedometer_enabled")
        val PEDOMETER_DAILY_STEPS = longPreferencesKey("pedometer_daily_steps")
        val PEDOMETER_DATE_KEY = stringPreferencesKey("pedometer_date_key")
        val DAILY_STEP_GOAL = longPreferencesKey("daily_step_goal")
        val HEALTH_CONNECT_WATERMARKS = stringPreferencesKey("hc_step_watermarks_v1")
        val HEALTH_CONNECT_ALL_SOURCES = booleanPreferencesKey("hc_all_sources")
        val LAST_WATCH_STEP_RECEIVED_MS = longPreferencesKey("last_watch_step_received_ms")

        // Hardcore RPG & Town Economy State Keys
        val SUNSTEEL_ORE = longPreferencesKey("rpg_sunsteel_ore")
        val AETHER_HERBS = longPreferencesKey("rpg_aether_herbs")
        val ELEMENTAL_ESSENCE = longPreferencesKey("rpg_elemental_essence")
        val SPECIALIZATION_ID = stringPreferencesKey("rpg_specialization_id")
        val EQUIPPED_TITLE_ID = stringPreferencesKey("rpg_equipped_title_id")
        val HARDCORE_OATH_ENABLED = booleanPreferencesKey("rpg_hardcore_oath_enabled")
        val ACTIVE_SOL_COIN_WAGER = longPreferencesKey("rpg_active_sol_coin_wager")
        val BATTLE_WOUND_STACKS = intPreferencesKey("rpg_battle_wound_stacks")
        val MERCENARY_CONTRACT_ID = stringPreferencesKey("rpg_mercenary_contract_id")
        val MERCENARY_BATTLES_LEFT = intPreferencesKey("rpg_mercenary_battles_left")
        val SHRINE_BLESSING_LABEL = stringPreferencesKey("rpg_shrine_blessing_label")
        val SHRINE_BATTLES_LEFT = intPreferencesKey("rpg_shrine_battles_left")
        val ITEM_DURABILITY_RAW = stringPreferencesKey("rpg_item_durability_raw")
        val BESTIARY_DEFEATS_RAW = stringPreferencesKey("rpg_bestiary_defeats_raw")
        val UNLOCKED_RUNES_RAW = stringPreferencesKey("rpg_unlocked_runes_raw")

        // Real-Life Watch Sleep Detection Persistence
        val LAST_CREDITED_SLEEP_END_MS = longPreferencesKey("watch_last_credited_sleep_end_ms")
        val MANUALLY_WOKEN_SLEEP_MS = longPreferencesKey("watch_manually_woken_sleep_ms")
        val WATCH_LIVE_SLEEPING = booleanPreferencesKey("watch_live_sleeping")
        val WATCH_LIVE_SLEEP_SINCE_MS = longPreferencesKey("watch_live_sleep_since_ms")
        val WATCH_LIVE_SLEEP_SOURCE = stringPreferencesKey("watch_live_sleep_source")

        // Single-Use Treasure Chest Consumption Ledger
        val CONSUMED_CHEST_IDS_RAW = stringPreferencesKey("rpg_consumed_chest_ids_raw")

        // Step/Sleep Fog-of-War & Town Merchant Special Materials State
        val STARFORGED_INGOTS = longPreferencesKey("rpg_starforged_ingots")
        val FOG_LANTERN_CHARGES = intPreferencesKey("rpg_fog_lantern_charges")
        val RESTED_CLARITY_BATTLES = intPreferencesKey("rpg_rested_clarity_battles")
        val RESTED_CLARITY_PCT = intPreferencesKey("rpg_rested_clarity_pct")
        val REALM_FOG_STEPS_RAW = stringPreferencesKey("rpg_realm_fog_steps_raw")
        val TOWN_MERCHANT_DATE_KEY = stringPreferencesKey("rpg_town_merchant_date_key")
        val TOWN_MERCHANT_PURCHASED_RAW = stringPreferencesKey("rpg_town_merchant_purchased_raw")
        val ACTIVITY_MASTERY_XP_RAW = stringPreferencesKey("rpg_activity_mastery_xp_raw")
        val COLLECTED_RELIC_IDS_RAW = stringPreferencesKey("rpg_collected_relic_ids_raw")
        val WILDS_HARVESTED_KEYS_RAW = stringPreferencesKey("rpg_wilds_harvested_keys_raw")
        val RELAXED_TIMING_ASSIST = booleanPreferencesKey("rpg_relaxed_timing_assist")

        // Step & Sleep Real-Life RPG Expansion Keys
        val HERO_STAMINA = intPreferencesKey("rpg_hero_stamina")
        val MAX_HERO_STAMINA = intPreferencesKey("rpg_max_hero_stamina")
        val ACTIVE_DREAM_TIER_ID = stringPreferencesKey("rpg_active_dream_tier_id")
        val LAST_SLEEP_DURATION_MIN = intPreferencesKey("rpg_last_sleep_duration_min")
        val LAST_SANCTUARY_REST_DATE = stringPreferencesKey("rpg_last_sanctuary_rest_date")
        val DREAM_SHARDS = longPreferencesKey("rpg_dream_shards")
        val AGILITY_STAT = intPreferencesKey("rpg_agility_stat")
        val PERCEPTION_STAT = intPreferencesKey("rpg_perception_stat")
        val CONSTELLATION_RANKS_RAW = stringPreferencesKey("rpg_constellation_ranks_raw")
        val ITEM_AFFIX_MAP_RAW = stringPreferencesKey("rpg_item_affix_map_raw")
        val ACTIVE_DUNGEON_ID = stringPreferencesKey("rpg_active_dungeon_id")
        val ACTIVE_DUNGEON_ROOM_INDEX = intPreferencesKey("rpg_active_dungeon_room_index")
        val ACTIVE_DUNGEON_ROOM_STEPS = intPreferencesKey("rpg_active_dungeon_room_steps")
        val DUNGEON_CLEARS_COUNT = intPreferencesKey("rpg_dungeon_clears_count")
        val SLAYER_CONTRACT_STEPS_RAW = stringPreferencesKey("rpg_slayer_contract_steps_raw")
        val SLAYER_CONTRACT_KILLS_RAW = stringPreferencesKey("rpg_slayer_contract_kills_raw")
        val SLAYER_CONTRACTS_COMPLETED = intPreferencesKey("rpg_slayer_contracts_completed")
    }

    val settingsFlow: Flow<SolstrideSettings> = context.solstrideDataStore.data.map { prefs ->
        val mode = when (prefs[Keys.DISPLAY_MODE]) {
            SolstrideDisplayMode.OBSIDIAN_OLED.name -> SolstrideDisplayMode.OBSIDIAN_OLED
            else -> SolstrideDisplayMode.SUNLIT_PARCHMENT
        }
        SolstrideSettings(
            displayMode = mode,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            batteryBatchingEnabled = prefs[Keys.BATTERY_BATCHING] ?: true,
            lastHardwareStepCount = prefs[Keys.LAST_HW_STEP_COUNT] ?: -1L,
            lastWatchSyncMs = prefs[Keys.LAST_WATCH_SYNC_MS] ?: 0L,
            watchOnlyWarningAcknowledged = prefs[Keys.WATCH_ONLY_WARNING_ACKNOWLEDGED] ?: true,
            pedometerEnabled = prefs[Keys.PEDOMETER_ENABLED] ?: true,
            pedometerDailySteps = prefs[Keys.PEDOMETER_DAILY_STEPS] ?: 0L,
            pedometerDateKey = prefs[Keys.PEDOMETER_DATE_KEY] ?: "",
            dailyStepGoal = (prefs[Keys.DAILY_STEP_GOAL] ?: 6000L).coerceIn(2000L, 25000L),
            healthConnectAllSources = prefs[Keys.HEALTH_CONNECT_ALL_SOURCES] ?: false
        )
    }

    val hardcoreRpgStateFlow: Flow<HardcoreRpgState> = context.solstrideDataStore.data.map { prefs ->
        HardcoreRpgState(
            sunsteelOre = (prefs[Keys.SUNSTEEL_ORE] ?: 0L).coerceAtLeast(0L),
            aetherHerbs = (prefs[Keys.AETHER_HERBS] ?: 0L).coerceAtLeast(0L),
            elementalEssence = (prefs[Keys.ELEMENTAL_ESSENCE] ?: 0L).coerceAtLeast(0L),
            starforgedIngots = (prefs[Keys.STARFORGED_INGOTS] ?: 0L).coerceAtLeast(0L),
            fogLanternCharges = (prefs[Keys.FOG_LANTERN_CHARGES] ?: 0).coerceIn(0, 99),
            restedClarityBattlesLeft = (prefs[Keys.RESTED_CLARITY_BATTLES] ?: 0).coerceIn(0, 10),
            restedClarityPct = (prefs[Keys.RESTED_CLARITY_PCT] ?: 0).coerceIn(0, 100),
            realmFogStepsMap = parseKeyIntMap(prefs[Keys.REALM_FOG_STEPS_RAW] ?: ""),
            townMerchantDateKey = prefs[Keys.TOWN_MERCHANT_DATE_KEY] ?: "",
            townMerchantPurchasedMap = parseKeyIntMap(prefs[Keys.TOWN_MERCHANT_PURCHASED_RAW] ?: ""),
            specializationId = prefs[Keys.SPECIALIZATION_ID] ?: "",
            equippedTitleId = prefs[Keys.EQUIPPED_TITLE_ID] ?: HeroTitleSpec.WAYFARER.id,
            hardcoreOathEnabled = prefs[Keys.HARDCORE_OATH_ENABLED] ?: false,
            activeSolCoinWager = (prefs[Keys.ACTIVE_SOL_COIN_WAGER] ?: 0L).coerceAtLeast(0L),
            battleWoundStacks = (prefs[Keys.BATTLE_WOUND_STACKS] ?: 0).coerceIn(0, 3),
            mercenaryContractId = prefs[Keys.MERCENARY_CONTRACT_ID] ?: MercenaryContractSpec.NONE.id,
            mercenaryBattlesLeft = (prefs[Keys.MERCENARY_BATTLES_LEFT] ?: 0).coerceAtLeast(0),
            shrineBlessingLabel = prefs[Keys.SHRINE_BLESSING_LABEL] ?: "",
            shrineBattlesLeft = (prefs[Keys.SHRINE_BATTLES_LEFT] ?: 0).coerceAtLeast(0),
            itemDurabilityMap = parseKeyIntMap(prefs[Keys.ITEM_DURABILITY_RAW] ?: ""),
            bestiaryDefeatsMap = parseKeyIntMap(prefs[Keys.BESTIARY_DEFEATS_RAW] ?: ""),
            unlockedRunesMap = parseKeyIntMap(prefs[Keys.UNLOCKED_RUNES_RAW] ?: ""),
            activityMasteryXpMap = parseKeyIntMap(prefs[Keys.ACTIVITY_MASTERY_XP_RAW] ?: ""),
            collectedRelicIds = parseIdList(prefs[Keys.COLLECTED_RELIC_IDS_RAW] ?: "").toSet(),
            wildsHarvestedKeys = parseIdList(prefs[Keys.WILDS_HARVESTED_KEYS_RAW] ?: "").toSet(),
            relaxedTimingAssist = prefs[Keys.RELAXED_TIMING_ASSIST] ?: false,
            heroStamina = (prefs[Keys.HERO_STAMINA] ?: 85).coerceIn(0, 400),
            maxHeroStamina = (prefs[Keys.MAX_HERO_STAMINA] ?: 100).coerceIn(80, 350),
            activeDreamTierId = prefs[Keys.ACTIVE_DREAM_TIER_ID] ?: "RESTORATIVE_SLUMBER",
            lastSleepDurationMinutes = (prefs[Keys.LAST_SLEEP_DURATION_MIN] ?: 390).coerceIn(0, 1440),
            lastSanctuaryRestDateKey = prefs[Keys.LAST_SANCTUARY_REST_DATE] ?: "",
            dreamShards = (prefs[Keys.DREAM_SHARDS] ?: 3L).coerceAtLeast(0L),
            agilityStat = (prefs[Keys.AGILITY_STAT] ?: 5).coerceIn(5, 999),
            perceptionStat = (prefs[Keys.PERCEPTION_STAT] ?: 5).coerceIn(5, 999),
            constellationRanksMap = parseKeyIntMap(prefs[Keys.CONSTELLATION_RANKS_RAW] ?: ""),
            itemAffixMap = parseKeyStringMap(prefs[Keys.ITEM_AFFIX_MAP_RAW] ?: ""),
            activeDungeonId = prefs[Keys.ACTIVE_DUNGEON_ID] ?: "dungeon_sunstone_catacombs",
            activeDungeonRoomIndex = (prefs[Keys.ACTIVE_DUNGEON_ROOM_INDEX] ?: 0).coerceAtLeast(0),
            activeDungeonRoomSteps = (prefs[Keys.ACTIVE_DUNGEON_ROOM_STEPS] ?: 0).coerceAtLeast(0),
            dungeonClearsCount = (prefs[Keys.DUNGEON_CLEARS_COUNT] ?: 0).coerceAtLeast(0),
            slayerContractStepsMap = parseKeyIntMap(prefs[Keys.SLAYER_CONTRACT_STEPS_RAW] ?: ""),
            slayerContractKillsMap = parseKeyIntMap(prefs[Keys.SLAYER_CONTRACT_KILLS_RAW] ?: ""),
            slayerContractsCompleted = (prefs[Keys.SLAYER_CONTRACTS_COMPLETED] ?: 0).coerceAtLeast(0)
        )
    }

    val consumedChestIdsFlow: Flow<Set<String>> = context.solstrideDataStore.data.map { prefs ->
        parseIdList(prefs[Keys.CONSUMED_CHEST_IDS_RAW] ?: "").toSet()
    }

    private fun parseIdList(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun parseKeyIntMap(raw: String): Map<String, Int> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(",")
            .mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2 && parts[0].isNotBlank()) {
                    val v = parts[1].toIntOrNull() ?: return@mapNotNull null
                    parts[0].trim() to v
                } else null
            }
            .toMap()
    }

    private fun encodeKeyIntMap(map: Map<String, Int>): String {
        return map.entries
            .filter { it.key.isNotBlank() }
            .joinToString(",") { "${it.key}:${it.value}" }
    }

    private fun parseKeyStringMap(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(",")
            .mapNotNull { entry ->
                val parts = entry.split(":", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    parts[0].trim() to parts[1].trim()
                } else null
            }
            .toMap()
    }

    private fun encodeKeyStringMap(map: Map<String, String>): String {
        return map.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .joinToString(",") { "${it.key}:${it.value}" }
    }

    suspend fun updateHardcoreRpgState(transform: (HardcoreRpgState) -> HardcoreRpgState) {
        val current = hardcoreRpgStateFlow.first()
        val next = transform(current)
        context.solstrideDataStore.edit { prefs ->
            prefs[Keys.SUNSTEEL_ORE] = next.sunsteelOre.coerceAtLeast(0L)
            prefs[Keys.AETHER_HERBS] = next.aetherHerbs.coerceAtLeast(0L)
            prefs[Keys.ELEMENTAL_ESSENCE] = next.elementalEssence.coerceAtLeast(0L)
            prefs[Keys.STARFORGED_INGOTS] = next.starforgedIngots.coerceAtLeast(0L)
            prefs[Keys.FOG_LANTERN_CHARGES] = next.fogLanternCharges.coerceIn(0, 99)
            prefs[Keys.RESTED_CLARITY_BATTLES] = next.restedClarityBattlesLeft.coerceIn(0, 10)
            prefs[Keys.RESTED_CLARITY_PCT] = next.restedClarityPct.coerceIn(0, 100)
            prefs[Keys.REALM_FOG_STEPS_RAW] = encodeKeyIntMap(next.realmFogStepsMap)
            prefs[Keys.TOWN_MERCHANT_DATE_KEY] = next.townMerchantDateKey
            prefs[Keys.TOWN_MERCHANT_PURCHASED_RAW] = encodeKeyIntMap(next.townMerchantPurchasedMap)
            prefs[Keys.SPECIALIZATION_ID] = next.specializationId
            prefs[Keys.EQUIPPED_TITLE_ID] = next.equippedTitleId
            prefs[Keys.HARDCORE_OATH_ENABLED] = next.hardcoreOathEnabled
            prefs[Keys.ACTIVE_SOL_COIN_WAGER] = next.activeSolCoinWager.coerceAtLeast(0L)
            prefs[Keys.BATTLE_WOUND_STACKS] = next.battleWoundStacks.coerceIn(0, 3)
            prefs[Keys.MERCENARY_CONTRACT_ID] = next.mercenaryContractId
            prefs[Keys.MERCENARY_BATTLES_LEFT] = next.mercenaryBattlesLeft.coerceAtLeast(0)
            prefs[Keys.SHRINE_BLESSING_LABEL] = next.shrineBlessingLabel
            prefs[Keys.SHRINE_BATTLES_LEFT] = next.shrineBattlesLeft.coerceAtLeast(0)
            prefs[Keys.ITEM_DURABILITY_RAW] = encodeKeyIntMap(next.itemDurabilityMap)
            prefs[Keys.BESTIARY_DEFEATS_RAW] = encodeKeyIntMap(next.bestiaryDefeatsMap)
            prefs[Keys.UNLOCKED_RUNES_RAW] = encodeKeyIntMap(next.unlockedRunesMap)
            prefs[Keys.ACTIVITY_MASTERY_XP_RAW] = encodeKeyIntMap(next.activityMasteryXpMap)
            prefs[Keys.COLLECTED_RELIC_IDS_RAW] = next.collectedRelicIds.joinToString(",")
            prefs[Keys.WILDS_HARVESTED_KEYS_RAW] = next.wildsHarvestedKeys.toList().takeLast(200).joinToString(",")
            prefs[Keys.RELAXED_TIMING_ASSIST] = next.relaxedTimingAssist
            prefs[Keys.HERO_STAMINA] = next.heroStamina.coerceIn(0, 400)
            prefs[Keys.MAX_HERO_STAMINA] = next.maxHeroStamina.coerceIn(80, 350)
            prefs[Keys.ACTIVE_DREAM_TIER_ID] = next.activeDreamTierId
            prefs[Keys.LAST_SLEEP_DURATION_MIN] = next.lastSleepDurationMinutes.coerceIn(0, 1440)
            prefs[Keys.LAST_SANCTUARY_REST_DATE] = next.lastSanctuaryRestDateKey
            prefs[Keys.DREAM_SHARDS] = next.dreamShards.coerceAtLeast(0L)
            prefs[Keys.AGILITY_STAT] = next.agilityStat.coerceIn(5, 999)
            prefs[Keys.PERCEPTION_STAT] = next.perceptionStat.coerceIn(5, 999)
            prefs[Keys.CONSTELLATION_RANKS_RAW] = encodeKeyIntMap(next.constellationRanksMap)
            prefs[Keys.ITEM_AFFIX_MAP_RAW] = encodeKeyStringMap(next.itemAffixMap)
            prefs[Keys.ACTIVE_DUNGEON_ID] = next.activeDungeonId
            prefs[Keys.ACTIVE_DUNGEON_ROOM_INDEX] = next.activeDungeonRoomIndex.coerceAtLeast(0)
            prefs[Keys.ACTIVE_DUNGEON_ROOM_STEPS] = next.activeDungeonRoomSteps.coerceAtLeast(0)
            prefs[Keys.DUNGEON_CLEARS_COUNT] = next.dungeonClearsCount.coerceAtLeast(0)
            prefs[Keys.SLAYER_CONTRACT_STEPS_RAW] = encodeKeyIntMap(next.slayerContractStepsMap)
            prefs[Keys.SLAYER_CONTRACT_KILLS_RAW] = encodeKeyIntMap(next.slayerContractKillsMap)
            prefs[Keys.SLAYER_CONTRACTS_COMPLETED] = next.slayerContractsCompleted.coerceAtLeast(0)
        }
    }

    /**
     * Resets all local RPG progression (materials, fog, bestiary, masteries, relics, wilds nodes)
     * while strictly preserving real-world step telemetry, watch watermarks, and pedometer counts.
     */
    suspend fun resetRpgProgressKeepingSteps() {
        context.solstrideDataStore.edit { prefs ->
            prefs[Keys.SUNSTEEL_ORE] = 0L
            prefs[Keys.AETHER_HERBS] = 0L
            prefs[Keys.ELEMENTAL_ESSENCE] = 0L
            prefs[Keys.STARFORGED_INGOTS] = 0L
            prefs[Keys.FOG_LANTERN_CHARGES] = 0
            prefs[Keys.RESTED_CLARITY_BATTLES] = 0
            prefs[Keys.RESTED_CLARITY_PCT] = 0
            prefs[Keys.REALM_FOG_STEPS_RAW] = ""
            prefs[Keys.TOWN_MERCHANT_DATE_KEY] = ""
            prefs[Keys.TOWN_MERCHANT_PURCHASED_RAW] = ""
            prefs[Keys.SPECIALIZATION_ID] = ""
            prefs[Keys.EQUIPPED_TITLE_ID] = HeroTitleSpec.WAYFARER.id
            prefs[Keys.HARDCORE_OATH_ENABLED] = false
            prefs[Keys.ACTIVE_SOL_COIN_WAGER] = 0L
            prefs[Keys.BATTLE_WOUND_STACKS] = 0
            prefs[Keys.MERCENARY_CONTRACT_ID] = MercenaryContractSpec.NONE.id
            prefs[Keys.MERCENARY_BATTLES_LEFT] = 0
            prefs[Keys.SHRINE_BLESSING_LABEL] = ""
            prefs[Keys.SHRINE_BATTLES_LEFT] = 0
            prefs[Keys.ITEM_DURABILITY_RAW] = ""
            prefs[Keys.BESTIARY_DEFEATS_RAW] = ""
            prefs[Keys.UNLOCKED_RUNES_RAW] = ""
            prefs[Keys.ACTIVITY_MASTERY_XP_RAW] = ""
            prefs[Keys.COLLECTED_RELIC_IDS_RAW] = ""
            prefs[Keys.WILDS_HARVESTED_KEYS_RAW] = ""
            prefs[Keys.CONSUMED_CHEST_IDS_RAW] = ""
            prefs[Keys.HERO_STAMINA] = 85
            prefs[Keys.MAX_HERO_STAMINA] = 100
            prefs[Keys.ACTIVE_DREAM_TIER_ID] = "RESTORATIVE_SLUMBER"
            prefs[Keys.LAST_SLEEP_DURATION_MIN] = 390
            prefs[Keys.LAST_SANCTUARY_REST_DATE] = ""
            prefs[Keys.DREAM_SHARDS] = 3L
            prefs[Keys.AGILITY_STAT] = 5
            prefs[Keys.PERCEPTION_STAT] = 5
            prefs[Keys.CONSTELLATION_RANKS_RAW] = ""
            prefs[Keys.ITEM_AFFIX_MAP_RAW] = ""
            prefs[Keys.ACTIVE_DUNGEON_ID] = "dungeon_sunstone_catacombs"
            prefs[Keys.ACTIVE_DUNGEON_ROOM_INDEX] = 0
            prefs[Keys.ACTIVE_DUNGEON_ROOM_STEPS] = 0
            prefs[Keys.DUNGEON_CLEARS_COUNT] = 0
            prefs[Keys.SLAYER_CONTRACT_STEPS_RAW] = ""
            prefs[Keys.SLAYER_CONTRACT_KILLS_RAW] = ""
            prefs[Keys.SLAYER_CONTRACTS_COMPLETED] = 0
        }
    }

    suspend fun resetAllRpgAndStepProgress() {
        resetRpgProgressKeepingSteps()
        context.solstrideDataStore.edit { prefs ->
            prefs[Keys.PEDOMETER_DAILY_STEPS] = 0L
        }
    }

    suspend fun setDisplayMode(mode: SolstrideDisplayMode) {
        context.solstrideDataStore.edit { it[Keys.DISPLAY_MODE] = mode.name }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.solstrideDataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setBatteryBatchingEnabled(enabled: Boolean) {
        context.solstrideDataStore.edit { it[Keys.BATTERY_BATCHING] = enabled }
    }

    suspend fun setLastHardwareStepCount(count: Long) {
        context.solstrideDataStore.edit { it[Keys.LAST_HW_STEP_COUNT] = count }
    }

    suspend fun setLastWatchSyncMs(timestampMs: Long) {
        context.solstrideDataStore.edit { it[Keys.LAST_WATCH_SYNC_MS] = timestampMs }
    }

    suspend fun acknowledgeWatchOnlyWarning() {
        context.solstrideDataStore.edit { it[Keys.WATCH_ONLY_WARNING_ACKNOWLEDGED] = true }
    }

    suspend fun setPedometerEnabled(enabled: Boolean) {
        context.solstrideDataStore.edit { it[Keys.PEDOMETER_ENABLED] = enabled }
    }

    suspend fun recordDailyPedometerSteps(dateKey: String, delta: Long) {
        if (delta <= 0L) return
        context.solstrideDataStore.edit { prefs ->
            val currentKey = prefs[Keys.PEDOMETER_DATE_KEY] ?: ""
            val currentCount = if (currentKey == dateKey) prefs[Keys.PEDOMETER_DAILY_STEPS] ?: 0L else 0L
            prefs[Keys.PEDOMETER_DATE_KEY] = dateKey
            prefs[Keys.PEDOMETER_DAILY_STEPS] = (currentCount + delta).coerceAtMost(500_000L)
        }
    }

    suspend fun setDailyStepGoal(goal: Long) {
        context.solstrideDataStore.edit { it[Keys.DAILY_STEP_GOAL] = goal.coerceIn(2000L, 25000L) }
    }

    suspend fun readLastHardwareStepCount(): Long {
        return context.solstrideDataStore.data.first()[Keys.LAST_HW_STEP_COUNT] ?: -1L
    }

    suspend fun readPedometerEnabled(): Boolean {
        return context.solstrideDataStore.data.first()[Keys.PEDOMETER_ENABLED] ?: true
    }

    suspend fun readHealthConnectAllSources(): Boolean {
        return context.solstrideDataStore.data.first()[Keys.HEALTH_CONNECT_ALL_SOURCES] ?: false
    }

    suspend fun setHealthConnectAllSources(allSources: Boolean) {
        context.solstrideDataStore.edit { it[Keys.HEALTH_CONNECT_ALL_SOURCES] = allSources }
    }

    suspend fun readLastWatchStepReceivedMs(): Long {
        return context.solstrideDataStore.data.first()[Keys.LAST_WATCH_STEP_RECEIVED_MS] ?: 0L
    }

    suspend fun setLastWatchStepReceivedMs(timestampMs: Long) {
        context.solstrideDataStore.edit { it[Keys.LAST_WATCH_STEP_RECEIVED_MS] = timestampMs }
    }

    suspend fun readHealthConnectWatermarksRaw(): String {
        return context.solstrideDataStore.data.first()[Keys.HEALTH_CONNECT_WATERMARKS] ?: ""
    }

    suspend fun writeHealthConnectWatermarksRaw(encoded: String) {
        context.solstrideDataStore.edit { it[Keys.HEALTH_CONNECT_WATERMARKS] = encoded }
    }

    suspend fun readLastCreditedSleepEndMs(): Long {
        return context.solstrideDataStore.data.first()[Keys.LAST_CREDITED_SLEEP_END_MS] ?: 0L
    }

    suspend fun setLastCreditedSleepEndMs(endMs: Long) {
        context.solstrideDataStore.edit { it[Keys.LAST_CREDITED_SLEEP_END_MS] = endMs }
    }

    suspend fun readManuallyWokenSleepMs(): Long {
        return context.solstrideDataStore.data.first()[Keys.MANUALLY_WOKEN_SLEEP_MS] ?: 0L
    }

    suspend fun setManuallyWokenSleepMs(sessionMarkerMs: Long) {
        context.solstrideDataStore.edit { it[Keys.MANUALLY_WOKEN_SLEEP_MS] = sessionMarkerMs }
    }

    suspend fun setWatchLiveSleeping(sleeping: Boolean, sourceLabel: String = "Galaxy Watch", sinceMs: Long = System.currentTimeMillis()) {
        context.solstrideDataStore.edit { prefs ->
            prefs[Keys.WATCH_LIVE_SLEEPING] = sleeping
            prefs[Keys.WATCH_LIVE_SLEEP_SOURCE] = sourceLabel
            if (sleeping) {
                val existingSince = prefs[Keys.WATCH_LIVE_SLEEP_SINCE_MS] ?: 0L
                prefs[Keys.WATCH_LIVE_SLEEP_SINCE_MS] = if (existingSince > 0L) existingSince else sinceMs
            } else {
                prefs[Keys.WATCH_LIVE_SLEEP_SINCE_MS] = 0L
            }
        }
    }

    suspend fun readWatchLiveSleepTriple(): Triple<Boolean, Long, String> {
        val prefs = context.solstrideDataStore.data.first()
        return Triple(
            prefs[Keys.WATCH_LIVE_SLEEPING] ?: false,
            prefs[Keys.WATCH_LIVE_SLEEP_SINCE_MS] ?: 0L,
            prefs[Keys.WATCH_LIVE_SLEEP_SOURCE] ?: "Galaxy Watch"
        )
    }

    suspend fun readConsumedChestIds(): Set<String> {
        val raw = context.solstrideDataStore.data.first()[Keys.CONSUMED_CHEST_IDS_RAW] ?: ""
        return parseIdList(raw).toSet()
    }

    suspend fun markChestConsumed(chestId: String) {
        val cleanId = chestId.trim()
        if (cleanId.isBlank()) return
        context.solstrideDataStore.edit { prefs ->
            val current = parseIdList(prefs[Keys.CONSUMED_CHEST_IDS_RAW] ?: "")
            if (cleanId in current) return@edit
            val updated = (current + cleanId).takeLast(300)
            prefs[Keys.CONSUMED_CHEST_IDS_RAW] = updated.joinToString(",")
        }
    }
}
