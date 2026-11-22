package com.example.domain

import com.example.data.model.ElementalAffinity
import com.example.data.model.HardcoreRpgState
import com.example.data.model.HeroProfile
import com.example.data.model.LootChest
import com.example.data.model.LootChestTier
import com.example.data.model.PetType
import com.example.data.model.WorldFogState
import kotlin.math.min
import kotlin.math.roundToInt

data class SleepSolCoinReward(
    val durationMinutes: Long,
    val sleepPercent: Int,
    val solCoinsEarned: Long
)

data class RealmSpec(
    val realmNumber: Int,
    val name: String,
    val biomeSubtitle: String,
    val recommendedLevel: Int,
    val elementalAffinity: ElementalAffinity,
    val guardianName: String,
    val guardianTitle: String,
    val minionNames: List<String>,
    val loreSnippet: String = "Ancient astrolabe monoliths hum with elemental power."
) {
    val title: String get() = name
    val subtitle: String get() = biomeSubtitle
    val affinity: ElementalAffinity get() = elementalAffinity
    val bossName: String get() = guardianName
}

enum class SkillBranch(val title: String, val subtitle: String) {
    SOLAR_OFFENSE("Solar Offense", "Amplify strikes, criticals & overdrive skills"),
    IRON_BASTION("Iron Bastion", "Fortify armor, health & counter-thorns"),
    WAYFARER_MASTERY("Wayfarer Mastery", "Boost step energy, expedition survival & rewards")
}

data class SkillNodeSpec(
    val index: Int,
    val id: String,
    val branch: SkillBranch,
    val name: String,
    val maxRank: Int,
    val perRankSummary: String
)

data class ComputedSkillTreeBonuses(
    val flatAttack: Int = 0,
    val flatDefense: Int = 0,
    val flatMaxHp: Int = 0,
    val flatMaxEnergy: Int = 0,
    val critChancePct: Int = 0,
    val armorPiercePct: Int = 0,
    val lifestealPct: Int = 0,
    val solarSurgePct: Int = 0,
    val damageReductionPct: Int = 0,
    val thornsPct: Int = 0,
    val turnRegenHp: Int = 0,
    val expeditionWardPct: Int = 0,
    val stepEnergyMultiplier: Float = 1.0f,
    val sunshardMultiplier: Float = 1.0f,
    val xpMultiplier: Float = 1.0f,
    val prestigeStatMultiplier: Float = 1.0f,
    val prestigeRewardMultiplier: Float = 1.0f,
    val prestigeExpeditionWardPct: Int = 0
)

enum class QuestCategory(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MILESTONE("Milestone")
}

data class QuestSpec(
    val id: String,
    val category: QuestCategory,
    val title: String,
    val description: String,
    val targetCount: Long,
    val sunshardReward: Long,
    val xpReward: Long,
    val solCoinReward: Long = 0L,
    val chestRewardTier: LootChestTier? = null,
    val chestUltraRarePct: Int = 0,
    val companionReward: PetType? = null
)

data class QuestStatus(
    val spec: QuestSpec,
    val currentProgress: Long,
    val isClaimed: Boolean,
    val claimKey: String
) {
    val isCompleted: Boolean get() = currentProgress >= spec.targetCount
    val canClaim: Boolean get() = isCompleted && !isClaimed
    val progressFraction: Float
        get() = (currentProgress.toFloat() / spec.targetCount.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
}

data class WayfarerEventSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val energyCost: Long,
    val solCoinCost: Long,
    val effectSummary: String
) {
    val actionLabel: String
        get() = when {
            solCoinCost > 0L -> "Wager ($solCoinCost Sol Coins)"
            energyCost > 0L -> "Activate ($energyCost Energy)"
            else -> "Activate"
        }
}

data class AlchemistRecipeSpec(
    val id: String,
    val name: String,
    val description: String,
    val herbCost: Long,
    val essenceCost: Long,
    val oreCost: Long,
    val solCoinCost: Long,
    val baseHealAmount: Int,
    val quantityProduced: Long,
    val curesWounds: Boolean = false,
    val grantsRune: Boolean = false
) {
    val healAmount: Long get() = baseHealAmount.toLong()
    val summary: String get() = description
    val grantsRandomRune: Boolean get() = grantsRune
}

data class BlackMarketOfferSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val solCoinCost: Long,
    val badgeText: String
)

data class BestiaryEntrySpec(
    val id: String,
    val name: String,
    val title: String,
    val realmOrModeLabel: String,
    val affinity: ElementalAffinity,
    val weakness: ElementalAffinity,
    val lore: String
) {
    val bossKey: String get() = id
    val bossName: String get() = name
    val realmNumber: Int
        get() = id.removePrefix("boss_realm_").toIntOrNull() ?: 10
}

data class PrestigeConditionStatus(
    val id: String,
    val title: String,
    val subtitle: String,
    val currentValue: Long,
    val targetValue: Long,
    val unitSuffix: String = ""
) {
    val isMet: Boolean get() = currentValue >= targetValue
    val progressFraction: Float
        get() = (currentValue.toFloat() / targetValue.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
}

data class PrestigeRequirementEvaluation(
    val nextPrestigeLevel: Long,
    val isMaxPrestigeReached: Boolean,
    val conditions: List<PrestigeConditionStatus>,
    val solCoinTributeCost: Long
) {
    val metCount: Int get() = conditions.count { it.isMet }
    val totalCount: Int get() = conditions.size
    val allConditionsMet: Boolean get() = !isMaxPrestigeReached && conditions.all { it.isMet }
    val firstUnmetCondition: PrestigeConditionStatus? get() = conditions.firstOrNull { !it.isMet }
}

object ProgressionEngine {
    const val SKILL_NODE_COUNT = 9
    const val MAX_REALM = 10L
    const val STAGES_PER_REALM = 5L
    const val PRESTIGE_MIN_LEVEL = 15L
    const val PRESTIGE_ALT_REALM_CLEARED = 3L
    const val MAX_PRESTIGE_LEVEL = 50L

    val realms: List<RealmSpec> = listOf(
        RealmSpec(
            realmNumber = 1,
            name = "Sunlit Dunes",
            biomeSubtitle = "Golden Limestone Badlands • Lv. 1+",
            recommendedLevel = 1,
            elementalAffinity = ElementalAffinity.SOLAR,
            guardianName = "Helios Colossus",
            guardianTitle = "Warden of the First Sundial",
            minionNames = listOf("Sunscorched Sentinel", "Sandstone Prowler", "Dune Construct"),
            loreSnippet = "A monolithic brass guardian awakened by the blinding noon rays."
        ),
        RealmSpec(
            realmNumber = 2,
            name = "Obsidian Rift",
            biomeSubtitle = "Volcanic Basalt Chasms • Lv. 4+",
            recommendedLevel = 4,
            elementalAffinity = ElementalAffinity.TERRA,
            guardianName = "Magma Wyrm Sovereign",
            guardianTitle = "Dread Wyrm of the Rift",
            minionNames = listOf("Obsidian Stalker", "Basalt Gargoyle", "Rift Reaver"),
            loreSnippet = "Coiled deep beneath fractured tectonic plates, feeding on molten sunsteel."
        ),
        RealmSpec(
            realmNumber = 3,
            name = "Sunken Astrolabe",
            biomeSubtitle = "Submerged Celestial Vaults • Lv. 8+",
            recommendedLevel = 8,
            elementalAffinity = ElementalAffinity.FROST,
            guardianName = "Chronos Leviathan",
            guardianTitle = "Keeper of Sunken Tides",
            minionNames = listOf("Astrolabe Automaton", "Tideforged Wraith", "Vault Myrmidon"),
            loreSnippet = "Its brass rings rotate in reverse, freezing intruders in glacial tides."
        ),
        RealmSpec(
            realmNumber = 4,
            name = "Cinder Highlands",
            biomeSubtitle = "Ash-Crowned Mesa Peaks • Lv. 12+",
            recommendedLevel = 12,
            elementalAffinity = ElementalAffinity.EMBER,
            guardianName = "Ignis Highlord",
            guardianTitle = "Warlord of the Ash Crown",
            minionNames = listOf("Cinder Drake", "Pyre Juggernaut", "Ashen Executioner"),
            loreSnippet = "Commands legions of ash-forged knights atop the burning caldera."
        ),
        RealmSpec(
            realmNumber = 5,
            name = "Zenith Citadel",
            biomeSubtitle = "Astral Sunspire Bastion • Lv. 16+",
            recommendedLevel = 16,
            elementalAffinity = ElementalAffinity.VOID,
            guardianName = "Aurelius the Eternal",
            guardianTitle = "Sovereign of the Sunspire",
            minionNames = listOf("Zenith Seraph", "Eclipse Inquisitor", "Astral Archon"),
            loreSnippet = "Once the First Strider King, now bound in eternal eclipse armor."
        ),
        RealmSpec(
            realmNumber = 6,
            name = "Frostspire Glacier",
            biomeSubtitle = "Shattered Permafrost Ridges • Lv. 20+",
            recommendedLevel = 20,
            elementalAffinity = ElementalAffinity.FROST,
            guardianName = "Boreas Frostweaver",
            guardianTitle = "Arch-Monarch of Eternal Winter",
            minionNames = listOf("Rimefang Wolf", "Glacial Construct", "Cryo Halberdier"),
            loreSnippet = "Freezes even solar fire into jagged blue permafrost crystals."
        ),
        RealmSpec(
            realmNumber = 7,
            name = "Emerald Canopy",
            biomeSubtitle = "Overgrown Sky-Root Sanctum • Lv. 25+",
            recommendedLevel = 25,
            elementalAffinity = ElementalAffinity.TERRA,
            guardianName = "Sylvanus Thornking",
            guardianTitle = "Ancient Root Behemoth",
            minionNames = listOf("Bramble Goliath", "Venomwing Wyvern", " Jade Warden"),
            loreSnippet = "Crushes iron armor beneath petrified sky-roots and venomous spores."
        ),
        RealmSpec(
            realmNumber = 8,
            name = "Voidfallen Catacombs",
            biomeSubtitle = "Abyssal Eclipse Necropolis • Lv. 30+",
            recommendedLevel = 30,
            elementalAffinity = ElementalAffinity.VOID,
            guardianName = "Malakor Voidreaver",
            guardianTitle = "Harbinger of the Black Sun",
            minionNames = listOf("Abyssal Dreadknight", "Shadow Siphoner", "Null Specter"),
            loreSnippet = "Drains the life force and solar energy of any strider who falters."
        ),
        RealmSpec(
            realmNumber = 9,
            name = "Stormglass Wastes",
            biomeSubtitle = "Fulgurite Lightning Flats • Lv. 36+",
            recommendedLevel = 36,
            elementalAffinity = ElementalAffinity.EMBER,
            guardianName = "Valerius Tempest",
            guardianTitle = "Fulmination Dragonlord",
            minionNames = listOf("Glasswing Chimera", "Plasma Centurion", "Thunder Myrmidon"),
            loreSnippet = "Where desert sand was fused into razor glass by relentless solar storms."
        ),
        RealmSpec(
            realmNumber = 10,
            name = "Celestial Solaris",
            biomeSubtitle = "Throne of the Astral Firmament • Lv. 42+",
            recommendedLevel = 42,
            elementalAffinity = ElementalAffinity.SOLAR,
            guardianName = "Sol Invictus Prime",
            guardianTitle = "Architect of the Eternal Stride",
            minionNames = listOf("Celestial Paladin", "Starforged Valkyrie", "Singularity Titan"),
            loreSnippet = "The supreme trial of Solstride—only true hardcore masters endure."
        )
    )

    val wayfarerEvents: List<WayfarerEventSpec> = listOf(
        WayfarerEventSpec(
            id = "shrine_dawn_blessing",
            title = "Shrine of Dawn Blessing",
            subtitle = "Channel Stride Energy into an ancient sundial altar",
            energyCost = 20L,
            solCoinCost = 0L,
            effectSummary = "Heals +35% Max HP & grants 'Dawn Empowered' (+15% ATK/DEF for 3 battles)"
        ),
        WayfarerEventSpec(
            id = "sunsteel_vein_harvest",
            title = "Sunsteel & Herb Prospecting",
            subtitle = "Excavate canyon veins using your accumulated Stride Energy",
            energyCost = 25L,
            solCoinCost = 0L,
            effectSummary = "Harvests +18 Sunsteel Ore, +14 Aether Herbs & +8 Elemental Essence"
        ),
        WayfarerEventSpec(
            id = "obsidian_gamble_altar",
            title = "Obsidian Blood-Oath Altar",
            subtitle = "Wager Sol Coins at the forbidden astrolabe monolith",
            energyCost = 0L,
            solCoinCost = 15L,
            effectSummary = "60% chance: +35 Sol Coins & +1 Elemental Rune • 40% chance: -20% HP & +40 Sunshards"
        )
    )

    val alchemistRecipes: List<AlchemistRecipeSpec> = listOf(
        AlchemistRecipeSpec(
            id = "brew_sunfire_draught",
            name = "Sunfire Healing Draught (x2)",
            description = "Potent herbal elixir that restores high HP mid-battle (3-turn Hardcore cooldown).",
            herbCost = 6L,
            essenceCost = 0L,
            oreCost = 0L,
            solCoinCost = 4L,
            baseHealAmount = 135,
            quantityProduced = 2L
        ),
        AlchemistRecipeSpec(
            id = "brew_ironbark_tonic",
            name = "Ironbark Bastion Tonic (x2)",
            description = "Concentrated oasis infusion restoring massive vitality during Boss Phase II.",
            herbCost = 10L,
            essenceCost = 2L,
            oreCost = 0L,
            solCoinCost = 8L,
            baseHealAmount = 240,
            quantityProduced = 2L
        ),
        AlchemistRecipeSpec(
            id = "brew_phoenix_panacea",
            name = "Phoenix Panacea & Wound Cure",
            description = "Immediately purges all Battle Wound stacks AND brews a supreme 420+ HP Panacea.",
            herbCost = 12L,
            essenceCost = 5L,
            oreCost = 0L,
            solCoinCost = 12L,
            baseHealAmount = 420,
            quantityProduced = 1L,
            curesWounds = true
        ),
        AlchemistRecipeSpec(
            id = "transmute_elemental_rune",
            name = "Distill Elemental Rune",
            description = "Fuses raw Sunsteel Ore, Aether Herbs, and Sol Coins into a socketable Elemental Rune.",
            herbCost = 8L,
            essenceCost = 6L,
            oreCost = 10L,
            solCoinCost = 10L,
            baseHealAmount = 0,
            quantityProduced = 1L,
            grantsRune = true
        )
    )

    val blackMarketOffers: List<BlackMarketOfferSpec> = listOf(
        BlackMarketOfferSpec(
            id = "bm_mystery_sovereign_cache",
            title = "Sovereign 6-Slot Mystery Cache",
            subtitle = "Gambles Sol Coins for an instant Epic (55%), Solar Relic (32%), or Ultra Rare (13%) 6-slot gear piece!",
            solCoinCost = 25L,
            badgeText = "HIGH-TIER GEAR"
        ),
        BlackMarketOfferSpec(
            id = "bm_elemental_rune_coffer",
            title = "Smuggler's Elemental Rune Coffer",
            subtitle = "Unlocks +2 random Socketable Elemental Runes & +12 Elemental Essence for the Forge.",
            solCoinCost = 15L,
            badgeText = "2x RUNES + ESSENCE"
        ),
        BlackMarketOfferSpec(
            id = "bm_forge_material_crate",
            title = "Sunsteel & Aether Supply Crate",
            subtitle = "Delivers +35 Sunsteel Ore, +28 Aether Herbs, +10 Elemental Essence & +50 Sunshards.",
            solCoinCost = 12L,
            badgeText = "CRAFTING BUNDLE"
        ),
        BlackMarketOfferSpec(
            id = "bm_physician_full_cure",
            title = "Sanctuary Physician's Surgery",
            subtitle = "Cures all Battle Wound stacks (-30% stat penalty removed) & restores 100% Hero HP.",
            solCoinCost = 10L,
            badgeText = "CURE WOUNDS + FULL HP"
        )
    )

    val bestiaryCatalog: List<BestiaryEntrySpec> = realms.map { r ->
        BestiaryEntrySpec(
            id = "boss_realm_${r.realmNumber}",
            name = r.guardianName,
            title = r.guardianTitle,
            realmOrModeLabel = "Realm ${r.realmNumber}: ${r.name}",
            affinity = r.elementalAffinity,
            weakness = ElementalAffinity.entries.first { it.beats() == r.elementalAffinity },
            lore = r.loreSnippet
        )
    } + listOf(
        BestiaryEntrySpec(
            id = "boss_spire_warden",
            name = "Spire Sovereign Warden",
            title = "Arch-Custodian of the Endless Gauntlet",
            realmOrModeLabel = "Endless Spire (Every 5th Floor)",
            affinity = ElementalAffinity.VOID,
            weakness = ElementalAffinity.SOLAR,
            lore = "Scales infinitely with Spire altitude, testing the limits of masterwork tempering."
        ),
        BestiaryEntrySpec(
            id = "boss_weekly_colossus",
            name = "Apophis World-Eater",
            title = "Cataclysmic Raid Titan",
            realmOrModeLabel = "Weekly Boss Raid",
            affinity = ElementalAffinity.EMBER,
            weakness = ElementalAffinity.VOID,
            lore = "An ancient multi-phase raid colossus that unleashes a lethal Doom Timer in Phase II."
        )
    )

    fun realmSpec(realmNumber: Int): RealmSpec {
        val idx = (realmNumber - 1).coerceIn(0, realms.lastIndex)
        return realms[idx]
    }

    fun realmForNumber(realmNumber: Int): RealmSpec = realmSpec(realmNumber)

    fun dailyRiftAffinity(dateKey: String): ElementalAffinity {
        val hash = kotlin.math.abs(dateKey.hashCode())
        return ElementalAffinity.entries[hash % ElementalAffinity.entries.size]
    }

    val skillNodes: List<SkillNodeSpec> = listOf(
        SkillNodeSpec(
            index = 0,
            id = "solar_might",
            branch = SkillBranch.SOLAR_OFFENSE,
            name = "Sunforged Edge",
            maxRank = 5,
            perRankSummary = "+4 Attack & +2% Crit Chance per rank"
        ),
        SkillNodeSpec(
            index = 1,
            id = "sundering_strikes",
            branch = SkillBranch.SOLAR_OFFENSE,
            name = "Sundering Precision",
            maxRank = 5,
            perRankSummary = "+4% Armor Pierce & +6% Solar Skill DMG per rank"
        ),
        SkillNodeSpec(
            index = 2,
            id = "sunblood_frenzy",
            branch = SkillBranch.SOLAR_OFFENSE,
            name = "Sunblood Siphon",
            maxRank = 5,
            perRankSummary = "+2% Lifesteal & +3 Attack per rank"
        ),
        SkillNodeSpec(
            index = 3,
            id = "bastion_bulwark",
            branch = SkillBranch.IRON_BASTION,
            name = "Obsidian Carapace",
            maxRank = 5,
            perRankSummary = "+3 Defense & +18 Max HP per rank"
        ),
        SkillNodeSpec(
            index = 4,
            id = "aegis_resilience",
            branch = SkillBranch.IRON_BASTION,
            name = "Aegis Ward",
            maxRank = 5,
            perRankSummary = "+2% Damage Reduction & +4% Thorns per rank"
        ),
        SkillNodeSpec(
            index = 5,
            id = "oasis_pulse",
            branch = SkillBranch.IRON_BASTION,
            name = "Oasis Renewal",
            maxRank = 5,
            perRankSummary = "+4 Turn Regen HP & +12 Max HP per rank"
        ),
        SkillNodeSpec(
            index = 6,
            id = "gale_cadence",
            branch = SkillBranch.WAYFARER_MASTERY,
            name = "Windrunner Cadence",
            maxRank = 5,
            perRankSummary = "+5% Step Energy Gain & +6 Max Energy per rank"
        ),
        SkillNodeSpec(
            index = 7,
            id = "pathfinder_ward",
            branch = SkillBranch.WAYFARER_MASTERY,
            name = "Pathfinder's Instinct",
            maxRank = 5,
            perRankSummary = "+3% Timed Expedition Survival Odds per rank"
        ),
        SkillNodeSpec(
            index = 8,
            id = "relic_prospector",
            branch = SkillBranch.WAYFARER_MASTERY,
            name = "Sunshard Prospector",
            maxRank = 5,
            perRankSummary = "+6% Sunshards & +6% XP Earned per rank"
        )
    )

    fun parseSkillRanks(raw: String): List<Int> {
        val tokens = raw.split(",")
        return List(SKILL_NODE_COUNT) { idx ->
            tokens.getOrNull(idx)?.trim()?.toIntOrNull()?.coerceIn(0, 5) ?: 0
        }
    }

    fun serializeSkillRanks(ranks: List<Int>): String {
        return List(SKILL_NODE_COUNT) { idx ->
            (ranks.getOrNull(idx) ?: 0).coerceIn(0, 5)
        }.joinToString(",")
    }

    const val REALM_FOG_FULL_CLEAR_STEPS: Int = 2000

    fun computeSkillTreeBonuses(profile: HeroProfile): ComputedSkillTreeBonuses {
        return ClassSkillCatalog.computeClassPassiveBonuses(profile)
    }

    /**
     * Computes dynamic Step & Sleep Fog-of-War RPG effects:
     * - Walking real steps clears the current Realm's mystic mist (0..2,000 steps = 0..100%),
     *   granting up to +10% Fog Clarity Crit Chance (boosted by Fog-Lantern Oil charges).
     * - Real-life Watch Sleep wraps the sanctuary in Dream-Mist and grants Rested Clarity
     *   (+10% Expedition Survival & +12% Battle XP for up to 3 battles) alongside max-50 Sol Coins.
     */
    fun computeWorldFogState(
        profile: HeroProfile,
        hardcoreState: HardcoreRpgState,
        todaySteps: Long = 0L,
        isHeroSleeping: Boolean = false
    ): WorldFogState {
        val realmNum = profile.currentRealm.toInt().coerceIn(1, MAX_REALM.toInt())
        val persistedRealmSteps = hardcoreState.realmFogSteps(realmNum)
        val effectiveSteps = maxOf(persistedRealmSteps, todaySteps.toInt().coerceAtMost(REALM_FOG_FULL_CLEAR_STEPS))
            .coerceIn(0, REALM_FOG_FULL_CLEAR_STEPS)

        val lanternActive = hardcoreState.fogLanternCharges > 0
        val baseClearPct = ((effectiveSteps.toFloat() / REALM_FOG_FULL_CLEAR_STEPS.toFloat()) * 100f)
            .roundToInt()
            .coerceIn(0, 100)
        val totalClearPct = (baseClearPct + if (lanternActive) 25 else 0).coerceIn(0, 100)

        // Visual fog stays gentle and atmospheric (0.08f sunlit haze to 0.68f mystic fog)
        val visualDensity = if (isHeroSleeping) {
            0.52f
        } else {
            (0.68f - (totalClearPct / 100f) * 0.60f).coerceIn(0.08f, 0.68f)
        }

        // Balanced RPG clarity bonuses
        val fogCritFromSteps = (totalClearPct / 10).coerceIn(0, 10)
        val lanternCritBonus = if (lanternActive) 8 else 0
        val fogCritTotal = (fogCritFromSteps + lanternCritBonus).coerceIn(0, 18)

        val restedActive = hardcoreState.restedClarityBattlesLeft > 0 || isHeroSleeping
        val restedRatio = if (isHeroSleeping) 1.0f else (hardcoreState.restedClarityPct.coerceIn(25, 100) / 100f)
        val restedWardBonus = (if (restedActive) (10f * restedRatio).roundToInt().coerceIn(3, 10) else 0) +
            (if (lanternActive) 6 else 0)
        val restedXpBonus = if (restedActive) (12f * restedRatio).roundToInt().coerceIn(4, 12) else 0

        val headline = when {
            isHeroSleeping -> "Watch Dream-Mist • Generating Sol Coins & Rested Clarity"
            lanternActive && totalClearPct >= 100 -> "Sunfire Lantern & Clear Skies • Peak Clarity (+${fogCritTotal}% Crit)"
            totalClearPct >= 100 -> "Realm $realmNum Fog Cleared • Sunlit Clarity (+${fogCritTotal}% Crit)"
            lanternActive -> "Fog-Lantern Lit (${hardcoreState.fogLanternCharges} left) • ${totalClearPct}% Clear"
            hardcoreState.restedClarityBattlesLeft > 0 -> "Rested Dream Clarity (${hardcoreState.restedClarityBattlesLeft} fights) • Fog ${totalClearPct}% Clear"
            totalClearPct >= 50 -> "Sunbeams Breaking Through • Realm Fog ${totalClearPct}% Cleared"
            else -> "Mystic Fog Veils Realm $realmNum • Walk to Dispel (${totalClearPct}%)"
        }

        return WorldFogState(
            realmNumber = realmNum,
            realmStepsWalked = effectiveSteps,
            realmStepsRequired = REALM_FOG_FULL_CLEAR_STEPS,
            realmFogClearedPct = totalClearPct,
            visualFogDensity = visualDensity,
            isDreamMistActive = isHeroSleeping || hardcoreState.restedClarityBattlesLeft > 0,
            restedClarityBattlesLeft = hardcoreState.restedClarityBattlesLeft,
            restedClarityPct = hardcoreState.restedClarityPct,
            fogLanternCharges = hardcoreState.fogLanternCharges,
            fogCritBonusPct = fogCritTotal,
            restedExpeditionWardBonusPct = restedWardBonus,
            restedXpBonusPct = restedXpBonus,
            statusHeadline = headline
        )
    }

    fun evaluatePrestigeRequirements(profile: HeroProfile): PrestigeRequirementEvaluation {
        val currentPrestige = profile.prestigeLevel.coerceAtLeast(0L)
        if (currentPrestige >= MAX_PRESTIGE_LEVEL) {
            return PrestigeRequirementEvaluation(
                nextPrestigeLevel = MAX_PRESTIGE_LEVEL,
                isMaxPrestigeReached = true,
                conditions = emptyList(),
                solCoinTributeCost = 0L
            )
        }

        val reqLevel = (PRESTIGE_MIN_LEVEL + currentPrestige * 3L).coerceAtMost(60L)
        val reqRealm = (PRESTIGE_ALT_REALM_CLEARED + currentPrestige).coerceAtMost(MAX_REALM)
        val reqRealmName = realms.firstOrNull { it.realmNumber == reqRealm.toInt() }?.name ?: "Realm $reqRealm"
        val reqBosses = 3L + currentPrestige * 3L
        val reqSpireFloor = (3L + currentPrestige * 2L).coerceAtMost(50L)
        val reqSteps = 5_000L + currentPrestige * 5_000L
        val tributeCost = (40L + currentPrestige * 20L).coerceAtMost(300L)

        val conditions = listOf(
            PrestigeConditionStatus(
                id = "hero_level",
                title = "Hero Level $reqLevel",
                subtitle = "Reach Level $reqLevel on this run (resets to Lv.1 on Ascension)",
                currentValue = profile.level.coerceAtLeast(1L),
                targetValue = reqLevel,
                unitSuffix = "Lv"
            ),
            PrestigeConditionStatus(
                id = "realm_conquest",
                title = "Clear Realm $reqRealm ($reqRealmName)",
                subtitle = "Defeat the Realm $reqRealm Guardian in Campaign",
                currentValue = profile.highestRealmCleared.coerceAtLeast(0L),
                targetValue = reqRealm,
                unitSuffix = "Realms"
            ),
            PrestigeConditionStatus(
                id = "true_bosses",
                title = "Guardian Slayer ($reqBosses Bosses)",
                subtitle = "Defeat $reqBosses Realm or True Bosses",
                currentValue = profile.trueBossWins.coerceAtLeast(0L),
                targetValue = reqBosses,
                unitSuffix = "Wins"
            ),
            PrestigeConditionStatus(
                id = "spire_floor",
                title = "Sunspire Floor $reqSpireFloor",
                subtitle = "Clear Floor $reqSpireFloor in the Endless Sunspire",
                currentValue = profile.highestSpireFloor.coerceAtLeast(0L),
                targetValue = reqSpireFloor,
                unitSuffix = "Floor"
            ),
            PrestigeConditionStatus(
                id = "pilgrim_steps",
                title = "Wayfarer Pilgrimage",
                subtitle = "Walk ${String.format(java.util.Locale.US, "%,d", reqSteps)} lifetime steps",
                currentValue = profile.totalSteps.coerceAtLeast(0L),
                targetValue = reqSteps,
                unitSuffix = "Steps"
            ),
            PrestigeConditionStatus(
                id = "sol_tribute",
                title = "Ascension Tribute ($tributeCost Sol Coins)",
                subtitle = "Spent to ignite the Solar Ascension altar",
                currentValue = profile.solCoins.coerceAtLeast(0L),
                targetValue = tributeCost,
                unitSuffix = "Coins"
            )
        )

        return PrestigeRequirementEvaluation(
            nextPrestigeLevel = currentPrestige + 1L,
            isMaxPrestigeReached = false,
            conditions = conditions,
            solCoinTributeCost = tributeCost
        )
    }

    fun canPrestige(profile: HeroProfile): Boolean {
        return evaluatePrestigeRequirements(profile).allConditionsMet
    }

    fun prestigeRomanNumeral(level: Int): String = when {
        level <= 0 -> ""
        level == 1 -> "I"
        level == 2 -> "II"
        level == 3 -> "III"
        level == 4 -> "IV"
        level == 5 -> "V"
        level == 6 -> "VI"
        level == 7 -> "VII"
        level == 8 -> "VIII"
        level == 9 -> "IX"
        level == 10 -> "X"
        else -> "$level"
    }

    val questCatalog: List<QuestSpec> = listOf(
        QuestSpec(
            id = "daily_fog_steps",
            category = QuestCategory.DAILY,
            title = "Dispel the Dawn Fog",
            description = "Walk 2,000 steps today to clear Realm Fog & sharpen Crit Clarity",
            targetCount = 2000L,
            sunshardReward = 25L,
            xpReward = 85L,
            solCoinReward = 18L
        ),
        QuestSpec(
            id = "daily_battles_2",
            category = QuestCategory.DAILY,
            title = "Sunhaven Skirmisher",
            description = "Win 2 tactical battles today",
            targetCount = 2L,
            sunshardReward = 22L,
            xpReward = 70L,
            solCoinReward = 15L,
            chestRewardTier = LootChestTier.HIGHLAND,
            chestUltraRarePct = 15
        ),
        QuestSpec(
            id = "daily_expedition_1",
            category = QuestCategory.DAILY,
            title = "Dawn Recon Bounty",
            description = "Complete 1 Timed Expedition today",
            targetCount = 1L,
            sunshardReward = 30L,
            xpReward = 95L,
            solCoinReward = 16L,
            chestRewardTier = LootChestTier.EXPEDITION,
            chestUltraRarePct = 25
        ),
        QuestSpec(
            id = "daily_chests_1",
            category = QuestCategory.DAILY,
            title = "Vault Unsealer",
            description = "Unseal 1 single-use Treasure Chest today",
            targetCount = 1L,
            sunshardReward = 20L,
            xpReward = 60L,
            solCoinReward = 12L
        ),
        QuestSpec(
            id = "weekly_steps_5k",
            category = QuestCategory.WEEKLY,
            title = "Highland Stride",
            description = "Walk 5,000 steps this week with your smart watch or phone",
            targetCount = 5000L,
            sunshardReward = 70L,
            xpReward = 220L,
            solCoinReward = 45L,
            chestRewardTier = LootChestTier.BOSS_VAULT,
            chestUltraRarePct = 35
        ),
        QuestSpec(
            id = "weekly_steps_15k",
            category = QuestCategory.WEEKLY,
            title = "Sunspire Marathon",
            description = "Walk 15,000 steps this week",
            targetCount = 15000L,
            sunshardReward = 140L,
            xpReward = 450L,
            solCoinReward = 85L,
            chestRewardTier = LootChestTier.SPIRE_SOVEREIGN,
            chestUltraRarePct = 55
        ),
        QuestSpec(
            id = "milestone_level_5",
            category = QuestCategory.MILESTONE,
            title = "Awakened Class Arts",
            description = "Reach Hero Level 5 to unlock your 2nd Active Class Skill",
            targetCount = 5L,
            sunshardReward = 50L,
            xpReward = 140L,
            solCoinReward = 35L
        ),
        QuestSpec(
            id = "milestone_level_8",
            category = QuestCategory.MILESTONE,
            title = "Highland Falconer",
            description = "Reach Hero Level 8 to recruit the Epic Dune Falcon companion",
            targetCount = 8L,
            sunshardReward = 80L,
            xpReward = 210L,
            solCoinReward = 45L,
            companionReward = PetType.DUNE_FALCON
        ),
        QuestSpec(
            id = "milestone_level_12",
            category = QuestCategory.MILESTONE,
            title = "Master of Sunhaven",
            description = "Reach Hero Level 12 to unlock your 3rd Signature Skill & earn Cinder Lynx",
            targetCount = 12L,
            sunshardReward = 115L,
            xpReward = 320L,
            solCoinReward = 75L,
            chestRewardTier = LootChestTier.BOSS_VAULT,
            chestUltraRarePct = 40,
            companionReward = PetType.CINDER_LYNX
        ),
        QuestSpec(
            id = "milestone_level_18",
            category = QuestCategory.MILESTONE,
            title = "Keeper of the Sunlit Oasis",
            description = "Reach Hero Level 18 to recruit the Solar Oasis Tortoise companion",
            targetCount = 18L,
            sunshardReward = 190L,
            xpReward = 480L,
            solCoinReward = 100L,
            chestRewardTier = LootChestTier.BOSS_VAULT,
            chestUltraRarePct = 55,
            companionReward = PetType.OASIS_TORTOISE
        ),
        QuestSpec(
            id = "milestone_level_25",
            category = QuestCategory.MILESTONE,
            title = "Zenith Phoenix Ascendant",
            description = "Reach Hero Level 25 to awaken the Legendary Solar Phoenix companion",
            targetCount = 25L,
            sunshardReward = 320L,
            xpReward = 750L,
            solCoinReward = 150L,
            chestRewardTier = LootChestTier.SPIRE_SOVEREIGN,
            chestUltraRarePct = 80,
            companionReward = PetType.SOLAR_PHOENIX
        ),
        QuestSpec(
            id = "milestone_realm_1",
            category = QuestCategory.MILESTONE,
            title = "Conqueror of Sunlit Dunes",
            description = "Clear Realm 1 (Defeat Helios Colossus at Stage 5)",
            targetCount = 1L,
            sunshardReward = 60L,
            xpReward = 200L,
            solCoinReward = 40L,
            chestRewardTier = LootChestTier.BOSS_VAULT,
            chestUltraRarePct = 35
        ),
        QuestSpec(
            id = "milestone_realm_5",
            category = QuestCategory.MILESTONE,
            title = "Sovereign of the Zenith Citadel",
            description = "Clear Realm 5 (Defeat Aurelius the Eternal)",
            targetCount = 5L,
            sunshardReward = 180L,
            xpReward = 600L,
            solCoinReward = 120L,
            chestRewardTier = LootChestTier.SPIRE_SOVEREIGN,
            chestUltraRarePct = 75
        ),
        QuestSpec(
            id = "milestone_realm_10",
            category = QuestCategory.MILESTONE,
            title = "Ascendant of Celestial Solaris",
            description = "Clear Realm 10 (Defeat Sol Invictus Prime)",
            targetCount = 10L,
            sunshardReward = 450L,
            xpReward = 1500L,
            solCoinReward = 250L,
            chestRewardTier = LootChestTier.SPIRE_SOVEREIGN,
            chestUltraRarePct = 100
        ),
        QuestSpec(
            id = "milestone_spire_5",
            category = QuestCategory.MILESTONE,
            title = "Spire Challenger",
            description = "Clear Floor 5 of the Endless Spire Gauntlet",
            targetCount = 5L,
            sunshardReward = 85L,
            xpReward = 260L,
            solCoinReward = 55L,
            chestRewardTier = LootChestTier.SPIRE_SOVEREIGN,
            chestUltraRarePct = 45
        ),
        QuestSpec(
            id = "milestone_spire_15",
            category = QuestCategory.MILESTONE,
            title = "Spire Sovereign",
            description = "Clear Floor 15 of the Endless Spire Gauntlet",
            targetCount = 15L,
            sunshardReward = 210L,
            xpReward = 650L,
            solCoinReward = 130L,
            chestRewardTier = LootChestTier.SPIRE_SOVEREIGN,
            chestUltraRarePct = 85
        ),
        QuestSpec(
            id = "milestone_bosses_5",
            category = QuestCategory.MILESTONE,
            title = "Bane of Guardians",
            description = "Win 5 True Boss Battles",
            targetCount = 5L,
            sunshardReward = 100L,
            xpReward = 300L,
            solCoinReward = 60L,
            chestRewardTier = LootChestTier.BOSS_VAULT,
            chestUltraRarePct = 50
        )
    )

    fun evaluateQuests(
        profile: HeroProfile,
        todayKey: String,
        todaySteps: Long = 0L,
        hardcoreState: HardcoreRpgState = HardcoreRpgState()
    ): List<QuestStatus> {
        val isSameDay = profile.dailyQuestDateKey == todayKey
        val dailyBattles = if (isSameDay) profile.dailyBattlesWon else 0L
        val dailyExps = if (isSameDay) profile.dailyExpeditionsDone else 0L
        val dailyChests = if (isSameDay) profile.dailyChestsOpened else 0L
        val realmSteps = hardcoreState.realmFogSteps(profile.currentRealm.toInt()).toLong()
        val dailyFogProgress = maxOf(todaySteps, realmSteps)

        return questCatalog.map { spec ->
            val claimKey = when (spec.category) {
                QuestCategory.DAILY -> "${spec.id}_$todayKey"
                QuestCategory.WEEKLY -> "${spec.id}_${profile.weekKey}"
                QuestCategory.MILESTONE -> spec.id
            }
            val current = when (spec.id) {
                "daily_fog_steps" -> dailyFogProgress
                "daily_battles_2" -> dailyBattles
                "daily_expedition_1" -> dailyExps
                "daily_chests_1" -> dailyChests
                "weekly_steps_5k", "weekly_steps_15k" -> profile.weeklySteps
                "milestone_level_5", "milestone_level_8", "milestone_level_12", "milestone_level_18", "milestone_level_25" -> profile.level
                "milestone_realm_1", "milestone_realm_5", "milestone_realm_10" -> profile.highestRealmCleared
                "milestone_spire_5", "milestone_spire_15" -> profile.highestSpireFloor
                "milestone_bosses_5" -> profile.trueBossWins
                else -> 0L
            }
            val clamped = min(current, spec.targetCount)
            QuestStatus(
                spec = spec,
                currentProgress = clamped,
                isClaimed = profile.claimedQuestIds.contains(claimKey),
                claimKey = claimKey
            )
        }
    }

    fun pruneAndAppendClaimedQuest(
        existingClaims: List<String>,
        newClaimKey: String,
        todayKey: String,
        weekKey: String
    ): List<String> {
        val filtered = existingClaims.filter { key ->
            when {
                key.startsWith("daily_") -> key.endsWith("_$todayKey")
                key.startsWith("weekly_") -> key.endsWith("_$weekKey")
                else -> true
            }
        }
        return (filtered + newClaimKey).distinct().takeLast(45)
    }

    fun buildQuestChest(
        userId: String,
        profile: HeroProfile,
        spec: QuestSpec
    ): LootChest? {
        val tier = spec.chestRewardTier ?: return null
        return LootChest(
            userId = userId,
            chestTier = tier.name,
            sourceLabel = "Quest: ${spec.title}",
            heroLevelSnapshot = profile.level.coerceAtLeast(1L),
            durationMinutes = if (tier == LootChestTier.EXPEDITION) 60L else 0L,
            ultraRareChancePct = spec.chestUltraRarePct.toLong().coerceIn(10L, 100L)
        )
    }

    const val FULL_SLEEP_TARGET_MINUTES: Long = 480L // 8 hours = 100% sleep
    const val MIN_CREDITED_SLEEP_MINUTES: Long = 10L
    const val MAX_SLEEP_SOL_COINS: Long = 50L

    /**
     * Real-life watch sleep grants Sol Coins proportional to percent of a full 8-hour sleep,
     * strictly capped at a maximum gain of 50 Sol Coins per sleep session.
     */
    fun computeSleepSolCoinReward(durationMinutes: Long): SleepSolCoinReward {
        val clampedMin = durationMinutes.coerceAtLeast(0L)
        val fraction = (clampedMin.toFloat() / FULL_SLEEP_TARGET_MINUTES.toFloat()).coerceIn(0f, 1f)
        val sleepPct = (fraction * 100f).roundToInt().coerceIn(0, 100)
        val coins = if (clampedMin < MIN_CREDITED_SLEEP_MINUTES) {
            0L
        } else {
            (fraction * MAX_SLEEP_SOL_COINS.toFloat())
                .roundToInt()
                .toLong()
                .coerceIn(1L, MAX_SLEEP_SOL_COINS)
        }
        return SleepSolCoinReward(
            durationMinutes = clampedMin,
            sleepPercent = sleepPct,
            solCoinsEarned = coins
        )
    }

    /**
     * Steps exclusively level the Hero (Hero XP) and fuel the Companion Pet Arena Widget.
     * Converts synced step deltas into Hero XP (1 XP per 4 steps, scaled by Hero XP multiplier).
     */
    fun computeHeroStepXp(stepDelta: Long, xpMultiplier: Float = 1.0f): Long {
        if (stepDelta <= 0L) return 0L
        val raw = (stepDelta.toFloat() / 4.0f) * xpMultiplier.coerceAtLeast(1.0f)
        return raw.roundToInt().toLong().coerceAtLeast(1L)
    }

    fun applyStepXpToProfile(
        profile: HeroProfile,
        xpGain: Long,
        baseMaxHp: Long = profile.maxHp,
        baseHeroPower: Long = profile.heroPower
    ): HeroProfile {
        var lvl = profile.level.coerceAtLeast(1L)
        var xp = profile.xp + xpGain.coerceAtLeast(0L)
        var needed = profile.xpToNextLevel.coerceAtLeast(100L)
        var points = profile.unspentPoints
        var maxHp = baseMaxHp.coerceAtLeast(100L)
        var hp = profile.currentHp
        var power = baseHeroPower.coerceAtLeast(100L)

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
}
