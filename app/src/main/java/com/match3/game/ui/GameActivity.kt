package com.match3.game.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.match3.game.databinding.ActivityGameBinding
import com.match3.game.domain.model.*
import com.match3.game.domain.progression.LevelGenerator
import com.match3.game.ui.viewmodel.GameViewModel
import com.match3.game.ui.views.BoardView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameActivity : AppCompatActivity(), BoardView.BoardInteractionListener {

    private lateinit var binding: ActivityGameBinding
    private val viewModel: GameViewModel by viewModels()

    private var levelNumber: Int = 1
    private var gameMode: GameMode = GameMode.SCORE_ACCUMULATION
    private var isAnimating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        levelNumber = intent.getIntExtra(MainActivity.EXTRA_LEVEL_NUMBER, 1)
        val modeName = intent.getStringExtra(MainActivity.EXTRA_GAME_MODE)
        gameMode = modeName?.let { GameMode.valueOf(it) } ?: GameMode.SCORE_ACCUMULATION

        setupViews()
        setupObservers()
        initGame()
    }

    private fun setupViews() {
        binding.boardView.listener = this

        binding.backButton.setOnClickListener {
            finish()
        }

        binding.continueButton.setOnClickListener {
            returnResult()
        }

        // Show/hide mode-specific UI
        when (gameMode) {
            GameMode.SCORE_ACCUMULATION -> {
                binding.multiplierText.visibility = View.VISIBLE
                binding.specialCellsText.visibility = View.GONE
            }
            GameMode.CLEAR_SPECIAL_CELLS -> {
                binding.multiplierText.visibility = View.GONE
                binding.specialCellsText.visibility = View.VISIBLE
            }
            else -> {}
        }
    }

    private fun setupObservers() {
        viewModel.board.observe(this) { board ->
            binding.boardView.board = board
        }

        viewModel.score.observe(this) { score ->
            binding.scoreText.text = "Score: $score"
        }

        viewModel.turnsRemaining.observe(this) { turns ->
            binding.turnsText.text = turns.toString()
        }

        viewModel.multiplier.observe(this) { multiplier ->
            if (multiplier > 1f) {
                binding.multiplierText.text = "×${String.format("%.1f", multiplier)}"
                binding.multiplierText.visibility = View.VISIBLE
            }
        }

        viewModel.targetScore.observe(this) { target ->
            binding.targetText.text = "Target: $target"
        }

        viewModel.specialCellsRemaining.observe(this) { remaining ->
            binding.specialCellsText.text = "❄️ Remaining: $remaining"
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
        val config = LevelGenerator.generateLevel(levelNumber)
        viewModel.initGame(config)
    }

    private fun processGameEvents(events: List<GameEvent>) {
        if (events.isEmpty()) return

        lifecycleScope.launch {
            isAnimating = true

            for (event in events) {
                when (event) {
                    is GameEvent.BlocksSwapped -> {
                        // Swap animation handled by BoardView
                    }
                    is GameEvent.BlocksMatched -> {
                        // Flash effect
                    }
                    is GameEvent.BlocksDestroyed -> {
                        binding.boardView.animateDestroy(event.positions) {}
                        delay(150)
                    }
                    is GameEvent.BlocksFell -> {
                        binding.boardView.animateFall(event.movements) {}
                        delay(100)
                    }
                    is GameEvent.BlocksSpawned -> {
                        binding.boardView.animateSpawn(event.positions) {}
                        delay(150)
                    }
                    is GameEvent.SpecialCreated -> {
                        binding.boardView.animateSpecialCreated(event.position) {}
                        delay(200)
                    }
                    is GameEvent.ScoreGained -> {
                        binding.boardView.showScorePopup(event.position, event.points, event.multiplier)
                    }
                    is GameEvent.RocketFired -> {
                        binding.boardView.animateRocket(event.position, event.isHorizontal) {}
                        delay(200)
                    }
                    is GameEvent.BombExploded -> {
                        binding.boardView.animateDestroy(event.clearedPositions) {}
                        delay(200)
                    }
                    is GameEvent.PropellerFlew -> {
                        binding.boardView.animatePropeller(event.from, event.to) {}
                        delay(300)
                    }
                    is GameEvent.DiscoActivated -> {
                        binding.boardView.animateDestroy(event.clearedPositions) {}
                        delay(300)
                    }
                    is GameEvent.CascadeStarted -> {
                        // Could show cascade indicator
                    }
                    is GameEvent.CascadeEnded -> {
                        // Cascade complete
                    }
                    is GameEvent.BoardStabilized -> {
                        // Update board view
                        binding.boardView.board = viewModel.getBoard()
                    }
                    else -> {}
                }
            }

            // Final board update
            binding.boardView.board = viewModel.getBoard()
            isAnimating = false
        }
    }

    private fun showGameOverScreen() {
        val isVictory = viewModel.isVictory.value ?: false
        val score = viewModel.score.value ?: 0
        val reward = viewModel.walletReward.value ?: 0

        binding.gameOverTitle.text = if (isVictory) "🎉 Victory!" else "😢 Defeat"
        binding.finalScoreText.text = "Score: $score"
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
        val intent = Intent().apply {
            putExtra(MainActivity.RESULT_REWARD, viewModel.walletReward.value ?: 0)
            putExtra(MainActivity.RESULT_VICTORY, viewModel.isVictory.value ?: false)
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
