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
    
    /**
     * Activate combo between two special blocks.
     * @param pos1 The position of the first block (where user started drag)
     * @param type1 The special type at pos1
     * @param pos2 The position of the second block (where user dragged to - TARGET)
     * @param type2 The special type at pos2
     * 
     * Combos activate at pos2 (the target/destination position)
     */
    fun activateCombo(
        pos1: Position, 
        type1: SpecialType, 
        pos2: Position, 
        type2: SpecialType
    ): ActivationResult {
        val destroyed = mutableSetOf<Position>()
        val events = mutableListOf<GameEvent>()
        val chained = mutableListOf<Pair<Position, SpecialType>>()
        
        // pos2 is the TARGET position (where we dragged TO)
        val targetPos = pos2
        
        events.add(GameEvent.ComboActivated(type1, type2, targetPos))
        
        // Normalize combo order for easier matching, but keep targetPos as activation center
        val (specialType1, specialType2) = if (type1.ordinal <= type2.ordinal) {
            type1 to type2
        } else {
            type2 to type1
        }
        
        when {
            // Rocket + Rocket = Cross (row + column) at target
            specialType1.isRocket() && specialType2.isRocket() -> {
                for (col in 0 until board.width) {
                    destroyed.add(Position(targetPos.row, col))
                }
                for (row in 0 until board.height) {
                    destroyed.add(Position(row, targetPos.col))
                }
                events.add(GameEvent.RocketFired(targetPos, true, destroyed))
            }
            
            // Rocket + Bomb = 3-wide cross at target
            specialType1.isRocket() && specialType2 == SpecialType.BOMB -> {
                for (col in 0 until board.width) {
                    for (r in -1..1) {
                        val p = Position(targetPos.row + r, col)
                        if (board.isValidPosition(p)) destroyed.add(p)
                    }
                }
                for (row in 0 until board.height) {
                    for (c in -1..1) {
                        val p = Position(row, targetPos.col + c)
                        if (board.isValidPosition(p)) destroyed.add(p)
                    }
                }
                events.add(GameEvent.RocketFired(targetPos, true, destroyed))
            }
            
            // Rocket + Propeller = Propeller carries rocket to destination, then fires
            specialType1.isRocket() && specialType2 == SpecialType.PROPELLER -> {
                val rocketType = if (type1.isRocket()) type1 else type2
                // Find a random target that's not the current positions
                val target = board.getRandomPositionExcluding(setOf(pos1, pos2))
                
                destroyed.add(pos1)
                destroyed.add(pos2)
                
                // Fire rocket from target position
                val isHorizontal = rocketType == SpecialType.ROCKET_HORIZONTAL
                if (isHorizontal) {
                    for (col in 0 until board.width) {
                        destroyed.add(Position(target.row, col))
                    }
                } else {
                    for (row in 0 until board.height) {
                        destroyed.add(Position(row, target.col))
                    }
                }
                // Propeller flies from targetPos (where combo activated) to target
                events.add(GameEvent.PropellerCarrying(targetPos, target, rocketType))
                events.add(GameEvent.RocketFired(target, isHorizontal, destroyed))
            }
            
            // Rocket + Disco = All cleared blocks become rockets
            specialType1.isRocket() && specialType2 == SpecialType.DISCO_BALL -> {
                val rocketType = if (type1.isRocket()) type1 else type2
                val rocketPos = if (type1.isRocket()) pos1 else pos2
                val targetColor = board.getBlock(rocketPos)?.color 
                    ?: BlockColor.entries[rng.nextInt(BlockColor.entries.size)]
                
                destroyed.add(pos1)
                destroyed.add(pos2)
                
                val colorPositions = mutableListOf<Position>()
                for (boardPos in board.getAllPositions()) {
                    val block = board.getBlock(boardPos)
                    if (block?.color == targetColor && boardPos != pos1 && boardPos != pos2) {
                        colorPositions.add(boardPos)
                    }
                }
                
                // Each position becomes a rocket and fires
                for (rocketP in colorPositions) {
                    val isHorizontal = rng.nextBoolean()
                    if (isHorizontal) {
                        for (col in 0 until board.width) {
                            destroyed.add(Position(rocketP.row, col))
                        }
                    } else {
                        for (row in 0 until board.height) {
                            destroyed.add(Position(row, rocketP.col))
                        }
                    }
                }
                
                events.add(GameEvent.DiscoActivated(targetPos, targetColor, destroyed))
            }
            
            // Disco + Bomb = All cleared blocks become bombs (Disco ordinal=3, Bomb ordinal=5)
            specialType1 == SpecialType.DISCO_BALL && specialType2 == SpecialType.BOMB -> {
                val bombPos = if (type1 == SpecialType.BOMB) pos1 else pos2
                val targetColor = board.getBlock(bombPos)?.color 
                    ?: BlockColor.entries[rng.nextInt(BlockColor.entries.size)]
                
                destroyed.add(pos1)
                destroyed.add(pos2)
                
                val colorPositions = mutableListOf<Position>()
                for (boardPos in board.getAllPositions()) {
                    val block = board.getBlock(boardPos)
                    if (block?.color == targetColor && boardPos != pos1 && boardPos != pos2) {
                        colorPositions.add(boardPos)
                    }
                }
                
                // Each position becomes a bomb and explodes
                for (bombP in colorPositions) {
                    for (p in bombP.get3x3Area()) {
                        if (board.isValidPosition(p)) destroyed.add(p)
                    }
                }
                
                events.add(GameEvent.DiscoActivated(targetPos, targetColor, destroyed))
            }
            
            // Disco + Propeller = All cleared blocks become propellers (Disco ordinal=3, Propeller ordinal=4)
            specialType1 == SpecialType.DISCO_BALL && specialType2 == SpecialType.PROPELLER -> {
                val propPos = if (type1 == SpecialType.PROPELLER) pos1 else pos2
                val targetColor = board.getBlock(propPos)?.color 
                    ?: BlockColor.entries[rng.nextInt(BlockColor.entries.size)]
                
                destroyed.add(pos1)
                destroyed.add(pos2)
                
                val colorPositions = mutableListOf<Position>()
                for (boardPos in board.getAllPositions()) {
                    val block = board.getBlock(boardPos)
                    if (block?.color == targetColor && boardPos != pos1 && boardPos != pos2) {
                        colorPositions.add(boardPos)
                    }
                }
                
                // Emit disco activation first
                events.add(GameEvent.DiscoActivated(targetPos, targetColor, colorPositions.toSet()))
                
                // Each position becomes a propeller and activates
                // Propeller destroys itself + 4 cells around it (cross pattern), then flies to target
                for (propP in colorPositions) {
                    // Destroy propeller position and its cross (4 adjacent cells)
                    destroyed.add(propP)
                    for (adjPos in propP.getCross()) {
                        if (board.isValidPosition(adjPos)) destroyed.add(adjPos)
                    }
                    
                    // Then fly to random target and destroy there too
                    val target = board.getRandomPositionExcluding(destroyed)
                    val propellerDestroyed = mutableSetOf(propP, target)
                    for (adjPos in target.getCross()) {
                        if (board.isValidPosition(adjPos)) {
                            destroyed.add(adjPos)
                            propellerDestroyed.add(adjPos)
                        }
                    }
                    destroyed.add(target)
                    
                    // Emit individual propeller flight event for animation
                    events.add(GameEvent.PropellerFlew(propP, target, propellerDestroyed))
                }
            }
            
            // Propeller + Bomb = Propeller carries bomb to random destination
            specialType1 == SpecialType.PROPELLER && specialType2 == SpecialType.BOMB -> {
                // Find a random target that's not the current positions
                val target = board.getRandomPositionExcluding(setOf(pos1, pos2))
                
                destroyed.add(pos1)
                destroyed.add(pos2)
                
                for (p in target.get3x3Area()) {
                    if (board.isValidPosition(p)) destroyed.add(p)
                }
                // Propeller flies from targetPos (where combo activated) to target
                events.add(GameEvent.PropellerCarrying(targetPos, target, SpecialType.BOMB))
                events.add(GameEvent.BombExploded(target, destroyed))
            }
            
            // Bomb + Bomb = 5x5 explosion at TARGET
            specialType1 == SpecialType.BOMB && specialType2 == SpecialType.BOMB -> {
                for (p in targetPos.get5x5Area()) {
                    if (board.isValidPosition(p)) destroyed.add(p)
                }
                events.add(GameEvent.BombExploded(targetPos, destroyed))
            }
            
            // Disco + Disco = Clear entire board
            specialType1 == SpecialType.DISCO_BALL && specialType2 == SpecialType.DISCO_BALL -> {
                for (boardPos in board.getAllPositions()) {
                    destroyed.add(boardPos)
                }
                events.add(GameEvent.DiscoActivated(targetPos, BlockColor.RED, destroyed))
            }
            
            // Propeller + Propeller = Two propellers fly
            specialType1 == SpecialType.PROPELLER && specialType2 == SpecialType.PROPELLER -> {
                destroyed.add(pos1)
                destroyed.add(pos2)
                
                val target1 = board.findNearestSpecial(pos1) ?: board.getRandomPosition()
                destroyed.add(target1)
                for (adjPos in target1.getCross()) {
                    if (board.isValidPosition(adjPos)) destroyed.add(adjPos)
                }
                
                val target2 = board.getRandomPosition()
                destroyed.add(target2)
                for (adjPos in target2.getCross()) {
                    if (board.isValidPosition(adjPos)) destroyed.add(adjPos)
                }
                
                events.add(GameEvent.PropellerFlew(pos1, target1, destroyed))
                events.add(GameEvent.PropellerFlew(pos2, target2, destroyed))
            }
        }
        
        // Find any specials in destroyed positions for chaining
        for (destroyedPos in destroyed) {
            if (destroyedPos != pos1 && destroyedPos != pos2) {
                val block = board.getBlock(destroyedPos)
                if (block?.isSpecial() == true) {
                    chained.add(destroyedPos to block.specialType)
                }
            }
        }
        
        return ActivationResult(destroyed, events, chained)
    }
}
