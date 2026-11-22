package com.example.domain

import com.example.data.model.DerivedHeroStats
import com.example.data.model.ElementalAffinity
import com.example.data.model.GearPerkType
import com.example.data.model.HeroProfile
import com.example.data.model.InventoryItem
import com.example.data.model.ItemRarity
import com.example.data.model.ItemSlot
import com.example.data.model.LootChest
import com.example.data.model.LootChestTier
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

data class ExpeditionPreset(
    val id: String,
    val title: String,
    val durationMinutes: Int,
    val shortLabel: String,
    val subtitle: String
)

enum class ExpeditionRiskTier(val label: String) {
    SAFE("Safe"),
    GUARDED("Guarded"),
    RISKY("Risky"),
    PERILOUS("Perilous")
}

data class ExpeditionSolCoinReward(
    val elapsedMinutes: Int,
    val plannedDurationMinutes: Int,
    val timeSpentPercent: Int,
    val maxDurationPercent: Int,
    val fullSolCoins: Long,
    val earnedSolCoins: Long
)

data class ExpeditionRiskForecast(
    val durationMinutes: Int,
    val survivalChancePct: Int,
    val recommendedGearPower: Int,
    val currentGearPower: Int,
    val ultraRareChancePct: Int,
    val riskTier: ExpeditionRiskTier,
    val expectedSolCoins: Long = 1L,
    val durationTimePercent: Int = 1
)

data class ExpeditionOutcomeReport(
    val succeeded: Boolean,
    val durationMinutes: Int,
    val survivalChancePct: Int,
    val xpEarned: Long,
    val sunshardsEarned: Long,
    val hpLost: Long,
    val awardedChest: LootChest?,
    val summaryMessage: String,
    val solCoinsEarned: Long = 0L,
    val timeSpentPercent: Int = 100
)

data class ChestOpenResult(
    val chest: LootChest = LootChest(),
    val item: InventoryItem,
    val wasUltraRare: Boolean,
    val rolledRarity: ItemRarity = item.resolvedRarity()
)

enum class ForgeBlueprintTier(
    val tierNumber: Int,
    val label: String,
    val minHeroLevel: Int,
    val baseRarity: ItemRarity,
    val oreCost: Long,
    val herbCost: Long,
    val essenceCost: Long,
    val sunshardCost: Long,
    val powerScalar: Float
) {
    BRONZE_SURVIVOR(
        tierNumber = 1,
        label = "Tier I • Ironhide",
        minHeroLevel = 1,
        baseRarity = ItemRarity.COMMON,
        oreCost = 10L,
        herbCost = 5L,
        essenceCost = 2L,
        sunshardCost = 20L,
        powerScalar = 1.65f
    ),
    SUNSTEEL_VANGUARD(
        tierNumber = 2,
        label = "Tier II • Sunsteel",
        minHeroLevel = 3,
        baseRarity = ItemRarity.RARE,
        oreCost = 22L,
        herbCost = 10L,
        essenceCost = 6L,
        sunshardCost = 55L,
        powerScalar = 2.65f
    ),
    OBSIDIAN_MASTERWORK(
        tierNumber = 3,
        label = "Tier III • Obsidian",
        minHeroLevel = 6,
        baseRarity = ItemRarity.EPIC,
        oreCost = 40L,
        herbCost = 18L,
        essenceCost = 14L,
        sunshardCost = 120L,
        powerScalar = 3.85f
    ),
    ASTRAL_SOVEREIGN(
        tierNumber = 4,
        label = "Tier IV • Astral",
        minHeroLevel = 10,
        baseRarity = ItemRarity.SOLAR,
        oreCost = 70L,
        herbCost = 30L,
        essenceCost = 26L,
        sunshardCost = 240L,
        powerScalar = 5.35f
    )
}

object ExpeditionEngine {

    const val MIN_DURATION_MINUTES = 5
    const val MAX_DURATION_MINUTES = 23 * 60 // 1380 minutes (23 hours)
    const val MAX_EXPEDITION_SOL_COINS = 100L // 100% of max 23h expedition time = 100 Sol Coins
    const val MIN_EARLY_STOP_COIN_MINUTES = 5
    const val BOSS_VICTORY_SOL_COINS = 12L
    const val CAMP_HEAL_SOL_COIN_COST = 5L
    const val PERK_REROLL_SOL_COIN_COST = 8L
    const val PERK_REROLL_SUNSHARD_COST = 40L
    const val RARITY_ASCEND_SOL_COIN_COST = 18L
    const val RARITY_ASCEND_ORE_COST = 15L
    const val RARITY_ASCEND_ESSENCE_COST = 8L
    const val STABILIZER_SEAL_SOL_COIN_COST = 10L
    const val FORGE_STABILIZER_SOL_COIN_COST = STABILIZER_SEAL_SOL_COIN_COST
    const val REPAIR_ITEM_SOL_COIN_COST = 4L
    const val REPAIR_ITEM_ORE_COST = 3L

    val presets: List<ExpeditionPreset> = listOf(
        ExpeditionPreset("recon_5m", "Quick Recon", 5, "5m", "Fast highland scout"),
        ExpeditionPreset("patrol_30m", "Canyon Patrol", 30, "30m", "Steady ruin sweep"),
        ExpeditionPreset("ridge_2h", "Ridge Trek", 120, "2h", "Deep vault search"),
        ExpeditionPreset("sunspire_8h", "Sunspire March", 480, "8h", "High Ultra Rare odds"),
        ExpeditionPreset("zenith_23h", "Zenith Odyssey", 1380, "23h", "Guaranteed Ultra Rare")
    )

    fun ultraRareChancePercent(durationMinutes: Int): Int {
        val clamped = durationMinutes.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
        return when {
            clamped >= 1080 -> 100
            clamped <= 30 -> 12 + ((clamped - 5) * 12f / 25f).roundToInt()
            clamped <= 120 -> 24 + ((clamped - 30) * 21f / 90f).roundToInt()
            clamped <= 480 -> 45 + ((clamped - 120) * 31f / 360f).roundToInt()
            else -> 76 + ((clamped - 480) * 24f / 600f).roundToInt()
        }.coerceIn(12, 100)
    }

    /**
     * Computes Sol Coins earned from an expedition based on percent of time spent:
     * - Full completion payout scales linearly with duration as a % of the 23h max expedition (up to 100 Sol Coins).
     * - Stopping early awards the exact % of planned time spent (requires at least 5 minutes elapsed).
     */
    fun computeSolCoinReward(
        elapsedMinutes: Int,
        plannedDurationMinutes: Int
    ): ExpeditionSolCoinReward {
        val safePlanned = plannedDurationMinutes.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
        val safeElapsed = elapsedMinutes.coerceIn(0, safePlanned)
        val maxDurationFraction = safePlanned.toFloat() / MAX_DURATION_MINUTES.toFloat()
        val maxDurationPct = (maxDurationFraction * 100f).roundToInt().coerceIn(1, 100)
        val fullCoins = (maxDurationFraction * MAX_EXPEDITION_SOL_COINS.toFloat())
            .roundToInt()
            .toLong()
            .coerceIn(1L, MAX_EXPEDITION_SOL_COINS)

        val timeSpentFraction = (safeElapsed.toFloat() / safePlanned.toFloat()).coerceIn(0f, 1f)
        val timeSpentPct = (timeSpentFraction * 100f).roundToInt().coerceIn(0, 100)
        val earned = when {
            safeElapsed >= safePlanned -> fullCoins
            safeElapsed < MIN_EARLY_STOP_COIN_MINUTES -> 0L
            else -> (fullCoins.toFloat() * timeSpentFraction)
                .roundToInt()
                .toLong()
                .coerceIn(1L, fullCoins)
        }
        return ExpeditionSolCoinReward(
            elapsedMinutes = safeElapsed,
            plannedDurationMinutes = safePlanned,
            timeSpentPercent = timeSpentPct,
            maxDurationPercent = maxDurationPct,
            fullSolCoins = fullCoins,
            earnedSolCoins = earned
        )
    }

    fun lootPowerMultiplier(durationMinutes: Int): Float {
        val clamped = durationMinutes.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
        val fraction = (clamped - MIN_DURATION_MINUTES).toFloat() /
            (MAX_DURATION_MINUTES - MIN_DURATION_MINUTES).toFloat()
        return 1.0f + (fraction * 0.65f)
    }

    fun recommendedGearPower(durationMinutes: Int, heroLevel: Int): Int {
        val clamped = durationMinutes.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
        val safeLvl = heroLevel.coerceAtLeast(1)
        val baseHazard = when {
            clamped <= 15 -> 45 + safeLvl * 9
            clamped <= 60 -> 78 + safeLvl * 12
            clamped <= 240 -> 120 + safeLvl * 15
            clamped <= 720 -> 175 + safeLvl * 19
            else -> 235 + safeLvl * 24
        }
        return baseHazard.coerceAtLeast(45)
    }

    fun evaluateRiskForecast(
        durationMinutes: Int,
        stats: DerivedHeroStats,
        heroLevel: Int
    ): ExpeditionRiskForecast {
        val clamped = durationMinutes.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
        val recommended = recommendedGearPower(clamped, heroLevel)
        val currentGear = stats.gearPowerScore
        val gearRatio = (currentGear.toFloat() / recommended.toFloat()).coerceIn(0.15f, 1.65f)

        val baseSurvival = when {
            gearRatio < 1.0f -> 34f + ((gearRatio - 0.15f) / 0.85f) * 44f
            else -> 78f + ((gearRatio - 1.0f) / 0.65f) * 10f
        }
        val durationHazardPenalty = when {
            clamped >= 1080 -> 12
            clamped >= 480 -> 8
            clamped >= 120 -> 4
            else -> 0
        }
        val finalPct = (baseSurvival.roundToInt() - durationHazardPenalty + stats.expeditionWardPercent)
            .coerceIn(30, 98)

        val tier = when {
            finalPct >= 85 -> ExpeditionRiskTier.SAFE
            finalPct >= 65 -> ExpeditionRiskTier.GUARDED
            finalPct >= 45 -> ExpeditionRiskTier.RISKY
            else -> ExpeditionRiskTier.PERILOUS
        }
        val coinPreview = computeSolCoinReward(clamped, clamped)
        return ExpeditionRiskForecast(
            durationMinutes = clamped,
            survivalChancePct = finalPct,
            recommendedGearPower = recommended,
            currentGearPower = currentGear,
            ultraRareChancePct = ultraRareChancePercent(clamped),
            riskTier = tier,
            expectedSolCoins = coinPreview.fullSolCoins,
            durationTimePercent = coinPreview.maxDurationPercent
        )
    }

    fun formatDuration(minutes: Int): String {
        val safe = minutes.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
        val hrs = safe / 60
        val mins = safe % 60
        return when {
            hrs == 0 -> "${mins}m"
            mins == 0 -> "${hrs}h"
            else -> "${hrs}h ${mins}m"
        }
    }

    fun formatCountdown(remainingMs: Long): String {
        val totalSeconds = (remainingMs.coerceAtLeast(0L) + 999L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun resolveCompletedExpedition(
        userId: String,
        profile: HeroProfile,
        stats: DerivedHeroStats
    ): ExpeditionOutcomeReport {
        val durationMin = profile.activeExpeditionDurationMin.toInt()
            .coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
        val safeLvl = profile.level.toInt().coerceAtLeast(1)
        val forecast = evaluateRiskForecast(durationMin, stats, safeLvl)
        val succeeded = Random.nextInt(100) < forecast.survivalChancePct

        val baseXp = (28L + (durationMin * 0.45f).roundToInt().toLong() + safeLvl * 8L)
        val fullXp = (baseXp * stats.totalXpMultiplier).roundToInt().toLong().coerceAtLeast(25L)
        val fullShards = ((10L + (durationMin * 0.14f).roundToInt().toLong()) * stats.totalSunshardMultiplier)
            .roundToInt().toLong().coerceAtLeast(8L)
        val coinReward = computeSolCoinReward(durationMin, durationMin)

        return if (succeeded) {
            val chest = LootChest(
                userId = userId,
                chestTier = LootChestTier.EXPEDITION.name,
                sourceLabel = "${formatDuration(durationMin)} Expedition Relic Chest",
                heroLevelSnapshot = safeLvl.toLong(),
                durationMinutes = durationMin.toLong(),
                ultraRareChancePct = forecast.ultraRareChancePct.toLong()
            )
            ExpeditionOutcomeReport(
                succeeded = true,
                durationMinutes = durationMin,
                survivalChancePct = forecast.survivalChancePct,
                xpEarned = fullXp,
                sunshardsEarned = fullShards,
                hpLost = 0L,
                awardedChest = chest,
                summaryMessage = "Expedition Succeeded (${forecast.survivalChancePct}% survival)! Earned +${coinReward.fullSolCoins} Sol Coins (${coinReward.maxDurationPercent}% of max time), Sealed Relic Chest & Forge Materials (${forecast.ultraRareChancePct}% Ultra Rare odds).",
                solCoinsEarned = coinReward.fullSolCoins,
                timeSpentPercent = 100
            )
        } else {
            val consolationXp = max(12L, (fullXp * 0.25f).roundToInt().toLong())
            val hpWear = max(10L, (stats.computedMaxHp * 0.18f).roundToInt().toLong())
            val salvageCoins = max(1L, (coinReward.fullSolCoins * 0.5f).roundToInt().toLong())
            ExpeditionOutcomeReport(
                succeeded = false,
                durationMinutes = durationMin,
                survivalChancePct = forecast.survivalChancePct,
                xpEarned = consolationXp,
                sunshardsEarned = 0L,
                hpLost = hpWear,
                awardedChest = null,
                summaryMessage = "Expedition Failed (${forecast.survivalChancePct}% survival odds). Your hero escaped bruised (-$hpWear HP, +$salvageCoins Sol Coins from 50% time salvage, +$consolationXp consolation XP, no treasure chest). Equip stronger gear to improve survival!",
                solCoinsEarned = salvageCoins,
                timeSpentPercent = 100
            )
        }
    }

    /**
     * Opens any sealed LootChest and rolls across all 6 equipment slots (Weapon, Armor, Helm, Boots, Ring, Relic)
     * or an Alchemist Elixir.
     */
    fun openLootChest(chest: LootChest, userId: String): ChestOpenResult {
        val tier = chest.resolvedTier()
        val safeLvl = chest.heroLevelSnapshot.toInt().coerceAtLeast(1)
        val durationMin = if (tier == LootChestTier.EXPEDITION) {
            chest.durationMinutes.toInt().coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
        } else {
            0
        }
        val ultraChance = if (tier == LootChestTier.EXPEDITION && durationMin > 0) {
            ultraRareChancePercent(durationMin)
        } else {
            chest.ultraRareChancePct.toInt().coerceIn(0, 100)
        }

        val isUltraRare = Random.nextInt(100) < ultraChance
        val rarity = when {
            isUltraRare -> ItemRarity.ULTRA_RARE
            tier == LootChestTier.SPIRE_SOVEREIGN -> if (Random.nextFloat() > 0.35f) ItemRarity.SOLAR else ItemRarity.EPIC
            tier == LootChestTier.BOSS_VAULT -> if (Random.nextFloat() > 0.45f) ItemRarity.SOLAR else ItemRarity.EPIC
            tier == LootChestTier.EXPEDITION -> if (durationMin >= 60) ItemRarity.SOLAR else ItemRarity.EPIC
            else -> {
                val r = Random.nextFloat()
                when {
                    r > 0.78f -> ItemRarity.SOLAR
                    r > 0.42f -> ItemRarity.EPIC
                    else -> ItemRarity.RARE
                }
            }
        }

        val rarityMult = when (rarity) {
            ItemRarity.ULTRA_RARE -> 6.0f
            ItemRarity.SOLAR -> 4.2f
            ItemRarity.EPIC -> 3.0f
            ItemRarity.RARE -> 2.1f
            ItemRarity.COMMON -> 1.2f
        }
        val durationScale = if (durationMin > 0) lootPowerMultiplier(durationMin) else when (tier) {
            LootChestTier.SPIRE_SOVEREIGN -> 1.38f
            LootChestTier.BOSS_VAULT -> 1.22f
            else -> 1.05f
        }
        val effectiveTier = rarityMult * durationScale

        // Roll across all 6 equipment slots (or 14% chance for Consumable on Highland chests)
        val slotRoll = if (tier == LootChestTier.HIGHLAND && !isUltraRare && Random.nextFloat() < 0.14f) {
            6
        } else {
            Random.nextInt(6)
        }

        val item = buildEquipmentForSlotIndex(
            slotIndex = slotRoll,
            userId = userId,
            safeLvl = safeLvl,
            rarity = rarity,
            effectiveTier = effectiveTier,
            sourceLabel = chest.sourceLabel
        )

        return ChestOpenResult(
            chest = chest,
            item = item,
            wasUltraRare = rarity == ItemRarity.ULTRA_RARE
        )
    }

    fun rollBlackMarketMysteryItem(userId: String, heroLevel: Int): InventoryItem {
        val safeLvl = heroLevel.coerceAtLeast(1)
        val roll = Random.nextInt(100)
        val rarity = when {
            roll < 15 -> ItemRarity.ULTRA_RARE
            roll < 48 -> ItemRarity.SOLAR
            else -> ItemRarity.EPIC
        }
        val rarityMult = when (rarity) {
            ItemRarity.ULTRA_RARE -> 6.2f
            ItemRarity.SOLAR -> 4.5f
            else -> 3.2f
        }
        val slotIndex = Random.nextInt(6)
        return buildEquipmentForSlotIndex(
            slotIndex = slotIndex,
            userId = userId,
            safeLvl = safeLvl,
            rarity = rarity,
            effectiveTier = rarityMult,
            sourceLabel = "Obsidian Black-Market Cache"
        )
    }

    fun rerollItemPerk(item: InventoryItem): Pair<GearPerkType, Int> {
        val rarity = item.resolvedRarity()
        val currentPerk = item.resolvedPerk().first
        val candidates = when (item.resolvedSlot()) {
            ItemSlot.WEAPON, ItemSlot.RING -> listOf(
                GearPerkType.CRIT_CHANCE,
                GearPerkType.ARMOR_PIERCE,
                GearPerkType.LIFESTEAL,
                GearPerkType.SOLAR_SURGE
            )
            ItemSlot.ARMOR, ItemSlot.HELM -> listOf(
                GearPerkType.DAMAGE_REDUCTION,
                GearPerkType.THORNS,
                GearPerkType.TURN_REGEN,
                GearPerkType.EXPEDITION_WARD
            )
            ItemSlot.BOOTS, ItemSlot.RELIC -> listOf(
                GearPerkType.EXPEDITION_WARD,
                GearPerkType.CRIT_CHANCE,
                GearPerkType.SOLAR_SURGE,
                GearPerkType.TURN_REGEN
            )
            ItemSlot.CONSUMABLE -> listOf(GearPerkType.NONE)
        }.filter { it != currentPerk }.ifEmpty { listOf(GearPerkType.CRIT_CHANCE) }

        val chosen = candidates[Random.nextInt(candidates.size)]
        val tier = rarity.ordinal + 1 + (item.upgradeLevel.toInt() / 3)
        val value = when (chosen) {
            GearPerkType.CRIT_CHANCE -> (8 + tier * 4 + Random.nextInt(0, 4)).coerceIn(8, 38)
            GearPerkType.ARMOR_PIERCE -> (12 + tier * 6 + Random.nextInt(0, 5)).coerceIn(12, 50)
            GearPerkType.LIFESTEAL -> (6 + tier * 3 + Random.nextInt(0, 3)).coerceIn(6, 26)
            GearPerkType.SOLAR_SURGE -> (12 + tier * 6 + Random.nextInt(0, 6)).coerceIn(12, 52)
            GearPerkType.DAMAGE_REDUCTION -> (6 + tier * 4 + Random.nextInt(0, 3)).coerceIn(6, 32)
            GearPerkType.THORNS -> (10 + tier * 5 + Random.nextInt(0, 4)).coerceIn(10, 42)
            GearPerkType.EXPEDITION_WARD -> (8 + tier * 4 + Random.nextInt(0, 4)).coerceIn(8, 35)
            GearPerkType.TURN_REGEN -> (7 + tier * 4 + Random.nextInt(0, 4)).coerceIn(7, 36)
            GearPerkType.NONE -> 0
        }
        return chosen to value
    }

    private fun buildEquipmentForSlotIndex(
        slotIndex: Int,
        userId: String,
        safeLvl: Int,
        rarity: ItemRarity,
        effectiveTier: Float,
        sourceLabel: String
    ): InventoryItem = when (slotIndex) {
        0 -> buildWeaponDrop(userId, safeLvl, rarity, effectiveTier, sourceLabel)
        1 -> buildArmorDrop(userId, safeLvl, rarity, effectiveTier, sourceLabel)
        2 -> buildHelmDrop(userId, safeLvl, rarity, effectiveTier, sourceLabel)
        3 -> buildBootsDrop(userId, safeLvl, rarity, effectiveTier, sourceLabel)
        4 -> buildRingDrop(userId, safeLvl, rarity, effectiveTier, sourceLabel)
        5 -> buildRelicDrop(userId, safeLvl, rarity, effectiveTier, sourceLabel)
        else -> InventoryItem(
            userId = userId,
            name = "Sunburst Tonic",
            slot = ItemSlot.CONSUMABLE.name,
            rarity = ItemRarity.RARE.name,
            healAmount = (85 + safeLvl * 16).toLong().coerceIn(50L, 9999L),
            quantity = 2L,
            description = "Recovered from $sourceLabel. Restores HP mid-battle or at camp.",
            perkType = GearPerkType.NONE.name,
            perkValue = 0L
        )
    }

    private fun rollWeaponPerk(rarity: ItemRarity): Pair<GearPerkType, Int> {
        val pool = listOf(
            GearPerkType.CRIT_CHANCE,
            GearPerkType.ARMOR_PIERCE,
            GearPerkType.LIFESTEAL,
            GearPerkType.SOLAR_SURGE
        )
        val perk = pool[Random.nextInt(pool.size)]
        val tier = rarity.ordinal + 1
        val value = when (perk) {
            GearPerkType.CRIT_CHANCE -> (6 + tier * 4 + Random.nextInt(0, 4)).coerceIn(6, 36)
            GearPerkType.ARMOR_PIERCE -> (10 + tier * 6 + Random.nextInt(0, 5)).coerceIn(10, 48)
            GearPerkType.LIFESTEAL -> (5 + tier * 3 + Random.nextInt(0, 3)).coerceIn(5, 24)
            GearPerkType.SOLAR_SURGE -> (10 + tier * 6 + Random.nextInt(0, 6)).coerceIn(10, 48)
            else -> 8
        }
        return perk to value
    }

    private fun rollArmorPerk(rarity: ItemRarity): Pair<GearPerkType, Int> {
        val pool = listOf(
            GearPerkType.DAMAGE_REDUCTION,
            GearPerkType.THORNS,
            GearPerkType.EXPEDITION_WARD,
            GearPerkType.TURN_REGEN
        )
        val perk = pool[Random.nextInt(pool.size)]
        val tier = rarity.ordinal + 1
        val value = when (perk) {
            GearPerkType.DAMAGE_REDUCTION -> (5 + tier * 4 + Random.nextInt(0, 3)).coerceIn(5, 30)
            GearPerkType.THORNS -> (8 + tier * 5 + Random.nextInt(0, 4)).coerceIn(8, 38)
            GearPerkType.EXPEDITION_WARD -> (6 + tier * 4 + Random.nextInt(0, 4)).coerceIn(6, 32)
            GearPerkType.TURN_REGEN -> (5 + tier * 4 + Random.nextInt(0, 4)).coerceIn(5, 34)
            else -> 8
        }
        return perk to value
    }

    private fun rollRelicPerk(rarity: ItemRarity): Pair<GearPerkType, Int> {
        val pool = listOf(
            GearPerkType.EXPEDITION_WARD,
            GearPerkType.CRIT_CHANCE,
            GearPerkType.SOLAR_SURGE,
            GearPerkType.TURN_REGEN
        )
        val perk = pool[Random.nextInt(pool.size)]
        val tier = rarity.ordinal + 1
        val value = when (perk) {
            GearPerkType.EXPEDITION_WARD -> (7 + tier * 4 + Random.nextInt(0, 4)).coerceIn(7, 34)
            GearPerkType.CRIT_CHANCE -> (5 + tier * 3 + Random.nextInt(0, 3)).coerceIn(5, 28)
            GearPerkType.SOLAR_SURGE -> (8 + tier * 5 + Random.nextInt(0, 4)).coerceIn(8, 40)
            GearPerkType.TURN_REGEN -> (5 + tier * 3 + Random.nextInt(0, 4)).coerceIn(5, 30)
            else -> 8
        }
        return perk to value
    }

    private fun buildWeaponDrop(
        userId: String,
        level: Int,
        rarity: ItemRarity,
        effectiveTier: Float,
        sourceLabel: String
    ): InventoryItem {
        val names = when (rarity) {
            ItemRarity.ULTRA_RARE -> listOf("Astral Eclipse Greatsword", "Dawnbreaker Halberd", "Helios Sovereign Blade")
            ItemRarity.SOLAR -> listOf("Zenith Sunblade", "Solaris Warbrand", "Cindercrest Claymore")
            ItemRarity.EPIC -> listOf("Obsidian Rift Edge", "Highland Relic Falchion", "Dunewarden Spear")
            else -> listOf("Sunforged Gladius", "Canyon Bronze Saber", "Wayfarer Twinblade")
        }
        val (perk, perkVal) = rollWeaponPerk(rarity)
        return InventoryItem(
            userId = userId,
            name = names[Random.nextInt(names.size)],
            slot = ItemSlot.WEAPON.name,
            rarity = rarity.name,
            attackBonus = (10 + level * 4 + (effectiveTier * 6.5f).roundToInt()).toLong().coerceIn(1L, 9999L),
            defenseBonus = (effectiveTier * 1.8f).roundToInt().toLong().coerceIn(0L, 9999L),
            hpBonus = if (rarity == ItemRarity.ULTRA_RARE) (24 + level * 4).toLong().coerceAtMost(9999L) else 0L,
            description = "${perk.formatSummary(perkVal)} • Looted from $sourceLabel.".take(180),
            perkType = perk.name,
            perkValue = perkVal.toLong()
        )
    }

    private fun buildArmorDrop(
        userId: String,
        level: Int,
        rarity: ItemRarity,
        effectiveTier: Float,
        sourceLabel: String
    ): InventoryItem {
        val names = when (rarity) {
            ItemRarity.ULTRA_RARE -> listOf("Celestial Sunplate Aegis", "Prismatic Vault Cuirass", "Sovereign Obsidian Mail")
            ItemRarity.SOLAR -> listOf("Gilded Vanguard Plate", "Sunspire Bastion Armor", "Aurelian Greatmail")
            ItemRarity.EPIC -> listOf("Obsidian Bastion Cuirass", "Canyon Warden Brigandine", "Riftforged Carapace")
            else -> listOf("Limestone Scale Mail", "Highland Strider Vest", "Bronze Crest Hauberk")
        }
        val (perk, perkVal) = rollArmorPerk(rarity)
        return InventoryItem(
            userId = userId,
            name = names[Random.nextInt(names.size)],
            slot = ItemSlot.ARMOR.name,
            rarity = rarity.name,
            attackBonus = if (rarity == ItemRarity.ULTRA_RARE) (5 + level).toLong().coerceAtMost(9999L) else 0L,
            defenseBonus = (8 + level * 3 + (effectiveTier * 5.2f).roundToInt()).toLong().coerceIn(1L, 9999L),
            hpBonus = (30 + level * 8 + (effectiveTier * 16f).roundToInt()).toLong().coerceIn(1L, 9999L),
            description = "${perk.formatSummary(perkVal)} • Looted from $sourceLabel.".take(180),
            perkType = perk.name,
            perkValue = perkVal.toLong()
        )
    }

    private fun buildHelmDrop(
        userId: String,
        level: Int,
        rarity: ItemRarity,
        effectiveTier: Float,
        sourceLabel: String
    ): InventoryItem {
        val names = when (rarity) {
            ItemRarity.ULTRA_RARE -> listOf("Crown of Sol Invictus", "Eclipse Sovereign Visor", "Astral Archon Greathelm")
            ItemRarity.SOLAR -> listOf("Sunspire Crest Casque", "Helios Diadem", "Cinderlord Barbute")
            ItemRarity.EPIC -> listOf("Obsidian Vanguard Helm", "Astrolabe Sight Hood", "Frostspire Crest")
            else -> listOf("Bronze Wayfarer Helm", "Sandstone Scout Cowl", "Highland Iron Casque")
        }
        val (perk, perkVal) = rollArmorPerk(rarity)
        return InventoryItem(
            userId = userId,
            name = names[Random.nextInt(names.size)],
            slot = ItemSlot.HELM.name,
            rarity = rarity.name,
            attackBonus = (2 + (effectiveTier * 1.8f).roundToInt()).toLong().coerceIn(1L, 9999L),
            defenseBonus = (6 + level * 2 + (effectiveTier * 4.0f).roundToInt()).toLong().coerceIn(1L, 9999L),
            hpBonus = (22 + level * 6 + (effectiveTier * 12f).roundToInt()).toLong().coerceIn(1L, 9999L),
            description = "${perk.formatSummary(perkVal)} • Looted from $sourceLabel.".take(180),
            perkType = perk.name,
            perkValue = perkVal.toLong()
        )
    }

    private fun buildBootsDrop(
        userId: String,
        level: Int,
        rarity: ItemRarity,
        effectiveTier: Float,
        sourceLabel: String
    ): InventoryItem {
        val names = when (rarity) {
            ItemRarity.ULTRA_RARE -> listOf("Greaves of the Eternal Stride", "Starlight Horizon Sabatons", "Singularity Windrunners")
            ItemRarity.SOLAR -> listOf("Sunstrider Winged Greaves", "Zenith Vanguard Sabatons", "Gale-Forged Treads")
            ItemRarity.EPIC -> listOf("Obsidian Ridge Greaves", "Canyon Pathfinder Boots", "Frostwalker Treads")
            else -> listOf("Sandstone Strider Boots", "Bronze March Greaves", "Wayfarer Leather Treads")
        }
        val (perk, perkVal) = rollRelicPerk(rarity)
        return InventoryItem(
            userId = userId,
            name = names[Random.nextInt(names.size)],
            slot = ItemSlot.BOOTS.name,
            rarity = rarity.name,
            attackBonus = (3 + level + (effectiveTier * 2.2f).roundToInt()).toLong().coerceIn(1L, 9999L),
            defenseBonus = (5 + level * 2 + (effectiveTier * 3.5f).roundToInt()).toLong().coerceIn(1L, 9999L),
            hpBonus = (14 + level * 4 + (effectiveTier * 8f).roundToInt()).toLong().coerceIn(1L, 9999L),
            energyBonus = (12 + (effectiveTier * 5f).roundToInt()).toLong().coerceIn(1L, 999L),
            description = "${perk.formatSummary(perkVal)} • Looted from $sourceLabel.".take(180),
            perkType = perk.name,
            perkValue = perkVal.toLong()
        )
    }

    private fun buildRingDrop(
        userId: String,
        level: Int,
        rarity: ItemRarity,
        effectiveTier: Float,
        sourceLabel: String
    ): InventoryItem {
        val names = when (rarity) {
            ItemRarity.ULTRA_RARE -> listOf("Signet of the Black Sun", "Helios Eternal Loop", "Astral Sovereign Band")
            ItemRarity.SOLAR -> listOf("Sunblood Ruby Signet", "Zenith Arcanist Ring", "Pyreheart Seal")
            ItemRarity.EPIC -> listOf("Obsidian Fang Band", "Astrolabe Chrono Ring", "Rimefire Loop")
            else -> listOf("Bronze Sundial Ring", "Canyon Quartz Band", "Strider Signet")
        }
        val (perk, perkVal) = rollWeaponPerk(rarity)
        return InventoryItem(
            userId = userId,
            name = names[Random.nextInt(names.size)],
            slot = ItemSlot.RING.name,
            rarity = rarity.name,
            attackBonus = (7 + level * 3 + (effectiveTier * 4.5f).roundToInt()).toLong().coerceIn(1L, 9999L),
            defenseBonus = (2 + (effectiveTier * 2.0f).roundToInt()).toLong().coerceIn(1L, 9999L),
            hpBonus = (12 + level * 3 + (effectiveTier * 6f).roundToInt()).toLong().coerceIn(0L, 9999L),
            energyBonus = (8 + (effectiveTier * 3f).roundToInt()).toLong().coerceIn(1L, 999L),
            description = "${perk.formatSummary(perkVal)} • Looted from $sourceLabel.".take(180),
            perkType = perk.name,
            perkValue = perkVal.toLong()
        )
    }

    private fun buildRelicDrop(
        userId: String,
        level: Int,
        rarity: ItemRarity,
        effectiveTier: Float,
        sourceLabel: String
    ): InventoryItem {
        val names = when (rarity) {
            ItemRarity.ULTRA_RARE -> listOf("Prismatic Astrolabe Heart", "Crown of the Eternal Stride", "Zenith Compass Singularity")
            ItemRarity.SOLAR -> listOf("Radiant Sundial Crest", "Vaultseeker Signet", "Horizon Sunstone")
            ItemRarity.EPIC -> listOf("Highland Horizon Medallion", "Obsidian Compass Charm", "Astrolabe Lens")
            else -> listOf("Bronze Wayfarer Seal", "Canyon Sun Amulet", "Strider Talisman")
        }
        val (perk, perkVal) = rollRelicPerk(rarity)
        return InventoryItem(
            userId = userId,
            name = names[Random.nextInt(names.size)],
            slot = ItemSlot.RELIC.name,
            rarity = rarity.name,
            attackBonus = (4 + (effectiveTier * 3.0f).roundToInt()).toLong().coerceIn(1L, 9999L),
            defenseBonus = (3 + (effectiveTier * 2.4f).roundToInt()).toLong().coerceIn(1L, 9999L),
            hpBonus = (16 + level * 3 + (effectiveTier * 10f).roundToInt()).toLong().coerceIn(0L, 9999L),
            energyBonus = (14 + (effectiveTier * 6f).roundToInt()).toLong().coerceIn(1L, 999L),
            description = "${perk.formatSummary(perkVal)} • Looted from $sourceLabel.".take(180),
            perkType = perk.name,
            perkValue = perkVal.toLong()
        )
    }

    /**
     * Forges a custom Weapon, Armor, Helm, Boots, Ring, or Relic directly from gathered materials.
     * Supports optional Elemental Essence infusion to tag the crafted piece with a live elemental rune.
     */
    fun forgeCustomEquipment(
        userId: String,
        slot: ItemSlot,
        tier: ForgeBlueprintTier,
        heroLevel: Int,
        chosenElement: ElementalAffinity? = null,
        guaranteedMasterwork: Boolean = false
    ): InventoryItem {
        val safeLvl = heroLevel.coerceAtLeast(1)
        val critForgeUpgrade = guaranteedMasterwork || Random.nextFloat() < 0.22f
        val finalRarity = if (critForgeUpgrade) {
            when (tier.baseRarity) {
                ItemRarity.COMMON -> ItemRarity.RARE
                ItemRarity.RARE -> ItemRarity.EPIC
                ItemRarity.EPIC -> ItemRarity.SOLAR
                ItemRarity.SOLAR -> ItemRarity.ULTRA_RARE
                else -> ItemRarity.ULTRA_RARE
            }
        } else {
            tier.baseRarity
        }
        val effectiveTier = tier.powerScalar * (if (critForgeUpgrade) 1.18f else 1.0f)
        val sourceLabel = when {
            guaranteedMasterwork -> "Starforged Masterwork Anvil (${tier.label})"
            critForgeUpgrade -> "Masterwork Critical Forge (${tier.label})"
            else -> "Blacksmith Anvil (${tier.label})"
        }
        val baseItem = when (slot) {
            ItemSlot.WEAPON -> buildWeaponDrop(userId, safeLvl, finalRarity, effectiveTier, sourceLabel)
            ItemSlot.ARMOR -> buildArmorDrop(userId, safeLvl, finalRarity, effectiveTier, sourceLabel)
            ItemSlot.HELM -> buildHelmDrop(userId, safeLvl, finalRarity, effectiveTier, sourceLabel)
            ItemSlot.BOOTS -> buildBootsDrop(userId, safeLvl, finalRarity, effectiveTier, sourceLabel)
            ItemSlot.RING -> buildRingDrop(userId, safeLvl, finalRarity, effectiveTier, sourceLabel)
            ItemSlot.RELIC -> buildRelicDrop(userId, safeLvl, finalRarity, effectiveTier, sourceLabel)
            ItemSlot.CONSUMABLE -> buildWeaponDrop(userId, safeLvl, finalRarity, effectiveTier, sourceLabel)
        }
        val prefix = when (tier) {
            ForgeBlueprintTier.BRONZE_SURVIVOR -> "Hand-Forged"
            ForgeBlueprintTier.SUNSTEEL_VANGUARD -> "Sunsteel"
            ForgeBlueprintTier.OBSIDIAN_MASTERWORK -> "Obsidian"
            ForgeBlueprintTier.ASTRAL_SOVEREIGN -> "Astral"
        }
        val elementPrefix = if (chosenElement != null) "${chosenElement.label} " else ""
        val slotNoun = when (slot) {
            ItemSlot.WEAPON -> listOf("Blade", "Waraxe", "Spear", "Claymore").random()
            ItemSlot.ARMOR -> listOf("Cuirass", "Brigandine", "Plate", "Hauberk").random()
            ItemSlot.HELM -> listOf("Greathelm", "Visor", "Crown", "Barbute").random()
            ItemSlot.BOOTS -> listOf("Greaves", "Sabatons", "Striders", "Treads").random()
            ItemSlot.RING -> listOf("Signet", "Band", "Seal", "Loop").random()
            ItemSlot.RELIC -> listOf("Astrolabe", "Talisman", "Compass", "Crest").random()
            ItemSlot.CONSUMABLE -> "Blade"
        }
        val runeTag = if (chosenElement != null) " [Rune:${chosenElement.name}]" else ""
        val forgedName = "$prefix $elementPrefix$slotNoun".take(56)
        val descWithRune = (baseItem.description.take(152) + runeTag).take(180)
        return baseItem.copy(
            name = forgedName,
            description = descWithRune
        )
    }
}
