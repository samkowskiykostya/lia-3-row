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
        private const val KEY_GATE_MATERIAL = "gate_material"
        private const val KEY_GATE_DURABILITY = "gate_durability"
        private const val KEY_OWNED_PERKS = "owned_perks"
    }
    
    fun saveProgress(progress: PlayerProgress) {
        prefs.edit().apply {
            putInt(KEY_CURRENT_LEVEL, progress.currentLevel)
            putInt(KEY_WALLET, progress.wallet)
            putString(KEY_GATE_MATERIAL, progress.gateMaterial.name)
            putInt(KEY_GATE_DURABILITY, progress.gateDurability)
            putString(KEY_OWNED_PERKS, gson.toJson(progress.ownedPerks))
            apply()
        }
    }
    
    fun loadProgress(): PlayerProgress {
        val currentLevel = prefs.getInt(KEY_CURRENT_LEVEL, 1)
        val wallet = prefs.getInt(KEY_WALLET, 0)
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
            gateMaterial = gateMaterial,
            gateDurability = gateDurability,
            ownedPerks = ownedPerks
        )
    }
    
    fun resetProgress() {
        prefs.edit().clear().apply()
    }
}
