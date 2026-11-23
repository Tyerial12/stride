package com.example.domain

import com.example.data.model.ClassSpecialization
import com.example.data.model.DerivedHeroStats
import com.example.data.model.ElementalAffinity
import com.example.data.model.HardcoreRpgState
import com.example.data.model.HeroClass
import com.example.data.model.HeroProfile
import com.example.data.model.InventoryItem
import com.example.data.model.LootChest
import com.example.data.model.LootChestTier
import com.example.data.model.OwnedPet
import com.example.data.model.ParryGrade
import com.example.data.model.PetEvolutionBranch
import com.example.data.model.WeeklyWorldBossCatalog
import com.example.data.model.currentIsoWeekKey
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

enum class BattleMode(val label: String, val subtitle: String) {
    REALM_CAMPAIGN("10-Realm", "Progress through 10 Realms & Guardians"),
    ENDLESS_SPIRE("Spire", "Infinite scaling floors & Sovereign Bosses"),
    DAILY_ELEMENTAL_RIFT("Daily Rift", "Rotating element • Guaranteed Runes & 2x Essence"),
    WEEKLY_BOSS_RAID("Raid Boss", "Colossal 2-Phase Titan • Huge Sol Coin Payout")
}

enum class EnemyIntentType(val label: String) {
    QUICK_STRIKE("Quick Slash"),
    HEAVY_CRUSH("Heavy Sundering Crush"),
    AEGIS_WARD("Obsidian Ward"),
    SOLAR_CATACLYSM("Elemental Cataclysm")
}

enum class EnemyArtKey {
    SENTINEL,
    STALKER,
    CONSTRUCT,
    DRAKE,
    COLOSSUS,
    WYRM,
    OBSIDIAN_GOLEM
}

enum class CombatStatusType(val label: String, val isBuff: Boolean, val badge: String) {
    BURN("Burn", false, "🔥"),
    FROSTBITE("Frostbite", false, "❄"),
    STUN("Stun", false, "⚡"),
    SUNDER("Sunder", false, "🛡"),
    POISON("Poison", false, "☠"),
    REGEN("Regen", true, "💚"),
    AEGIS_BARRIER("Aegis", true, "✨")
}

data class ActiveCombatStatus(
    val type: CombatStatusType,
    val turnsRemaining: Int,
    val potency: Int
)

enum class BattleAnimAction {
    NONE,
    HERO_STRIKE,
    HERO_SOLAR_SKILL,
    HERO_ULTIMATE_COMBO,
    HERO_GUARD,
    HERO_ELIXIR,
    ENEMY_ATTACK,
    ENEMY_WARD
}

data class BattleAnimEvent(
    val sequenceId: Long,
    val heroAction: BattleAnimAction,
    val enemyAction: BattleAnimAction = BattleAnimAction.NONE,
    val heroDamageDealt: Int = 0,
    val heroWasCrit: Boolean = false,
    val enemyDamageDealt: Int = 0,
    val enemyWasGuarded: Boolean = false,
    val heroLifestealHeal: Int = 0,
    val heroElixirHeal: Int = 0,
    val heroTurnRegenHeal: Int = 0,
    val thornsReflected: Int = 0,
    val perkCallouts: List<String> = emptyList()
)

data class EnemyIntent(
    val type: EnemyIntentType,
    val expectedDamage: Int,
    val shieldGain: Int = 0,
    val inflictsStatus: CombatStatusType? = null
)

data class CombatEnemy(
    val name: String,
    val title: String,
    val level: Int,
    val maxHp: Int,
    val currentHp: Int,
    val attack: Int,
    val defense: Int,
    val shield: Int = 0,
    val isBoss: Boolean = false,
    val artKey: EnemyArtKey = EnemyArtKey.SENTINEL,
    val elementalAffinity: ElementalAffinity = ElementalAffinity.SOLAR,
    val bossPhase: Int = 1,
    val doomTurnsLeft: Int = 0,
    val bestiaryKey: String = "",
    val statuses: List<ActiveCombatStatus> = emptyList(),
    val nextIntent: EnemyIntent
) {
    val phase: Int get() = bossPhase
    val affinity: ElementalAffinity get() = elementalAffinity
    val doomTurnsRemaining: Int get() = doomTurnsLeft
}

data class BattleOutcome(
    val victory: Boolean,
    val retreated: Boolean = false,
    val wasBossVictory: Boolean = false,
    val mode: BattleMode = BattleMode.REALM_CAMPAIGN,
    val realmNumber: Int = 1,
    val stageNumber: Int = 1,
    val spireFloor: Int = 1,
    val xpEarned: Long = 0L,
    val sunshardsEarned: Long = 0L,
    val solCoinsEarned: Long = 0L,
    val solCoinsLost: Long = 0L,
    val oreEarned: Long = 0L,
    val herbsEarned: Long = 0L,
    val essenceEarned: Long = 0L,
    val droppedRune: ElementalAffinity? = null,
    val bestiaryBossKey: String? = null,
    val woundInflicted: Boolean = false,
    val gearDurabilityLossPct: Int = 4,
    val droppedChest: LootChest? = null,
    val leveledUpTo: Long? = null,
    val progressionNote: String? = null,
    val summaryLine: String = ""
)

data class BattleState(
    val active: Boolean = false,
    val mode: BattleMode = BattleMode.REALM_CAMPAIGN,
    val realmNumber: Int = 1,
    val stageNumber: Int = 1,
    val spireFloor: Int = 1,
    val currentWave: Int = 1,
    val totalWaves: Int = 1,
    val turnNumber: Int = 1,
    val valor: Int = 25,
    val petSynergy: Int = 40,
    val heroBarrier: Int = 0,
    val heroStatuses: List<ActiveCombatStatus> = emptyList(),
    val elixirCooldownTurns: Int = 0,
    val skillCooldowns: List<Int> = listOf(0, 0, 0),
    val hardcoreOathActive: Boolean = false,
    val stakedSolCoins: Long = 0L,
    val guarding: Boolean = false,
    val enemy: CombatEnemy? = null,
    val combatLog: List<String> = emptyList(),
    val outcome: BattleOutcome? = null,
    val lastAnimEvent: BattleAnimEvent? = null,
    val comboCount: Int = 0,
    val petBurstCharge: Int = 35,
    val pendingEnemyTelegraph: Boolean = false,
    val lastParryGrade: ParryGrade? = null,
    val weeklyMutatorLabel: String = "",
    val weeklyMutatorSummary: String = "",
    val telegraphHeroAction: BattleAnimAction = BattleAnimAction.HERO_STRIKE,
    val telegraphHeroDamage: Int = 0,
    val telegraphDealtCrit: Boolean = false,
    val telegraphLifesteal: Int = 0,
    val telegraphElixirHeal: Int = 0,
    val telegraphCallouts: List<String> = emptyList(),
    val isDungeonBossFight: Boolean = false,
    val dungeonId: String = ""
) {
    val activeWager: Long get() = stakedSolCoins
    val hardcoreActive: Boolean get() = hardcoreOathActive
}

data class TurnResolution(
    val nextBattle: BattleState,
    val updatedHp: Long,
    val updatedEnergy: Long,
    val consumedElixirId: String? = null,
    val tookDamage: Boolean = false,
    val dealtCrit: Boolean = false,
    val guardedHit: Boolean = false
)

object CombatEngine {

    val wagerTiers: List<Long> = listOf(0L, 10L, 25L, 50L)

    fun spawnEncounter(
        profile: HeroProfile,
        mode: BattleMode,
        forceBoss: Boolean = false,
        hardcoreState: HardcoreRpgState = HardcoreRpgState(),
        todayKey: String = "",
        dateKey: String = todayKey
    ): BattleState {
        val realmNum = profile.currentRealm.toInt().coerceIn(1, ProgressionEngine.MAX_REALM.toInt())
        val stageNum = profile.realmStage.toInt().coerceIn(1, 5)
        val spireFloor = profile.currentSpireFloor.toInt().coerceAtLeast(1)
        val hardcore = hardcoreState.hardcoreOathEnabled
        val wager = hardcoreState.activeSolCoinWager.coerceAtMost(profile.solCoins).coerceAtLeast(0L)

        val totalWaves = when (mode) {
            BattleMode.REALM_CAMPAIGN -> if (forceBoss || stageNum == 5) 2 else 2
            BattleMode.ENDLESS_SPIRE -> if (forceBoss || (spireFloor % 5 == 0)) 2 else 1
            BattleMode.DAILY_ELEMENTAL_RIFT -> 3
            BattleMode.WEEKLY_BOSS_RAID -> 2
        }

        val firstEnemy = buildWaveEnemy(
            profile = profile,
            mode = mode,
            realmNum = realmNum,
            stageNum = stageNum,
            spireFloor = spireFloor,
            wave = 1,
            totalWaves = totalWaves,
            forceBoss = forceBoss,
            hardcore = hardcore,
            todayKey = todayKey
        )

        val hcNote = if (hardcore) " [IRONSTRIDE HARDCORE +35% ENEMY PWR]" else ""
        val wagerNote = if (wager > 0L) " [BLOOD-OATH WAGER: $wager SOL COINS]" else ""
        val (mutatorLabel, mutatorSummary) = if (mode == BattleMode.ENDLESS_SPIRE) {
            WeeklyWorldBossCatalog.sunspireWeeklyMutator(currentIsoWeekKey())
        } else {
            "" to ""
        }
        val adjustedFirstEnemy = if (mutatorLabel == "Ironhide Bastion") {
            firstEnemy.copy(shield = firstEnemy.shield + (firstEnemy.maxHp * 0.20f).roundToInt())
        } else {
            firstEnemy
        }
        val mutatorNote = if (mutatorLabel.isNotBlank()) " [Mutator: $mutatorLabel]" else ""

        return BattleState(
            active = true,
            mode = mode,
            realmNumber = realmNum,
            stageNumber = if (forceBoss) 5 else stageNum,
            spireFloor = spireFloor,
            currentWave = 1,
            totalWaves = totalWaves,
            turnNumber = 1,
            valor = 30,
            petSynergy = 50,
            comboCount = 0,
            petBurstCharge = 35,
            pendingEnemyTelegraph = false,
            lastParryGrade = null,
            weeklyMutatorLabel = mutatorLabel,
            weeklyMutatorSummary = mutatorSummary,
            heroBarrier = 0,
            heroStatuses = emptyList(),
            elixirCooldownTurns = 0,
            hardcoreOathActive = hardcore,
            stakedSolCoins = wager,
            guarding = false,
            enemy = adjustedFirstEnemy,
            combatLog = listOf(
                "Wave 1/$totalWaves: ${adjustedFirstEnemy.name} (Lv.${adjustedFirstEnemy.level} • ${adjustedFirstEnemy.elementalAffinity.label})$hcNote$wagerNote$mutatorNote!",
                "Telegraphed Intent: ${adjustedFirstEnemy.nextIntent.type.label}."
            ),
            outcome = null,
            lastAnimEvent = null
        )
    }

    fun spawnDungeonBossEncounter(
        profile: HeroProfile,
        dungeon: com.example.data.model.StepDungeonSpec,
        hardcoreState: HardcoreRpgState
    ): BattleState {
        val enemyLevel = max(dungeon.recommendedLevel, profile.level.toInt())
        val tierIdx = (dungeon.recommendedLevel / 4) + 1
        val steep = steepLevelMultiplier(enemyLevel, tierIdx)
        val hcMult = if (hardcoreState.hardcoreOathEnabled) 1.38f else 1.0f
        val hp = ((210 + enemyLevel * 40 + tierIdx * 24) * steep * hcMult).roundToInt()
        val atk = ((18 + enemyLevel * 4 + tierIdx * 3) * steep * hcMult).roundToInt()
        val def = ((10 + enemyLevel * 2 + tierIdx * 2) * (0.85f + steep * 0.20f)).roundToInt()
        val bossEnemy = CombatEnemy(
            name = dungeon.bossName,
            title = "${dungeon.title} • Final Dungeon Sovereign",
            level = enemyLevel,
            maxHp = hp,
            currentHp = hp,
            attack = atk,
            defense = def,
            shield = 20 + enemyLevel * 3,
            isBoss = true,
            artKey = if (dungeon.chestRewardTier == LootChestTier.SPIRE_SOVEREIGN) EnemyArtKey.WYRM else EnemyArtKey.COLOSSUS,
            elementalAffinity = dungeon.affinity,
            bestiaryKey = "boss_spire_warden",
            nextIntent = rollIntent(atk, isBoss = true)
        )
        return BattleState(
            active = true,
            mode = BattleMode.REALM_CAMPAIGN,
            realmNumber = profile.currentRealm.toInt().coerceIn(1, 10),
            stageNumber = 5,
            spireFloor = profile.currentSpireFloor.toInt(),
            currentWave = 1,
            totalWaves = 1,
            turnNumber = 1,
            valor = 35,
            petSynergy = 55,
            comboCount = 0,
            petBurstCharge = 45,
            heroBarrier = 0,
            heroStatuses = emptyList(),
            elixirCooldownTurns = 0,
            hardcoreOathActive = hardcoreState.hardcoreOathEnabled,
            stakedSolCoins = hardcoreState.activeSolCoinWager.coerceAtMost(profile.solCoins).coerceAtLeast(0L),
            guarding = false,
            enemy = bossEnemy,
            isDungeonBossFight = true,
            dungeonId = dungeon.id,
            combatLog = listOf(
                "DUNGEON BOSS VAULT: ${bossEnemy.name} (Lv.${bossEnemy.level} • ${bossEnemy.elementalAffinity.label})!",
                "Defeat the Dungeon Sovereign to unlock the guaranteed ${dungeon.chestRewardTier.title}!"
            ),
            outcome = null,
            lastAnimEvent = null
        )
    }

    /**
     * Steep compounding difficulty multiplier: Level 1-2 stays fair for an armorless starter hero,
     * while Level 3+ and higher Realms escalate rapidly, demanding custom-forged gear & widget-trained pets.
     */
    private fun steepLevelMultiplier(enemyLevel: Int, realmOrTier: Int = 1): Float {
        val safeLvl = enemyLevel.coerceAtLeast(1)
        val tierBonus = ((realmOrTier - 1).coerceAtLeast(0)) * 0.16f
        return if (safeLvl <= 2) {
            0.88f + ((safeLvl - 1) * 0.14f) + tierBonus
        } else {
            val aboveTwo = safeLvl - 2
            1.04f + (aboveTwo * 0.16f) + (aboveTwo * aboveTwo * 0.0085f) + tierBonus
        }
    }

    private fun buildWaveEnemy(
        profile: HeroProfile,
        mode: BattleMode,
        realmNum: Int,
        stageNum: Int,
        spireFloor: Int,
        wave: Int,
        totalWaves: Int,
        forceBoss: Boolean,
        hardcore: Boolean,
        todayKey: String
    ): CombatEnemy {
        val isFinalWave = wave >= totalWaves
        val hcMult = if (hardcore) 1.38f else 1.0f

        return when (mode) {
            BattleMode.WEEKLY_BOSS_RAID -> {
                val lvl = max(profile.level.toInt() + 4, 8)
                val steep = steepLevelMultiplier(lvl, realmNum + 1)
                if (!isFinalWave) {
                    val hp = ((150 + lvl * 34) * steep * hcMult).roundToInt()
                    val atk = ((19 + lvl * 5) * steep * hcMult).roundToInt()
                    val def = ((9 + lvl * 3) * (0.85f + steep * 0.25f)).roundToInt()
                    CombatEnemy(
                        name = "Solaris Raid Vanguard",
                        title = "Herald of the World-Eater",
                        level = lvl,
                        maxHp = hp,
                        currentHp = hp,
                        attack = atk,
                        defense = def,
                        shield = 20 + lvl * 2,
                        isBoss = false,
                        artKey = EnemyArtKey.DRAKE,
                        elementalAffinity = ElementalAffinity.EMBER,
                        nextIntent = EnemyIntent(EnemyIntentType.HEAVY_CRUSH, expectedDamage = (atk * 1.25f).roundToInt())
                    )
                } else {
                    val bossLvl = lvl + 2
                    val bossSteep = steepLevelMultiplier(bossLvl, realmNum + 2)
                    val hp = ((320 + bossLvl * 60) * bossSteep * hcMult).roundToInt()
                    val atk = ((28 + bossLvl * 7) * bossSteep * hcMult).roundToInt()
                    val def = ((15 + bossLvl * 4) * (0.85f + bossSteep * 0.25f)).roundToInt()
                    CombatEnemy(
                        name = "Apophis World-Eater",
                        title = "Cataclysmic Raid Titan • Phase I",
                        level = bossLvl,
                        maxHp = hp,
                        currentHp = hp,
                        attack = atk,
                        defense = def,
                        shield = 42 + bossLvl * 3,
                        isBoss = true,
                        artKey = EnemyArtKey.WYRM,
                        elementalAffinity = ElementalAffinity.EMBER,
                        bestiaryKey = "boss_weekly_colossus",
                        nextIntent = EnemyIntent(EnemyIntentType.SOLAR_CATACLYSM, expectedDamage = (atk * 1.38f).roundToInt())
                    )
                }
            }
            BattleMode.DAILY_ELEMENTAL_RIFT -> {
                val riftElement = ProgressionEngine.dailyRiftAffinity(todayKey.ifBlank { "daily" })
                val lvl = max(profile.level.toInt() + wave, 2)
                val isRiftBoss = isFinalWave
                val steep = steepLevelMultiplier(lvl, realmNum)
                val hp = (((if (isRiftBoss) 235 else 125) + lvl * (if (isRiftBoss) 42 else 26)) * steep * hcMult).roundToInt()
                val atk = (((if (isRiftBoss) 23 else 15) + lvl * 5) * steep * hcMult).roundToInt()
                val def = ((7 + lvl * 3) * (0.85f + steep * 0.22f)).roundToInt()
                CombatEnemy(
                    name = if (isRiftBoss) "${riftElement.label} Rift Archon" else "${riftElement.label} Rift Spawn",
                    title = if (isRiftBoss) "Daily Elemental Rift Sovereign" else "Wave $wave/$totalWaves Rift Guardian",
                    level = lvl,
                    maxHp = hp,
                    currentHp = hp,
                    attack = atk,
                    defense = def,
                    shield = if (isRiftBoss) 24 + lvl * 2 else lvl,
                    isBoss = isRiftBoss,
                    artKey = if (isRiftBoss) EnemyArtKey.COLOSSUS else EnemyArtKey.CONSTRUCT,
                    elementalAffinity = riftElement,
                    bestiaryKey = if (isRiftBoss) "boss_spire_warden" else "",
                    nextIntent = rollIntent(atk, isRiftBoss)
                )
            }
            BattleMode.ENDLESS_SPIRE -> {
                val isBoss = isFinalWave && (forceBoss || (spireFloor % 5 == 0))
                val enemyLevel = max(profile.level.toInt(), (spireFloor * 2) + (wave - 1))
                val steep = steepLevelMultiplier(enemyLevel, (spireFloor / 3) + 1)
                val hp = (((if (isBoss) 240 else 120) + enemyLevel * (if (isBoss) 40 else 26)) * steep * hcMult).roundToInt()
                val atk = (((if (isBoss) 22 else 14) + enemyLevel * 5) * steep * hcMult).roundToInt()
                val def = (((if (isBoss) 10 else 5) + enemyLevel * 3) * (0.85f + steep * 0.22f)).roundToInt()
                val affinity = ElementalAffinity.entries[(spireFloor + wave) % ElementalAffinity.entries.size]
                val spireNames = listOf("Spire Chronoguard", "Astral Myrmidon", "Voidscale Stalker", "Sunspire Construct")
                val name = if (isBoss) "Spire Sovereign F$spireFloor" else spireNames[(spireFloor + wave) % spireNames.size]
                val art = if (isBoss) {
                    if (spireFloor % 10 == 0) EnemyArtKey.WYRM else EnemyArtKey.COLOSSUS
                } else {
                    EnemyArtKey.entries[(spireFloor + wave) % 4]
                }
                CombatEnemy(
                    name = name,
                    title = if (isBoss) "Spire Sovereign Boss • Floor $spireFloor" else "Endless Spire • Floor $spireFloor",
                    level = enemyLevel,
                    maxHp = hp,
                    currentHp = hp,
                    attack = atk,
                    defense = def,
                    shield = if (isBoss) 18 + spireFloor * 3 else spireFloor,
                    isBoss = isBoss,
                    artKey = art,
                    elementalAffinity = affinity,
                    bestiaryKey = if (isBoss) "boss_spire_warden" else "",
                    nextIntent = rollIntent(atk, isBoss)
                )
            }
            BattleMode.REALM_CAMPAIGN -> {
                val realm = ProgressionEngine.realmSpec(realmNum)
                val isBoss = isFinalWave && (forceBoss || stageNum == 5)
                val baseRealmLevel = realm.recommendedLevel + (stageNum - 1) + (wave - 1)
                val enemyLevel = max(baseRealmLevel, profile.level.toInt().coerceAtLeast(1) + (wave - 1))
                val steep = steepLevelMultiplier(enemyLevel, realmNum)
                val baseHp = if (isBoss) 185 else 88
                val baseAtk = if (isBoss) 16 else 10
                val hp = (((baseHp + enemyLevel * (if (isBoss) 38 else 24) + (realmNum * 22)) * steep) * hcMult).roundToInt()
                val atk = (((baseAtk + enemyLevel * 4 + (realmNum * 3)) * steep) * hcMult).roundToInt()
                val def = ((((if (isBoss) 8 else 3) + enemyLevel * 2 + realmNum * 2) * (0.85f + steep * 0.20f))).roundToInt()

                val name = if (isBoss) {
                    realm.guardianName
                } else {
                    realm.minionNames[(stageNum + wave) % realm.minionNames.size]
                }
                val art = if (isBoss) {
                    when (realmNum % 3) {
                        1 -> EnemyArtKey.COLOSSUS
                        2 -> EnemyArtKey.WYRM
                        else -> EnemyArtKey.DRAKE
                    }
                } else {
                    when ((realmNum + stageNum + wave) % 4) {
                        0 -> EnemyArtKey.SENTINEL
                        1 -> EnemyArtKey.STALKER
                        2 -> EnemyArtKey.CONSTRUCT
                        else -> EnemyArtKey.DRAKE
                    }
                }
                CombatEnemy(
                    name = name,
                    title = if (isBoss) "${realm.guardianTitle} (Realm $realmNum Boss)" else "${realm.name} • Stage $stageNum/5 (Wave $wave/$totalWaves)",
                    level = enemyLevel,
                    maxHp = hp,
                    currentHp = hp,
                    attack = atk,
                    defense = def,
                    shield = if (isBoss) 14 + realmNum * 4 else 0,
                    isBoss = isBoss,
                    artKey = art,
                    elementalAffinity = realm.elementalAffinity,
                    bestiaryKey = if (isBoss) "boss_realm_$realmNum" else "",
                    nextIntent = rollIntent(atk, isBoss)
                )
            }
        }
    }

    fun executeStrike(
        battle: BattleState,
        profile: HeroProfile,
        stats: DerivedHeroStats
    ): TurnResolution {
        val enemy = battle.enemy ?: return TurnResolution(battle, profile.currentHp, profile.strideEnergy)
        val callouts = mutableListOf<String>()

        val elemMult = stats.activeElement.damageMultiplierAgainst(enemy.elementalAffinity)
        if (elemMult > 1.05f) callouts.add("SUPER EFFECTIVE (${stats.activeElement.label})!")
        else if (elemMult < 0.95f) callouts.add("RESISTED (${stats.activeElement.label})")

        val enemySundered = enemy.statuses.any { it.type == CombatStatusType.SUNDER }
        val sunderPierce = if (enemySundered) 35 else 0
        val totalPierce = (stats.armorPiercePercent + sunderPierce).coerceAtMost(85)

        val isCrit = Random.nextInt(100) < stats.critChancePercent
        val critMult = if (isCrit) 1.60f else 1.0f
        if (isCrit) callouts.add("CRIT!")

        val effectiveEnemyDef = ((enemy.defense * (100 - totalPierce)) / 100f).roundToInt()
        if (totalPierce > 0 && enemy.defense > 0) {
            callouts.add("Pierce ${totalPierce}%")
        }

        val comboMult = 1.0f + (battle.comboCount.coerceIn(0, 5) * 0.10f)
        if (battle.comboCount > 0) callouts.add("${battle.comboCount}x COMBO!")

        val jitter = Random.nextInt(-2, 4)
        val rawDmg = max(7, ((stats.totalAttack * critMult * elemMult * comboMult).roundToInt() - (effectiveEnemyDef / 2) + jitter))
        val shieldAbsorbed = min(enemy.shield, rawDmg)
        val hpDmg = (rawDmg - shieldAbsorbed).coerceAtLeast(0)
        val nextEnemyHp = (enemy.currentHp - hpDmg).coerceAtLeast(0)
        val nextEnemyShield = (enemy.shield - shieldAbsorbed).coerceAtLeast(0)

        var nextHeroHp = profile.currentHp
        var lifestealHeal = 0
        if (stats.lifestealPercent > 0 && hpDmg > 0) {
            lifestealHeal = max(2, (hpDmg * stats.lifestealPercent) / 100)
            nextHeroHp = (nextHeroHp + lifestealHeal).coerceAtMost(stats.computedMaxHp.toLong())
            callouts.add("Leech +$lifestealHeal HP")
        }

        // Apply elemental proc chance on Strike (35% chance)
        var updatedEnemyStatuses = enemy.statuses
        if (Random.nextInt(100) < 35) {
            val procStatus = statusForElement(stats.activeElement, stats.totalAttack)
            updatedEnemyStatuses = applyOrRefreshStatus(updatedEnemyStatuses, procStatus)
            callouts.add("+${procStatus.type.label}")
        }

        val tempoMult = if (battle.weeklyMutatorLabel == "Quickened Tempo") 1.25f else 1.0f
        // Strictly real-life Steps and Sleep recharge Stride Energy (0 in-combat energy generation)
        val nextEnergy = profile.strideEnergy
        val nextValor = (battle.valor + ((if (isCrit) 35 else 24) * tempoMult).roundToInt()).coerceAtMost(100)
        val nextSynergy = (battle.petSynergy + 28).coerceAtMost(100)
        val nextPetBurst = (battle.petBurstCharge + ((if (isCrit) 22 else 15) * tempoMult).roundToInt()).coerceAtMost(100)
        val nextCombo = (battle.comboCount + if (isCrit) 1 else 0).coerceAtMost(5)

        val strikeLine = buildString {
            if (isCrit) append("CRITICAL STRIKE! ") else append("Hero strikes ")
            append("(${stats.activeElement.label}) for $hpDmg DMG")
            if (shieldAbsorbed > 0) append(" ($shieldAbsorbed shield)")
            if (lifestealHeal > 0) append(" • +$lifestealHeal Leech")
            append(".")
        }

        val updatedEnemy = enemy.copy(
            currentHp = nextEnemyHp,
            shield = nextEnemyShield,
            statuses = updatedEnemyStatuses
        )
        val midState = battle.copy(
            valor = nextValor,
            petSynergy = nextSynergy,
            petBurstCharge = nextPetBurst,
            comboCount = nextCombo,
            guarding = false,
            enemy = updatedEnemy,
            combatLog = (battle.combatLog + strikeLine).takeLast(9)
        )

        return resolveAfterPlayerAction(
            battle = midState,
            profile = profile.copy(currentHp = nextHeroHp, strideEnergy = nextEnergy),
            stats = stats,
            heroAction = BattleAnimAction.HERO_STRIKE,
            heroDamageDealt = hpDmg + shieldAbsorbed,
            dealtCrit = isCrit,
            lifestealHeal = lifestealHeal,
            callouts = callouts
        )
    }

    fun executeSolarSkill(
        battle: BattleState,
        profile: HeroProfile,
        stats: DerivedHeroStats
    ): TurnResolution {
        val enemy = battle.enemy ?: return TurnResolution(battle, profile.currentHp, profile.strideEnergy)
        val energyCost = 20L
        if (profile.strideEnergy < energyCost) {
            val log = (battle.combatLog + "Need 20 Stride Energy! Walk real steps or rest in the Sleep Sanctuary to recharge.").takeLast(9)
            return TurnResolution(battle.copy(combatLog = log), profile.currentHp, profile.strideEnergy)
        }

        val callouts = mutableListOf<String>()
        val elemMult = stats.activeElement.damageMultiplierAgainst(enemy.elementalAffinity)
        if (elemMult > 1.05f) callouts.add("SUPER EFFECTIVE (${stats.activeElement.label})!")

        val overdrive = battle.valor >= 100
        val arcanistBonus = if (profile.resolvedClass() == HeroClass.ARCANIST) 1.25f else 1.0f
        val gearSkillBonus = 1.0f + (stats.solarSkillBonusPercent / 100f)
        val overdriveMult = if (overdrive) 2.25f else 1.65f
        val isCrit = overdrive || Random.nextInt(100) < (stats.critChancePercent + 12)
        val critMult = if (isCrit) 1.40f else 1.0f

        if (overdrive) callouts.add("OVERDRIVE!")
        else if (isCrit) callouts.add("SKILL CRIT!")

        val enemySundered = enemy.statuses.any { it.type == CombatStatusType.SUNDER }
        val totalPierce = (stats.armorPiercePercent + 20 + if (enemySundered) 35 else 0).coerceAtMost(90)
        val effectiveDef = ((enemy.defense * (100 - totalPierce)) / 100f).roundToInt()

        val rawDmg = max(
            16,
            (stats.totalAttack * overdriveMult * arcanistBonus * gearSkillBonus * critMult * elemMult).roundToInt() - (effectiveDef / 3)
        )
        val shatteredShield = if (overdrive) 0 else (enemy.shield - min(enemy.shield, rawDmg)).coerceAtLeast(0)
        val hpDmg = if (overdrive) rawDmg else (rawDmg - min(enemy.shield, rawDmg)).coerceAtLeast(8)
        val nextEnemyHp = (enemy.currentHp - hpDmg).coerceAtLeast(0)

        // Guaranteed elemental status effect on Solar Skill!
        val procStatus = statusForElement(stats.activeElement, stats.totalAttack)
        val updatedEnemyStatuses = applyOrRefreshStatus(enemy.statuses, procStatus)
        callouts.add("${procStatus.type.label} (${procStatus.turnsRemaining}T)")

        var nextHeroHp = profile.currentHp
        var lifestealHeal = 0
        if (stats.lifestealPercent > 0 && hpDmg > 0) {
            lifestealHeal = max(3, (hpDmg * stats.lifestealPercent) / 100)
            nextHeroHp = (nextHeroHp + lifestealHeal).coerceAtMost(stats.computedMaxHp.toLong())
            callouts.add("Leech +$lifestealHeal HP")
        }

        val nextEnergy = (profile.strideEnergy - energyCost).coerceAtLeast(0L)
        val nextValor = if (overdrive) 0 else (battle.valor + 35).coerceAtMost(100)
        val nextSynergy = (battle.petSynergy + 35).coerceAtMost(100)

        val skillTitle = if (overdrive) "${stats.activeElement.title} Overdrive" else "${stats.activeElement.label} Surge"
        val logLine = "$skillTitle erupts for $hpDmg DMG & inflicts ${procStatus.type.label}!"

        val updatedEnemy = enemy.copy(
            currentHp = nextEnemyHp,
            shield = shatteredShield,
            statuses = updatedEnemyStatuses
        )
        val midState = battle.copy(
            valor = nextValor,
            petSynergy = nextSynergy,
            guarding = false,
            enemy = updatedEnemy,
            combatLog = (battle.combatLog + logLine).takeLast(9)
        )

        return resolveAfterPlayerAction(
            battle = midState,
            profile = profile.copy(currentHp = nextHeroHp, strideEnergy = nextEnergy),
            stats = stats,
            heroAction = BattleAnimAction.HERO_SOLAR_SKILL,
            heroDamageDealt = hpDmg,
            dealtCrit = isCrit,
            lifestealHeal = lifestealHeal,
            callouts = callouts
        )
    }

    /**
     * Executes one of the hero's 3 level-unlocked, Sol-Coin-upgradable Class Active Skills (index 0..2).
     * Consumes Valor (built via Strike/Guard) and tracks per-skill turn cooldowns cleanly.
     */
    fun executeClassActiveSkill(
        skillIndex: Int,
        battle: BattleState,
        profile: HeroProfile,
        stats: DerivedHeroStats
    ): TurnResolution {
        val enemy = battle.enemy ?: return TurnResolution(battle, profile.currentHp, profile.strideEnergy)
        val activeSkills = ClassSkillCatalog.activeSkillsFor(profile.resolvedClass())
        val skill = activeSkills.getOrNull(skillIndex)
            ?: return TurnResolution(battle, profile.currentHp, profile.strideEnergy)

        if (!skill.isUnlockedAt(profile.level.toInt())) {
            val log = (battle.combatLog + "${skill.name} unlocks at Hero Level ${skill.unlockLevel}!").takeLast(9)
            return TurnResolution(battle.copy(combatLog = log), profile.currentHp, profile.strideEnergy)
        }
        val rank = ClassSkillCatalog.effectiveRank(profile, skill)
        if (rank <= 0) {
            val log = (battle.combatLog + "Train ${skill.name} Rank I with Sol Coins at the Skill Shrine or Skills tab!").takeLast(9)
            return TurnResolution(battle.copy(combatLog = log), profile.currentHp, profile.strideEnergy)
        }
        val currentCd = battle.skillCooldowns.getOrElse(skillIndex) { 0 }
        if (currentCd > 0) {
            val log = (battle.combatLog + "${skill.evolvedNameForRank(rank)} is on cooldown ($currentCd turn(s) left).").takeLast(9)
            return TurnResolution(battle.copy(combatLog = log), profile.currentHp, profile.strideEnergy)
        }
        val effectiveValorCost = skill.effectiveValorCost(rank)
        val effectiveCdTurns = skill.effectiveCooldownTurns(rank)
        val evolvedSkillName = skill.evolvedNameForRank(rank)
        val masteryTier = skill.masteryTierForRank(rank)
        if (battle.valor < effectiveValorCost) {
            val log = (battle.combatLog + "$evolvedSkillName needs $effectiveValorCost Valor (currently ${battle.valor}). Use Strike or Guard!").takeLast(9)
            return TurnResolution(battle.copy(combatLog = log), profile.currentHp, profile.strideEnergy)
        }

        val callouts = mutableListOf("${evolvedSkillName.uppercase()} [${masteryTier.roman} R$rank]!")
        val elemMult = stats.activeElement.damageMultiplierAgainst(enemy.elementalAffinity)
        if (elemMult > 1.05f) callouts.add("SUPER EFFECTIVE!")

        val rankMult = skill.effectiveMultiplier(rank)
        val gearSkillBonus = 1.0f + (stats.solarSkillBonusPercent / 100f)
        val enemySundered = enemy.statuses.any { it.type == CombatStatusType.SUNDER }
        val enemyDebuffed = enemy.statuses.any { !it.type.isBuff }

        var extraCritPct = 0
        var guaranteedCrit = false
        var extraPiercePct = if (enemySundered) 35 else 0
        var bonusFlatPower = 0
        var conditionalMult = 1.0f
        var barrierGain = 0
        var directHeal = 0
        var extraLifestealPct = 0
        var valorRefund = 0
        var updatedStatuses = enemy.statuses

        when (skill.activeEffect) {
            ActiveSkillEffect.SUNBREAKER_BASH -> {
                updatedStatuses = applyOrRefreshStatus(updatedStatuses, ActiveCombatStatus(CombatStatusType.SUNDER, 3, 35))
                callouts.add("SUNDER 3T")
                val stunChance = (30 + (rank - 1) * 5).coerceAtMost(75)
                if (Random.nextInt(100) < stunChance) {
                    updatedStatuses = applyOrRefreshStatus(updatedStatuses, ActiveCombatStatus(CombatStatusType.STUN, 1, 1))
                    callouts.add("STUNNED!")
                }
            }
            ActiveSkillEffect.AEGIS_BULWARK -> {
                val pct = 0.26f + ((rank - 1) * 0.04f)
                barrierGain = max(18, (stats.computedMaxHp * pct).roundToInt())
                callouts.add("AEGIS +$barrierGain")
            }
            ActiveSkillEffect.SOLAR_JUDGMENT -> {
                bonusFlatPower = (stats.totalDefense * 0.65f).roundToInt()
                val healPct = 0.12f + ((rank - 1) * 0.02f)
                directHeal = max(14, (stats.computedMaxHp * healPct).roundToInt())
                callouts.add("+$directHeal HP")
            }
            ActiveSkillEffect.PYRE_BOLT -> {
                val burnPotency = max(12, ((stats.totalAttack * (0.34f + rank * 0.05f))).roundToInt())
                updatedStatuses = applyOrRefreshStatus(updatedStatuses, ActiveCombatStatus(CombatStatusType.BURN, 3, burnPotency))
                callouts.add("SCORCH BURN ($burnPotency/T)")
            }
            ActiveSkillEffect.ASTRAL_NOVA -> {
                extraPiercePct += 35 + ((rank - 1) * 3)
                valorRefund = 12
                callouts.add("+12 VALOR")
            }
            ActiveSkillEffect.ECLIPSE_METEOR -> {
                val burnPotency = max(16, ((stats.totalAttack * (0.42f + rank * 0.06f))).roundToInt())
                updatedStatuses = applyOrRefreshStatus(updatedStatuses, ActiveCombatStatus(CombatStatusType.BURN, 3, burnPotency))
                updatedStatuses = applyOrRefreshStatus(updatedStatuses, ActiveCombatStatus(CombatStatusType.SUNDER, 3, 35))
                callouts.add("METEOR BURN + SUNDER")
            }
            ActiveSkillEffect.FROSTBITE_SHOT -> {
                extraCritPct += (rank - 1) * 3
                updatedStatuses = applyOrRefreshStatus(updatedStatuses, ActiveCombatStatus(CombatStatusType.FROSTBITE, 3, 25))
                callouts.add("FROSTBITE -25% ATK")
            }
            ActiveSkillEffect.GALE_VOLLEY -> {
                extraCritPct += 20 + ((rank - 1) * 3)
                callouts.add("3-ARROW VOLLEY!")
            }
            ActiveSkillEffect.HORIZON_SNIPE -> {
                guaranteedCrit = true
                extraPiercePct += 40
                callouts.add("GUARANTEED CRIT!")
            }
            ActiveSkillEffect.VENOM_FANG -> {
                extraPiercePct += 15
                val poisonPotency = max(12, ((stats.totalAttack * (0.35f + rank * 0.05f))).roundToInt())
                updatedStatuses = applyOrRefreshStatus(updatedStatuses, ActiveCombatStatus(CombatStatusType.POISON, 3, poisonPotency))
                callouts.add("VOID POISON ($poisonPotency/T)")
            }
            ActiveSkillEffect.PHANTOM_STEP -> {
                extraLifestealPct = 30
                val shieldPct = 0.14f + ((rank - 1) * 0.035f)
                barrierGain = max(14, (stats.computedMaxHp * shieldPct).roundToInt())
                callouts.add("SHADOW BARRIER +$barrierGain")
            }
            ActiveSkillEffect.NIGHTFALL_EXECUTION -> {
                if (enemyDebuffed) {
                    conditionalMult = 1.40f
                    callouts.add("EXECUTE +40%!")
                }
                extraPiercePct += 25
            }
            null -> {}
        }

        val isCrit = guaranteedCrit || Random.nextInt(100) < (stats.critChancePercent + extraCritPct)
        val critMult = if (isCrit) 1.50f else 1.0f
        if (isCrit && !guaranteedCrit) callouts.add("SKILL CRIT!")

        val totalPierce = (stats.armorPiercePercent + extraPiercePct).coerceAtMost(92)
        val effectiveDef = ((enemy.defense * (100 - totalPierce)) / 100f).roundToInt()

        val rawDmg = max(
            14,
            (((stats.totalAttack + bonusFlatPower) * rankMult * gearSkillBonus * conditionalMult * critMult * elemMult).roundToInt()) - (effectiveDef / 3)
        )
        val shieldAbsorbed = min(enemy.shield, rawDmg)
        val hpDmg = (rawDmg - shieldAbsorbed).coerceAtLeast(if (rawDmg > 0) 6 else 0)
        val nextEnemyHp = (enemy.currentHp - hpDmg).coerceAtLeast(0)
        val nextEnemyShield = (enemy.shield - shieldAbsorbed).coerceAtLeast(0)

        var nextHeroHp = profile.currentHp
        var totalHeal = directHeal
        val effectiveLifesteal = stats.lifestealPercent + extraLifestealPct
        if (effectiveLifesteal > 0 && hpDmg > 0) {
            val leech = max(3, (hpDmg * effectiveLifesteal) / 100)
            totalHeal += leech
            callouts.add("Leech +$leech HP")
        }
        if (totalHeal > 0) {
            nextHeroHp = (nextHeroHp + totalHeal).coerceAtMost(stats.computedMaxHp.toLong())
        }

        val nextValor = (battle.valor - effectiveValorCost + valorRefund).coerceIn(0, 100)
        val nextSynergy = (battle.petSynergy + 30).coerceAtMost(100)
        val nextCooldowns = List(3) { idx ->
            if (idx == skillIndex) (effectiveCdTurns + 1) else battle.skillCooldowns.getOrElse(idx) { 0 }
        }

        val logLine = "$evolvedSkillName (${masteryTier.roman} • Rank $rank) strikes for $hpDmg DMG" +
            (if (barrierGain > 0) " • +$barrierGain Barrier" else "") +
            (if (totalHeal > 0) " • +$totalHeal HP" else "") + "!"

        val midState = battle.copy(
            valor = nextValor,
            petSynergy = nextSynergy,
            heroBarrier = battle.heroBarrier + barrierGain,
            skillCooldowns = nextCooldowns,
            guarding = skill.activeEffect == ActiveSkillEffect.AEGIS_BULWARK,
            enemy = enemy.copy(
                currentHp = nextEnemyHp,
                shield = nextEnemyShield,
                statuses = updatedStatuses
            ),
            combatLog = (battle.combatLog + logLine).takeLast(9)
        )

        return resolveAfterPlayerAction(
            battle = midState,
            profile = profile.copy(currentHp = nextHeroHp),
            stats = stats,
            heroAction = BattleAnimAction.HERO_SOLAR_SKILL,
            heroDamageDealt = hpDmg + shieldAbsorbed,
            dealtCrit = isCrit,
            lifestealHeal = totalHeal,
            callouts = callouts
        )
    }

    /**
     * Unleashes the Hero's Class Specialization Ultimate AND Active Companion Pet Combo when
     * Pet Synergy >= 100% (or Valor >= 100%). Costs 0 Energy and deals devastating elemental combo effects!
     */
    fun executeUltimateAndPetCombo(
        battle: BattleState,
        profile: HeroProfile,
        stats: DerivedHeroStats,
        equippedPet: OwnedPet?
    ): TurnResolution {
        val enemy = battle.enemy ?: return TurnResolution(battle, profile.currentHp, profile.strideEnergy)
        if (battle.petSynergy < 100 && battle.valor < 100) {
            val log = (battle.combatLog + "Build 100% Pet Synergy or Valor to unleash Ultimate & Companion Combo!").takeLast(9)
            return TurnResolution(battle.copy(combatLog = log), profile.currentHp, profile.strideEnergy)
        }

        val spec = stats.specialization
        val petType = equippedPet?.resolvedType()
        val callouts = mutableListOf<String>()
        callouts.add("${spec.tierRoman} ULTIMATE: ${spec.ultimateName.uppercase()}!")

        val ultElemMult = spec.affinity.damageMultiplierAgainst(enemy.elementalAffinity)
        val petMult = petType?.comboDamageMult ?: 1.35f
        val tierBaseMult = when (spec.classTier) {
            4 -> 3.45f
            3 -> 2.90f
            else -> 2.45f
        }
        val combinedMult = tierBaseMult + (petMult - 1.0f)

        val rawDmg = max(
            24,
            (stats.totalAttack * combinedMult * ultElemMult).roundToInt() - (enemy.defense / 4)
        )
        val nextEnemyHp = (enemy.currentHp - rawDmg).coerceAtLeast(0)

        var updatedEnemyStatuses = enemy.statuses
        // Apply Specialization Ultimate status effects across all 4 Class Tiers
        when (spec) {
            ClassSpecialization.SUNSHIELD_PALADIN,
            ClassSpecialization.SOLAR_TEMPLAR_ARCHON -> {
                updatedEnemyStatuses = applyOrRefreshStatus(
                    updatedEnemyStatuses,
                    ActiveCombatStatus(CombatStatusType.SUNDER, 3, 35)
                )
            }
            ClassSpecialization.OBSIDIAN_JUGGERNAUT,
            ClassSpecialization.TITANBREAKER_WARLORD,
            ClassSpecialization.IMMORTAL_SUNKING -> {
                updatedEnemyStatuses = applyOrRefreshStatus(
                    updatedEnemyStatuses,
                    ActiveCombatStatus(CombatStatusType.STUN, 1, 1)
                )
                updatedEnemyStatuses = applyOrRefreshStatus(
                    updatedEnemyStatuses,
                    ActiveCombatStatus(CombatStatusType.SUNDER, 3, 35)
                )
                callouts.add("ENEMY STUNNED + SUNDER!")
            }
            ClassSpecialization.GALE_RANGER,
            ClassSpecialization.STORM_WARDEN,
            ClassSpecialization.STARFALL_DEADEYE,
            ClassSpecialization.VERDANT_BEASTMASTER,
            ClassSpecialization.HORIZON_SOVEREIGN -> {
                updatedEnemyStatuses = applyOrRefreshStatus(
                    updatedEnemyStatuses,
                    ActiveCombatStatus(CombatStatusType.FROSTBITE, 3, 25)
                )
                if (spec.classTier >= 3) {
                    updatedEnemyStatuses = applyOrRefreshStatus(
                        updatedEnemyStatuses,
                        ActiveCombatStatus(CombatStatusType.SUNDER, 3, 35)
                    )
                }
                callouts.add("FROSTBITE -25% ATK!")
            }
            ClassSpecialization.MIRAGE_ASSASSIN,
            ClassSpecialization.NIGHTFALL_REAPER,
            ClassSpecialization.PHANTOM_SHOGUN,
            ClassSpecialization.CRIMSON_DERVISH,
            ClassSpecialization.VOID_EMPEROR_BLADE -> {
                updatedEnemyStatuses = applyOrRefreshStatus(
                    updatedEnemyStatuses,
                    ActiveCombatStatus(CombatStatusType.POISON, 3, max(12, stats.totalAttack / 2))
                )
                if (spec.classTier >= 3) {
                    updatedEnemyStatuses = applyOrRefreshStatus(
                        updatedEnemyStatuses,
                        ActiveCombatStatus(CombatStatusType.SUNDER, 3, 35)
                    )
                }
                callouts.add("LETHAL POISON!")
            }
            ClassSpecialization.DAWN_ARCANIST,
            ClassSpecialization.CELESTIAL_ARCHMAGE,
            ClassSpecialization.ASTRAL_GODWEAVER -> {
                updatedEnemyStatuses = applyOrRefreshStatus(
                    updatedEnemyStatuses,
                    ActiveCombatStatus(CombatStatusType.BURN, 4, max(14, stats.totalAttack / 2))
                )
                updatedEnemyStatuses = applyOrRefreshStatus(
                    updatedEnemyStatuses,
                    ActiveCombatStatus(CombatStatusType.SUNDER, 3, 35)
                )
                callouts.add("SUPERNOVA BURN!")
            }
            ClassSpecialization.ECLIPSE_INQUISITOR,
            ClassSpecialization.ABYSSAL_CHRONOMANCER -> {
                updatedEnemyStatuses = applyOrRefreshStatus(
                    updatedEnemyStatuses,
                    ActiveCombatStatus(CombatStatusType.POISON, 3, max(12, stats.totalAttack / 2))
                )
                updatedEnemyStatuses = applyOrRefreshStatus(
                    updatedEnemyStatuses,
                    ActiveCombatStatus(CombatStatusType.SUNDER, 3, 35)
                )
                if (spec == ClassSpecialization.ABYSSAL_CHRONOMANCER) {
                    updatedEnemyStatuses = applyOrRefreshStatus(
                        updatedEnemyStatuses,
                        ActiveCombatStatus(CombatStatusType.STUN, 1, 1)
                    )
                    callouts.add("CHRONO STASIS STUN!")
                }
            }
        }

        // Apply Pet Companion Combo Bonus
        var comboHeal = (stats.computedMaxHp * (petType?.comboHealPct ?: 0.15f)).roundToInt()
        if (spec == ClassSpecialization.ECLIPSE_INQUISITOR ||
            spec == ClassSpecialization.ABYSSAL_CHRONOMANCER ||
            spec == ClassSpecialization.MIRAGE_ASSASSIN ||
            spec == ClassSpecialization.NIGHTFALL_REAPER ||
            spec == ClassSpecialization.PHANTOM_SHOGUN ||
            spec == ClassSpecialization.CRIMSON_DERVISH ||
            spec == ClassSpecialization.VOID_EMPEROR_BLADE ||
            spec == ClassSpecialization.VERDANT_BEASTMASTER ||
            spec == ClassSpecialization.IMMORTAL_SUNKING
        ) {
            comboHeal += (rawDmg * 0.25f).roundToInt()
        }
        var nextBarrier = battle.heroBarrier
        if (spec == ClassSpecialization.SUNSHIELD_PALADIN ||
            spec == ClassSpecialization.SOLAR_TEMPLAR_ARCHON ||
            spec == ClassSpecialization.IMMORTAL_SUNKING ||
            spec == ClassSpecialization.ASTRAL_GODWEAVER ||
            petType?.name == "AEGIS_SCARAB"
        ) {
            val addedBarrier = (stats.computedMaxHp * (if (spec.classTier >= 3) 0.45f else 0.35f)).roundToInt()
            nextBarrier += addedBarrier
            callouts.add("AEGIS +$addedBarrier")
        }
        if (petType != null) {
            callouts.add("PET: ${petType.activeSkillName}!")
            val petStatus = statusForElement(petType.elementalAffinity, stats.totalAttack)
            updatedEnemyStatuses = applyOrRefreshStatus(updatedEnemyStatuses, petStatus)
        }

        val nextHeroHp = (profile.currentHp + comboHeal).coerceAtMost(stats.computedMaxHp.toLong())
        val nextEnergy = profile.strideEnergy

        val comboLabel = if (petType != null) {
            "${spec.ultimateName} + ${petType.activeSkillName} deal $rawDmg DMG (+$comboHeal HP)!"
        } else {
            "${spec.ultimateName} unleashes $rawDmg DMG (+$comboHeal HP)!"
        }

        val updatedEnemy = enemy.copy(
            currentHp = nextEnemyHp,
            shield = 0,
            statuses = updatedEnemyStatuses
        )
        val midState = battle.copy(
            valor = if (battle.petSynergy >= 100) battle.valor else 0,
            petSynergy = 0,
            petBurstCharge = (battle.petBurstCharge + 25).coerceAtMost(100),
            comboCount = (battle.comboCount + 1).coerceAtMost(5),
            heroBarrier = nextBarrier,
            guarding = false,
            enemy = updatedEnemy,
            combatLog = (battle.combatLog + comboLabel).takeLast(9)
        )

        return resolveAfterPlayerAction(
            battle = midState,
            profile = profile.copy(currentHp = nextHeroHp, strideEnergy = nextEnergy),
            stats = stats,
            heroAction = BattleAnimAction.HERO_SOLAR_SKILL,
            heroDamageDealt = rawDmg,
            dealtCrit = true,
            lifestealHeal = comboHeal,
            callouts = callouts
        )
    }

    /**
     * Unleashes the equipped Companion's Evolved Signature Burst Skill (Stage 1 / Stage 2 Branch / Stage 3 Apex)
     * when petBurstCharge >= 100. Costs 0 Energy!
     */
    fun executePetEvolutionBurst(
        battle: BattleState,
        profile: HeroProfile,
        stats: DerivedHeroStats,
        equippedPet: OwnedPet?
    ): TurnResolution {
        val enemy = battle.enemy ?: return TurnResolution(battle, profile.currentHp, profile.strideEnergy)
        if (equippedPet == null) {
            val log = (battle.combatLog + "Equip a companion pet in the Pets tab to unleash Pet Evolution Bursts!").takeLast(9)
            return TurnResolution(battle.copy(combatLog = log), profile.currentHp, profile.strideEnergy)
        }
        if (battle.petBurstCharge < 100) {
            val log = (battle.combatLog + "Pet Burst at ${battle.petBurstCharge}% — land Strikes & Perfect Parries to reach 100%!").takeLast(9)
            return TurnResolution(battle.copy(combatLog = log), profile.currentHp, profile.strideEnergy)
        }

        val petType = equippedPet.resolvedType()
        val evoForm = equippedPet.resolvedForm()
        val stage = equippedPet.resolvedStage()
        val branch = equippedPet.resolvedBranch()
        val comboMult = 1.0f + (battle.comboCount.coerceIn(0, 5) * 0.10f)
        val elemMult = petType.elementalAffinity.damageMultiplierAgainst(enemy.elementalAffinity)

        val baseMult = when (stage) {
            3 -> 2.85f
            2 -> 2.15f
            else -> 1.65f
        }
        val branchDmgMult = if (branch == PetEvolutionBranch.VANGUARD) 1.22f else 1.08f
        val rawDmg = max(
            20,
            (stats.totalAttack * baseMult * branchDmgMult * comboMult * elemMult).roundToInt() - (enemy.defense / 4)
        )
        val nextEnemyHp = (enemy.currentHp - rawDmg).coerceAtLeast(0)

        val callouts = mutableListOf(
            "STAGE $stage BURST: ${evoForm.burstSkillName.uppercase()}!",
            evoForm.formName.uppercase()
        )
        var updatedStatuses = applyOrRefreshStatus(
            enemy.statuses,
            statusForElement(petType.elementalAffinity, stats.totalAttack)
        )
        var nextBarrier = battle.heroBarrier
        var healAmt = (stats.computedMaxHp * (if (branch == PetEvolutionBranch.MYSTIC) 0.24f else 0.14f)).roundToInt()

        if (branch == PetEvolutionBranch.MYSTIC || stage >= 3) {
            val barrier = (stats.computedMaxHp * (0.20f + stage * 0.06f)).roundToInt()
            nextBarrier += barrier
            callouts.add("ASTRAL BARRIER +$barrier")
        }
        if (branch == PetEvolutionBranch.VANGUARD || stage >= 3) {
            updatedStatuses = applyOrRefreshStatus(
                updatedStatuses,
                ActiveCombatStatus(CombatStatusType.SUNDER, 3, 40)
            )
            callouts.add("SUNDERED!")
        }

        val nextHeroHp = (profile.currentHp + healAmt).coerceAtMost(stats.computedMaxHp.toLong())
        val logLine = "${evoForm.formName} unleashes ${evoForm.burstSkillName} for $rawDmg DMG (+$healAmt HP)!"

        val midState = battle.copy(
            petBurstCharge = 0,
            comboCount = (battle.comboCount + 1).coerceAtMost(5),
            heroBarrier = nextBarrier,
            guarding = false,
            enemy = enemy.copy(
                currentHp = nextEnemyHp,
                shield = (enemy.shield - rawDmg / 2).coerceAtLeast(0),
                statuses = updatedStatuses
            ),
            combatLog = (battle.combatLog + logLine).takeLast(9)
        )

        return resolveAfterPlayerAction(
            battle = midState,
            profile = profile.copy(currentHp = nextHeroHp),
            stats = stats,
            heroAction = BattleAnimAction.HERO_ULTIMATE_COMBO,
            heroDamageDealt = rawDmg,
            dealtCrit = true,
            lifestealHeal = healAmt,
            callouts = callouts
        )
    }

    fun executeGuard(
        battle: BattleState,
        profile: HeroProfile,
        stats: DerivedHeroStats
    ): TurnResolution {
        val enemy = battle.enemy ?: return TurnResolution(battle, profile.currentHp, profile.strideEnergy)
        val nextEnergy = profile.strideEnergy
        val nextValor = (battle.valor + 24).coerceAtMost(100)
        val nextSynergy = (battle.petSynergy + 28).coerceAtMost(100)

        val barrierGain = if (stats.specialization == ClassSpecialization.SUNSHIELD_PALADIN) {
            max(12, (stats.computedMaxHp * 0.14f).roundToInt())
        } else {
            max(6, (stats.totalDefense * 0.45f).roundToInt())
        }
        val nextBarrier = battle.heroBarrier + barrierGain

        val counterDmg = max(4, stats.totalDefense / 3)
        val nextEnemyHp = (enemy.currentHp - counterDmg).coerceAtLeast(0)
        val updatedEnemy = enemy.copy(currentHp = nextEnemyHp)

        val midState = battle.copy(
            valor = nextValor,
            petSynergy = nextSynergy,
            heroBarrier = nextBarrier,
            guarding = true,
            enemy = updatedEnemy,
            combatLog = (battle.combatLog + "Aegis Guard raised (-60% incoming DMG, +$barrierGain Barrier, +24 Valor, $counterDmg counter).").takeLast(9)
        )

        return resolveAfterPlayerAction(
            battle = midState,
            profile = profile.copy(strideEnergy = nextEnergy),
            stats = stats,
            heroAction = BattleAnimAction.HERO_GUARD,
            heroDamageDealt = counterDmg,
            callouts = listOf("AEGIS GUARD +$barrierGain")
        )
    }

    fun executeElixir(
        battle: BattleState,
        profile: HeroProfile,
        stats: DerivedHeroStats,
        elixir: InventoryItem
    ): TurnResolution {
        if (battle.elixirCooldownTurns > 0) {
            val log = (battle.combatLog + "Hardcore Elixir Cooldown active (${battle.elixirCooldownTurns} turn(s) left)! Use Guard or Pet Combo to survive.").takeLast(9)
            return TurnResolution(battle.copy(combatLog = log), profile.currentHp, profile.strideEnergy)
        }
        val heal = elixir.healAmount.coerceAtLeast(65L)
        val nextHp = (profile.currentHp + heal).coerceAtMost(stats.computedMaxHp.toLong())
        val regenStatus = ActiveCombatStatus(CombatStatusType.REGEN, 2, max(10, (heal / 4L).toInt()))
        val midState = battle.copy(
            guarding = false,
            elixirCooldownTurns = 3,
            heroStatuses = applyOrRefreshStatus(battle.heroStatuses, regenStatus),
            combatLog = (battle.combatLog + "Drank ${elixir.name} (+$heal HP & 2-turn Regen • 3-Turn Elixir Cooldown).").takeLast(9)
        )
        return resolveAfterPlayerAction(
            battle = midState,
            profile = profile.copy(currentHp = nextHp),
            stats = stats,
            consumedElixirId = elixir.id,
            heroAction = BattleAnimAction.HERO_ELIXIR,
            elixirHeal = heal.toInt(),
            callouts = listOf("+$heal HP (3T CD)")
        )
    }

    fun executeRetreat(
        battle: BattleState,
        profile: HeroProfile
    ): TurnResolution {
        val unbankedToll = (profile.solCoins * 0.10f).roundToInt().toLong()
        val totalLost = (battle.stakedSolCoins + unbankedToll).coerceAtMost(profile.solCoins)
        val outcome = BattleOutcome(
            victory = false,
            retreated = true,
            mode = battle.mode,
            realmNumber = battle.realmNumber,
            stageNumber = battle.stageNumber,
            spireFloor = battle.spireFloor,
            solCoinsLost = totalLost,
            gearDurabilityLossPct = 6,
            summaryLine = if (totalLost > 0L) {
                "Retreated to camp (-$totalLost Sol Coins retreat & wager toll, -6% Gear Durability)."
            } else {
                "Retreated safely to Sunlit Camp (-6% Gear Durability)."
            }
        )
        return TurnResolution(
            nextBattle = battle.copy(
                active = false,
                guarding = false,
                outcome = outcome,
                combatLog = (battle.combatLog + outcome.summaryLine).takeLast(9)
            ),
            updatedHp = profile.currentHp,
            updatedEnergy = profile.strideEnergy
        )
    }

    private fun resolveAfterPlayerAction(
        battle: BattleState,
        profile: HeroProfile,
        stats: DerivedHeroStats,
        consumedElixirId: String? = null,
        heroAction: BattleAnimAction = BattleAnimAction.HERO_STRIKE,
        heroDamageDealt: Int = 0,
        dealtCrit: Boolean = false,
        lifestealHeal: Int = 0,
        elixirHeal: Int = 0,
        callouts: List<String> = emptyList()
    ): TurnResolution {
        var enemy = battle.enemy ?: return TurnResolution(battle, profile.currentHp, profile.strideEnergy, consumedElixirId)
        val seqId = System.currentTimeMillis()
        val nextCooldown = (battle.elixirCooldownTurns - 1).coerceAtLeast(0)
        val nextSkillCooldowns = List(3) { idx ->
            (battle.skillCooldowns.getOrElse(idx) { 0 } - 1).coerceAtLeast(0)
        }

        // 1. Tick Enemy DoT Status Effects (Burn & Poison) before checking death
        var enemyDotDamage = 0
        val dotNames = mutableListOf<String>()
        enemy.statuses.forEach { st ->
            if (st.type == CombatStatusType.BURN || st.type == CombatStatusType.POISON) {
                enemyDotDamage += st.potency
                dotNames.add("${st.type.label} -${st.potency}")
            }
        }
        if (enemyDotDamage > 0 && enemy.currentHp > 0) {
            enemy = enemy.copy(currentHp = (enemy.currentHp - enemyDotDamage).coerceAtLeast(0))
        }

        // 2. Check if current wave enemy is defeated!
        if (enemy.currentHp <= 0) {
            if (battle.currentWave < battle.totalWaves) {
                val nextWave = battle.currentWave + 1
                val nextEnemy = buildWaveEnemy(
                    profile = profile,
                    mode = battle.mode,
                    realmNum = battle.realmNumber,
                    stageNum = battle.stageNumber,
                    spireFloor = battle.spireFloor,
                    wave = nextWave,
                    totalWaves = battle.totalWaves,
                    forceBoss = battle.stageNumber == 5,
                    hardcore = battle.hardcoreOathActive,
                    todayKey = ""
                )
                val waveHeal = max(8L, (stats.computedMaxHp * 0.10f).roundToInt().toLong())
                val healedHp = (profile.currentHp + waveHeal).coerceAtMost(stats.computedMaxHp.toLong())
                val waveLogs = (
                    battle.combatLog +
                        "${enemy.name} slain! (+${waveHeal} Breather HP)" +
                        "WAVE $nextWave/${battle.totalWaves}: ${nextEnemy.name} (${nextEnemy.elementalAffinity.label}) enters the arena!"
                    ).takeLast(9)

                return TurnResolution(
                    nextBattle = battle.copy(
                        currentWave = nextWave,
                        turnNumber = battle.turnNumber + 1,
                        elixirCooldownTurns = nextCooldown,
                        skillCooldowns = nextSkillCooldowns,
                        guarding = false,
                        enemy = nextEnemy,
                        combatLog = waveLogs,
                        lastAnimEvent = BattleAnimEvent(
                            sequenceId = seqId,
                            heroAction = heroAction,
                            heroDamageDealt = heroDamageDealt + enemyDotDamage,
                            heroWasCrit = dealtCrit,
                            heroLifestealHeal = lifestealHeal,
                            heroElixirHeal = elixirHeal,
                            perkCallouts = callouts + listOf("WAVE $nextWave/${battle.totalWaves}!")
                        )
                    ),
                    updatedHp = healedHp,
                    updatedEnergy = profile.strideEnergy,
                    consumedElixirId = consumedElixirId,
                    dealtCrit = dealtCrit
                )
            }

            // Final Wave Cleared -> VICTORY!
            val hcRewardMult = if (battle.hardcoreOathActive) 1.50f else 1.0f
            val baseXp = ((if (enemy.isBoss) 95L else 48L) + (enemy.level * 16L))
            val xpEarned = (baseXp * stats.totalXpMultiplier * hcRewardMult).roundToInt().toLong().coerceAtLeast(30L)
            val baseShards = ((if (enemy.isBoss) 28L else 11L) + (enemy.level * 4L))
            val shardsEarned = (baseShards * stats.totalSunshardMultiplier * hcRewardMult).roundToInt().toLong().coerceAtLeast(8L)

            val wagerProfit = if (battle.stakedSolCoins > 0L) battle.stakedSolCoins else 0L
            val baseCoins = when {
                battle.mode == BattleMode.WEEKLY_BOSS_RAID -> 28L
                enemy.isBoss -> ExpeditionEngine.BOSS_VICTORY_SOL_COINS
                battle.hardcoreOathActive -> 5L
                else -> 3L
            }
            val hcCoinMult = if (battle.hardcoreOathActive) 1.60f else 1.0f
            val solCoinsEarned = (baseCoins * hcCoinMult).roundToInt().toLong() + wagerProfit

            val oreEarned = (if (enemy.isBoss) 10L else 4L) + (if (battle.hardcoreOathActive) 4L else 0L)
            val herbsEarned = (if (enemy.isBoss) 8L else 3L) + (if (battle.hardcoreOathActive) 3L else 0L)
            val essenceEarned = when {
                battle.mode == BattleMode.DAILY_ELEMENTAL_RIFT -> 10L
                enemy.isBoss -> 6L
                else -> 2L
            }

            val droppedRune: ElementalAffinity? = when {
                battle.mode == BattleMode.DAILY_ELEMENTAL_RIFT -> enemy.elementalAffinity
                battle.mode == BattleMode.WEEKLY_BOSS_RAID -> ElementalAffinity.entries[Random.nextInt(ElementalAffinity.entries.size)]
                enemy.isBoss && (battle.hardcoreOathActive || Random.nextInt(100) < 55) -> enemy.elementalAffinity
                else -> null
            }

            val chestTier = when {
                battle.mode == BattleMode.ENDLESS_SPIRE && enemy.isBoss -> LootChestTier.SPIRE_SOVEREIGN
                battle.mode == BattleMode.WEEKLY_BOSS_RAID -> LootChestTier.SPIRE_SOVEREIGN
                enemy.isBoss -> LootChestTier.BOSS_VAULT
                else -> LootChestTier.HIGHLAND
            }
            val hcUltraBonus = if (battle.hardcoreOathActive) 18 else 0
            val wagerUltraBonus = if (battle.stakedSolCoins >= 25L) 15 else if (battle.stakedSolCoins > 0L) 8 else 0
            val ultraOdds = when (chestTier) {
                LootChestTier.SPIRE_SOVEREIGN -> (38 + battle.spireFloor * 2 + hcUltraBonus + wagerUltraBonus).coerceIn(38, 100)
                LootChestTier.BOSS_VAULT -> (26 + battle.realmNumber * 5 + hcUltraBonus + wagerUltraBonus).coerceIn(28, 95)
                else -> (10 + battle.realmNumber * 2 + hcUltraBonus + wagerUltraBonus).coerceIn(10, 65)
            }

            // Strict Chest Exclusivity Rule: Loot Chests drop ONLY on Boss victories (including Dungeon Final Bosses)
            val droppedChest: LootChest? = if (enemy.isBoss || battle.isDungeonBossFight) {
                LootChest(
                    userId = profile.userId,
                    chestTier = chestTier.name,
                    sourceLabel = if (battle.isDungeonBossFight) "${enemy.name} Dungeon Vault" else "${enemy.name} Boss Spoils",
                    heroLevelSnapshot = profile.level.coerceAtLeast(1L),
                    durationMinutes = 0L,
                    ultraRareChancePct = ultraOdds.toLong()
                )
            } else {
                null
            }

            val projectedLevel = if (profile.xp + xpEarned >= profile.xpToNextLevel) profile.level + 1L else null

            val progressionNote = when (battle.mode) {
                BattleMode.ENDLESS_SPIRE -> "Spire Floor ${battle.spireFloor} Cleared! Floor ${battle.spireFloor + 1} Unlocked!"
                BattleMode.DAILY_ELEMENTAL_RIFT -> "Daily ${enemy.elementalAffinity.label} Rift Conquered! Bonus ${enemy.elementalAffinity.runeName} & Essence claimed!"
                BattleMode.WEEKLY_BOSS_RAID -> "Weekly Raid Titan Slain! Massive Sol Coin & Mythic Spoils claimed!"
                BattleMode.REALM_CAMPAIGN -> {
                    val realm = ProgressionEngine.realmSpec(battle.realmNumber)
                    if (battle.stageNumber >= 5) {
                        if (battle.realmNumber < ProgressionEngine.MAX_REALM) {
                            val nextRealm = ProgressionEngine.realmSpec(battle.realmNumber + 1)
                            "Realm ${battle.realmNumber} (${realm.name}) Conquered! Realm ${nextRealm.realmNumber}: ${nextRealm.name} Unlocked!"
                        } else {
                            "Celestial Solaris Conquered! You are the Supreme Strider of all 10 Realms!"
                        }
                    } else {
                        "${realm.name} Stage ${battle.stageNumber}/5 Cleared!"
                    }
                }
            }

            val coinNote = " • +$solCoinsEarned Sol Coins" + if (wagerProfit > 0L) " (Wager Won!)" else ""
            val matNote = " • +$oreEarned Ore, +$herbsEarned Herbs, +$essenceEarned Essence"
            val runeNote = if (droppedRune != null) " • +1 ${droppedRune.runeName}!" else ""
            val outcome = BattleOutcome(
                victory = true,
                wasBossVictory = enemy.isBoss,
                mode = battle.mode,
                realmNumber = battle.realmNumber,
                stageNumber = battle.stageNumber,
                spireFloor = battle.spireFloor,
                xpEarned = xpEarned,
                sunshardsEarned = shardsEarned,
                solCoinsEarned = solCoinsEarned,
                oreEarned = oreEarned,
                herbsEarned = herbsEarned,
                essenceEarned = essenceEarned,
                droppedRune = droppedRune,
                bestiaryBossKey = enemy.bestiaryKey.takeIf { it.isNotBlank() },
                gearDurabilityLossPct = 4,
                droppedChest = droppedChest,
                leveledUpTo = projectedLevel,
                progressionNote = progressionNote,
                summaryLine = "Defeated ${enemy.name}! +$xpEarned XP • +$shardsEarned Shards$coinNote$matNote$runeNote"
            )
            return TurnResolution(
                nextBattle = battle.copy(
                    active = false,
                    guarding = false,
                    enemy = enemy.copy(currentHp = 0),
                    outcome = outcome,
                    combatLog = (battle.combatLog + outcome.summaryLine).takeLast(9),
                    lastAnimEvent = BattleAnimEvent(
                        sequenceId = seqId,
                        heroAction = heroAction,
                        heroDamageDealt = heroDamageDealt + enemyDotDamage,
                        heroWasCrit = dealtCrit,
                        heroLifestealHeal = lifestealHeal,
                        heroElixirHeal = elixirHeal,
                        perkCallouts = callouts
                    )
                ),
                updatedHp = profile.currentHp,
                updatedEnergy = profile.strideEnergy,
                consumedElixirId = consumedElixirId,
                dealtCrit = dealtCrit
            )
        }

        // 3. Check Boss Phase II Enrage Trigger (when Boss drops <= 50% HP for the first time)
        val phaseLogs = mutableListOf<String>()
        if (dotNames.isNotEmpty()) {
            phaseLogs.add("${enemy.name} suffers ${dotNames.joinToString(", ")}!")
        }
        if (enemy.isBoss && enemy.bossPhase == 1 && enemy.currentHp <= (enemy.maxHp / 2)) {
            val phaseShield = max(25, enemy.maxHp / 5)
            val enragedAtk = (enemy.attack * 1.25f).roundToInt()
            val doomTurns = if (battle.hardcoreOathActive || battle.mode == BattleMode.WEEKLY_BOSS_RAID) 5 else 0
            enemy = enemy.copy(
                bossPhase = 2,
                title = "${enemy.name} • PHASE II ENRAGED!",
                attack = enragedAtk,
                shield = enemy.shield + phaseShield,
                doomTurnsLeft = doomTurns,
                nextIntent = EnemyIntent(EnemyIntentType.SOLAR_CATACLYSM, expectedDamage = (enragedAtk * 1.35f).roundToInt())
            )
            val doomText = if (doomTurns > 0) " • DOOM COUNTER: $doomTurns TURNS!" else ""
            phaseLogs.add("BOSS PHASE II ENRAGE! ${enemy.name} gains +$phaseShield Shield & +25% ATK$doomText!")
        }

        // 4. Active Timing Telegraph Gate: if enemy will attack and hero isn't already Guarding,
        // open the live Dodge / Parry Ring window instead of immediately applying enemy damage!
        val enemyStunned = enemy.statuses.any { it.type == CombatStatusType.STUN }
        val intent = enemy.nextIntent
        val shouldOpenTelegraph = !enemyStunned &&
            intent.type != EnemyIntentType.AEGIS_WARD &&
            !battle.guarding &&
            !battle.pendingEnemyTelegraph

        if (shouldOpenTelegraph) {
            val telegraphBattle = battle.copy(
                enemy = enemy,
                pendingEnemyTelegraph = true,
                lastParryGrade = null,
                elixirCooldownTurns = nextCooldown,
                skillCooldowns = nextSkillCooldowns,
                telegraphHeroAction = heroAction,
                telegraphHeroDamage = heroDamageDealt + enemyDotDamage,
                telegraphDealtCrit = dealtCrit,
                telegraphLifesteal = lifestealHeal,
                telegraphElixirHeal = elixirHeal,
                telegraphCallouts = callouts,
                combatLog = (battle.combatLog + phaseLogs + "⚡ ${enemy.name} winds up ${intent.type.label} — TAP DODGE / PARRY!").takeLast(9),
                lastAnimEvent = BattleAnimEvent(
                    sequenceId = seqId,
                    heroAction = heroAction,
                    enemyAction = BattleAnimAction.NONE,
                    heroDamageDealt = heroDamageDealt + enemyDotDamage,
                    heroWasCrit = dealtCrit,
                    heroLifestealHeal = lifestealHeal,
                    heroElixirHeal = elixirHeal,
                    perkCallouts = callouts
                )
            )
            return TurnResolution(
                nextBattle = telegraphBattle,
                updatedHp = profile.currentHp,
                updatedEnergy = profile.strideEnergy,
                consumedElixirId = consumedElixirId,
                dealtCrit = dealtCrit
            )
        }

        return resolveEnemyTurnNow(
            battle = battle.copy(enemy = enemy),
            profile = profile,
            stats = stats,
            consumedElixirId = consumedElixirId,
            heroAction = heroAction,
            heroDamageDealt = heroDamageDealt + enemyDotDamage,
            dealtCrit = dealtCrit,
            lifestealHeal = lifestealHeal,
            elixirHeal = elixirHeal,
            callouts = callouts,
            phaseLogs = phaseLogs,
            nextCooldown = nextCooldown,
            nextSkillCooldowns = nextSkillCooldowns,
            parryGrade = null
        )
    }

    /**
     * Resolves an active Dodge / Parry telegraph window once the player taps the ring (or timer expires).
     */
    fun resolveActiveTelegraph(
        battle: BattleState,
        profile: HeroProfile,
        stats: DerivedHeroStats,
        parryGrade: ParryGrade
    ): TurnResolution {
        if (!battle.pendingEnemyTelegraph || battle.enemy == null) {
            return TurnResolution(battle, profile.currentHp, profile.strideEnergy)
        }
        val clearedBattle = battle.copy(
            pendingEnemyTelegraph = false,
            lastParryGrade = parryGrade
        )
        return resolveEnemyTurnNow(
            battle = clearedBattle,
            profile = profile,
            stats = stats,
            consumedElixirId = null,
            heroAction = BattleAnimAction.NONE,
            heroDamageDealt = 0,
            dealtCrit = parryGrade == ParryGrade.PERFECT,
            lifestealHeal = 0,
            elixirHeal = 0,
            callouts = listOf(parryGrade.label),
            phaseLogs = mutableListOf(),
            nextCooldown = battle.elixirCooldownTurns,
            nextSkillCooldowns = battle.skillCooldowns,
            parryGrade = parryGrade
        )
    }

    private fun resolveEnemyTurnNow(
        battle: BattleState,
        profile: HeroProfile,
        stats: DerivedHeroStats,
        consumedElixirId: String?,
        heroAction: BattleAnimAction,
        heroDamageDealt: Int,
        dealtCrit: Boolean,
        lifestealHeal: Int,
        elixirHeal: Int,
        callouts: List<String>,
        phaseLogs: MutableList<String>,
        nextCooldown: Int,
        nextSkillCooldowns: List<Int>,
        parryGrade: ParryGrade?
    ): TurnResolution {
        var enemy = battle.enemy ?: return TurnResolution(battle, profile.currentHp, profile.strideEnergy, consumedElixirId)
        val seqId = System.currentTimeMillis()
        val enemyStunned = enemy.statuses.any { it.type == CombatStatusType.STUN }
        val intent = enemy.nextIntent
        var nextHeroHp = profile.currentHp
        var nextHeroBarrier = battle.heroBarrier
        var nextCombo = battle.comboCount
        var nextPetBurst = battle.petBurstCharge
        var nextValor = battle.valor
        var counterSlashDmg = 0
        var tookDamage = false
        var guardedHit = false
        var enemyDealtDmg = 0
        var thornsDmg = 0
        var regenHeal = 0
        var enemyAnimAction = BattleAnimAction.ENEMY_ATTACK
        val turnCallouts = callouts.toMutableList()
        var heroStatuses = battle.heroStatuses

        if (enemyStunned) {
            enemyAnimAction = BattleAnimAction.NONE
            turnCallouts.add("ENEMY STUNNED!")
            phaseLogs.add("${enemy.name} is STUNNED and skips its attack turn!")
        } else if (intent.type == EnemyIntentType.AEGIS_WARD) {
            enemyAnimAction = BattleAnimAction.ENEMY_WARD
            enemy = enemy.copy(shield = enemy.shield + intent.shieldGain)
            phaseLogs.add("${enemy.name} casts ${intent.type.label} (+${intent.shieldGain} Shield).")
        } else {
            val frostbitten = enemy.statuses.any { it.type == CombatStatusType.FROSTBITE }
            val frostMult = if (frostbitten) 0.75f else 1.0f
            val elemVsHeroMult = enemy.elementalAffinity.damageMultiplierAgainst(stats.activeElement)

            val vanguardGuardBonus = if (profile.resolvedClass() == HeroClass.VANGUARD) 0.32f else 0.42f
            val guardMult = if (battle.guarding) vanguardGuardBonus else 1.0f
            val parryMult = when (parryGrade) {
                ParryGrade.PERFECT -> 0.15f
                ParryGrade.GLANCING -> 0.55f
                else -> 1.0f
            }
            val mutatorEnemyMult = if (battle.weeklyMutatorLabel == "Solar Flare") 1.15f else 1.0f
            val dmgRedMult = (100 - stats.damageReductionPercent).coerceIn(35, 100) / 100f

            val doomExecute = enemy.doomTurnsLeft == 1
            val rawIncoming = if (doomExecute) {
                turnCallouts.add("DOOM CATACLYSM!")
                stats.computedMaxHp
            } else {
                max(
                    if (parryGrade == ParryGrade.PERFECT) 1 else 4,
                    ((intent.expectedDamage * frostMult * elemVsHeroMult * mutatorEnemyMult - (stats.totalDefense / 3)) * guardMult * parryMult * dmgRedMult).roundToInt()
                )
            }

            // Apply Parry Combo, Pet Burst & Counter-Slash rewards
            when (parryGrade) {
                ParryGrade.PERFECT -> {
                    nextCombo = (nextCombo + 1).coerceAtMost(5)
                    nextPetBurst = (nextPetBurst + parryGrade.burstGain).coerceAtMost(100)
                    nextValor = (nextValor + 15).coerceAtMost(100)
                    val flareBonus = if (battle.weeklyMutatorLabel == "Solar Flare") 2.0f else 1.0f
                    counterSlashDmg = max(10, (stats.totalAttack * 0.65f * flareBonus).roundToInt())
                    val nextEhp = (enemy.currentHp - counterSlashDmg).coerceAtLeast(1)
                    enemy = enemy.copy(currentHp = nextEhp)
                    turnCallouts.add("COUNTER $counterSlashDmg!")
                    turnCallouts.add("${nextCombo}x COMBO!")
                }
                ParryGrade.GLANCING -> {
                    nextPetBurst = (nextPetBurst + parryGrade.burstGain).coerceAtMost(100)
                }
                ParryGrade.MISSED -> {
                    nextCombo = 0
                    nextPetBurst = (nextPetBurst + parryGrade.burstGain).coerceAtMost(100)
                }
                null -> {}
            }

            // Aegis Barrier absorbs incoming damage first
            val barrierAbsorbed = min(nextHeroBarrier, rawIncoming)
            nextHeroBarrier = (nextHeroBarrier - barrierAbsorbed).coerceAtLeast(0)
            enemyDealtDmg = (rawIncoming - barrierAbsorbed).coerceAtLeast(0)

            nextHeroHp = (nextHeroHp - enemyDealtDmg).coerceAtLeast(0L)
            tookDamage = enemyDealtDmg > 0
            guardedHit = battle.guarding || barrierAbsorbed > 0 || parryGrade == ParryGrade.PERFECT || parryGrade == ParryGrade.GLANCING

            // Hardcore / Cataclysm status infliction on unguarded heavy hits
            if (!battle.guarding && parryGrade != ParryGrade.PERFECT && (intent.type == EnemyIntentType.SOLAR_CATACLYSM || (battle.hardcoreOathActive && Random.nextInt(100) < 40))) {
                val inflicted = when (enemy.elementalAffinity) {
                    ElementalAffinity.EMBER, ElementalAffinity.SOLAR -> ActiveCombatStatus(CombatStatusType.BURN, 2, max(8, enemy.attack / 3))
                    ElementalAffinity.FROST -> ActiveCombatStatus(CombatStatusType.FROSTBITE, 2, 20)
                    ElementalAffinity.TERRA -> ActiveCombatStatus(CombatStatusType.SUNDER, 2, 25)
                    ElementalAffinity.VOID -> ActiveCombatStatus(CombatStatusType.POISON, 2, max(8, enemy.attack / 3))
                }
                heroStatuses = applyOrRefreshStatus(heroStatuses, inflicted)
                turnCallouts.add("Hero ${inflicted.type.label}!")
            }

            if (stats.thornsPercent > 0) {
                thornsDmg = max(3, (intent.expectedDamage * stats.thornsPercent) / 100)
                val nextEhp = (enemy.currentHp - thornsDmg).coerceAtLeast(1)
                enemy = enemy.copy(currentHp = nextEhp)
                turnCallouts.add("Thorns $thornsDmg")
            }

            val blockTag = buildString {
                when (parryGrade) {
                    ParryGrade.PERFECT -> append(" (PERFECT PARRY! Counter $counterSlashDmg DMG!)")
                    ParryGrade.GLANCING -> append(" (Glancing Dodge -45%)")
                    else -> {}
                }
                if (battle.guarding) append(" (Guarded!)")
                if (barrierAbsorbed > 0) append(" ($barrierAbsorbed absorbed by Aegis)")
                if (thornsDmg > 0) append(" • Thorns $thornsDmg")
            }
            phaseLogs.add("${enemy.name} uses ${intent.type.label} for $enemyDealtDmg DMG$blockTag.")
        }

        // 5. Tick Hero Turn Regen & Hero DoT Statuses
        var heroStatusNetHp = stats.turnRegenHp
        heroStatuses.forEach { st ->
            when (st.type) {
                CombatStatusType.REGEN -> heroStatusNetHp += st.potency
                CombatStatusType.BURN, CombatStatusType.POISON -> heroStatusNetHp -= st.potency
                else -> {}
            }
        }
        if (nextHeroHp > 0L && heroStatusNetHp != 0) {
            if (heroStatusNetHp > 0) {
                regenHeal = heroStatusNetHp
                nextHeroHp = (nextHeroHp + regenHeal).coerceAtMost(stats.computedMaxHp.toLong())
                turnCallouts.add("+$regenHeal Regen")
            } else {
                val dotLoss = (-heroStatusNetHp).toLong()
                nextHeroHp = (nextHeroHp - dotLoss).coerceAtLeast(1L)
                turnCallouts.add("-$dotLoss Burn/Poison")
            }
        }

        // Decrement status turn counters and Doom counter
        val tickedEnemyStatuses = tickStatuses(enemy.statuses)
        val tickedHeroStatuses = tickStatuses(heroStatuses)
        val nextDoom = if (enemy.doomTurnsLeft > 1) enemy.doomTurnsLeft - 1 else enemy.doomTurnsLeft

        val animEvent = BattleAnimEvent(
            sequenceId = seqId,
            heroAction = if (counterSlashDmg > 0) BattleAnimAction.HERO_STRIKE else heroAction,
            enemyAction = enemyAnimAction,
            heroDamageDealt = heroDamageDealt + counterSlashDmg,
            heroWasCrit = dealtCrit,
            enemyDamageDealt = enemyDealtDmg,
            enemyWasGuarded = guardedHit,
            heroLifestealHeal = lifestealHeal,
            heroElixirHeal = elixirHeal,
            heroTurnRegenHeal = regenHeal,
            thornsReflected = thornsDmg,
            perkCallouts = turnCallouts
        )

        // 6. Check Hero Defeat (Hardcore Penalties: 15% Sol Coin Toll + Wager Lost + Battle Wound + 14% Durability loss)
        if (nextHeroHp <= 0L) {
            val recoveredHp = (stats.computedMaxHp * 0.35f).toLong().coerceAtLeast(25L)
            val consolationXp = 15L
            val unbankedToll = (profile.solCoins * 0.15f).roundToInt().toLong()
            val totalCoinsLost = (battle.stakedSolCoins + unbankedToll).coerceAtMost(profile.solCoins)

            val outcome = BattleOutcome(
                victory = false,
                mode = battle.mode,
                realmNumber = battle.realmNumber,
                stageNumber = battle.stageNumber,
                spireFloor = battle.spireFloor,
                xpEarned = consolationXp,
                sunshardsEarned = 3L,
                solCoinsLost = totalCoinsLost,
                woundInflicted = true,
                gearDurabilityLossPct = 14,
                summaryLine = "DEFEATED by ${enemy.name}! Lost -$totalCoinsLost Sol Coins (15% Toll + Wager), suffered +1 Battle Wound (-10% Stats) & -14% Gear Durability. Cure wounds & repair gear at the Sanctuary!"
            )
            return TurnResolution(
                nextBattle = battle.copy(
                    active = false,
                    guarding = false,
                    pendingEnemyTelegraph = false,
                    enemy = enemy,
                    outcome = outcome,
                    combatLog = (battle.combatLog + phaseLogs + outcome.summaryLine).takeLast(9),
                    lastAnimEvent = animEvent
                ),
                updatedHp = recoveredHp,
                updatedEnergy = profile.strideEnergy,
                consumedElixirId = consumedElixirId,
                tookDamage = true,
                dealtCrit = dealtCrit,
                guardedHit = guardedHit
            )
        }

        val nextIntent = rollIntent(enemy.attack, enemy.isBoss || enemy.bossPhase >= 2)
        return TurnResolution(
            nextBattle = battle.copy(
                turnNumber = battle.turnNumber + 1,
                valor = nextValor,
                comboCount = nextCombo,
                petBurstCharge = nextPetBurst,
                heroBarrier = nextHeroBarrier,
                heroStatuses = tickedHeroStatuses,
                elixirCooldownTurns = nextCooldown,
                skillCooldowns = nextSkillCooldowns,
                guarding = false,
                pendingEnemyTelegraph = false,
                enemy = enemy.copy(
                    statuses = tickedEnemyStatuses,
                    doomTurnsLeft = nextDoom,
                    nextIntent = nextIntent
                ),
                combatLog = (battle.combatLog + phaseLogs).takeLast(9),
                lastAnimEvent = animEvent
            ),
            updatedHp = nextHeroHp,
            updatedEnergy = profile.strideEnergy,
            consumedElixirId = consumedElixirId,
            tookDamage = tookDamage,
            dealtCrit = dealtCrit,
            guardedHit = guardedHit
        )
    }

    private fun statusForElement(element: ElementalAffinity, heroAtk: Int): ActiveCombatStatus = when (element) {
        ElementalAffinity.SOLAR -> ActiveCombatStatus(CombatStatusType.SUNDER, 2, 35)
        ElementalAffinity.EMBER -> ActiveCombatStatus(CombatStatusType.BURN, 3, max(10, heroAtk / 3))
        ElementalAffinity.FROST -> ActiveCombatStatus(CombatStatusType.FROSTBITE, 2, 25)
        ElementalAffinity.TERRA -> ActiveCombatStatus(CombatStatusType.STUN, 1, 1)
        ElementalAffinity.VOID -> ActiveCombatStatus(CombatStatusType.POISON, 3, max(10, heroAtk / 3))
    }

    private fun applyOrRefreshStatus(
        existing: List<ActiveCombatStatus>,
        added: ActiveCombatStatus
    ): List<ActiveCombatStatus> {
        val without = existing.filterNot { it.type == added.type }
        return without + added
    }

    private fun tickStatuses(statuses: List<ActiveCombatStatus>): List<ActiveCombatStatus> {
        return statuses.mapNotNull { st ->
            val left = st.turnsRemaining - 1
            if (left > 0) st.copy(turnsRemaining = left) else null
        }
    }

    private fun rollIntent(baseAttack: Int, isBoss: Boolean): EnemyIntent {
        val roll = Random.nextFloat()
        return when {
            isBoss && roll > 0.62f -> EnemyIntent(
                type = EnemyIntentType.SOLAR_CATACLYSM,
                expectedDamage = (baseAttack * 1.45f).roundToInt()
            )
            roll > 0.52f -> EnemyIntent(
                type = EnemyIntentType.HEAVY_CRUSH,
                expectedDamage = (baseAttack * 1.25f).roundToInt()
            )
            roll < 0.22f -> EnemyIntent(
                type = EnemyIntentType.AEGIS_WARD,
                expectedDamage = 0,
                shieldGain = (baseAttack * 0.9f).roundToInt().coerceAtLeast(10)
            )
            else -> EnemyIntent(
                type = EnemyIntentType.QUICK_STRIKE,
                expectedDamage = baseAttack
            )
        }
    }
}
