package com.example.domain

import com.example.data.model.HeroClass
import com.example.data.model.HeroProfile
import kotlin.math.roundToInt

enum class ClassSkillKind {
    ACTIVE,
    PASSIVE
}

enum class ActiveSkillEffect {
    // Vanguard
    SUNBREAKER_BASH,     // Heavy strike + Armor Sunder + Stun chance
    AEGIS_BULWARK,       // Grants massive Aegis Barrier + Thorns counter + light hit
    SOLAR_JUDGMENT,      // Scales with ATK + DEF, high solar damage + heal
    // Arcanist
    PYRE_BOLT,           // Ember burst + multi-turn Scorch Burn
    ASTRAL_NOVA,         // Armor-piercing arcane blast + Valor refund
    ECLIPSE_METEOR,      // Colossal elemental cataclysm + Sunder + Burn
    // Ranger
    FROSTBITE_SHOT,      // Precision frost shot + Frostbite (-25% Enemy ATK)
    GALE_VOLLEY,         // Multi-hit volley with elevated crit rate
    HORIZON_SNIPE,       // Guaranteed Critical piercing execution
    // Shadowblade
    VENOM_FANG,          // Swift void strike + stacking Poison
    PHANTOM_STEP,        // High-lifesteal strike + Shadow Barrier
    NIGHTFALL_EXECUTION  // Devastating finisher dealing +40% bonus vs debuffed foes
}

enum class SkillMasteryTier(
    val tierNumber: Int,
    val roman: String,
    val title: String,
    val minRank: Int,
    val maxRank: Int,
    val requiredHeroLevelBonus: Int,
    val milestoneBonusSummary: String
) {
    TIER_1_NOVICE(
        tierNumber = 1,
        roman = "Tier I",
        title = "Initiate Art",
        minRank = 1,
        maxRank = 3,
        requiredHeroLevelBonus = 0,
        milestoneBonusSummary = "Base Combat Art & Passive Foundation"
    ),
    TIER_2_ADEPT(
        tierNumber = 2,
        roman = "Tier II",
        title = "Adept Mastery",
        minRank = 4,
        maxRank = 6,
        requiredHeroLevelBonus = 3,
        milestoneBonusSummary = "Awakens Tier II Adept Synergy (+12% Skill Power & -2 Valor Cost)"
    ),
    TIER_3_MASTER(
        tierNumber = 3,
        roman = "Tier III",
        title = "Grandmaster Art",
        minRank = 7,
        maxRank = 9,
        requiredHeroLevelBonus = 7,
        milestoneBonusSummary = "Awakens Tier III Grandmaster Overdrive (+25% Skill Power & +10% Crit/Status)"
    ),
    TIER_4_MYTHIC(
        tierNumber = 4,
        roman = "Tier IV",
        title = "Mythic Transcendence",
        minRank = 10,
        maxRank = 10,
        requiredHeroLevelBonus = 11,
        milestoneBonusSummary = "Awakens Tier IV Mythic Apex Form (+45% Skill Power & -1T Cooldown)"
    );

    companion object {
        fun forRank(rank: Int): SkillMasteryTier = when {
            rank >= 10 -> TIER_4_MYTHIC
            rank >= 7 -> TIER_3_MASTER
            rank >= 4 -> TIER_2_ADEPT
            else -> TIER_1_NOVICE
        }
    }
}

data class ClassSkillSpec(
    val index: Int, // 0..2 = Active, 3..8 = Passive
    val id: String,
    val heroClass: HeroClass,
    val kind: ClassSkillKind,
    val name: String,
    val unlockLevel: Int,
    val maxRank: Int = 10,
    val valorCost: Int = 0,
    val cooldownTurns: Int = 0,
    val activeEffect: ActiveSkillEffect? = null,
    val basePowerPct: Int = 0,
    val perRankPowerPct: Int = 0,
    val summary: String,
    val perRankSummary: String
) {
    val skillBranchTier: Int
        get() = when {
            unlockLevel <= 3 -> 1
            unlockLevel <= 9 -> 2
            unlockLevel <= 14 -> 3
            else -> 4
        }

    val skillBranchTierLabel: String
        get() = when (skillBranchTier) {
            1 -> "Tier I • Foundation"
            2 -> "Tier II • Vanguard Art"
            3 -> "Tier III • Ascendant Art"
            else -> "Tier IV • Apex Art"
        }

    fun isUnlockedAt(heroLevel: Int): Boolean = heroLevel >= unlockLevel

    fun masteryTierForRank(rank: Int): SkillMasteryTier =
        SkillMasteryTier.forRank(rank.coerceAtLeast(1))

    fun evolvedNameForRank(rank: Int): String {
        return when (masteryTierForRank(rank)) {
            SkillMasteryTier.TIER_1_NOVICE -> name
            SkillMasteryTier.TIER_2_ADEPT -> "Adept $name"
            SkillMasteryTier.TIER_3_MASTER -> "Grandmaster $name"
            SkillMasteryTier.TIER_4_MYTHIC -> "Mythic $name ★"
        }
    }

    fun effectiveValorCost(rank: Int): Int {
        if (valorCost <= 0) return 0
        val reduction = when {
            rank >= 10 -> 5
            rank >= 7 -> 3
            rank >= 4 -> 2
            else -> 0
        }
        return (valorCost - reduction).coerceAtLeast(10)
    }

    fun effectiveCooldownTurns(rank: Int): Int {
        if (cooldownTurns <= 0) return 0
        return if (rank >= 10) (cooldownTurns - 1).coerceAtLeast(1) else cooldownTurns
    }

    fun effectiveMultiplier(rank: Int): Float {
        val safeRank = rank.coerceIn(1, maxRank)
        val tierMilestonePct = when {
            safeRank >= 10 -> 45
            safeRank >= 7 -> 25
            safeRank >= 4 -> 12
            else -> 0
        }
        return (basePowerPct + (safeRank - 1) * perRankPowerPct + tierMilestonePct) / 100f
    }
}

/**
 * 36-skill Multi-Tier Class Mastery Catalog (4 Classes × 9 Skills across 4 Unlock Tiers, each upgradable across 4 Mastery Tiers up to Rank 10).
 * Skills unlock automatically as the hero levels up, and ranks 1..10 are upgraded using Sol Coins
 * on a smooth, long-term balanced curve.
 */
object ClassSkillCatalog {

    const val TOTAL_SKILLS_PER_CLASS = 9
    const val ACTIVE_SKILL_COUNT = 3
    const val MAX_SKILL_RANK = 10

    /**
     * Smooth escalating Sol Coin cost per rank-up across 4 Mastery Tiers (Ranks 1..10):
     */
    fun upgradeSolCoinCost(currentRank: Int): Long = when (currentRank.coerceIn(0, MAX_SKILL_RANK - 1)) {
        0 -> 10L
        1 -> 15L
        2 -> 25L
        3 -> 40L // Tier II Adept Awakening
        4 -> 60L
        5 -> 85L
        6 -> 115L // Tier III Grandmaster Awakening
        7 -> 150L
        8 -> 195L
        else -> 250L // Tier IV Mythic Transcendence (Rank 10)
    }

    private val vanguardSkills = listOf(
        ClassSkillSpec(
            index = 0, id = "van_sunbreaker", heroClass = HeroClass.VANGUARD, kind = ClassSkillKind.ACTIVE,
            name = "Sunbreaker Bash", unlockLevel = 1, valorCost = 20, cooldownTurns = 0,
            activeEffect = ActiveSkillEffect.SUNBREAKER_BASH, basePowerPct = 145, perRankPowerPct = 20,
            summary = "Shield-infused Solar strike that inflicts Armor Sunder (3T) & 30% Stun chance.",
            perRankSummary = "+20% Skill DMG & +6% Stun chance per rank"
        ),
        ClassSkillSpec(
            index = 1, id = "van_aegis", heroClass = HeroClass.VANGUARD, kind = ClassSkillKind.ACTIVE,
            name = "Aegis Bulwark", unlockLevel = 5, valorCost = 25, cooldownTurns = 2,
            activeEffect = ActiveSkillEffect.AEGIS_BULWARK, basePowerPct = 95, perRankPowerPct = 15,
            summary = "Raises a radiant Aegis Barrier (26% Max HP + scaling) and counters with radiant Thorns.",
            perRankSummary = "+5% Max HP Barrier & +15% counter strike DMG per rank"
        ),
        ClassSkillSpec(
            index = 2, id = "van_judgment", heroClass = HeroClass.VANGUARD, kind = ClassSkillKind.ACTIVE,
            name = "Solar Judgment", unlockLevel = 12, valorCost = 40, cooldownTurns = 3,
            activeEffect = ActiveSkillEffect.SOLAR_JUDGMENT, basePowerPct = 225, perRankPowerPct = 30,
            summary = "Calls down a sun-forged hammer scaling with ATK + DEF and heals 12% Max HP.",
            perRankSummary = "+30% Skill DMG & +2% Max HP heal per rank"
        ),
        ClassSkillSpec(
            index = 3, id = "van_fortitude", heroClass = HeroClass.VANGUARD, kind = ClassSkillKind.PASSIVE,
            name = "Sunplate Fortitude", unlockLevel = 2,
            summary = "Tempers vitality and raw armor.",
            perRankSummary = "+26 Max HP & +4 Defense per rank"
        ),
        ClassSkillSpec(
            index = 4, id = "van_thorns", heroClass = HeroClass.VANGUARD, kind = ClassSkillKind.PASSIVE,
            name = "Radiant Counter-Thorns", unlockLevel = 4,
            summary = "Punishes attackers with solar spikes and flat mitigation.",
            perRankSummary = "+3% DMG Reduction & +6% Thorns Reflect per rank"
        ),
        ClassSkillSpec(
            index = 5, id = "van_regen", heroClass = HeroClass.VANGUARD, kind = ClassSkillKind.PASSIVE,
            name = "Sanctuary Heart", unlockLevel = 7,
            summary = "Steadily mends battle wounds every combat turn.",
            perRankSummary = "+6 HP/Turn Regen & +14 Max HP per rank"
        ),
        ClassSkillSpec(
            index = 6, id = "van_might", heroClass = HeroClass.VANGUARD, kind = ClassSkillKind.PASSIVE,
            name = "Hammer of Dawn", unlockLevel = 10,
            summary = "Adds crushing force and armor penetration.",
            perRankSummary = "+5 Attack & +4% Armor Pierce per rank"
        ),
        ClassSkillSpec(
            index = 7, id = "van_bastion", heroClass = HeroClass.VANGUARD, kind = ClassSkillKind.PASSIVE,
            name = "Marching Bastion", unlockLevel = 15,
            summary = "Protects hero during lengthy Timed Expeditions.",
            perRankSummary = "+4% Expedition Survival & +5 Defense per rank"
        ),
        ClassSkillSpec(
            index = 8, id = "van_titan", heroClass = HeroClass.VANGUARD, kind = ClassSkillKind.PASSIVE,
            name = "Titan's Resolve", unlockLevel = 20,
            summary = "Pinnacle Vanguard mastery amplifying skills and rewards.",
            perRankSummary = "+8% Skill Overdrive & +6% XP / Sunshards per rank"
        )
    )

    private val arcanistSkills = listOf(
        ClassSkillSpec(
            index = 0, id = "arc_pyre", heroClass = HeroClass.ARCANIST, kind = ClassSkillKind.ACTIVE,
            name = "Pyre Bolt", unlockLevel = 1, valorCost = 20, cooldownTurns = 0,
            activeEffect = ActiveSkillEffect.PYRE_BOLT, basePowerPct = 155, perRankPowerPct = 22,
            summary = "Hurls a searing Ember lance that ignites enemies with 3-turn Scorch Burn.",
            perRankSummary = "+22% Spell DMG & stronger Scorch Burn per rank"
        ),
        ClassSkillSpec(
            index = 1, id = "arc_nova", heroClass = HeroClass.ARCANIST, kind = ClassSkillKind.ACTIVE,
            name = "Astral Nova", unlockLevel = 5, valorCost = 25, cooldownTurns = 2,
            activeEffect = ActiveSkillEffect.ASTRAL_NOVA, basePowerPct = 185, perRankPowerPct = 25,
            summary = "Unleashes an armor-piercing starburst (+35% Pierce) and refunds +12 Valor.",
            perRankSummary = "+25% Spell DMG & +3% extra Armor Pierce per rank"
        ),
        ClassSkillSpec(
            index = 2, id = "arc_meteor", heroClass = HeroClass.ARCANIST, kind = ClassSkillKind.ACTIVE,
            name = "Eclipse Meteor", unlockLevel = 12, valorCost = 40, cooldownTurns = 3,
            activeEffect = ActiveSkillEffect.ECLIPSE_METEOR, basePowerPct = 265, perRankPowerPct = 35,
            summary = "Summons a sun-forged meteor dealing massive Elemental DMG + Burn + Sunder.",
            perRankSummary = "+35% Cataclysm DMG per rank"
        ),
        ClassSkillSpec(
            index = 3, id = "arc_mind", heroClass = HeroClass.ARCANIST, kind = ClassSkillKind.PASSIVE,
            name = "Scorching Mind", unlockLevel = 2,
            summary = "Amplifies spellpower and active skill overdrive.",
            perRankSummary = "+5 Attack & +8% Skill Overdrive per rank"
        ),
        ClassSkillSpec(
            index = 4, id = "arc_pierce", heroClass = HeroClass.ARCANIST, kind = ClassSkillKind.PASSIVE,
            name = "Aether Rend", unlockLevel = 4,
            summary = "Spells tear straight through boss armor plating.",
            perRankSummary = "+5% Armor Pierce & +2% Crit Chance per rank"
        ),
        ClassSkillSpec(
            index = 5, id = "arc_siphon", heroClass = HeroClass.ARCANIST, kind = ClassSkillKind.PASSIVE,
            name = "Solar Siphon", unlockLevel = 7,
            summary = "Channels spell damage back into hero health.",
            perRankSummary = "+3% Lifesteal & +12 Max HP per rank"
        ),
        ClassSkillSpec(
            index = 6, id = "arc_ward", heroClass = HeroClass.ARCANIST, kind = ClassSkillKind.PASSIVE,
            name = "Astrolabe Barrier", unlockLevel = 10,
            summary = "Weaves arcane shields for combat and expeditions.",
            perRankSummary = "+3 Defense, +2% DMG Reduction & +3% Exp. Survival per rank"
        ),
        ClassSkillSpec(
            index = 7, id = "arc_scholar", heroClass = HeroClass.ARCANIST, kind = ClassSkillKind.PASSIVE,
            name = "Starlight Scholar", unlockLevel = 15,
            summary = "Extracts richer wisdom and Sunshards from every encounter.",
            perRankSummary = "+7% XP & +7% Sunshards per rank"
        ),
        ClassSkillSpec(
            index = 8, id = "arc_conduit", heroClass = HeroClass.ARCANIST, kind = ClassSkillKind.PASSIVE,
            name = "Overdrive Conduit", unlockLevel = 20,
            summary = "Apex arcane resonance unleashing devastating critical spells.",
            perRankSummary = "+6 Attack, +3% Crit & +10% Skill Overdrive per rank"
        )
    )

    private val rangerSkills = listOf(
        ClassSkillSpec(
            index = 0, id = "rng_frostshot", heroClass = HeroClass.RANGER, kind = ClassSkillKind.ACTIVE,
            name = "Frostbite Shot", unlockLevel = 1, valorCost = 20, cooldownTurns = 0,
            activeEffect = ActiveSkillEffect.FROSTBITE_SHOT, basePowerPct = 150, perRankPowerPct = 20,
            summary = "Looses a rime-tipped arrow inflicting 3-turn Frostbite (-25% Enemy ATK).",
            perRankSummary = "+20% Arrow DMG & +3% Crit Chance per rank"
        ),
        ClassSkillSpec(
            index = 1, id = "rng_volley", heroClass = HeroClass.RANGER, kind = ClassSkillKind.ACTIVE,
            name = "Gale Multi-Volley", unlockLevel = 5, valorCost = 25, cooldownTurns = 2,
            activeEffect = ActiveSkillEffect.GALE_VOLLEY, basePowerPct = 195, perRankPowerPct = 26,
            summary = "Fires a 3-arrow windstorm with +20% bonus Critical Chance.",
            perRankSummary = "+26% Volley DMG & +4% Crit Chance per rank"
        ),
        ClassSkillSpec(
            index = 2, id = "rng_snipe", heroClass = HeroClass.RANGER, kind = ClassSkillKind.ACTIVE,
            name = "Horizon Snipe", unlockLevel = 12, valorCost = 40, cooldownTurns = 3,
            activeEffect = ActiveSkillEffect.HORIZON_SNIPE, basePowerPct = 240, perRankPowerPct = 32,
            summary = "Guaranteed Critical piercing shot that ignores 40% Enemy Defense.",
            perRankSummary = "+32% Snipe DMG per rank"
        ),
        ClassSkillSpec(
            index = 3, id = "rng_eagle", heroClass = HeroClass.RANGER, kind = ClassSkillKind.PASSIVE,
            name = "Eagle Eye", unlockLevel = 2,
            summary = "Sharpens critical precision and bow draw strength.",
            perRankSummary = "+4% Crit Chance & +4 Attack per rank"
        ),
        ClassSkillSpec(
            index = 4, id = "rng_pierce", heroClass = HeroClass.RANGER, kind = ClassSkillKind.PASSIVE,
            name = "Broadhead Arrows", unlockLevel = 4,
            summary = "Barbed shafts puncture thick monster hides.",
            perRankSummary = "+5% Armor Pierce & +5% Skill Overdrive per rank"
        ),
        ClassSkillSpec(
            index = 5, id = "rng_trail", heroClass = HeroClass.RANGER, kind = ClassSkillKind.PASSIVE,
            name = "Trailblazer Instinct", unlockLevel = 7,
            summary = "Mastery of highland paths for safer Timed Expeditions.",
            perRankSummary = "+4% Expedition Survival & +14 Max HP per rank"
        ),
        ClassSkillSpec(
            index = 6, id = "rng_mend", heroClass = HeroClass.RANGER, kind = ClassSkillKind.PASSIVE,
            name = "Oasis Herbalism", unlockLevel = 10,
            summary = "Sustains stamina and health during drawn-out fights.",
            perRankSummary = "+5 HP/Turn Regen & +3 Defense per rank"
        ),
        ClassSkillSpec(
            index = 7, id = "rng_prospector", heroClass = HeroClass.RANGER, kind = ClassSkillKind.PASSIVE,
            name = "Horizon Prospector", unlockLevel = 15,
            summary = "Uncovers bonus Sunshards and XP from battles & expeditions.",
            perRankSummary = "+7% XP & +7% Sunshards per rank"
        ),
        ClassSkillSpec(
            index = 8, id = "rng_apex", heroClass = HeroClass.RANGER, kind = ClassSkillKind.PASSIVE,
            name = "Apex Hunter", unlockLevel = 20,
            summary = "Master marksman precision and relentless momentum.",
            perRankSummary = "+6 Attack, +4% Crit & +3% Lifesteal per rank"
        )
    )

    private val shadowbladeSkills = listOf(
        ClassSkillSpec(
            index = 0, id = "shd_venom", heroClass = HeroClass.SHADOWBLADE, kind = ClassSkillKind.ACTIVE,
            name = "Venom Fang", unlockLevel = 1, valorCost = 20, cooldownTurns = 0,
            activeEffect = ActiveSkillEffect.VENOM_FANG, basePowerPct = 150, perRankPowerPct = 22,
            summary = "Twin eclipse slash inflicting 3-turn Void Poison and +15% Armor Pierce.",
            perRankSummary = "+22% Slash DMG & stronger Poison DoT per rank"
        ),
        ClassSkillSpec(
            index = 1, id = "shd_phantom", heroClass = HeroClass.SHADOWBLADE, kind = ClassSkillKind.ACTIVE,
            name = "Phantom Step", unlockLevel = 5, valorCost = 25, cooldownTurns = 2,
            activeEffect = ActiveSkillEffect.PHANTOM_STEP, basePowerPct = 170, perRankPowerPct = 24,
            summary = "Strikes from shadow with 30% Lifesteal and grants an Evasion Shadow Barrier.",
            perRankSummary = "+24% Strike DMG & +4% Max HP Shadow Barrier per rank"
        ),
        ClassSkillSpec(
            index = 2, id = "shd_nightfall", heroClass = HeroClass.SHADOWBLADE, kind = ClassSkillKind.ACTIVE,
            name = "Nightfall Execution", unlockLevel = 12, valorCost = 40, cooldownTurns = 3,
            activeEffect = ActiveSkillEffect.NIGHTFALL_EXECUTION, basePowerPct = 245, perRankPowerPct = 35,
            summary = "Lethal void execution dealing +40% bonus DMG against poisoned or debuffed foes.",
            perRankSummary = "+35% Execution DMG per rank"
        ),
        ClassSkillSpec(
            index = 3, id = "shd_edge", heroClass = HeroClass.SHADOWBLADE, kind = ClassSkillKind.PASSIVE,
            name = "Lethal Edge", unlockLevel = 2,
            summary = "Hones twin blades for savage critical strikes.",
            perRankSummary = "+5 Attack & +3% Crit Chance per rank"
        ),
        ClassSkillSpec(
            index = 4, id = "shd_leech", heroClass = HeroClass.SHADOWBLADE, kind = ClassSkillKind.PASSIVE,
            name = "Eclipse Bloodleech", unlockLevel = 4,
            summary = "Drains vitality from every strike and active skill.",
            perRankSummary = "+4% Lifesteal & +12 Max HP per rank"
        ),
        ClassSkillSpec(
            index = 5, id = "shd_rend", heroClass = HeroClass.SHADOWBLADE, kind = ClassSkillKind.PASSIVE,
            name = "Shadow Armor Rend", unlockLevel = 7,
            summary = "Slips daggers cleanly through heavy guardian plates.",
            perRankSummary = "+6% Armor Pierce & +5% Skill Overdrive per rank"
        ),
        ClassSkillSpec(
            index = 6, id = "shd_reflex", heroClass = HeroClass.SHADOWBLADE, kind = ClassSkillKind.PASSIVE,
            name = "Mirage Reflexes", unlockLevel = 10,
            summary = "Deflects incoming blows and improves stealth scouting.",
            perRankSummary = "+3% DMG Reduction, +2 Defense & +3% Exp. Survival per rank"
        ),
        ClassSkillSpec(
            index = 7, id = "shd_plunder", heroClass = HeroClass.SHADOWBLADE, kind = ClassSkillKind.PASSIVE,
            name = "Guild Plunderer", unlockLevel = 15,
            summary = "Extracts extra Sunshards and combat insight from foes.",
            perRankSummary = "+7% XP & +7% Sunshards per rank"
        ),
        ClassSkillSpec(
            index = 8, id = "shd_focus", heroClass = HeroClass.SHADOWBLADE, kind = ClassSkillKind.PASSIVE,
            name = "Assassin's Focus", unlockLevel = 20,
            summary = "Ultimate shadow mastery empowering crits and finishers.",
            perRankSummary = "+6 Attack, +4% Crit & +8% Skill Overdrive per rank"
        )
    )

    fun skillsFor(heroClass: HeroClass): List<ClassSkillSpec> = when (heroClass) {
        HeroClass.VANGUARD -> vanguardSkills
        HeroClass.ARCANIST -> arcanistSkills
        HeroClass.RANGER -> rangerSkills
        HeroClass.SHADOWBLADE -> shadowbladeSkills
    }

    fun activeSkillsFor(heroClass: HeroClass): List<ClassSkillSpec> =
        skillsFor(heroClass).filter { it.kind == ClassSkillKind.ACTIVE }

    fun passiveSkillsFor(heroClass: HeroClass): List<ClassSkillSpec> =
        skillsFor(heroClass).filter { it.kind == ClassSkillKind.PASSIVE }

    fun effectiveRank(profile: HeroProfile, skill: ClassSkillSpec): Int {
        val ranks = ProgressionEngine.parseSkillRanks(profile.skillRanks)
        val stored = ranks.getOrElse(skill.index) { 0 }
        // Starter Level-1 Active Skill is automatically usable at Rank 1!
        return if (skill.index == 0 && profile.level >= skill.unlockLevel) {
            stored.coerceAtLeast(1)
        } else {
            stored
        }
    }

    fun computeClassPassiveBonuses(profile: HeroProfile): ComputedSkillTreeBonuses {
        val heroClass = profile.resolvedClass()
        val ranks = ProgressionEngine.parseSkillRanks(profile.skillRanks)
        val pLvl = profile.prestigeLevel.toInt().coerceIn(0, ProgressionEngine.MAX_PRESTIGE_LEVEL.toInt())
        val prestigeStatMult = 1.0f + (pLvl * 0.15f)
        val prestigeRewardMult = 1.0f + (pLvl * 0.10f)
        val prestigeWard = (pLvl * 3).coerceAtMost(25)

        val r3 = ranks[3]
        val r4 = ranks[4]
        val r5 = ranks[5]
        val r6 = ranks[6]
        val r7 = ranks[7]
        val r8 = ranks[8]

        return when (heroClass) {
            HeroClass.VANGUARD -> ComputedSkillTreeBonuses(
                flatAttack = r6 * 5,
                flatDefense = (r3 * 4) + (r7 * 5),
                flatMaxHp = (r3 * 26) + (r5 * 14),
                armorPiercePct = r6 * 4,
                solarSurgePct = r8 * 8,
                damageReductionPct = r4 * 3,
                thornsPct = r4 * 6,
                turnRegenHp = r5 * 6,
                expeditionWardPct = r7 * 4,
                sunshardMultiplier = 1.0f + (r8 * 0.06f),
                xpMultiplier = 1.0f + (r8 * 0.06f),
                prestigeStatMultiplier = prestigeStatMult,
                prestigeRewardMultiplier = prestigeRewardMult,
                prestigeExpeditionWardPct = prestigeWard
            )
            HeroClass.ARCANIST -> ComputedSkillTreeBonuses(
                flatAttack = (r3 * 5) + (r8 * 6),
                flatDefense = r6 * 3,
                flatMaxHp = r5 * 12,
                critChancePct = (r4 * 2) + (r8 * 3),
                armorPiercePct = r4 * 5,
                lifestealPct = r5 * 3,
                solarSurgePct = (r3 * 8) + (r8 * 10),
                damageReductionPct = r6 * 2,
                expeditionWardPct = r6 * 3,
                sunshardMultiplier = 1.0f + (r7 * 0.07f),
                xpMultiplier = 1.0f + (r7 * 0.07f),
                prestigeStatMultiplier = prestigeStatMult,
                prestigeRewardMultiplier = prestigeRewardMult,
                prestigeExpeditionWardPct = prestigeWard
            )
            HeroClass.RANGER -> ComputedSkillTreeBonuses(
                flatAttack = (r3 * 4) + (r8 * 6),
                flatDefense = r6 * 3,
                flatMaxHp = r5 * 14,
                critChancePct = (r3 * 4) + (r8 * 4),
                armorPiercePct = r4 * 5,
                lifestealPct = r8 * 3,
                solarSurgePct = r4 * 5,
                turnRegenHp = r6 * 5,
                expeditionWardPct = r5 * 4,
                sunshardMultiplier = 1.0f + (r7 * 0.07f),
                xpMultiplier = 1.0f + (r7 * 0.07f),
                prestigeStatMultiplier = prestigeStatMult,
                prestigeRewardMultiplier = prestigeRewardMult,
                prestigeExpeditionWardPct = prestigeWard
            )
            HeroClass.SHADOWBLADE -> ComputedSkillTreeBonuses(
                flatAttack = (r3 * 5) + (r8 * 6),
                flatDefense = r6 * 2,
                flatMaxHp = r4 * 12,
                critChancePct = (r3 * 3) + (r8 * 4),
                armorPiercePct = r5 * 6,
                lifestealPct = r4 * 4,
                solarSurgePct = (r5 * 5) + (r8 * 8),
                damageReductionPct = r6 * 3,
                expeditionWardPct = r6 * 3,
                sunshardMultiplier = 1.0f + (r7 * 0.07f),
                xpMultiplier = 1.0f + (r7 * 0.07f),
                prestigeStatMultiplier = prestigeStatMult,
                prestigeRewardMultiplier = prestigeRewardMult,
                prestigeExpeditionWardPct = prestigeWard
            )
        }
    }
}
