package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PetRarity(val label: String) {
    COMMON("Common"),
    RARE("Rare"),
    EPIC("Epic"),
    LEGENDARY("Legendary")
}

enum class PetType(
    val displayName: String,
    val title: String,
    val rarity: PetRarity,
    val coinCost: Long,
    val bonusHeadline: String,
    val lore: String,
    val unlockRequirementSummary: String
) {
    SUNFOX_KIT(
        displayName = "Sunfox Kit",
        title = "Amber Dune Scout",
        rarity = PetRarity.COMMON,
        coinCost = 10L,
        bonusHeadline = "+Attack %",
        lore = "Swift desert fox whose ember tail sharpens every hero strike.",
        unlockRequirementSummary = "Available immediately"
    ),
    AEGIS_SCARAB(
        displayName = "Aegis Scarab",
        title = "Gilded Shell Warden",
        rarity = PetRarity.COMMON,
        coinCost = 10L,
        bonusHeadline = "+Defense %",
        lore = "Armored bronze beetle that projects a solar ward around its bearer.",
        unlockRequirementSummary = "Available immediately"
    ),
    DUNE_FALCON(
        displayName = "Dune Falcon",
        title = "Thermal Stride Hawk",
        rarity = PetRarity.RARE,
        coinCost = 20L,
        bonusHeadline = "+Energy Charge & Max EN",
        lore = "Soars on midday updrafts to channel footsteps into rapid Stride Energy.",
        unlockRequirementSummary = "Walk 1,000 lifetime steps or reach Level 2"
    ),
    CINDER_LYNX(
        displayName = "Cinder Lynx",
        title = "Highland Solar Stalker",
        rarity = PetRarity.EPIC,
        coinCost = 30L,
        bonusHeadline = "+Solar Skill DMG %",
        lore = "Fierce summit predator that supercharges Solar Skill and Overdrive strikes.",
        unlockRequirementSummary = "Win 3 Highland Boss battles"
    ),
    OASIS_TORTOISE(
        displayName = "Oasis Tortoise",
        title = "Relic Shell Guardian",
        rarity = PetRarity.EPIC,
        coinCost = 40L,
        bonusHeadline = "+Max HP & Battle Regen",
        lore = "Ancient sandstone tortoise whose vitality restores HP after every victory.",
        unlockRequirementSummary = "Complete 1 Timed Expedition"
    ),
    SOLAR_PHOENIX(
        displayName = "Solar Phoenix",
        title = "Zenith Sovereign",
        rarity = PetRarity.LEGENDARY,
        coinCost = 60L,
        bonusHeadline = "+All Stats & Bonus XP",
        lore = "Mythic dawnbird whose radiant aura amplifies combat stats and XP earned.",
        unlockRequirementSummary = "Win 6 Boss battles & reach Level 4"
    );

    companion object {
        fun fromRaw(raw: String?): PetType? =
            entries.firstOrNull { it.name == raw }
    }
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
    val attackBonus: Int = 0,
    val defenseBonus: Int = 0,
    val hpBonus: Int = 0,
    val description: String
)

data class PetUnlockStatus(
    val unlocked: Boolean,
    val progressLabel: String,
    val progressFraction: Float
)

data class ComputedPetBonus(
    val attackPercentBonus: Float = 0f,
    val defensePercentBonus: Float = 0f,
    val maxHpPercentBonus: Float = 0f,
    val flatMaxEnergyBonus: Int = 0,
    val stepEnergyChargeMultiplier: Float = 1.0f,
    val solarSkillDamageMultiplier: Float = 1.0f,
    val postBattleHealPercent: Float = 0f,
    val xpGainMultiplier: Float = 1.0f,
    val gearAttackBonus: Int = 0,
    val gearDefenseBonus: Int = 0,
    val gearHpBonus: Int = 0,
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
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedType(): PetType = PetType.fromRaw(petType) ?: PetType.SUNFOX_KIT
}

object PetCatalog {

    const val MAX_PET_LEVEL = 50L

    val foodShop: List<PetFoodSpec> = listOf(
        PetFoodSpec(
            id = "food_sunberry_treat",
            name = "Sunberry Treat",
            coinCost = 5L,
            xpGain = 50L,
            description = "Crisp highland berries that grant +50 Pet XP to your equipped companion."
        ),
        PetFoodSpec(
            id = "food_spiced_jerky",
            name = "Spiced Trail Jerky",
            coinCost = 10L,
            xpGain = 120L,
            description = "Hearty expedition rations granting +120 Pet XP for rapid leveling."
        ),
        PetFoodSpec(
            id = "food_solar_ambrosia",
            name = "Solar Ambrosia",
            coinCost = 20L,
            xpGain = 280L,
            description = "Concentrated dawn nectar granting +280 Pet XP and surging companion power."
        )
    )

    val weaponShop: List<PetGearSpec> = listOf(
        PetGearSpec(
            id = "pet_wpn_bronze_fang",
            name = "Bronze Fang Claw",
            slot = PetGearSlot.WEAPON,
            tier = 1,
            coinCost = 10L,
            attackBonus = 6,
            description = "Forged bronze talons fitted for your equipped companion (+6 Hero ATK)."
        ),
        PetGearSpec(
            id = "pet_wpn_sunsteel_talon",
            name = "Sunsteel Talon",
            slot = PetGearSlot.WEAPON,
            tier = 2,
            coinCost = 20L,
            attackBonus = 14,
            description = "Radiant sunsteel edge that amplifies companion assists (+14 Hero ATK)."
        ),
        PetGearSpec(
            id = "pet_wpn_zenith_spire",
            name = "Zenith Flame Spire",
            slot = PetGearSlot.WEAPON,
            tier = 3,
            coinCost = 35L,
            attackBonus = 25,
            defenseBonus = 4,
            description = "Apex relic harness channeling searing dawn bursts (+25 Hero ATK, +4 DEF)."
        )
    )

    val armorShop: List<PetGearSpec> = listOf(
        PetGearSpec(
            id = "pet_arm_desert_harness",
            name = "Woven Desert Harness",
            slot = PetGearSlot.ARMOR,
            tier = 1,
            coinCost = 10L,
            defenseBonus = 6,
            hpBonus = 15,
            description = "Reinforced leather barding shielding your companion (+6 DEF, +15 HP)."
        ),
        PetGearSpec(
            id = "pet_arm_obsidian_barding",
            name = "Obsidian Scale Barding",
            slot = PetGearSlot.ARMOR,
            tier = 2,
            coinCost = 20L,
            defenseBonus = 14,
            hpBonus = 35,
            description = "Interlocking highland obsidian plates (+14 DEF, +35 HP)."
        ),
        PetGearSpec(
            id = "pet_arm_aegis_mantle",
            name = "Aegis Sunplate Mantle",
            slot = PetGearSlot.ARMOR,
            tier = 3,
            coinCost = 35L,
            defenseBonus = 24,
            hpBonus = 65,
            description = "Masterwork golden sunplate armor (+24 DEF, +65 HP)."
        )
    )

    fun weaponById(id: String): PetGearSpec? = weaponShop.firstOrNull { it.id == id }
    fun armorById(id: String): PetGearSpec? = armorShop.firstOrNull { it.id == id }

    fun evaluateUnlock(petType: PetType, profile: HeroProfile): PetUnlockStatus {
        return when (petType) {
            PetType.SUNFOX_KIT, PetType.AEGIS_SCARAB -> PetUnlockStatus(
                unlocked = true,
                progressLabel = "Unlocked — Ready to adopt",
                progressFraction = 1f
            )
            PetType.DUNE_FALCON -> {
                val stepsOk = profile.totalSteps >= 1000L
                val lvlOk = profile.level >= 2L
                val unlocked = stepsOk || lvlOk
                val stepFrac = (profile.totalSteps.toFloat() / 1000f).coerceIn(0f, 1f)
                val lvlFrac = (profile.level.toFloat() / 2f).coerceIn(0f, 1f)
                PetUnlockStatus(
                    unlocked = unlocked,
                    progressLabel = if (unlocked) {
                        "Condition Met"
                    } else {
                        "Steps ${profile.totalSteps}/1,000 or Level ${profile.level}/2"
                    },
                    progressFraction = if (unlocked) 1f else maxOf(stepFrac, lvlFrac)
                )
            }
            PetType.CINDER_LYNX -> {
                val wins = profile.trueBossWins
                val unlocked = wins >= 3L
                PetUnlockStatus(
                    unlocked = unlocked,
                    progressLabel = if (unlocked) "Condition Met" else "Boss Wins: $wins / 3",
                    progressFraction = (wins.toFloat() / 3f).coerceIn(0f, 1f)
                )
            }
            PetType.OASIS_TORTOISE -> {
                val done = profile.expeditionsCompleted
                val unlocked = done >= 1L
                PetUnlockStatus(
                    unlocked = unlocked,
                    progressLabel = if (unlocked) "Condition Met" else "Expeditions Completed: $done / 1",
                    progressFraction = (done.toFloat() / 1f).coerceIn(0f, 1f)
                )
            }
            PetType.SOLAR_PHOENIX -> {
                val wins = profile.trueBossWins
                val lvl = profile.level
                val unlocked = wins >= 6L && lvl >= 4L
                val combinedFrac = (
                    (wins.toFloat() / 6f).coerceIn(0f, 1f) +
                        (lvl.toFloat() / 4f).coerceIn(0f, 1f)
                    ) / 2f
                PetUnlockStatus(
                    unlocked = unlocked,
                    progressLabel = if (unlocked) "Condition Met" else "Boss Wins $wins/6 · Hero Level $lvl/4",
                    progressFraction = if (unlocked) 1f else combinedFrac
                )
            }
        }
    }

    fun computeBonus(pet: OwnedPet?): ComputedPetBonus {
        if (pet == null) return ComputedPetBonus()
        val type = pet.resolvedType()
        val extraLevels = (pet.level - 1L).coerceAtLeast(0L).toInt()
        val weapon = weaponById(pet.equippedWeaponId)
        val armor = armorById(pet.equippedArmorId)

        val gearAtk = (weapon?.attackBonus ?: 0) + (armor?.attackBonus ?: 0)
        val gearDef = (weapon?.defenseBonus ?: 0) + (armor?.defenseBonus ?: 0)
        val gearHp = (weapon?.hpBonus ?: 0) + (armor?.hpBonus ?: 0)

        return when (type) {
            PetType.SUNFOX_KIT -> {
                val pct = 6 + extraLevels * 2
                ComputedPetBonus(
                    attackPercentBonus = pct / 100f,
                    gearAttackBonus = gearAtk,
                    gearDefenseBonus = gearDef,
                    gearHpBonus = gearHp,
                    summaryText = "+$pct% Hero Attack"
                )
            }
            PetType.AEGIS_SCARAB -> {
                val pct = 8 + extraLevels * 2
                ComputedPetBonus(
                    defensePercentBonus = pct / 100f,
                    gearAttackBonus = gearAtk,
                    gearDefenseBonus = gearDef,
                    gearHpBonus = gearHp,
                    summaryText = "+$pct% Hero Defense"
                )
            }
            PetType.DUNE_FALCON -> {
                val chargePct = 15 + extraLevels * 3
                val flatEn = 10 + extraLevels * 4
                ComputedPetBonus(
                    flatMaxEnergyBonus = flatEn,
                    stepEnergyChargeMultiplier = 1f + (chargePct / 100f),
                    gearAttackBonus = gearAtk,
                    gearDefenseBonus = gearDef,
                    gearHpBonus = gearHp,
                    summaryText = "+$chargePct% Step Energy · +$flatEn Max EN"
                )
            }
            PetType.CINDER_LYNX -> {
                val skillPct = 12 + extraLevels * 3
                ComputedPetBonus(
                    solarSkillDamageMultiplier = 1f + (skillPct / 100f),
                    gearAttackBonus = gearAtk,
                    gearDefenseBonus = gearDef,
                    gearHpBonus = gearHp,
                    summaryText = "+$skillPct% Solar Skill & Overdrive DMG"
                )
            }
            PetType.OASIS_TORTOISE -> {
                val hpPct = 10 + extraLevels * 3
                val regenPct = (5 + extraLevels).coerceAtMost(35)
                ComputedPetBonus(
                    maxHpPercentBonus = hpPct / 100f,
                    postBattleHealPercent = regenPct / 100f,
                    gearAttackBonus = gearAtk,
                    gearDefenseBonus = gearDef,
                    gearHpBonus = gearHp,
                    summaryText = "+$hpPct% Max HP · +$regenPct% Post-Battle Heal"
                )
            }
            PetType.SOLAR_PHOENIX -> {
                val allPct = 8 + extraLevels * 2
                val xpPct = 15 + extraLevels * 3
                ComputedPetBonus(
                    attackPercentBonus = allPct / 100f,
                    defensePercentBonus = allPct / 100f,
                    maxHpPercentBonus = allPct / 100f,
                    xpGainMultiplier = 1f + (xpPct / 100f),
                    gearAttackBonus = gearAtk,
                    gearDefenseBonus = gearDef,
                    gearHpBonus = gearHp,
                    summaryText = "+$allPct% All Combat Stats · +$xpPct% Bonus XP"
                )
            }
        }
    }

    fun previewBonusAtLevel1(petType: PetType): String =
        computeBonus(OwnedPet(petType = petType.name, level = 1L)).summaryText

    fun applyFoodXp(pet: OwnedPet, xpGain: Long): OwnedPet {
        var lvl = pet.level.coerceIn(1L, MAX_PET_LEVEL)
        var xp = pet.xp + xpGain
        var needed = pet.xpToNextLevel.coerceAtLeast(60L)

        while (xp >= needed && lvl < MAX_PET_LEVEL) {
            xp -= needed
            lvl += 1L
            needed = 60L + (lvl - 1L) * 40L
        }
        if (lvl >= MAX_PET_LEVEL) {
            xp = xp.coerceAtMost(needed)
        }
        return pet.copy(
            level = lvl,
            xp = xp.coerceIn(0L, 1_000_000L),
            xpToNextLevel = needed.coerceIn(1L, 1_000_000L)
        )
    }
}
