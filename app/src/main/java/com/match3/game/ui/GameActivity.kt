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

        // Show/hide mode-specific UI - multiplier hidden by default, shown when increased
        when (gameMode) {
            GameMode.SCORE_ACCUMULATION -> {
                binding.multiplierText.visibility = View.GONE // Hidden until multiplier increases
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
            // Only update if not animating (to avoid overwriting incremental updates)
            if (!isAnimating) {
                binding.scoreText.text = "Score: $score"
            }
        }

        viewModel.turnsRemaining.observe(this) { turns ->
            binding.turnsText.text = turns.toString()
        }

        // Multiplier is now handled via MultiplierIncreased event for pulsating animation

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
        val config = LevelGenerator.generateLevel(levelNumber, this)
        viewModel.initGame(config)
    }

    private fun processGameEvents(events: List<GameEvent>) {
        if (events.isEmpty()) return

        lifecycleScope.launch {
            isAnimating = true
            
            // Track current score for incremental updates
            var displayedScore = binding.scoreText.text.toString().replace("Score: ", "").toIntOrNull() ?: 0

            for (event in events) {
                when (event) {
                    is GameEvent.BlocksSwapped -> {
                        binding.boardView.animateSwap(event.pos1, event.pos2) {}
                        delay(250)
                        // Update board after swap
                        binding.boardView.board = viewModel.getBoard()
                    }
                    is GameEvent.BlocksMatched -> {
                        // Highlight matched blocks briefly
                        binding.boardView.highlightPositions(event.positions, event.color)
                        delay(150)
                    }
                    is GameEvent.SpecialForming -> {
                        // Show special block forming animation - blocks blink special color
                        binding.boardView.animateSpecialForming(event.positions, event.type, event.creationPos) {}
                        delay(400)
                    }
                    is GameEvent.BlocksDestroyed -> {
                        binding.boardView.animateDestroy(event.positions) {}
                        delay(350)
                        // Update board after destruction
                        binding.boardView.board = viewModel.getBoard()
                    }
                    is GameEvent.BlocksFell -> {
                        // Update board BEFORE fall animation so we animate from old to new
                        binding.boardView.board = viewModel.getBoard()
                        binding.boardView.animateFall(event.movements) {}
                        delay(300)
                    }
                    is GameEvent.BlocksSpawned -> {
                        binding.boardView.board = viewModel.getBoard()
                        binding.boardView.animateSpawn(event.positions) {}
                        delay(300)
                    }
                    is GameEvent.SpecialCreated -> {
                        binding.boardView.board = viewModel.getBoard()
                        binding.boardView.animateSpecialCreated(event.position) {}
                        delay(400)
                    }
                    is GameEvent.ScoreGained -> {
                        // Update score incrementally
                        displayedScore += event.points
                        binding.scoreText.text = "Score: $displayedScore"
                        binding.boardView.showScorePopup(event.position, event.points, event.multiplier)
                        delay(50) // Small delay between score popups
                    }
                    is GameEvent.MultiplierIncreased -> {
                        // Show pulsating multiplier animation
                        binding.multiplierText.text = "×${String.format("%.1f", event.newMultiplier)}"
                        binding.multiplierText.visibility = View.VISIBLE
                        binding.multiplierText.animate()
                            .scaleX(1.5f).scaleY(1.5f)
                            .setDuration(150)
                            .withEndAction {
                                binding.multiplierText.animate()
                                    .scaleX(1f).scaleY(1f)
                                    .setDuration(150)
                                    .start()
                            }
                            .start()
                        delay(100)
                    }
                    is GameEvent.RocketFired -> {
                        binding.boardView.animateRocket(event.position, event.isHorizontal) {}
                        delay(400)
                        binding.boardView.board = viewModel.getBoard()
                    }
                    is GameEvent.BombExploded -> {
                        binding.boardView.animateExplosion(event.position, event.clearedPositions) {}
                        delay(400)
                        binding.boardView.board = viewModel.getBoard()
                    }
                    is GameEvent.PropellerFlew -> {
                        binding.boardView.animatePropeller(event.from, event.to) {}
                        delay(500)
                        binding.boardView.board = viewModel.getBoard()
                    }
                    is GameEvent.PropellerCarrying -> {
                        binding.boardView.animatePropellerCarrying(event.from, event.to, event.carryingType) {}
                        delay(600)
                        binding.boardView.board = viewModel.getBoard()
                    }
                    is GameEvent.DiscoActivated -> {
                        binding.boardView.animateDisco(event.position, event.color, event.clearedPositions) {}
                        delay(500)
                        binding.boardView.board = viewModel.getBoard()
                    }
                    is GameEvent.ComboActivated -> {
                        // Show combo text
                        binding.boardView.showComboText(event.type1, event.type2, event.position)
                        delay(200)
                    }
                    is GameEvent.CascadeStarted -> {
                        delay(100)
                    }
                    is GameEvent.CascadeEnded -> {
                        // Cascade complete
                    }
                    is GameEvent.TurnEnded -> {
                        // Hide multiplier when turn ends
                        binding.multiplierText.visibility = View.GONE
                        binding.turnsText.text = event.turnsRemaining.toString()
                    }
                    is GameEvent.BoardStabilized -> {
                        // Final board sync
                        binding.boardView.board = viewModel.getBoard()
                        delay(100)
                    }
                    is GameEvent.GameWon, is GameEvent.GameLost -> {
                        // Wait for animations to complete before showing game over
                        delay(500)
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
        // Wait for animations to complete before showing game over
        lifecycleScope.launch {
            // Wait until not animating
            while (isAnimating) {
                delay(100)
            }
            
            val isVictory = viewModel.isVictory.value ?: false
            val score = viewModel.score.value ?: 0
            val reward = viewModel.walletReward.value ?: 0
            val turnsRemaining = viewModel.turnsRemaining.value ?: 0
            val turnsBonus = if (isVictory) turnsRemaining * 5 else 0

            binding.gameOverTitle.text = if (isVictory) "🎉 Victory!" else "😢 Defeat"
            binding.finalScoreText.text = "Score: $score"
            
            // Show breakdown of reward
            val rewardText = StringBuilder()
            if (isVictory && turnsBonus > 0) {
                rewardText.append("⏱️ Remaining moves bonus: +$turnsBonus\n")
            }
            if (reward > 0) {
                rewardText.append("💰 Total reward: +$reward")
            }
            binding.rewardText.text = rewardText.toString()
            binding.rewardText.visibility = if (rewardText.isNotEmpty()) View.VISIBLE else View.GONE

            binding.gameOverOverlay.visibility = View.VISIBLE
            binding.gameOverOverlay.alpha = 0f
            binding.gameOverOverlay.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
        }
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
