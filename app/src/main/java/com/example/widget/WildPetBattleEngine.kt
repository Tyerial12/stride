package com.example.widget

import com.example.data.model.ElementalAffinity
import com.example.data.model.PetCatalog
import kotlin.math.roundToInt
import kotlin.random.Random

data class WildPetFoe(
    val name: String,
    val level: Int,
    val affinity: ElementalAffinity
)

data class WildPetBattleOutcome(
    val won: Boolean,
    val foe: WildPetFoe,
    val xpGained: Long,
    val oreWon: Long,
    val essenceWon: Long,
    val summaryLine: String
)

data class PetProgressionPreview(
    val level: Int,
    val xp: Long,
    val xpToNext: Long,
    val levelsGained: Int
)

/**
 * Self-contained wild pet battle & progression preview engine for the Home Screen Pet Widget.
 * Allows your Companion Pet to fight wild creatures directly from the widget while the app is closed,
 * earning exclusive Pet XP, level-ups, and bonus forging materials.
 */
object WildPetBattleEngine {

    const val FEED_SOL_COIN_COST = 5L
    const val FEED_XP_GAIN = 35L
    const val FEED_MOOD_GAIN = 30
    const val FEED_BOND_XP_GAIN = 26L
    const val WILD_FIGHT_BOND_XP_GAIN = 32L
    const val MAX_WILD_STAMINA = 5
    const val STEPS_PER_STAMINA = 250L
    const val STEPS_PER_FREE_TREAT = 500L

    private val wildCreatures: List<Pair<String, ElementalAffinity>> = listOf(
        "Wild Dune Jackal" to ElementalAffinity.SOLAR,
        "Wild Cinder Cub" to ElementalAffinity.EMBER,
        "Wild Obsidian Beetle" to ElementalAffinity.TERRA,
        "Wild Frost Terrapin" to ElementalAffinity.FROST,
        "Wild Eclipse Kestrel" to ElementalAffinity.VOID,
        "Wild Mirage Lynx" to ElementalAffinity.EMBER,
        "Wild Glacial Fox" to ElementalAffinity.FROST,
        "Wild Monolith Scarab" to ElementalAffinity.TERRA
    )

    fun resolveWildBattle(
        petName: String,
        petLevel: Int,
        petElementLabel: String,
        weaponTier: Int,
        armorTier: Int,
        moodPct: Int
    ): WildPetBattleOutcome {
        val safeLvl = petLevel.coerceIn(1, PetCatalog.MAX_PET_LEVEL.toInt())
        val foeLevel = (safeLvl + Random.nextInt(-1, 2)).coerceAtLeast(1)
        val (foeName, foeAffinity) = wildCreatures[Random.nextInt(wildCreatures.size)]
        val foe = WildPetFoe(name = foeName, level = foeLevel, affinity = foeAffinity)

        val petAffinity = ElementalAffinity.entries.firstOrNull {
            it.label.equals(petElementLabel, ignoreCase = true)
        } ?: ElementalAffinity.SOLAR

        val elemMult = when {
            petAffinity.beats() == foeAffinity -> 1.25f
            foeAffinity.beats() == petAffinity -> 0.85f
            else -> 1.0f
        }
        val moodMult = 0.88f + (moodPct.coerceIn(0, 100) / 100f) * 0.32f
        val gearBonus = (weaponTier * 14f) + (armorTier * 12f)
        val myPower = ((24f + safeLvl * 11f + gearBonus) * moodMult * elemMult) * (0.90f + Random.nextFloat() * 0.24f)
        val foePower = (22f + foeLevel * 10.5f) * (0.86f + Random.nextFloat() * 0.26f)

        val won = myPower >= foePower
        return if (won) {
            val xpGain = (34L + foeLevel * 9L + (if (elemMult > 1.0f) 8L else 0L)).coerceAtLeast(30L)
            val oreGain = (2L + (foeLevel / 3L)).coerceIn(2L, 8L)
            val essenceGain = if (foeLevel >= 3 || Random.nextBoolean()) (1L + foeLevel / 5L).coerceIn(1L, 4L) else 0L
            val matNote = if (essenceGain > 0L) "+${oreGain} Ore +${essenceGain} Ess" else "+${oreGain} Ore"
            WildPetBattleOutcome(
                won = true,
                foe = foe,
                xpGained = xpGain,
                oreWon = oreGain,
                essenceWon = essenceGain,
                summaryLine = "🏆 Beat ${foe.affinity.symbol} ${foe.name} Lv.$foeLevel! +${xpGain} XP • $matNote"
            )
        } else {
            val consolationXp = (14L + foeLevel * 3L).coerceAtLeast(14L)
            WildPetBattleOutcome(
                won = false,
                foe = foe,
                xpGained = consolationXp,
                oreWon = 0L,
                essenceWon = 0L,
                summaryLine = "💥 Lost to ${foe.affinity.symbol} ${foe.name} Lv.$foeLevel (+${consolationXp} XP) • Feed or gear up!"
            )
        }
    }

    fun computePreviewProgression(
        baseLevel: Int,
        baseXp: Long,
        addedXp: Long
    ): PetProgressionPreview {
        var lvl = baseLevel.toLong().coerceIn(1L, PetCatalog.MAX_PET_LEVEL)
        var xp = (baseXp + addedXp).coerceAtLeast(0L)
        var needed = PetCatalog.xpRequiredForLevel(lvl)
        var gained = 0

        while (xp >= needed && lvl < PetCatalog.MAX_PET_LEVEL) {
            xp -= needed
            lvl += 1L
            gained += 1
            needed = PetCatalog.xpRequiredForLevel(lvl)
        }
        if (lvl >= PetCatalog.MAX_PET_LEVEL) {
            xp = 0L
            needed = PetCatalog.xpRequiredForLevel(PetCatalog.MAX_PET_LEVEL)
        }
        return PetProgressionPreview(
            level = lvl.toInt(),
            xp = xp,
            xpToNext = needed,
            levelsGained = gained
        )
    }
}
