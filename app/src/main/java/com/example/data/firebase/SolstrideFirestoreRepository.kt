package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.DEFAULT_SKILL_RANKS
import com.example.data.model.GearPerkType
import com.example.data.model.HeroClass
import com.example.data.model.HeroProfile
import com.example.data.model.InventoryItem
import com.example.data.model.ItemRarity
import com.example.data.model.ItemSlot
import com.example.data.model.LeaderboardEntry
import com.example.data.model.LootChest
import com.example.data.model.OwnedPet
import com.example.data.model.PetType
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
                    "sunshards" to 85L,
                    "solCoins" to 0L,
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
                    "skillRanks" to DEFAULT_SKILL_RANKS,
                    "dailyQuestDateKey" to todayKey,
                    "dailyBattlesWon" to 0L,
                    "dailyExpeditionsDone" to 0L,
                    "dailyChestsOpened" to 0L,
                    "claimedQuestIds" to emptyList<String>(),
                    "chestsOpenedTotal" to 0L,
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
                "name" to "Sunforged Gladius",
                "slot" to ItemSlot.WEAPON.name,
                "rarity" to ItemRarity.RARE.name,
                "attackBonus" to 10L,
                "defenseBonus" to 2L,
                "hpBonus" to 0L,
                "energyBonus" to 0L,
                "healAmount" to 0L,
                "upgradeLevel" to 1L,
                "quantity" to 1L,
                "equipped" to true,
                "description" to "+10% Critical Strike Chance • Tempered bronze blade etched with astrolabe markings.",
                "perkType" to GearPerkType.CRIT_CHANCE.name,
                "perkValue" to 10L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "starter_aegis" to mapOf(
                "userId" to uid,
                "name" to "Limestone Brigandine",
                "slot" to ItemSlot.ARMOR.name,
                "rarity" to ItemRarity.COMMON.name,
                "attackBonus" to 0L,
                "defenseBonus" to 7L,
                "hpBonus" to 18L,
                "energyBonus" to 0L,
                "healAmount" to 0L,
                "upgradeLevel" to 0L,
                "quantity" to 1L,
                "equipped" to true,
                "description" to "-8% Incoming Enemy Damage • Breathable desert mail built for long marches.",
                "perkType" to GearPerkType.DAMAGE_REDUCTION.name,
                "perkValue" to 8L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "starter_compass" to mapOf(
                "userId" to uid,
                "name" to "Sundial Stride Relic",
                "slot" to ItemSlot.RELIC.name,
                "rarity" to ItemRarity.RARE.name,
                "attackBonus" to 2L,
                "defenseBonus" to 2L,
                "hpBonus" to 0L,
                "energyBonus" to 15L,
                "healAmount" to 0L,
                "upgradeLevel" to 0L,
                "quantity" to 1L,
                "equipped" to true,
                "description" to "+10% Expedition Survival Odds • Guides heroes safely across sunlit canyons.",
                "perkType" to GearPerkType.EXPEDITION_WARD.name,
                "perkValue" to 10L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            "starter_elixir" to mapOf(
                "userId" to uid,
                "name" to "Amber Dawn Elixir",
                "slot" to ItemSlot.CONSUMABLE.name,
                "rarity" to ItemRarity.COMMON.name,
                "attackBonus" to 0L,
                "defenseBonus" to 0L,
                "hpBonus" to 0L,
                "energyBonus" to 0L,
                "healAmount" to 65L,
                "upgradeLevel" to 0L,
                "quantity" to 3L,
                "equipped" to false,
                "description" to "Restores 65 HP instantly during or between battles.",
                "perkType" to GearPerkType.NONE.name,
                "perkValue" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        starters.forEach { (docId, payload) ->
            invCol.document(docId).set(payload).await()
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
                "healAmount" to item.healAmount.coerceIn(0L, 9999L),
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
}
