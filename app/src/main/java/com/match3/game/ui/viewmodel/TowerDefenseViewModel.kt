package com.match3.game.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.match3.game.domain.engine.Board
import com.match3.game.domain.engine.Enemy
import com.match3.game.domain.engine.Gate
import com.match3.game.domain.engine.TowerDefenseEngine
import com.match3.game.domain.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TowerDefenseViewModel : ViewModel() {

    private var engine: TowerDefenseEngine? = null

    private val _playerBoard = MutableLiveData<Board>()
    val playerBoard: LiveData<Board> = _playerBoard

    private val _enemies = MutableLiveData<List<Enemy>>()
    val enemies: LiveData<List<Enemy>> = _enemies

    private val _gate = MutableLiveData<Gate>()
    val gate: LiveData<Gate> = _gate

    private val _score = MutableLiveData(0)
    val score: LiveData<Int> = _score

    private val _turnsRemaining = MutableLiveData(0)
    val turnsRemaining: LiveData<Int> = _turnsRemaining

    private val _currentTurn = MutableLiveData(0)
    val currentTurn: LiveData<Int> = _currentTurn

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

    private val _gateDamageThisTurn = MutableLiveData(0)
    val gateDamageThisTurn: LiveData<Int> = _gateDamageThisTurn

    fun initGame(config: LevelConfig, initialGate: Gate) {
        engine = TowerDefenseEngine(config, initialGate)
        updateState()
    }

    fun swap(pos1: Position, pos2: Position) {
        if (_isProcessing.value == true) return

        viewModelScope.launch {
            _isProcessing.value = true

            val e = engine ?: return@launch
            val success = e.swap(pos1, pos2)

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

            val e = engine ?: return@launch
            val success = e.tapBlock(pos)

            if (success) {
                processEvents()
            }

            _isProcessing.value = false
        }
    }

    private suspend fun processEvents() {
        val e = engine ?: return
        val events = e.getEvents()

        _gameEvents.value = events
        updateState()

        delay(50)
    }

    private fun updateState() {
        val e = engine ?: return

        _playerBoard.value = e.playerBoard
        _enemies.value = e.enemies.toList()
        _gate.value = e.gate
        _score.value = e.score
        _turnsRemaining.value = e.turnsRemaining
        _currentTurn.value = e.currentTurn
        _walletReward.value = e.getWalletReward()
        _gateDamageThisTurn.value = e.getGateDamagePerTurn()
        // Set isVictory BEFORE isGameOver so observers see correct victory state
        _isVictory.value = e.isVictory
        _isGameOver.value = e.isGameOver
    }

    fun getPlayerBoard(): Board? = engine?.playerBoard

    fun getFinalGateDurability(): Int = engine?.gate?.durability ?: 0
}
