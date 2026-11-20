package com.example.domain

import com.example.data.model.DerivedHeroStats
import com.example.data.model.HeroClass
import com.example.data.model.HeroProfile
import com.example.data.model.InventoryItem
import com.example.data.model.LootChest
import com.example.data.model.LootChestTier
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

enum class EnemyArtKey {
    OBSIDIAN_GOLEM,
    GILDED_WYVERN
}

enum class BattleMode(val label: String) {
    REALM_CAMPAIGN("Realm Campaign"),
    ENDLESS_SPIRE("Endless Spire")
}

enum class BattleAnimAction {
    NONE,
    HERO_STRIKE,
    HERO_SOLAR_SKILL,
    HERO_GUARD,
    HERO_ELIXIR,
    ENEMY_ATTACK,
    ENEMY_WARD
}

data class BattleAnimEvent(
    val sequenceId: Long,
    val heroAction: BattleAnimAction = BattleAnimAction.NONE,
    val heroDamageDealt: Int = 0,
    val heroWasCrit: Boolean = false,
    val heroArmorPierced: Boolean = false,
    val heroLifestealHeal: Int = 0,
    val heroTurnRegenHeal: Int = 0,
    val heroElixirHeal: Int = 0,
    val enemyAction: BattleAnimAction = BattleAnimAction.NONE,
    val enemyDamageDealt: Int = 0,
    val enemyWasGuarded: Boolean = false,
    val thornsReflected: Int = 0,
    val perkCallouts: List<String> = emptyList()
)

enum class EnemyIntentType(val label: String) {
    CLEAVE("Rending Cleave"),
    HEAVY_SLAM("Crushing Slam"),
    AEGIS_WARD("Obsidian Ward"),
    SOLAR_BREATH("Scorching Gale")
}

data class EnemyIntent(
    val type: EnemyIntentType,
    val expectedDamage: Int,
    val shieldGain: Int = 0
)

data class CombatEnemy(
    val name: String,
    val title: String,
    val level: Int,
    val maxHp: Int,
    val currentHp: Int,
    val shield: Int,
    val baseAttack: Int,
    val baseDefense: Int,
    val xpReward: Long,
    val shardReward: Long,
    val isBoss: Boolean,
    val artKey: EnemyArtKey,
    val nextIntent: EnemyIntent
)

data class BattleState(
    val active: Boolean = false,
    val mode: BattleMode = BattleMode.REALM_CAMPAIGN,
    val realmNumber: Int = 1,
    val stageNumber: Int = 1,
    val spireFloor: Int = 1,
    val enemy: CombatEnemy? = null,
    val turnNumber: Int = 1,
    val valor: Int = 0, // 0..100; at 100 next Solar Skill deals bonus Overdrive crit
    val playerGuarding: Boolean = false,
    val combatLog: List<String> = emptyList(),
    val lastAnimEvent: BattleAnimEvent? = null,
    val outcome: BattleOutcome? = null
)

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
    val leveledUpTo: Long? = null,
    val droppedChest: LootChest? = null,
    val progressionNote: String? = null,
    val summaryLine: String = ""
)

data class TurnResolution(
    val nextBattle: BattleState,
    val updatedHp: Long,
    val updatedEnergy: Long,
    val consumedElixirId: String? = null,
    val playerCrit: Boolean = false,
    val tookDamage: Boolean = false,
    val guardedHit: Boolean = false
)

object CombatEngine {

    private var animCounter: Long = 1L
    private fun nextAnimId(): Long {
        animCounter += 1L
        return animCounter
    }

    fun spawnEncounter(
        profile: HeroProfile,
        mode: BattleMode = BattleMode.REALM_CAMPAIGN,
        forceBoss: Boolean = false
    ): BattleState {
        val heroLvl = profile.level.toInt().coerceAtLeast(1)

        return if (mode == BattleMode.ENDLESS_SPIRE) {
            val floor = profile.currentSpireFloor.toInt().coerceAtLeast(1)
            val isSpireBoss = forceBoss || (floor % 5 == 0)
            val spireScale = 1.0f + ((floor - 1) * 0.14f)
            val enemyLvl = max(heroLvl, 1 + floor)
            val art = if (floor % 2 == 0) EnemyArtKey.GILDED_WYVERN else EnemyArtKey.OBSIDIAN_GOLEM
            val name = if (isSpireBoss) {
                if (art == EnemyArtKey.OBSIDIAN_GOLEM) "Spire Warden Colossus (F$floor)" else "Astral Spire Drake (F$floor)"
            } else {
                if (art == EnemyArtKey.OBSIDIAN_GOLEM) "Spire Sentinel (F$floor)" else "Zenith Skyhunter (F$floor)"
            }
            val title = if (isSpireBoss) "Endless Spire Boss" else "Spire Floor $floor Guardian"
            val bossHpBoost = if (isSpireBoss) 1.38f else 1.0f
            val bossAtkBoost = if (isSpireBoss) 1.24f else 1.0f

            val maxHp = ((95 + enemyLvl * 24) * spireScale * bossHpBoost).roundToInt()
            val atk = ((12 + enemyLvl * 3.8f) * (1f + (floor - 1) * 0.08f) * bossAtkBoost).roundToInt()
            val def = ((5 + enemyLvl * 1.9f) * (1f + (floor - 1) * 0.06f)).roundToInt()
            val xpReward = ((58 + floor * 18 + heroLvl * 16) * if (isSpireBoss) 1.75f else 1.0f).toLong()
            val shardReward = ((30 + floor * 10) * if (isSpireBoss) 1.85f else 1.0f).toLong()

            val enemy = CombatEnemy(
                name = name,
                title = title,
                level = enemyLvl,
                maxHp = maxHp,
                currentHp = maxHp,
                shield = if (isSpireBoss) 18 + floor * 3 else floor * 2,
                baseAttack = atk,
                baseDefense = def,
                xpReward = xpReward,
                shardReward = shardReward,
                isBoss = isSpireBoss,
                artKey = art,
                nextIntent = rollEnemyIntent(atk, def, 1)
            )
            val bossHint = if (isSpireBoss) " (Boss Victory awards +${ExpeditionEngine.BOSS_VICTORY_SOL_COINS} Sol Coins & Spire Sovereign Chest!)" else ""
            BattleState(
                active = true,
                mode = BattleMode.ENDLESS_SPIRE,
                realmNumber = profile.currentRealm.toInt().coerceIn(1, 5),
                stageNumber = profile.realmStage.toInt().coerceIn(1, 5),
                spireFloor = floor,
                enemy = enemy,
                turnNumber = 1,
                valor = 25,
                playerGuarding = false,
                combatLog = listOf("Endless Spire Floor $floor: ${enemy.name} bars the ascent!$bossHint"),
                lastAnimEvent = null,
                outcome = null
            )
        } else {
            val realmNum = profile.currentRealm.toInt().coerceIn(1, ProgressionEngine.MAX_REALM.toInt())
            val stageNum = if (forceBoss) 5 else profile.realmStage.toInt().coerceIn(1, 5)
            val realm = ProgressionEngine.realmSpec(realmNum)
            val isBoss = forceBoss || stageNum >= 5
            val stageScale = realm.statScale * (1.0f + ((stageNum - 1) * 0.07f))
            val enemyLvl = max(heroLvl, realm.recommendedLevel + (stageNum - 1))

            val art = if (isBoss) realm.guardianArt else {
                if (stageNum % 2 == 0) EnemyArtKey.GILDED_WYVERN else EnemyArtKey.OBSIDIAN_GOLEM
            }
            val name = if (isBoss) realm.guardianName else realm.minionNames[(stageNum - 1) % realm.minionNames.size]
            val title = if (isBoss) realm.guardianTitle else "${realm.name} • Stage $stageNum/5"

            val maxHp = ((88 + enemyLvl * 24) * stageScale * if (isBoss) 1.36f else 1.0f).roundToInt()
            val atk = ((11 + enemyLvl * 3.8f) * (1f + (realmNum - 1) * 0.14f) * if (isBoss) 1.24f else 1.0f).roundToInt()
            val def = ((4 + enemyLvl * 1.9f) * (1f + (realmNum - 1) * 0.10f) * if (isBoss) 1.12f else 1.0f).roundToInt()
            val xpReward = ((50 + enemyLvl * 22) * if (isBoss) 1.65f else 1.0f).toLong()
            val shardReward = ((26 + enemyLvl * 10) * if (isBoss) 1.75f else 1.0f).toLong()

            val enemy = CombatEnemy(
                name = name,
                title = title,
                level = enemyLvl,
                maxHp = maxHp,
                currentHp = maxHp,
                shield = if (isBoss) 14 + realmNum * 6 + enemyLvl else 0,
                baseAttack = atk,
                baseDefense = def,
                xpReward = xpReward,
                shardReward = shardReward,
                isBoss = isBoss,
                artKey = art,
                nextIntent = rollEnemyIntent(atk, def, 1)
            )
            val bossHint = if (isBoss) " (Victory awards +${ExpeditionEngine.BOSS_VICTORY_SOL_COINS} Sol Coins & Boss Vault Chest!)" else ""
            BattleState(
                active = true,
                mode = BattleMode.REALM_CAMPAIGN,
                realmNumber = realmNum,
                stageNumber = stageNum,
                spireFloor = profile.currentSpireFloor.toInt().coerceAtLeast(1),
                enemy = enemy,
                turnNumber = 1,
                valor = 25,
                playerGuarding = false,
                combatLog = listOf("Realm $realmNum-${stageNum}: ${enemy.name} (${enemy.title}) engages!$bossHint"),
                lastAnimEvent = null,
                outcome = null
            )
        }
    }

    fun executeStrike(
        battle: BattleState,
        profile: HeroProfile,
        stats: DerivedHeroStats
    ): TurnResolution {
        val enemy = battle.enemy ?: return TurnResolution(battle, profile.currentHp, profile.strideEnergy)
        val isCrit = Random.nextInt(100) < stats.critChancePercent
        val effectiveEnemyDef = ((enemy.baseDefense / 2f) * (1f - stats.armorPiercePercent / 100f)).roundToInt()
        val rawDamage = stats.totalAttack + Random.nextInt(-2, 5)
        val critScaled = if (isCrit) (rawDamage * 1.55f).roundToInt() else rawDamage
        val mitigated = max(6, critScaled - effectiveEnemyDef)

        val (enemyAfterHit, shieldBroken) = applyDamageToEnemy(enemy, mitigated)
        val lifestealHeal = if (stats.lifestealPercent > 0) {
            max(1, (mitigated * stats.lifestealPercent / 100f).roundToInt())
        } else 0
        val hpAfterLeech = (profile.currentHp + lifestealHeal).coerceAtMost(stats.computedMaxHp.toLong())
        val nextValor = (battle.valor + if (isCrit) 38 else 30).coerceAtMost(100)

        val callouts = mutableListOf<String>()
        if (isCrit) callouts.add("CRIT!")
        if (stats.armorPiercePercent > 0) callouts.add("PIERCE")
        if (lifestealHeal > 0) callouts.add("+$lifestealHeal LEECH")
        if (shieldBroken) callouts.add("SHIELD BREAK")

        val hitNote = buildString {
            append(if (isCrit) "CRITICAL Strike dealt $mitigated DMG" else "Strike dealt $mitigated DMG")
            if (lifestealHeal > 0) append(" (+$lifestealHeal HP Siphon)")
            if (shieldBroken) append(" [Guard Shattered]")
            append(" (+${if (isCrit) 38 else 30} Valor).")
        }

        val heroEvent = BattleAnimEvent(
            sequenceId = nextAnimId(),
            heroAction = BattleAnimAction.HERO_STRIKE,
            heroDamageDealt = mitigated,
            heroWasCrit = isCrit,
            heroArmorPierced = stats.armorPiercePercent > 0,
            heroLifestealHeal = lifestealHeal,
            perkCallouts = callouts
        )

        if (enemyAfterHit.currentHp <= 0) {
            return buildVictoryResolution(
                battle = battle.copy(valor = nextValor, lastAnimEvent = heroEvent),
                defeatedEnemy = enemyAfterHit,
                profile = profile.copy(currentHp = hpAfterLeech),
                stats = stats,
                finalStrikeLog = hitNote
            )
        }
        return resolveEnemyTurn(
            battle = battle.copy(enemy = enemyAfterHit, valor = nextValor, playerGuarding = false),
            profile = profile.copy(currentHp = hpAfterLeech),
            stats = stats,
            playerLog = hitNote,
            energyAfterAction = (profile.strideEnergy + 4L).coerceAtMost(stats.computedMaxEnergy.toLong()),
            playerCrit = isCrit,
            baseAnimEvent = heroEvent
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
            return TurnResolution(
                nextBattle = battle.copy(
                    combatLog = (battle.combatLog + "Need $energyCost Stride Energy for Solar Skill — walk or Guard to charge.").takeLast(4)
                ),
                updatedHp = profile.currentHp,
                updatedEnergy = profile.strideEnergy
            )
        }

        val isValorOverdrive = battle.valor >= 100
        val bonusCrit = !isValorOverdrive && (Random.nextInt(100) < stats.critChancePercent)
        val classBonus = if (profile.resolvedClass() == HeroClass.SUNBLADE) 1.18f else 1.0f
        val petBonus = stats.petSkillDamageMultiplier
        val surgeBonus = 1f + (stats.solarSurgeBonusPct / 100f)
        val baseMult = when {
            isValorOverdrive -> 2.30f
            bonusCrit -> 2.05f
            else -> 1.65f
        }
        val effectiveDef = ((enemy.baseDefense / 4f) * (1f - stats.armorPiercePercent / 100f)).roundToInt()
        val damage = max(14, (stats.totalAttack * baseMult * classBonus * petBonus * surgeBonus).roundToInt() - effectiveDef)

        val (enemyAfterHit, shieldBroken) = applyDamageToEnemy(enemy, damage)
        val lifestealHeal = if (stats.lifestealPercent > 0) {
            max(2, (damage * stats.lifestealPercent / 100f).roundToInt())
        } else 0
        val hpAfterLeech = (profile.currentHp + lifestealHeal).coerceAtMost(stats.computedMaxHp.toLong())
        val nextEnergy = (profile.strideEnergy - energyCost).coerceAtLeast(0L)
        val nextValor = if (isValorOverdrive) 0 else (battle.valor + 18).coerceAtMost(100)

        val callouts = mutableListOf<String>()
        if (isValorOverdrive) callouts.add("OVERDRIVE!") else if (bonusCrit) callouts.add("SKILL CRIT!")
        if (stats.solarSurgeBonusPct > 0) callouts.add("SOLAR SURGE")
        if (lifestealHeal > 0) callouts.add("+$lifestealHeal LEECH")
        if (shieldBroken) callouts.add("SHIELD BREAK")

        val skillText = buildString {
            append(if (isValorOverdrive) "SOLAR OVERDRIVE! Blazing beam pierced for $damage DMG" else "Solar Skill unleashed for $damage DMG (-20 Energy)")
            if (lifestealHeal > 0) append(" (+$lifestealHeal HP Siphon)")
            append(".")
        }

        val heroEvent = BattleAnimEvent(
            sequenceId = nextAnimId(),
            heroAction = BattleAnimAction.HERO_SOLAR_SKILL,
            heroDamageDealt = damage,
            heroWasCrit = isValorOverdrive || bonusCrit,
            heroArmorPierced = stats.armorPiercePercent > 0,
            heroLifestealHeal = lifestealHeal,
            perkCallouts = callouts
        )

        if (enemyAfterHit.currentHp <= 0) {
            return buildVictoryResolution(
                battle = battle.copy(valor = nextValor, lastAnimEvent = heroEvent),
                defeatedEnemy = enemyAfterHit,
                profile = profile.copy(currentHp = hpAfterLeech, strideEnergy = nextEnergy),
                stats = stats,
                finalStrikeLog = skillText
            )
        }
        return resolveEnemyTurn(
            battle = battle.copy(enemy = enemyAfterHit, valor = nextValor, playerGuarding = false),
            profile = profile.copy(currentHp = hpAfterLeech),
            stats = stats,
            playerLog = skillText,
            energyAfterAction = nextEnergy,
            playerCrit = true,
            baseAnimEvent = heroEvent
        )
    }

    fun executeGuard(
        battle: BattleState,
        profile: HeroProfile,
        stats: DerivedHeroStats
    ): TurnResolution {
        val enemy = battle.enemy ?: return TurnResolution(battle, profile.currentHp, profile.strideEnergy)
        val recoveredEnergy = (profile.strideEnergy + 18L).coerceAtMost(stats.computedMaxEnergy.toLong())
        val counterDamage = if (profile.resolvedClass() == HeroClass.VANGUARD) max(6, stats.totalDefense / 2) else 0
        val (enemyAfterCounter, _) = if (counterDamage > 0) applyDamageToEnemy(enemy, counterDamage) else (enemy to false)
        val note = if (counterDamage > 0) {
            "Raised Aegis Guard (-60% DMG, +18 Energy, $counterDamage Vanguard counter DMG)."
        } else {
            "Raised Aegis Guard (-60% incoming DMG, +18 Stride Energy)."
        }

        val heroEvent = BattleAnimEvent(
            sequenceId = nextAnimId(),
            heroAction = BattleAnimAction.HERO_GUARD,
            heroDamageDealt = counterDamage,
            perkCallouts = if (counterDamage > 0) listOf("AEGIS GUARD", "COUNTER $counterDamage") else listOf("AEGIS GUARD")
        )

        if (enemyAfterCounter.currentHp <= 0) {
            return buildVictoryResolution(
                battle = battle.copy(lastAnimEvent = heroEvent),
                defeatedEnemy = enemyAfterCounter,
                profile = profile.copy(strideEnergy = recoveredEnergy),
                stats = stats,
                finalStrikeLog = note
            )
        }
        return resolveEnemyTurn(
            battle = battle.copy(
                enemy = enemyAfterCounter,
                valor = (battle.valor + 20).coerceAtMost(100),
                playerGuarding = true
            ),
            profile = profile,
            stats = stats,
            playerLog = note,
            energyAfterAction = recoveredEnergy,
            playerCrit = false,
            baseAnimEvent = heroEvent
        )
    }

    fun executeElixir(
        battle: BattleState,
        profile: HeroProfile,
        stats: DerivedHeroStats,
        elixir: InventoryItem
    ): TurnResolution {
        val heal = elixir.healAmount.coerceAtLeast(35L)
        val healedHp = (profile.currentHp + heal).coerceAtMost(stats.computedMaxHp.toLong())
        val actualHeal = (healedHp - profile.currentHp).toInt()
        val logText = "Drank ${elixir.name} (+$actualHeal HP)."

        val heroEvent = BattleAnimEvent(
            sequenceId = nextAnimId(),
            heroAction = BattleAnimAction.HERO_ELIXIR,
            heroElixirHeal = actualHeal,
            perkCallouts = listOf("+$actualHeal HP ELIXIR")
        )
        return resolveEnemyTurn(
            battle = battle.copy(playerGuarding = false),
            profile = profile.copy(currentHp = healedHp),
            stats = stats,
            playerLog = logText,
            energyAfterAction = profile.strideEnergy,
            playerCrit = false,
            baseAnimEvent = heroEvent,
            consumedElixirId = elixir.id
        )
    }

    fun executeRetreat(battle: BattleState, profile: HeroProfile): TurnResolution {
        val outcome = BattleOutcome(
            victory = false,
            retreated = true,
            mode = battle.mode,
            realmNumber = battle.realmNumber,
            stageNumber = battle.stageNumber,
            spireFloor = battle.spireFloor,
            solCoinsEarned = 0L,
            summaryLine = "Tactical retreat completed. Your banked steps and gear remain safe."
        )
        return TurnResolution(
            nextBattle = battle.copy(
                active = false,
                outcome = outcome,
                combatLog = (battle.combatLog + outcome.summaryLine).takeLast(4)
            ),
            updatedHp = profile.currentHp,
            updatedEnergy = profile.strideEnergy
        )
    }

    private fun resolveEnemyTurn(
        battle: BattleState,
        profile: HeroProfile,
        stats: DerivedHeroStats,
        playerLog: String,
        energyAfterAction: Long,
        playerCrit: Boolean,
        baseAnimEvent: BattleAnimEvent,
        consumedElixirId: String? = null
    ): TurnResolution {
        val enemy = battle.enemy ?: return TurnResolution(battle, profile.currentHp, energyAfterAction)
        val intent = enemy.nextIntent
        val nextTurn = battle.turnNumber + 1

        var updatedEnemy = enemy
        var updatedHp = profile.currentHp
        var tookDamage = false
        var enemyAction = BattleAnimAction.ENEMY_ATTACK
        var enemyDamageDealt = 0
        var thornsReflected = 0
        val extraCallouts = baseAnimEvent.perkCallouts.toMutableList()
        val enemyLog: String

        if (intent.type == EnemyIntentType.AEGIS_WARD) {
            enemyAction = BattleAnimAction.ENEMY_WARD
            updatedEnemy = enemy.copy(shield = enemy.shield + intent.shieldGain)
            enemyLog = "${enemy.name} used ${intent.type.label} (+${intent.shieldGain} Shield)."
        } else {
            val reducedByDef = max(4, intent.expectedDamage - (stats.totalDefense / 2))
            // Apply Armor Damage Reduction perk!
            val afterArmorPerk = max(2, (reducedByDef * (1f - stats.damageReductionPercent / 100f)).roundToInt())
            val finalDmg = if (battle.playerGuarding) max(2, (afterArmorPerk * 0.4f).roundToInt()) else afterArmorPerk
            enemyDamageDealt = finalDmg
            updatedHp = (profile.currentHp - finalDmg).coerceAtLeast(0L)
            tookDamage = finalDmg > 0

            // Apply Armor Thorns reflect perk!
            if (stats.thornsPercent > 0 && intent.expectedDamage > 0) {
                thornsReflected = max(3, (intent.expectedDamage * stats.thornsPercent / 100f).roundToInt())
                val (afterThorns, _) = applyDamageToEnemy(updatedEnemy, thornsReflected)
                updatedEnemy = afterThorns
                extraCallouts.add("THORNS $thornsReflected")
            }

            enemyLog = buildString {
                if (battle.playerGuarding) {
                    append("Blocked ${enemy.name}'s ${intent.type.label} — took $finalDmg DMG")
                } else {
                    append("${enemy.name} used ${intent.type.label} for $finalDmg DMG")
                }
                if (thornsReflected > 0) append(" (Thorns reflected $thornsReflected DMG!)")
            }
        }

        // Apply Armor / Skill Tree Dawn Mending turn regeneration if hero survived
        var regenApplied = 0
        if (updatedHp > 0L && stats.turnRegenHp > 0) {
            val afterRegen = (updatedHp + stats.turnRegenHp).coerceAtMost(stats.computedMaxHp.toLong())
            regenApplied = (afterRegen - updatedHp).toInt()
            updatedHp = afterRegen
            if (regenApplied > 0) extraCallouts.add("+$regenApplied REGEN")
        }

        val fullAnimEvent = baseAnimEvent.copy(
            enemyAction = enemyAction,
            enemyDamageDealt = enemyDamageDealt,
            enemyWasGuarded = battle.playerGuarding,
            thornsReflected = thornsReflected,
            heroTurnRegenHeal = regenApplied,
            perkCallouts = extraCallouts.distinct().take(4)
        )

        // Check if Thorns finished the enemy while the hero still stands!
        if (updatedHp > 0L && updatedEnemy.currentHp <= 0) {
            return buildVictoryResolution(
                battle = battle.copy(lastAnimEvent = fullAnimEvent),
                defeatedEnemy = updatedEnemy,
                profile = profile.copy(currentHp = updatedHp, strideEnergy = energyAfterAction),
                stats = stats,
                finalStrikeLog = "$playerLog $enemyLog"
            )
        }

        val logs = (battle.combatLog + playerLog + enemyLog +
            if (regenApplied > 0) "Dawn Mending restored +$regenApplied HP." else null)
            .filterNotNull()
            .takeLast(4)

        if (updatedHp <= 0L) {
            val defeatOutcome = BattleOutcome(
                victory = false,
                retreated = false,
                wasBossVictory = false,
                mode = battle.mode,
                realmNumber = battle.realmNumber,
                stageNumber = battle.stageNumber,
                spireFloor = battle.spireFloor,
                xpEarned = 18L,
                sunshardsEarned = 12L,
                solCoinsEarned = 0L,
                summaryLine = "Overwhelmed in battle — recovered at Sunlit Camp with 18 XP & 12 Sunshards."
            )
            return TurnResolution(
                nextBattle = battle.copy(
                    active = false,
                    enemy = updatedEnemy,
                    combatLog = logs,
                    lastAnimEvent = fullAnimEvent,
                    outcome = defeatOutcome
                ),
                updatedHp = max(30L, (stats.computedMaxHp * 0.5f).toLong()),
                updatedEnergy = energyAfterAction,
                consumedElixirId = consumedElixirId,
                playerCrit = playerCrit,
                tookDamage = true,
                guardedHit = battle.playerGuarding
            )
        }

        val refreshedEnemy = updatedEnemy.copy(
            nextIntent = rollEnemyIntent(updatedEnemy.baseAttack, updatedEnemy.baseDefense, nextTurn)
        )
        return TurnResolution(
            nextBattle = battle.copy(
                enemy = refreshedEnemy,
                turnNumber = nextTurn,
                playerGuarding = false,
                combatLog = logs,
                lastAnimEvent = fullAnimEvent
            ),
            updatedHp = updatedHp,
            updatedEnergy = energyAfterAction,
            consumedElixirId = consumedElixirId,
            playerCrit = playerCrit,
            tookDamage = tookDamage,
            guardedHit = battle.playerGuarding
        )
    }

    private fun buildVictoryResolution(
        battle: BattleState,
        defeatedEnemy: CombatEnemy,
        profile: HeroProfile,
        stats: DerivedHeroStats,
        finalStrikeLog: String
    ): TurnResolution {
        val droppedChest = maybeDropLootChest(
            userId = profile.userId,
            level = profile.level.toInt(),
            mode = battle.mode,
            isBoss = defeatedEnemy.isBoss,
            enemyName = defeatedEnemy.name,
            spireFloor = battle.spireFloor
        )
        val xpEarned = (defeatedEnemy.xpReward * stats.totalXpMultiplier).roundToInt().toLong()
            .coerceAtLeast(defeatedEnemy.xpReward)
        val shardEarned = (defeatedEnemy.shardReward * stats.totalSunshardMultiplier).roundToInt().toLong()
            .coerceAtLeast(defeatedEnemy.shardReward)

        // Strict rule: +10 Sol Coins awarded ONLY for won Boss Battles.
        val solCoinsEarned = if (defeatedEnemy.isBoss) ExpeditionEngine.BOSS_VICTORY_SOL_COINS else 0L
        val postBattleHeal = if (stats.petPostBattleHealPercent > 0f) {
            (stats.computedMaxHp * stats.petPostBattleHealPercent).roundToInt().toLong()
        } else 0L
        val finalHp = (profile.currentHp + postBattleHeal).coerceAtMost(stats.computedMaxHp.toLong())

        val totalXp = profile.xp + xpEarned
        val leveledTo = if (totalXp >= profile.xpToNextLevel) profile.level + 1L else null

        val progressionNote = when (battle.mode) {
            BattleMode.ENDLESS_SPIRE -> "Spire Floor ${battle.spireFloor} cleared! Floor ${battle.spireFloor + 1} unlocked."
            BattleMode.REALM_CAMPAIGN -> {
                if (battle.stageNumber >= 5) {
                    if (battle.realmNumber < ProgressionEngine.MAX_REALM.toInt()) {
                        "Realm ${battle.realmNumber} Conquered! Realm ${battle.realmNumber + 1} (${ProgressionEngine.realmSpec(battle.realmNumber + 1).name}) unlocked!"
                    } else {
                        "Zenith Citadel Cleared! Challenge Endless Spire or trigger Prestige Rebirth!"
                    }
                } else {
                    "Realm ${battle.realmNumber} Stage ${battle.stageNumber} cleared -> Stage ${battle.stageNumber + 1}/5."
                }
            }
        }

        val summary = buildString {
            append("Defeated ${defeatedEnemy.name}! +$xpEarned XP, +$shardEarned Sunshards")
            if (solCoinsEarned > 0L) append(", +$solCoinsEarned Sol Coins")
            if (postBattleHeal > 0L) append(" (Pet healed +$postBattleHeal HP)")
            if (droppedChest != null) append(" & sealed ${droppedChest.resolvedTier().title} dropped!")
        }

        val outcome = BattleOutcome(
            victory = true,
            wasBossVictory = defeatedEnemy.isBoss,
            mode = battle.mode,
            realmNumber = battle.realmNumber,
            stageNumber = battle.stageNumber,
            spireFloor = battle.spireFloor,
            xpEarned = xpEarned,
            sunshardsEarned = shardEarned,
            solCoinsEarned = solCoinsEarned,
            leveledUpTo = leveledTo,
            droppedChest = droppedChest,
            progressionNote = progressionNote,
            summaryLine = summary
        )
        return TurnResolution(
            nextBattle = battle.copy(
                active = false,
                enemy = defeatedEnemy.copy(currentHp = 0),
                combatLog = (battle.combatLog + finalStrikeLog + summary).takeLast(4),
                outcome = outcome
            ),
            updatedHp = finalHp,
            updatedEnergy = profile.strideEnergy,
            playerCrit = true
        )
    }

    private fun applyDamageToEnemy(enemy: CombatEnemy, damage: Int): Pair<CombatEnemy, Boolean> {
        return if (enemy.shield > 0) {
            val absorbed = minOf(enemy.shield, damage)
            val remaining = damage - absorbed
            val broken = absorbed == enemy.shield
            enemy.copy(
                shield = enemy.shield - absorbed,
                currentHp = (enemy.currentHp - remaining).coerceAtLeast(0)
            ) to broken
        } else {
            enemy.copy(currentHp = (enemy.currentHp - damage).coerceAtLeast(0)) to false
        }
    }

    private fun rollEnemyIntent(baseAttack: Int, baseDefense: Int, turn: Int): EnemyIntent {
        val cycle = (turn + Random.nextInt(3)) % 4
        return when (cycle) {
            0 -> EnemyIntent(EnemyIntentType.CLEAVE, baseAttack)
            1 -> EnemyIntent(EnemyIntentType.HEAVY_SLAM, (baseAttack * 1.45f).toInt())
            2 -> EnemyIntent(EnemyIntentType.AEGIS_WARD, 0, shieldGain = 10 + baseDefense)
            else -> EnemyIntent(EnemyIntentType.SOLAR_BREATH, (baseAttack * 1.25f).toInt())
        }
    }

    private fun maybeDropLootChest(
        userId: String,
        level: Int,
        mode: BattleMode,
        isBoss: Boolean,
        enemyName: String,
        spireFloor: Int
    ): LootChest? {
        if (isBoss) {
            val tier = if (mode == BattleMode.ENDLESS_SPIRE) LootChestTier.SPIRE_SOVEREIGN else LootChestTier.BOSS_VAULT
            val ultraPct = if (tier == LootChestTier.SPIRE_SOVEREIGN) {
                (36 + (spireFloor / 5) * 4).coerceAtMost(85)
            } else {
                tier.baseUltraRarePct
            }
            return LootChest(
                userId = userId,
                chestTier = tier.name,
                sourceLabel = "$enemyName Hoard".take(78),
                heroLevelSnapshot = level.coerceAtLeast(1).toLong(),
                durationMinutes = 0L,
                ultraRareChancePct = ultraPct.toLong()
            )
        }
        val dropChance = if (mode == BattleMode.ENDLESS_SPIRE) 0.78f else 0.72f
        if (Random.nextFloat() > dropChance) return null
        val tier = if (mode == BattleMode.ENDLESS_SPIRE && spireFloor >= 5) {
            LootChestTier.BOSS_VAULT
        } else {
            LootChestTier.HIGHLAND
        }
        return LootChest(
            userId = userId,
            chestTier = tier.name,
            sourceLabel = "$enemyName Spoils".take(78),
            heroLevelSnapshot = level.coerceAtLeast(1).toLong(),
            durationMinutes = 0L,
            ultraRareChancePct = tier.baseUltraRarePct.toLong()
        )
    }
}
