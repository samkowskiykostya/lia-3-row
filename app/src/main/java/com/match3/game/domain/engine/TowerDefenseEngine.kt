package com.match3.game.domain.engine

import com.match3.game.domain.model.*

data class Enemy(
    val id: Int,
    val type: EnemyType,
    var row: Int,
    val col: Int,
    var hp: Int,
    val maxHp: Int,
    var isAtGate: Boolean = false
) {
    fun getEmoji(): String = when {
        hp <= 0 -> "💀"
        isAtGate -> "😈"
        else -> type.emoji
    }
    
    fun getDamagePerTurn(): Int = when (type) {
        EnemyType.BASIC -> 1
        EnemyType.CONTROLLER -> 2
        EnemyType.SPAWNER -> 1
        EnemyType.BOSS -> 5
    }
}

data class Gate(
    val material: GateMaterial,
    var durability: Int,
    val maxDurability: Int
) {
    fun getEmoji(): String = when {
        durability <= 0 -> "💔"
        durability < maxDurability / 4 -> "🚪⚠️"
        durability < maxDurability / 2 -> "🚪"
        else -> "🚪✨"
    }
    
    fun takeDamage(amount: Int): Int {
        val actualDamage = minOf(amount, durability)
        durability -= actualDamage
        return actualDamage
    }
}

enum class GateMaterial(val displayName: String, val baseDurability: Int, val emoji: String) {
    WOOD("Wood", 10, "🪵"),
    STONE("Stone", 25, "🪨"),
    IRON("Iron", 40, "🔩"),
    STEEL("Steel", 60, "⚙️"),
    DIAMOND("Diamond", 100, "💎")
}

class TowerDefenseEngine(
    private val config: LevelConfig,
    initialGate: Gate
) {
    // Player board is 8x9, enemy field is 4x9
    val playerBoard: Board
    private val rng: SeededRandom
    private val matchFinder: MatchFinder
    private val specialActivator: SpecialActivator
    
    val enemies = mutableListOf<Enemy>()
    var gate: Gate = initialGate
        private set
    
    var score: Int = 0
        private set
    var turnsRemaining: Int = config.maxTurns
        private set
    var currentTurn: Int = 0
        private set
    var multiplier: Float = 1.0f
        private set
    var isGameOver: Boolean = false
        private set
    var isVictory: Boolean = false
        private set
    
    private val eventQueue = mutableListOf<GameEventWithState>()
    private var nextEnemyId = 0
    
    /** Helper to emit an event with current board snapshot */
    private fun emit(event: GameEvent) {
        eventQueue.add(GameEventWithState(event, playerBoard.copy()))
    }
    
    companion object {
        const val ENEMY_FIELD_HEIGHT = 4
        const val PLAYER_FIELD_HEIGHT = 8
        const val FIELD_WIDTH = 9
    }
    
    init {
        rng = SeededRandom(config.seed)
        playerBoard = Board(FIELD_WIDTH, PLAYER_FIELD_HEIGHT, rng)
        matchFinder = MatchFinder(playerBoard)
        specialActivator = SpecialActivator(playerBoard, rng)
        
        playerBoard.fillEmptyWithoutMatches()
    }
    
    fun getEvents(): List<GameEventWithState> {
        val events = eventQueue.toList()
        eventQueue.clear()
        return events
    }
    
    fun canSwap(pos1: Position, pos2: Position): Boolean {
        if (isGameOver) return false
        if (!pos1.isAdjacent(pos2)) return false
        
        val cell1 = playerBoard.getCell(pos1) ?: return false
        val cell2 = playerBoard.getCell(pos2) ?: return false
        
        return cell1.canSwap() && cell2.canSwap()
    }
    
    fun swap(pos1: Position, pos2: Position): Boolean {
        if (!canSwap(pos1, pos2)) return false
        
        val block1 = playerBoard.getBlock(pos1)
        val block2 = playerBoard.getBlock(pos2)
        
        playerBoard.setBlock(pos1, block2)
        playerBoard.setBlock(pos2, block1)
        
        emit(GameEvent.BlocksSwapped(pos1, pos2))
        
        // Check for special combos
        val special1 = block1?.isSpecial() == true
        val special2 = block2?.isSpecial() == true
        
        if (special1 && special2) {
            val result = specialActivator.activateCombo(
                pos1, block2!!.specialType,
                pos2, block1!!.specialType
            )
            processActivationResult(result)
            resolveBoard()
            endTurn()
            return true
        }
        
        val matchesAtPos1 = matchFinder.findMatchesAt(pos1)
        val matchesAtPos2 = matchFinder.findMatchesAt(pos2)
        
        if (matchesAtPos1.isEmpty() && matchesAtPos2.isEmpty()) {
            if (special1 || special2) {
                val specialPos = if (special2) pos1 else pos2
                val otherPos = if (special2) pos2 else pos1
                val result = specialActivator.activateSpecial(specialPos, otherPos)
                processActivationResult(result)
                resolveBoard()
                endTurn()
                return true
            }
            
            playerBoard.setBlock(pos1, block1)
            playerBoard.setBlock(pos2, block2)
            return false
        }
        
        resolveBoard()
        endTurn()
        
        return true
    }
    
    fun tapBlock(pos: Position): Boolean {
        if (isGameOver) return false
        
        val block = playerBoard.getBlock(pos) ?: return false
        if (!block.isSpecial()) return false
        
        val result = specialActivator.activateSpecial(pos)
        if (result.destroyedPositions.isEmpty()) return false
        
        processActivationResult(result)
        resolveBoard()
        endTurn()
        
        return true
    }
    
    private fun processActivationResult(result: SpecialActivator.ActivationResult) {
        for (e in result.events) emit(e)
        
        val projectilesPerColumn = mutableMapOf<Int, Int>()
        
        for (pos in result.destroyedPositions) {
            val block = playerBoard.getBlock(pos)
            if (block != null) {
                score++
                playerBoard.setBlock(pos, null)
                
                // Fire projectile upward
                projectilesPerColumn[pos.col] = (projectilesPerColumn[pos.col] ?: 0) + 1
            }
        }
        
        emit(GameEvent.BlocksDestroyed(result.destroyedPositions))
        
        // Process projectiles
        for ((col, count) in projectilesPerColumn) {
            fireProjectiles(col, count)
        }
        
        // Process chained activations
        for ((chainPos, _) in result.chainedActivations) {
            if (playerBoard.getBlock(chainPos) != null) {
                val chainResult = specialActivator.activateSpecial(chainPos)
                processActivationResult(chainResult)
            }
        }
    }
    
    private fun resolveBoard() {
        var cascadeLevel = 0
        
        do {
            val movements = playerBoard.applyGravity()
            if (movements.isNotEmpty()) {
                emit(GameEvent.BlocksFell(movements))
            }
            
            val spawned = playerBoard.spawnNewBlocks()
            if (spawned.isNotEmpty()) {
                emit(GameEvent.BlocksSpawned(spawned))
            }
            
            val matches = matchFinder.findAllMatches()
            
            if (matches.isEmpty()) break
            
            cascadeLevel++
            emit(GameEvent.CascadeStarted(cascadeLevel))
            
            for (match in matches) {
                processMatch(match)
            }
            
        } while (true)
        
        if (cascadeLevel > 0) {
            emit(GameEvent.CascadeEnded(cascadeLevel))
        }
        
        emit(GameEvent.BoardStabilized)
    }
    
    private fun processMatch(match: Match) {
        emit(GameEvent.BlocksMatched(match.positions, match.color))
        
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
        val projectilesPerColumn = mutableMapOf<Int, Int>()
        
        for (pos in match.positions) {
            val block = playerBoard.getBlock(pos)
            if (block != null) {
                if (block.isSpecial() && pos != creationPos) {
                    val result = specialActivator.activateSpecial(pos)
                    processActivationResult(result)
                }
                
                score++
                playerBoard.setBlock(pos, null)
                
                // Fire projectile
                projectilesPerColumn[pos.col] = (projectilesPerColumn[pos.col] ?: 0) + 1
            }
        }
        
        emit(GameEvent.BlocksDestroyed(match.positions))
        
        // Process projectiles
        for ((col, count) in projectilesPerColumn) {
            fireProjectiles(col, count)
        }
        
        if (specialType != SpecialType.NONE) {
            val specialBlock = Block(color = match.color, specialType = specialType)
            playerBoard.setBlock(creationPos, specialBlock)
            emit(GameEvent.SpecialCreated(creationPos, specialType, match.color))
        }
    }
    
    private fun fireProjectiles(col: Int, count: Int) {
        emit(GameEvent.ProjectileFired(col, count))
        
        // Find enemies in this column, sorted by row (closest to gate first)
        val enemiesInColumn = enemies
            .filter { it.col == col && it.hp > 0 }
            .sortedByDescending { it.row }
        
        var remainingDamage = count
        
        for (enemy in enemiesInColumn) {
            if (remainingDamage <= 0) break
            
            val damage = minOf(remainingDamage, enemy.hp)
            enemy.hp -= damage
            remainingDamage -= damage
            
            emit(GameEvent.EnemyDamaged(enemy.id, damage, enemy.hp))
            
            if (enemy.hp <= 0) {
                emit(GameEvent.EnemyDestroyed(enemy.id, Position(enemy.row, enemy.col)))
            }
        }
        
        // Remove dead enemies
        enemies.removeAll { it.hp <= 0 }
    }
    
    private fun endTurn() {
        currentTurn++
        turnsRemaining--
        
        // Spawn new enemies based on config
        for (enemyConfig in config.enemies) {
            if (enemyConfig.spawnTurn == currentTurn) {
                spawnEnemy(enemyConfig)
            }
        }
        
        // Move enemies down
        moveEnemies()
        
        // Enemies at gate attack
        processEnemyAttacks()
        
        emit(GameEvent.TurnEnded(turnsRemaining))
        
        checkWinCondition()
    }
    
    private fun spawnEnemy(config: EnemyConfig) {
        val enemy = Enemy(
            id = nextEnemyId++,
            type = config.type,
            row = 0,
            col = config.spawnCol,
            hp = config.hp,
            maxHp = config.hp
        )
        enemies.add(enemy)
        emit(GameEvent.EnemySpawned(enemy.id, enemy.type, Position(enemy.row, enemy.col)))
    }
    
    private fun moveEnemies() {
        for (enemy in enemies) {
            if (!enemy.isAtGate && enemy.hp > 0) {
                val oldRow = enemy.row
                enemy.row++
                
                // Check if reached gate (row >= ENEMY_FIELD_HEIGHT)
                if (enemy.row >= ENEMY_FIELD_HEIGHT) {
                    enemy.isAtGate = true
                    enemy.row = ENEMY_FIELD_HEIGHT - 1
                }
                
                emit(GameEvent.EnemyMoved(enemy.id, Position(oldRow, enemy.col), Position(enemy.row, enemy.col)))
            }
        }
    }
    
    private fun processEnemyAttacks() {
        for (enemy in enemies) {
            if (enemy.isAtGate && enemy.hp > 0) {
                val damage = enemy.getDamagePerTurn()
                val actualDamage = gate.takeDamage(damage)
                
                if (actualDamage > 0) {
                    emit(GameEvent.GateDamaged(actualDamage, gate.durability))
                }
            }
        }
    }
    
    private fun checkWinCondition() {
        // Lose if gate is destroyed
        if (gate.durability <= 0) {
            isGameOver = true
            isVictory = false
            emit(GameEvent.GameLost("Gate destroyed!"))
            return
        }
        
        // Win if all enemies are defeated and no more spawns
        val allEnemiesDefeated = enemies.all { it.hp <= 0 }
        val noMoreSpawns = config.enemies.none { it.spawnTurn > currentTurn }
        
        if (allEnemiesDefeated && noMoreSpawns && currentTurn > 0) {
            isGameOver = true
            isVictory = true
            emit(GameEvent.GameWon(score, gate.durability))
            return
        }
        
        // Lose if out of turns and enemies remain
        if (turnsRemaining <= 0 && enemies.any { it.hp > 0 }) {
            isGameOver = true
            isVictory = false
            emit(GameEvent.GameLost("Out of turns with enemies remaining"))
        }
    }
    
    fun getEnemyAt(row: Int, col: Int): Enemy? {
        return enemies.find { it.row == row && it.col == col && it.hp > 0 }
    }
    
    fun getGateDamagePerTurn(): Int {
        return enemies.filter { it.isAtGate && it.hp > 0 }.sumOf { it.getDamagePerTurn() }
    }
    
    fun getWalletReward(): Int {
        return if (isVictory) score + gate.durability else 0
    }
}
