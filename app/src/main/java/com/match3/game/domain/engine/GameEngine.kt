package com.match3.game.domain.engine

import com.match3.game.domain.model.*

class GameEngine(
    private val config: LevelConfig
) {
    val board: Board
    private val rng: SeededRandom
    private val matchFinder: MatchFinder
    private val specialActivator: SpecialActivator
    
    var score: Int = 0
        private set
    var turnsRemaining: Int = config.maxTurns
        private set
    var multiplier: Float = 1.0f
        private set
    var isGameOver: Boolean = false
        private set
    var isVictory: Boolean = false
        private set
    
    private val eventQueue = mutableListOf<GameEventWithState>()
    private val frozenZoneCounters = mutableMapOf<Int, Int>()
    
    init {
        rng = SeededRandom(config.seed)
        board = Board(config.boardWidth, config.boardHeight, rng)
        matchFinder = MatchFinder(board)
        specialActivator = SpecialActivator(board, rng)
        
        // Setup special cells
        board.setupSpecialCells(config.specialCells)
        
        // Initialize frozen zone counters
        for (zone in config.frozenZones) {
            frozenZoneCounters[zone.zoneId] = 0
        }
        
        // Fill board without initial matches
        board.fillEmptyWithoutMatches()
    }
    
    fun getEvents(): List<GameEventWithState> {
        val events = eventQueue.toList()
        eventQueue.clear()
        return events
    }
    
    /** Helper to emit an event with current board snapshot */
    private fun emit(event: GameEvent) {
        eventQueue.add(GameEventWithState(event, board.copy()))
    }
    
    fun canSwap(pos1: Position, pos2: Position): Boolean {
        if (isGameOver) return false
        if (!pos1.isAdjacent(pos2)) return false
        
        val cell1 = board.getCell(pos1) ?: return false
        val cell2 = board.getCell(pos2) ?: return false
        
        if (!cell1.canSwap() || !cell2.canSwap()) return false
        
        return true
    }
    
    fun swap(pos1: Position, pos2: Position): Boolean {
        if (!canSwap(pos1, pos2)) return false
        
        val block1 = board.getBlock(pos1)
        val block2 = board.getBlock(pos2)
        
        // Store special types BEFORE swap for combo detection
        val special1 = block1?.isSpecial() == true
        val special2 = block2?.isSpecial() == true
        val type1 = block1?.specialType ?: SpecialType.NONE
        val type2 = block2?.specialType ?: SpecialType.NONE
        
        // Perform swap
        board.setBlock(pos1, block2)
        board.setBlock(pos2, block1)
        
        emit(GameEvent.BlocksSwapped(pos1, pos2))
        
        // Check for special combos first (use original types, pos2 is the target)
        if (special1 && special2) {
            // Special combo - pos1 is source (dragged from), pos2 is target (dragged to)
            // type1 was at pos1 (now at pos2), type2 was at pos2 (now at pos1)
            val result = specialActivator.activateCombo(
                pos1, type1,  // Original block from pos1
                pos2, type2   // Original block from pos2 (target)
            )
            processActivationResult(result)
            resolveBoard(null)
            consumeTurn()
            return true
        }
        
        // Check if swap creates a match
        val matchesAtPos1 = matchFinder.findMatchesAt(pos1)
        val matchesAtPos2 = matchFinder.findMatchesAt(pos2)
        
        if (matchesAtPos1.isEmpty() && matchesAtPos2.isEmpty()) {
            // No match - check if either block is special (tap activation)
            if (special1 || special2) {
                val specialPos = if (special2) pos1 else pos2
                val otherPos = if (special2) pos2 else pos1
                val result = specialActivator.activateSpecial(specialPos, otherPos)
                processActivationResult(result)
                resolveBoard(null)
                consumeTurn()
                return true
            }
            
            // Swap back
            board.setBlock(pos1, block1)
            board.setBlock(pos2, block2)
            return false
        }
        
        // Process matches - pos2 is where user swiped TO
        resolveBoard(pos2)
        consumeTurn()
        
        return true
    }
    
    fun tapBlock(pos: Position): Boolean {
        if (isGameOver) return false
        
        val block = board.getBlock(pos) ?: return false
        
        if (!block.isSpecial()) return false
        
        val result = specialActivator.activateSpecial(pos)
        if (result.destroyedPositions.isEmpty()) return false
        
        processActivationResult(result)
        resolveBoard(null)
        consumeTurn()
        
        return true
    }
    
    private fun processActivationResult(result: SpecialActivator.ActivationResult) {
        for (e in result.events) emit(e)
        
        // Destroy blocks and damage cells
        for (pos in result.destroyedPositions) {
            val block = board.getBlock(pos)
            if (block != null) {
                addScore(1, pos)
                board.setBlock(pos, null)
                
                // Damage adjacent special cells
                val damaged = board.damageAdjacentCells(pos, block.color)
                for (damagedPos in damaged) {
                    val cell = board.getCell(damagedPos)
                    if (cell != null) {
                        if (cell.type == CellType.NORMAL) {
                            emit(GameEvent.CellCleared(damagedPos, cell.type))
                        } else {
                            emit(GameEvent.CellDamaged(damagedPos, cell.durability))
                        }
                    }
                }
            }
        }
        
        emit(GameEvent.BlocksDestroyed(result.destroyedPositions))
        
        // Process chained activations
        for ((chainPos, chainType) in result.chainedActivations) {
            if (board.getBlock(chainPos) != null) {
                val chainResult = specialActivator.activateSpecial(chainPos)
                processActivationResult(chainResult)
            }
        }
    }
    
    private fun resolveBoard(swappedTo: Position?) {
        var cascadeLevel = 0
        var isFirstPass = true
        
        do {
            // Apply gravity
            val movements = board.applyGravity()
            if (movements.isNotEmpty()) {
                emit(GameEvent.BlocksFell(movements))
            }
            
            // Spawn new blocks
            val spawned = board.spawnNewBlocks()
            if (spawned.isNotEmpty()) {
                emit(GameEvent.BlocksSpawned(spawned))
            }
            
            // Find matches - only pass swappedTo on first pass (user-initiated)
            val matches = if (isFirstPass && swappedTo != null) {
                matchFinder.findAllMatchesWithSwapInfo(swappedTo)
            } else {
                matchFinder.findAllMatches()
            }
            isFirstPass = false
            
            if (matches.isEmpty()) {
                break
            }
            
            cascadeLevel++
            emit(GameEvent.CascadeStarted(cascadeLevel))
            
            // Process each match
            for (match in matches) {
                processMatch(match)
            }
            
        } while (true)
        
        if (cascadeLevel > 0) {
            emit(GameEvent.CascadeEnded(cascadeLevel))
        }
        
        emit(GameEvent.BoardStabilized)
        checkWinCondition()
    }
    
    private fun processMatch(match: Match) {
        emit(GameEvent.BlocksMatched(match.positions, match.color))
        
        // Determine if a special should be created
        val specialType = when (match.type) {
            Match.MatchType.LINE_4 -> {
                val isHorizontal = match.positions.map { it.row }.distinct().size == 1
                if (isHorizontal) SpecialType.ROCKET_HORIZONTAL else SpecialType.ROCKET_VERTICAL
            }
            Match.MatchType.LINE_5 -> SpecialType.DISCO_BALL
            Match.MatchType.SQUARE_2X2 -> SpecialType.PROPELLER
            Match.MatchType.T_SHAPE, Match.MatchType.L_SHAPE -> SpecialType.BOMB
            else -> SpecialType.NONE
        }
        
        val creationPos = match.getCreationPosition()
        
        // Emit special forming event BEFORE destroying blocks (for animation)
        if (specialType != SpecialType.NONE) {
            emit(GameEvent.SpecialForming(match.positions, specialType, creationPos))
        }
        
        // Destroy matched blocks
        for (pos in match.positions) {
            val block = board.getBlock(pos)
            if (block != null) {
                // Check if this block is special and should chain
                if (block.isSpecial() && pos != creationPos) {
                    val result = specialActivator.activateSpecial(pos)
                    processActivationResult(result)
                }
                
                addScore(1, pos)
                board.setBlock(pos, null)
                
                // Damage adjacent special cells
                board.damageAdjacentCells(pos, match.color)
            }
        }
        
        emit(GameEvent.BlocksDestroyed(match.positions))
        
        // Create special block at creation position
        if (specialType != SpecialType.NONE) {
            val specialBlock = Block(color = match.color, specialType = specialType)
            board.setBlock(creationPos, specialBlock)
            emit(GameEvent.SpecialCreated(creationPos, specialType, match.color))
        }
    }
    
    private var turnScore: Int = 0 // Score accumulated this turn for multiplier calculation
    
    private fun addScore(points: Int, pos: Position) {
        val actualPoints = (points * multiplier).toInt()
        score += actualPoints
        turnScore += actualPoints
        emit(GameEvent.ScoreGained(actualPoints, pos, multiplier))
        
        // Update multiplier based on turn score (resets each turn)
        val newMultiplier = 1.0f + (turnScore / 10) * 0.2f
        if (newMultiplier > multiplier) {
            multiplier = newMultiplier
            emit(GameEvent.MultiplierIncreased(multiplier))
        }
        
        // Check win condition immediately when score is gained (for score mode)
        if (config.mode == GameMode.SCORE_ACCUMULATION && score >= config.targetScore && !isGameOver) {
            isGameOver = true
            isVictory = true
            val bonus = (score - config.targetScore).coerceAtLeast(0) + turnsRemaining * 5
            emit(GameEvent.GameWon(score, bonus))
        }
    }
    
    private fun consumeTurn() {
        turnsRemaining--
        // Reset multiplier and turn score for next turn
        multiplier = 1.0f
        turnScore = 0
        emit(GameEvent.TurnEnded(turnsRemaining))
        
        if (turnsRemaining <= 0) {
            checkWinCondition()
        }
    }
    
    private fun checkWinCondition() {
        when (config.mode) {
            GameMode.SCORE_ACCUMULATION -> {
                // Win immediately when target score is reached
                if (score >= config.targetScore && !isGameOver) {
                    isGameOver = true
                    isVictory = true
                    val bonus = (score - config.targetScore).coerceAtLeast(0) + turnsRemaining * 5
                    emit(GameEvent.GameWon(score, bonus))
                } else if (turnsRemaining <= 0 && !isGameOver) {
                    isGameOver = true
                    isVictory = false
                    emit(GameEvent.GameLost("Target score not reached"))
                }
            }
            GameMode.CLEAR_SPECIAL_CELLS -> {
                val remaining = board.getSpecialCellsRemaining()
                if (remaining == 0) {
                    isGameOver = true
                    isVictory = true
                    val bonus = turnsRemaining * 10
                    emit(GameEvent.GameWon(score, bonus))
                } else if (turnsRemaining <= 0) {
                    isGameOver = true
                    isVictory = false
                    emit(GameEvent.GameLost("Special cells remaining: $remaining"))
                }
            }
            GameMode.TOWER_DEFENSE -> {
                // Handled by TowerDefenseEngine
            }
        }
    }
    
    fun getWalletReward(): Int {
        return when (config.mode) {
            GameMode.SCORE_ACCUMULATION -> {
                if (isVictory) (score - config.targetScore).coerceAtLeast(0) else 0
            }
            GameMode.CLEAR_SPECIAL_CELLS -> {
                if (isVictory) score + turnsRemaining * 10 else 0
            }
            GameMode.TOWER_DEFENSE -> score
        }
    }
}
