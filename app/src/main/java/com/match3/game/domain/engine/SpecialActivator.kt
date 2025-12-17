package com.match3.game.domain.engine

import com.match3.game.domain.model.*

class SpecialActivator(
    private val board: Board,
    private val rng: SeededRandom
) {
    
    data class ActivationResult(
        val destroyedPositions: Set<Position>,
        val events: List<GameEvent>,
        val chainedActivations: List<Pair<Position, SpecialType>>
    )
    
    fun activateSpecial(pos: Position, swappedWith: Position? = null): ActivationResult {
        val block = board.getBlock(pos) ?: return ActivationResult(emptySet(), emptyList(), emptyList())
        
        if (!block.isSpecial()) {
            return ActivationResult(emptySet(), emptyList(), emptyList())
        }
        
        // Check for combo with swapped block
        if (swappedWith != null) {
            val otherBlock = board.getBlock(swappedWith)
            if (otherBlock?.isSpecial() == true) {
                return activateCombo(pos, block.specialType, swappedWith, otherBlock.specialType)
            }
        }
        
        return when (block.specialType) {
            SpecialType.ROCKET_HORIZONTAL -> activateRocket(pos, true)
            SpecialType.ROCKET_VERTICAL -> activateRocket(pos, false)
            SpecialType.DISCO_BALL -> activateDisco(pos, swappedWith)
            SpecialType.PROPELLER -> activatePropeller(pos)
            SpecialType.BOMB -> activateBomb(pos)
            else -> ActivationResult(emptySet(), emptyList(), emptyList())
        }
    }
    
    private fun activateRocket(pos: Position, isHorizontal: Boolean): ActivationResult {
        val destroyed = mutableSetOf<Position>()
        val events = mutableListOf<GameEvent>()
        val chained = mutableListOf<Pair<Position, SpecialType>>()
        
        if (isHorizontal) {
            for (col in 0 until board.width) {
                val targetPos = Position(pos.row, col)
                if (board.isValidPosition(targetPos)) {
                    val targetBlock = board.getBlock(targetPos)
                    if (targetBlock?.isSpecial() == true && targetPos != pos) {
                        chained.add(targetPos to targetBlock.specialType)
                    }
                    destroyed.add(targetPos)
                }
            }
        } else {
            for (row in 0 until board.height) {
                val targetPos = Position(row, pos.col)
                if (board.isValidPosition(targetPos)) {
                    val targetBlock = board.getBlock(targetPos)
                    if (targetBlock?.isSpecial() == true && targetPos != pos) {
                        chained.add(targetPos to targetBlock.specialType)
                    }
                    destroyed.add(targetPos)
                }
            }
        }
        
        events.add(GameEvent.RocketFired(pos, isHorizontal, destroyed))
        
        return ActivationResult(destroyed, events, chained)
    }
    
    private fun activateDisco(pos: Position, swappedWith: Position?): ActivationResult {
        val destroyed = mutableSetOf<Position>()
        val events = mutableListOf<GameEvent>()
        val chained = mutableListOf<Pair<Position, SpecialType>>()
        
        // Determine which color to clear
        val targetColor = if (swappedWith != null) {
            board.getBlock(swappedWith)?.color ?: BlockColor.entries[rng.nextInt(BlockColor.entries.size)]
        } else {
            BlockColor.entries[rng.nextInt(BlockColor.entries.size)]
        }
        
        destroyed.add(pos)
        
        // Find all blocks of that color
        for (boardPos in board.getAllPositions()) {
            val block = board.getBlock(boardPos)
            if (block?.color == targetColor) {
                if (block.isSpecial() && boardPos != pos) {
                    chained.add(boardPos to block.specialType)
                }
                destroyed.add(boardPos)
            }
        }
        
        events.add(GameEvent.DiscoActivated(pos, targetColor, destroyed))
        
        return ActivationResult(destroyed, events, chained)
    }
    
    private fun activatePropeller(pos: Position): ActivationResult {
        val destroyed = mutableSetOf<Position>()
        val events = mutableListOf<GameEvent>()
        val chained = mutableListOf<Pair<Position, SpecialType>>()
        
        // Find target - nearest special or random block
        val target = board.findNearestSpecial(pos) ?: board.getRandomPosition()
        
        // Destroy target and cross pattern
        destroyed.add(pos)
        destroyed.add(target)
        
        for (adjPos in target.getCross()) {
            if (board.isValidPosition(adjPos)) {
                val adjBlock = board.getBlock(adjPos)
                if (adjBlock?.isSpecial() == true && adjPos != pos) {
                    chained.add(adjPos to adjBlock.specialType)
                }
                destroyed.add(adjPos)
            }
        }
        
        // Check if target itself is special
        val targetBlock = board.getBlock(target)
        if (targetBlock?.isSpecial() == true && target != pos) {
            chained.add(target to targetBlock.specialType)
        }
        
        events.add(GameEvent.PropellerFlew(pos, target, destroyed))
        
        return ActivationResult(destroyed, events, chained)
    }
    
    private fun activateBomb(pos: Position): ActivationResult {
        val destroyed = mutableSetOf<Position>()
        val events = mutableListOf<GameEvent>()
        val chained = mutableListOf<Pair<Position, SpecialType>>()
        
        for (targetPos in pos.get3x3Area()) {
            if (board.isValidPosition(targetPos)) {
                val targetBlock = board.getBlock(targetPos)
                if (targetBlock?.isSpecial() == true && targetPos != pos) {
                    chained.add(targetPos to targetBlock.specialType)
                }
                destroyed.add(targetPos)
            }
        }
        
        events.add(GameEvent.BombExploded(pos, destroyed))
        
        return ActivationResult(destroyed, events, chained)
    }
    
    fun activateCombo(
        pos1: Position, 
        type1: SpecialType, 
        pos2: Position, 
        type2: SpecialType
    ): ActivationResult {
        val destroyed = mutableSetOf<Position>()
        val events = mutableListOf<GameEvent>()
        val chained = mutableListOf<Pair<Position, SpecialType>>()
        
        events.add(GameEvent.ComboActivated(type1, type2, pos1))
        
        // Normalize combo order for easier matching
        val (t1, t2, p1, p2) = if (type1.ordinal <= type2.ordinal) {
            listOf(type1, type2, pos1, pos2)
        } else {
            listOf(type2, type1, pos2, pos1)
        } as List<Any>
        
        val specialType1 = t1 as SpecialType
        val specialType2 = t2 as SpecialType
        val position1 = p1 as Position
        val position2 = p2 as Position
        
        when {
            // Rocket + Rocket = Cross (row + column)
            specialType1.isRocket() && specialType2.isRocket() -> {
                for (col in 0 until board.width) {
                    destroyed.add(Position(position1.row, col))
                }
                for (row in 0 until board.height) {
                    destroyed.add(Position(row, position1.col))
                }
                events.add(GameEvent.RocketFired(position1, true, destroyed))
            }
            
            // Rocket + Bomb = 3-wide cross
            specialType1.isRocket() && specialType2 == SpecialType.BOMB -> {
                for (col in 0 until board.width) {
                    for (r in -1..1) {
                        val targetPos = Position(position1.row + r, col)
                        if (board.isValidPosition(targetPos)) {
                            destroyed.add(targetPos)
                        }
                    }
                }
                for (row in 0 until board.height) {
                    for (c in -1..1) {
                        val targetPos = Position(row, position1.col + c)
                        if (board.isValidPosition(targetPos)) {
                            destroyed.add(targetPos)
                        }
                    }
                }
                events.add(GameEvent.RocketFired(position1, true, destroyed))
            }
            
            // Rocket + Propeller = Propeller carries rocket
            specialType1.isRocket() && specialType2 == SpecialType.PROPELLER -> {
                val target = board.findNearestSpecial(position1) ?: board.getRandomPosition()
                destroyed.add(position1)
                destroyed.add(position2)
                
                // Fire rocket from target position
                val isHorizontal = specialType1 == SpecialType.ROCKET_HORIZONTAL
                if (isHorizontal) {
                    for (col in 0 until board.width) {
                        destroyed.add(Position(target.row, col))
                    }
                } else {
                    for (row in 0 until board.height) {
                        destroyed.add(Position(row, target.col))
                    }
                }
                events.add(GameEvent.PropellerFlew(position2, target, setOf(position2, target)))
                events.add(GameEvent.RocketFired(target, isHorizontal, destroyed))
            }
            
            // Rocket + Disco = All cleared blocks become rockets
            specialType1.isRocket() && specialType2 == SpecialType.DISCO_BALL -> {
                val targetColor = board.getBlock(position1)?.color 
                    ?: BlockColor.entries[rng.nextInt(BlockColor.entries.size)]
                
                destroyed.add(position1)
                destroyed.add(position2)
                
                val colorPositions = mutableListOf<Position>()
                for (boardPos in board.getAllPositions()) {
                    val block = board.getBlock(boardPos)
                    if (block?.color == targetColor && boardPos != position1 && boardPos != position2) {
                        colorPositions.add(boardPos)
                    }
                }
                
                // Each position becomes a rocket and fires
                for (rocketPos in colorPositions) {
                    val isHorizontal = rng.nextBoolean()
                    if (isHorizontal) {
                        for (col in 0 until board.width) {
                            destroyed.add(Position(rocketPos.row, col))
                        }
                    } else {
                        for (row in 0 until board.height) {
                            destroyed.add(Position(row, rocketPos.col))
                        }
                    }
                }
                
                events.add(GameEvent.DiscoActivated(position2, targetColor, destroyed))
            }
            
            // Bomb + Bomb = 5x5 explosion
            specialType1 == SpecialType.BOMB && specialType2 == SpecialType.BOMB -> {
                for (targetPos in position1.get5x5Area()) {
                    if (board.isValidPosition(targetPos)) {
                        destroyed.add(targetPos)
                    }
                }
                events.add(GameEvent.BombExploded(position1, destroyed))
            }
            
            // Bomb + Propeller = Propeller carries bomb
            specialType1 == SpecialType.BOMB && specialType2 == SpecialType.PROPELLER -> {
                val target = board.findNearestSpecial(position1) ?: board.getRandomPosition()
                destroyed.add(position1)
                destroyed.add(position2)
                
                for (targetPos in target.get3x3Area()) {
                    if (board.isValidPosition(targetPos)) {
                        destroyed.add(targetPos)
                    }
                }
                events.add(GameEvent.PropellerFlew(position2, target, setOf(position2, target)))
                events.add(GameEvent.BombExploded(target, destroyed))
            }
            
            // Bomb + Disco = All cleared blocks become bombs
            specialType1 == SpecialType.BOMB && specialType2 == SpecialType.DISCO_BALL -> {
                val targetColor = board.getBlock(position1)?.color 
                    ?: BlockColor.entries[rng.nextInt(BlockColor.entries.size)]
                
                destroyed.add(position1)
                destroyed.add(position2)
                
                val colorPositions = mutableListOf<Position>()
                for (boardPos in board.getAllPositions()) {
                    val block = board.getBlock(boardPos)
                    if (block?.color == targetColor && boardPos != position1 && boardPos != position2) {
                        colorPositions.add(boardPos)
                    }
                }
                
                // Each position becomes a bomb and explodes
                for (bombPos in colorPositions) {
                    for (targetPos in bombPos.get3x3Area()) {
                        if (board.isValidPosition(targetPos)) {
                            destroyed.add(targetPos)
                        }
                    }
                }
                
                events.add(GameEvent.DiscoActivated(position2, targetColor, destroyed))
            }
            
            // Propeller + Disco = All cleared blocks become propellers
            specialType1 == SpecialType.PROPELLER && specialType2 == SpecialType.DISCO_BALL -> {
                val targetColor = board.getBlock(position1)?.color 
                    ?: BlockColor.entries[rng.nextInt(BlockColor.entries.size)]
                
                destroyed.add(position1)
                destroyed.add(position2)
                
                val colorPositions = mutableListOf<Position>()
                for (boardPos in board.getAllPositions()) {
                    val block = board.getBlock(boardPos)
                    if (block?.color == targetColor && boardPos != position1 && boardPos != position2) {
                        colorPositions.add(boardPos)
                    }
                }
                
                // Each position becomes a propeller and activates
                for (propPos in colorPositions) {
                    destroyed.add(propPos)
                    val target = board.getRandomPosition()
                    destroyed.add(target)
                    for (adjPos in target.getCross()) {
                        if (board.isValidPosition(adjPos)) {
                            destroyed.add(adjPos)
                        }
                    }
                }
                
                events.add(GameEvent.DiscoActivated(position2, targetColor, destroyed))
            }
            
            // Disco + Disco = Clear entire board
            specialType1 == SpecialType.DISCO_BALL && specialType2 == SpecialType.DISCO_BALL -> {
                for (boardPos in board.getAllPositions()) {
                    destroyed.add(boardPos)
                }
                events.add(GameEvent.DiscoActivated(position1, BlockColor.RED, destroyed))
            }
            
            // Propeller + Propeller = Two propellers fly
            specialType1 == SpecialType.PROPELLER && specialType2 == SpecialType.PROPELLER -> {
                destroyed.add(position1)
                destroyed.add(position2)
                
                val target1 = board.findNearestSpecial(position1) ?: board.getRandomPosition()
                destroyed.add(target1)
                for (adjPos in target1.getCross()) {
                    if (board.isValidPosition(adjPos)) destroyed.add(adjPos)
                }
                
                val target2 = board.getRandomPosition()
                destroyed.add(target2)
                for (adjPos in target2.getCross()) {
                    if (board.isValidPosition(adjPos)) destroyed.add(adjPos)
                }
                
                events.add(GameEvent.PropellerFlew(position1, target1, destroyed))
                events.add(GameEvent.PropellerFlew(position2, target2, destroyed))
            }
        }
        
        // Find any specials in destroyed positions for chaining
        for (destroyedPos in destroyed) {
            if (destroyedPos != position1 && destroyedPos != position2) {
                val block = board.getBlock(destroyedPos)
                if (block?.isSpecial() == true) {
                    chained.add(destroyedPos to block.specialType)
                }
            }
        }
        
        return ActivationResult(destroyed, events, chained)
    }
}
