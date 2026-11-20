package com.example.domain

import com.example.data.model.HeroProfile
import com.example.data.model.LootChest
import com.example.data.model.LootChestTier
import com.example.data.model.currentCalendarDateKey

enum class SkillBranch(val title: String, val subtitle: String) {
    SOLAR_OFFENSE("Solar Offense", "Crit, Armor Pierce & Sun Siphon"),
    IRON_BASTION("Iron Bastion", "Damage Reduction, Thorns & Mending"),
    WAYFARER_MASTERY("Wayfarer Mastery", "Expedition Survival, Energy & Relic Yield")
}

data class SkillNodeSpec(
    val index: Int,
    val id: String,
    val branch: SkillBranch,
    val name: String,
    val maxRank: Int = 5,
    val perRankSummary: String
)

data class RealmChapterSpec(
    val realmNumber: Int,
    val name: String,
    val biomeSubtitle: String,
    val recommendedLevel: Int,
    val guardianName: String,
    val guardianTitle: String,
    val guardianArt: EnemyArtKey,
    val minionNames: List<String>,
    val statScale: Float
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
    val chestRewardTier: LootChestTier?,
    val chestUltraRarePct: Int = 0
)

data class QuestStatus(
    val spec: QuestSpec,
    val currentProgress: Long,
    val claimKey: String,
    val isClaimed: Boolean
) {
    val isCompleted: Boolean get() = currentProgress >= spec.targetCount
    val canClaim: Boolean get() = isCompleted && !isClaimed
    val progressFraction: Float get() = (currentProgress.toFloat() / spec.targetCount.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
}

object ProgressionEngine {

    const val PRESTIGE_MIN_LEVEL = 15L
    const val PRESTIGE_ALT_REALM_CLEARED = 3L
    const val MAX_PRESTIGE_LEVEL = 99L
    const val STAGES_PER_REALM = 5L
    const val MAX_REALM = 5L

    val realms: List<RealmChapterSpec> = listOf(
        RealmChapterSpec(
            realmNumber = 1,
            name = "Sunlit Dunes",
            biomeSubtitle = "Scorched canyon passes & ancient obelisks",
            recommendedLevel = 1,
            guardianName = "Sunshard Colossus",
            guardianTitle = "Realm I Guardian",
            guardianArt = EnemyArtKey.OBSIDIAN_GOLEM,
            minionNames = listOf("Obsidian Sentinel", "Dune Stalker Wyvern", "Sandstone Warden", "Ruin Marauder"),
            statScale = 1.00f
        ),
        RealmChapterSpec(
            realmNumber = 2,
            name = "Obsidian Rift",
            biomeSubtitle = "Volcanic glass chasms & basalt shrines",
            recommendedLevel = 4,
            guardianName = "Crimson Zenith Drake",
            guardianTitle = "Realm II Guardian",
            guardianArt = EnemyArtKey.GILDED_WYVERN,
            minionNames = listOf("Basalt Shieldbearer", "Riftwing Wyvern", "Cinder Construct", "Obsidian Reaver"),
            statScale = 1.24f
        ),
        RealmChapterSpec(
            realmNumber = 3,
            name = "Sunken Astrolabe",
            biomeSubtitle = "Star-carved temple vaults beneath the salt flats",
            recommendedLevel = 8,
            guardianName = "Chrono-Golem Aethel",
            guardianTitle = "Realm III Guardian",
            guardianArt = EnemyArtKey.OBSIDIAN_GOLEM,
            minionNames = listOf("Astrolabe Keeper", "Gilded Vault Drake", "Sundial Automaton", "Starforged Sentinel"),
            statScale = 1.52f
        ),
        RealmChapterSpec(
            realmNumber = 4,
            name = "Cinder Highlands",
            biomeSubtitle = "Ash-crowned plateaus bathed in eternal noon",
            recommendedLevel = 12,
            guardianName = "Embercrest Sovereign",
            guardianTitle = "Realm IV Guardian",
            guardianArt = EnemyArtKey.GILDED_WYVERN,
            minionNames = listOf("Ashen Titan", "Solarflame Wyvern", "Pyre Guardian", "Highland Warlord"),
            statScale = 1.85f
        ),
        RealmChapterSpec(
            realmNumber = 5,
            name = "Zenith Citadel",
            biomeSubtitle = "Sky-piercing sanctum of the Eternal Stride",
            recommendedLevel = 16,
            guardianName = "Astral Eclipse Warden",
            guardianTitle = "Realm V Supreme Guardian",
            guardianArt = EnemyArtKey.OBSIDIAN_GOLEM,
            minionNames = listOf("Zenith Praetorian", "Eclipse Skywyrm", "Celestial Golem", "Aurelian Executioner"),
            statScale = 2.25f
        )
    )

    val skillNodes: List<SkillNodeSpec> = listOf(
        SkillNodeSpec(0, "sunfire_edge", SkillBranch.SOLAR_OFFENSE, "Sunfire Edge", 5, "+4% ATK & +2% Crit per rank"),
        SkillNodeSpec(1, "piercing_ray", SkillBranch.SOLAR_OFFENSE, "Piercing Ray", 5, "+5% Armor Pierce & +4% Skill DMG"),
        SkillNodeSpec(2, "blood_solstice", SkillBranch.SOLAR_OFFENSE, "Blood Solstice", 5, "+3% Lifesteal per rank"),
        SkillNodeSpec(3, "obsidian_shell", SkillBranch.IRON_BASTION, "Obsidian Shell", 5, "+5% DEF & -3% Enemy DMG per rank"),
        SkillNodeSpec(4, "solar_thorns", SkillBranch.IRON_BASTION, "Solar Thorns", 5, "+4% Thorns & +15 Max HP per rank"),
        SkillNodeSpec(5, "dawn_recovery", SkillBranch.IRON_BASTION, "Dawn Recovery", 5, "+4 HP Regen each battle turn"),
        SkillNodeSpec(6, "pathfinder_ward", SkillBranch.WAYFARER_MASTERY, "Pathfinder Ward", 5, "+4% Expedition Survival per rank"),
        SkillNodeSpec(7, "stride_conduit", SkillBranch.WAYFARER_MASTERY, "Stride Conduit", 5, "+8% Step Energy & +6 Max Energy"),
        SkillNodeSpec(8, "relic_hunter", SkillBranch.WAYFARER_MASTERY, "Relic Hunter", 5, "+6% XP & +5% Sunshards per rank")
    )

    val quests: List<QuestSpec> = listOf(
        QuestSpec(
            id = "daily_battles_3",
            category = QuestCategory.DAILY,
            title = "Highland Skirmisher",
            description = "Win 3 battles today in Realm Campaign or Endless Spire.",
            targetCount = 3L,
            sunshardReward = 110L,
            xpReward = 90L,
            chestRewardTier = LootChestTier.HIGHLAND,
            chestUltraRarePct = 14
        ),
        QuestSpec(
            id = "daily_expedition_1",
            category = QuestCategory.DAILY,
            title = "Sunlit Scout",
            description = "Successfully complete 1 Timed Expedition today.",
            targetCount = 1L,
            sunshardReward = 90L,
            xpReward = 140L,
            chestRewardTier = LootChestTier.EXPEDITION,
            chestUltraRarePct = 28
        ),
        QuestSpec(
            id = "daily_chests_2",
            category = QuestCategory.DAILY,
            title = "Relic Breaker",
            description = "Open 2 Loot Chests today from battles or your Vault.",
            targetCount = 2L,
            sunshardReward = 120L,
            xpReward = 110L,
            chestRewardTier = null
        ),
        QuestSpec(
            id = "weekly_steps_5000",
            category = QuestCategory.WEEKLY,
            title = "Weekly Strider (5k)",
            description = "Record 5,000 watch or fitness tracker steps this week.",
            targetCount = 5_000L,
            sunshardReward = 240L,
            xpReward = 220L,
            chestRewardTier = LootChestTier.BOSS_VAULT,
            chestUltraRarePct = 28
        ),
        QuestSpec(
            id = "weekly_steps_15000",
            category = QuestCategory.WEEKLY,
            title = "Zenith Marathon (15k)",
            description = "Record 15,000 watch or fitness tracker steps this week.",
            targetCount = 15_000L,
            sunshardReward = 450L,
            xpReward = 420L,
            chestRewardTier = LootChestTier.SPIRE_SOVEREIGN,
            chestUltraRarePct = 40
        ),
        QuestSpec(
            id = "mile_realm_1",
            category = QuestCategory.MILESTONE,
            title = "Dunes Conqueror",
            description = "Defeat the Realm I Guardian (Sunshard Colossus).",
            targetCount = 1L,
            sunshardReward = 180L,
            xpReward = 200L,
            chestRewardTier = LootChestTier.BOSS_VAULT,
            chestUltraRarePct = 30
        ),
        QuestSpec(
            id = "mile_realm_3",
            category = QuestCategory.MILESTONE,
            title = "Astrolabe Sovereign",
            description = "Conquer Realm III (Sunken Astrolabe).",
            targetCount = 3L,
            sunshardReward = 350L,
            xpReward = 450L,
            chestRewardTier = LootChestTier.SPIRE_SOVEREIGN,
            chestUltraRarePct = 42
        ),
        QuestSpec(
            id = "mile_spire_5",
            category = QuestCategory.MILESTONE,
            title = "Spire Climber V",
            description = "Clear Floor 5 of the Endless Spire Gauntlet.",
            targetCount = 5L,
            sunshardReward = 220L,
            xpReward = 250L,
            chestRewardTier = LootChestTier.BOSS_VAULT,
            chestUltraRarePct = 30
        ),
        QuestSpec(
            id = "mile_spire_15",
            category = QuestCategory.MILESTONE,
            title = "Spire Ascendant XV",
            description = "Clear Floor 15 of the Endless Spire Gauntlet.",
            targetCount = 15L,
            sunshardReward = 500L,
            xpReward = 550L,
            chestRewardTier = LootChestTier.SPIRE_SOVEREIGN,
            chestUltraRarePct = 48
        ),
        QuestSpec(
            id = "mile_chests_10",
            category = QuestCategory.MILESTONE,
            title = "Master Vaultbreaker",
            description = "Open 10 total Treasure Chests.",
            targetCount = 10L,
            sunshardReward = 300L,
            xpReward = 300L,
            chestRewardTier = LootChestTier.SPIRE_SOVEREIGN,
            chestUltraRarePct = 45
        ),
        QuestSpec(
            id = "mile_prestige_1",
            category = QuestCategory.MILESTONE,
            title = "Solar Ascension",
            description = "Trigger your first Prestige Rebirth (Rank I).",
            targetCount = 1L,
            sunshardReward = 500L,
            xpReward = 300L,
            chestRewardTier = LootChestTier.SPIRE_SOVEREIGN,
            chestUltraRarePct = 55
        )
    )

    fun realmSpec(realmNumber: Int): RealmChapterSpec =
        realms.firstOrNull { it.realmNumber == realmNumber.coerceIn(1, realms.size) } ?: realms.first()

    fun serializeSkillRanks(ranks: List<Int>): String =
        (0 until 9).joinToString(",") { idx -> (ranks.getOrElse(idx) { 0 }).coerceIn(0, 5).toString() }

    fun canPrestige(profile: HeroProfile): Boolean =
        profile.prestigeLevel < MAX_PRESTIGE_LEVEL &&
            (profile.level >= PRESTIGE_MIN_LEVEL || profile.highestRealmCleared >= PRESTIGE_ALT_REALM_CLEARED)

    fun prestigeRomanNumeral(rank: Int): String = when (rank) {
        0 -> ""
        1 -> "I"
        2 -> "II"
        3 -> "III"
        4 -> "IV"
        5 -> "V"
        6 -> "VI"
        7 -> "VII"
        8 -> "VIII"
        9 -> "IX"
        10 -> "X"
        else -> "P$rank"
    }

    fun evaluateQuests(profile: HeroProfile, todayKey: String = currentCalendarDateKey()): List<QuestStatus> {
        val isSameDay = profile.dailyQuestDateKey == todayKey
        val dailyBattles = if (isSameDay) profile.dailyBattlesWon else 0L
        val dailyExps = if (isSameDay) profile.dailyExpeditionsDone else 0L
        val dailyChests = if (isSameDay) profile.dailyChestsOpened else 0L
        val claimedSet = profile.claimedQuestIds.toSet()

        return quests.map { spec ->
            val claimKey = when (spec.category) {
                QuestCategory.DAILY -> "${spec.id}_$todayKey"
                QuestCategory.WEEKLY -> "${spec.id}_${profile.weekKey}"
                QuestCategory.MILESTONE -> spec.id
            }
            val progress = when (spec.id) {
                "daily_battles_3" -> dailyBattles
                "daily_expedition_1" -> dailyExps
                "daily_chests_2" -> dailyChests
                "weekly_steps_5000", "weekly_steps_15000" -> profile.weeklySteps
                "mile_realm_1", "mile_realm_3" -> profile.highestRealmCleared
                "mile_spire_5", "mile_spire_15" -> profile.highestSpireFloor
                "mile_chests_10" -> profile.chestsOpenedTotal
                "mile_prestige_1" -> profile.prestigeLevel
                else -> 0L
            }
            QuestStatus(
                spec = spec,
                currentProgress = progress.coerceAtLeast(0L),
                claimKey = claimKey,
                isClaimed = claimedSet.contains(claimKey)
            )
        }
    }

    /**
     * Prunes stale daily/weekly claim keys so claimedQuestIds always stays well below the 40-item Firestore limit.
     */
    fun pruneAndAppendClaimedQuest(
        existingClaims: List<String>,
        newClaimKey: String,
        todayKey: String,
        weekKey: String
    ): List<String> {
        val kept = existingClaims.filter { key ->
            when {
                key.startsWith("daily_") -> key.endsWith("_$todayKey")
                key.startsWith("weekly_") -> key.endsWith("_$weekKey")
                else -> true
            }
        }
        return (kept + newClaimKey).distinct().takeLast(40)
    }

    fun buildQuestChest(userId: String, profile: HeroProfile, spec: QuestSpec): LootChest? {
        val tier = spec.chestRewardTier ?: return null
        return LootChest(
            userId = userId,
            chestTier = tier.name,
            sourceLabel = "Quest: ${spec.title}",
            heroLevelSnapshot = profile.level.coerceAtLeast(1L),
            durationMinutes = if (tier == LootChestTier.EXPEDITION) 60L else 0L,
            ultraRareChancePct = spec.chestUltraRarePct.coerceIn(10, 100).toLong()
        )
    }
}
