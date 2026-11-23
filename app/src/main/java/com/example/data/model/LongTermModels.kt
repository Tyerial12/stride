package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.abs
import kotlin.math.max

/**
 * Version stamp used to trigger the one-time v2.0 Fresh Journey progression restart
 * (resetting RPG progression while strictly preserving all tracked steps).
 */
const val CURRENT_MIGRATION_VERSION = 2L
const val DEFAULT_TOWN_BUILDING_LEVELS = "1,1,1,1,1"

data class FreshJourneyResetBanner(
    val preservedLifetimeSteps: Long,
    val preservedWeeklySteps: Long,
    val heroName: String
) {
    val preservedTotalSteps: Long get() = preservedLifetimeSteps
}

// ============================================================================
// 1. 3-STAGE BRANCHING PET EVOLUTION SYSTEM
// ============================================================================

enum class PetEvolutionBranch(
    val id: String,
    val label: String,
    val tagline: String,
    val accentHex: Long
) {
    NONE("NONE", "Base Form", "Awakens at Level 15", 0xFFD97706L),
    VANGUARD(
        id = "VANGUARD",
        label = "Solar Vanguard",
        tagline = "Radiant offense, high crit & solar burst power",
        accentHex = 0xFFF59E0BL
    ),
    MYSTIC(
        id = "MYSTIC",
        label = "Astral Mystic",
        tagline = "Elemental control, Aegis barriers & step-energy surge",
        accentHex = 0xFF38BDF8L
    );

    companion object {
        fun fromRaw(raw: String?): PetEvolutionBranch =
            entries.firstOrNull { it.id == raw || it.name == raw } ?: NONE
    }
}

data class PetEvolutionFormSpec(
    val petType: PetType,
    val stage: Int,
    val branch: PetEvolutionBranch,
    val formName: String,
    val formTitle: String,
    val lore: String,
    val signatureSkillName: String,
    val signatureSkillSummary: String,
    val burstDamageMult: Float,
    val burstHealPct: Float,
    val burstBarrierPct: Float,
    val statScaleMultiplier: Float,
    val primaryColorHex: Long,
    val auraColorHex: Long
) {
    val title: String get() = formTitle
    val burstSkillName: String get() = signatureSkillName
    val burstSkillDescription: String get() = signatureSkillSummary
    val auraHex: Long get() = auraColorHex
}

data class PetEvolutionRequirement(
    val targetStage: Int,
    val minLevel: Long,
    val minBondXp: Long,
    val minStepsWalked: Long,
    val evolutionShardsNeeded: Long,
    val mythicCoresNeeded: Long,
    val minTrueBossWins: Long,
    val minRealmReached: Long = 1L,
    val minMenagerieLevel: Int = 1,
    val solCoinTribute: Long = 0L,
    val sunshardTribute: Long = 0L
) {
    val minPetLevel: Long get() = minLevel
    val minStepsWithPet: Long get() = minStepsWalked
    val evolutionShardsCost: Long get() = evolutionShardsNeeded
    val mythicCoresCost: Long get() = mythicCoresNeeded
}

object PetEvolutionCatalog {
    const val MAX_BOND_XP = 3_500L
    const val DAILY_CARE_LIMIT = 4

    // Stage 2: Branching Awakening — multi-week companion dedication
    const val STAGE_2_LEVEL = 22L
    const val STAGE_2_BOND = 1_200L
    const val STAGE_2_STEPS = 15_000L
    const val STAGE_2_SHARDS = 8L
    const val STAGE_2_BOSS_WINS = 6L
    const val STAGE_2_REALM = 3L
    const val STAGE_2_MENAGERIE_LV = 2
    const val STAGE_2_SOL_COINS = 75L
    const val STAGE_2_SUNSHARDS = 400L

    // Stage 3: Apex Mythic Ascension — true long-term endgame prestige
    const val STAGE_3_LEVEL = 42L
    const val STAGE_3_BOND = 3_500L
    const val STAGE_3_STEPS = 50_000L
    const val STAGE_3_SHARDS = 12L
    const val STAGE_3_CORES = 5L
    const val STAGE_3_BOSS_WINS = 20L
    const val STAGE_3_REALM = 6L
    const val STAGE_3_MENAGERIE_LV = 4
    const val STAGE_3_SOL_COINS = 250L
    const val STAGE_3_SUNSHARDS = 1_500L

    fun requirementForNextStage(currentStage: Int): PetEvolutionRequirement? = when (currentStage) {
        1 -> PetEvolutionRequirement(
            targetStage = 2,
            minLevel = STAGE_2_LEVEL,
            minBondXp = STAGE_2_BOND,
            minStepsWalked = STAGE_2_STEPS,
            evolutionShardsNeeded = STAGE_2_SHARDS,
            mythicCoresNeeded = 0L,
            minTrueBossWins = STAGE_2_BOSS_WINS,
            minRealmReached = STAGE_2_REALM,
            minMenagerieLevel = STAGE_2_MENAGERIE_LV,
            solCoinTribute = STAGE_2_SOL_COINS,
            sunshardTribute = STAGE_2_SUNSHARDS
        )
        2 -> PetEvolutionRequirement(
            targetStage = 3,
            minLevel = STAGE_3_LEVEL,
            minBondXp = STAGE_3_BOND,
            minStepsWalked = STAGE_3_STEPS,
            evolutionShardsNeeded = STAGE_3_SHARDS,
            mythicCoresNeeded = STAGE_3_CORES,
            minTrueBossWins = STAGE_3_BOSS_WINS,
            minRealmReached = STAGE_3_REALM,
            minMenagerieLevel = STAGE_3_MENAGERIE_LV,
            solCoinTribute = STAGE_3_SOL_COINS,
            sunshardTribute = STAGE_3_SUNSHARDS
        )
        else -> null
    }

    fun nextRequirement(currentStage: Int): PetEvolutionRequirement? = requirementForNextStage(currentStage)

    fun formFor(
        petType: PetType,
        stage: Int,
        branch: PetEvolutionBranch = PetEvolutionBranch.NONE
    ): PetEvolutionFormSpec = resolveForm(petType, stage, branch)

    fun bondPercent(bondXp: Long): Int = ((bondXp.coerceIn(0L, MAX_BOND_XP) * 100L) / MAX_BOND_XP).toInt()

    fun resolveForm(
        petType: PetType,
        stage: Int,
        branch: PetEvolutionBranch
    ): PetEvolutionFormSpec {
        val safeStage = stage.coerceIn(1, 3)
        val effectiveBranch = if (safeStage <= 1) PetEvolutionBranch.NONE else {
            if (branch == PetEvolutionBranch.NONE) PetEvolutionBranch.VANGUARD else branch
        }
        return when (petType) {
            PetType.SUNFOX_KIT -> when (safeStage) {
                1 -> PetEvolutionFormSpec(
                    petType, 1, PetEvolutionBranch.NONE,
                    "Sunfox Kit", "Canyon Ember Scout", petType.lore,
                    "Ember Volley", "160% Ember DMG + Burn & +15 EN",
                    1.60f, 0.08f, 0.0f, 1.0f, 0xFFFF9F1CL, 0xFFFFD166L
                )
                2 -> if (effectiveBranch == PetEvolutionBranch.VANGUARD) {
                    PetEvolutionFormSpec(
                        petType, 2, PetEvolutionBranch.VANGUARD,
                        "Flaretail Reynard", "Sunblaze Vanguard",
                        "Its twin tails burn with forged sunfire, igniting every canyon trail.",
                        "Solar Flare Blitz", "225% Ember DMG + Scorch Burn & +20% Crit",
                        2.25f, 0.10f, 0.08f, 1.38f, 0xFFFF6B00L, 0xFFFFB800L
                    )
                } else {
                    PetEvolutionFormSpec(
                        petType, 2, PetEvolutionBranch.MYSTIC,
                        "Mirage Kitsune", "Astral Dune Seer",
                        "Weaves shimmering desert illusions that refill Stride Energy from moonlight.",
                        "Astral Mirage Nova", "195% Solar DMG + 22% Aegis Barrier & +30 EN",
                        1.95f, 0.14f, 0.22f, 1.35f, 0xFF38BDF8L, 0xFFA855F7L
                    )
                }
                else -> if (effectiveBranch == PetEvolutionBranch.VANGUARD) {
                    PetEvolutionFormSpec(
                        petType, 3, PetEvolutionBranch.VANGUARD,
                        "Helios Nine-Tail Sovereign", "Apex Sunfire Mythic",
                        "Nine blazing ribbons of living sunlight crown the ultimate canyon guardian.",
                        "Corona Nine-Tail Cataclysm", "310% Ember DMG + Guaranteed Crit, Burn & Sunder",
                        3.10f, 0.16f, 0.15f, 1.85f, 0xFFFF4D00L, 0xFFFFD700L
                    )
                } else {
                    PetEvolutionFormSpec(
                        petType, 3, PetEvolutionBranch.MYSTIC,
                        "Celestial Astral Kitsune", "Apex Starlight Mythic",
                        "Orbits starlight sigils that bend enemy strikes and overflow Stride Energy.",
                        "Starfall Sanctuary Veil", "270% Solar DMG + 36% Aegis Barrier, Regen & +45 EN",
                        2.70f, 0.24f, 0.36f, 1.82f, 0xFF818CF8L, 0xFF22D3EEL
                    )
                }
            }
            PetType.AEGIS_SCARAB -> when (safeStage) {
                1 -> PetEvolutionFormSpec(
                    petType, 1, PetEvolutionBranch.NONE,
                    "Aegis Scarab", "Obsidian Carapace Ward", petType.lore,
                    "Obsidian Bulwark Slam", "145% Terra DMG + 30% Barrier & Sunder",
                    1.45f, 0.15f, 0.30f, 1.0f, 0xFF38BDF8L, 0xFF60A5FAL
                )
                2 -> if (effectiveBranch == PetEvolutionBranch.VANGUARD) {
                    PetEvolutionFormSpec(
                        petType, 2, PetEvolutionBranch.VANGUARD,
                        "Sunsteel Goliath Beetle", "Siegehorn Juggernaut",
                        "Grows a massive gilded ramming horn capable of shattering boss armor.",
                        "Gilded Horn Ram", "215% Terra DMG + Sunder, Stun chance & 28% Barrier",
                        2.15f, 0.14f, 0.28f, 1.38f, 0xFFF59E0BL, 0xFFD97706L
                    )
                } else {
                    PetEvolutionFormSpec(
                        petType, 2, PetEvolutionBranch.MYSTIC,
                        "Prism Runescarab", "Astral Bastion Keeper",
                        "Etched with crystalline runes that turn incoming hits into shimmering shields.",
                        "Prismatic Citadel Pulse", "185% Terra DMG + 42% Aegis Barrier & Frostbite",
                        1.85f, 0.18f, 0.42f, 1.36f, 0xFF06B6D4L, 0xFF818CF8L
                    )
                }
                else -> if (effectiveBranch == PetEvolutionBranch.VANGUARD) {
                    PetEvolutionFormSpec(
                        petType, 3, PetEvolutionBranch.VANGUARD,
                        "Imperator Titan Scarab", "Apex Siege Mythic",
                        "A winged golden colossus whose carapace rings like the Sunhaven bell.",
                        "Sovereign Monolith Crash", "295% Terra DMG + Stun, Sunder & 40% Barrier",
                        2.95f, 0.20f, 0.40f, 1.85f, 0xFFEAB308L, 0xFFFF7A00L
                    )
                } else {
                    PetEvolutionFormSpec(
                        petType, 3, PetEvolutionBranch.MYSTIC,
                        "Aetherion Astral Scarab", "Apex Sanctuary Mythic",
                        "Hovers on twin crystal rings projecting an unbreakable astral dome.",
                        "Aetherion Invictus Dome", "255% Solar DMG + 55% Aegis Barrier & Full Regen",
                        2.55f, 0.28f, 0.55f, 1.82f, 0xFF22D3EEL, 0xFFC084FCL
                    )
                }
            }
            PetType.DUNE_FALCON -> when (safeStage) {
                1 -> PetEvolutionFormSpec(
                    petType, 1, PetEvolutionBranch.NONE,
                    "Dune Falcon", "Highland Windrider", petType.lore,
                    "Glacial Gale Dive", "175% Frost DMG + Frostbite & +25 EN",
                    1.75f, 0.10f, 0.0f, 1.0f, 0xFFFDE047L, 0xFF38BDF8L
                )
                2 -> if (effectiveBranch == PetEvolutionBranch.VANGUARD) {
                    PetEvolutionFormSpec(
                        petType, 2, PetEvolutionBranch.VANGUARD,
                        "Stormtalon Roc", "Solar Gale Striker",
                        "Dives at supersonic speed, tearing through shields with radiant talons.",
                        "Sunstrike Divebomb", "235% Frost DMG + Armor Pierce & +25 EN",
                        2.35f, 0.12f, 0.10f, 1.38f, 0xFFFBBF24L, 0xFFF97316L
                    )
                } else {
                    PetEvolutionFormSpec(
                        petType, 2, PetEvolutionBranch.MYSTIC,
                        "Zephyr Frosthawk", "Aurora Windweaver",
                        "Leaves crystalline contrails that freeze enemy momentum mid-swing.",
                        "Aurora Tempest", "205% Frost DMG + Frostbite, 24% Barrier & +35 EN",
                        2.05f, 0.15f, 0.24f, 1.36f, 0xFF38BDF8L, 0xFF67E8F9L
                    )
                }
                else -> if (effectiveBranch == PetEvolutionBranch.VANGUARD) {
                    PetEvolutionFormSpec(
                        petType, 3, PetEvolutionBranch.VANGUARD,
                        "Skybreaker Sun-Griffin", "Apex Tempest Mythic",
                        "A crowned solar raptor commanding thunderous updrafts across ten realms.",
                        "Skybreaker Hyper-Dive", "315% Solar DMG + Guaranteed Crit & +40 EN",
                        3.15f, 0.18f, 0.18f, 1.85f, 0xFFFFB800L, 0xFF38BDF8L
                    )
                } else {
                    PetEvolutionFormSpec(
                        petType, 3, PetEvolutionBranch.MYSTIC,
                        "Borealis Star-Falcon", "Apex Aurora Mythic",
                        "Wings spun from polar aurora light that multiply every real-world stride.",
                        "Borealis Absolute Zero", "275% Frost DMG + Stun, Frostbite & +55 EN",
                        2.75f, 0.22f, 0.34f, 1.82f, 0xFF22D3EEL, 0xFFA5B4FCL
                    )
                }
            }
            PetType.CINDER_LYNX -> when (safeStage) {
                1 -> PetEvolutionFormSpec(
                    petType, 1, PetEvolutionBranch.NONE,
                    "Cinder Lynx", "Fierce Apex Stalker", petType.lore,
                    "Eclipse Predator Rend", "210% Void DMG + Poison & 20% Leech",
                    2.10f, 0.14f, 0.0f, 1.0f, 0xFFF97316L, 0xFFE11D48L
                )
                2 -> if (effectiveBranch == PetEvolutionBranch.VANGUARD) {
                    PetEvolutionFormSpec(
                        petType, 2, PetEvolutionBranch.VANGUARD,
                        "Pyremane Sabertooth", "Crimson Flame Hunter",
                        "Blazing fangs carve molten scars through heavily armored bosses.",
                        "Infernal Fang Flurry", "255% Ember DMG + Guaranteed Crit & Burn",
                        2.55f, 0.16f, 0.08f, 1.40f, 0xFFEA580CL, 0xFFFF4D6DL
                    )
                } else {
                    PetEvolutionFormSpec(
                        petType, 2, PetEvolutionBranch.MYSTIC,
                        "Umbral Voidpanther", "Eclipse Shadow Stalker",
                        "Phases through attacks in a veil of violet mist, siphoning vitality.",
                        "Eclipse Phantom Pounce", "230% Void DMG + Poison, 26% Heal & 20% Barrier",
                        2.30f, 0.26f, 0.20f, 1.38f, 0xFF9333EAL, 0xFFC084FCL
                    )
                }
                else -> if (effectiveBranch == PetEvolutionBranch.VANGUARD) {
                    PetEvolutionFormSpec(
                        petType, 3, PetEvolutionBranch.VANGUARD,
                        "Solflare Apex Lion", "Apex Ember Mythic",
                        "A mane of roaring solar plasma erupts around this legendary apex hunter.",
                        "Supernova Sovereign Mauling", "340% Ember DMG + Guaranteed Crit, Sunder & Leech",
                        3.40f, 0.22f, 0.15f, 1.88f, 0xFFFF3D00L, 0xFFFFD166L
                    )
                } else {
                    PetEvolutionFormSpec(
                        petType, 3, PetEvolutionBranch.MYSTIC,
                        "Nightcrown Astral Panther", "Apex Void Mythic",
                        "A constellation-cloaked predator that devours enemy shields on contact.",
                        "Void Eclipse Execution", "310% Void DMG + Lethal Poison, 34% Heal & Barrier",
                        3.10f, 0.34f, 0.28f, 1.85f, 0xFF7C3AEDL, 0xFFE879F9L
                    )
                }
            }
            PetType.OASIS_TORTOISE -> when (safeStage) {
                1 -> PetEvolutionFormSpec(
                    petType, 1, PetEvolutionBranch.NONE,
                    "Oasis Tortoise", "Ancient Vitality Keeper", petType.lore,
                    "Sanctuary Oasis Wave", "140% Terra DMG + 32% HP Heal & Regen",
                    1.40f, 0.32f, 0.12f, 1.0f, 0xFF34D399L, 0xFF10B981L
                )
                2 -> if (effectiveBranch == PetEvolutionBranch.VANGUARD) {
                    PetEvolutionFormSpec(
                        petType, 2, PetEvolutionBranch.VANGUARD,
                        "Sunspire Bastionback", "Golden Grove Warden",
                        "A sunlit obelisk rises from its mossy shell, radiating fierce vitality.",
                        "Bastion Quake Surge", "205% Terra DMG + 35% Heal, Sunder & 25% Barrier",
                        2.05f, 0.35f, 0.25f, 1.38f, 0xFF10B981L, 0xFFF59E0BL
                    )
                } else {
                    PetEvolutionFormSpec(
                        petType, 2, PetEvolutionBranch.MYSTIC,
                        "Tidebloom Spirit-Shell", "Astral Spring Guardian",
                        "Carries an enchanted floating spring that purifies wounds and restores mana.",
                        "Tidebloom Renewal", "185% Frost DMG + 44% HP Heal, Regen & +30 EN",
                        1.85f, 0.44f, 0.28f, 1.36f, 0xFF14B8A6L, 0xFF38BDF8L
                    )
                }
                else -> if (effectiveBranch == PetEvolutionBranch.VANGUARD) {
                    PetEvolutionFormSpec(
                        petType, 3, PetEvolutionBranch.VANGUARD,
                        "Worldshell Sun-Titan", "Apex Terra Mythic",
                        "Bears a gleaming citadel garden on its shell, unmoved by any cataclysm.",
                        "Continental Aegis Slam", "285% Terra DMG + 42% Heal, Stun & 45% Barrier",
                        2.85f, 0.42f, 0.45f, 1.85f, 0xFF059669L, 0xFFFFD166L
                    )
                } else {
                    PetEvolutionFormSpec(
                        petType, 3, PetEvolutionBranch.MYSTIC,
                        "Eternity Oasis Leviathan", "Apex Sanctuary Mythic",
                        "Floats upon luminous emerald waterfalls, granting near-immortal sustain.",
                        "Eternal Oasis Awakening", "255% Solar DMG + 58% HP Heal, Regen & 40% Barrier",
                        2.55f, 0.58f, 0.40f, 1.85f, 0xFF2DD4BFL, 0xFF818CF8L
                    )
                }
            }
            PetType.SOLAR_PHOENIX -> when (safeStage) {
                1 -> PetEvolutionFormSpec(
                    petType, 1, PetEvolutionBranch.NONE,
                    "Solar Phoenix", "Zenith Ascendant", petType.lore,
                    "Zenith Supernova Rebirth", "250% Solar DMG + Burn, Stun & 25% Heal",
                    2.50f, 0.25f, 0.10f, 1.0f, 0xFFFF4D6DL, 0xFFFFB800L
                )
                2 -> if (effectiveBranch == PetEvolutionBranch.VANGUARD) {
                    PetEvolutionFormSpec(
                        petType, 2, PetEvolutionBranch.VANGUARD,
                        "Radiant Seraph Phoenix", "Dawnfire Vanguard",
                        "Four wings of molten gold blaze bright enough to pierce any Realm Fog.",
                        "Dawnfire Solar Judgment", "300% Solar DMG + Burn, Sunder & 30% Heal",
                        3.00f, 0.30f, 0.20f, 1.42f, 0xFFFF6B00L, 0xFFFFD700L
                    )
                } else {
                    PetEvolutionFormSpec(
                        petType, 2, PetEvolutionBranch.MYSTIC,
                        "Nebula Starfire Phoenix", "Astral Eclipse Weaver",
                        "Feathers woven from violet cosmic flames that restore HP, Barrier, and Energy.",
                        "Nebula Rebirth Cascade", "275% Void DMG + 36% Heal, 35% Barrier & +35 EN",
                        2.75f, 0.36f, 0.35f, 1.40f, 0xFFC084FCL, 0xFF38BDF8L
                    )
                }
                else -> if (effectiveBranch == PetEvolutionBranch.VANGUARD) {
                    PetEvolutionFormSpec(
                        petType, 3, PetEvolutionBranch.VANGUARD,
                        "Empyrean Sun-God Phoenix", "Apex Zenith Mythic",
                        "Crowned in a miniature star, heralding unstoppable Solar Ascension.",
                        "Empyrean Zenith Supernova", "380% Solar DMG + Guaranteed Crit, Stun, Burn & 38% Heal",
                        3.80f, 0.38f, 0.30f, 1.95f, 0xFFFFB800L, 0xFFFF3D00L
                    )
                } else {
                    PetEvolutionFormSpec(
                        petType, 3, PetEvolutionBranch.MYSTIC,
                        "Chronos Astral Phoenix", "Apex Cosmic Mythic",
                        "Transcends time with prism wings, showering its hero in endless astral energy.",
                        "Chronos Star-Rebirth", "345% Solar DMG + 48% Heal, 48% Barrier & +60 EN",
                        3.45f, 0.48f, 0.48f, 1.92f, 0xFF818CF8L, 0xFFF472B6L
                    )
                }
            }
        }
    }
}

// ============================================================================
// 2. UPGRADEABLE SUNHAVEN TOWN BUILDINGS (LEVEL 1..5)
// ============================================================================

enum class TownBuildingId(
    val index: Int,
    val title: String,
    val npcRole: TownNpcRole,
    val perkPerLevelSummary: String,
    val accentHex: Long
) {
    FORGE(
        index = 0,
        title = "Torin's Sun-Forge",
        npcRole = TownNpcRole.BLACKSMITH,
        perkPerLevelSummary = "+4% Gear Attack & +4% Masterwork Forge Bonus per level",
        accentHex = 0xFFE11D48L
    ),
    MENAGERIE(
        index = 1,
        title = "Pip's Menagerie",
        npcRole = TownNpcRole.PET_STORE,
        perkPerLevelSummary = "+12% Pet Bond XP & unlocks Stage 2/3 Evolution Catalysts",
        accentHex = 0xFFF59E0BL
    ),
    MARKET(
        index = 2,
        title = "Zari's Grand Market",
        npcRole = TownNpcRole.MATERIALS_MERCHANT,
        perkPerLevelSummary = "+1 Daily Merchant Stock & +5% Sunshard Drops per level",
        accentHex = 0xFF0D9488L
    ),
    TRAINING_HALL(
        index = 3,
        title = "Kaelen's Training Hall",
        npcRole = TownNpcRole.SKILL_MENTOR,
        perkPerLevelSummary = "+5% Hero XP & +3% Crit Chance per level",
        accentHex = 0xFF7C3AEDL
    ),
    ALCHEMIST_SPIRE(
        index = 4,
        title = "Sable's Alchemist Spire",
        npcRole = TownNpcRole.ALCHEMIST_SPIRE,
        perkPerLevelSummary = "+5% Max HP & +4% Elixir Potency per level",
        accentHex = 0xFF10B981L
    );

    fun bonusDescription(level: Int): String {
        val above = (level - 1).coerceAtLeast(0)
        return when (this) {
            FORGE -> "Active: +${above * 4}% Attack & +${above * 4}% Forge Power • $perkPerLevelSummary"
            MENAGERIE -> "Active: +${above * 12}% Pet Bond XP • $perkPerLevelSummary"
            MARKET -> "Active: +$above Daily Stock & +${above * 5}% Sunshards • $perkPerLevelSummary"
            TRAINING_HALL -> "Active: +${above * 5}% Hero XP & +${above * 3}% Crit • $perkPerLevelSummary"
            ALCHEMIST_SPIRE -> "Active: +${above * 5}% Max HP & +${above * 4}% Brew Potency • $perkPerLevelSummary"
        }
    }
}

data class TownBuildingUpgradeCost(
    val nextLevel: Int,
    val solCoins: Long,
    val sunshards: Long = 0L,
    val oreNeeded: Long,
    val herbsNeeded: Long,
    val essenceNeeded: Long,
    val lifetimeStepsNeeded: Long
) {
    val oreCost: Long get() = oreNeeded
}

data class TownBuildingBonuses(
    val atkPct: Int = 0,
    val hpPct: Int = 0,
    val critPct: Int = 0,
    val xpBonusPct: Int = 0,
    val sunshardBonusPct: Int = 0,
    val bondXpBonusPct: Int = 0,
    val extraMerchantStock: Int = 0,
    val masterworkBonusPct: Int = 0,
    val totalBuildingRank: Int = 5
)

object TownBuildingCatalog {
    const val MAX_BUILDING_LEVEL = 5

    fun parseLevels(raw: String?): List<Int> {
        val parts = raw.orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }
        return List(TownBuildingId.entries.size) { idx ->
            (parts.getOrNull(idx) ?: 1).coerceIn(1, MAX_BUILDING_LEVEL)
        }
    }

    fun serializeLevels(levels: List<Int>): String =
        List(TownBuildingId.entries.size) { idx ->
            (levels.getOrNull(idx) ?: 1).coerceIn(1, MAX_BUILDING_LEVEL)
        }.joinToString(",")

    fun levelOf(rawLevels: String?, building: TownBuildingId): Int =
        parseLevels(rawLevels).getOrElse(building.index) { 1 }

    fun levelOf(profile: HeroProfile, building: TownBuildingId): Int =
        levelOf(profile.townBuildingLevels, building)

    fun upgradeCost(currentLevel: Int): TownBuildingUpgradeCost? =
        upgradeCostFor(TownBuildingId.FORGE, currentLevel)

    fun upgradeCostFor(building: TownBuildingId, currentLevel: Int): TownBuildingUpgradeCost? {
        if (currentLevel >= MAX_BUILDING_LEVEL) return null
        val next = currentLevel + 1
        val coinCost = 20L * next * next
        val shardCost = 60L * next * next
        val matScale = (next - 1).toLong()
        return TownBuildingUpgradeCost(
            nextLevel = next,
            solCoins = coinCost,
            sunshards = shardCost,
            oreNeeded = if (building == TownBuildingId.FORGE || building == TownBuildingId.MARKET) 12L * matScale else 6L * matScale,
            herbsNeeded = if (building == TownBuildingId.ALCHEMIST_SPIRE || building == TownBuildingId.MENAGERIE) 12L * matScale else 5L * matScale,
            essenceNeeded = 5L * matScale,
            lifetimeStepsNeeded = 1500L * matScale * matScale
        )
    }

    fun computeBonuses(profile: HeroProfile): TownBuildingBonuses {
        val levels = parseLevels(profile.townBuildingLevels)
        val forgeAbove = (levels[TownBuildingId.FORGE.index] - 1).coerceAtLeast(0)
        val menagerieAbove = (levels[TownBuildingId.MENAGERIE.index] - 1).coerceAtLeast(0)
        val marketAbove = (levels[TownBuildingId.MARKET.index] - 1).coerceAtLeast(0)
        val hallAbove = (levels[TownBuildingId.TRAINING_HALL.index] - 1).coerceAtLeast(0)
        val spireAbove = (levels[TownBuildingId.ALCHEMIST_SPIRE.index] - 1).coerceAtLeast(0)

        return TownBuildingBonuses(
            atkPct = forgeAbove * 4,
            hpPct = spireAbove * 5,
            critPct = hallAbove * 3,
            xpBonusPct = hallAbove * 5,
            sunshardBonusPct = marketAbove * 5,
            bondXpBonusPct = menagerieAbove * 12,
            extraMerchantStock = marketAbove,
            masterworkBonusPct = forgeAbove * 4,
            totalBuildingRank = levels.sum()
        )
    }
}

// ============================================================================
// 3. LIVE WORLD EVENTS, SEASONS, MASTERIES, RELIC SETS & LEAGUES
// ============================================================================

enum class LiveWorldEventSpec(
    val title: String,
    val subtitle: String,
    val badge: String,
    val stepEnergyBonusPct: Int = 0,
    val shardDropBonusPct: Int = 0,
    val petBondBonusPct: Int = 0,
    val bossCoinBonusPct: Int = 0,
    val accentHex: Long
) {
    SOLAR_ECLIPSE_SURGE(
        title = "Solar Eclipse Surge",
        subtitle = "+35% Evolution Shard drops in Wilds & Boss Battles",
        badge = "ECLIPSE",
        shardDropBonusPct = 35,
        accentHex = 0xFFA855F7L
    ),
    GOLDEN_CARAVAN(
        title = "Golden Caravan Festival",
        subtitle = "+30% Step Energy & +25% Boss Sol Coin payouts",
        badge = "CARAVAN",
        stepEnergyBonusPct = 30,
        bossCoinBonusPct = 25,
        accentHex = 0xFFF59E0BL
    ),
    ASTRAL_BLOOM(
        title = "Astral Bond Festival",
        subtitle = "+40% Pet Bond & Companion XP from Care and Walks",
        badge = "BOND",
        petBondBonusPct = 40,
        accentHex = 0xFF38BDF8L
    ),
    SUNFORGED_TIDE(
        title = "Sunforged Relic Tide",
        subtitle = "+20% Step Energy & +25% Sunshard / Wilds Chest drops",
        badge = "RELICS",
        stepEnergyBonusPct = 20,
        shardDropBonusPct = 25,
        accentHex = 0xFF10B981L
    );

    val bonusSummary: String get() = subtitle

    companion object {
        private const val ROTATION_WINDOW_MS = 4L * 60L * 60L * 1000L

        fun activeForDate(dateKey: String): LiveWorldEventSpec {
            val hash = abs(dateKey.hashCode())
            return entries[hash % entries.size]
        }

        fun currentEventWithRemainingMinutes(nowMs: Long = System.currentTimeMillis()): Pair<LiveWorldEventSpec, Int> {
            val slot = (nowMs / ROTATION_WINDOW_MS).toInt()
            val event = entries[abs(slot) % entries.size]
            val elapsedInSlot = nowMs % ROTATION_WINDOW_MS
            val remainingMin = ((ROTATION_WINDOW_MS - elapsedInSlot) / 60_000L).toInt().coerceAtLeast(1)
            return event to remainingMin
        }
    }
}

data class SeasonTierSpec(
    val tier: Int,
    val xpRequired: Long,
    val rewardLabel: String,
    val solCoins: Long = 0L,
    val sunshards: Long = 0L,
    val evolutionShards: Long = 0L,
    val mythicCores: Long = 0L,
    val oreGrant: Long = 0L,
    val grantBossChest: Boolean = false
)

object SeasonCatalog {
    const val CURRENT_SEASON_KEY = "S1_DAWN_EVOLUTION"
    const val CURRENT_SEASON_TITLE = "Season I: Dawn of Evolution"
    const val MAX_TIERS = 30

    val tiers: List<SeasonTierSpec> = (1..MAX_TIERS).map { tier ->
        val xpReq = tier * 250L + (tier - 1) * 50L
        when {
            tier % 10 == 0 -> SeasonTierSpec(
                tier = tier,
                xpRequired = xpReq,
                rewardLabel = "+1 Mythic Apex Core, +2 Evo Shards & +55 Sol Coins",
                mythicCores = 1L,
                evolutionShards = 2L,
                solCoins = 55L,
                grantBossChest = false
            )
            tier % 5 == 0 -> SeasonTierSpec(
                tier = tier,
                xpRequired = xpReq,
                rewardLabel = "+2 Evolution Shards & +30 Sol Coins",
                solCoins = 30L,
                evolutionShards = 2L
            )
            tier % 3 == 0 -> SeasonTierSpec(
                tier = tier,
                xpRequired = xpReq,
                rewardLabel = "+1 Evolution Shard & +15 Sunsteel Ore",
                evolutionShards = 1L,
                oreGrant = 15L
            )
            else -> SeasonTierSpec(
                tier = tier,
                xpRequired = xpReq,
                rewardLabel = "+${10 + tier * 2} Sol Coins & +${35 + tier * 10} Sunshards",
                solCoins = (10 + tier * 2).toLong(),
                sunshards = (35 + tier * 10).toLong()
            )
        }
    }

    fun currentTierForXp(seasonXp: Long): Int =
        tiers.lastOrNull { seasonXp >= it.xpRequired }?.tier ?: 0
}

enum class ActivityMasteryType(
    val id: String,
    val title: String,
    val bonusPerLevelSummary: String,
    val accentHex: Long
) {
    COMBAT("COMBAT", "Combat Mastery", "+2% Attack & +1% Crit per level", 0xFFE11D48L),
    EXPLORATION("EXPLORATION", "Wilds Exploration", "+2% Step Energy & +2% Sunshards per level", 0xFF0D9488L),
    SMITHING("SMITHING", "Anvil Smithing", "+2% Defense & +3% Masterwork Forge power per level", 0xFFF59E0BL),
    ALCHEMY("ALCHEMY", "Cauldron Alchemy", "+2% Max HP & +3% Elixir healing per level", 0xFF10B981L),
    COMPANION("COMPANION", "Companion Bond", "+3% Pet Stat Scaling & Bond XP per level", 0xFF8B5CF6L);

    companion object {
        fun levelForXp(xp: Int): Int {
            val safe = xp.coerceAtLeast(0)
            var lvl = 1
            var remaining = safe
            while (lvl < 25) {
                val needed = 100 + (lvl - 1) * 75
                if (remaining < needed) break
                remaining -= needed
                lvl += 1
            }
            return lvl
        }

        fun progressFractionForXp(xp: Int): Float {
            val safe = xp.coerceAtLeast(0)
            var lvl = 1
            var remaining = safe
            while (lvl < 25) {
                val needed = 100 + (lvl - 1) * 75
                if (remaining < needed) {
                    return (remaining.toFloat() / needed.toFloat()).coerceIn(0f, 1f)
                }
                remaining -= needed
                lvl += 1
            }
            return 1f
        }
    }
}

enum class RelicSetSpec(
    val id: String,
    val title: String,
    val pieces: List<String>,
    val twoPieceBonusLabel: String,
    val fourPieceBonusLabel: String,
    val twoPieceAtkPct: Int,
    val twoPieceDefPct: Int,
    val fourPieceHpPct: Int,
    val fourPieceCritPct: Int,
    val accentHex: Long
) {
    SUNFORGED_DAWN(
        id = "SUNFORGED_DAWN",
        title = "Sunforged Dawn",
        pieces = listOf("dawn_crest", "dawn_seal", "dawn_chalice", "dawn_diadem"),
        twoPieceBonusLabel = "+4% ATK & +2% DEF",
        fourPieceBonusLabel = "+8% Max HP & +4% Crit",
        twoPieceAtkPct = 4, twoPieceDefPct = 2, fourPieceHpPct = 8, fourPieceCritPct = 4,
        accentHex = 0xFFF59E0BL
    ),
    DUNE_PHANTOM(
        id = "DUNE_PHANTOM",
        title = "Dune Phantom",
        pieces = listOf("dune_compass", "dune_veil", "dune_hourglass", "dune_signet"),
        twoPieceBonusLabel = "+5% ATK",
        fourPieceBonusLabel = "+5% Crit & +6% Max HP",
        twoPieceAtkPct = 5, twoPieceDefPct = 0, fourPieceHpPct = 6, fourPieceCritPct = 5,
        accentHex = 0xFF0D9488L
    ),
    OBSIDIAN_CITADEL(
        id = "OBSIDIAN_CITADEL",
        title = "Obsidian Citadel",
        pieces = listOf("obs_bulwark", "obs_key", "obs_tablet", "obs_heart"),
        twoPieceBonusLabel = "+6% DEF",
        fourPieceBonusLabel = "+10% Max HP & +3% Crit",
        twoPieceAtkPct = 0, twoPieceDefPct = 6, fourPieceHpPct = 10, fourPieceCritPct = 3,
        accentHex = 0xFF64748BL
    ),
    ASTRAL_TIDE(
        id = "ASTRAL_TIDE",
        title = "Astral Tide",
        pieces = listOf("ast_astrolabe", "ast_prism", "ast_bell", "ast_orb"),
        twoPieceBonusLabel = "+3% ATK & +3% DEF",
        fourPieceBonusLabel = "+7% Max HP & +5% Crit",
        twoPieceAtkPct = 3, twoPieceDefPct = 3, fourPieceHpPct = 7, fourPieceCritPct = 5,
        accentHex = 0xFF38BDF8L
    ),
    CINDER_REIGN(
        id = "CINDER_REIGN",
        title = "Cinder Reign",
        pieces = listOf("cin_ember", "cin_brand", "cin_fang", "cin_crown"),
        twoPieceBonusLabel = "+6% ATK",
        fourPieceBonusLabel = "+6% Crit & +5% Max HP",
        twoPieceAtkPct = 6, twoPieceDefPct = 0, fourPieceHpPct = 5, fourPieceCritPct = 6,
        accentHex = 0xFFE11D48L
    ),
    ZENITH_CROWN(
        id = "ZENITH_CROWN",
        title = "Zenith Sovereign",
        pieces = listOf("zen_feather", "zen_scepter", "zen_mirror", "zen_star"),
        twoPieceBonusLabel = "+5% ATK & +5% DEF",
        fourPieceBonusLabel = "+12% Max HP & +6% Crit",
        twoPieceAtkPct = 5, twoPieceDefPct = 5, fourPieceHpPct = 12, fourPieceCritPct = 6,
        accentHex = 0xFFA855F7L
    );

    companion object {
        val allPieceIds: List<String> = entries.flatMap { it.pieces }

        fun pieceDisplayName(pieceId: String): String = pieceId
            .substringAfter("_")
            .replaceFirstChar { it.uppercase() }

        fun nextUncollectedRelic(collected: Set<String>): Pair<RelicSetSpec, String>? {
            for (set in entries) {
                val missing = set.pieces.firstOrNull { it !in collected }
                if (missing != null) return set to missing
            }
            return null
        }
    }
}

enum class SeasonalLeagueTier(
    val title: String,
    val minScore: Long,
    val accentHex: Long
) {
    BRONZE_STRIDER("Bronze Strider", 0L, 0xFFB45309L),
    SILVER_WAYFARER("Silver Wayfarer", 3_500L, 0xFF94A3B8L),
    GOLD_VANGUARD("Gold Vanguard", 10_000L, 0xFFF59E0BL),
    OBSIDIAN_WARDEN("Obsidian Warden", 25_000L, 0xFF8B5CF6L),
    SUNFORGED_LEGEND("Sunforged Legend", 50_000L, 0xFFEF4444L);

    companion object {
        fun computeLeagueScore(weeklySteps: Long, highestSpireFloor: Long, bossesDefeated: Long): Long =
            weeklySteps + (highestSpireFloor * 600L) + (bossesDefeated * 180L)

        fun forScore(score: Long): SeasonalLeagueTier =
            entries.lastOrNull { score >= it.minScore } ?: BRONZE_STRIDER
    }
}

enum class BestiaryMasteryRank(val label: String, val minKills: Int, val bonusDmgPct: Int) {
    UNSEEN("Unseen", 0, 0),
    BRONZE("Bronze", 1, 3),
    SILVER("Silver", 5, 6),
    GOLD("Gold", 12, 10),
    APEX("Apex", 25, 16);

    companion object {
        fun forKills(kills: Int): BestiaryMasteryRank =
            entries.lastOrNull { kills >= it.minKills } ?: UNSEEN
    }
}

// ============================================================================
// 4. INTERACTIVE MINI-GAMES & ACTIVE TIMING COMBAT GRADES
// ============================================================================

enum class ParryGrade(val label: String, val damageNegatedPct: Int, val comboBonus: Int, val burstGain: Int) {
    PERFECT("PERFECT PARRY!", 85, 1, 22),
    GLANCING("GLANCING DODGE", 45, 0, 10),
    MISSED("DIRECT HIT", 0, 0, 4)
}

enum class ForgeGrade(val label: String, val statBonusMultiplier: Float, val masteryXp: Int) {
    STANDARD("Standard Forging", 1.00f, 18),
    TEMPERED("Tempered Edge (+8%)", 1.08f, 34),
    MASTERWORK("MASTERWORK (+18%!)", 1.18f, 60)
}

enum class BrewGrade(val label: String, val potencyMultiplier: Float, val extraQuantity: Int, val masteryXp: Int) {
    STANDARD("Standard Brew", 1.0f, 0, 16),
    POTENT("Potent Elixir (+25% Heal)", 1.25f, 0, 32),
    DOUBLE_BREW("MASTER DOUBLE BREW (2x!)", 1.20f, 1, 55)
}

// ============================================================================
// 5. WALKABLE 2D REALM WILDS FIELD MODEL
// ============================================================================

enum class WildsNodeType(val label: String, val actionVerb: String, val accentHex: Long) {
    CHEST("Supply Cache", "Salvage Cache", 0xFFF59E0BL),
    ORE_VEIN("Sunsteel Vein", "Mine Ore", 0xFF94A3B8L),
    HERB_GROVE("Aether Grove", "Gather Herbs", 0xFF10B981L),
    EVOLUTION_CRYSTAL("Evolution Crystal", "Harvest Shard", 0xFF38BDF8L),
    SHRINE("Ancient Shrine", "Claim Blessing", 0xFFA855F7L),
    ROAMING_FOE("Roaming Stalker", "Ambush Foe", 0xFFE11D48L),
    GUARDIAN("Realm Guardian", "Challenge Boss", 0xFFFF4D00L)
}

data class WildsFieldNode(
    val id: String,
    val type: WildsNodeType,
    val posX: Float,
    val posY: Float,
    val label: String,
    val harvested: Boolean = false
)

object WildsFieldGenerator {
    fun generateNodesForRealm(
        realmNumber: Int,
        dateKey: String,
        harvestedKeys: Set<String>
    ): List<WildsFieldNode> {
        val seed = abs("$realmNumber:$dateKey".hashCode())
        fun jitter(idx: Int, base: Float, range: Float): Float {
            val v = ((seed + idx * 73) % 100) / 100f
            return (base + (v - 0.5f) * range).coerceIn(0.12f, 0.88f)
        }
        val templates = listOf(
            Triple("chest_1", WildsNodeType.CHEST, 0.22f to 0.30f),
            Triple("ore_1", WildsNodeType.ORE_VEIN, 0.76f to 0.28f),
            Triple("herb_1", WildsNodeType.HERB_GROVE, 0.24f to 0.74f),
            Triple("evo_1", WildsNodeType.EVOLUTION_CRYSTAL, 0.52f to 0.26f),
            Triple("shrine_1", WildsNodeType.SHRINE, 0.78f to 0.72f),
            Triple("foe_1", WildsNodeType.ROAMING_FOE, 0.44f to 0.62f),
            Triple("boss_1", WildsNodeType.GUARDIAN, 0.84f to 0.48f)
        )
        return templates.mapIndexed { idx, (suffix, type, basePos) ->
            val key = "r${realmNumber}_${dateKey}_$suffix"
            WildsFieldNode(
                id = key,
                type = type,
                posX = jitter(idx * 2 + 1, basePos.first, 0.10f),
                posY = jitter(idx * 2 + 2, basePos.second, 0.10f),
                label = type.label,
                harvested = key in harvestedKeys && type != WildsNodeType.ROAMING_FOE && type != WildsNodeType.GUARDIAN
            )
        }
    }
}

// ============================================================================
// 6. FIRESTORE MODELS: WEEKLY WORLD BOSS, CO-OP RAIDS & STEP-POWERED GUILDS
// ============================================================================

@Keep
@IgnoreExtraProperties
data class WeeklyWorldBoss(
    @DocumentId val id: String = "",
    val weekKey: String = "",
    val bossName: String = "Apophis World-Eater",
    val elementalAffinity: String = ElementalAffinity.EMBER.name,
    val maxHp: Long = 250_000L,
    val currentHp: Long = 250_000L,
    val totalStrikes: Long = 0L,
    val publicVisible: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedAffinity(): ElementalAffinity =
        ElementalAffinity.entries.firstOrNull { it.name == elementalAffinity } ?: ElementalAffinity.EMBER
}

@Keep
@IgnoreExtraProperties
data class WorldBossContribution(
    @DocumentId val id: String = "",
    val userId: String = "",
    val weekKey: String = "",
    val displayName: String = "Strider",
    val totalDamage: Long = 0L,
    val strikesCount: Long = 0L,
    val claimedMilestoneTier: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CoopRaidRoom(
    @DocumentId val id: String = "",
    val hostUid: String = "",
    val hostName: String = "Strider",
    val bossName: String = "Solaris Raid Titan",
    val bossAffinity: String = ElementalAffinity.SOLAR.name,
    val bossMaxHp: Long = 12_000L,
    val bossCurrentHp: Long = 12_000L,
    val participantUids: List<String> = emptyList(),
    val participantNames: List<String> = emptyList(),
    val status: String = "OPEN", // OPEN, ACTIVE, VICTORY
    val totalTeamDamage: Long = 0L,
    val lastActionLog: String = "",
    val claimedUids: List<String> = emptyList(),
    val publicVisible: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedAffinity(): ElementalAffinity =
        ElementalAffinity.entries.firstOrNull { it.name == bossAffinity } ?: ElementalAffinity.SOLAR
}

@Keep
@IgnoreExtraProperties
data class SolstrideGuild(
    @DocumentId val id: String = "",
    val name: String = "Sunhaven Vanguard",
    val motto: String = "Every step lights the dawn.",
    val emblemHex: Long = 0xFFF59E0BL,
    val leaderUid: String = "",
    val leaderName: String = "Strider",
    val memberUids: List<String> = emptyList(),
    val memberNames: List<String> = emptyList(),
    val weekKey: String = "",
    val weeklyGuildSteps: Long = 0L,
    val weeklyStepGoal: Long = 50_000L,
    val weeklyBossDamage: Long = 0L,
    val guildLevel: Long = 1L,
    val publicVisible: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GuildChatMessage(
    @DocumentId val id: String = "",
    val guildId: String = "",
    val userId: String = "",
    val displayName: String = "Strider",
    val heroLevel: Long = 1L,
    val message: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

object WeeklyWorldBossCatalog {
    data class BossTemplate(val name: String, val affinity: ElementalAffinity, val maxHp: Long)

    private val rotation = listOf(
        BossTemplate("Apophis World-Eater", ElementalAffinity.EMBER, 250_000L),
        BossTemplate("Borealis Frost Leviathan", ElementalAffinity.FROST, 260_000L),
        BossTemplate("Aetherion Void Sovereign", ElementalAffinity.VOID, 275_000L),
        BossTemplate("Monolith Earth Colossus", ElementalAffinity.TERRA, 265_000L),
        BossTemplate("Helios Zenith Archon", ElementalAffinity.SOLAR, 280_000L)
    )

    fun templateForWeek(weekKey: String): BossTemplate {
        val idx = abs(weekKey.hashCode()) % rotation.size
        return rotation[idx]
    }

    val milestoneThresholds: List<Pair<Long, Long>> = listOf(
        1_500L to 15L,    // 1.5k DMG -> 15 Sol Coins
        5_000L to 25L,    // 5k DMG -> 25 Sol Coins + 1 Evo Shard
        15_000L to 40L,   // 15k DMG -> 40 Sol Coins + 2 Evo Shards
        35_000L to 65L    // 35k DMG -> 65 Sol Coins + 1 Mythic Core
    )

    fun sunspireWeeklyMutator(weekKey: String): Pair<String, String> {
        val mutators = listOf(
            "Solar Flare" to "Enemies deal +15% DMG, but Perfect Parries deal 2x Counter DMG!",
            "Ironhide Bastion" to "Spire foes begin with +20% Shield; Armor Pierce & Sunder shine.",
            "Quickened Tempo" to "+25% Pet Burst & Valor charge speed on every Strike.",
            "Astral Harvest" to "+40% Sunshards & bonus Evolution Shards every 5th Floor."
        )
        return mutators[abs(weekKey.hashCode()) % mutators.size]
    }
}

// ============================================================================
// 7. STEP & SLEEP REAL-LIFE RPG EXPANSION: DREAM TIERS, STEP DUNGEONS,
//    CONSTELLATION TALENTS, GEAR AFFIXES & STEP-FUELED SLAYER CONTRACTS
// ============================================================================

enum class DreamRestTier(
    val id: String,
    val title: String,
    val subtitle: String,
    val minSleepMinutes: Int,
    val staminaRestorePct: Int,
    val stepEnergyMultiplier: Float,
    val bonusXpPct: Int,
    val bonusCritPct: Int,
    val dreamShardGrant: Int,
    val woundsCured: Int,
    val accentHex: Long
) {
    DAWN_DROWSINESS(
        id = "DAWN_DROWSINESS",
        title = "Tier I • Dawn Drowsiness",
        subtitle = "< 5.5 hrs sleep • Partial Stamina recovery & base step energy yield",
        minSleepMinutes = 0,
        staminaRestorePct = 40,
        stepEnergyMultiplier = 1.00f,
        bonusXpPct = 0,
        bonusCritPct = 0,
        dreamShardGrant = 1,
        woundsCured = 1,
        accentHex = 0xFF64748BL
    ),
    RESTORATIVE_SLUMBER(
        id = "RESTORATIVE_SLUMBER",
        title = "Tier II • Restorative Slumber",
        subtitle = "5.5 – 6.9 hrs sleep • +75% Stamina, 1.15x Step Energy & +10% Hero XP",
        minSleepMinutes = 330,
        staminaRestorePct = 75,
        stepEnergyMultiplier = 1.15f,
        bonusXpPct = 10,
        bonusCritPct = 3,
        dreamShardGrant = 1,
        woundsCured = 2,
        accentHex = 0xFF38BDF8L
    ),
    DEEP_REM_RESONANCE(
        id = "DEEP_REM_RESONANCE",
        title = "Tier III • Deep REM Resonance",
        subtitle = "7.0 – 8.2 hrs sleep • 100% Stamina, 1.30x Step Energy, +25% XP & Cures Wounds",
        minSleepMinutes = 420,
        staminaRestorePct = 100,
        stepEnergyMultiplier = 1.30f,
        bonusXpPct = 25,
        bonusCritPct = 8,
        dreamShardGrant = 2,
        woundsCured = 3,
        accentHex = 0xFF8B5CF6L
    ),
    ASTRAL_LUCID_DREAM(
        id = "ASTRAL_LUCID_DREAM",
        title = "Tier IV • Astral Lucid Dream",
        subtitle = "8.3+ hrs sleep • 120% Overcharged Stamina, 1.50x Step Energy, +35% XP & +3 Dream Shards",
        minSleepMinutes = 495,
        staminaRestorePct = 120,
        stepEnergyMultiplier = 1.50f,
        bonusXpPct = 35,
        bonusCritPct = 12,
        dreamShardGrant = 3,
        woundsCured = 3,
        accentHex = 0xFFF59E0BL
    );

    companion object {
        fun forSleepMinutes(minutes: Int): DreamRestTier =
            entries.lastOrNull { minutes >= it.minSleepMinutes } ?: DAWN_DROWSINESS

        fun fromId(id: String): DreamRestTier =
            entries.firstOrNull { it.id == id || it.name == id } ?: RESTORATIVE_SLUMBER
    }
}

enum class DungeonChamberType(
    val title: String,
    val badge: String,
    val description: String,
    val accentHex: Long
) {
    ENTRANCE_GATE(
        title = "Labyrinth Threshold",
        badge = "GATE",
        description = "Unseal the runic gate with Sleep Stamina and begin your delve.",
        accentHex = 0xFF38BDF8L
    ),
    STEP_CORRIDOR(
        title = "Echoing Step Corridor",
        badge = "STEPS",
        description = "Walk real-world steps (or sprint with Step Battle Energy) to cross the hall.",
        accentHex = 0xFF10B981L
    ),
    TRAP_GAUNTLET(
        title = "Pendulum & Spike Gauntlet",
        badge = "TRAP",
        description = "Test your Hero Agility & Perception to disarm traps for bonus Ore.",
        accentHex = 0xFFF59E0BL
    ),
    SUNLIT_ALTAR(
        title = "Sanctified Dawn Altar",
        badge = "ALTAR",
        description = "Restores +35% Hero HP and empowers your next Boss fight.",
        accentHex = 0xFFA855F7L
    ),
    ELITE_WARDEN(
        title = "Inner Sanctum Warden",
        badge = "ELITE",
        description = "Defeat the chamber guardian to claim the Boss Vault Key & Essence.",
        accentHex = 0xFFE11D48L
    ),
    RELIC_CHAMBER(
        title = "Runic Alcove",
        badge = "RUNE",
        description = "Harvest Sunshards, Aether Herbs & Starlight dust before the final vault.",
        accentHex = 0xFF0D9488L
    ),
    FINAL_BOSS_VAULT(
        title = "Dungeon Boss Vault",
        badge = "BOSS CHEST",
        description = "Vanquish the Dungeon Sovereign to unlock the guaranteed Pinnacle Boss Chest!",
        accentHex = 0xFFFF4D00L
    );

    val label: String get() = badge
}

data class DungeonChamberNode(
    val index: Int,
    val name: String,
    val type: DungeonChamberType,
    val stepsRequired: Int,
    val energySprintCost: Int
) {
    val title: String get() = name
    val subtitle: String get() = type.description
    val staminaCost: Int get() = (energySprintCost / 2).coerceAtLeast(6)
}

data class StepDungeonSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val recommendedLevel: Int,
    val staminaEntryCost: Int,
    val affinity: ElementalAffinity,
    val bossName: String,
    val chestRewardTier: LootChestTier,
    val chambers: List<DungeonChamberNode>,
    val accentHex: Long
) {
    val name: String get() = title
    val recommendedRealm: Int get() = ((recommendedLevel + 2) / 3).coerceIn(1, 10)
}

object StepDungeonCatalog {
    val dungeons: List<StepDungeonSpec> = listOf(
        StepDungeonSpec(
            id = "dungeon_sunstone_catacombs",
            title = "Sunstone Catacombs",
            subtitle = "5-Room Ancient Solar Tomb • Guaranteed Boss Vault Chest",
            recommendedLevel = 1,
            staminaEntryCost = 20,
            affinity = ElementalAffinity.SOLAR,
            bossName = "Tomb-King Khoros",
            chestRewardTier = LootChestTier.BOSS_VAULT,
            accentHex = 0xFFF59E0BL,
            chambers = listOf(
                DungeonChamberNode(0, "Sunstone Threshold", DungeonChamberType.ENTRANCE_GATE, 250, 12),
                DungeonChamberNode(1, "Hall of Golden Pillars", DungeonChamberType.STEP_CORRIDOR, 350, 14),
                DungeonChamberNode(2, "Searing Plate Corridor", DungeonChamberType.TRAP_GAUNTLET, 350, 14),
                DungeonChamberNode(3, "Helios Brazier Shrine", DungeonChamberType.SUNLIT_ALTAR, 300, 12),
                DungeonChamberNode(4, "Vault of Tomb-King Khoros", DungeonChamberType.FINAL_BOSS_VAULT, 400, 16)
            )
        ),
        StepDungeonSpec(
            id = "dungeon_verdant_labyrinth",
            title = "Verdant Briar Labyrinth",
            subtitle = "5-Room Overgrown Monolith Maze • Boss Vault Chest + Herbs",
            recommendedLevel = 4,
            staminaEntryCost = 25,
            affinity = ElementalAffinity.TERRA,
            bossName = "Sylvan Arch-Thorn",
            chestRewardTier = LootChestTier.BOSS_VAULT,
            accentHex = 0xFF10B981L,
            chambers = listOf(
                DungeonChamberNode(0, "Briarwood Archway", DungeonChamberType.ENTRANCE_GATE, 350, 14),
                DungeonChamberNode(1, "Winding Root Passage", DungeonChamberType.STEP_CORRIDOR, 450, 15),
                DungeonChamberNode(2, "Spore-Cloud Thicket", DungeonChamberType.TRAP_GAUNTLET, 450, 15),
                DungeonChamberNode(3, "Grove Warden Keep", DungeonChamberType.ELITE_WARDEN, 450, 16),
                DungeonChamberNode(4, "Heart of the Arch-Thorn", DungeonChamberType.FINAL_BOSS_VAULT, 500, 18)
            )
        ),
        StepDungeonSpec(
            id = "dungeon_frostspire_vaults",
            title = "Frostspire Crystal Vaults",
            subtitle = "6-Room Glacial Cavern • High Ultra-Rare Boss Chest Odds",
            recommendedLevel = 8,
            staminaEntryCost = 30,
            affinity = ElementalAffinity.FROST,
            bossName = "Glacial Cryo-Warden",
            chestRewardTier = LootChestTier.BOSS_VAULT,
            accentHex = 0xFF38BDF8L,
            chambers = listOf(
                DungeonChamberNode(0, "Rime-Sealed Portcullis", DungeonChamberType.ENTRANCE_GATE, 450, 15),
                DungeonChamberNode(1, "Mirror-Ice Causeway", DungeonChamberType.STEP_CORRIDOR, 550, 16),
                DungeonChamberNode(2, "Icicle Pendulum Bridge", DungeonChamberType.TRAP_GAUNTLET, 550, 16),
                DungeonChamberNode(3, "Sanctuary of Winter Star", DungeonChamberType.SUNLIT_ALTAR, 450, 14),
                DungeonChamberNode(4, "Frostforged Sentry Post", DungeonChamberType.ELITE_WARDEN, 550, 18),
                DungeonChamberNode(5, "Throne of the Cryo-Warden", DungeonChamberType.FINAL_BOSS_VAULT, 650, 20)
            )
        ),
        StepDungeonSpec(
            id = "dungeon_cinderforge_depths",
            title = "Cinderforge Magma Depths",
            subtitle = "6-Room Volcanic Foundry • Sovereign Chest & Starforged Ore",
            recommendedLevel = 12,
            staminaEntryCost = 35,
            affinity = ElementalAffinity.EMBER,
            bossName = "Ignis the Anvil-Breaker",
            chestRewardTier = LootChestTier.SPIRE_SOVEREIGN,
            accentHex = 0xFFE11D48L,
            chambers = listOf(
                DungeonChamberNode(0, "Slag-Iron Blast Door", DungeonChamberType.ENTRANCE_GATE, 550, 16),
                DungeonChamberNode(1, "Basalt Lava Viaduct", DungeonChamberType.STEP_CORRIDOR, 700, 18),
                DungeonChamberNode(2, "Flame-Vent Gauntlet", DungeonChamberType.TRAP_GAUNTLET, 700, 18),
                DungeonChamberNode(3, "Smelter's Runic Alcove", DungeonChamberType.RELIC_CHAMBER, 600, 16),
                DungeonChamberNode(4, "Pyre-Drake Barracks", DungeonChamberType.ELITE_WARDEN, 750, 20),
                DungeonChamberNode(5, "Crucible of Ignis", DungeonChamberType.FINAL_BOSS_VAULT, 850, 22)
            )
        ),
        StepDungeonSpec(
            id = "dungeon_astral_sanctum",
            title = "Astral Mirage Sanctum",
            subtitle = "7-Room Starlight Observatory • Spire Sovereign Chest + Dream Shards",
            recommendedLevel = 18,
            staminaEntryCost = 40,
            affinity = ElementalAffinity.VOID,
            bossName = "Nebula Weaver Azir",
            chestRewardTier = LootChestTier.SPIRE_SOVEREIGN,
            accentHex = 0xFF8B5CF6L,
            chambers = listOf(
                DungeonChamberNode(0, "Starlight Portal", DungeonChamberType.ENTRANCE_GATE, 700, 18),
                DungeonChamberNode(1, "Zero-Gravity Promenade", DungeonChamberType.STEP_CORRIDOR, 850, 20),
                DungeonChamberNode(2, "Prism Laser Lattice", DungeonChamberType.TRAP_GAUNTLET, 850, 20),
                DungeonChamberNode(3, "Lunar Dream Font", DungeonChamberType.SUNLIT_ALTAR, 700, 18),
                DungeonChamberNode(4, "Celestial Astrolabe Vault", DungeonChamberType.RELIC_CHAMBER, 850, 20),
                DungeonChamberNode(5, "Void-Sentinel Concourse", DungeonChamberType.ELITE_WARDEN, 900, 22),
                DungeonChamberNode(6, "Nexus of Nebula Weaver Azir", DungeonChamberType.FINAL_BOSS_VAULT, 1000, 25)
            )
        ),
        StepDungeonSpec(
            id = "dungeon_abyssal_citadel",
            title = "Abyssal Obsidian Citadel",
            subtitle = "7-Room Endgame Raid Dungeon • Apex Sovereign Chest + Mythic Core Chance",
            recommendedLevel = 25,
            staminaEntryCost = 45,
            affinity = ElementalAffinity.VOID,
            bossName = "Obsidian Sovereign Malakor",
            chestRewardTier = LootChestTier.SPIRE_SOVEREIGN,
            accentHex = 0xFFEC4899L,
            chambers = listOf(
                DungeonChamberNode(0, "Obsidian Dreadgate", DungeonChamberType.ENTRANCE_GATE, 850, 20),
                DungeonChamberNode(1, "Abyssal Rampart Walk", DungeonChamberType.STEP_CORRIDOR, 1050, 22),
                DungeonChamberNode(2, "Shadow-Scythe Corridor", DungeonChamberType.TRAP_GAUNTLET, 1050, 22),
                DungeonChamberNode(3, "Altar of Eclipse Flame", DungeonChamberType.SUNLIT_ALTAR, 900, 20),
                DungeonChamberNode(4, "Vault of Forbidden Runes", DungeonChamberType.RELIC_CHAMBER, 1050, 22),
                DungeonChamberNode(5, "Dreadguard Adjudicator Hall", DungeonChamberType.ELITE_WARDEN, 1150, 24),
                DungeonChamberNode(6, "Sanctum of Sovereign Malakor", DungeonChamberType.FINAL_BOSS_VAULT, 1300, 28)
            )
        )
    )

    fun fromId(id: String): StepDungeonSpec =
        dungeons.firstOrNull { it.id == id } ?: dungeons.first()

    fun byId(id: String): StepDungeonSpec = fromId(id)
}

enum class ConstellationBranch(
    val id: String,
    val title: String,
    val subtitle: String,
    val accentHex: Long
) {
    STRIDER(
        id = "STRIDER",
        title = "Way of the Strider",
        subtitle = "Step-to-Energy efficiency, Dungeon Corridor speed & Expedition Chest mastery",
        accentHex = 0xFF10B981L
    ),
    DAWNBLADE(
        id = "DAWNBLADE",
        title = "Way of the Dawnblade",
        subtitle = "Critical lethality, Perfect Parry counter-strikes & Boss armor sundering",
        accentHex = 0xFFF59E0BL
    ),
    DREAMWEAVER(
        id = "DREAMWEAVER",
        title = "Way of the Dreamweaver",
        subtitle = "Sleep Stamina capacity, Dream Rest buff potency & Companion Pet synergy",
        accentHex = 0xFF8B5CF6L
    )
}

data class ConstellationNodeSpec(
    val id: String,
    val branch: ConstellationBranch,
    val tierIndex: Int,
    val title: String,
    val bonusPerRankSummary: String,
    val maxRank: Int = 5,
    val shardCostPerRank: Int = 1,
    val canvasX: Float,
    val canvasY: Float
) {
    val summary: String get() = bonusPerRankSummary
    val dreamShardCost: Int get() = shardCostPerRank
    val sunshardCost: Long get() = shardCostPerRank * 95L
}

object ConstellationCatalog {
    val nodes: List<ConstellationNodeSpec> = listOf(
        // Branch 1: Way of the Strider
        ConstellationNodeSpec(
            id = "strider_pace",
            branch = ConstellationBranch.STRIDER,
            tierIndex = 0,
            title = "Tireless Cadence",
            bonusPerRankSummary = "+6% Step-to-Battle-Energy conversion per rank",
            maxRank = 5,
            shardCostPerRank = 1,
            canvasX = 0.18f,
            canvasY = 0.78f
        ),
        ConstellationNodeSpec(
            id = "strider_corridor",
            branch = ConstellationBranch.STRIDER,
            tierIndex = 1,
            title = "Labyrinth Pathfinder",
            bonusPerRankSummary = "-6% Steps required per Dungeon Chamber & +4 ATK per rank",
            maxRank = 5,
            shardCostPerRank = 1,
            canvasX = 0.24f,
            canvasY = 0.56f
        ),
        ConstellationNodeSpec(
            id = "strider_expedition",
            branch = ConstellationBranch.STRIDER,
            tierIndex = 2,
            title = "Horizon Trailblazer",
            bonusPerRankSummary = "+3% Expedition Survival & +4% Boss Chest Ultra-Rare odds per rank",
            maxRank = 5,
            shardCostPerRank = 2,
            canvasX = 0.16f,
            canvasY = 0.34f
        ),
        ConstellationNodeSpec(
            id = "strider_apex",
            branch = ConstellationBranch.STRIDER,
            tierIndex = 3,
            title = "Northstar Strider",
            bonusPerRankSummary = "+6 Max Battle Energy, +5 Max Stamina & +4% All Stats per rank",
            maxRank = 5,
            shardCostPerRank = 2,
            canvasX = 0.26f,
            canvasY = 0.14f
        ),

        // Branch 2: Way of the Dawnblade
        ConstellationNodeSpec(
            id = "dawn_precision",
            branch = ConstellationBranch.DAWNBLADE,
            tierIndex = 0,
            title = "Solar Precision",
            bonusPerRankSummary = "+2% Critical Chance & +5 Flat Attack per rank",
            maxRank = 5,
            shardCostPerRank = 1,
            canvasX = 0.50f,
            canvasY = 0.80f
        ),
        ConstellationNodeSpec(
            id = "dawn_riposte",
            branch = ConstellationBranch.DAWNBLADE,
            tierIndex = 1,
            title = "Aegis Riposte",
            bonusPerRankSummary = "+4% Defense & +2% Damage Reduction per rank",
            maxRank = 5,
            shardCostPerRank = 1,
            canvasX = 0.50f,
            canvasY = 0.56f
        ),
        ConstellationNodeSpec(
            id = "dawn_sunder",
            branch = ConstellationBranch.DAWNBLADE,
            tierIndex = 2,
            title = "Titan Sunderer",
            bonusPerRankSummary = "+3% Armor Pierce & +2% Lifesteal per rank",
            maxRank = 5,
            shardCostPerRank = 2,
            canvasX = 0.50f,
            canvasY = 0.34f
        ),
        ConstellationNodeSpec(
            id = "dawn_apex",
            branch = ConstellationBranch.DAWNBLADE,
            tierIndex = 3,
            title = "Supernova Ascendant",
            bonusPerRankSummary = "+8% Active Skill Overdrive DMG & +25 Max HP per rank",
            maxRank = 5,
            shardCostPerRank = 2,
            canvasX = 0.50f,
            canvasY = 0.12f
        ),

        // Branch 3: Way of the Dreamweaver
        ConstellationNodeSpec(
            id = "dream_reservoir",
            branch = ConstellationBranch.DREAMWEAVER,
            tierIndex = 0,
            title = "Lunar Stamina Font",
            bonusPerRankSummary = "+8 Max Sleep Stamina & +4 Turn Regen HP per rank",
            maxRank = 5,
            shardCostPerRank = 1,
            canvasX = 0.82f,
            canvasY = 0.78f
        ),
        ConstellationNodeSpec(
            id = "dream_harmony",
            branch = ConstellationBranch.DREAMWEAVER,
            tierIndex = 1,
            title = "Deep REM Harmony",
            bonusPerRankSummary = "+5% Hero XP from Steps & Battles per rank",
            maxRank = 5,
            shardCostPerRank = 1,
            canvasX = 0.76f,
            canvasY = 0.56f
        ),
        ConstellationNodeSpec(
            id = "dream_aura",
            branch = ConstellationBranch.DREAMWEAVER,
            tierIndex = 2,
            title = "Astral Ward",
            bonusPerRankSummary = "+5% Sunshard Yield & +18 Max HP per rank",
            maxRank = 5,
            shardCostPerRank = 2,
            canvasX = 0.84f,
            canvasY = 0.34f
        ),
        ConstellationNodeSpec(
            id = "dream_apex",
            branch = ConstellationBranch.DREAMWEAVER,
            tierIndex = 3,
            title = "Sovereign of Dreams",
            bonusPerRankSummary = "+6% ATK/DEF while Well-Rested & +5 Max Energy per rank",
            maxRank = 5,
            shardCostPerRank = 2,
            canvasX = 0.74f,
            canvasY = 0.14f
        )
    )

    fun fromId(id: String): ConstellationNodeSpec? =
        nodes.firstOrNull { it.id == id }

    fun byId(id: String): ConstellationNodeSpec? = fromId(id)

    fun byBranch(branch: ConstellationBranch): List<ConstellationNodeSpec> =
        nodes.filter { it.branch == branch }
}

data class GearAffixSpec(
    val id: String,
    val label: String,
    val isPrefix: Boolean,
    val bonusSummary: String,
    val atkPct: Int = 0,
    val defPct: Int = 0,
    val hpPct: Int = 0,
    val critPct: Int = 0,
    val piercePct: Int = 0,
    val stepEnergyPct: Int = 0
) {
    fun summary(): String = bonusSummary
}

object GearAffixCatalog {
    val prefixes: List<GearAffixSpec> = listOf(
        GearAffixSpec("PRE_SUNFORGED", "Sunforged", true, "+6% ATK & +2% Crit", atkPct = 6, critPct = 2),
        GearAffixSpec("PRE_TIRELESS", "Tireless Strider's", true, "+8% Step Energy & +4% DEF", defPct = 4, stepEnergyPct = 8),
        GearAffixSpec("PRE_ASTRAL", "Astral Dreamer's", true, "+7% Max HP & +4% ATK", atkPct = 4, hpPct = 7),
        GearAffixSpec("PRE_TITAN", "Titan-Slayer's", true, "+5% Armor Pierce & +5% ATK", atkPct = 5, piercePct = 5),
        GearAffixSpec("PRE_OBSIDIAN", "Obsidian Bastion", true, "+8% DEF & +5% Max HP", defPct = 8, hpPct = 5),
        GearAffixSpec("PRE_CELESTIAL", "Celestial Sovereign", true, "+7% ATK, +5% DEF & +3% Crit", atkPct = 7, defPct = 5, critPct = 3)
    )

    val suffixes: List<GearAffixSpec> = listOf(
        GearAffixSpec("SUF_DAWN", "of the Radiant Dawn", false, "+5% ATK & +4% Max HP", atkPct = 5, hpPct = 4),
        GearAffixSpec("SUF_SLUMBER", "of Deep Slumber", false, "+8% Step Energy & +6% Max HP", hpPct = 6, stepEnergyPct = 8),
        GearAffixSpec("SUF_PRECISION", "of Solar Precision", false, "+4% Crit & +3% Pierce", critPct = 4, piercePct = 3),
        GearAffixSpec("SUF_LABYRINTH", "of the Labyrinth", false, "+6% DEF & +4% ATK", atkPct = 4, defPct = 6),
        GearAffixSpec("SUF_ECLIPSE", "of the Eclipse", false, "+6% Armor Pierce & +2% Crit", critPct = 2, piercePct = 6),
        GearAffixSpec("SUF_IMMORTAL", "of the Immortal Stride", false, "+8% Max HP, +5% DEF & +5% Step Energy", defPct = 5, hpPct = 8, stepEnergyPct = 5)
    )

    fun rollRandomPair(seed: Int): Pair<GearAffixSpec, GearAffixSpec> {
        val preIdx = abs(seed) % prefixes.size
        val sufIdx = abs(seed * 31 + 17) % suffixes.size
        return prefixes[preIdx] to suffixes[sufIdx]
    }

    fun parsePair(raw: String?): Pair<GearAffixSpec?, GearAffixSpec?> {
        if (raw.isNullOrBlank()) return null to null
        val parts = raw.split("|")
        val pre = parts.getOrNull(0)?.let { id -> prefixes.firstOrNull { it.id == id } }
        val suf = parts.getOrNull(1)?.let { id -> suffixes.firstOrNull { it.id == id } }
        return pre to suf
    }

    fun formatItemTitleWithAffixes(baseName: String, rawAffix: String?): String {
        val (pre, suf) = parsePair(rawAffix)
        return buildString {
            if (pre != null) append("${pre.label} ")
            append(baseName)
            if (suf != null) append(" ${suf.label}")
        }
    }
}

data class SlayerContractSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val stepsRequired: Int,
    val combatTargetRequired: Int,
    val requiresBossOrDungeon: Boolean,
    val dreamShardReward: Int,
    val sunshardReward: Long,
    val oreReward: Long,
    val essenceReward: Long,
    val xpReward: Long,
    val accentHex: Long
) {
    val killsRequired: Int get() = combatTargetRequired
    val solCoinReward: Long get() = dreamShardReward * 18L
    val evolutionShardReward: Long get() = if (requiresBossOrDungeon) 1L else 0L
}

object SlayerContractCatalog {
    val contracts: List<SlayerContractSpec> = listOf(
        SlayerContractSpec(
            id = "slayer_dune_patrol",
            title = "Dune Stalker Patrol",
            subtitle = "Walk 1,200 real steps & win 2 tactical battles",
            stepsRequired = 1200,
            combatTargetRequired = 2,
            requiresBossOrDungeon = false,
            dreamShardReward = 1,
            sunshardReward = 45L,
            oreReward = 8L,
            essenceReward = 4L,
            xpReward = 140L,
            accentHex = 0xFF10B981L
        ),
        SlayerContractSpec(
            id = "slayer_catacomb_delver",
            title = "Catacomb Corridor Delver",
            subtitle = "Walk 2,500 real steps & clear or win 3 encounters",
            stepsRequired = 2500,
            combatTargetRequired = 3,
            requiresBossOrDungeon = false,
            dreamShardReward = 2,
            sunshardReward = 80L,
            oreReward = 14L,
            essenceReward = 8L,
            xpReward = 240L,
            accentHex = 0xFF38BDF8L
        ),
        SlayerContractSpec(
            id = "slayer_guardian_hunt",
            title = "Realm Guardian Bounty",
            subtitle = "Walk 4,000 real steps & defeat 1 Boss or Dungeon Sovereign",
            stepsRequired = 4000,
            combatTargetRequired = 1,
            requiresBossOrDungeon = true,
            dreamShardReward = 2,
            sunshardReward = 120L,
            oreReward = 20L,
            essenceReward = 12L,
            xpReward = 380L,
            accentHex = 0xFFF59E0BL
        ),
        SlayerContractSpec(
            id = "slayer_abyssal_marathon",
            title = "Abyssal Titan Slayer Oath",
            subtitle = "Walk 7,500 real steps & defeat 2 Bosses or Dungeon Sovereigns",
            stepsRequired = 7500,
            combatTargetRequired = 2,
            requiresBossOrDungeon = true,
            dreamShardReward = 3,
            sunshardReward = 220L,
            oreReward = 32L,
            essenceReward = 20L,
            xpReward = 650L,
            accentHex = 0xFF8B5CF6L
        )
    )
}

