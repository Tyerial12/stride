package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.CURRENT_MIGRATION_VERSION
import com.example.data.model.CoopRaidRoom
import com.example.data.model.DEFAULT_SKILL_RANKS
import com.example.data.model.DEFAULT_TOWN_BUILDING_LEVELS
import com.example.data.model.FreshJourneyResetBanner
import com.example.data.model.GearPerkType
import com.example.data.model.GuildChatMessage
import com.example.data.model.HeroAvatarConfig
import com.example.data.model.HeroClass
import com.example.data.model.HeroProfile
import com.example.data.model.InventoryItem
import com.example.data.model.ItemRarity
import com.example.data.model.ItemSlot
import com.example.data.model.LeaderboardEntry
import com.example.data.model.LootChest
import com.example.data.model.MultiplayerChatMessage
import com.example.data.model.MultiplayerDuel
import com.example.data.model.MultiplayerPetPlaydate
import com.example.data.model.OwnedPet
import com.example.data.model.PetEvolutionBranch
import com.example.data.model.PetPlayActivity
import com.example.data.model.PetType
import com.example.data.model.SeasonCatalog
import com.example.data.model.SolstrideGuild
import com.example.data.model.TownPresencePlayer
import com.example.data.model.WeeklyWorldBoss
import com.example.data.model.WeeklyWorldBossCatalog
import com.example.data.model.WorldBossContribution
import com.example.data.model.currentCalendarDateKey
import com.example.data.model.currentIsoWeekKey
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SolstrideFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        if (expectedUserId != null && uid != expectedUserId) {
            throw IllegalStateException("Signed-in user mismatch; refusing cross-user write.")
        }
        return uid
    }

    fun observeHeroProfile(userId: String): Flow<HeroProfile?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(HeroProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeInventory(userId: String): Flow<List<InventoryItem>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/inventory"
        emitAll(
            db.collection("users").document(uid).collection("inventory")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(InventoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeChests(userId: String): Flow<List<LootChest>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/chests"
        emitAll(
            db.collection("users").document(uid).collection("chests")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(LootChest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePets(userId: String): Flow<List<OwnedPet>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/pets"
        emitAll(
            db.collection("users").document(uid).collection("pets")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(OwnedPet::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeLeaderboard(userId: String): Flow<List<LeaderboardEntry>> = flow {
        val uid = requireUserId(userId)
        val path = "leaderboard"
        emitAll(
            db.collection("leaderboard")
                .whereEqualTo("publicVisible", true)
                .limit(100)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(LeaderboardEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeTownPresence(userId: String): Flow<List<TownPresencePlayer>> = flow {
        val uid = requireUserId(userId)
        val path = "town_presence"
        emitAll(
            db.collection("town_presence")
                .whereEqualTo("inTown", true)
                .limit(25)
                .snapshots()
                .map { snapshot ->
                    val nowSec = System.currentTimeMillis() / 1000L
                    snapshot.toObjects(TownPresencePlayer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { player ->
                            val ts = player.updatedAt?.seconds ?: nowSec
                            (nowSec - ts) <= 300L
                        }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertTownPresence(
        userId: String,
        presence: TownPresencePlayer
    ): Result<Unit> {
        val path = "town_presence/$userId"
        return try {
            val uid = requireUserId(userId)
            val payload = mapOf(
                "userId" to uid,
                "displayName" to presence.displayName.trim().take(50).ifBlank { "Strider" },
                "heroClass" to presence.resolvedClass().name,
                "heroLevel" to presence.heroLevel.coerceIn(1L, 999L),
                "heroPower" to presence.heroPower.coerceIn(1L, 10_000_000L),
                "prestigeLevel" to presence.prestigeLevel.coerceIn(0L, 99L),
                "avatarConfig" to presence.resolvedAvatar().serialize(),
                "equippedPetId" to presence.equippedPetId.take(40),
                "equippedPetLevel" to presence.equippedPetLevel.coerceIn(1L, 50L),
                "equippedPetStage" to presence.equippedPetStage.coerceIn(1L, 3L),
                "equippedPetBranch" to presence.resolvedPetBranch().name,
                "posX" to presence.posX.coerceIn(0.0, 1.0),
                "posY" to presence.posY.coerceIn(0.0, 1.0),
                "facingRight" to presence.facingRight,
                "emote" to presence.emote.take(32),
                "inTown" to presence.inTown,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("town_presence").document(uid)
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun leaveTownPresence(userId: String): Result<Unit> {
        val path = "town_presence/$userId"
        return try {
            val uid = requireUserId(userId)
            db.collection("town_presence").document(uid).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun ensureInitializedProfile(userId: String, rawDisplayName: String?): Result<Unit> {
        val profilePath = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val safeName = rawDisplayName?.trim()?.take(48).takeUnless { it.isNullOrBlank() } ?: "Solar Vanguard"
            val weekKey = currentIsoWeekKey()
            val todayKey = currentCalendarDateKey()
            val userDoc = db.collection("users").document(uid)
            val snapshot = userDoc.get().await()

            if (!snapshot.exists()) {
                val initialProfile = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "heroClass" to HeroClass.VANGUARD.name,
                    "level" to 1L,
                    "xp" to 0L,
                    "xpToNextLevel" to 120L,
                    "might" to 5L,
                    "vigor" to 5L,
                    "endurance" to 5L,
                    "unspentPoints" to 2L,
                    "currentHp" to 165L,
                    "maxHp" to 165L,
                    "strideEnergy" to 45L,
                    "maxStrideEnergy" to 110L,
                    "encounterProgress" to 40L,
                    "sunshards" to 0L,
                    "solCoins" to 20L,
                    "totalSteps" to 0L,
                    "weeklySteps" to 0L,
                    "weekKey" to weekKey,
                    "bossesDefeated" to 0L,
                    "trueBossWins" to 0L,
                    "heroPower" to 220L,
                    "equippedPetId" to "",
                    "expeditionsCompleted" to 0L,
                    "expeditionsFailed" to 0L,
                    "activeExpeditionStartMs" to 0L,
                    "activeExpeditionDurationMin" to 0L,
                    "activeExpeditionEndMs" to 0L,
                    "currentRealm" to 1L,
                    "realmStage" to 1L,
                    "highestRealmCleared" to 0L,
                    "currentSpireFloor" to 1L,
                    "highestSpireFloor" to 0L,
                    "prestigeLevel" to 0L,
                    "skillRanks" to "1,0,0,0,0,0,0,0,0",
                    "avatarConfig" to HeroAvatarConfig.DEFAULT_RAW,
                    "characterCreated" to false,
                    "dailyQuestDateKey" to todayKey,
                    "dailyBattlesWon" to 0L,
                    "dailyExpeditionsDone" to 0L,
                    "dailyChestsOpened" to 0L,
                    "claimedQuestIds" to emptyList<String>(),
                    "chestsOpenedTotal" to 0L,
                    "migrationVersion" to CURRENT_MIGRATION_VERSION,
                    "guildId" to "",
                    "seasonKey" to SeasonCatalog.CURRENT_SEASON_KEY,
                    "seasonXp" to 0L,
                    "claimedSeasonTiers" to emptyList<Long>(),
                    "townBuildingLevels" to DEFAULT_TOWN_BUILDING_LEVELS,
                    "evolutionShards" to 0L,
                    "mythicCores" to 0L,
                    "guildCrestsOwned" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDoc.set(initialProfile).await()
                seedStarterInventory(uid)
                upsertLeaderboard(
                    userId = uid,
                    displayName = safeName,
                    heroClass = HeroClass.VANGUARD.name,
                    heroLevel = 1L,
                    heroPower = 220L,
                    weeklySteps = 0L,
                    weekKey = weekKey,
                    bossesDefeated = 0L,
                    prestigeLevel = 0L,
                    highestSpireFloor = 0L
                )
            } else {
                val existing = snapshot.toObject(HeroProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (existing != null) {
                    val lbSnap = db.collection("leaderboard").document(uid).get().await()
                    if (!lbSnap.exists()) {
                        upsertLeaderboard(
                            userId = uid,
                            displayName = existing.displayName,
                            heroClass = existing.heroClass,
                            heroLevel = existing.level,
                            heroPower = existing.heroPower,
                            weeklySteps = existing.weeklySteps,
                            weekKey = existing.weekKey,
                            bossesDefeated = existing.bossesDefeated,
                            prestigeLevel = existing.prestigeLevel,
                            highestSpireFloor = existing.highestSpireFloor
                        )
                    }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, profilePath)
            Result.failure(e)
        }
    }

    private suspend fun seedStarterInventory(uid: String) {
        val invCol = db.collection("users").document(uid).collection("inventory")
        val existing = invCol.limit(1).get().await()
        if (!existing.isEmpty) return

        val starters = listOf(
            "starter_sunblade" to mapOf(
                "userId" to uid,
                "name" to "Weathered Bronze Blade",
                "slot" to ItemSlot.WEAPON.name,
                "rarity" to ItemRarity.COMMON.name,
                "attackBonus" to 8L,
                "defenseBonus" to 0L,
                "hpBonus" to 0L,
                "energyBonus" to 0L,
                "healAmount" to 0L,
                "upgradeLevel" to 0L,
                "quantity" to 1L,
                "equipped" to true,
                "description" to "+6% Critical Strike Chance • Basic starter weapon. Spend Sol Coins in Sunhaven or battle to gather materials and forge armor!",
                "perkType" to GearPerkType.CRIT_CHANCE.name,
                "perkValue" to 6L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        starters.forEach { (docId, payload) ->
            invCol.document(docId).set(payload).await()
        }
    }

    suspend fun resetAllUserProgress(userId: String, rawDisplayName: String?): Result<Unit> {
        val profilePath = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val userDoc = db.collection("users").document(uid)

            // 1. Delete all inventory items
            val invDocs = userDoc.collection("inventory").get().await()
            for (doc in invDocs.documents) {
                doc.reference.delete().await()
            }

            // 2. Delete all unopened chests
            val chestDocs = userDoc.collection("chests").get().await()
            for (doc in chestDocs.documents) {
                doc.reference.delete().await()
            }

            // 3. Delete all owned pets
            val petDocs = userDoc.collection("pets").get().await()
            for (doc in petDocs.documents) {
                doc.reference.delete().await()
            }

            // 4. Delete hero profile and re-create a clean Level 1 profile with starter 6-slot gear
            val existingSnap = userDoc.get().await()
            val preservedName = rawDisplayName?.trim()?.take(48).takeUnless { it.isNullOrBlank() }
                ?: existingSnap.getString("displayName")?.take(48)
                ?: "Solar Vanguard"
            if (existingSnap.exists()) {
                userDoc.delete().await()
            }

            ensureInitializedProfile(uid, preservedName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, profilePath)
            Result.failure(e)
        }
    }

    /**
     * One-time idempotent progression reset (migrationVersion < 2 -> 2).
     * Resets all RPG progression, inventory, chests, pets, currencies, realms, Spire, prestige,
     * skills, and town buildings to a balanced Level 1 start while 100% preserving totalSteps,
     * weeklySteps, weekKey, displayName, avatarConfig, and characterCreated.
     */
    suspend fun performOneTimeFreshJourneyReset(userId: String): Result<FreshJourneyResetBanner?> {
        val profilePath = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val userDoc = db.collection("users").document(uid)
            val snap = userDoc.get().await()
            if (!snap.exists()) return Result.success(null)

            val existing = snap.toObject(HeroProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.success(null)

            if (existing.migrationVersion >= CURRENT_MIGRATION_VERSION) {
                return Result.success(null)
            }

            // 1. Delete all inventory, chests, and pets subcollections cleanly
            val invDocs = userDoc.collection("inventory").get().await()
            for (doc in invDocs.documents) doc.reference.delete().await()

            val chestDocs = userDoc.collection("chests").get().await()
            for (doc in chestDocs.documents) doc.reference.delete().await()

            val petDocs = userDoc.collection("pets").get().await()
            for (doc in petDocs.documents) doc.reference.delete().await()

            // 2. Preserve Steps + Identity strictly; reset RPG progression + stamp migrationVersion = 2
            val preservedTotalSteps = existing.totalSteps.coerceAtLeast(0L)
            val preservedWeeklySteps = existing.weeklySteps.coerceAtLeast(0L)
            val preservedWeekKey = existing.weekKey.ifBlank { currentIsoWeekKey() }
            val preservedName = existing.displayName.trim().take(50).ifBlank { "Solar Vanguard" }
            val preservedClass = existing.resolvedClass().name
            val preservedAvatar = existing.resolvedAvatar().serialize()
            val todayKey = currentCalendarDateKey()

            val resetUpdates = mapOf(
                "displayName" to preservedName,
                "heroClass" to preservedClass,
                "level" to 1L,
                "xp" to 0L,
                "xpToNextLevel" to 120L,
                "might" to 5L,
                "vigor" to 5L,
                "endurance" to 5L,
                "unspentPoints" to 2L,
                "currentHp" to 165L,
                "maxHp" to 165L,
                "strideEnergy" to 45L,
                "maxStrideEnergy" to 110L,
                "encounterProgress" to 40L,
                "sunshards" to 0L,
                "solCoins" to 20L,
                "totalSteps" to preservedTotalSteps,
                "weeklySteps" to preservedWeeklySteps,
                "weekKey" to preservedWeekKey,
                "bossesDefeated" to 0L,
                "trueBossWins" to 0L,
                "heroPower" to 220L,
                "equippedPetId" to "",
                "expeditionsCompleted" to 0L,
                "expeditionsFailed" to 0L,
                "activeExpeditionStartMs" to 0L,
                "activeExpeditionDurationMin" to 0L,
                "activeExpeditionEndMs" to 0L,
                "currentRealm" to 1L,
                "realmStage" to 1L,
                "highestRealmCleared" to 0L,
                "currentSpireFloor" to 1L,
                "highestSpireFloor" to 0L,
                "prestigeLevel" to 0L,
                "skillRanks" to "1,0,0,0,0,0,0,0,0",
                "dailyQuestDateKey" to todayKey,
                "dailyBattlesWon" to 0L,
                "dailyExpeditionsDone" to 0L,
                "dailyChestsOpened" to 0L,
                "claimedQuestIds" to emptyList<String>(),
                "chestsOpenedTotal" to 0L,
                "avatarConfig" to preservedAvatar,
                "characterCreated" to existing.characterCreated,
                "migrationVersion" to CURRENT_MIGRATION_VERSION,
                "guildId" to "",
                "seasonKey" to SeasonCatalog.CURRENT_SEASON_KEY,
                "seasonXp" to 0L,
                "claimedSeasonTiers" to emptyList<Long>(),
                "townBuildingLevels" to DEFAULT_TOWN_BUILDING_LEVELS,
                "evolutionShards" to 0L,
                "mythicCores" to 0L,
                "guildCrestsOwned" to 0L,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userDoc.update(resetUpdates).await()

            // 3. Re-seed Lv 1 starter weapon & sync leaderboard keeping steps
            seedStarterInventory(uid)
            upsertLeaderboard(
                userId = uid,
                displayName = preservedName,
                heroClass = preservedClass,
                heroLevel = 1L,
                heroPower = 220L,
                weeklySteps = preservedWeeklySteps,
                weekKey = preservedWeekKey,
                bossesDefeated = 0L,
                prestigeLevel = 0L,
                highestSpireFloor = 0L
            )

            Result.success(
                FreshJourneyResetBanner(
                    preservedLifetimeSteps = preservedTotalSteps,
                    preservedWeeklySteps = preservedWeeklySteps,
                    heroName = preservedName
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, profilePath)
            Result.failure(e)
        }
    }

    suspend fun updateHeroProfile(userId: String, updates: Map<String, Any>): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val payload = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(uid).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addLootChest(userId: String, chest: LootChest): Result<LootChest> {
        val docId = "chest_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "users/$userId/chests/$docId"
        return try {
            val uid = requireUserId(userId)
            val safeLabel = chest.sourceLabel.trim().take(80).ifBlank { "Highland Treasure" }
            val safeLevel = chest.heroLevelSnapshot.coerceIn(1L, 999L)
            val safeDuration = chest.durationMinutes.coerceIn(0L, 1380L)
            val safeUltra = chest.ultraRareChancePct.coerceIn(0L, 100L)

            val payload = mapOf(
                "userId" to uid,
                "chestTier" to chest.chestTier,
                "sourceLabel" to safeLabel,
                "heroLevelSnapshot" to safeLevel,
                "durationMinutes" to safeDuration,
                "ultraRareChancePct" to safeUltra,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid)
                .collection("chests").document(docId)
                .set(payload)
                .await()
            Result.success(
                chest.copy(
                    id = docId,
                    userId = uid,
                    sourceLabel = safeLabel,
                    heroLevelSnapshot = safeLevel,
                    durationMinutes = safeDuration,
                    ultraRareChancePct = safeUltra
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteLootChest(userId: String, chestId: String): Result<Unit> {
        val path = "users/$userId/chests/$chestId"
        return try {
            val uid = requireUserId(userId)
            require(chestId.isNotBlank()) { "Chest ID must not be blank" }
            db.collection("users").document(uid)
                .collection("chests").document(chestId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    /**
     * Atomically verifies a chest still exists, permanently deletes it, and writes the rolled
     * reward item inside a single Firestore transaction so no chest can ever be opened twice.
     */
    suspend fun openAndConsumeLootChest(
        userId: String,
        chestId: String,
        item: InventoryItem
    ): Result<String> {
        val path = "users/$userId/chests/$chestId"
        return try {
            val uid = requireUserId(userId)
            require(chestId.isNotBlank()) { "Chest ID must not be blank" }
            val chestRef = db.collection("users").document(uid).collection("chests").document(chestId)
            val newDocId = "item_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val itemRef = db.collection("users").document(uid).collection("inventory").document(newDocId)
            val payload = mapOf(
                "userId" to uid,
                "name" to item.name.trim().take(60).ifBlank { "Relic" },
                "slot" to item.slot,
                "rarity" to item.rarity,
                "attackBonus" to item.attackBonus.coerceIn(0L, 9999L),
                "defenseBonus" to item.defenseBonus.coerceIn(0L, 9999L),
                "hpBonus" to item.hpBonus.coerceIn(0L, 9999L),
                "energyBonus" to item.energyBonus.coerceIn(0L, 999L),
                "healAmount" to item.healAmount.coerceIn(0L, 99999L),
                "upgradeLevel" to item.upgradeLevel.coerceIn(0L, 99L),
                "quantity" to item.quantity.coerceIn(1L, 999L),
                "equipped" to item.equipped,
                "description" to item.description.take(180),
                "perkType" to item.perkType,
                "perkValue" to item.perkValue.coerceIn(0L, 100L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.runTransaction { txn ->
                val snap = txn.get(chestRef)
                if (!snap.exists()) {
                    throw IllegalStateException("CHEST_ALREADY_CONSUMED")
                }
                txn.delete(chestRef)
                txn.set(itemRef, payload)
                newDocId
            }.await()
            Result.success(newDocId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun adoptPet(userId: String, petType: PetType): Result<Unit> {
        val docId = petType.name
        val path = "users/$userId/pets/$docId"
        return try {
            val uid = requireUserId(userId)
            val payload = mapOf(
                "userId" to uid,
                "petType" to petType.name,
                "level" to 1L,
                "xp" to 0L,
                "xpToNextLevel" to 60L,
                "equippedWeaponId" to "",
                "equippedArmorId" to "",
                "evolutionStage" to 1L,
                "evolutionBranch" to PetEvolutionBranch.NONE.name,
                "bondXp" to 0L,
                "stepsWalkedWithPet" to 0L,
                "lastCareDateKey" to "",
                "dailyCareActions" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid)
                .collection("pets").document(docId)
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateOwnedPet(
        userId: String,
        petId: String,
        updates: Map<String, Any>
    ): Result<Unit> {
        val path = "users/$userId/pets/$petId"
        return try {
            val uid = requireUserId(userId)
            require(petId.isNotBlank()) { "Pet ID must not be blank" }
            val payload = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(uid)
                .collection("pets").document(petId)
                .update(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertLeaderboard(
        userId: String,
        displayName: String,
        heroClass: String,
        heroLevel: Long,
        heroPower: Long,
        weeklySteps: Long,
        weekKey: String,
        bossesDefeated: Long,
        prestigeLevel: Long = 0L,
        highestSpireFloor: Long = 0L
    ): Result<Unit> {
        val path = "leaderboard/$userId"
        return try {
            val uid = requireUserId(userId)
            val safeName = displayName.trim().take(50).ifBlank { "Solar Strider" }
            val docRef = db.collection("leaderboard").document(uid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "displayName" to safeName,
                        "heroClass" to heroClass,
                        "heroLevel" to heroLevel.coerceIn(1L, 999L),
                        "heroPower" to heroPower.coerceIn(1L, 10_000_000L),
                        "weeklySteps" to weeklySteps.coerceIn(0L, 10_000_000L),
                        "weekKey" to weekKey.take(20),
                        "bossesDefeated" to bossesDefeated.coerceIn(0L, 1_000_000L),
                        "prestigeLevel" to prestigeLevel.coerceIn(0L, 99L),
                        "highestSpireFloor" to highestSpireFloor.coerceIn(0L, 9999L),
                        "publicVisible" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeName,
                        "heroClass" to heroClass,
                        "heroLevel" to heroLevel.coerceIn(1L, 999L),
                        "heroPower" to heroPower.coerceIn(1L, 10_000_000L),
                        "weeklySteps" to weeklySteps.coerceIn(0L, 10_000_000L),
                        "weekKey" to weekKey.take(20),
                        "bossesDefeated" to bossesDefeated.coerceIn(0L, 1_000_000L),
                        "prestigeLevel" to prestigeLevel.coerceIn(0L, 99L),
                        "highestSpireFloor" to highestSpireFloor.coerceIn(0L, 9999L),
                        "publicVisible" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun addInventoryItem(userId: String, item: InventoryItem): Result<String> {
        val docId = "item_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "users/$userId/inventory/$docId"
        return try {
            val uid = requireUserId(userId)
            val payload = mapOf(
                "userId" to uid,
                "name" to item.name.trim().take(60).ifBlank { "Relic" },
                "slot" to item.slot,
                "rarity" to item.rarity,
                "attackBonus" to item.attackBonus.coerceIn(0L, 9999L),
                "defenseBonus" to item.defenseBonus.coerceIn(0L, 9999L),
                "hpBonus" to item.hpBonus.coerceIn(0L, 9999L),
                "energyBonus" to item.energyBonus.coerceIn(0L, 999L),
                "healAmount" to item.healAmount.coerceIn(0L, 99999L),
                "upgradeLevel" to item.upgradeLevel.coerceIn(0L, 99L),
                "quantity" to item.quantity.coerceIn(1L, 999L),
                "equipped" to item.equipped,
                "description" to item.description.take(180),
                "perkType" to item.perkType,
                "perkValue" to item.perkValue.coerceIn(0L, 100L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid)
                .collection("inventory").document(docId)
                .set(payload)
                .await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateInventoryItem(
        userId: String,
        itemId: String,
        updates: Map<String, Any>
    ): Result<Unit> {
        val path = "users/$userId/inventory/$itemId"
        return try {
            val uid = requireUserId(userId)
            require(itemId.isNotBlank()) { "Item ID must not be blank" }
            val payload = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(uid)
                .collection("inventory").document(itemId)
                .update(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteInventoryItem(userId: String, itemId: String): Result<Unit> {
        val path = "users/$userId/inventory/$itemId"
        return try {
            val uid = requireUserId(userId)
            require(itemId.isNotBlank()) { "Item ID must not be blank" }
            db.collection("users").document(uid)
                .collection("inventory").document(itemId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Real-Time Multiplayer Chat (/multiplayer_chat) ---

    fun observeMultiplayerChat(userId: String): Flow<List<MultiplayerChatMessage>> = flow {
        val uid = requireUserId(userId)
        val path = "multiplayer_chat"
        emitAll(
            db.collection("multiplayer_chat")
                .whereEqualTo("channel", "GLOBAL")
                .limit(80)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MultiplayerChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                        .takeLast(60)
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendMultiplayerChatMessage(
        userId: String,
        profile: HeroProfile,
        rawMessage: String
    ): Result<Unit> {
        val docId = "msg_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val path = "multiplayer_chat/$docId"
        return try {
            val uid = requireUserId(userId)
            val cleanMsg = rawMessage.trim().take(240)
            require(cleanMsg.isNotEmpty()) { "Message must not be empty" }
            val safePetId = PetType.entries.firstOrNull { it.name == profile.equippedPetId }?.name ?: ""
            val payload = mapOf(
                "userId" to uid,
                "displayName" to profile.displayName.trim().take(50).ifBlank { "Strider" },
                "heroClass" to profile.resolvedClass().name,
                "heroLevel" to profile.level.coerceIn(1L, 999L),
                "avatarConfig" to profile.resolvedAvatar().serialize(),
                "equippedPetId" to safePetId,
                "message" to cleanMsg,
                "channel" to "GLOBAL",
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("multiplayer_chat").document(docId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMultiplayerChatMessage(userId: String, messageId: String): Result<Unit> {
        val path = "multiplayer_chat/$messageId"
        return try {
            requireUserId(userId)
            require(messageId.isNotBlank()) { "Message ID must not be blank" }
            db.collection("multiplayer_chat").document(messageId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Real-Time Multiplayer Hero Duels (/multiplayer_duels) ---

    fun observeMultiplayerDuels(userId: String): Flow<List<MultiplayerDuel>> = flow {
        val uid = requireUserId(userId)
        val path = "multiplayer_duels"
        emitAll(
            db.collection("multiplayer_duels")
                .whereArrayContains("participantUids", uid)
                .limit(25)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MultiplayerDuel::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createDuelChallenge(
        userId: String,
        challenger: HeroProfile,
        challengerMaxHp: Long,
        challengerPower: Long,
        opponent: TownPresencePlayer
    ): Result<String> {
        val docId = "duel_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val path = "multiplayer_duels/$docId"
        return try {
            val uid = requireUserId(userId)
            val oppUid = opponent.userId.ifBlank { opponent.id }
            require(oppUid.isNotBlank() && oppUid != uid) { "Invalid duel opponent" }

            val safeChallengerHp = challengerMaxHp.coerceIn(100L, 1_000_000L)
            val safeOppHp = (120L + opponent.heroLevel * 28L + (opponent.heroPower / 4L)).coerceIn(100L, 1_000_000L)
            val cPet = PetType.entries.firstOrNull { it.name == challenger.equippedPetId }?.name ?: ""
            val oPet = PetType.entries.firstOrNull { it.name == opponent.equippedPetId }?.name ?: ""
            val cName = challenger.displayName.trim().take(50).ifBlank { "Strider" }
            val oName = opponent.displayName.trim().take(50).ifBlank { "Strider" }

            val payload = mapOf(
                "participantUids" to listOf(uid, oppUid),
                "challengerUid" to uid,
                "challengerName" to cName,
                "challengerClass" to challenger.resolvedClass().name,
                "challengerLevel" to challenger.level.coerceIn(1L, 999L),
                "challengerPower" to challengerPower.coerceIn(1L, 10_000_000L),
                "challengerAvatar" to challenger.resolvedAvatar().serialize(),
                "challengerPetId" to cPet,
                "challengerHp" to safeChallengerHp,
                "challengerMaxHp" to safeChallengerHp,
                "challengerGuard" to false,
                "opponentUid" to oppUid,
                "opponentName" to oName,
                "opponentClass" to opponent.resolvedClass().name,
                "opponentLevel" to opponent.heroLevel.coerceIn(1L, 999L),
                "opponentPower" to opponent.heroPower.coerceIn(1L, 10_000_000L),
                "opponentAvatar" to opponent.resolvedAvatar().serialize(),
                "opponentPetId" to oPet,
                "opponentHp" to safeOppHp,
                "opponentMaxHp" to safeOppHp,
                "opponentGuard" to false,
                "status" to "PENDING",
                "turnUid" to uid,
                "turnNumber" to 1L,
                "winnerUid" to "",
                "lastActionLog" to "$cName challenged $oName to a Solar Duel!".take(240),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("multiplayer_duels").document(docId).set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateDuelState(
        userId: String,
        duelId: String,
        updates: Map<String, Any>
    ): Result<Unit> {
        val path = "multiplayer_duels/$duelId"
        return try {
            requireUserId(userId)
            require(duelId.isNotBlank()) { "Duel ID must not be blank" }
            val payload = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("multiplayer_duels").document(duelId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteDuel(userId: String, duelId: String): Result<Unit> {
        val path = "multiplayer_duels/$duelId"
        return try {
            requireUserId(userId)
            require(duelId.isNotBlank()) { "Duel ID must not be blank" }
            db.collection("multiplayer_duels").document(duelId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Real-Time Companion Pet Playdates (/multiplayer_pet_playdates) ---

    fun observePetPlaydates(userId: String): Flow<List<MultiplayerPetPlaydate>> = flow {
        val uid = requireUserId(userId)
        val path = "multiplayer_pet_playdates"
        emitAll(
            db.collection("multiplayer_pet_playdates")
                .whereArrayContains("participantUids", uid)
                .limit(20)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MultiplayerPetPlaydate::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createPetPlaydate(
        userId: String,
        hostProfile: HeroProfile,
        hostPet: OwnedPet,
        guestPlayer: TownPresencePlayer
    ): Result<String> {
        val docId = "play_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val path = "multiplayer_pet_playdates/$docId"
        return try {
            val uid = requireUserId(userId)
            val guestUid = guestPlayer.userId.ifBlank { guestPlayer.id }
            val guestPetType = guestPlayer.resolvedPetType()
            require(guestUid.isNotBlank() && guestUid != uid && guestPetType != null) {
                "Both players must have an equipped companion pet to start a playdate"
            }
            val hostPetType = hostPet.resolvedType()
            val hName = hostProfile.displayName.trim().take(50).ifBlank { "Strider" }
            val gName = guestPlayer.displayName.trim().take(50).ifBlank { "Strider" }
            val payload = mapOf(
                "participantUids" to listOf(uid, guestUid),
                "hostUid" to uid,
                "hostName" to hName,
                "hostPetType" to hostPetType.name,
                "hostPetLevel" to hostPet.level.coerceIn(1L, 50L),
                "guestUid" to guestUid,
                "guestName" to gName,
                "guestPetType" to guestPetType.name,
                "guestPetLevel" to guestPlayer.equippedPetLevel.coerceIn(1L, 50L),
                "status" to "PENDING",
                "activity" to PetPlayActivity.CHASE.id,
                "playCount" to 0L,
                "happinessBonus" to 0L,
                "lastPlayLog" to "${hostPetType.displayName} invited ${guestPetType.displayName} to play in the Sunhaven Pet Park!".take(240),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("multiplayer_pet_playdates").document(docId).set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePetPlaydate(
        userId: String,
        playdateId: String,
        updates: Map<String, Any>
    ): Result<Unit> {
        val path = "multiplayer_pet_playdates/$playdateId"
        return try {
            requireUserId(userId)
            require(playdateId.isNotBlank()) { "Playdate ID must not be blank" }
            val payload = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("multiplayer_pet_playdates").document(playdateId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePetPlaydate(userId: String, playdateId: String): Result<Unit> {
        val path = "multiplayer_pet_playdates/$playdateId"
        return try {
            requireUserId(userId)
            require(playdateId.isNotBlank()) { "Playdate ID must not be blank" }
            db.collection("multiplayer_pet_playdates").document(playdateId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Weekly World Boss (/world_bosses/{weekKey}) ---

    fun observeWeeklyWorldBoss(userId: String, weekKey: String): Flow<WeeklyWorldBoss?> = flow {
        val uid = requireUserId(userId)
        val path = "world_bosses/$weekKey"
        emitAll(
            db.collection("world_bosses").document(weekKey)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(WeeklyWorldBoss::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeWorldBossContributions(userId: String, weekKey: String): Flow<List<WorldBossContribution>> = flow {
        val uid = requireUserId(userId)
        val path = "world_bosses/$weekKey/contributions"
        emitAll(
            db.collection("world_bosses").document(weekKey).collection("contributions")
                .limit(30)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(WorldBossContribution::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.totalDamage }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureWeeklyWorldBoss(userId: String, weekKey: String): Result<Unit> {
        val path = "world_bosses/$weekKey"
        return try {
            requireUserId(userId)
            val docRef = db.collection("world_bosses").document(weekKey)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val tpl = WeeklyWorldBossCatalog.templateForWeek(weekKey)
                docRef.set(
                    mapOf(
                        "weekKey" to weekKey,
                        "bossName" to tpl.name,
                        "elementalAffinity" to tpl.affinity.name,
                        "maxHp" to tpl.maxHp,
                        "currentHp" to tpl.maxHp,
                        "totalStrikes" to 0L,
                        "publicVisible" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun strikeWeeklyWorldBoss(
        userId: String,
        weekKey: String,
        displayName: String,
        damage: Long
    ): Result<Long> {
        val path = "world_bosses/$weekKey"
        return try {
            val uid = requireUserId(userId)
            val safeDamage = damage.coerceIn(10L, 50_000L)
            val bossRef = db.collection("world_bosses").document(weekKey)
            val contribRef = bossRef.collection("contributions").document(uid)
            val tpl = WeeklyWorldBossCatalog.templateForWeek(weekKey)

            val newRemaining = db.runTransaction { txn ->
                val bossSnap = txn.get(bossRef)
                val contribSnap = txn.get(contribRef)
                val curHp = if (bossSnap.exists()) {
                    bossSnap.getLong("currentHp") ?: tpl.maxHp
                } else {
                    tpl.maxHp
                }
                val curStrikes = if (bossSnap.exists()) bossSnap.getLong("totalStrikes") ?: 0L else 0L
                val nextHp = (curHp - safeDamage).coerceAtLeast(0L)

                if (bossSnap.exists()) {
                    txn.update(
                        bossRef,
                        mapOf(
                            "currentHp" to nextHp,
                            "totalStrikes" to curStrikes + 1L,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    txn.set(
                        bossRef,
                        mapOf(
                            "weekKey" to weekKey,
                            "bossName" to tpl.name,
                            "elementalAffinity" to tpl.affinity.name,
                            "maxHp" to tpl.maxHp,
                            "currentHp" to nextHp,
                            "totalStrikes" to 1L,
                            "publicVisible" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                val prevDmg = if (contribSnap.exists()) contribSnap.getLong("totalDamage") ?: 0L else 0L
                val prevCount = if (contribSnap.exists()) contribSnap.getLong("strikesCount") ?: 0L else 0L
                val prevClaimed = if (contribSnap.exists()) contribSnap.getLong("claimedMilestoneTier") ?: 0L else 0L
                txn.set(
                    contribRef,
                    mapOf(
                        "userId" to uid,
                        "weekKey" to weekKey,
                        "displayName" to displayName.trim().take(50).ifBlank { "Strider" },
                        "totalDamage" to (prevDmg + safeDamage).coerceAtMost(100_000_000L),
                        "strikesCount" to (prevCount + 1L).coerceAtMost(100_000L),
                        "claimedMilestoneTier" to prevClaimed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                nextHp
            }.await()
            Result.success(newRemaining)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateWorldBossClaimedMilestone(
        userId: String,
        weekKey: String,
        displayName: String,
        totalDamage: Long,
        strikesCount: Long,
        claimedTier: Long
    ): Result<Unit> {
        val path = "world_bosses/$weekKey/contributions/$userId"
        return try {
            val uid = requireUserId(userId)
            db.collection("world_bosses").document(weekKey)
                .collection("contributions").document(uid)
                .set(
                    mapOf(
                        "userId" to uid,
                        "weekKey" to weekKey,
                        "displayName" to displayName.trim().take(50).ifBlank { "Strider" },
                        "totalDamage" to totalDamage.coerceIn(0L, 100_000_000L),
                        "strikesCount" to strikesCount.coerceIn(0L, 100_000L),
                        "claimedMilestoneTier" to claimedTier.coerceIn(0L, 10L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- Co-op Boss Raid Rooms (/coop_raids/{raidId}) ---

    fun observeCoopRaids(userId: String): Flow<List<CoopRaidRoom>> = flow {
        val uid = requireUserId(userId)
        val path = "coop_raids"
        emitAll(
            db.collection("coop_raids")
                .whereEqualTo("publicVisible", true)
                .limit(20)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CoopRaidRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createCoopRaid(
        userId: String,
        hostName: String,
        bossName: String,
        bossAffinity: String,
        bossMaxHp: Long
    ): Result<String> {
        val docId = "raid_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val path = "coop_raids/$docId"
        return try {
            val uid = requireUserId(userId)
            val safeHost = hostName.trim().take(50).ifBlank { "Strider" }
            val safeMaxHp = bossMaxHp.coerceIn(2_000L, 500_000L)
            val payload = mapOf(
                "hostUid" to uid,
                "hostName" to safeHost,
                "bossName" to bossName.trim().take(60).ifBlank { "Solaris Raid Titan" },
                "bossAffinity" to bossAffinity,
                "bossMaxHp" to safeMaxHp,
                "bossCurrentHp" to safeMaxHp,
                "participantUids" to listOf(uid),
                "participantNames" to listOf(safeHost),
                "status" to "OPEN",
                "totalTeamDamage" to 0L,
                "lastActionLog" to "$safeHost opened a Co-op Raid vs $bossName!".take(240),
                "claimedUids" to emptyList<String>(),
                "publicVisible" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("coop_raids").document(docId).set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun joinCoopRaid(userId: String, displayName: String, raidId: String): Result<Unit> {
        val path = "coop_raids/$raidId"
        return try {
            val uid = requireUserId(userId)
            val safeName = displayName.trim().take(50).ifBlank { "Strider" }
            val docRef = db.collection("coop_raids").document(raidId)
            db.runTransaction { txn ->
                val snap = txn.get(docRef)
                if (!snap.exists()) throw IllegalStateException("Raid room no longer exists.")
                @Suppress("UNCHECKED_CAST")
                val uids = (snap.get("participantUids") as? List<String>)?.toMutableList() ?: mutableListOf()
                @Suppress("UNCHECKED_CAST")
                val names = (snap.get("participantNames") as? List<String>)?.toMutableList() ?: mutableListOf()
                if (uid !in uids) {
                    if (uids.size >= 4) throw IllegalStateException("Raid party is full (4/4).")
                    uids.add(uid)
                    names.add(safeName)
                }
                val status = if (snap.getString("status") == "OPEN" && uids.size >= 2) "ACTIVE" else (snap.getString("status") ?: "OPEN")
                txn.update(
                    docRef,
                    mapOf(
                        "participantUids" to uids,
                        "participantNames" to names,
                        "status" to status,
                        "lastActionLog" to "$safeName joined the Raid squad!".take(240),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun strikeCoopRaid(
        userId: String,
        displayName: String,
        raidId: String,
        damage: Long,
        actionLabel: String
    ): Result<Boolean> {
        val path = "coop_raids/$raidId"
        return try {
            val uid = requireUserId(userId)
            val safeName = displayName.trim().take(50).ifBlank { "Strider" }
            val safeDamage = damage.coerceIn(10L, 50_000L)
            val docRef = db.collection("coop_raids").document(raidId)
            val defeated = db.runTransaction { txn ->
                val snap = txn.get(docRef)
                if (!snap.exists()) throw IllegalStateException("Raid room closed.")
                @Suppress("UNCHECKED_CAST")
                val uids = (snap.get("participantUids") as? List<String>) ?: emptyList()
                if (uid !in uids) throw IllegalStateException("Join the Raid first.")
                val curHp = snap.getLong("bossCurrentHp") ?: 0L
                val curTotal = snap.getLong("totalTeamDamage") ?: 0L
                val nextHp = (curHp - safeDamage).coerceAtLeast(0L)
                val nextStatus = if (nextHp <= 0L) "VICTORY" else "ACTIVE"
                val log = if (nextHp <= 0L) {
                    "$safeName landed the killing $actionLabel ($safeDamage DMG)! RAID VICTORY!"
                } else {
                    "$safeName hit with $actionLabel for $safeDamage DMG!"
                }
                txn.update(
                    docRef,
                    mapOf(
                        "bossCurrentHp" to nextHp,
                        "status" to nextStatus,
                        "totalTeamDamage" to (curTotal + safeDamage).coerceAtMost(10_000_000L),
                        "lastActionLog" to log.take(240),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                nextHp <= 0L
            }.await()
            Result.success(defeated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markCoopRaidRewardClaimed(userId: String, raidId: String): Result<Unit> {
        val path = "coop_raids/$raidId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("coop_raids").document(raidId)
            db.runTransaction { txn ->
                val snap = txn.get(docRef)
                if (!snap.exists()) return@runTransaction
                @Suppress("UNCHECKED_CAST")
                val claimed = (snap.get("claimedUids") as? List<String>)?.toMutableList() ?: mutableListOf()
                if (uid !in claimed) {
                    claimed.add(uid)
                    txn.update(
                        docRef,
                        mapOf(
                            "claimedUids" to claimed,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCoopRaid(userId: String, raidId: String): Result<Unit> {
        val path = "coop_raids/$raidId"
        return try {
            requireUserId(userId)
            db.collection("coop_raids").document(raidId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Step-Powered Guilds (/guilds/{guildId} & /guilds/{guildId}/messages) ---

    fun observeGuilds(userId: String): Flow<List<SolstrideGuild>> = flow {
        val uid = requireUserId(userId)
        val path = "guilds"
        emitAll(
            db.collection("guilds")
                .whereEqualTo("publicVisible", true)
                .limit(30)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(SolstrideGuild::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.weeklyGuildSteps }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeGuildChat(userId: String, guildId: String): Flow<List<GuildChatMessage>> = flow {
        val uid = requireUserId(userId)
        val path = "guilds/$guildId/messages"
        emitAll(
            db.collection("guilds").document(guildId).collection("messages")
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(GuildChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                        .takeLast(40)
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createGuild(
        userId: String,
        leaderName: String,
        guildName: String,
        motto: String,
        emblemHex: Long,
        initialWeeklySteps: Long
    ): Result<String> {
        val docId = "guild_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val path = "guilds/$docId"
        return try {
            val uid = requireUserId(userId)
            val safeName = guildName.trim().take(36)
            require(safeName.length >= 2) { "Guild name must be at least 2 characters." }
            val safeMotto = motto.trim().take(90).ifBlank { "Every step lights the dawn." }
            val safeLeader = leaderName.trim().take(50).ifBlank { "Strider" }
            val payload = mapOf(
                "name" to safeName,
                "motto" to safeMotto,
                "emblemHex" to emblemHex,
                "leaderUid" to uid,
                "leaderName" to safeLeader,
                "memberUids" to listOf(uid),
                "memberNames" to listOf(safeLeader),
                "weekKey" to currentIsoWeekKey(),
                "weeklyGuildSteps" to initialWeeklySteps.coerceIn(0L, 100_000_000L),
                "weeklyStepGoal" to 50_000L,
                "weeklyBossDamage" to 0L,
                "guildLevel" to 1L,
                "publicVisible" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("guilds").document(docId).set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun joinGuild(userId: String, displayName: String, guildId: String): Result<Unit> {
        val path = "guilds/$guildId"
        return try {
            val uid = requireUserId(userId)
            val safeName = displayName.trim().take(50).ifBlank { "Strider" }
            val docRef = db.collection("guilds").document(guildId)
            db.runTransaction { txn ->
                val snap = txn.get(docRef)
                if (!snap.exists()) throw IllegalStateException("Guild not found.")
                @Suppress("UNCHECKED_CAST")
                val uids = (snap.get("memberUids") as? List<String>)?.toMutableList() ?: mutableListOf()
                @Suppress("UNCHECKED_CAST")
                val names = (snap.get("memberNames") as? List<String>)?.toMutableList() ?: mutableListOf()
                if (uid !in uids) {
                    if (uids.size >= 25) throw IllegalStateException("Guild roster is full (25/25).")
                    uids.add(uid)
                    names.add(safeName)
                    txn.update(
                        docRef,
                        mapOf(
                            "memberUids" to uids,
                            "memberNames" to names,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun leaveGuild(userId: String, guildId: String): Result<Unit> {
        val path = "guilds/$guildId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("guilds").document(guildId)
            val snap = docRef.get().await()
            if (!snap.exists()) return Result.success(Unit)
            @Suppress("UNCHECKED_CAST")
            val uids = (snap.get("memberUids") as? List<String>)?.toMutableList() ?: mutableListOf()
            @Suppress("UNCHECKED_CAST")
            val names = (snap.get("memberNames") as? List<String>)?.toMutableList() ?: mutableListOf()
            val idx = uids.indexOf(uid)
            if (idx >= 0) {
                uids.removeAt(idx)
                if (idx < names.size) names.removeAt(idx)
            }
            if (uids.isEmpty()) {
                docRef.delete().await()
            } else {
                val curLeader = snap.getString("leaderUid") ?: ""
                val nextLeaderUid = if (curLeader == uid) uids.first() else curLeader
                val nextLeaderName = if (curLeader == uid) names.firstOrNull().orEmpty().ifBlank { "Strider" }
                else snap.getString("leaderName").orEmpty().ifBlank { "Strider" }
                docRef.update(
                    mapOf(
                        "memberUids" to uids,
                        "memberNames" to names,
                        "leaderUid" to nextLeaderUid,
                        "leaderName" to nextLeaderName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun contributeGuildProgress(
        userId: String,
        guildId: String,
        stepsDelta: Long,
        bossDamageDelta: Long = 0L
    ): Result<Unit> {
        if (guildId.isBlank() || (stepsDelta <= 0L && bossDamageDelta <= 0L)) return Result.success(Unit)
        val path = "guilds/$guildId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("guilds").document(guildId)
            val nowWeek = currentIsoWeekKey()
            db.runTransaction { txn ->
                val snap = txn.get(docRef)
                if (!snap.exists()) return@runTransaction
                @Suppress("UNCHECKED_CAST")
                val uids = (snap.get("memberUids") as? List<String>) ?: emptyList()
                if (uid !in uids) return@runTransaction
                val storedWeek = snap.getString("weekKey") ?: nowWeek
                val baseSteps = if (storedWeek == nowWeek) (snap.getLong("weeklyGuildSteps") ?: 0L) else 0L
                val baseDmg = if (storedWeek == nowWeek) (snap.getLong("weeklyBossDamage") ?: 0L) else 0L
                val nextSteps = (baseSteps + stepsDelta.coerceAtLeast(0L)).coerceAtMost(100_000_000L)
                val nextDmg = (baseDmg + bossDamageDelta.coerceAtLeast(0L)).coerceAtMost(100_000_000L)
                val curLevel = snap.getLong("guildLevel") ?: 1L
                val curGoal = snap.getLong("weeklyStepGoal") ?: 50_000L
                val nextLevel = if (nextSteps >= curGoal && curLevel < 50L) curLevel + 1L else curLevel
                val nextGoal = if (nextLevel > curLevel) (curGoal + 25_000L).coerceAtMost(10_000_000L) else curGoal
                txn.update(
                    docRef,
                    mapOf(
                        "weekKey" to nowWeek,
                        "weeklyGuildSteps" to nextSteps,
                        "weeklyBossDamage" to nextDmg,
                        "guildLevel" to nextLevel,
                        "weeklyStepGoal" to nextGoal,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendGuildChatMessage(
        userId: String,
        guildId: String,
        displayName: String,
        heroLevel: Long,
        rawMessage: String
    ): Result<Unit> {
        val docId = "gmsg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "guilds/$guildId/messages/$docId"
        return try {
            val uid = requireUserId(userId)
            val clean = rawMessage.trim().take(240)
            require(clean.isNotEmpty()) { "Message cannot be empty" }
            val payload = mapOf(
                "guildId" to guildId,
                "userId" to uid,
                "displayName" to displayName.trim().take(50).ifBlank { "Strider" },
                "heroLevel" to heroLevel.coerceIn(1L, 999L),
                "message" to clean,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("guilds").document(guildId)
                .collection("messages").document(docId)
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }
}
