package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.min
import kotlin.math.roundToInt

enum class PetRarity(
    val label: String,
    val tierRank: Int
) {
    RARE(label = "Rare", tierRank = 1),
    EPIC(label = "Epic", tierRank = 2),
    SOLAR(label = "Solar", tierRank = 3),
    LEGENDARY(label = "Legendary", tierRank = 4)
}

enum class PetType(
    val displayName: String,
    val title: String,
    val rarity: PetRarity,
    val lore: String,
    val coinCost: Long,
    val unlockRequirementSummary: String,
    val elementalAffinity: ElementalAffinity,
    val activeSkillName: String,
    val activeSkillSummary: String,
    val comboDamageMult: Float,
    val comboHealPct: Float
) {
    SUNFOX_KIT(
        displayName = "Sunfox Kit",
        title = "Canyon Ember Scout",
        rarity = PetRarity.RARE,
        lore = "A nimble desert fox whose glowing tail guides striders toward hidden Sunshard veins.",
        coinCost = 10L,
        unlockRequirementSummary = "Defeat 1 True Boss",
        elementalAffinity = ElementalAffinity.EMBER,
        activeSkillName = "Sunfox Ember Volley",
        activeSkillSummary = "160% Ember DMG + inflicts 3-turn Scorch Burn & +15 Stride Energy",
        comboDamageMult = 1.60f,
        comboHealPct = 0.08f
    ),
    AEGIS_SCARAB(
        displayName = "Aegis Scarab",
        title = "Obsidian Carapace Ward",
        rarity = PetRarity.RARE,
        lore = "Forged from living gold and basalt, it projects a hardened barrier around its companion.",
        coinCost = 20L,
        unlockRequirementSummary = "Reach Hero Level 3",
        elementalAffinity = ElementalAffinity.TERRA,
        activeSkillName = "Obsidian Bulwark Slam",
        activeSkillSummary = "145% Terra DMG + grants 30% Max HP Aegis Barrier & Sunders enemy armor",
        comboDamageMult = 1.45f,
        comboHealPct = 0.15f
    ),
    DUNE_FALCON(
        displayName = "Dune Falcon",
        title = "Highland Windrider",
        rarity = PetRarity.EPIC,
        lore = "Rides thermal updrafts above the canyon rim, converting every step into extra Solar Stride Energy.",
        coinCost = 30L,
        unlockRequirementSummary = "Walk 1,500 Total Steps",
        elementalAffinity = ElementalAffinity.FROST,
        activeSkillName = "Glacial Gale Dive",
        activeSkillSummary = "175% Frost DMG + inflicts Frostbite (-25% Enemy ATK) & +25 Stride Energy",
        comboDamageMult = 1.75f,
        comboHealPct = 0.10f
    ),
    CINDER_LYNX(
        displayName = "Cinder Lynx",
        title = "Fierce Apex Stalker",
        rarity = PetRarity.EPIC,
        lore = "Prowls beside its hero with blazing claws that amplify every strike and solar overdrive.",
        coinCost = 40L,
        unlockRequirementSummary = "Defeat 3 True Bosses",
        elementalAffinity = ElementalAffinity.VOID,
        activeSkillName = "Eclipse Predator Rend",
        activeSkillSummary = "Guaranteed Crit 210% Void DMG + inflicts 3-turn Bleed Poison & 20% Leech",
        comboDamageMult = 2.10f,
        comboHealPct = 0.14f
    ),
    OASIS_TORTOISE(
        displayName = "Oasis Tortoise",
        title = "Ancient Vitality Keeper",
        rarity = PetRarity.SOLAR,
        lore = "Carries a miniature sunlit oasis on its shell, fortifying your hero's stamina and life force.",
        coinCost = 50L,
        unlockRequirementSummary = "Reach Hero Level 6",
        elementalAffinity = ElementalAffinity.TERRA,
        activeSkillName = "Sanctuary Oasis Wave",
        activeSkillSummary = "140% Terra DMG + restores 32% Max HP, grants Regen & cleanses Battle Wounds",
        comboDamageMult = 1.40f,
        comboHealPct = 0.32f
    ),
    SOLAR_PHOENIX(
        displayName = "Solar Phoenix",
        title = "Zenith Ascendant",
        rarity = PetRarity.LEGENDARY,
        lore = "Born from the crown of the Sunspire, it blesses every battle victory with radiant XP and power.",
        coinCost = 80L,
        unlockRequirementSummary = "Defeat 8 True Bosses & Reach Level 10",
        elementalAffinity = ElementalAffinity.SOLAR,
        activeSkillName = "Zenith Supernova Rebirth",
        activeSkillSummary = "250% Solar DMG + inflicts Burn & Stun, restores 25% HP & +40 Valor",
        comboDamageMult = 2.50f,
        comboHealPct = 0.25f
    )
}

enum class PetGearSlot(val label: String) {
    WEAPON("Pet Weapon"),
    ARMOR("Pet Armor")
}

data class PetFoodSpec(
    val id: String,
    val name: String,
    val coinCost: Long,
    val xpGain: Long,
    val description: String
)

data class PetGearSpec(
    val id: String,
    val name: String,
    val slot: PetGearSlot,
    val tier: Int,
    val coinCost: Long,
    val attackBonus: Int,
    val defenseBonus: Int,
    val hpBonus: Int,
    val description: String
)

data class PetUnlockEvaluation(
    val unlocked: Boolean,
    val progressLabel: String,
    val progressFraction: Float
)

data class ComputedPetBonus(
    val flatAttack: Int = 0,
    val flatDefense: Int = 0,
    val flatMaxHp: Int = 0,
    val flatMaxEnergy: Int = 0,
    val attackMultiplier: Float = 1.0f,
    val defenseMultiplier: Float = 1.0f,
    val maxHpMultiplier: Float = 1.0f,
    val stepEnergyMultiplier: Float = 1.0f,
    val xpMultiplier: Float = 1.0f,
    val sunshardMultiplier: Float = 1.0f,
    val summaryText: String = "No companion equipped"
)

@Keep
@IgnoreExtraProperties
data class OwnedPet(
    @DocumentId val id: String = "",
    val userId: String = "",
    val petType: String = PetType.SUNFOX_KIT.name,
    val level: Long = 1L,
    val xp: Long = 0L,
    val xpToNextLevel: Long = 60L,
    val equippedWeaponId: String = "",
    val equippedArmorId: String = "",
    val evolutionStage: Long = 1L,
    val evolutionBranch: String = PetEvolutionBranch.NONE.id,
    val bondXp: Long = 0L,
    val stepsWalkedWithPet: Long = 0L,
    val lastCareDateKey: String = "",
    val dailyCareActions: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedType(): PetType =
        PetType.entries.firstOrNull { it.name == petType } ?: PetType.SUNFOX_KIT

    fun resolvedStage(): Int = evolutionStage.toInt().coerceIn(1, 3)

    fun resolvedBranch(): PetEvolutionBranch = PetEvolutionBranch.fromRaw(evolutionBranch)

    fun resolvedForm(): PetEvolutionFormSpec =
        PetEvolutionCatalog.resolveForm(resolvedType(), resolvedStage(), resolvedBranch())
}

data class PetEvolutionCatalystOffer(
    val id: String,
    val name: String,
    val description: String,
    val coinCost: Long,
    val requiredMenagerieLevel: Int,
    val grantsEvolutionShards: Long = 0L,
    val grantsMythicCores: Long = 0L
)

object PetCatalog {
    const val MAX_PET_LEVEL = 50L

    val catalystShop: List<PetEvolutionCatalystOffer> = listOf(
        PetEvolutionCatalystOffer(
            id = "cat_evolution_shard",
            name = "Sunstone Evolution Shard",
            description = "Rare solar crystal for Stage 2 & Stage 3 Pet Evolution (8 needed for Stage 2, 12 for Stage 3).",
            coinCost = 35L,
            requiredMenagerieLevel = 2,
            grantsEvolutionShards = 1L
        ),
        PetEvolutionCatalystOffer(
            id = "cat_evolution_bundle",
            name = "Awakening Catalyst Trio (x3)",
            description = "Merchant crate of 3 Evolution Shards reserved for seasoned Menagerie patrons.",
            coinCost = 95L,
            requiredMenagerieLevel = 3,
            grantsEvolutionShards = 3L
        ),
        PetEvolutionCatalystOffer(
            id = "cat_mythic_core",
            name = "Mythic Apex Core",
            description = "Endgame celestial core for Stage 3 Apex Mythic Ascension (5 needed per pet).",
            coinCost = 120L,
            requiredMenagerieLevel = 4,
            grantsMythicCores = 1L
        )
    )

    val foodShop: List<PetFoodSpec> = listOf(
        PetFoodSpec(
            id = "food_sunberry",
            name = "Sunberry Biscuit",
            coinCost = 5L,
            xpGain = 35L,
            description = "Crisp canyon biscuit baked with golden sunberries (+35 Pet XP)."
        ),
        PetFoodSpec(
            id = "food_golden_fig",
            name = "Golden Oasis Fig",
            coinCost = 10L,
            xpGain = 85L,
            description = "Rich honeyed fig harvested from high-altitude oases (+85 Pet XP)."
        ),
        PetFoodSpec(
            id = "food_radiant_nectar",
            name = "Radiant Solar Nectar",
            coinCost = 20L,
            xpGain = 200L,
            description = "Pure condensed sunlight that rapidly empowers companions (+200 Pet XP)."
        ),
        PetFoodSpec(
            id = "food_sovereign_feast",
            name = "Sovereign Phoenix Feast",
            coinCost = 45L,
            xpGain = 520L,
            description = "Lavish royal banquet that surges companion mastery (+520 Pet XP)."
        )
    )

    val weaponShop: List<PetGearSpec> = listOf(
        PetGearSpec(
            id = "pet_wpn_bronze_fang",
            name = "Bronze Fang Collar",
            slot = PetGearSlot.WEAPON,
            tier = 1,
            coinCost = 10L,
            attackBonus = 5,
            defenseBonus = 0,
            hpBonus = 0,
            description = "Tempered bronze spikes that add +5 Hero Attack while equipped."
        ),
        PetGearSpec(
            id = "pet_wpn_sunsteel_talon",
            name = "Sunsteel Talon Crest",
            slot = PetGearSlot.WEAPON,
            tier = 2,
            coinCost = 25L,
            attackBonus = 12,
            defenseBonus = 2,
            hpBonus = 0,
            description = "Solar-forged talons granting +12 Attack and +2 Defense."
        ),
        PetGearSpec(
            id = "pet_wpn_zenith_spire",
            name = "Zenith Spire Horn",
            slot = PetGearSlot.WEAPON,
            tier = 3,
            coinCost = 45L,
            attackBonus = 24,
            defenseBonus = 5,
            hpBonus = 15,
            description = "Relic horn channeling pure sunlight for +24 Attack, +5 Defense, and +15 Max HP."
        )
    )

    val armorShop: List<PetGearSpec> = listOf(
        PetGearSpec(
            id = "pet_arm_desert_harness",
            name = "Sandstone Harness",
            slot = PetGearSlot.ARMOR,
            tier = 1,
            coinCost = 10L,
            attackBonus = 0,
            defenseBonus = 4,
            hpBonus = 18,
            description = "Light leather harness adding +4 Defense and +18 Max HP."
        ),
        PetGearSpec(
            id = "pet_arm_obsidian_barding",
            name = "Obsidian Scale Barding",
            slot = PetGearSlot.ARMOR,
            tier = 2,
            coinCost = 25L,
            attackBonus = 2,
            defenseBonus = 10,
            hpBonus = 40,
            description = "Interlocking basalt plates granting +10 Defense, +40 Max HP, and +2 Attack."
        ),
        PetGearSpec(
            id = "pet_arm_aegis_mantle",
            name = "Solar Aegis Mantle",
            slot = PetGearSlot.ARMOR,
            tier = 3,
            coinCost = 45L,
            attackBonus = 5,
            defenseBonus = 20,
            hpBonus = 85,
            description = "Masterwork sanctuary armor granting +20 Defense, +85 Max HP, and +5 Attack."
        )
    )

    fun weaponById(id: String): PetGearSpec? = weaponShop.firstOrNull { it.id == id }
    fun armorById(id: String): PetGearSpec? = armorShop.firstOrNull { it.id == id }

    fun evaluateUnlock(type: PetType, profile: HeroProfile): PetUnlockEvaluation {
        return when (type) {
            PetType.SUNFOX_KIT -> {
                val cur = profile.trueBossWins.coerceAtLeast(profile.bossesDefeated.coerceAtMost(1L))
                val unlocked = cur >= 1L
                PetUnlockEvaluation(
                    unlocked = unlocked,
                    progressLabel = "${min(cur, 1L)} / 1 Boss Defeated",
                    progressFraction = (cur.toFloat() / 1f).coerceIn(0f, 1f)
                )
            }
            PetType.AEGIS_SCARAB -> {
                val cur = profile.level
                val unlocked = cur >= 3L
                PetUnlockEvaluation(
                    unlocked = unlocked,
                    progressLabel = "Level ${min(cur, 3L)} / 3",
                    progressFraction = (cur.toFloat() / 3f).coerceIn(0f, 1f)
                )
            }
            PetType.DUNE_FALCON -> {
                val cur = profile.totalSteps
                val unlocked = cur >= 1500L
                PetUnlockEvaluation(
                    unlocked = unlocked,
                    progressLabel = "${min(cur, 1500L)} / 1,500 Steps",
                    progressFraction = (cur.toFloat() / 1500f).coerceIn(0f, 1f)
                )
            }
            PetType.CINDER_LYNX -> {
                val cur = profile.trueBossWins
                val unlocked = cur >= 3L
                PetUnlockEvaluation(
                    unlocked = unlocked,
                    progressLabel = "${min(cur, 3L)} / 3 True Bosses",
                    progressFraction = (cur.toFloat() / 3f).coerceIn(0f, 1f)
                )
            }
            PetType.OASIS_TORTOISE -> {
                val cur = profile.level
                val unlocked = cur >= 6L
                PetUnlockEvaluation(
                    unlocked = unlocked,
                    progressLabel = "Level ${min(cur, 6L)} / 6",
                    progressFraction = (cur.toFloat() / 6f).coerceIn(0f, 1f)
                )
            }
            PetType.SOLAR_PHOENIX -> {
                val bossPart = (profile.trueBossWins.toFloat() / 8f).coerceIn(0f, 1f)
                val lvlPart = (profile.level.toFloat() / 10f).coerceIn(0f, 1f)
                val unlocked = profile.trueBossWins >= 8L && profile.level >= 10L
                PetUnlockEvaluation(
                    unlocked = unlocked,
                    progressLabel = "${min(profile.trueBossWins, 8L)}/8 Bosses · Lv.${min(profile.level, 10L)}/10",
                    progressFraction = ((bossPart + lvlPart) / 2f).coerceIn(0f, 1f)
                )
            }
        }
    }

    fun xpRequiredForLevel(level: Long): Long = 85L + (level - 1L).coerceAtLeast(0L) * 65L

    fun applyFoodXp(pet: OwnedPet, xpGain: Long): OwnedPet {
        var lvl = pet.level.coerceIn(1L, MAX_PET_LEVEL)
        var curXp = (pet.xp + xpGain).coerceAtLeast(0L)
        var needed = xpRequiredForLevel(lvl)

        while (curXp >= needed && lvl < MAX_PET_LEVEL) {
            curXp -= needed
            lvl += 1L
            needed = xpRequiredForLevel(lvl)
        }
        if (lvl >= MAX_PET_LEVEL) {
            curXp = 0L
            needed = xpRequiredForLevel(MAX_PET_LEVEL)
        }
        return pet.copy(
            level = lvl,
            xp = curXp,
            xpToNextLevel = needed
        )
    }

    fun computeBonus(pet: OwnedPet?): ComputedPetBonus {
        if (pet == null) return ComputedPetBonus()
        val type = pet.resolvedType()
        val lvl = pet.level.toInt().coerceIn(1, MAX_PET_LEVEL.toInt())
        val form = pet.resolvedForm()
        val evoMult = form.statScaleMultiplier
        val branch = pet.resolvedBranch()
        val stageTag = if (form.stage > 1) " [S${form.stage} ${branch.label}]" else ""
        val weapon = weaponById(pet.equippedWeaponId)
        val armor = armorById(pet.equippedArmorId)

        val gearAtk = (weapon?.attackBonus ?: 0) + (armor?.attackBonus ?: 0)
        val gearDef = (weapon?.defenseBonus ?: 0) + (armor?.defenseBonus ?: 0)
        val gearHp = (weapon?.hpBonus ?: 0) + (armor?.hpBonus ?: 0)
        val branchAtkExtra = if (form.stage >= 2 && branch == PetEvolutionBranch.VANGUARD) form.stage * 5 else 0
        val branchDefExtra = if (form.stage >= 2 && branch == PetEvolutionBranch.MYSTIC) form.stage * 4 else 0
        val branchEnergyExtra = if (form.stage >= 2 && branch == PetEvolutionBranch.MYSTIC) form.stage * 8 else 0

        return when (type) {
            PetType.SUNFOX_KIT -> {
                val shardPct = ((10 + lvl * 3) * evoMult).roundToInt()
                val flatAtk = ((2 + lvl) * evoMult).roundToInt() + gearAtk + branchAtkExtra
                ComputedPetBonus(
                    flatAttack = flatAtk,
                    flatDefense = gearDef + branchDefExtra,
                    flatMaxHp = gearHp,
                    flatMaxEnergy = branchEnergyExtra,
                    sunshardMultiplier = 1.0f + (shardPct / 100f),
                    summaryText = "+$shardPct% Sunshards · +$flatAtk ATK$stageTag"
                )
            }
            PetType.AEGIS_SCARAB -> {
                val defPct = ((8 + lvl * 2) * evoMult).roundToInt()
                val flatDef = ((4 + lvl * 2) * evoMult).roundToInt() + gearDef + branchDefExtra
                val flatHp = ((10 + lvl * 4) * evoMult).roundToInt() + gearHp
                ComputedPetBonus(
                    flatAttack = gearAtk + branchAtkExtra,
                    flatDefense = flatDef,
                    flatMaxHp = flatHp,
                    flatMaxEnergy = branchEnergyExtra,
                    defenseMultiplier = 1.0f + (defPct / 100f),
                    summaryText = "+$defPct% DEF · +$flatDef DEF · +$flatHp HP$stageTag"
                )
            }
            PetType.DUNE_FALCON -> {
                val stepPct = ((12 + lvl * 3) * evoMult).roundToInt()
                val flatEnergy = ((10 + lvl * 3) * evoMult).roundToInt() + branchEnergyExtra
                ComputedPetBonus(
                    flatAttack = (2 * evoMult).roundToInt() + gearAtk + branchAtkExtra,
                    flatDefense = gearDef + branchDefExtra,
                    flatMaxHp = gearHp,
                    flatMaxEnergy = flatEnergy,
                    stepEnergyMultiplier = 1.0f + (stepPct / 100f),
                    summaryText = "+$stepPct% Step Energy · +$flatEnergy Max EN$stageTag"
                )
            }
            PetType.CINDER_LYNX -> {
                val atkPct = ((10 + lvl * 2) * evoMult).roundToInt()
                val flatAtk = ((6 + lvl * 3) * evoMult).roundToInt() + gearAtk + branchAtkExtra
                ComputedPetBonus(
                    flatAttack = flatAtk,
                    flatDefense = gearDef + branchDefExtra,
                    flatMaxHp = gearHp,
                    flatMaxEnergy = branchEnergyExtra,
                    attackMultiplier = 1.0f + (atkPct / 100f),
                    summaryText = "+$atkPct% ATK · +$flatAtk ATK$stageTag"
                )
            }
            PetType.OASIS_TORTOISE -> {
                val hpPct = ((12 + lvl * 3) * evoMult).roundToInt()
                val flatHp = ((22 + lvl * 8) * evoMult).roundToInt() + gearHp
                val flatDef = ((3 + lvl) * evoMult).roundToInt() + gearDef + branchDefExtra
                ComputedPetBonus(
                    flatAttack = gearAtk + branchAtkExtra,
                    flatDefense = flatDef,
                    flatMaxHp = flatHp,
                    flatMaxEnergy = branchEnergyExtra,
                    maxHpMultiplier = 1.0f + (hpPct / 100f),
                    summaryText = "+$hpPct% Max HP · +$flatHp HP · +$flatDef DEF$stageTag"
                )
            }
            PetType.SOLAR_PHOENIX -> {
                val allPct = ((10 + lvl * 2) * evoMult).roundToInt()
                val xpPct = ((12 + lvl * 3) * evoMult).roundToInt()
                val flatAtk = ((8 + lvl * 3) * evoMult).roundToInt() + gearAtk + branchAtkExtra
                val flatDef = ((6 + lvl * 2) * evoMult).roundToInt() + gearDef + branchDefExtra
                val flatHp = ((20 + lvl * 6) * evoMult).roundToInt() + gearHp
                ComputedPetBonus(
                    flatAttack = flatAtk,
                    flatDefense = flatDef,
                    flatMaxHp = flatHp,
                    flatMaxEnergy = ((15 + lvl * 2) * evoMult).roundToInt() + branchEnergyExtra,
                    attackMultiplier = 1.0f + (allPct / 100f),
                    defenseMultiplier = 1.0f + (allPct / 100f),
                    maxHpMultiplier = 1.0f + (allPct / 100f),
                    xpMultiplier = 1.0f + (xpPct / 100f),
                    sunshardMultiplier = 1.0f + (allPct / 100f),
                    summaryText = "+$allPct% All Stats/Shards · +$xpPct% XP$stageTag"
                )
            }
        }
    }

    fun previewBonusAtLevel1(type: PetType): String {
        val sample = OwnedPet(petType = type.name, level = 1L)
        return computeBonus(sample).summaryText
    }
}
