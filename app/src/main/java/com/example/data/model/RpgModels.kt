package com.example.data.model

import com.example.domain.ProgressionEngine
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.roundToInt

import androidx.annotation.Keep

enum class HeroClass(
    val displayTitle: String,
    val subtitle: String,
    val perkSummary: String,
    val defaultAffinity: ElementalAffinity,
    val starterWeaponName: String,
    val accentHex: Long
) {
    VANGUARD(
        displayTitle = "Vanguard",
        subtitle = "Solar Shieldbearer",
        perkSummary = "+20% Guard Mitigation & +15% Defense",
        defaultAffinity = ElementalAffinity.SOLAR,
        starterWeaponName = "Sun-Worn Broadsword",
        accentHex = 0xFFD97706L
    ),
    ARCANIST(
        displayTitle = "Arcanist",
        subtitle = "Pyre & Astral Spellweaver",
        perkSummary = "+25% Active Skill DMG & Elemental Burn Mastery",
        defaultAffinity = ElementalAffinity.EMBER,
        starterWeaponName = "Apprentice Ember Staff",
        accentHex = 0xFF7C3AEDL
    ),
    RANGER(
        displayTitle = "Ranger",
        subtitle = "Gale Horizon Marksman",
        perkSummary = "+14% Crit Chance & +8% Expedition Survival",
        defaultAffinity = ElementalAffinity.FROST,
        starterWeaponName = "Weathered Yew Bow",
        accentHex = 0xFF0D9488L
    ),
    SHADOWBLADE(
        displayTitle = "Shadowblade",
        subtitle = "Eclipse Twin-Dagger Assassin",
        perkSummary = "+14% Armor Pierce & +10% Lifesteal",
        defaultAffinity = ElementalAffinity.VOID,
        starterWeaponName = "Worn Iron Twin-Dirk",
        accentHex = 0xFFE11D48L
    );

    val title: String get() = displayTitle
    val roleTag: String get() = subtitle
    val passiveSummary: String get() = perkSummary

    companion object {
        /**
         * Resolves both the 4 remade classes and legacy saved class names (STRIDER -> RANGER, SUNBLADE -> ARCANIST).
         */
        fun fromRaw(raw: String?): HeroClass = when (raw?.trim()?.uppercase(Locale.US)) {
            "VANGUARD" -> VANGUARD
            "ARCANIST", "SUNBLADE" -> ARCANIST
            "RANGER", "STRIDER" -> RANGER
            "SHADOWBLADE" -> SHADOWBLADE
            else -> VANGUARD
        }
    }
}

enum class ElementalAffinity(
    val label: String,
    val title: String,
    val runeName: String,
    val procSummary: String
) {
    SOLAR(
        label = "Solar",
        title = "Radiant Sun",
        runeName = "Rune of Helios",
        procSummary = "Inflicts Blind/Sunder & +15% Ultimate Charge"
    ),
    EMBER(
        label = "Ember",
        title = "Scorching Flame",
        runeName = "Rune of Cinder",
        procSummary = "Inflicts 3-turn Scorch Burn DoT"
    ),
    FROST(
        label = "Frost",
        title = "Glacial Rime",
        runeName = "Rune of Rime",
        procSummary = "Inflicts Frostbite (-25% Enemy ATK)"
    ),
    TERRA(
        label = "Terra",
        title = "Obsidian Earth",
        runeName = "Rune of Monolith",
        procSummary = "Inflicts Armor Sunder & Stun chance"
    ),
    VOID(
        label = "Void",
        title = "Eclipse Shadow",
        runeName = "Rune of Eclipse",
        procSummary = "Inflicts Void Poison & +10% Lifesteal"
    );

    fun beats(): ElementalAffinity = when (this) {
        SOLAR -> VOID
        VOID -> EMBER
        EMBER -> FROST
        FROST -> TERRA
        TERRA -> SOLAR
    }

    fun weakTo(): ElementalAffinity = when (this) {
        SOLAR -> TERRA
        TERRA -> FROST
        FROST -> EMBER
        EMBER -> VOID
        VOID -> SOLAR
    }

    fun damageMultiplierAgainst(defender: ElementalAffinity): Float = when {
        this.beats() == defender -> 1.35f
        this.weakTo() == defender -> 0.75f
        else -> 1.0f
    }

    fun weakAgainst(): ElementalAffinity = beats()

    val symbol: String
        get() = when (this) {
            SOLAR -> "☀"
            EMBER -> "🔥"
            FROST -> "❄"
            TERRA -> "⛰"
            VOID -> "🌑"
        }
}

enum class ClassSpecialization(
    val id: String,
    val baseClass: HeroClass,
    val title: String,
    val affinity: ElementalAffinity,
    val unlockLevel: Int,
    val passiveSummary: String,
    val ultimateName: String,
    val ultimateDescription: String,
    val atkMultBonus: Float,
    val defMultBonus: Float,
    val critBonusPct: Int,
    val lifestealBonusPct: Int,
    val pierceBonusPct: Int,
    val regenBonusHp: Int
) {
    SUNSHIELD_PALADIN(
        id = "SUNSHIELD_PALADIN",
        baseClass = HeroClass.VANGUARD,
        title = "Sunshield Paladin",
        affinity = ElementalAffinity.SOLAR,
        unlockLevel = 3,
        passiveSummary = "+18% DEF, +12 Turn Regen & Aegis Barrier on Guard",
        ultimateName = "Solar Sanctuary Aegis",
        ultimateDescription = "250% Solar DMG + grants 40% Max HP Aegis Barrier & +20% Heal",
        atkMultBonus = 0.08f,
        defMultBonus = 0.18f,
        critBonusPct = 4,
        lifestealBonusPct = 0,
        pierceBonusPct = 5,
        regenBonusHp = 12
    ),
    OBSIDIAN_JUGGERNAUT(
        id = "OBSIDIAN_JUGGERNAUT",
        baseClass = HeroClass.VANGUARD,
        title = "Obsidian Juggernaut",
        affinity = ElementalAffinity.TERRA,
        unlockLevel = 3,
        passiveSummary = "+16% ATK, +18% Armor Pierce & Seismic Stun procs",
        ultimateName = "Cataclysmic Earthshatter",
        ultimateDescription = "300% Terra DMG + inflicts 1-turn Stun & 3-turn Armor Sunder",
        atkMultBonus = 0.16f,
        defMultBonus = 0.10f,
        critBonusPct = 6,
        lifestealBonusPct = 4,
        pierceBonusPct = 18,
        regenBonusHp = 4
    ),
    GALE_RANGER(
        id = "GALE_RANGER",
        baseClass = HeroClass.RANGER,
        title = "Gale Horizon Marksman",
        affinity = ElementalAffinity.FROST,
        unlockLevel = 3,
        passiveSummary = "+14% Crit Chance, +10% Armor Pierce & Frostbite Volley",
        ultimateName = "Glacial Tempest Volley",
        ultimateDescription = "320% Frost multi-hit DMG + inflicts 3-turn Frostbite (-25% Enemy ATK)",
        atkMultBonus = 0.14f,
        defMultBonus = 0.06f,
        critBonusPct = 14,
        lifestealBonusPct = 4,
        pierceBonusPct = 10,
        regenBonusHp = 4
    ),
    STORM_WARDEN(
        id = "STORM_WARDEN",
        baseClass = HeroClass.RANGER,
        title = "Suntrail Warden",
        affinity = ElementalAffinity.TERRA,
        unlockLevel = 3,
        passiveSummary = "+14% DEF, +12% ATK & +10 Turn Regen with Snaring Shot",
        ultimateName = "Shatterthorn Barrage",
        ultimateDescription = "300% Terra DMG + inflicts 1-turn Stun & heals 18% Max HP",
        atkMultBonus = 0.12f,
        defMultBonus = 0.14f,
        critBonusPct = 8,
        lifestealBonusPct = 6,
        pierceBonusPct = 10,
        regenBonusHp = 10
    ),
    MIRAGE_ASSASSIN(
        id = "MIRAGE_ASSASSIN",
        baseClass = HeroClass.SHADOWBLADE,
        title = "Mirage Eclipse Assassin",
        affinity = ElementalAffinity.VOID,
        unlockLevel = 3,
        passiveSummary = "+20% Armor Pierce, +12% Lifesteal & Lethal Void Crits",
        ultimateName = "Eclipse Phantom Execution",
        ultimateDescription = "Guaranteed Critical 350% Void DMG + 3-turn Poison & 25% Lifesteal",
        atkMultBonus = 0.18f,
        defMultBonus = 0.0f,
        critBonusPct = 12,
        lifestealBonusPct = 12,
        pierceBonusPct = 20,
        regenBonusHp = 0
    ),
    NIGHTFALL_REAPER(
        id = "NIGHTFALL_REAPER",
        baseClass = HeroClass.SHADOWBLADE,
        title = "Cinderblade Reaper",
        affinity = ElementalAffinity.EMBER,
        unlockLevel = 3,
        passiveSummary = "+20% ATK, +14% Crit & Scorching Shadow Flurry",
        ultimateName = "Ashfall Thousand Cuts",
        ultimateDescription = "345% Ember DMG + inflicts 3-turn Scorch Burn & Armor Sunder",
        atkMultBonus = 0.20f,
        defMultBonus = 0.04f,
        critBonusPct = 14,
        lifestealBonusPct = 8,
        pierceBonusPct = 14,
        regenBonusHp = 0
    ),
    DAWN_ARCANIST(
        id = "DAWN_ARCANIST",
        baseClass = HeroClass.ARCANIST,
        title = "Dawn Pyre Arcanist",
        affinity = ElementalAffinity.EMBER,
        unlockLevel = 3,
        passiveSummary = "+22% ATK & Skill Overdrive + Scorching Burn DoT",
        ultimateName = "Supernova Firestorm",
        ultimateDescription = "340% Ember DMG + inflicts 4-turn Scorching Burn & Sunder",
        atkMultBonus = 0.22f,
        defMultBonus = 0.04f,
        critBonusPct = 10,
        lifestealBonusPct = 4,
        pierceBonusPct = 12,
        regenBonusHp = 4
    ),
    ECLIPSE_INQUISITOR(
        id = "ECLIPSE_INQUISITOR",
        baseClass = HeroClass.ARCANIST,
        title = "Eclipse Void Inquisitor",
        affinity = ElementalAffinity.VOID,
        unlockLevel = 3,
        passiveSummary = "+16% Lifesteal, +12% ATK & Soul-Drain Barrier",
        ultimateName = "Void Judgement Nova",
        ultimateDescription = "300% Void DMG + drains 35% of damage dealt as Hero HP & Aegis Shield",
        atkMultBonus = 0.12f,
        defMultBonus = 0.12f,
        critBonusPct = 8,
        lifestealBonusPct = 16,
        pierceBonusPct = 12,
        regenBonusHp = 8
    );

    val ultimateSummary: String
        get() = ultimateDescription

    companion object {
        fun forClass(heroClass: HeroClass): List<ClassSpecialization> =
            entries.filter { it.baseClass == heroClass }

        fun fromIdOrDefault(id: String, heroClass: HeroClass): ClassSpecialization {
            val matched = entries.firstOrNull { (it.id == id || it.name == id) && it.baseClass == heroClass }
            return matched ?: forClass(heroClass).first()
        }

        fun fromName(name: String, heroClass: HeroClass = HeroClass.VANGUARD): ClassSpecialization =
            entries.firstOrNull { it.name == name || it.id == name } ?: fromIdOrDefault(name, heroClass)
    }
}

enum class ItemSlot(val label: String, val shortCode: String, val isEquipment: Boolean) {
    WEAPON("Weapon", "WPN", true),
    ARMOR("Armor", "ARM", true),
    HELM("Helm", "HLM", true),
    BOOTS("Boots", "BTS", true),
    RING("Ring", "RNG", true),
    RELIC("Relic", "RLC", true),
    CONSUMABLE("Elixir", "ELX", false);

    companion object {
        val equipmentSlots: List<ItemSlot> = listOf(WEAPON, ARMOR, HELM, BOOTS, RING, RELIC)
    }
}

enum class ItemRarity(val label: String, val multiplier: Float) {
    COMMON("Common", 1.0f),
    RARE("Rare", 1.35f),
    EPIC("Epic", 1.85f),
    SOLAR("Solar Relic", 2.5f),
    ULTRA_RARE("Ultra Rare", 3.6f);

    val tier: Int
        get() = ordinal + 1

    fun nextRarityOrNull(): ItemRarity? = when (this) {
        COMMON -> RARE
        RARE -> EPIC
        EPIC -> SOLAR
        SOLAR -> ULTRA_RARE
        ULTRA_RARE -> null
    }
}

enum class GearPerkType(
    val label: String,
    val unitSuffix: String
) {
    NONE("No Perk", ""),
    CRIT_CHANCE("Solar Precision", "% Crit Chance"),
    ARMOR_PIERCE("Sundering Edge", "% Armor Pierce"),
    LIFESTEAL("Sunblood Siphon", "% Lifesteal"),
    SOLAR_SURGE("Astrolabe Overdrive", "% Solar Skill DMG"),
    DAMAGE_REDUCTION("Bastion Plate", "% DMG Reduction"),
    THORNS("Spiked Aegis", "% Thorns Reflect"),
    EXPEDITION_WARD("Wayfarer Ward", "% Expedition Survival"),
    TURN_REGEN("Oasis Vitality", " HP/Turn Regen");

    fun formatSummary(value: Int): String = when (this) {
        NONE -> "Standard Forging"
        CRIT_CHANCE -> "+$value% Critical Strike Chance"
        ARMOR_PIERCE -> "Ignores $value% Enemy Defense"
        LIFESTEAL -> "Heals $value% of Strike & Skill Damage"
        SOLAR_SURGE -> "+$value% Solar Skill Overdrive Damage"
        DAMAGE_REDUCTION -> "-$value% Incoming Enemy Damage"
        THORNS -> "Reflects $value% Blocked/Taken Damage"
        EXPEDITION_WARD -> "+$value% Timed Expedition Survival Odds"
        TURN_REGEN -> "Regenerates +$value HP Every Combat Turn"
    }
}

enum class LootChestTier(
    val title: String,
    val subtitle: String,
    val defaultUltraRarePct: Int
) {
    HIGHLAND(
        title = "Highland Spoils Chest",
        subtitle = "Recovered from canyon skirmishes",
        defaultUltraRarePct = 8
    ),
    BOSS_VAULT(
        title = "Boss Vault Chest",
        subtitle = "Wrested from a Realm Guardian",
        defaultUltraRarePct = 28
    ),
    EXPEDITION(
        title = "Expedition Relic Chest",
        subtitle = "Recovered from a completed Timed Expedition",
        defaultUltraRarePct = 35
    ),
    SPIRE_SOVEREIGN(
        title = "Spire Sovereign Chest",
        subtitle = "Claimed from the Endless Spire Gauntlet",
        defaultUltraRarePct = 42
    )
}

enum class StepSource(val displayLabel: String) {
    PHONE("Phone Pedometer"),
    GALAXY_WATCH("Galaxy Watch")
}

sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

const val DEFAULT_SKILL_RANKS = "0,0,0,0,0,0,0,0,0"

enum class MercenaryContractSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val solCoinCost: Long,
    val battleCharges: Int,
    val atkBonusPct: Int,
    val defBonusPct: Int,
    val critBonusPct: Int,
    val coinFindBonusPct: Int
) {
    NONE("NONE", "No Active Contract", "Hire a mercenary pact at the Black Market", 0L, 0, 0, 0, 0, 0),
    SUNBLADE_VETERAN(
        id = "SUNBLADE_VETERAN",
        title = "Solar Warlord Pact",
        subtitle = "+25% Attack & +12% Crit Chance for 5 Battles",
        solCoinCost = 18L,
        battleCharges = 5,
        atkBonusPct = 25,
        defBonusPct = 0,
        critBonusPct = 12,
        coinFindBonusPct = 0
    ),
    IRONCLAD_SENTINEL(
        id = "IRONCLAD_SENTINEL",
        title = "Obsidian Bulwark Pact",
        subtitle = "+30% Defense & immunity to Armor Sunder for 5 Battles",
        solCoinCost = 16L,
        battleCharges = 5,
        atkBonusPct = 5,
        defBonusPct = 30,
        critBonusPct = 0,
        coinFindBonusPct = 0
    ),
    GREED_SYNDICATE(
        id = "GREED_SYNDICATE",
        title = "Fortune Raiders Syndicate",
        subtitle = "+50% Sol Coin & Sunshard Drops + 10% Crit for 5 Battles",
        solCoinCost = 22L,
        battleCharges = 5,
        atkBonusPct = 10,
        defBonusPct = 10,
        critBonusPct = 10,
        coinFindBonusPct = 50
    );

    val durationBattles: Int
        get() = battleCharges

    val summary: String
        get() = subtitle

    companion object {
        fun fromId(id: String): MercenaryContractSpec =
            entries.firstOrNull { it.id == id || it.name == id } ?: NONE
    }
}

enum class HeroTitleSpec(
    val id: String,
    val title: String,
    val requirementText: String,
    val bonusSummary: String,
    val atkPct: Int,
    val defPct: Int,
    val hpPct: Int,
    val critPct: Int
) {
    WAYFARER("WAYFARER", "Sunlit Wayfarer", "Starter Title", "+2% All Stats", 2, 2, 2, 1),
    DUNE_CONQUEROR("DUNE_CONQUEROR", "Dune Conqueror", "Clear Realm 2", "+5% ATK & +3% DEF", 5, 3, 2, 2),
    RIFT_BREAKER("RIFT_BREAKER", "Obsidian Riftbreaker", "Defeat 10 Bosses", "+8% ATK & +4% Crit", 8, 4, 4, 4),
    SPIRE_ASCENDANT("SPIRE_ASCENDANT", "Spire Ascendant", "Reach Endless Spire Floor 10", "+10% ATK, +10% DEF & +5% HP", 10, 10, 5, 4),
    IRONSTRIDE_IMMORTAL("IRONSTRIDE_IMMORTAL", "Ironstride Immortal", "Clear Realm 5 or Prestige I", "+14% ATK, +12% DEF & +6% Crit", 14, 12, 10, 6),
    CELESTIAL_SOVEREIGN("CELESTIAL_SOVEREIGN", "Celestial Sovereign", "Clear Realm 10 or Defeat 50 Bosses", "+20% All Combat Stats & +8% Crit", 20, 20, 15, 8);

    val requirementLabel: String
        get() = requirementText

    fun isUnlocked(profile: HeroProfile, codexTotalKills: Int): Boolean = when (this) {
        WAYFARER -> true
        DUNE_CONQUEROR -> profile.highestRealmCleared >= 2L || profile.level >= 5L || profile.totalSteps >= 25_000L
        RIFT_BREAKER -> profile.trueBossWins >= 10L || codexTotalKills >= 15 || profile.highestRealmCleared >= 5L
        SPIRE_ASCENDANT -> profile.highestSpireFloor >= 10L || profile.level >= 12L
        IRONSTRIDE_IMMORTAL -> profile.highestRealmCleared >= 5L || profile.prestigeLevel >= 1L || codexTotalKills >= 6
        CELESTIAL_SOVEREIGN -> profile.highestRealmCleared >= 10L || profile.trueBossWins >= 50L || profile.prestigeLevel >= 3L
    }

    companion object {
        val DAWN_INITIATE: HeroTitleSpec get() = WAYFARER
        val STRIDE_CENTURION: HeroTitleSpec get() = DUNE_CONQUEROR
        val REALM_CONQUEROR: HeroTitleSpec get() = RIFT_BREAKER
        val CODEX_GRANDMASTER: HeroTitleSpec get() = IRONSTRIDE_IMMORTAL
        val ETERNAL_SOVEREIGN: HeroTitleSpec get() = CELESTIAL_SOVEREIGN

        fun fromId(id: String): HeroTitleSpec =
            entries.firstOrNull { it.id == id || it.name == id } ?: WAYFARER

        fun fromName(name: String): HeroTitleSpec = fromId(name)
    }
}

data class HardcoreRpgState(
    val sunsteelOre: Long = 0L,
    val aetherHerbs: Long = 0L,
    val elementalEssence: Long = 0L,
    val starforgedIngots: Long = 0L,
    val fogLanternCharges: Int = 0,
    val restedClarityBattlesLeft: Int = 0,
    val restedClarityPct: Int = 0,
    val realmFogStepsMap: Map<String, Int> = emptyMap(),
    val townMerchantDateKey: String = "",
    val townMerchantPurchasedMap: Map<String, Int> = emptyMap(),
    val specializationId: String = "",
    val equippedTitleId: String = HeroTitleSpec.WAYFARER.name,
    val hardcoreOathEnabled: Boolean = false,
    val activeSolCoinWager: Long = 0L,
    val battleWoundStacks: Int = 0,
    val mercenaryContractId: String = MercenaryContractSpec.NONE.id,
    val mercenaryBattlesLeft: Int = 0,
    val shrineBlessingLabel: String = "",
    val shrineBattlesLeft: Int = 0,
    val itemDurabilityMap: Map<String, Int> = emptyMap(),
    val bestiaryDefeatsMap: Map<String, Int> = emptyMap(),
    val unlockedRunesMap: Map<String, Int> = emptyMap()
) {
    fun durabilityFor(itemId: String): Int {
        if (itemId.isBlank()) return 100
        return (itemDurabilityMap[itemId] ?: 100).coerceIn(0, 100)
    }

    fun codexTotalKills(): Int = bestiaryDefeatsMap.values.sum()

    fun codexBonusAtkPct(): Int = (bestiaryDefeatsMap.count { it.value >= 1 } * 2) +
        (bestiaryDefeatsMap.count { it.value >= 5 } * 3)

    fun codexBonusHpPct(): Int = (bestiaryDefeatsMap.count { it.value >= 1 } * 2) +
        (bestiaryDefeatsMap.count { it.value >= 10 } * 4)

    fun bestiaryKills(bossKey: String): Int =
        (bestiaryDefeatsMap[bossKey] ?: 0).coerceAtLeast(0)

    fun runeCount(affinity: ElementalAffinity): Int =
        (unlockedRunesMap[affinity.name] ?: 0).coerceAtLeast(0)

    fun activeMercenary(): MercenaryContractSpec =
        if (mercenaryBattlesLeft > 0) MercenaryContractSpec.fromId(mercenaryContractId)
        else MercenaryContractSpec.NONE

    fun realmFogSteps(realmNumber: Int): Int =
        (realmFogStepsMap[realmNumber.toString()] ?: 0).coerceAtLeast(0)

    fun merchantPurchasesToday(offerId: String, todayKey: String): Int =
        if (townMerchantDateKey == todayKey) (townMerchantPurchasedMap[offerId] ?: 0).coerceAtLeast(0) else 0

    fun townPurchasesToday(offerId: String, todayKey: String): Int =
        merchantPurchasesToday(offerId, todayKey)
}

@Keep
@IgnoreExtraProperties
data class HeroProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Solar Vanguard",
    val heroClass: String = HeroClass.VANGUARD.name,
    val avatarConfig: String = HeroAvatarConfig.DEFAULT_RAW,
    val characterCreated: Boolean = false,
    val level: Long = 1L,
    val xp: Long = 0L,
    val xpToNextLevel: Long = 120L,
    val might: Long = 5L,
    val vigor: Long = 5L,
    val endurance: Long = 5L,
    val unspentPoints: Long = 2L,
    val currentHp: Long = 140L,
    val maxHp: Long = 140L,
    val strideEnergy: Long = 40L,
    val maxStrideEnergy: Long = 100L,
    val encounterProgress: Long = 35L,
    val sunshards: Long = 0L,
    val solCoins: Long = 20L,
    val totalSteps: Long = 0L,
    val weeklySteps: Long = 0L,
    val weekKey: String = currentIsoWeekKey(),
    val bossesDefeated: Long = 0L,
    val trueBossWins: Long = 0L,
    val heroPower: Long = 180L,
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

    fun resolvedAvatar(): HeroAvatarConfig = HeroAvatarConfig.parse(avatarConfig)

    fun hasActiveExpedition(): Boolean =
        activeExpeditionDurationMin > 0L && activeExpeditionEndMs > 0L

    fun parsedSkillRanks(): List<Int> =
        ProgressionEngine.parseSkillRanks(skillRanks)
}

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
    fun resolvedSlot(): ItemSlot =
        ItemSlot.entries.firstOrNull { it.name == slot } ?: ItemSlot.WEAPON

    fun resolvedRarity(): ItemRarity =
        ItemRarity.entries.firstOrNull { it.name == rarity } ?: ItemRarity.COMMON

    fun resolvedRune(): ElementalAffinity? {
        val marker = "[RUNE:"
        val idx = description.indexOf(marker)
        if (idx < 0) return null
        val end = description.indexOf("]", idx)
        if (end < 0) return null
        val token = description.substring(idx + marker.length, end).trim()
        return ElementalAffinity.entries.firstOrNull { it.name == token }
    }

    fun cleanDescriptionWithoutTags(): String {
        return description.replace(Regex("\\[RUNE:[A-Z_]+\\]\\s*"), "").trim()
    }

    fun cleanDescription(): String = cleanDescriptionWithoutTags()

    fun descriptionWithRune(affinity: ElementalAffinity): String {
        val base = cleanDescriptionWithoutTags()
        return "[RUNE:${affinity.name}] $base".take(180)
    }

    fun resolvedPerk(): Pair<GearPerkType, Int> {
        val explicit = GearPerkType.entries.firstOrNull { it.name == perkType } ?: GearPerkType.NONE
        if (explicit != GearPerkType.NONE && perkValue > 0L) {
            return explicit to perkValue.toInt().coerceIn(1, 100)
        }
        val tier = resolvedRarity().ordinal + 1
        val upg = upgradeLevel.toInt().coerceAtLeast(0)
        return when (resolvedSlot()) {
            ItemSlot.WEAPON -> GearPerkType.CRIT_CHANCE to (5 + tier * 3 + upg).coerceAtMost(35)
            ItemSlot.ARMOR -> GearPerkType.DAMAGE_REDUCTION to (4 + tier * 3 + upg).coerceAtMost(30)
            ItemSlot.HELM -> GearPerkType.TURN_REGEN to (4 + tier * 3 + upg).coerceAtMost(32)
            ItemSlot.BOOTS -> GearPerkType.EXPEDITION_WARD to (5 + tier * 3 + upg).coerceAtMost(35)
            ItemSlot.RING -> GearPerkType.LIFESTEAL to (4 + tier * 2 + upg).coerceAtMost(24)
            ItemSlot.RELIC -> GearPerkType.SOLAR_SURGE to (6 + tier * 4 + upg).coerceAtMost(40)
            ItemSlot.CONSUMABLE -> GearPerkType.NONE to 0
        }
    }

    fun gearPowerScore(durabilityPct: Int = 100): Int {
        if (resolvedSlot() == ItemSlot.CONSUMABLE) return 0
        val (_, pVal) = resolvedPerk()
        val runeBonus = if (resolvedRune() != null) 25 else 0
        val raw = (
            attackBonus * 3L +
                defenseBonus * 3L +
                (hpBonus / 2L) +
                energyBonus +
                (pVal * 2L) +
                (upgradeLevel * 6L) +
                ((resolvedRarity().ordinal + 1) * 14L) +
                runeBonus
            ).toInt()
        val durabilityMult = if (durabilityPct <= 0) 0.50f else if (durabilityPct < 30) 0.85f else 1.0f
        return (raw * durabilityMult).roundToInt().coerceAtLeast(1)
    }

    fun upgradeCost(): Long {
        val base = 25L + (upgradeLevel * 20L)
        return (base * resolvedRarity().multiplier).toLong()
    }

    fun temperSolCoinCost(): Long {
        return 4L + (upgradeLevel * 3L) + (resolvedRarity().ordinal * 2L)
    }

    fun temperSuccessChancePct(useStabilizer: Boolean = false): Int {
        return when {
            upgradeLevel < 5L -> 100
            useStabilizer -> (92 - ((upgradeLevel - 5L).toInt() * 4)).coerceIn(55, 95)
            else -> (80 - ((upgradeLevel - 5L).toInt() * 7)).coerceIn(25, 80)
        }
    }

    fun salvageSunshards(): Long {
        val base = 6L + (upgradeLevel * 4L)
        return (base * resolvedRarity().multiplier).toLong()
    }
}

@IgnoreExtraProperties
data class LootChest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val chestTier: String = LootChestTier.HIGHLAND.name,
    val sourceLabel: String = "Highland Spoils",
    val heroLevelSnapshot: Long = 1L,
    val durationMinutes: Long = 0L,
    val ultraRareChancePct: Long = 8L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedTier(): LootChestTier =
        LootChestTier.entries.firstOrNull { it.name == chestTier } ?: LootChestTier.HIGHLAND
}

@Keep
@IgnoreExtraProperties
data class LeaderboardEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Solar Vanguard",
    val heroClass: String = HeroClass.VANGUARD.name,
    val heroLevel: Long = 1L,
    val heroPower: Long = 180L,
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
    val pieceCount: Int,
    val label: String,
    val attackPctBonus: Int,
    val defensePctBonus: Int,
    val expeditionWardBonus: Int,
    val critBonusPct: Int = 0
)

data class DerivedHeroStats(
    val totalAttack: Int,
    val totalDefense: Int,
    val computedMaxHp: Int,
    val computedMaxEnergy: Int,
    val computedHeroPower: Int,
    val gearPowerScore: Int,
    val critChancePercent: Int,
    val armorPiercePercent: Int,
    val lifestealPercent: Int,
    val solarSkillBonusPercent: Int,
    val damageReductionPercent: Int,
    val thornsPercent: Int,
    val expeditionWardPercent: Int,
    val turnRegenHp: Int,
    val stepEnergyMultiplier: Float,
    val totalXpMultiplier: Float,
    val totalSunshardMultiplier: Float,
    val setBonus: GearSetBonus,
    val petBonusSummary: String,
    val activeElement: ElementalAffinity = ElementalAffinity.SOLAR,
    val specialization: ClassSpecialization = ClassSpecialization.SUNSHIELD_PALADIN,
    val activeTitle: HeroTitleSpec = HeroTitleSpec.WAYFARER,
    val woundPenaltyPct: Int = 0,
    val lowestGearDurabilityPct: Int = 100,
    val shatteredGearCount: Int = 0,
    val mercenarySummary: String = "",
    val codexBonusSummary: String = ""
) {
    val activeSpecialization: ClassSpecialization
        get() = specialization

    val primaryAffinity: ElementalAffinity
        get() = activeElement

    val woundPenaltyPercent: Int
        get() = woundPenaltyPct

    val brokenGearCount: Int
        get() = shatteredGearCount
}

private fun evaluateGearSetBonus(equippedItems: List<InventoryItem>): GearSetBonus {
    val gearOnly = equippedItems.filter { it.resolvedSlot().isEquipment }
    val count = gearOnly.size
    if (count < 2) {
        return GearSetBonus(null, count, "Equip 2, 4, or 6 gear pieces for Set Resonance", 0, 0, 0, 0)
    }
    val sortedRarities = gearOnly.map { it.resolvedRarity() }.sortedByDescending { it.ordinal }
    return when {
        count >= 6 -> {
            val minRarity6 = sortedRarities[5]
            when (minRarity6) {
                ItemRarity.ULTRA_RARE -> GearSetBonus(minRarity6, 6, "6pc Sovereign Singularity (+30% ATK/DEF, +16% Survive, +10% Crit)", 30, 30, 16, 10)
                ItemRarity.SOLAR -> GearSetBonus(minRarity6, 6, "6pc Solar Ascendant Set (+22% ATK/DEF, +12% Survive, +8% Crit)", 22, 22, 12, 8)
                ItemRarity.EPIC -> GearSetBonus(minRarity6, 6, "6pc Obsidian Vanguard Set (+16% ATK/DEF, +9% Survive, +6% Crit)", 16, 16, 9, 6)
                else -> GearSetBonus(minRarity6, 6, "6pc Full Strider Panoply (+12% ATK/DEF, +6% Survive, +4% Crit)", 12, 12, 6, 4)
            }
        }
        count >= 4 -> {
            val minRarity4 = sortedRarities[3]
            when (minRarity4) {
                ItemRarity.ULTRA_RARE -> GearSetBonus(minRarity4, 4, "4pc Mythic Resonance (+20% ATK/DEF, +10% Survive)", 20, 20, 10, 6)
                ItemRarity.SOLAR -> GearSetBonus(minRarity4, 4, "4pc Solar Resonance (+15% ATK/DEF, +8% Survive)", 15, 15, 8, 4)
                ItemRarity.EPIC -> GearSetBonus(minRarity4, 4, "4pc Epic Resonance (+11% ATK/DEF, +6% Survive)", 11, 11, 6, 3)
                else -> GearSetBonus(minRarity4, 4, "4pc Battle Harness (+8% ATK/DEF, +4% Survive)", 8, 8, 4, 2)
            }
        }
        count >= 3 -> {
            val minRarity3 = sortedRarities[2]
            when (minRarity3) {
                ItemRarity.ULTRA_RARE -> GearSetBonus(minRarity3, 3, "3pc Ultra Rare Set (+16% ATK/DEF, +8% Survive)", 16, 16, 8, 4)
                ItemRarity.SOLAR -> GearSetBonus(minRarity3, 3, "3pc Solar Relic Set (+12% ATK/DEF, +6% Survive)", 12, 12, 6, 3)
                ItemRarity.EPIC -> GearSetBonus(minRarity3, 3, "3pc Epic Set (+8% ATK/DEF, +4% Survive)", 8, 8, 4, 2)
                ItemRarity.RARE -> GearSetBonus(minRarity3, 3, "3pc Rare Set (+5% ATK/DEF, +3% Survive)", 5, 5, 3, 1)
                ItemRarity.COMMON -> GearSetBonus(minRarity3, 3, "3pc Wayfarer Set (+3% ATK/DEF, +2% Survive)", 3, 3, 2, 0)
            }
        }
        else -> {
            val minRarity2 = sortedRarities[1]
            GearSetBonus(minRarity2, 2, "2pc Skirmisher Resonance (+4% ATK/DEF, +2% Survive)", 4, 4, 2, 1)
        }
    }
}

fun computeDerivedStats(
    profile: HeroProfile,
    inventory: List<InventoryItem>,
    equippedPet: OwnedPet? = null,
    hardcoreState: HardcoreRpgState = HardcoreRpgState()
): DerivedHeroStats {
    val equipped = inventory.filter { it.equipped && it.resolvedSlot().isEquipment }

    var atkGear = 0
    var defGear = 0
    var hpGear = 0
    var energyGear = 0
    var lowestDurability = 100
    var shatteredCount = 0

    equipped.forEach { item ->
        val dur = hardcoreState.durabilityFor(item.id)
        if (dur < lowestDurability) lowestDurability = dur
        if (dur <= 0) shatteredCount += 1
        val durMult = if (dur <= 0) 0.50f else if (dur < 25) 0.85f else 1.0f
        atkGear += (item.attackBonus * durMult).roundToInt()
        defGear += (item.defenseBonus * durMult).roundToInt()
        hpGear += (item.hpBonus * durMult).roundToInt()
        energyGear += (item.energyBonus * durMult).roundToInt()
    }

    val petBonus = PetCatalog.computeBonus(equippedPet)
    val setBonus = evaluateGearSetBonus(equipped)
    val skillBonus = ProgressionEngine.computeSkillTreeBonuses(profile)
    val heroClass = profile.resolvedClass()
    val spec = ClassSpecialization.fromIdOrDefault(hardcoreState.specializationId, heroClass)
    val title = HeroTitleSpec.fromId(hardcoreState.equippedTitleId)
    val mercenary = hardcoreState.activeMercenary()

    var critPct = 8 + (profile.level.toInt() / 3).coerceAtMost(10)
    var piercePct = 0
    var lifestealPct = 0
    var solarSurgePct = 0
    var dmgRedPct = 0
    var thornsPct = 0
    var expWardPct = setBonus.expeditionWardBonus + skillBonus.expeditionWardPct + skillBonus.prestigeExpeditionWardPct
    var turnRegen = 0
    var runeElement: ElementalAffinity? = null

    equipped.forEach { item ->
        val (perk, pVal) = item.resolvedPerk()
        when (perk) {
            GearPerkType.CRIT_CHANCE -> critPct += pVal
            GearPerkType.ARMOR_PIERCE -> piercePct += pVal
            GearPerkType.LIFESTEAL -> lifestealPct += pVal
            GearPerkType.SOLAR_SURGE -> solarSurgePct += pVal
            GearPerkType.DAMAGE_REDUCTION -> dmgRedPct += pVal
            GearPerkType.THORNS -> thornsPct += pVal
            GearPerkType.EXPEDITION_WARD -> expWardPct += pVal
            GearPerkType.TURN_REGEN -> turnRegen += pVal
            GearPerkType.NONE -> {}
        }
        val socketed = item.resolvedRune()
        if (socketed != null) {
            if (item.resolvedSlot() == ItemSlot.WEAPON || runeElement == null) {
                runeElement = socketed
            }
            when (socketed) {
                ElementalAffinity.SOLAR -> solarSurgePct += 8
                ElementalAffinity.EMBER -> atkGear += 6
                ElementalAffinity.FROST -> dmgRedPct += 4
                ElementalAffinity.TERRA -> piercePct += 7
                ElementalAffinity.VOID -> lifestealPct += 5
            }
        }
    }

    when (heroClass) {
        HeroClass.VANGUARD -> {}
        HeroClass.ARCANIST -> solarSurgePct += 25
        HeroClass.RANGER -> {
            critPct += 14
            expWardPct += 8
        }
        HeroClass.SHADOWBLADE -> {
            piercePct += 14
            lifestealPct += 10
        }
    }

    // Balanced Step Fog-of-War & Watch Sleep Rested Clarity bonuses
    val realmFogState = ProgressionEngine.computeWorldFogState(profile, hardcoreState, 0L, false)
    critPct += realmFogState.fogCritBonusPct
    expWardPct += realmFogState.restedExpeditionWardBonusPct

    // Apply Specialization, Skill Tree, Title, Codex & Mercenary bonuses
    val specUnlocked = profile.level >= spec.unlockLevel
    if (specUnlocked) {
        critPct += spec.critBonusPct
        lifestealPct += spec.lifestealBonusPct
        piercePct += spec.pierceBonusPct
        turnRegen += spec.regenBonusHp
    }

    critPct = (critPct + skillBonus.critChancePct + setBonus.critBonusPct + title.critPct + mercenary.critBonusPct)
        .coerceIn(5, 85)
    piercePct = (piercePct + skillBonus.armorPiercePct).coerceIn(0, 75)
    lifestealPct = (lifestealPct + skillBonus.lifestealPct).coerceIn(0, 50)
    solarSurgePct = (solarSurgePct + skillBonus.solarSurgePct).coerceIn(0, 200)
    dmgRedPct = (dmgRedPct + skillBonus.damageReductionPct).coerceIn(0, 65)
    thornsPct = (thornsPct + skillBonus.thornsPct).coerceIn(0, 90)
    expWardPct = expWardPct.coerceIn(0, 60)
    turnRegen = (turnRegen + skillBonus.turnRegenHp).coerceIn(0, 300)

    val rawAtk = 12 + (profile.might.toInt() * 3) + (profile.level.toInt() * 2) +
        atkGear + petBonus.flatAttack + skillBonus.flatAttack
    val rawDef = 8 + (profile.vigor.toInt() * 2) + profile.level.toInt() +
        defGear + petBonus.flatDefense + skillBonus.flatDefense

    val classDefMult = if (heroClass == HeroClass.VANGUARD) 1.15f else 1.0f
    val setAtkMult = 1.0f + (setBonus.attackPctBonus / 100f)
    val setDefMult = 1.0f + (setBonus.defensePctBonus / 100f)
    val specAtkMult = if (specUnlocked) 1.0f + spec.atkMultBonus else 1.0f
    val specDefMult = if (specUnlocked) 1.0f + spec.defMultBonus else 1.0f
    val titleAtkMult = 1.0f + (title.atkPct / 100f) + (hardcoreState.codexBonusAtkPct() / 100f)
    val titleDefMult = 1.0f + (title.defPct / 100f)
    val mercAtkMult = 1.0f + (mercenary.atkBonusPct / 100f)
    val mercDefMult = 1.0f + (mercenary.defBonusPct / 100f)

    // Battle Wounds hardcore penalty (-10% per stack up to -30%)
    val woundStacks = hardcoreState.battleWoundStacks.coerceIn(0, 3)
    val woundPenaltyPct = woundStacks * 10
    val woundMult = (1.0f - (woundPenaltyPct / 100f)).coerceIn(0.70f, 1.0f)

    val totalAtk = max(
        1,
        (rawAtk * petBonus.attackMultiplier * setAtkMult * specAtkMult * titleAtkMult * mercAtkMult * skillBonus.prestigeStatMultiplier * woundMult).roundToInt()
    )
    val totalDef = max(
        1,
        (rawDef * classDefMult * petBonus.defenseMultiplier * setDefMult * specDefMult * titleDefMult * mercDefMult * skillBonus.prestigeStatMultiplier).roundToInt()
    )

    val rawMaxHp = 110 + (profile.vigor.toInt() * 10) + (profile.level.toInt() * 12) +
        hpGear + petBonus.flatMaxHp + skillBonus.flatMaxHp
    val titleHpMult = 1.0f + (title.hpPct / 100f) + (hardcoreState.codexBonusHpPct() / 100f)
    val maxHp = max(
        60,
        (rawMaxHp * petBonus.maxHpMultiplier * titleHpMult * skillBonus.prestigeStatMultiplier * woundMult).roundToInt()
    )
    val maxEnergy = max(
        60,
        85 + (profile.endurance.toInt() * 5) + energyGear + petBonus.flatMaxEnergy + skillBonus.flatMaxEnergy
    )

    val rawGearPower = equipped.sumOf { it.gearPowerScore(hardcoreState.durabilityFor(it.id)) } +
        (setBonus.attackPctBonus * 4)

    val power = (totalAtk * 5) + (totalDef * 4) + maxHp + (profile.level.toInt() * 25) +
        (critPct * 2) + (piercePct * 2) + (dmgRedPct * 3) + (expWardPct * 2) +
        (profile.prestigeLevel.toInt() * 120)

    val activeElement = runeElement ?: if (specUnlocked) spec.affinity else heroClass.defaultAffinity
    val mercSummary = if (mercenary != MercenaryContractSpec.NONE && hardcoreState.mercenaryBattlesLeft > 0) {
        "${mercenary.title} (${hardcoreState.mercenaryBattlesLeft}B left)"
    } else ""
    val codexSummary = "+${hardcoreState.codexBonusAtkPct()}% ATK, +${hardcoreState.codexBonusHpPct()}% Max HP (${hardcoreState.codexTotalKills()} Boss Kills)"

    return DerivedHeroStats(
        totalAttack = totalAtk,
        totalDefense = totalDef,
        computedMaxHp = maxHp,
        computedMaxEnergy = maxEnergy,
        computedHeroPower = power,
        gearPowerScore = rawGearPower,
        critChancePercent = critPct,
        armorPiercePercent = piercePct,
        lifestealPercent = lifestealPct,
        solarSkillBonusPercent = solarSurgePct,
        damageReductionPercent = dmgRedPct,
        thornsPercent = thornsPct,
        expeditionWardPercent = expWardPct,
        turnRegenHp = turnRegen,
        stepEnergyMultiplier = petBonus.stepEnergyMultiplier * skillBonus.stepEnergyMultiplier,
        totalXpMultiplier = petBonus.xpMultiplier * skillBonus.xpMultiplier * skillBonus.prestigeRewardMultiplier * (1.0f + realmFogState.restedXpBonusPct / 100f),
        totalSunshardMultiplier = petBonus.sunshardMultiplier * skillBonus.sunshardMultiplier * skillBonus.prestigeRewardMultiplier,
        setBonus = setBonus,
        petBonusSummary = petBonus.summaryText,
        activeElement = activeElement,
        specialization = spec,
        activeTitle = title,
        woundPenaltyPct = woundPenaltyPct,
        lowestGearDurabilityPct = lowestDurability,
        shatteredGearCount = shatteredCount,
        mercenarySummary = mercSummary,
        codexBonusSummary = codexSummary
    )
}

fun currentIsoWeekKey(nowMs: Long = System.currentTimeMillis()): String {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = nowMs
        firstDayOfWeek = Calendar.MONDAY
        minimalDaysInFirstWeek = 4
    }
    val year = calendar.get(Calendar.YEAR)
    val week = calendar.get(Calendar.WEEK_OF_YEAR)
    return String.format(Locale.US, "%04d-W%02d", year, week)
}

fun currentCalendarDateKey(nowMs: Long = System.currentTimeMillis()): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return sdf.format(Date(nowMs))
}
