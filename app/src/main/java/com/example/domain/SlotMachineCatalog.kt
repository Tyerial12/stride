package com.example.domain

import kotlin.random.Random

enum class SlotPrizeRarity(
    val label: String,
    val badgeColorHex: Long,
    val wedgeStartHex: Long,
    val wedgeEndHex: Long,
    val textColorHex: Long
) {
    COMMON(
        label = "Common",
        badgeColorHex = 0xFFD97706,
        wedgeStartHex = 0xFF451A03,
        wedgeEndHex = 0xFF78350F,
        textColorHex = 0xFFFEF3C7
    ),
    UNCOMMON(
        label = "Uncommon",
        badgeColorHex = 0xFF10B981,
        wedgeStartHex = 0xFF064E3B,
        wedgeEndHex = 0xFF047857,
        textColorHex = 0xFFD1FAE5
    ),
    RARE(
        label = "Rare",
        badgeColorHex = 0xFF3B82F6,
        wedgeStartHex = 0xFF1E3A8A,
        wedgeEndHex = 0xFF1D4ED8,
        textColorHex = 0xFFDBEAFE
    ),
    EPIC(
        label = "Epic",
        badgeColorHex = 0xFFA855F7,
        wedgeStartHex = 0xFF4C1D95,
        wedgeEndHex = 0xFF7E22CE,
        textColorHex = 0xFFF3E8FF
    ),
    SOLAR(
        label = "Solar",
        badgeColorHex = 0xFFF59E0B,
        wedgeStartHex = 0xFF92400E,
        wedgeEndHex = 0xFFD97706,
        textColorHex = 0xFFFFFBEB
    ),
    ULTRA_RARE(
        label = "ULTRA RARE",
        badgeColorHex = 0xFFEC4899,
        wedgeStartHex = 0xFF831843,
        wedgeEndHex = 0xFFDB2777,
        textColorHex = 0xFFFFF1F2
    )
}

enum class SlotPrizeIconKind {
    PET_CARE_DOUBLE,
    SUNSHARDS,
    FORGE_INGOT,
    LOOT_CHEST,
    COIN_JACKPOT,
    PET_CARE_SINGLE,
    ALCHEMIST_VIAL,
    PET_FEAST,
    EPIC_BLADE,
    MYTHIC_CORE
}

data class SlotWheelPrize(
    val id: String,
    val title: String,
    val shortWheelLabel: String,
    val symbol: String,
    val iconKind: SlotPrizeIconKind,
    val rarity: SlotPrizeRarity,
    val weightOutOf1000: Int,
    val rewardSummary: String,
    val bonusPetCareCharges: Int = 0
) {
    val probabilityPctText: String
        get() {
            val whole = weightOutOf1000 / 10
            val tenths = weightOutOf1000 % 10
            return if (tenths == 0) "$whole%" else "$whole.$tenths%"
        }
}

data class SlotSpinResult(
    val spinId: Long,
    val wedgeIndex: Int,
    val prize: SlotWheelPrize,
    val headline: String,
    val detailLine: String,
    val bonusPetCareGranted: Int = 0,
    val awardedItemName: String? = null,
    val awardedChestLabel: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
) {
    val isUltraRare: Boolean get() = prize.rarity == SlotPrizeRarity.ULTRA_RARE
    val isJackpot: Boolean get() = prize.rarity == SlotPrizeRarity.SOLAR || isUltraRare
}

data class SlotMachineUiState(
    val lastResult: SlotSpinResult? = null,
    val recentHistory: List<SlotSpinResult> = emptyList(),
    val totalSpinsThisSession: Int = 0,
    val ultraRareHitsThisSession: Int = 0
)

/**
 * Weighted loot table & spinning-wheel definition for the Sunhaven Fortune Slot Wheel.
 * Total weight = 1000 (100.0%). Combined Ultra Rare Extra Pet Care chance = 4.0% (1.5% +2, 2.5% +1).
 */
object SlotMachineCatalog {
    const val SPIN_COST_SOL_COINS = 50L
    const val TOTAL_WEIGHT = 1000

    val wheelPrizes: List<SlotWheelPrize> = listOf(
        // Index 0 — ULTRA RARE (1.5%)
        SlotWheelPrize(
            id = "ultra_pet_care_double",
            title = "Celestial Pet Care (+2)",
            shortWheelLabel = "+2 PET CARE",
            symbol = "🐾✨",
            iconKind = SlotPrizeIconKind.PET_CARE_DOUBLE,
            rarity = SlotPrizeRarity.ULTRA_RARE,
            weightOutOf1000 = 15,
            rewardSummary = "+2 Extra Pet Care charges (bypass 4/4 daily cap in Sanctuary & Widget) + 2 Wild Stamina",
            bonusPetCareCharges = 2
        ),
        // Index 1 — COMMON (17.0%)
        SlotWheelPrize(
            id = "sunshard_coin_pouch",
            title = "Sunshard & Coin Pouch",
            shortWheelLabel = "SHARDS+20c",
            symbol = "☀",
            iconKind = SlotPrizeIconKind.SUNSHARDS,
            rarity = SlotPrizeRarity.COMMON,
            weightOutOf1000 = 170,
            rewardSummary = "+75 Sunshards, +20 Sol Coins rebate & +8 Sunsteel Ore"
        ),
        // Index 2 — RARE (14.0%)
        SlotWheelPrize(
            id = "starforged_cache",
            title = "Starforged Forge Cache",
            shortWheelLabel = "STARFORGE",
            symbol = "⚒",
            iconKind = SlotPrizeIconKind.FORGE_INGOT,
            rarity = SlotPrizeRarity.RARE,
            weightOutOf1000 = 140,
            rewardSummary = "+1 Starforged Ingot, +12 Sunsteel Ore & +6 Elemental Essence"
        ),
        // Index 3 — EPIC (10.0%)
        SlotWheelPrize(
            id = "boss_vault_chest",
            title = "Boss Vault Treasure Chest",
            shortWheelLabel = "BOSS CHEST",
            symbol = "🎁",
            iconKind = SlotPrizeIconKind.LOOT_CHEST,
            rarity = SlotPrizeRarity.EPIC,
            weightOutOf1000 = 100,
            rewardSummary = "Drops a Boss Vault Chest (18% Ultra Rare gear chance) into your Vault"
        ),
        // Index 4 — SOLAR (6.0%)
        SlotWheelPrize(
            id = "sol_coin_jackpot",
            title = "Sunvault Coin Jackpot",
            shortWheelLabel = "+150 COINS",
            symbol = "🪙",
            iconKind = SlotPrizeIconKind.COIN_JACKPOT,
            rarity = SlotPrizeRarity.SOLAR,
            weightOutOf1000 = 60,
            rewardSummary = "+150 Sol Coins (3x return!) & +50 Sunshards"
        ),
        // Index 5 — ULTRA RARE (2.5%) — Opposite side of wheel for dramatic near-misses
        SlotWheelPrize(
            id = "ultra_pet_care_single",
            title = "Sunlit Pet Care (+1)",
            shortWheelLabel = "+1 PET CARE",
            symbol = "💖🐾",
            iconKind = SlotPrizeIconKind.PET_CARE_SINGLE,
            rarity = SlotPrizeRarity.ULTRA_RARE,
            weightOutOf1000 = 25,
            rewardSummary = "+1 Extra Pet Care charge (bypass 4/4 daily cap in Sanctuary & Widget) + 1 Wild Stamina",
            bonusPetCareCharges = 1
        ),
        // Index 6 — UNCOMMON (17.0%)
        SlotWheelPrize(
            id = "alchemist_dream_satchel",
            title = "Alchemist & Dream Satchel",
            shortWheelLabel = "DREAM PACK",
            symbol = "🧪",
            iconKind = SlotPrizeIconKind.ALCHEMIST_VIAL,
            rarity = SlotPrizeRarity.UNCOMMON,
            weightOutOf1000 = 170,
            rewardSummary = "+10 Aether Herbs, +4 Dream Shards, +30 Stride Energy & +40 Sunshards"
        ),
        // Index 7 — RARE (16.0%)
        SlotWheelPrize(
            id = "companion_bond_feast",
            title = "Companion Bond Feast",
            shortWheelLabel = "PET FEAST",
            symbol = "🍖",
            iconKind = SlotPrizeIconKind.PET_FEAST,
            rarity = SlotPrizeRarity.RARE,
            weightOutOf1000 = 160,
            rewardSummary = "+85 Pet XP, +60 Bond XP for your active Pet & +1 Evolution Shard"
        ),
        // Index 8 — EPIC (11.0%)
        SlotWheelPrize(
            id = "forged_epic_gear",
            title = "Masterwork Gear Drop",
            shortWheelLabel = "EPIC GEAR",
            symbol = "⚔",
            iconKind = SlotPrizeIconKind.EPIC_BLADE,
            rarity = SlotPrizeRarity.EPIC,
            weightOutOf1000 = 110,
            rewardSummary = "Forges a scaled Epic or Solar weapon/armor piece straight into your Gear Bag"
        ),
        // Index 9 — SOLAR (5.0%)
        SlotWheelPrize(
            id = "mythic_evolution_relic",
            title = "Mythic Core & Shards",
            shortWheelLabel = "MYTHIC CORE",
            symbol = "💎",
            iconKind = SlotPrizeIconKind.MYTHIC_CORE,
            rarity = SlotPrizeRarity.SOLAR,
            weightOutOf1000 = 50,
            rewardSummary = "+1 Mythic Core & +2 Evolution Shards for Stage 2/3 Pet Evolution"
        )
    )

    fun rollPrize(random: Random = Random.Default): Pair<Int, SlotWheelPrize> {
        val total = wheelPrizes.sumOf { it.weightOutOf1000 }.coerceAtLeast(1)
        val roll = random.nextInt(total)
        var cumulative = 0
        wheelPrizes.forEachIndexed { index, prize ->
            cumulative += prize.weightOutOf1000
            if (roll < cumulative) {
                return index to prize
            }
        }
        return 1 to wheelPrizes[1]
    }
}
