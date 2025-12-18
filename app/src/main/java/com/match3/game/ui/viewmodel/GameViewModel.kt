package com.match3.game.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.match3.game.domain.engine.Board
import com.match3.game.domain.engine.GameEngine
import com.match3.game.domain.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {

    private var gameEngine: GameEngine? = null

    private val _board = MutableLiveData<Board>()
    val board: LiveData<Board> = _board

    private val _score = MutableLiveData(0)
    val score: LiveData<Int> = _score

    private val _turnsRemaining = MutableLiveData(0)
    val turnsRemaining: LiveData<Int> = _turnsRemaining

    private val _multiplier = MutableLiveData(1.0f)
    val multiplier: LiveData<Float> = _multiplier

    private val _targetScore = MutableLiveData(0)
    val targetScore: LiveData<Int> = _targetScore

    private val _specialCellsRemaining = MutableLiveData(0)
    val specialCellsRemaining: LiveData<Int> = _specialCellsRemaining

    private val _gameMode = MutableLiveData<GameMode>()
    val gameMode: LiveData<GameMode> = _gameMode

    private val _gameEvents = MutableLiveData<List<GameEventWithState>>()
    val gameEvents: LiveData<List<GameEventWithState>> = _gameEvents

    private val _isGameOver = MutableLiveData(false)
    val isGameOver: LiveData<Boolean> = _isGameOver

    private val _isVictory = MutableLiveData(false)
    val isVictory: LiveData<Boolean> = _isVictory

    private val _walletReward = MutableLiveData(0)
    val walletReward: LiveData<Int> = _walletReward

    private val _isProcessing = MutableLiveData(false)
    val isProcessing: LiveData<Boolean> = _isProcessing

    fun initGame(config: LevelConfig) {
        gameEngine = GameEngine(config)
        _gameMode.value = config.mode
        _targetScore.value = config.targetScore
        updateState()
    }

    fun swap(pos1: Position, pos2: Position) {
        if (_isProcessing.value == true) return

        viewModelScope.launch {
            _isProcessing.value = true

            val engine = gameEngine ?: return@launch
            val success = engine.swap(pos1, pos2)

            if (success) {
                processEvents()
            }

            _isProcessing.value = false
        }
    }

    fun tapBlock(pos: Position) {
        if (_isProcessing.value == true) return

        viewModelScope.launch {
            _isProcessing.value = true

            val engine = gameEngine ?: return@launch
            val success = engine.tapBlock(pos)

            if (success) {
                processEvents()
            }

            _isProcessing.value = false
        }
    }

    private suspend fun processEvents() {
        val engine = gameEngine ?: return
        val events = engine.getEvents()

        _gameEvents.value = events
        updateState()

        // Small delay to allow animations
        delay(50)
    }

    private fun updateState() {
        val engine = gameEngine ?: return

        _board.value = engine.board
        _score.value = engine.score
        _turnsRemaining.value = engine.turnsRemaining
        _multiplier.value = engine.multiplier
        _specialCellsRemaining.value = engine.board.getSpecialCellsRemaining()
        _isGameOver.value = engine.isGameOver
        _isVictory.value = engine.isVictory
        _walletReward.value = engine.getWalletReward()
    }

    fun getBoard(): Board? = gameEngine?.board
}
