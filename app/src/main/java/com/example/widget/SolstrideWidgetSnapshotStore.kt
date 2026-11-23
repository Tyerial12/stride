package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.model.DerivedHeroStats
import com.example.data.model.HardcoreRpgState
import com.example.data.model.HeroProfile
import com.example.data.model.InventoryItem
import com.example.data.model.ItemSlot
import com.example.data.model.OwnedPet
import com.example.data.model.PetCatalog
import com.example.data.model.PetEvolutionCatalog
import com.example.data.model.currentCalendarDateKey
import com.example.domain.ExpeditionEngine
import com.example.domain.ProgressionEngine
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PetWidgetLedger(
    val petId: String = "",
    val pendingPetXp: Long = 0L,
    val pendingBondXp: Long = 0L,
    val pendingCareActions: Int = 0,
    val pendingCareDateKey: String = "",
    val pendingBonusCareUsed: Int = 0,
    val pendingCoinsSpent: Long = 0L,
    val pendingOreWon: Long = 0L,
    val pendingEssenceWon: Long = 0L,
    val pendingWildWins: Int = 0,
    val pendingWildLosses: Int = 0,
    val pendingFeeds: Int = 0,
    val totalWildWins: Int = 0,
    val petMoodPct: Int = 65,
    val petWildStamina: Int = 3,
    val stepTreatsAvailable: Int = 1,
    val lastStaminaStepCount: Long = 0L,
    val lastPassiveRechargeMs: Long = 0L,
    val lastWildBattleSummary: String = "Close app & tap Feed or Wild Fight to level your pet!"
) {
    fun hasPendingSync(): Boolean =
        pendingPetXp > 0L || pendingBondXp > 0L || pendingCareActions > 0 ||
            pendingBonusCareUsed > 0 || pendingCoinsSpent > 0L || pendingOreWon > 0L ||
            pendingEssenceWon > 0L || pendingWildWins > 0 || pendingWildLosses > 0 || pendingFeeds > 0
}

data class WidgetSnapshotState(
    val displayName: String = "Solar Vanguard",
    val heroClassLabel: String = "Vanguard",
    val specializationTitle: String = "Sunshield Paladin",
    val elementSymbol: String = "☀",
    val elementLabel: String = "Solar",
    val level: Long = 1L,
    val prestigeLevel: Long = 0L,
    val currentHp: Long = 140L,
    val maxHp: Long = 140L,
    val strideEnergy: Long = 40L,
    val maxStrideEnergy: Long = 100L,
    val attack: Int = 28,
    val defense: Int = 20,
    val critChancePct: Int = 8,
    val heroPower: Int = 180,
    val solCoins: Long = 20L,
    val sunshards: Long = 0L,
    val realmNumber: Long = 1L,
    val realmStage: Long = 1L,
    val realmName: String = "Sunlit Dunes",
    val spireFloor: Long = 1L,
    val bossesDefeated: Long = 0L,
    val encounterProgressPct: Int = 35,
    val unopenedChestCount: Int = 0,
    val claimableQuestCount: Int = 0,
    val unspentMasteryPoints: Long = 0L,
    val equippedGearCount: Int = 1,
    val equippedArmorSlots: Int = 0,
    val woundStacks: Int = 0,
    val woundPenaltyPct: Int = 0,
    val lowestDurabilityPct: Int = 100,
    val shatteredGearCount: Int = 0,
    val hardcoreOathEnabled: Boolean = false,
    val isHeroSleeping: Boolean = false,
    val sleepSourceLabel: String = "",
    val activeSleepMinutes: Long = 0L,
    val lastCompletedSleepMinutes: Long = 0L,
    val isAppForegroundLocked: Boolean = false,
    val hasPet: Boolean = false,
    val petId: String = "",
    val petName: String = "No Companion",
    val petTitle: String = "Visit Sanctuary",
    val petRarityLabel: String = "",
    val petLevel: Int = 1,
    val petStage: Int = 1,
    val petBondXp: Long = 0L,
    val petDailyCareUsed: Int = 0,
    val petDailyCareLimit: Int = PetEvolutionCatalog.DAILY_CARE_LIMIT,
    val petLastCareDateKey: String = "",
    val petBonusCareCharges: Int = 0,
    val petXp: Long = 0L,
    val petXpToNext: Long = 60L,
    val petElementSymbol: String = "🐾",
    val petElementLabel: String = "None",
    val petFoodLevel: Int = 0,
    val petGearLevel: Int = 0,
    val petActiveSkillName: String = "Adopt a pet for Combat Combos",
    val petBonusSummary: String = "Equip a companion for ATK, DEF & Step Energy bonuses.",
    val petMoodPct: Int = 65,
    val petWildStamina: Int = 3,
    val petStepTreats: Int = 1,
    val petTotalWildWins: Int = 0,
    val petLastBattleSummary: String = "Close app & tap Feed or Wild Fight to level your pet!",
    val todaySteps: Long = 0L,
    val dailyStepGoal: Long = 6000L,
    val weeklySteps: Long = 0L,
    val stepsToNextStamina: Int = 250,
    val isWatchConnected: Boolean = false,
    val stepSourceLabel: String = "Phone Live",
    val sunsteelOre: Long = 0L,
    val aetherHerbs: Long = 0L,
    val elementalEssence: Long = 0L,
    val hasActiveExpedition: Boolean = false,
    val expeditionEndMs: Long = 0L,
    val expeditionDurationMin: Long = 0L,
    val lastUpdatedMs: Long = System.currentTimeMillis()
) {
    fun hpPercent(): Int =
        ((currentHp.toFloat() / maxHp.coerceAtLeast(1L).toFloat()) * 100f).toInt().coerceIn(0, 100)

    fun energyPercent(): Int =
        ((strideEnergy.toFloat() / maxStrideEnergy.coerceAtLeast(1L).toFloat()) * 100f).toInt().coerceIn(0, 100)

    fun stepGoalPercent(): Int =
        ((todaySteps.toFloat() / dailyStepGoal.coerceAtLeast(1L).toFloat()) * 100f).toInt().coerceIn(0, 100)

    fun petXpPercent(): Int =
        ((petXp.toFloat() / petXpToNext.coerceAtLeast(1L).toFloat()) * 100f).toInt().coerceIn(0, 100)

    fun effectiveDailyCareUsed(todayKey: String = currentCalendarDateKey()): Int {
        val cap = petDailyCareLimit.coerceAtLeast(1)
        return if (petLastCareDateKey == todayKey) petDailyCareUsed.coerceIn(0, cap) else 0
    }

    fun remainingDailyCare(todayKey: String = currentCalendarDateKey()): Int {
        val cap = petDailyCareLimit.coerceAtLeast(1)
        return (cap - effectiveDailyCareUsed(todayKey)).coerceAtLeast(0)
    }

    fun totalRemainingCare(todayKey: String = currentCalendarDateKey()): Int =
        remainingDailyCare(todayKey) + petBonusCareCharges.coerceAtLeast(0)

    fun isDailyCareExhausted(todayKey: String = currentCalendarDateKey()): Boolean =
        hasPet && totalRemainingCare(todayKey) <= 0

    fun dailyCareRestedMessage(): String {
        val cap = petDailyCareLimit.coerceAtLeast(1)
        return "🌙 $petName is happily rested! Daily Care resets tomorrow ($cap/$cap)."
    }

    fun hasHardcoreAlert(): Boolean =
        woundStacks > 0 || shatteredGearCount > 0 || lowestDurabilityPct < 30

    fun hardcoreAlertLine(): String = when {
        woundStacks > 0 && shatteredGearCount > 0 ->
            "⚠ $woundStacks Wound(s) (-${woundPenaltyPct}%) & $shatteredGearCount Shattered Gear!"
        woundStacks > 0 ->
            "⚠ $woundStacks Battle Wound(s) (-${woundPenaltyPct}% HP/ATK) • Tap to Cure"
        shatteredGearCount > 0 ->
            "⚠ $shatteredGearCount Shattered Gear (-50% stats) • Tap Forge to Repair"
        lowestDurabilityPct < 30 ->
            "⚠ Worn Gear (${lowestDurabilityPct}% Durability) • Repair at Forge"
        else -> ""
    }

    fun expeditionStatusLine(nowMs: Long = System.currentTimeMillis()): String {
        if (isHeroSleeping) {
            return "🌙 Hero Sleeping (${sleepSourceLabel.ifBlank { "Watch Rest" }})"
        }
        if (!hasActiveExpedition || expeditionEndMs <= 0L) {
            return if (encounterProgressPct >= 100) "⚔ Encounter Ready! Tap Battle"
            else "Camp Ready • Start Expedition"
        }
        val remaining = expeditionEndMs - nowMs
        return if (remaining <= 0L) {
            "🎁 Expedition Complete! Tap to Claim"
        } else {
            "⏳ Expedition: ${ExpeditionEngine.formatCountdown(remaining)} left"
        }
    }

    fun formattedSteps(): String = NumberFormat.getIntegerInstance(Locale.US).format(todaySteps)

    fun formattedGoal(): String = NumberFormat.getIntegerInstance(Locale.US).format(dailyStepGoal)

    fun formattedWeeklySteps(): String = NumberFormat.getIntegerInstance(Locale.US).format(weeklySteps)

    fun prestigeTag(): String = if (prestigeLevel > 0L) " ★P$prestigeLevel" else ""

    fun expeditionProgressPercent(nowMs: Long = System.currentTimeMillis()): Int {
        if (!hasActiveExpedition || expeditionDurationMin <= 0L || expeditionEndMs <= 0L) return 0
        val totalMs = (expeditionDurationMin * 60_000L).coerceAtLeast(1L)
        val remaining = (expeditionEndMs - nowMs).coerceAtLeast(0L)
        val elapsed = (totalMs - remaining).coerceIn(0L, totalMs)
        return ((elapsed.toFloat() / totalMs.toFloat()) * 100f).toInt().coerceIn(0, 100)
    }

    fun gearLoadoutSummary(): String = when {
        hasHardcoreAlert() -> hardcoreAlertLine()
        equippedArmorSlots <= 0 -> "🛡 0/5 Armor Equipped • Forge Armor at Anvil!"
        else -> "🛡 Gear $equippedGearCount/6 • Armor $equippedArmorSlots/5 • Dur ${lowestDurabilityPct}%"
    }

    fun sleepStatusSummary(): String {
        if (!isWatchConnected) {
            return "☀ Watch Disconnected • Steps fuel Energy & Stamina"
        }
        if (isHeroSleeping) {
            val reward = ProgressionEngine.computeSleepSolCoinReward(activeSleepMinutes)
            val dur = if (activeSleepMinutes >= 60L) {
                "${activeSleepMinutes / 60L}h ${activeSleepMinutes % 60L}m"
            } else if (activeSleepMinutes > 0L) {
                "${activeSleepMinutes}m"
            } else {
                "Live"
            }
            return "🌙 Sleeping ($dur) • +${reward.solCoinsEarned}/50 Coins (${reward.sleepPercent}%)"
        }
        if (lastCompletedSleepMinutes > 0L) {
            val reward = ProgressionEngine.computeSleepSolCoinReward(lastCompletedSleepMinutes)
            val hrs = lastCompletedSleepMinutes / 60L
            val mins = lastCompletedSleepMinutes % 60L
            val fmt = if (hrs > 0L) "${hrs}h ${mins}m" else "${mins}m"
            return "☀ Watch Connected • Last Sleep $fmt (+${reward.solCoinsEarned}/50 Coins)"
        }
        return "☀ Watch Connected • Sleep grants up to 50 Sol Coins"
    }

    fun formattedSyncTime(): String {
        if (lastUpdatedMs <= 0L) return "Tap refresh to sync"
        val fmt = SimpleDateFormat("h:mm a", Locale.US)
        return "Synced ${fmt.format(Date(lastUpdatedMs))}"
    }
}

enum class WidgetPinResult {
    PIN_REQUESTED,
    NOT_SUPPORTED
}

data class WidgetAutoSyncConfig(
    val enabled: Boolean = true,
    val intervalMinutes: Long = 15L
) {
    fun intervalLabel(): String = when (intervalMinutes) {
        15L -> "Every 15m"
        30L -> "Every 30m"
        60L -> "Every 60m"
        else -> "Every ${intervalMinutes}m"
    }
}

enum class SolstrideWidgetType(
    val id: String,
    val title: String,
    val sizeBadge: String,
    val description: String,
    val providerClass: Class<*>
) {
    UNIFIED_COMMAND(
        id = "command",
        title = "Battle & Campaign Radar",
        sizeBadge = "4×2",
        description = "Realm & Stage tracker, Endless Spire floor, Boss Encounter Radar, Unopened Vault Chests, Claimable Quests & Skill Points.",
        providerClass = SolstrideCommandWidgetProvider::class.java
    ),
    HERO_STATS(
        id = "hero",
        title = "Hero Build & Forge Armory",
        sizeBadge = "3×2",
        description = "Character Vitals (HP/Energy), ATK/DEF/CRIT, 6-Slot Gear Loadout, Durability/Wound warnings, Treasury & Blacksmith Forge.",
        providerClass = SolstrideHeroWidgetProvider::class.java
    ),
    COMPANION_PET(
        id = "pet",
        title = "Interactive Pet Arena",
        sizeBadge = "4×2",
        description = "Exclusive Home-Screen Pet Leveling! Feed treats & Fight Wild Pets while app is closed — auto-syncs & locks when app opens.",
        providerClass = SolstridePetWidgetProvider::class.java
    ),
    STEPS_EXPEDITION(
        id = "steps",
        title = "Steps, Watch Sleep & Expedition",
        sizeBadge = "3×2",
        description = "Live Daily/Weekly Steps, Galaxy Watch link, Real-Life Watch Sleep status, Pet Stamina step tracker & Timed Expedition countdown.",
        providerClass = SolstrideStepWidgetProvider::class.java
    )
}

object SolstrideWidgetSnapshotStore {
    private const val PREFS_NAME = "solstride_widget_snapshot_v1"

    fun save(context: Context, snapshot: WidgetSnapshotState) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("displayName", snapshot.displayName)
            .putString("heroClassLabel", snapshot.heroClassLabel)
            .putString("specializationTitle", snapshot.specializationTitle)
            .putString("elementSymbol", snapshot.elementSymbol)
            .putString("elementLabel", snapshot.elementLabel)
            .putLong("level", snapshot.level)
            .putLong("prestigeLevel", snapshot.prestigeLevel)
            .putLong("currentHp", snapshot.currentHp)
            .putLong("maxHp", snapshot.maxHp)
            .putLong("strideEnergy", snapshot.strideEnergy)
            .putLong("maxStrideEnergy", snapshot.maxStrideEnergy)
            .putInt("attack", snapshot.attack)
            .putInt("defense", snapshot.defense)
            .putInt("critChancePct", snapshot.critChancePct)
            .putInt("heroPower", snapshot.heroPower)
            .putLong("solCoins", snapshot.solCoins)
            .putLong("sunshards", snapshot.sunshards)
            .putLong("realmNumber", snapshot.realmNumber)
            .putLong("realmStage", snapshot.realmStage)
            .putString("realmName", snapshot.realmName)
            .putLong("spireFloor", snapshot.spireFloor)
            .putLong("bossesDefeated", snapshot.bossesDefeated)
            .putInt("encounterProgressPct", snapshot.encounterProgressPct)
            .putInt("unopenedChestCount", snapshot.unopenedChestCount)
            .putInt("claimableQuestCount", snapshot.claimableQuestCount)
            .putLong("unspentMasteryPoints", snapshot.unspentMasteryPoints)
            .putInt("equippedGearCount", snapshot.equippedGearCount)
            .putInt("equippedArmorSlots", snapshot.equippedArmorSlots)
            .putInt("woundStacks", snapshot.woundStacks)
            .putInt("woundPenaltyPct", snapshot.woundPenaltyPct)
            .putInt("lowestDurabilityPct", snapshot.lowestDurabilityPct)
            .putInt("shatteredGearCount", snapshot.shatteredGearCount)
            .putBoolean("hardcoreOathEnabled", snapshot.hardcoreOathEnabled)
            .putBoolean("isHeroSleeping", snapshot.isHeroSleeping)
            .putString("sleepSourceLabel", snapshot.sleepSourceLabel)
            .putLong("activeSleepMinutes", snapshot.activeSleepMinutes)
            .putLong("lastCompletedSleepMinutes", snapshot.lastCompletedSleepMinutes)
            .putBoolean("isAppForegroundLocked", snapshot.isAppForegroundLocked)
            .putBoolean("hasPet", snapshot.hasPet)
            .putString("petId", snapshot.petId)
            .putString("petName", snapshot.petName)
            .putString("petTitle", snapshot.petTitle)
            .putString("petRarityLabel", snapshot.petRarityLabel)
            .putInt("petLevel", snapshot.petLevel)
            .putInt("petStage", snapshot.petStage)
            .putLong("petBondXp", snapshot.petBondXp)
            .putInt("petDailyCareUsed", snapshot.petDailyCareUsed)
            .putInt("petDailyCareLimit", snapshot.petDailyCareLimit)
            .putString("petLastCareDateKey", snapshot.petLastCareDateKey)
            .putInt("petBonusCareCharges", snapshot.petBonusCareCharges.coerceIn(0, 99))
            .putLong("petXp", snapshot.petXp)
            .putLong("petXpToNext", snapshot.petXpToNext)
            .putString("petElementSymbol", snapshot.petElementSymbol)
            .putString("petElementLabel", snapshot.petElementLabel)
            .putInt("petFoodLevel", snapshot.petFoodLevel)
            .putInt("petGearLevel", snapshot.petGearLevel)
            .putString("petActiveSkillName", snapshot.petActiveSkillName)
            .putString("petBonusSummary", snapshot.petBonusSummary)
            .putInt("petMoodPct", snapshot.petMoodPct)
            .putInt("petWildStamina", snapshot.petWildStamina)
            .putInt("petStepTreats", snapshot.petStepTreats)
            .putInt("petTotalWildWins", snapshot.petTotalWildWins)
            .putString("petLastBattleSummary", snapshot.petLastBattleSummary)
            .putLong("todaySteps", snapshot.todaySteps)
            .putLong("dailyStepGoal", snapshot.dailyStepGoal)
            .putLong("weeklySteps", snapshot.weeklySteps)
            .putInt("stepsToNextStamina", snapshot.stepsToNextStamina)
            .putBoolean("isWatchConnected", snapshot.isWatchConnected)
            .putString("stepSourceLabel", snapshot.stepSourceLabel)
            .putLong("sunsteelOre", snapshot.sunsteelOre)
            .putLong("aetherHerbs", snapshot.aetherHerbs)
            .putLong("elementalEssence", snapshot.elementalEssence)
            .putBoolean("hasActiveExpedition", snapshot.hasActiveExpedition)
            .putLong("expeditionEndMs", snapshot.expeditionEndMs)
            .putLong("expeditionDurationMin", snapshot.expeditionDurationMin)
            .putLong("lastUpdatedMs", snapshot.lastUpdatedMs)
            .apply()
    }

    fun read(context: Context): WidgetSnapshotState {
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ledger = readPetLedger(context)
        val baseCoins = p.getLong("solCoins", 20L)
        val basePetLevel = p.getInt("petLevel", 1)
        val basePetXp = p.getLong("petXp", 0L)
        val savedPetId = p.getString("petId", "") ?: ""
        val ledgerMatchesPet = ledger.petId.isBlank() || savedPetId.isBlank() || ledger.petId == savedPetId
        val previewProg = WildPetBattleEngine.computePreviewProgression(
            baseLevel = basePetLevel,
            baseXp = basePetXp,
            addedXp = if (ledgerMatchesPet) ledger.pendingPetXp else 0L
        )
        val todayKey = currentCalendarDateKey()
        val dailyCareLimit = p.getInt("petDailyCareLimit", PetEvolutionCatalog.DAILY_CARE_LIMIT).coerceAtLeast(1)
        val savedCareDateKey = p.getString("petLastCareDateKey", "") ?: ""
        val baseCareUsed = if (savedCareDateKey == todayKey) {
            p.getInt("petDailyCareUsed", 0).coerceIn(0, dailyCareLimit)
        } else {
            0
        }
        val pendingCareToday = if (ledgerMatchesPet && ledger.pendingCareDateKey == todayKey) {
            ledger.pendingCareActions.coerceAtLeast(0)
        } else {
            0
        }
        val effectiveDailyCareUsed = (baseCareUsed + pendingCareToday).coerceIn(0, dailyCareLimit)
        val effectiveCareDateKey = if (effectiveDailyCareUsed > 0) todayKey else savedCareDateKey
        val effectiveBonusCareCharges = (p.getInt("petBonusCareCharges", 0).coerceIn(0, 99) - ledger.pendingBonusCareUsed)
            .coerceAtLeast(0)
        val effectiveBondXp = (p.getLong("petBondXp", 0L).coerceAtLeast(0L) +
            if (ledgerMatchesPet) ledger.pendingBondXp else 0L)
            .coerceAtMost(PetEvolutionCatalog.MAX_BOND_XP)
        val todayStepsVal = p.getLong("todaySteps", 0L)
        val stepsTowardNext = (todayStepsVal - ledger.lastStaminaStepCount).coerceAtLeast(0L) % WildPetBattleEngine.STEPS_PER_STAMINA
        val computedStepsToNextStamina = (WildPetBattleEngine.STEPS_PER_STAMINA - stepsTowardNext).toInt().coerceIn(1, WildPetBattleEngine.STEPS_PER_STAMINA.toInt())
        return WidgetSnapshotState(
            displayName = p.getString("displayName", "Solar Vanguard") ?: "Solar Vanguard",
            heroClassLabel = p.getString("heroClassLabel", "Vanguard") ?: "Vanguard",
            specializationTitle = p.getString("specializationTitle", "Sunshield Paladin") ?: "Sunshield Paladin",
            elementSymbol = p.getString("elementSymbol", "☀") ?: "☀",
            elementLabel = p.getString("elementLabel", "Solar") ?: "Solar",
            level = p.getLong("level", 1L),
            prestigeLevel = p.getLong("prestigeLevel", 0L),
            currentHp = p.getLong("currentHp", 140L),
            maxHp = p.getLong("maxHp", 140L),
            strideEnergy = p.getLong("strideEnergy", 40L),
            maxStrideEnergy = p.getLong("maxStrideEnergy", 100L),
            attack = p.getInt("attack", 28),
            defense = p.getInt("defense", 20),
            critChancePct = p.getInt("critChancePct", 8),
            heroPower = p.getInt("heroPower", 180),
            solCoins = (baseCoins - ledger.pendingCoinsSpent).coerceAtLeast(0L),
            sunshards = p.getLong("sunshards", 0L),
            realmNumber = p.getLong("realmNumber", 1L),
            realmStage = p.getLong("realmStage", 1L),
            realmName = p.getString("realmName", "Sunlit Dunes") ?: "Sunlit Dunes",
            spireFloor = p.getLong("spireFloor", 1L),
            bossesDefeated = p.getLong("bossesDefeated", 0L),
            encounterProgressPct = p.getInt("encounterProgressPct", 35),
            unopenedChestCount = p.getInt("unopenedChestCount", 0),
            claimableQuestCount = p.getInt("claimableQuestCount", 0),
            unspentMasteryPoints = p.getLong("unspentMasteryPoints", 0L),
            equippedGearCount = p.getInt("equippedGearCount", 1),
            equippedArmorSlots = p.getInt("equippedArmorSlots", 0),
            woundStacks = p.getInt("woundStacks", 0),
            woundPenaltyPct = p.getInt("woundPenaltyPct", 0),
            lowestDurabilityPct = p.getInt("lowestDurabilityPct", 100),
            shatteredGearCount = p.getInt("shatteredGearCount", 0),
            hardcoreOathEnabled = p.getBoolean("hardcoreOathEnabled", false),
            isHeroSleeping = p.getBoolean("isHeroSleeping", false),
            sleepSourceLabel = p.getString("sleepSourceLabel", "") ?: "",
            activeSleepMinutes = p.getLong("activeSleepMinutes", 0L),
            lastCompletedSleepMinutes = p.getLong("lastCompletedSleepMinutes", 0L),
            isAppForegroundLocked = p.getBoolean("isAppForegroundLocked", false),
            hasPet = p.getBoolean("hasPet", false),
            petId = savedPetId,
            petName = p.getString("petName", "No Companion") ?: "No Companion",
            petTitle = p.getString("petTitle", "Visit Sanctuary") ?: "Visit Sanctuary",
            petRarityLabel = p.getString("petRarityLabel", "") ?: "",
            petLevel = previewProg.level,
            petStage = p.getInt("petStage", 1).coerceIn(1, 3),
            petBondXp = effectiveBondXp,
            petDailyCareUsed = effectiveDailyCareUsed,
            petDailyCareLimit = dailyCareLimit,
            petLastCareDateKey = effectiveCareDateKey,
            petBonusCareCharges = effectiveBonusCareCharges,
            petXp = previewProg.xp,
            petXpToNext = previewProg.xpToNext,
            petElementSymbol = p.getString("petElementSymbol", "🐾") ?: "🐾",
            petElementLabel = p.getString("petElementLabel", "None") ?: "None",
            petFoodLevel = p.getInt("petFoodLevel", 0),
            petGearLevel = p.getInt("petGearLevel", 0),
            petActiveSkillName = p.getString("petActiveSkillName", "Adopt a pet for Combat Combos")
                ?: "Adopt a pet for Combat Combos",
            petBonusSummary = p.getString("petBonusSummary", "Equip a companion for ATK, DEF & Step Energy bonuses.")
                ?: "Equip a companion for ATK, DEF & Step Energy bonuses.",
            petMoodPct = ledger.petMoodPct,
            petWildStamina = ledger.petWildStamina,
            petStepTreats = ledger.stepTreatsAvailable,
            petTotalWildWins = ledger.totalWildWins,
            petLastBattleSummary = ledger.lastWildBattleSummary,
            todaySteps = todayStepsVal,
            dailyStepGoal = p.getLong("dailyStepGoal", 6000L),
            weeklySteps = p.getLong("weeklySteps", 0L),
            stepsToNextStamina = computedStepsToNextStamina,
            isWatchConnected = p.getBoolean("isWatchConnected", false),
            stepSourceLabel = p.getString("stepSourceLabel", "Phone Live") ?: "Phone Live",
            sunsteelOre = p.getLong("sunsteelOre", 0L) + ledger.pendingOreWon,
            aetherHerbs = p.getLong("aetherHerbs", 0L),
            elementalEssence = p.getLong("elementalEssence", 0L) + ledger.pendingEssenceWon,
            hasActiveExpedition = p.getBoolean("hasActiveExpedition", false),
            expeditionEndMs = p.getLong("expeditionEndMs", 0L),
            expeditionDurationMin = p.getLong("expeditionDurationMin", 0L),
            lastUpdatedMs = p.getLong("lastUpdatedMs", System.currentTimeMillis())
        )
    }

    fun readPetLedger(context: Context): PetWidgetLedger {
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return PetWidgetLedger(
            petId = p.getString("ledger_petId", "") ?: "",
            pendingPetXp = p.getLong("ledger_pendingPetXp", 0L).coerceAtLeast(0L),
            pendingBondXp = p.getLong("ledger_pendingBondXp", 0L).coerceAtLeast(0L),
            pendingCareActions = p.getInt("ledger_pendingCareActions", 0).coerceAtLeast(0),
            pendingCareDateKey = p.getString("ledger_pendingCareDateKey", "") ?: "",
            pendingBonusCareUsed = p.getInt("ledger_pendingBonusCareUsed", 0).coerceAtLeast(0),
            pendingCoinsSpent = p.getLong("ledger_pendingCoinsSpent", 0L).coerceAtLeast(0L),
            pendingOreWon = p.getLong("ledger_pendingOreWon", 0L).coerceAtLeast(0L),
            pendingEssenceWon = p.getLong("ledger_pendingEssenceWon", 0L).coerceAtLeast(0L),
            pendingWildWins = p.getInt("ledger_pendingWildWins", 0).coerceAtLeast(0),
            pendingWildLosses = p.getInt("ledger_pendingWildLosses", 0).coerceAtLeast(0),
            pendingFeeds = p.getInt("ledger_pendingFeeds", 0).coerceAtLeast(0),
            totalWildWins = p.getInt("ledger_totalWildWins", 0).coerceAtLeast(0),
            petMoodPct = p.getInt("ledger_petMoodPct", 65).coerceIn(0, 100),
            petWildStamina = p.getInt("ledger_petWildStamina", 3).coerceIn(0, WildPetBattleEngine.MAX_WILD_STAMINA),
            stepTreatsAvailable = p.getInt("ledger_stepTreats", 1).coerceIn(0, 20),
            lastStaminaStepCount = p.getLong("ledger_lastStaminaStepCount", 0L).coerceAtLeast(0L),
            lastPassiveRechargeMs = p.getLong("ledger_lastPassiveRechargeMs", System.currentTimeMillis()),
            lastWildBattleSummary = p.getString(
                "ledger_lastWildBattleSummary",
                "Close app & tap Feed or Wild Fight to level your pet!"
            ) ?: "Close app & tap Feed or Wild Fight to level your pet!"
        )
    }

    private fun savePetLedger(context: Context, ledger: PetWidgetLedger) {
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        p.edit()
            .putString("ledger_petId", ledger.petId)
            .putLong("ledger_pendingPetXp", ledger.pendingPetXp.coerceAtLeast(0L))
            .putLong("ledger_pendingBondXp", ledger.pendingBondXp.coerceAtLeast(0L))
            .putInt("ledger_pendingCareActions", ledger.pendingCareActions.coerceAtLeast(0))
            .putString("ledger_pendingCareDateKey", ledger.pendingCareDateKey)
            .putInt("ledger_pendingBonusCareUsed", ledger.pendingBonusCareUsed.coerceAtLeast(0))
            .putLong("ledger_pendingCoinsSpent", ledger.pendingCoinsSpent.coerceAtLeast(0L))
            .putLong("ledger_pendingOreWon", ledger.pendingOreWon.coerceAtLeast(0L))
            .putLong("ledger_pendingEssenceWon", ledger.pendingEssenceWon.coerceAtLeast(0L))
            .putInt("ledger_pendingWildWins", ledger.pendingWildWins.coerceAtLeast(0))
            .putInt("ledger_pendingWildLosses", ledger.pendingWildLosses.coerceAtLeast(0))
            .putInt("ledger_pendingFeeds", ledger.pendingFeeds.coerceAtLeast(0))
            .putInt("ledger_totalWildWins", ledger.totalWildWins.coerceAtLeast(0))
            .putInt("ledger_petMoodPct", ledger.petMoodPct.coerceIn(0, 100))
            .putInt("ledger_petWildStamina", ledger.petWildStamina.coerceIn(0, WildPetBattleEngine.MAX_WILD_STAMINA))
            .putInt("ledger_stepTreats", ledger.stepTreatsAvailable.coerceIn(0, 20))
            .putLong("ledger_lastStaminaStepCount", ledger.lastStaminaStepCount.coerceAtLeast(0L))
            .putLong("ledger_lastPassiveRechargeMs", ledger.lastPassiveRechargeMs)
            .putString("ledger_lastWildBattleSummary", ledger.lastWildBattleSummary)
            .putLong("lastUpdatedMs", System.currentTimeMillis())
            .apply()
    }

    fun clearPendingPetLedgerAfterSync(context: Context) {
        val cur = readPetLedger(context)
        savePetLedger(
            context,
            cur.copy(
                pendingPetXp = 0L,
                pendingBondXp = 0L,
                pendingCareActions = 0,
                pendingCareDateKey = "",
                pendingBonusCareUsed = 0,
                pendingCoinsSpent = 0L,
                pendingOreWon = 0L,
                pendingEssenceWon = 0L,
                pendingWildWins = 0,
                pendingWildLosses = 0,
                pendingFeeds = 0
            )
        )
    }

    fun grantBonusWildStamina(context: Context, amount: Int) {
        if (amount <= 0) return
        val cur = readPetLedger(context)
        savePetLedger(
            context,
            cur.copy(
                petWildStamina = (cur.petWildStamina + amount).coerceAtMost(WildPetBattleEngine.MAX_WILD_STAMINA)
            )
        )
        SolstrideWidgetUpdater.updateAllWidgets(context)
    }

    fun resetPetLedgerForNewGame(context: Context) {
        savePetLedger(
            context,
            PetWidgetLedger(
                petMoodPct = 65,
                petWildStamina = 3,
                stepTreatsAvailable = 1,
                lastStaminaStepCount = 0L,
                lastPassiveRechargeMs = System.currentTimeMillis(),
                lastWildBattleSummary = "Adopt a pet in Sanctuary, then close app to train on this widget!"
            )
        )
    }

    fun isAppForegroundLocked(context: Context): Boolean {
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return p.getBoolean("isAppForegroundLocked", false)
    }

    fun setAppForegroundLocked(context: Context, locked: Boolean) {
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        p.edit()
            .putBoolean("isAppForegroundLocked", locked)
            .putLong("lastUpdatedMs", System.currentTimeMillis())
            .apply()
        SolstrideWidgetUpdater.updateAllWidgets(context)
    }

    /**
     * Converts walked steps (+1 stamina / 250 steps) and elapsed time
     * (+1 stamina / 30 min) into Pet Widget Wild Battle Stamina.
     * Feeding pets always requires spending Sol Coins.
     */
    fun rechargeWildStaminaAndTreats(context: Context, totalOrTodaySteps: Long) {
        val ledger = readPetLedger(context)
        val now = System.currentTimeMillis()
        var stamina = ledger.petWildStamina
        var nextStepBase = ledger.lastStaminaStepCount

        if (totalOrTodaySteps < nextStepBase) {
            // New calendar day reset
            nextStepBase = 0L
        }
        val stepDiff = (totalOrTodaySteps - nextStepBase).coerceAtLeast(0L)
        if (stepDiff >= WildPetBattleEngine.STEPS_PER_STAMINA) {
            val staminaGain = (stepDiff / WildPetBattleEngine.STEPS_PER_STAMINA).toInt()
            stamina = (stamina + staminaGain).coerceAtMost(WildPetBattleEngine.MAX_WILD_STAMINA)
            nextStepBase += staminaGain * WildPetBattleEngine.STEPS_PER_STAMINA
        }

        // Gradual passive recharge: +1 stamina every 30 minutes
        val elapsedMin = ((now - ledger.lastPassiveRechargeMs).coerceAtLeast(0L)) / 60_000L
        val passiveGain = (elapsedMin / 30L).toInt()
        val nextPassiveMs = if (passiveGain > 0) {
            stamina = (stamina + passiveGain).coerceAtMost(WildPetBattleEngine.MAX_WILD_STAMINA)
            now
        } else {
            ledger.lastPassiveRechargeMs
        }

        if (stamina != ledger.petWildStamina || ledger.stepTreatsAvailable != 0 ||
            nextStepBase != ledger.lastStaminaStepCount || nextPassiveMs != ledger.lastPassiveRechargeMs
        ) {
            savePetLedger(
                context,
                ledger.copy(
                    petWildStamina = stamina,
                    stepTreatsAvailable = 0,
                    lastStaminaStepCount = nextStepBase,
                    lastPassiveRechargeMs = nextPassiveMs
                )
            )
        }
    }

    /**
     * Direct Home Screen Widget FEED action — ONLY works while Solstride app is closed
     * and shares the exact same Daily Care limit (4/day) as the in-app Pet Sanctuary!
     * Always spends 5 Sol Coins to boost Mood, Bond XP & Pet XP.
     */
    fun performWidgetFeedAction(context: Context): Boolean {
        if (isAppForegroundLocked(context)) return false
        val snapshot = read(context)
        if (!snapshot.hasPet) return false

        val ledger = readPetLedger(context)
        val todayKey = currentCalendarDateKey()
        if (snapshot.isDailyCareExhausted(todayKey)) {
            savePetLedger(
                context,
                ledger.copy(lastWildBattleSummary = snapshot.dailyCareRestedMessage())
            )
            SolstrideWidgetUpdater.updateAllWidgets(context)
            return false
        }

        if (snapshot.solCoins < WildPetBattleEngine.FEED_SOL_COIN_COST) {
            savePetLedger(
                context,
                ledger.copy(
                    lastWildBattleSummary = "Need ${WildPetBattleEngine.FEED_SOL_COIN_COST} Sol Coins to feed ${snapshot.petName}!"
                )
            )
            SolstrideWidgetUpdater.updateAllWidgets(context)
            return false
        }

        val newMood = (ledger.petMoodPct + WildPetBattleEngine.FEED_MOOD_GAIN).coerceAtMost(100)
        val newPendingXp = ledger.pendingPetXp + WildPetBattleEngine.FEED_XP_GAIN
        val newPendingBondXp = ledger.pendingBondXp + WildPetBattleEngine.FEED_BOND_XP_GAIN
        val newPendingCoins = ledger.pendingCoinsSpent + WildPetBattleEngine.FEED_SOL_COIN_COST
        val useBonusCharge = snapshot.remainingDailyCare(todayKey) <= 0
        val samePetAndDay = ledger.pendingCareDateKey == todayKey &&
            (ledger.petId.isBlank() || snapshot.petId.isBlank() || ledger.petId == snapshot.petId)
        val nextPendingCare = if (useBonusCharge) {
            if (samePetAndDay) ledger.pendingCareActions else 0
        } else {
            (if (samePetAndDay) ledger.pendingCareActions else 0) + 1
        }
        val nextPendingCareDate = if (useBonusCharge) {
            ledger.pendingCareDateKey
        } else {
            todayKey
        }
        val nextPendingBonusUsed = ledger.pendingBonusCareUsed + (if (useBonusCharge) 1 else 0)
        val totalRemainingAfter = (snapshot.totalRemainingCare(todayKey) - 1).coerceAtLeast(0)

        val basePrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prevLevel = snapshot.petLevel
        val previewAfter = WildPetBattleEngine.computePreviewProgression(
            baseLevel = basePrefs.getInt("petLevel", 1),
            baseXp = basePrefs.getLong("petXp", 0L),
            addedXp = newPendingXp
        )
        val lvlUpTag = if (previewAfter.level > prevLevel) " ★ LV.${previewAfter.level}!" else ""
        val careSuffix = when {
            useBonusCharge && totalRemainingAfter <= 0 -> " • Used Bonus Care (0 left)"
            useBonusCharge -> " • Used Bonus Care ($totalRemainingAfter bonus left)"
            totalRemainingAfter <= 0 -> " • Limit (${snapshot.petDailyCareLimit}/${snapshot.petDailyCareLimit}), resets tomorrow!"
            else -> " • $totalRemainingAfter care left"
        }
        val summary = "🍖 Fed ${snapshot.petName} (-${WildPetBattleEngine.FEED_SOL_COIN_COST}c)! +${WildPetBattleEngine.FEED_XP_GAIN} XP$lvlUpTag$careSuffix"

        savePetLedger(
            context,
            ledger.copy(
                petId = snapshot.petId.ifBlank { ledger.petId },
                pendingPetXp = newPendingXp,
                pendingBondXp = newPendingBondXp,
                pendingCareActions = nextPendingCare,
                pendingCareDateKey = nextPendingCareDate,
                pendingBonusCareUsed = nextPendingBonusUsed,
                pendingCoinsSpent = newPendingCoins,
                pendingFeeds = ledger.pendingFeeds + 1,
                petMoodPct = newMood,
                stepTreatsAvailable = 0,
                lastWildBattleSummary = summary
            )
        )
        SolstrideWidgetUpdater.updateAllWidgets(context)
        return true
    }

    /**
     * Direct Home Screen Widget WILD FIGHT action — ONLY works while Solstride app is closed
     * and shares the exact same Daily Care limit (4/day) as the in-app Pet Sanctuary!
     * Spends 1 step-recharged Wild Stamina to battle a wild pet for Pet XP, Bond XP & Forge Materials.
     */
    fun performWidgetWildFightAction(context: Context): Boolean {
        if (isAppForegroundLocked(context)) return false
        val snapshot = read(context)
        if (!snapshot.hasPet) return false

        val ledger = readPetLedger(context)
        val todayKey = currentCalendarDateKey()
        if (snapshot.isDailyCareExhausted(todayKey)) {
            savePetLedger(
                context,
                ledger.copy(lastWildBattleSummary = snapshot.dailyCareRestedMessage())
            )
            SolstrideWidgetUpdater.updateAllWidgets(context)
            return false
        }

        if (ledger.petWildStamina <= 0) {
            savePetLedger(
                context,
                ledger.copy(
                    lastWildBattleSummary = "⚡ 0 Stamina! Walk 250 steps or wait 30m to recharge Wild Battles."
                )
            )
            SolstrideWidgetUpdater.updateAllWidgets(context)
            return false
        }

        val outcome = WildPetBattleEngine.resolveWildBattle(
            petName = snapshot.petName,
            petLevel = snapshot.petLevel,
            petElementLabel = snapshot.petElementLabel,
            weaponTier = snapshot.petFoodLevel,
            armorTier = snapshot.petGearLevel,
            moodPct = ledger.petMoodPct
        )

        val newPendingXp = ledger.pendingPetXp + outcome.xpGained
        val newPendingBondXp = ledger.pendingBondXp + WildPetBattleEngine.WILD_FIGHT_BOND_XP_GAIN
        val useBonusCharge = snapshot.remainingDailyCare(todayKey) <= 0
        val samePetAndDay = ledger.pendingCareDateKey == todayKey &&
            (ledger.petId.isBlank() || snapshot.petId.isBlank() || ledger.petId == snapshot.petId)
        val nextPendingCare = if (useBonusCharge) {
            if (samePetAndDay) ledger.pendingCareActions else 0
        } else {
            (if (samePetAndDay) ledger.pendingCareActions else 0) + 1
        }
        val nextPendingCareDate = if (useBonusCharge) {
            ledger.pendingCareDateKey
        } else {
            todayKey
        }
        val nextPendingBonusUsed = ledger.pendingBonusCareUsed + (if (useBonusCharge) 1 else 0)
        val totalRemainingAfter = (snapshot.totalRemainingCare(todayKey) - 1).coerceAtLeast(0)

        val basePrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prevLevel = snapshot.petLevel
        val previewAfter = WildPetBattleEngine.computePreviewProgression(
            baseLevel = basePrefs.getInt("petLevel", 1),
            baseXp = basePrefs.getLong("petXp", 0L),
            addedXp = newPendingXp
        )
        val lvlUpTag = if (previewAfter.level > prevLevel) " ★ LV.${previewAfter.level}!" else ""
        val careSuffix = when {
            useBonusCharge && totalRemainingAfter <= 0 -> " • Used Bonus Care (0 left)"
            useBonusCharge -> " • Bonus Care ($totalRemainingAfter left)"
            totalRemainingAfter <= 0 -> " • Limit (${snapshot.petDailyCareLimit}/${snapshot.petDailyCareLimit}), resets tomorrow!"
            else -> " • $totalRemainingAfter left"
        }

        savePetLedger(
            context,
            ledger.copy(
                petId = snapshot.petId.ifBlank { ledger.petId },
                pendingPetXp = newPendingXp,
                pendingBondXp = newPendingBondXp,
                pendingCareActions = nextPendingCare,
                pendingCareDateKey = nextPendingCareDate,
                pendingBonusCareUsed = nextPendingBonusUsed,
                pendingOreWon = ledger.pendingOreWon + outcome.oreWon,
                pendingEssenceWon = ledger.pendingEssenceWon + outcome.essenceWon,
                pendingWildWins = ledger.pendingWildWins + (if (outcome.won) 1 else 0),
                pendingWildLosses = ledger.pendingWildLosses + (if (outcome.won) 0 else 1),
                totalWildWins = ledger.totalWildWins + (if (outcome.won) 1 else 0),
                petWildStamina = (ledger.petWildStamina - 1).coerceAtLeast(0),
                petMoodPct = (ledger.petMoodPct - 12).coerceAtLeast(15),
                lastWildBattleSummary = "${outcome.summaryLine}$lvlUpTag$careSuffix"
            )
        )
        SolstrideWidgetUpdater.updateAllWidgets(context)
        return true
    }

    fun readAutoSyncConfig(context: Context): WidgetAutoSyncConfig {
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = p.getBoolean("widgetAutoSyncEnabled", true)
        val interval = p.getLong("widgetAutoSyncIntervalMin", 15L).coerceIn(15L, 60L)
        return WidgetAutoSyncConfig(enabled = enabled, intervalMinutes = interval)
    }

    fun saveAutoSyncConfig(context: Context, config: WidgetAutoSyncConfig) {
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        p.edit()
            .putBoolean("widgetAutoSyncEnabled", config.enabled)
            .putLong("widgetAutoSyncIntervalMin", config.intervalMinutes.coerceIn(15L, 60L))
            .apply()
    }

    fun updateStepTelemetryOnly(
        context: Context,
        todaySteps: Long,
        weeklySteps: Long,
        strideEnergy: Long,
        encounterProgressPct: Int,
        isWatchConnected: Boolean
    ) {
        rechargeWildStaminaAndTreats(context, todaySteps)
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prevToday = p.getLong("todaySteps", 0L)
        val prevWeekly = p.getLong("weeklySteps", 0L)
        p.edit()
            .putLong("todaySteps", todaySteps.coerceAtLeast(prevToday))
            .putLong("weeklySteps", weeklySteps.coerceAtLeast(prevWeekly))
            .putLong("strideEnergy", strideEnergy)
            .putInt("encounterProgressPct", encounterProgressPct.coerceIn(0, 100))
            .putBoolean("isWatchConnected", isWatchConnected)
            .putString("stepSourceLabel", if (isWatchConnected) "Watch Linked" else "Phone Live")
            .putLong("lastUpdatedMs", System.currentTimeMillis())
            .apply()
    }

    fun updateWatchConnectionOnly(
        context: Context,
        isWatchConnected: Boolean
    ) {
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = p.edit()
            .putBoolean("isWatchConnected", isWatchConnected)
            .putString("stepSourceLabel", if (isWatchConnected) "Watch Linked" else "Phone Live")
            .putLong("lastUpdatedMs", System.currentTimeMillis())
        if (!isWatchConnected) {
            editor.putBoolean("isHeroSleeping", false)
            editor.putString("sleepSourceLabel", "")
        }
        editor.apply()
    }

    fun updateHeroSleepingOnly(
        context: Context,
        isSleeping: Boolean,
        sleepSourceLabel: String
    ) {
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        p.edit()
            .putBoolean("isHeroSleeping", isSleeping)
            .putString("sleepSourceLabel", sleepSourceLabel)
            .putLong("lastUpdatedMs", System.currentTimeMillis())
            .apply()
    }

    fun buildFromLiveState(
        context: Context,
        profile: HeroProfile,
        stats: DerivedHeroStats,
        equippedPet: OwnedPet?,
        hardcoreState: HardcoreRpgState,
        todaySteps: Long,
        dailyStepGoal: Long,
        isWatchConnected: Boolean,
        inventory: List<InventoryItem> = emptyList(),
        unopenedChestCount: Int = 0,
        isHeroSleeping: Boolean = false,
        sleepSourceLabel: String = "",
        activeSleepMinutes: Long = 0L,
        lastCompletedSleepMinutes: Long = 0L,
        isAppForegroundLocked: Boolean = isAppForegroundLocked(context)
    ): WidgetSnapshotState {
        rechargeWildStaminaAndTreats(context, todaySteps)
        val ledger = readPetLedger(context)
        val realm = ProgressionEngine.realmSpec(profile.currentRealm.toInt())
        val todayKey = currentCalendarDateKey()
        val petType = equippedPet?.resolvedType()
        val petForm = equippedPet?.resolvedForm()
        val petBonus = PetCatalog.computeBonus(equippedPet)
        val weaponTier = equippedPet?.equippedWeaponId?.let { PetCatalog.weaponById(it)?.tier } ?: 0
        val armorTier = equippedPet?.equippedArmorId?.let { PetCatalog.armorById(it)?.tier } ?: 0
        val basePetLvl = equippedPet?.level?.toInt()?.coerceAtLeast(1) ?: 1
        val basePetXp = equippedPet?.xp?.coerceAtLeast(0L) ?: 0L
        val basePetXpNext = equippedPet?.xpToNextLevel?.coerceAtLeast(1L)
            ?: PetCatalog.xpRequiredForLevel(basePetLvl.toLong())
        val baseDailyCareUsed = if (equippedPet != null && equippedPet.lastCareDateKey == todayKey) {
            equippedPet.dailyCareActions.toInt().coerceIn(0, PetEvolutionCatalog.DAILY_CARE_LIMIT)
        } else {
            0
        }
        val baseCareDateKey = equippedPet?.lastCareDateKey.orEmpty()

        val equippedEquipment = inventory.filter { it.equipped && it.resolvedSlot().isEquipment }
        val equippedGearCount = equippedEquipment.size
        val equippedArmorSlots = equippedEquipment.count { it.resolvedSlot() != ItemSlot.WEAPON }
        val claimableQuests = runCatching {
            ProgressionEngine.evaluateQuests(profile, todayKey).count { it.canClaim }
        }.getOrDefault(0)
        val stepsTowardNext = (todaySteps.coerceAtLeast(0L) - ledger.lastStaminaStepCount).coerceAtLeast(0L) %
            WildPetBattleEngine.STEPS_PER_STAMINA
        val stepsToNextStamina = (WildPetBattleEngine.STEPS_PER_STAMINA - stepsTowardNext)
            .toInt()
            .coerceIn(1, WildPetBattleEngine.STEPS_PER_STAMINA.toInt())

        return WidgetSnapshotState(
            displayName = profile.displayName.ifBlank { "Solar Vanguard" },
            heroClassLabel = profile.resolvedClass().displayTitle,
            specializationTitle = stats.specialization.title,
            elementSymbol = stats.activeElement.symbol,
            elementLabel = stats.activeElement.label,
            level = profile.level.coerceAtLeast(1L),
            prestigeLevel = profile.prestigeLevel.coerceAtLeast(0L),
            currentHp = profile.currentHp.coerceIn(0L, stats.computedMaxHp.toLong()),
            maxHp = stats.computedMaxHp.toLong().coerceAtLeast(1L),
            strideEnergy = profile.strideEnergy.coerceIn(0L, stats.computedMaxEnergy.toLong()),
            maxStrideEnergy = stats.computedMaxEnergy.toLong().coerceAtLeast(1L),
            attack = stats.totalAttack,
            defense = stats.totalDefense,
            critChancePct = stats.critChancePercent.coerceIn(0, 100),
            heroPower = stats.computedHeroPower,
            solCoins = profile.solCoins.coerceAtLeast(0L),
            sunshards = profile.sunshards.coerceAtLeast(0L),
            realmNumber = profile.currentRealm.coerceIn(1L, ProgressionEngine.MAX_REALM),
            realmStage = profile.realmStage.coerceIn(1L, 10L),
            realmName = realm.name,
            spireFloor = profile.currentSpireFloor.coerceAtLeast(1L),
            bossesDefeated = profile.bossesDefeated.coerceAtLeast(0L),
            encounterProgressPct = profile.encounterProgress.toInt().coerceIn(0, 100),
            unopenedChestCount = unopenedChestCount.coerceAtLeast(0),
            claimableQuestCount = claimableQuests,
            unspentMasteryPoints = profile.unspentPoints.coerceAtLeast(0L),
            equippedGearCount = equippedGearCount,
            equippedArmorSlots = equippedArmorSlots,
            woundStacks = hardcoreState.battleWoundStacks.coerceIn(0, 3),
            woundPenaltyPct = stats.woundPenaltyPct,
            lowestDurabilityPct = stats.lowestGearDurabilityPct,
            shatteredGearCount = stats.shatteredGearCount,
            hardcoreOathEnabled = hardcoreState.hardcoreOathEnabled,
            isHeroSleeping = isHeroSleeping,
            sleepSourceLabel = sleepSourceLabel,
            activeSleepMinutes = activeSleepMinutes.coerceAtLeast(0L),
            lastCompletedSleepMinutes = lastCompletedSleepMinutes.coerceAtLeast(0L),
            isAppForegroundLocked = isAppForegroundLocked,
            hasPet = equippedPet != null && petType != null,
            petId = equippedPet?.id?.ifBlank { petType?.name.orEmpty() } ?: "",
            petName = petForm?.formName ?: petType?.displayName ?: "No Companion",
            petTitle = petForm?.title ?: petType?.title ?: "Adopt in Sanctuary",
            petRarityLabel = petType?.rarity?.label ?: "",
            petLevel = basePetLvl,
            petStage = equippedPet?.resolvedStage() ?: 1,
            petBondXp = equippedPet?.bondXp?.coerceAtLeast(0L) ?: 0L,
            petDailyCareUsed = baseDailyCareUsed,
            petDailyCareLimit = PetEvolutionCatalog.DAILY_CARE_LIMIT,
            petLastCareDateKey = baseCareDateKey,
            petBonusCareCharges = hardcoreState.bonusPetCareCharges.coerceIn(0, 99),
            petXp = basePetXp,
            petXpToNext = basePetXpNext,
            petElementSymbol = petType?.elementalAffinity?.symbol ?: "🐾",
            petElementLabel = petType?.elementalAffinity?.label ?: "None",
            petFoodLevel = weaponTier,
            petGearLevel = armorTier,
            petActiveSkillName = petType?.activeSkillName ?: "Adopt a pet for Combat Combos",
            petBonusSummary = if (equippedPet != null) petBonus.summaryText
            else "Equip a companion for ATK, DEF & Step Energy bonuses.",
            petMoodPct = ledger.petMoodPct,
            petWildStamina = ledger.petWildStamina,
            petStepTreats = ledger.stepTreatsAvailable,
            petTotalWildWins = ledger.totalWildWins,
            petLastBattleSummary = ledger.lastWildBattleSummary,
            todaySteps = todaySteps.coerceAtLeast(0L),
            dailyStepGoal = dailyStepGoal.coerceAtLeast(1000L),
            weeklySteps = profile.weeklySteps.coerceAtLeast(0L),
            stepsToNextStamina = stepsToNextStamina,
            isWatchConnected = isWatchConnected,
            stepSourceLabel = if (isWatchConnected) "Watch Linked" else "Phone Live",
            sunsteelOre = hardcoreState.sunsteelOre,
            aetherHerbs = hardcoreState.aetherHerbs,
            elementalEssence = hardcoreState.elementalEssence,
            hasActiveExpedition = profile.hasActiveExpedition(),
            expeditionEndMs = profile.activeExpeditionEndMs,
            expeditionDurationMin = profile.activeExpeditionDurationMin,
            lastUpdatedMs = System.currentTimeMillis()
        )
    }
}

object SolstrideWidgetUpdater {
    const val ACTION_REFRESH_SOLSTRIDE_WIDGETS = "com.example.ACTION_REFRESH_SOLSTRIDE_WIDGETS"
    const val ACTION_PET_WIDGET_FEED = "com.example.ACTION_PET_WIDGET_FEED"
    const val ACTION_PET_WIDGET_WILD_FIGHT = "com.example.ACTION_PET_WIDGET_WILD_FIGHT"
    const val EXTRA_WIDGET_TARGET_TAB = "extra_widget_target_tab"

    const val TAB_EXPEDITION = "expedition"
    const val TAB_BATTLE = "battle"
    const val TAB_PETS = "pets"
    const val TAB_INVENTORY = "inventory"

    fun activeWidgetCount(context: Context): Int {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        return SolstrideWidgetType.entries.sumOf { widgetType ->
            runCatching {
                manager.getAppWidgetIds(ComponentName(appContext, widgetType.providerClass)).size
            }.getOrDefault(0)
        }
    }

    fun hasAnyActiveWidgets(context: Context): Boolean = activeWidgetCount(context) > 0

    fun updateAllWidgets(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val snapshot = SolstrideWidgetSnapshotStore.read(appContext)

        SolstrideWidgetType.entries.forEach { widgetType ->
            val component = ComponentName(appContext, widgetType.providerClass)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                ids.forEach { appWidgetId ->
                    val views = when (widgetType) {
                        SolstrideWidgetType.UNIFIED_COMMAND ->
                            SolstrideCommandWidgetProvider.buildRemoteViews(appContext, snapshot)
                        SolstrideWidgetType.HERO_STATS ->
                            SolstrideHeroWidgetProvider.buildRemoteViews(appContext, snapshot)
                        SolstrideWidgetType.COMPANION_PET ->
                            SolstridePetWidgetProvider.buildRemoteViews(appContext, snapshot)
                        SolstrideWidgetType.STEPS_EXPEDITION ->
                            SolstrideStepWidgetProvider.buildRemoteViews(appContext, snapshot)
                    }
                    manager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }

    fun touchRefreshTimestampAndUpdate(context: Context) {
        val p = context.applicationContext.getSharedPreferences("solstride_widget_snapshot_v1", Context.MODE_PRIVATE)
        p.edit().putLong("lastUpdatedMs", System.currentTimeMillis()).apply()
        updateAllWidgets(context)
    }

    fun requestPinWidget(context: Context, widgetType: SolstrideWidgetType): WidgetPinResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return WidgetPinResult.NOT_SUPPORTED
        }
        val appWidgetManager = AppWidgetManager.getInstance(context)
        if (!appWidgetManager.isRequestPinAppWidgetSupported) {
            return WidgetPinResult.NOT_SUPPORTED
        }
        val provider = ComponentName(context, widgetType.providerClass)
        val accepted = runCatching {
            appWidgetManager.requestPinAppWidget(provider, null, null)
        }.getOrDefault(false)
        return if (accepted) WidgetPinResult.PIN_REQUESTED else WidgetPinResult.NOT_SUPPORTED
    }
}
