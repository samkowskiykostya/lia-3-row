package com.match3.game.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.match3.game.databinding.ActivityMainBinding
import com.match3.game.domain.model.GameMode
import com.match3.game.ui.adapter.ShopAdapter
import com.match3.game.ui.viewmodel.MainViewModel
import com.match3.game.ui.viewmodel.ShopItem
import com.match3.game.ui.viewmodel.ShopItemType

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var shopAdapter: ShopAdapter

    companion object {
        const val EXTRA_LEVEL_NUMBER = "level_number"
        const val EXTRA_GAME_MODE = "game_mode"
        const val RESULT_REWARD = "reward"
        const val RESULT_VICTORY = "victory"
        const val RESULT_GATE_DAMAGE = "gate_damage"
        const val REQUEST_GAME = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupShopRecyclerView()
        setupObservers()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadProgress()
    }

    private fun setupShopRecyclerView() {
        shopAdapter = ShopAdapter { item ->
            handleShopPurchase(item)
        }

        binding.shopRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = shopAdapter
        }
    }

    private fun setupObservers() {
        viewModel.playerProgress.observe(this) { progress ->
            binding.walletText.text = "💰 ${progress.wallet}"
            binding.levelNumberText.text = "Level ${progress.currentLevel}"

            val material = progress.gateMaterial
            binding.gateMaterialText.text = "${material.emoji} ${material.displayName}"
            binding.gateDurabilityText.text = "${progress.gateDurability}/${material.baseDurability}"

            val healthPercent = (progress.gateDurability.toFloat() / material.baseDurability * 100).toInt()
            binding.gateHealthBar.progress = healthPercent

            binding.playButton.text = "▶️ Start Level ${progress.currentLevel}"

            shopAdapter.playerWallet = progress.wallet
        }

        viewModel.nextLevelConfig.observe(this) { config ->
            val modeEmoji = when (config.mode) {
                GameMode.SCORE_ACCUMULATION -> "🎯"
                GameMode.CLEAR_SPECIAL_CELLS -> "❄️"
                GameMode.TOWER_DEFENSE -> "⚔️"
            }
            val modeName = when (config.mode) {
                GameMode.SCORE_ACCUMULATION -> "Score Mode"
                GameMode.CLEAR_SPECIAL_CELLS -> "Clear Mode"
                GameMode.TOWER_DEFENSE -> "Tower Defense"
            }
            binding.levelModeText.text = "$modeEmoji $modeName"
        }

        viewModel.levelDescription.observe(this) { description ->
            binding.levelDescriptionText.text = description
        }

        viewModel.shopItems.observe(this) { items ->
            shopAdapter.submitList(items)
        }
    }

    private fun setupClickListeners() {
        binding.playButton.setOnClickListener {
            startGame()
        }
    }

    private fun handleShopPurchase(item: ShopItem) {
        val success = when (item.type) {
            ShopItemType.REPAIR -> viewModel.repairGate(item.value)
            ShopItemType.UPGRADE -> viewModel.upgradeGate()
            ShopItemType.PERK -> viewModel.buyPerk(item.perkType!!)
        }

        if (success) {
            Toast.makeText(this, "✅ Purchased ${item.name}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "❌ Not enough coins", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startGame() {
        val config = viewModel.nextLevelConfig.value ?: return
        val progress = viewModel.playerProgress.value ?: return

        val intent = when (config.mode) {
            GameMode.TOWER_DEFENSE -> {
                Intent(this, TowerDefenseActivity::class.java).apply {
                    putExtra(EXTRA_LEVEL_NUMBER, config.levelNumber)
                }
            }
            else -> {
                Intent(this, GameActivity::class.java).apply {
                    putExtra(EXTRA_LEVEL_NUMBER, config.levelNumber)
                    putExtra(EXTRA_GAME_MODE, config.mode.name)
                }
            }
        }

        startActivityForResult(intent, REQUEST_GAME)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_GAME && resultCode == RESULT_OK) {
            val reward = data?.getIntExtra(RESULT_REWARD, 0) ?: 0
            val victory = data?.getBooleanExtra(RESULT_VICTORY, false) ?: false
            val gateDamage = data?.getIntExtra(RESULT_GATE_DAMAGE, 0) ?: 0

            viewModel.onLevelComplete(reward, victory, gateDamage)

            if (victory) {
                Toast.makeText(this, "🎉 Level Complete! +$reward coins", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "😢 Level Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
