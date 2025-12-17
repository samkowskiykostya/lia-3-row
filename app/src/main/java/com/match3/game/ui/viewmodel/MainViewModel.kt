package com.match3.game.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.match3.game.data.GameRepository
import com.match3.game.domain.engine.GateMaterial
import com.match3.game.domain.model.LevelConfig
import com.match3.game.domain.progression.LevelGenerator
import com.match3.game.domain.progression.PerkType
import com.match3.game.domain.progression.PlayerProgress

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GameRepository(application)

    private val _playerProgress = MutableLiveData<PlayerProgress>()
    val playerProgress: LiveData<PlayerProgress> = _playerProgress

    private val _nextLevelConfig = MutableLiveData<LevelConfig>()
    val nextLevelConfig: LiveData<LevelConfig> = _nextLevelConfig

    private val _levelDescription = MutableLiveData<String>()
    val levelDescription: LiveData<String> = _levelDescription

    private val _levelEmoji = MutableLiveData<String>()
    val levelEmoji: LiveData<String> = _levelEmoji

    private val _shopItems = MutableLiveData<List<ShopItem>>()
    val shopItems: LiveData<List<ShopItem>> = _shopItems

    init {
        loadProgress()
        generateShopItems()
    }

    fun loadProgress() {
        val progress = repository.loadProgress()
        _playerProgress.value = progress
        updateNextLevel()
    }

    private fun updateNextLevel() {
        val progress = _playerProgress.value ?: return
        val config = LevelGenerator.generateLevel(progress.currentLevel)
        _nextLevelConfig.value = config
        _levelDescription.value = LevelGenerator.getLevelDescription(config)
        _levelEmoji.value = LevelGenerator.getLevelEmoji(config)
    }

    fun saveProgress() {
        _playerProgress.value?.let { repository.saveProgress(it) }
    }

    fun onLevelComplete(reward: Int, isVictory: Boolean, gateDamage: Int = 0) {
        val progress = _playerProgress.value ?: return

        if (isVictory) {
            progress.addCurrency(reward)
            progress.advanceLevel()
        }

        // Apply gate damage from tower defense
        if (gateDamage > 0) {
            progress.applyGateDamage(gateDamage)
        }

        repository.saveProgress(progress)
        _playerProgress.value = progress
        updateNextLevel()
        generateShopItems()
    }

    fun repairGate(amount: Int): Boolean {
        val progress = _playerProgress.value ?: return false
        val cost = progress.getRepairCost(amount)

        if (progress.repairGate(amount, cost)) {
            repository.saveProgress(progress)
            _playerProgress.value = progress
            generateShopItems()
            return true
        }
        return false
    }

    fun upgradeGate(): Boolean {
        val progress = _playerProgress.value ?: return false

        if (progress.upgradeGate()) {
            repository.saveProgress(progress)
            _playerProgress.value = progress
            generateShopItems()
            return true
        }
        return false
    }

    fun buyPerk(perk: PerkType): Boolean {
        val progress = _playerProgress.value ?: return false

        if (progress.buyPerk(perk)) {
            repository.saveProgress(progress)
            _playerProgress.value = progress
            generateShopItems()
            return true
        }
        return false
    }

    fun activatePerk(perk: PerkType): Boolean {
        val progress = _playerProgress.value ?: return false

        if (progress.activatePerk(perk)) {
            repository.saveProgress(progress)
            _playerProgress.value = progress
            return true
        }
        return false
    }

    private fun generateShopItems() {
        val progress = _playerProgress.value ?: return
        val items = mutableListOf<ShopItem>()

        // Gate repair
        val maxRepair = progress.gateMaterial.baseDurability - progress.gateDurability
        if (maxRepair > 0) {
            val repairAmount = minOf(10, maxRepair)
            items.add(ShopItem(
                id = "repair_10",
                name = "Repair Gate",
                description = "Restore $repairAmount durability",
                emoji = "🔧",
                cost = progress.getRepairCost(repairAmount),
                type = ShopItemType.REPAIR,
                value = repairAmount
            ))
        }

        // Gate upgrade
        if (progress.gateMaterial != GateMaterial.DIAMOND) {
            val nextMaterial = when (progress.gateMaterial) {
                GateMaterial.WOOD -> GateMaterial.STONE
                GateMaterial.STONE -> GateMaterial.IRON
                GateMaterial.IRON -> GateMaterial.STEEL
                GateMaterial.STEEL -> GateMaterial.DIAMOND
                GateMaterial.DIAMOND -> null
            }
            nextMaterial?.let {
                items.add(ShopItem(
                    id = "upgrade_gate",
                    name = "Upgrade to ${it.displayName}",
                    description = "Max durability: ${it.baseDurability}",
                    emoji = it.emoji,
                    cost = progress.getGateUpgradeCost(),
                    type = ShopItemType.UPGRADE
                ))
            }
        }

        // Perks
        for (perk in PerkType.entries) {
            items.add(ShopItem(
                id = "perk_${perk.name}",
                name = perk.displayName,
                description = perk.description,
                emoji = perk.emoji,
                cost = perk.cost,
                type = ShopItemType.PERK,
                perkType = perk,
                owned = progress.ownedPerks[perk] ?: 0
            ))
        }

        _shopItems.value = items
    }

    fun resetProgress() {
        repository.resetProgress()
        loadProgress()
    }
}

data class ShopItem(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val cost: Int,
    val type: ShopItemType,
    val value: Int = 0,
    val perkType: PerkType? = null,
    val owned: Int = 0
)

enum class ShopItemType {
    REPAIR,
    UPGRADE,
    PERK
}
