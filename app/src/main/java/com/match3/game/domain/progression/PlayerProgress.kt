package com.match3.game.domain.progression

import com.match3.game.domain.engine.Gate
import com.match3.game.domain.engine.GateMaterial
import com.match3.game.domain.model.*

data class PlayerProgress(
    var currentLevel: Int = 1,
    var wallet: Int = 0,
    var lives: Int = 5,
    var maxLives: Int = 5,
    var gateMaterial: GateMaterial = GateMaterial.WOOD,
    var gateDurability: Int = GateMaterial.WOOD.baseDurability,
    var ownedPerks: MutableMap<PerkType, Int> = mutableMapOf(),
    var activePerks: MutableMap<PerkType, Int> = mutableMapOf() // Perk -> turns remaining
) {
    companion object {
        const val LIFE_COST = 10
    }
    fun getGate(): Gate {
        return Gate(
            material = gateMaterial,
            durability = gateDurability,
            maxDurability = gateMaterial.baseDurability
        )
    }
    
    fun addCurrency(amount: Int) {
        wallet += amount
    }
    
    fun spendCurrency(amount: Int): Boolean {
        if (wallet >= amount) {
            wallet -= amount
            return true
        }
        return false
    }
    
    fun repairGate(amount: Int, cost: Int): Boolean {
        if (spendCurrency(cost)) {
            gateDurability = minOf(gateDurability + amount, gateMaterial.baseDurability)
            return true
        }
        return false
    }
    
    fun upgradeGate(): Boolean {
        val nextMaterial = when (gateMaterial) {
            GateMaterial.WOOD -> GateMaterial.STONE
            GateMaterial.STONE -> GateMaterial.IRON
            GateMaterial.IRON -> GateMaterial.STEEL
            GateMaterial.STEEL -> GateMaterial.DIAMOND
            GateMaterial.DIAMOND -> null
        }
        
        if (nextMaterial != null) {
            val cost = getGateUpgradeCost()
            if (spendCurrency(cost)) {
                gateMaterial = nextMaterial
                gateDurability = nextMaterial.baseDurability
                return true
            }
        }
        return false
    }
    
    fun getGateUpgradeCost(): Int {
        return when (gateMaterial) {
            GateMaterial.WOOD -> 100
            GateMaterial.STONE -> 250
            GateMaterial.IRON -> 500
            GateMaterial.STEEL -> 1000
            GateMaterial.DIAMOND -> Int.MAX_VALUE
        }
    }
    
    fun getRepairCost(amount: Int): Int {
        return amount * when (gateMaterial) {
            GateMaterial.WOOD -> 1
            GateMaterial.STONE -> 2
            GateMaterial.IRON -> 3
            GateMaterial.STEEL -> 4
            GateMaterial.DIAMOND -> 5
        }
    }
    
    fun buyPerk(perk: PerkType): Boolean {
        val cost = perk.cost
        if (spendCurrency(cost)) {
            ownedPerks[perk] = (ownedPerks[perk] ?: 0) + 1
            return true
        }
        return false
    }
    
    fun activatePerk(perk: PerkType): Boolean {
        val owned = ownedPerks[perk] ?: 0
        if (owned > 0) {
            ownedPerks[perk] = owned - 1
            activePerks[perk] = perk.duration
            return true
        }
        return false
    }
    
    fun consumePerkTurn() {
        val expired = mutableListOf<PerkType>()
        for ((perk, turns) in activePerks) {
            if (turns <= 1) {
                expired.add(perk)
            } else {
                activePerks[perk] = turns - 1
            }
        }
        expired.forEach { activePerks.remove(it) }
    }
    
    fun isPerkActive(perk: PerkType): Boolean {
        return (activePerks[perk] ?: 0) > 0
    }
    
    fun applyGateDamage(damage: Int) {
        gateDurability = maxOf(0, gateDurability - damage)
    }
    
    fun advanceLevel() {
        currentLevel++
    }
    
    fun useLife(): Boolean {
        if (lives > 0) {
            lives--
            return true
        }
        return false
    }
    
    fun buyLife(): Boolean {
        if (spendCurrency(LIFE_COST) && lives < maxLives) {
            lives++
            return true
        }
        return false
    }
    
    fun hasLives(): Boolean = lives > 0
    
    fun refillLife() {
        if (lives < maxLives) {
            lives++
        }
    }
}

enum class PerkType(
    val displayName: String,
    val description: String,
    val emoji: String,
    val cost: Int,
    val duration: Int // in turns, 0 = instant
) {
    EXTRA_ROCKET(
        "Extra Rocket",
        "Start with a random rocket on the board",
        "🚀",
        50,
        0
    ),
    EXTRA_BOMB(
        "Extra Bomb",
        "Start with a bomb on the board",
        "💣",
        75,
        0
    ),
    EXTRA_PROPELLER(
        "Extra Propeller",
        "Start with a propeller on the board",
        "🌀",
        60,
        0
    ),
    EXTRA_DISCO(
        "Extra Disco Ball",
        "Start with a disco ball on the board",
        "🪩",
        100,
        0
    ),
    DOUBLE_DAMAGE(
        "Double Damage",
        "Projectiles deal 2x damage for 5 turns",
        "⚔️",
        150,
        5
    ),
    SHIELD(
        "Shield",
        "Gate takes 50% less damage for 3 turns",
        "🛡️",
        200,
        3
    ),
    LUCKY_SPAWNS(
        "Lucky Spawns",
        "Higher chance of special blocks for 10 turns",
        "🍀",
        100,
        10
    ),
    SCORE_BOOST(
        "Score Boost",
        "2x score multiplier for 5 turns",
        "✨",
        80,
        5
    )
}
