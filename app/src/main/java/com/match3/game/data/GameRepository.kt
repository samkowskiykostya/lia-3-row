package com.match3.game.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.match3.game.domain.engine.GateMaterial
import com.match3.game.domain.progression.PerkType
import com.match3.game.domain.progression.PlayerProgress

class GameRepository(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("match3_game", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val KEY_PLAYER_PROGRESS = "player_progress"
        private const val KEY_CURRENT_LEVEL = "current_level"
        private const val KEY_WALLET = "wallet"
        private const val KEY_LIVES = "lives"
        private const val KEY_GATE_MATERIAL = "gate_material"
        private const val KEY_GATE_DURABILITY = "gate_durability"
        private const val KEY_OWNED_PERKS = "owned_perks"
        private const val KEY_IN_GAME = "in_game"
        private const val KEY_GAME_LEVEL = "game_level"
        private const val KEY_GAME_MODE = "game_mode"
    }
    
    fun saveProgress(progress: PlayerProgress) {
        prefs.edit().apply {
            putInt(KEY_CURRENT_LEVEL, progress.currentLevel)
            putInt(KEY_WALLET, progress.wallet)
            putInt(KEY_LIVES, progress.lives)
            putString(KEY_GATE_MATERIAL, progress.gateMaterial.name)
            putInt(KEY_GATE_DURABILITY, progress.gateDurability)
            putString(KEY_OWNED_PERKS, gson.toJson(progress.ownedPerks))
            apply()
        }
    }
    
    fun loadProgress(): PlayerProgress {
        val currentLevel = prefs.getInt(KEY_CURRENT_LEVEL, 1)
        val wallet = prefs.getInt(KEY_WALLET, 0)
        val lives = prefs.getInt(KEY_LIVES, 5)
        val gateMaterialName = prefs.getString(KEY_GATE_MATERIAL, GateMaterial.WOOD.name)
        val gateMaterial = try {
            GateMaterial.valueOf(gateMaterialName!!)
        } catch (e: Exception) {
            GateMaterial.WOOD
        }
        val gateDurability = prefs.getInt(KEY_GATE_DURABILITY, gateMaterial.baseDurability)
        
        val ownedPerksJson = prefs.getString(KEY_OWNED_PERKS, "{}")
        val ownedPerks: MutableMap<PerkType, Int> = try {
            val map = gson.fromJson(ownedPerksJson, Map::class.java) as? Map<String, Double>
            map?.mapKeys { PerkType.valueOf(it.key) }?.mapValues { it.value.toInt() }?.toMutableMap()
                ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
        
        return PlayerProgress(
            currentLevel = currentLevel,
            wallet = wallet,
            lives = lives,
            gateMaterial = gateMaterial,
            gateDurability = gateDurability,
            ownedPerks = ownedPerks
        )
    }
    
    /** Save that player is currently in a game */
    fun saveGameInProgress(levelNumber: Int, gameMode: String) {
        prefs.edit().apply {
            putBoolean(KEY_IN_GAME, true)
            putInt(KEY_GAME_LEVEL, levelNumber)
            putString(KEY_GAME_MODE, gameMode)
            apply()
        }
    }
    
    /** Clear game in progress state */
    fun clearGameInProgress() {
        prefs.edit().apply {
            putBoolean(KEY_IN_GAME, false)
            remove(KEY_GAME_LEVEL)
            remove(KEY_GAME_MODE)
            apply()
        }
    }
    
    /** Check if there's a game in progress */
    fun hasGameInProgress(): Boolean {
        return prefs.getBoolean(KEY_IN_GAME, false)
    }
    
    /** Get the level number of game in progress */
    fun getGameInProgressLevel(): Int {
        return prefs.getInt(KEY_GAME_LEVEL, 1)
    }
    
    /** Get the game mode of game in progress */
    fun getGameInProgressMode(): String {
        return prefs.getString(KEY_GAME_MODE, "SCORE_ACCUMULATION") ?: "SCORE_ACCUMULATION"
    }
    
    fun resetProgress() {
        prefs.edit().clear().apply()
    }
}
