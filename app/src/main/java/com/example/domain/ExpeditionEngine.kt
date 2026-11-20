package com.example.domain

import com.example.data.model.DerivedHeroStats
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

data class ExpeditionRiskForecast(
    val durationMinutes: Int,
    val survivalChancePct: Int,
    val recommendedGearPower: Int,
    val currentGearPower: Int,
    val ultraRareChancePct: Int,
    val riskTier: ExpeditionRiskTier
)

data class ExpeditionOutcomeReport(
    val succeeded: Boolean,
    val durationMinutes: Int,
    val survivalChancePct: Int,
    val xpEarned: Long,
    val sunshardsEarned: Long,
    val hpLost: Long,
    val awardedChest: LootChest?,
    val summaryMessage: String
)

data class ChestOpenResult(
    val chest: LootChest,
    val item: InventoryItem,
    val wasUltraRare: Boolean
)

object ExpeditionEngine {

    const val MIN_DURATION_MINUTES = 5
    const val MAX_DURATION_MINUTES = 23 * 60 // 1380 minutes (23 hours)
    const val BOSS_VICTORY_SOL_COINS = 10L
    const val CAMP_HEAL_SOL_COIN_COST = 5L

    val presets: List<ExpeditionPreset> = listOf(
        ExpeditionPreset("recon_5m", "Quick Recon", 5, "5m", "Fast highland scout"),
        ExpeditionPreset("patrol_30m", "Canyon Patrol", 30, "30m", "Steady ruin sweep"),
        ExpeditionPreset("ridge_2h", "Ridge Trek", 120, "2h", "Deep vault search"),
        ExpeditionPreset("sunspire_8h", "Sunspire March", 480, "8h", "High Ultra Rare odds"),
        ExpeditionPreset("zenith_23h", "Zenith Odyssey", 1380, "23h", "Guaranteed Ultra Rare")
    )

    /**
     * Scales Ultra Rare drop probability smoothly from 12% at 5 minutes up to 100% at 18–23 hours.
     * Preserved identically inside Expedition Loot Chests!
     */
    fun ultraRareChancePercent(durationMinutes: Int): Int {
        val clamped = durationMinutes.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
        return when {
            clamped >= 1080 -> 100 // 18h..23h guaranteed Ultra Rare
            clamped <= 30 -> {
                // 5m -> 12%, 30m -> 24%
                12 + ((clamped - 5) * 12f / 25f).roundToInt()
            }
            clamped <= 120 -> {
                // 30m -> 24%, 2h -> 45%
                24 + ((clamped - 30) * 21f / 90f).roundToInt()
            }
            clamped <= 480 -> {
                // 2h -> 45%, 8h -> 76%
                45 + ((clamped - 120) * 31f / 360f).roundToInt()
            }
            else -> {
                // 8h -> 76%, 18h -> 100%
                76 + ((clamped - 480) * 24f / 600f).roundToInt()
            }
        }.coerceIn(12, 100)
    }

    /**
     * Returns a power multiplier (1.00x at 5m up to 1.65x at 23h) so longer expeditions roll stronger Ultra Rare gear.
     */
    fun lootPowerMultiplier(durationMinutes: Int): Float {
        val clamped = durationMinutes.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
        val fraction = (clamped - MIN_DURATION_MINUTES).toFloat() /
            (MAX_DURATION_MINUTES - MIN_DURATION_MINUTES).toFloat()
        return 1.0f + (fraction * 0.65f)
    }

    /**
     * Gear Power threshold recommended for safely completing an expedition of [durationMinutes].
     * Longer expeditions require stronger equipped Weapons, Armor, and Relics.
     */
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

    /**
     * Evaluates expedition survival odds (30%..98%) anchored directly to equipped Weapon/Armor/Relic
     * Gear Power, Armor Wayfarer Ward perks, Set Resonance, Skill Tree Pathfinder Ward, and Prestige.
     */
    fun evaluateRiskForecast(
        durationMinutes: Int,
        stats: DerivedHeroStats,
        heroLevel: Int
    ): ExpeditionRiskForecast {
        val clamped = durationMinutes.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
        val recommended = recommendedGearPower(clamped, heroLevel)
        val currentGear = stats.gearPowerScore
        val gearRatio = (currentGear.toFloat() / recommended.toFloat()).coerceIn(0.15f, 1.65f)

        // Base survival climbs from 34% (severely undergeared) to 78% at 1.0x gear power and 86% overgeared
        val baseSurvival = when {
            gearRatio < 1.0f -> 34f + ((gearRatio - 0.15f) / 0.85f) * 44f
            else -> 78f + ((gearRatio - 1.0f) / 0.65f) * 10f
        }
        // Longer expeditions carry slightly higher inherent hazard unless mitigated by gear wards
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
        return ExpeditionRiskForecast(
            durationMinutes = clamped,
            survivalChancePct = finalPct,
            recommendedGearPower = recommended,
            currentGearPower = currentGear,
            ultraRareChancePct = ultraRareChancePercent(clamped),
            riskTier = tier
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

    /**
     * Resolves a finished timed expedition with a real survival check driven by equipped armor & weapons.
     * Success spawns an interactive LootChest preserving exact 12%..100% Ultra Rare relic odds.
     * Failure deals minor HP wear, grants small consolation XP, and drops zero chest.
     */
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
        val fullShards = ((22L + (durationMin * 0.35f).roundToInt().toLong()) * stats.totalSunshardMultiplier)
            .roundToInt().toLong().coerceAtLeast(20L)

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
                summaryMessage = "Expedition Succeeded (${forecast.survivalChancePct}% survival)! Sealed Relic Chest recovered (${forecast.ultraRareChancePct}% Ultra Rare odds)."
            )
        } else {
            val consolationXp = max(12L, (fullXp * 0.25f).roundToInt().toLong())
            val hpWear = max(10L, (stats.computedMaxHp * 0.18f).roundToInt().toLong())
            ExpeditionOutcomeReport(
                succeeded = false,
                durationMinutes = durationMin,
                survivalChancePct = forecast.survivalChancePct,
                xpEarned = consolationXp,
                sunshardsEarned = 0L,
                hpLost = hpWear,
                awardedChest = null,
                summaryMessage = "Expedition Failed (${forecast.survivalChancePct}% survival odds). Your hero escaped bruised (-$hpWear HP, +$consolationXp consolation XP, no treasure chest). Upgrade weapons, armor, or Wayfarer Ward perks to improve survival!"
            )
        }
    }

    /**
     * Opens any sealed LootChest and rolls a weapon, armor, relic, or elixir with real combat/expedition perks.
     * Expedition chests preserve the exact duration-scaled Ultra Rare relic probability (12%..100%).
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

        // Ultra Rare / Boss / Expedition chests never waste a roll on plain consumables
        val slotRoll = if (tier == LootChestTier.HIGHLAND && !isUltraRare && Random.nextFloat() < 0.18f) {
            3
        } else {
            Random.nextInt(3)
        }

        val item = when (slotRoll) {
            0 -> buildWeaponDrop(userId, safeLvl, rarity, effectiveTier, chest.sourceLabel)
            1 -> buildArmorDrop(userId, safeLvl, rarity, effectiveTier, chest.sourceLabel)
            2 -> buildRelicDrop(userId, safeLvl, rarity, effectiveTier, chest.sourceLabel)
            else -> InventoryItem(
                userId = userId,
                name = "Sunburst Tonic",
                slot = ItemSlot.CONSUMABLE.name,
                rarity = ItemRarity.RARE.name,
                healAmount = (75 + safeLvl * 14).toLong().coerceIn(50L, 9999L),
                quantity = 2L,
                description = "Recovered from ${chest.sourceLabel}. Restores HP mid-battle or at camp.",
                perkType = GearPerkType.NONE.name,
                perkValue = 0L
            )
        }

        return ChestOpenResult(
            chest = chest,
            item = item,
            wasUltraRare = rarity == ItemRarity.ULTRA_RARE
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
}
