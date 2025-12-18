package com.match3.game.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.match3.game.databinding.ActivityGameBinding
import com.match3.game.data.GameRepository
import com.match3.game.domain.engine.Board
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
    private var pendingSwap: Pair<Position, Position>? = null

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
            // Only update board from observer when not animating
            // During animation, board updates come from event snapshots
            if (!isAnimating) {
                binding.boardView.board = board
            }
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

    private fun processGameEvents(events: List<GameEventWithState>) {
        if (events.isEmpty()) {
            // No events means swap was invalid - animate snap back
            pendingSwap?.let { (pos1, pos2) ->
                binding.boardView.animateInvalidSwap(pos1, pos2)
            }
            pendingSwap = null
            return
        }

        lifecycleScope.launch {
            isAnimating = true
            pendingSwap = null
            
            // Track current score for incremental updates
            var displayedScore = binding.scoreText.text.toString().replace("Score: ", "").toIntOrNull() ?: 0
            
            // Group events for batch processing
            var i = 0
            while (i < events.size) {
                val eventWithState = events[i]
                val event = eventWithState.event
                val boardAfter = eventWithState.boardAfter
                
                when (event) {
                    is GameEvent.BlocksSwapped -> {
                        // Swap already animated by drag, just update board
                        binding.boardView.board = boardAfter
                        delay(50)
                    }
                    is GameEvent.BlocksMatched -> {
                        // Collect all consecutive BlocksMatched events to highlight simultaneously
                        val allMatched = mutableSetOf<Position>()
                        var j = i
                        while (j < events.size && events[j].event is GameEvent.BlocksMatched) {
                            allMatched.addAll((events[j].event as GameEvent.BlocksMatched).positions)
                            j++
                        }
                        
                        // Quick blink effect - turn gray briefly then disappear
                        binding.boardView.animateMatchedBlink(allMatched) {}
                        delay(250) // Fast blink
                        
                        // Skip to the matched events we processed
                        i = j - 1
                    }
                    is GameEvent.SpecialForming -> {
                        // Special forming happens at same time as destruction
                        // Just note where special will appear
                    }
                    is GameEvent.BlocksDestroyed -> {
                        // Destruction animation - blocks fade out quickly
                        binding.boardView.animateDestroy(event.positions) {}
                        delay(150)
                        // Update board to state AFTER destruction
                        binding.boardView.board = boardAfter
                    }
                    is GameEvent.SpecialCreated -> {
                        // Special appears immediately after destruction
                        binding.boardView.board = boardAfter
                        binding.boardView.animateSpecialCreated(event.position) {}
                        delay(200)
                    }
                    is GameEvent.BlocksFell -> {
                        // Update board FIRST, then animate blocks falling INTO their new positions
                        binding.boardView.board = boardAfter
                        binding.boardView.animateFallDown(event.movements) {}
                        delay(200)
                    }
                    is GameEvent.BlocksSpawned -> {
                        // Update board to show new blocks, then animate them dropping in
                        binding.boardView.board = boardAfter
                        binding.boardView.animateSpawn(event.positions) {}
                        delay(150)
                    }
                    is GameEvent.ScoreGained -> {
                        // Update score incrementally
                        displayedScore += event.points
                        binding.scoreText.text = "Score: $displayedScore"
                        binding.boardView.showScorePopup(event.position, event.points, event.multiplier)
                        // No delay - score popups are non-blocking
                    }
                    is GameEvent.MultiplierIncreased -> {
                        // Show pulsating multiplier animation
                        binding.multiplierText.text = "×${String.format("%.1f", event.newMultiplier)}"
                        binding.multiplierText.visibility = View.VISIBLE
                        binding.multiplierText.animate()
                            .scaleX(1.5f).scaleY(1.5f)
                            .setDuration(100)
                            .withEndAction {
                                binding.multiplierText.animate()
                                    .scaleX(1f).scaleY(1f)
                                    .setDuration(100)
                                    .start()
                            }
                            .start()
                    }
                    is GameEvent.RocketFired -> {
                        binding.boardView.animateRocket(event.position, event.isHorizontal) {}
                        delay(200)
                        binding.boardView.board = boardAfter
                    }
                    is GameEvent.BombExploded -> {
                        binding.boardView.animateExplosion(event.position, event.clearedPositions) {}
                        delay(200)
                        binding.boardView.board = boardAfter
                    }
                    is GameEvent.PropellerFlew -> {
                        binding.boardView.animatePropeller(event.from, event.to) {}
                        delay(300)
                        binding.boardView.board = boardAfter
                    }
                    is GameEvent.PropellerCarrying -> {
                        binding.boardView.animatePropellerCarrying(event.from, event.to, event.carryingType) {}
                        delay(400)
                        binding.boardView.board = boardAfter
                    }
                    is GameEvent.DiscoActivated -> {
                        binding.boardView.animateDisco(event.position, event.color, event.clearedPositions) {}
                        delay(300)
                        binding.boardView.board = boardAfter
                    }
                    is GameEvent.ComboActivated -> {
                        binding.boardView.showComboText(event.type1, event.type2, event.position)
                        delay(150)
                    }
                    is GameEvent.CascadeStarted -> {
                        // Brief pause between cascade levels
                        delay(50)
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
                        binding.boardView.board = boardAfter
                    }
                    is GameEvent.GameWon, is GameEvent.GameLost -> {
                        // Brief delay before showing game over
                        delay(300)
                    }
                    else -> {}
                }
                i++
            }

            // Final sync with actual current board state
            viewModel.syncFullState()
            viewModel.getBoard()?.let { binding.boardView.board = it }
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

        // Track pending swap for invalid swap animation
        pendingSwap = pos1 to pos2
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
