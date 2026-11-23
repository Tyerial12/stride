package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MultiplayerSubTab(val label: String) {
    GUILD("Guild"),
    RAIDS("Raids"),
    PLAYERS("Players"),
    CHAT("Chat"),
    DUELS("Duels"),
    PET_PLAY("Pet Park"),
    RANKS("Ranks")
}

enum class DuelActionKind(val label: String, val subtitle: String) {
    STRIKE("Solar Strike", "Reliable class-scaled attack"),
    CLASS_ART("Class Art", "1.38x burst + 8% HP leech"),
    GUARD("Aegis Guard", "-45% next hit + 14% HP heal")
}

enum class PetPlayActivity(
    val id: String,
    val title: String,
    val verbSummary: String
) {
    CHASE("CHASE", "Sunbeam Tag", "raced across the golden courtyard playing Sunbeam Tag!"),
    FETCH("FETCH", "Astral Fetch", "leapt together chasing a glowing Astral Orb!"),
    SPAR("SPAR", "Friendly Spar", "tumbled in a playful paw-and-wing spar!"),
    NAP("NAP", "Oasis Sun-Nap", "curled up in a warm sunbeam for a cozy Oasis Nap!")
    ;

    companion object {
        fun fromRaw(raw: String): PetPlayActivity =
            entries.firstOrNull { it.id == raw } ?: CHASE
    }
}

@Keep
@IgnoreExtraProperties
data class MultiplayerChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Strider",
    val heroClass: String = HeroClass.VANGUARD.name,
    val heroLevel: Long = 1L,
    val avatarConfig: String = HeroAvatarConfig.DEFAULT_RAW,
    val equippedPetId: String = "",
    val message: String = "",
    val channel: String = "GLOBAL",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun resolvedClass(): HeroClass = HeroClass.fromRaw(heroClass)
    fun resolvedAvatar(): HeroAvatarConfig = HeroAvatarConfig.parse(avatarConfig)
    fun resolvedPetType(): PetType? = PetType.entries.firstOrNull { it.name == equippedPetId }
}

@Keep
@IgnoreExtraProperties
data class MultiplayerDuel(
    @DocumentId val id: String = "",
    val participantUids: List<String> = emptyList(),
    val challengerUid: String = "",
    val challengerName: String = "Strider",
    val challengerClass: String = HeroClass.VANGUARD.name,
    val challengerLevel: Long = 1L,
    val challengerPower: Long = 100L,
    val challengerAvatar: String = HeroAvatarConfig.DEFAULT_RAW,
    val challengerPetId: String = "",
    val challengerHp: Long = 120L,
    val challengerMaxHp: Long = 120L,
    val challengerGuard: Boolean = false,
    val opponentUid: String = "",
    val opponentName: String = "Strider",
    val opponentClass: String = HeroClass.VANGUARD.name,
    val opponentLevel: Long = 1L,
    val opponentPower: Long = 100L,
    val opponentAvatar: String = HeroAvatarConfig.DEFAULT_RAW,
    val opponentPetId: String = "",
    val opponentHp: Long = 120L,
    val opponentMaxHp: Long = 120L,
    val opponentGuard: Boolean = false,
    val status: String = "PENDING",
    val turnUid: String = "",
    val turnNumber: Long = 1L,
    val winnerUid: String = "",
    val lastActionLog: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedChallengerClass(): HeroClass = HeroClass.fromRaw(challengerClass)
    fun resolvedChallengerAvatar(): HeroAvatarConfig = HeroAvatarConfig.parse(challengerAvatar)
    fun resolvedChallengerPet(): PetType? = PetType.entries.firstOrNull { it.name == challengerPetId }

    fun resolvedOpponentClass(): HeroClass = HeroClass.fromRaw(opponentClass)
    fun resolvedOpponentAvatar(): HeroAvatarConfig = HeroAvatarConfig.parse(opponentAvatar)
    fun resolvedOpponentPet(): PetType? = PetType.entries.firstOrNull { it.name == opponentPetId }
}

@Keep
@IgnoreExtraProperties
data class MultiplayerPetPlaydate(
    @DocumentId val id: String = "",
    val participantUids: List<String> = emptyList(),
    val hostUid: String = "",
    val hostName: String = "Strider",
    val hostPetType: String = PetType.SUNFOX_KIT.name,
    val hostPetLevel: Long = 1L,
    val guestUid: String = "",
    val guestName: String = "Strider",
    val guestPetType: String = PetType.SUNFOX_KIT.name,
    val guestPetLevel: Long = 1L,
    val status: String = "PENDING",
    val activity: String = PetPlayActivity.CHASE.id,
    val playCount: Long = 0L,
    val happinessBonus: Long = 0L,
    val lastPlayLog: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedHostPet(): PetType = PetType.entries.firstOrNull { it.name == hostPetType } ?: PetType.SUNFOX_KIT
    fun resolvedGuestPet(): PetType = PetType.entries.firstOrNull { it.name == guestPetType } ?: PetType.SUNFOX_KIT
    fun resolvedActivity(): PetPlayActivity = PetPlayActivity.fromRaw(activity)
}
