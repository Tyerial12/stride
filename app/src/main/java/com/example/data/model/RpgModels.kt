package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

const val DEFAULT_SKILL_RANKS = "0,0,0,0,0,0,0,0,0"

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class HeroClass(val displayTitle: String, val perkSummary: String) {
    VANGUARD("Vanguard", "+15% Defense & Guard Counter"),
    STRIDER("Strider", "+20% Step Energy Charge Rate"),
    SUNBLADE("Sunblade", "+18% Solar Skill Critical Damage");

    companion object {
        fun fromRaw(raw: String?): HeroClass =
            entries.firstOrNull { it.name == raw } ?: VANGUARD
    }
}

enum class ItemSlot(val label: String) {
    WEAPON("Weapon"),
    ARMOR("Armor"),
    RELIC("Relic"),
    CONSUMABLE("Elixir");

    companion object {
        fun fromRaw(raw: String?): ItemSlot =
            entries.firstOrNull { it.name == raw } ?: WEAPON
    }
}

enum class ItemRarity(val label: String, val salvageValue: Int, val upgradeMultiplier: Int) {
    COMMON("Common", 15, 25),
    RARE("Rare", 35, 45),
    EPIC("Epic", 75, 80),
    SOLAR("Solar Relic", 150, 120),
    ULTRA_RARE("Ultra Rare", 320, 180);

    companion object {
        fun fromRaw(raw: String?): ItemRarity =
            entries.firstOrNull { it.name == raw } ?: COMMON
    }
}

enum class GearPerkType(val displayName: String, private val formatPattern: String) {
    NONE("Standard", ""),
    CRIT_CHANCE("Solar Crit", "+%d%% Critical Strike Chance"),
    ARMOR_PIERCE("Armor Pierce", "Ignores %d%% Enemy Defense"),
    LIFESTEAL("Sun Siphon", "Heals %d%% of Attack Damage"),
    SOLAR_SURGE("Solar Surge", "+%d%% Solar Skill Damage"),
    DAMAGE_REDUCTION("Obsidian Plating", "-%d%% Incoming Enemy Damage"),
    THORNS("Sunfire Thorns", "Reflects %d%% of Enemy Strikes"),
    EXPEDITION_WARD("Wayfarer Ward", "+%d%% Expedition Survival Odds"),
    TURN_REGEN("Dawn Mending", "Restores +%d HP Each Turn");

    fun formatSummary(value: Int): String =
        if (this == NONE || value <= 0) "" else String.format(Locale.US, formatPattern, value)

    fun shortBadge(value: Int): String = when (this) {
        NONE -> ""
        CRIT_CHANCE -> "+$value% CRIT"
        ARMOR_PIERCE -> "$value% PIERCE"
        LIFESTEAL -> "$value% LEECH"
        SOLAR_SURGE -> "+$value% SKILL"
        DAMAGE_REDUCTION -> "-$value% DMG"
        THORNS -> "$value% THORNS"
        EXPEDITION_WARD -> "+$value% SURVIVAL"
        TURN_REGEN -> "+$value HP/T"
    }

    companion object {
        fun fromRaw(raw: String?): GearPerkType =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

enum class LootChestTier(
    val title: String,
    val subtitle: String,
    val baseUltraRarePct: Int
) {
    HIGHLAND("Highland Chest", "Skirmish & Quest Spoils", 12),
    BOSS_VAULT("Boss Vault Chest", "Guardian Hoard", 28),
    EXPEDITION("Expedition Relic Chest", "Timed Journey Hoard", 12),
    SPIRE_SOVEREIGN("Spire Sovereign Chest", "Endgame Relic Vault", 36);

    companion object {
        fun fromRaw(raw: String?): LootChestTier =
            entries.firstOrNull { it.name == raw } ?: HIGHLAND
    }
}

enum class StepSource(val label: String) {
    PHONE("Phone Sensor"),
    GALAXY_WATCH("Galaxy Watch")
}

@Keep
@IgnoreExtraProperties
data class HeroProfile(
    val userId: String = "",
    val displayName: String = "Solar Strider",
    val heroClass: String = HeroClass.VANGUARD.name,
    val level: Long = 1L,
    val xp: Long = 0L,
    val xpToNextLevel: Long = 120L,
    val might: Long = 5L,
    val vigor: Long = 5L,
    val endurance: Long = 5L,
    val unspentPoints: Long = 0L,
    val currentHp: Long = 120L,
    val maxHp: Long = 120L,
    val strideEnergy: Long = 35L,
    val maxStrideEnergy: Long = 100L,
    val encounterProgress: Long = 25L,
    val sunshards: Long = 60L,
    val solCoins: Long = 0L,
    val totalSteps: Long = 0L,
    val weeklySteps: Long = 0L,
    val weekKey: String = currentIsoWeekKey(),
    val bossesDefeated: Long = 0L,
    val trueBossWins: Long = 0L,
    val heroPower: Long = 145L,
    val equippedPetId: String = "",
    val expeditionsCompleted: Long = 0L,
    val expeditionsFailed: Long = 0L,
    val activeExpeditionStartMs: Long = 0L,
    val activeExpeditionDurationMin: Long = 0L,
    val activeExpeditionEndMs: Long = 0L,
    val currentRealm: Long = 1L,
    val realmStage: Long = 1L,
    val highestRealmCleared: Long = 0L,
    val currentSpireFloor: Long = 1L,
    val highestSpireFloor: Long = 0L,
    val prestigeLevel: Long = 0L,
    val skillRanks: String = DEFAULT_SKILL_RANKS,
    val dailyQuestDateKey: String = "",
    val dailyBattlesWon: Long = 0L,
    val dailyExpeditionsDone: Long = 0L,
    val dailyChestsOpened: Long = 0L,
    val claimedQuestIds: List<String> = emptyList(),
    val chestsOpenedTotal: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedClass(): HeroClass = HeroClass.fromRaw(heroClass)
    fun hasActiveExpedition(): Boolean = activeExpeditionEndMs > 0L

    fun parsedSkillRanks(): List<Int> {
        val parts = skillRanks.split(",")
        if (parts.size != 9) return List(9) { 0 }
        return parts.map { (it.trim().toIntOrNull() ?: 0).coerceIn(0, 5) }
    }

    fun skillRank(nodeIndex: Int): Int =
        parsedSkillRanks().getOrElse(nodeIndex) { 0 }

    fun totalSkillPointsSpent(): Int = parsedSkillRanks().sum()
}

@Keep
@IgnoreExtraProperties
data class InventoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val slot: String = ItemSlot.WEAPON.name,
    val rarity: String = ItemRarity.COMMON.name,
    val attackBonus: Long = 0L,
    val defenseBonus: Long = 0L,
    val hpBonus: Long = 0L,
    val energyBonus: Long = 0L,
    val healAmount: Long = 0L,
    val upgradeLevel: Long = 0L,
    val quantity: Long = 1L,
    val equipped: Boolean = false,
    val description: String = "",
    val perkType: String = GearPerkType.NONE.name,
    val perkValue: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedSlot(): ItemSlot = ItemSlot.fromRaw(slot)
    fun resolvedRarity(): ItemRarity = ItemRarity.fromRaw(rarity)

    /**
     * Resolves this item's passive combat/expedition perk. Legacy items stored before perks
     * were introduced automatically receive a balanced slot+rarity perk so existing gear is useful.
     */
    fun resolvedPerk(): Pair<GearPerkType, Int> {
        val slotEnum = resolvedSlot()
        if (slotEnum == ItemSlot.CONSUMABLE) return GearPerkType.NONE to 0
        val parsed = GearPerkType.fromRaw(perkType)
        if (parsed != GearPerkType.NONE && perkValue > 0L) {
            return parsed to perkValue.toInt().coerceIn(1, 100)
        }
        val rarityTier = resolvedRarity().ordinal + 1 // 1..5
        val forgeBonus = upgradeLevel.toInt().coerceAtMost(15)
        return when (slotEnum) {
            ItemSlot.WEAPON -> GearPerkType.CRIT_CHANCE to (4 + rarityTier * 3 + forgeBonus).coerceAtMost(45)
            ItemSlot.ARMOR -> GearPerkType.DAMAGE_REDUCTION to (3 + rarityTier * 3 + (forgeBonus / 2)).coerceAtMost(35)
            ItemSlot.RELIC -> GearPerkType.EXPEDITION_WARD to (4 + rarityTier * 3 + (forgeBonus / 2)).coerceAtMost(35)
            ItemSlot.CONSUMABLE -> GearPerkType.NONE to 0
        }
    }

    /**
     * Composite gear power score used to evaluate Expedition Survival Odds and loadouts.
     */
    fun gearPowerScore(): Int {
        if (resolvedSlot() == ItemSlot.CONSUMABLE) return 0
        val rarityWeight = (resolvedRarity().ordinal + 1) * 14
        val statWeight = (attackBonus.toInt() * 3) + (defenseBonus.toInt() * 3) +
            (hpBonus.toInt() / 2) + (energyBonus.toInt() * 2)
        val (_, pVal) = resolvedPerk()
        return (rarityWeight + statWeight + (pVal * 2) + (upgradeLevel.toInt() * 6)).coerceAtLeast(10)
    }

    fun upgradeCost(): Long {
        val base = resolvedRarity().upgradeMultiplier.toLong()
        return base + (upgradeLevel * 20L)
    }

    fun salvageSunshards(): Long {
        val base = resolvedRarity().salvageValue.toLong()
        return base + (upgradeLevel * 10L)
    }
}

@Keep
@IgnoreExtraProperties
data class LootChest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val chestTier: String = LootChestTier.HIGHLAND.name,
    val sourceLabel: String = "Highland Spoils",
    val heroLevelSnapshot: Long = 1L,
    val durationMinutes: Long = 0L,
    val ultraRareChancePct: Long = 12L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedTier(): LootChestTier = LootChestTier.fromRaw(chestTier)
}

@Keep
@IgnoreExtraProperties
data class LeaderboardEntry(
    val userId: String = "",
    val displayName: String = "Solar Strider",
    val heroClass: String = HeroClass.VANGUARD.name,
    val heroLevel: Long = 1L,
    val heroPower: Long = 145L,
    val weeklySteps: Long = 0L,
    val weekKey: String = currentIsoWeekKey(),
    val bossesDefeated: Long = 0L,
    val prestigeLevel: Long = 0L,
    val highestSpireFloor: Long = 0L,
    val publicVisible: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedClass(): HeroClass = HeroClass.fromRaw(heroClass)
}

data class GearSetBonus(
    val activeTier: ItemRarity?,
    val label: String,
    val attackBonusPct: Float,
    val defenseBonusPct: Float,
    val survivalBonusPct: Int,
    val critBonusPct: Int,
    val summary: String
) {
    companion object {
        val NONE = GearSetBonus(
            activeTier = null,
            label = "No Set Resonance",
            attackBonusPct = 0f,
            defenseBonusPct = 0f,
            survivalBonusPct = 0,
            critBonusPct = 0,
            summary = "Equip Weapon + Armor + Relic of Rare+ to awaken Set Resonance"
        )
    }
}

data class DerivedHeroStats(
    val totalAttack: Int,
    val totalDefense: Int,
    val computedMaxHp: Int,
    val computedMaxEnergy: Int,
    val computedHeroPower: Int,
    val gearPowerScore: Int = 0,
    val petBonusSummary: String = "No companion equipped",
    val petSkillDamageMultiplier: Float = 1.0f,
    val petStepEnergyMultiplier: Float = 1.0f,
    val petXpMultiplier: Float = 1.0f,
    val petPostBattleHealPercent: Float = 0f,
    val critChancePercent: Int = 5,
    val armorPiercePercent: Int = 0,
    val lifestealPercent: Int = 0,
    val solarSurgeBonusPct: Int = 0,
    val damageReductionPercent: Int = 0,
    val thornsPercent: Int = 0,
    val turnRegenHp: Int = 0,
    val expeditionWardPercent: Int = 0,
    val setBonus: GearSetBonus = GearSetBonus.NONE,
    val prestigeStatMultiplier: Float = 1.0f,
    val totalXpMultiplier: Float = 1.0f,
    val totalSunshardMultiplier: Float = 1.0f,
    val stepEnergyMultiplier: Float = 1.0f,
    val activePerkSummaries: List<String> = emptyList()
)

fun computeGearSetBonus(equippedGear: List<InventoryItem>): GearSetBonus {
    val weapon = equippedGear.firstOrNull { it.resolvedSlot() == ItemSlot.WEAPON }
    val armor = equippedGear.firstOrNull { it.resolvedSlot() == ItemSlot.ARMOR }
    val relic = equippedGear.firstOrNull { it.resolvedSlot() == ItemSlot.RELIC }
    if (weapon == null || armor == null || relic == null) return GearSetBonus.NONE

    val lowestTier = listOf(weapon, armor, relic).minOf { it.resolvedRarity().ordinal }
    return when (ItemRarity.entries[lowestTier]) {
        ItemRarity.COMMON -> GearSetBonus(
            activeTier = ItemRarity.COMMON,
            label = "Wayfarer Kit (3/3)",
            attackBonusPct = 0.05f,
            defenseBonusPct = 0.05f,
            survivalBonusPct = 4,
            critBonusPct = 2,
            summary = "+5% ATK/DEF, +4% Expedition Survival"
        )
        ItemRarity.RARE -> GearSetBonus(
            activeTier = ItemRarity.RARE,
            label = "Sunforged Set (3/3)",
            attackBonusPct = 0.10f,
            defenseBonusPct = 0.10f,
            survivalBonusPct = 8,
            critBonusPct = 4,
            summary = "+10% ATK/DEF, +4% Crit, +8% Survival"
        )
        ItemRarity.EPIC -> GearSetBonus(
            activeTier = ItemRarity.EPIC,
            label = "Obsidian Resonance (3/3)",
            attackBonusPct = 0.16f,
            defenseBonusPct = 0.16f,
            survivalBonusPct = 12,
            critBonusPct = 7,
            summary = "+16% ATK/DEF, +7% Crit, +12% Survival"
        )
        ItemRarity.SOLAR -> GearSetBonus(
            activeTier = ItemRarity.SOLAR,
            label = "Solar Sovereign Set (3/3)",
            attackBonusPct = 0.24f,
            defenseBonusPct = 0.24f,
            survivalBonusPct = 18,
            critBonusPct = 10,
            summary = "+24% ATK/DEF, +10% Crit, +18% Survival"
        )
        ItemRarity.ULTRA_RARE -> GearSetBonus(
            activeTier = ItemRarity.ULTRA_RARE,
            label = "Astral Zenith Ascension (3/3)",
            attackBonusPct = 0.35f,
            defenseBonusPct = 0.35f,
            survivalBonusPct = 25,
            critBonusPct = 15,
            summary = "+35% ATK/DEF, +15% Crit, +25% Survival"
        )
    }
}

fun computeDerivedStats(
    profile: HeroProfile,
    inventory: List<InventoryItem>,
    equippedPet: OwnedPet? = null
): DerivedHeroStats {
    val equippedGear = inventory.filter { it.equipped && it.resolvedSlot() != ItemSlot.CONSUMABLE }
    val gearAtk = equippedGear.sumOf { it.attackBonus }.toInt()
    val gearDef = equippedGear.sumOf { it.defenseBonus }.toInt()
    val gearHp = equippedGear.sumOf { it.hpBonus }.toInt()
    val gearEnergy = equippedGear.sumOf { it.energyBonus }.toInt()
    val gearPowerScore = equippedGear.sumOf { it.gearPowerScore() }
    val setBonus = computeGearSetBonus(equippedGear)

    val petBonus = PetCatalog.computeBonus(equippedPet)
    val skills = profile.parsedSkillRanks()

    // Skill Tree ranks (indices 0..8)
    val sunfireRank = skills[0]       // +4% ATK, +2% Crit
    val piercingRank = skills[1]      // +5% Pierce, +4% Solar Skill DMG
    val bloodSolsticeRank = skills[2] // +3% Lifesteal
    val obsidianShellRank = skills[3] // +5% DEF, +3% DMG Reduction
    val solarThornsRank = skills[4]   // +4% Thorns, +15 Max HP
    val dawnRecoveryRank = skills[5]  // +4 HP/turn
    val pathfinderRank = skills[6]    // +4% Expedition Survival
    val strideConduitRank = skills[7] // +8% Step Energy Rate, +6 Max Energy
    val relicHunterRank = skills[8]   // +6% XP, +5% Sunshards

    // Prestige Rebirth permanent multipliers
    val prestigeRank = profile.prestigeLevel.toInt().coerceIn(0, 99)
    val prestigeStatMult = 1.0f + (prestigeRank * 0.15f)
    val prestigeXpMult = 1.0f + (prestigeRank * 0.10f)
    val prestigeSurvivalBonus = prestigeRank * 5

    // Aggregate equipped gear perks
    var critChance = 5 + setBonus.critBonusPct + (sunfireRank * 2)
    var armorPierce = piercingRank * 5
    var lifesteal = bloodSolsticeRank * 3
    var solarSurge = piercingRank * 4
    var dmgReduction = obsidianShellRank * 3
    var thorns = solarThornsRank * 4
    var turnRegen = dawnRecoveryRank * 4
    var expWard = setBonus.survivalBonusPct + (pathfinderRank * 4) + prestigeSurvivalBonus

    val perkSummaries = mutableListOf<String>()
    equippedGear.forEach { item ->
        val (perk, value) = item.resolvedPerk()
        when (perk) {
            GearPerkType.CRIT_CHANCE -> critChance += value
            GearPerkType.ARMOR_PIERCE -> armorPierce += value
            GearPerkType.LIFESTEAL -> lifesteal += value
            GearPerkType.SOLAR_SURGE -> solarSurge += value
            GearPerkType.DAMAGE_REDUCTION -> dmgReduction += value
            GearPerkType.THORNS -> thorns += value
            GearPerkType.EXPEDITION_WARD -> expWard += value
            GearPerkType.TURN_REGEN -> turnRegen += value
            GearPerkType.NONE -> Unit
        }
        val badge = perk.shortBadge(value)
        if (badge.isNotBlank()) perkSummaries.add("${item.resolvedSlot().label}: $badge")
    }

    val baseAtk = 10 + (profile.might.toInt() * 3) + (profile.level.toInt() * 2)
    val baseDef = 5 + (profile.vigor.toInt() * 2) + profile.level.toInt()

    val preMultAtk = baseAtk + gearAtk + petBonus.gearAttackBonus
    val totalAtk = (preMultAtk *
        (1f + petBonus.attackPercentBonus + setBonus.attackBonusPct + (sunfireRank * 0.04f)) *
        prestigeStatMult).roundToInt()

    val rawDef = baseDef + gearDef + petBonus.gearDefenseBonus
    val classDef = if (profile.resolvedClass() == HeroClass.VANGUARD) {
        (rawDef * 1.15f).roundToInt()
    } else {
        rawDef
    }
    val totalDef = (classDef *
        (1f + petBonus.defensePercentBonus + setBonus.defenseBonusPct + (obsidianShellRank * 0.05f)) *
        prestigeStatMult).roundToInt()

    val rawHp = 95 + (profile.vigor.toInt() * 10) + (profile.level.toInt() * 12) +
        gearHp + petBonus.gearHpBonus + (solarThornsRank * 15)
    val maxHp = (rawHp * (1f + petBonus.maxHpPercentBonus) * prestigeStatMult).roundToInt()

    val maxEnergy = 80 + (profile.endurance.toInt() * 5) + gearEnergy +
        petBonus.flatMaxEnergyBonus + (strideConduitRank * 6)

    val perkPowerBonus = (critChance * 2) + (armorPierce * 2) + (dmgReduction * 3) +
        (lifesteal * 3) + (thorns * 2) + (turnRegen * 2) + (expWard * 2)
    val heroPower = (profile.level.toInt() * 25) + (totalAtk * 4) + (totalDef * 3) +
        (maxHp / 4) + (maxEnergy / 2) + perkPowerBonus + (prestigeRank * 150)

    val totalXpMult = (petBonus.xpGainMultiplier * prestigeXpMult * (1f + relicHunterRank * 0.06f))
    val totalShardMult = 1f + (relicHunterRank * 0.05f) + (prestigeRank * 0.08f)
    val totalStepEnergyMult = petBonus.stepEnergyChargeMultiplier * (1f + strideConduitRank * 0.08f)

    return DerivedHeroStats(
        totalAttack = totalAtk.coerceAtLeast(1),
        totalDefense = totalDef.coerceAtLeast(1),
        computedMaxHp = maxHp.coerceIn(50, 999999),
        computedMaxEnergy = maxEnergy.coerceIn(50, 9999),
        computedHeroPower = heroPower.coerceIn(1, 10_000_000),
        gearPowerScore = gearPowerScore,
        petBonusSummary = petBonus.summaryText,
        petSkillDamageMultiplier = petBonus.solarSkillDamageMultiplier,
        petStepEnergyMultiplier = petBonus.stepEnergyChargeMultiplier,
        petXpMultiplier = petBonus.xpGainMultiplier,
        petPostBattleHealPercent = petBonus.postBattleHealPercent,
        critChancePercent = critChance.coerceIn(5, 80),
        armorPiercePercent = armorPierce.coerceIn(0, 75),
        lifestealPercent = lifesteal.coerceIn(0, 45),
        solarSurgeBonusPct = solarSurge.coerceIn(0, 120),
        damageReductionPercent = dmgReduction.coerceIn(0, 55),
        thornsPercent = thorns.coerceIn(0, 60),
        turnRegenHp = turnRegen.coerceIn(0, 120),
        expeditionWardPercent = expWard.coerceIn(0, 65),
        setBonus = setBonus,
        prestigeStatMultiplier = prestigeStatMult,
        totalXpMultiplier = totalXpMult,
        totalSunshardMultiplier = totalShardMult,
        stepEnergyMultiplier = totalStepEnergyMult,
        activePerkSummaries = perkSummaries
    )
}

fun currentIsoWeekKey(): String {
    val cal = Calendar.getInstance()
    cal.firstDayOfWeek = Calendar.MONDAY
    cal.minimalDaysInFirstWeek = 4
    val year = cal.get(Calendar.YEAR)
    val week = cal.get(Calendar.WEEK_OF_YEAR)
    return String.format(Locale.US, "%04d-W%02d", year, week)
}

fun currentCalendarDateKey(): String {
    val cal = Calendar.getInstance()
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1
    val day = cal.get(Calendar.DAY_OF_MONTH)
    return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
}
