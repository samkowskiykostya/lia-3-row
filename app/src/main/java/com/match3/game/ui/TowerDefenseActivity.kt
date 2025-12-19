package com.match3.game.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.match3.game.databinding.ActivityTowerDefenseBinding
import com.match3.game.domain.engine.Board
import com.match3.game.domain.model.*
import com.match3.game.domain.progression.LevelGenerator
import com.match3.game.data.GameRepository
import com.match3.game.ui.viewmodel.TowerDefenseViewModel
import com.match3.game.ui.views.BoardView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TowerDefenseActivity : AppCompatActivity(), BoardView.BoardInteractionListener {

    private lateinit var binding: ActivityTowerDefenseBinding
    private val viewModel: TowerDefenseViewModel by viewModels()

    private var levelNumber: Int = 1
    private var isAnimating = false
    private var initialGateDurability = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTowerDefenseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        levelNumber = intent.getIntExtra(MainActivity.EXTRA_LEVEL_NUMBER, 1)

        setupViews()
        setupObservers()
        initGame()
    }

    private fun setupViews() {
        binding.playerBoardView.listener = this

        binding.backButton.setOnClickListener {
            finish()
        }

        binding.continueButton.setOnClickListener {
            returnResult()
        }
    }

    private fun setupObservers() {
        viewModel.playerBoard.observe(this) { board ->
            // Only update board from observer when not animating
            if (!isAnimating) {
                binding.playerBoardView.board = board
            }
        }

        viewModel.enemies.observe(this) { enemies ->
            binding.enemyFieldView.enemies = enemies
        }

        viewModel.gate.observe(this) { gate ->
            binding.enemyFieldView.gate = gate
        }

        viewModel.score.observe(this) { score ->
            binding.scoreText.text = "Score: $score"
        }

        viewModel.turnsRemaining.observe(this) { turns ->
            binding.turnsText.text = turns.toString()
        }

        viewModel.gateDamageThisTurn.observe(this) { damage ->
            if (damage > 0) {
                binding.damageWarningText.text = "⚠️ Incoming: $damage damage"
                binding.damageWarningText.visibility = View.VISIBLE
            } else {
                binding.damageWarningText.visibility = View.GONE
            }
        }

        viewModel.gameEvents.observe(this) { events ->
            processGameEvents(events)
        }

        viewModel.isGameOver.observe(this) { isOver ->
            if (isOver) {
                showGameOverScreen()
            }
        }
    }

    private fun initGame() {
        val config = LevelGenerator.generateLevel(levelNumber, this)
        val repository = GameRepository(this)
        val progress = repository.loadProgress()
        val gate = progress.getGate()
        initialGateDurability = gate.durability

        viewModel.initGame(config, gate)
    }

    private fun processGameEvents(events: List<GameEventWithState>) {
        if (events.isEmpty()) return

        lifecycleScope.launch {
            isAnimating = true

            for (eventWithState in events) {
                val event = eventWithState.event
                val boardAfter = eventWithState.boardAfter
                
                when (event) {
                    is GameEvent.BlocksDestroyed -> {
                        binding.playerBoardView.animateDestroy(event.positions) {}
                        delay(150)
                        binding.playerBoardView.board = boardAfter
                    }
                    is GameEvent.BlocksFell -> {
                        binding.playerBoardView.animateFall(event.movements) {}
                        delay(100)
                        binding.playerBoardView.board = boardAfter
                    }
                    is GameEvent.BlocksSpawned -> {
                        binding.playerBoardView.board = boardAfter
                        binding.playerBoardView.animateSpawn(event.positions) {}
                        delay(150)
                    }
                    is GameEvent.SpecialCreated -> {
                        binding.playerBoardView.board = boardAfter
                        binding.playerBoardView.animateSpecialCreated(event.position) {}
                        delay(200)
                    }
                    is GameEvent.ProjectileFired -> {
                        binding.enemyFieldView.animateProjectile(event.fromCol) {}
                        delay(100)
                    }
                    is GameEvent.EnemySpawned -> {
                        // Update enemies list immediately when spawned
                        viewModel.enemies.value?.let { binding.enemyFieldView.enemies = it }
                        binding.enemyFieldView.animateEnemySpawn(event.enemyId)
                        delay(100)
                    }
                    is GameEvent.EnemyDamaged -> {
                        // Update enemies list immediately when damaged
                        viewModel.enemies.value?.let { binding.enemyFieldView.enemies = it }
                        binding.enemyFieldView.animateEnemyDamage(event.enemyId)
                        delay(100)
                    }
                    is GameEvent.EnemyDestroyed -> {
                        // Update enemies list immediately when destroyed
                        viewModel.enemies.value?.let { binding.enemyFieldView.enemies = it }
                        binding.enemyFieldView.animateEnemyDeath(event.enemyId)
                        delay(200)
                    }
                    is GameEvent.EnemyMoved -> {
                        // Update enemies list when moved
                        viewModel.enemies.value?.let { binding.enemyFieldView.enemies = it }
                        binding.enemyFieldView.invalidate()
                        delay(50)
                    }
                    is GameEvent.GateDamaged -> {
                        viewModel.gate.value?.let { binding.enemyFieldView.gate = it }
                        binding.enemyFieldView.animateGateDamage()
                        delay(100)
                    }
                    is GameEvent.RocketFired -> {
                        binding.playerBoardView.animateRocket(event.position, event.isHorizontal) {}
                        delay(200)
                        binding.playerBoardView.board = boardAfter
                    }
                    is GameEvent.BombExploded -> {
                        binding.playerBoardView.animateDestroy(event.clearedPositions) {}
                        delay(200)
                        binding.playerBoardView.board = boardAfter
                    }
                    is GameEvent.PropellerFlew -> {
                        binding.playerBoardView.animatePropeller(event.from, event.to) {}
                        delay(300)
                        binding.playerBoardView.board = boardAfter
                    }
                    is GameEvent.DiscoActivated -> {
                        binding.playerBoardView.animateDestroy(event.clearedPositions) {}
                        delay(300)
                        binding.playerBoardView.board = boardAfter
                    }
                    is GameEvent.BoardStabilized -> {
                        binding.playerBoardView.board = boardAfter
                        binding.enemyFieldView.invalidate()
                    }
                    is GameEvent.TurnEnded -> {
                        // Update enemy positions
                        binding.enemyFieldView.invalidate()
                        delay(200)
                    }
                    else -> {}
                }
            }

            viewModel.getPlayerBoard()?.let { binding.playerBoardView.board = it }
            binding.enemyFieldView.invalidate()
            isAnimating = false
        }
    }

    private fun showGameOverScreen() {
        val isVictory = viewModel.isVictory.value ?: false
        val score = viewModel.score.value ?: 0
        val reward = viewModel.walletReward.value ?: 0
        val finalGateDurability = viewModel.getFinalGateDurability()
        val gate = viewModel.gate.value

        binding.gameOverTitle.text = if (isVictory) "⚔️ Victory!" else "💀 Defeat"
        binding.finalScoreText.text = "Score: $score"
        binding.gateStatusText.text = "🚪 Gate: $finalGateDurability/${gate?.maxDurability ?: 0}"
        binding.gateStatusText.setTextColor(
            if (finalGateDurability > 0) getColor(com.match3.game.R.color.game_green)
            else getColor(com.match3.game.R.color.game_accent)
        )
        binding.rewardText.text = if (reward > 0) "💰 +$reward" else ""
        binding.rewardText.visibility = if (reward > 0) View.VISIBLE else View.GONE

        binding.gameOverOverlay.visibility = View.VISIBLE
        binding.gameOverOverlay.alpha = 0f
        binding.gameOverOverlay.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    private fun returnResult() {
        val finalGateDurability = viewModel.getFinalGateDurability()
        val gateDamage = initialGateDurability - finalGateDurability

        val intent = Intent().apply {
            putExtra(MainActivity.RESULT_REWARD, viewModel.walletReward.value ?: 0)
            putExtra(MainActivity.RESULT_VICTORY, viewModel.isVictory.value ?: false)
            putExtra(MainActivity.RESULT_GATE_DAMAGE, gateDamage.coerceAtLeast(0))
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    override fun onSwap(pos1: Position, pos2: Position) {
        if (isAnimating) return
        if (viewModel.isProcessing.value == true) return

        viewModel.swap(pos1, pos2)
    }

    override fun onTap(pos: Position) {
        if (isAnimating) return
        if (viewModel.isProcessing.value == true) return

        viewModel.tapBlock(pos)
    }

    override fun onBackPressed() {
        if (binding.gameOverOverlay.visibility == View.VISIBLE) {
            returnResult()
        } else {
            super.onBackPressed()
        }
    }
}
