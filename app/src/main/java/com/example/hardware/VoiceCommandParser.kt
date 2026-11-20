package com.example.hardware

import android.content.Intent
import android.speech.RecognizerIntent
import java.util.Locale

enum class VoiceCommandAction(val spokenHint: String) {
    STRIKE("Strike"),
    SOLAR_SKILL("Solar Skill"),
    GUARD("Guard"),
    HEAL("Heal"),
    RETREAT("Retreat"),
    ENGAGE_BATTLE("Battle"),
    OPEN_EXPEDITION("Expedition"),
    OPEN_PETS("Pets"),
    OPEN_INVENTORY("Inventory"),
    OPEN_LEADERBOARD("Ranks"),
    SYNC_WATCH("Sync Watch"),
    TOGGLE_DISPLAY_MODE("Sunlight Mode")
}

object VoiceCommandParser {

    fun buildSpeechPromptIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Say Strike, Skill, Guard, Heal, Battle, Pets, Inventory, Ranks, Sync, or Sunlight"
            )
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

    fun parseTranscript(candidates: List<String>): VoiceCommandAction? {
        for (raw in candidates) {
            val text = raw.lowercase(Locale.US).trim()
            if (text.isBlank()) continue
            val matched = when {
                text.contains("solar") || text.contains("skill") || text.contains("special") || text.contains("burst") ->
                    VoiceCommandAction.SOLAR_SKILL
                text.contains("strike") || text.contains("attack") || text.contains("slash") || text.contains("hit") ->
                    VoiceCommandAction.STRIKE
                text.contains("guard") || text.contains("defend") || text.contains("shield") || text.contains("block") ->
                    VoiceCommandAction.GUARD
                text.contains("heal") || text.contains("potion") || text.contains("elixir") || text.contains("drink") ->
                    VoiceCommandAction.HEAL
                text.contains("retreat") || text.contains("flee") || text.contains("escape") ->
                    VoiceCommandAction.RETREAT
                text.contains("battle") || text.contains("fight") || text.contains("engage") || text.contains("combat") ->
                    VoiceCommandAction.ENGAGE_BATTLE
                text.contains("pet") || text.contains("companion") || text.contains("shop") ->
                    VoiceCommandAction.OPEN_PETS
                text.contains("inventory") || text.contains("gear") || text.contains("bag") || text.contains("hero") ->
                    VoiceCommandAction.OPEN_INVENTORY
                text.contains("rank") || text.contains("leader") || text.contains("score") || text.contains("board") ->
                    VoiceCommandAction.OPEN_LEADERBOARD
                text.contains("sync") || text.contains("watch") || text.contains("galaxy") ->
                    VoiceCommandAction.SYNC_WATCH
                text.contains("sun") || text.contains("dark") || text.contains("oled") || text.contains("mode") ->
                    VoiceCommandAction.TOGGLE_DISPLAY_MODE
                text.contains("walk") || text.contains("home") || text.contains("expedition") || text.contains("hub") ->
                    VoiceCommandAction.OPEN_EXPEDITION
                else -> null
            }
            if (matched != null) return matched
        }
        return null
    }
}
