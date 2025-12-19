package com.match3.game.domain.engine

import com.match3.game.domain.model.*

class Board(
    val width: Int,
    val height: Int,
    private val rng: SeededRandom
) {
    private val cells: Array<Array<Cell>> = Array(height) { row ->
        Array(width) { col ->
            Cell(row = row, col = col)
        }
    }
    
    fun getCell(pos: Position): Cell? {
        return if (isValidPosition(pos)) cells[pos.row][pos.col] else null
    }
    
    fun getCell(row: Int, col: Int): Cell? {
        return if (isValidPosition(row, col)) cells[row][col] else null
    }
    
    fun setCell(pos: Position, cell: Cell) {
        if (isValidPosition(pos)) {
            cells[pos.row][pos.col] = cell
        }
    }
    
    fun setBlock(pos: Position, block: Block?) {
        if (isValidPosition(pos)) {
            cells[pos.row][pos.col] = cells[pos.row][pos.col].copy(block = block)
        }
    }
    
    fun getBlock(pos: Position): Block? = getCell(pos)?.block
    
    fun isValidPosition(pos: Position): Boolean = isValidPosition(pos.row, pos.col)
    
    fun isValidPosition(row: Int, col: Int): Boolean = 
        row in 0 until height && col in 0 until width
    
    fun getAllPositions(): List<Position> {
        val positions = mutableListOf<Position>()
        for (row in 0 until height) {
            for (col in 0 until width) {
                positions.add(Position(row, col))
            }
        }
        return positions
    }
    
    fun getAllCells(): List<Cell> {
        return getAllPositions().mapNotNull { getCell(it) }
    }
    
    fun fillEmpty(): List<Position> {
        val spawned = mutableListOf<Position>()
        for (col in 0 until width) {
            for (row in 0 until height) {
                val pos = Position(row, col)
                val cell = getCell(pos)
                if (cell != null && cell.type == CellType.NORMAL && cell.block == null) {
                    val block = Block(color = rng.nextBlockColor())
                    setBlock(pos, block)
                    spawned.add(pos)
                }
            }
        }
        return spawned
    }
    
    /**
     * Initialize board from string layout.
     * Format: '.' = empty, 'R'/'G'/'b'/'Y' = colors, 'H'/'V' = rockets, 'B' = bomb, 'P' = propeller, 'D' = disco
     */
    fun initializeFromLayout(layout: List<String>) {
        for (row in layout.indices) {
            val rowStr = layout[row]
            for (col in rowStr.indices) {
                val pos = Position(row, col)
                val char = rowStr[col]
                
                when (char) {
                    '.' -> setBlock(pos, null) // Empty
                    'H' -> setBlock(pos, Block(color = rng.nextBlockColor(), specialType = SpecialType.ROCKET_HORIZONTAL))
                    'V' -> setBlock(pos, Block(color = rng.nextBlockColor(), specialType = SpecialType.ROCKET_VERTICAL))
                    'B' -> setBlock(pos, Block(color = rng.nextBlockColor(), specialType = SpecialType.BOMB))
                    'P' -> setBlock(pos, Block(color = rng.nextBlockColor(), specialType = SpecialType.PROPELLER))
                    'D' -> setBlock(pos, Block(color = rng.nextBlockColor(), specialType = SpecialType.DISCO_BALL))
                    'R' -> setBlock(pos, Block(color = BlockColor.RED))
                    'G' -> setBlock(pos, Block(color = BlockColor.GREEN))
                    'b' -> setBlock(pos, Block(color = BlockColor.BLUE))
                    'Y' -> setBlock(pos, Block(color = BlockColor.YELLOW))
                }
            }
        }
    }
    
    fun fillEmptyWithoutMatches(): List<Position> {
        val spawned = mutableListOf<Position>()
        for (col in 0 until width) {
            for (row in height - 1 downTo 0) {
                val pos = Position(row, col)
                val cell = getCell(pos)
                if (cell != null && cell.type == CellType.NORMAL && cell.block == null) {
                    var block: Block
                    var attempts = 0
                    do {
                        block = Block(color = rng.nextBlockColor())
                        setBlock(pos, block)
                        attempts++
                    } while (wouldCreateMatch(pos) && attempts < 10)
                    spawned.add(pos)
                }
            }
        }
        return spawned
    }
    
    private fun wouldCreateMatch(pos: Position): Boolean {
        val block = getBlock(pos) ?: return false
        val color = block.color
        
        // Check horizontal
        var hCount = 1
        var c = pos.col - 1
        while (c >= 0 && getBlock(Position(pos.row, c))?.color == color) {
            hCount++
            c--
        }
        c = pos.col + 1
        while (c < width && getBlock(Position(pos.row, c))?.color == color) {
            hCount++
            c++
        }
        if (hCount >= 3) return true
        
        // Check vertical
        var vCount = 1
        var r = pos.row - 1
        while (r >= 0 && getBlock(Position(r, pos.col))?.color == color) {
            vCount++
            r--
        }
        r = pos.row + 1
        while (r < height && getBlock(Position(r, pos.col))?.color == color) {
            vCount++
            r++
        }
        return vCount >= 3
    }
    
    fun applyGravity(): List<Pair<Position, Position>> {
        val movements = mutableListOf<Pair<Position, Position>>()
        
        for (col in 0 until width) {
            var writeRow = height - 1
            
            // Find the lowest empty normal cell
            while (writeRow >= 0) {
                val cell = getCell(Position(writeRow, col))
                if (cell != null && cell.type == CellType.NORMAL && cell.block == null) {
                    break
                }
                writeRow--
            }
            
            if (writeRow < 0) continue
            
            // Move blocks down
            for (readRow in writeRow - 1 downTo 0) {
                val readPos = Position(readRow, col)
                val readCell = getCell(readPos)
                
                if (readCell?.type != CellType.NORMAL) continue
                if (readCell.block == null) continue
                
                val writePos = Position(writeRow, col)
                val writeCell = getCell(writePos)
                
                if (writeCell?.type == CellType.NORMAL && writeCell.block == null) {
                    setBlock(writePos, readCell.block)
                    setBlock(readPos, null)
                    movements.add(readPos to writePos)
                    writeRow--
                    
                    // Find next empty position
                    while (writeRow >= 0) {
                        val nextCell = getCell(Position(writeRow, col))
                        if (nextCell != null && nextCell.type == CellType.NORMAL && nextCell.block == null) {
                            break
                        }
                        writeRow--
                    }
                }
            }
        }
        
        return movements
    }
    
    fun spawnNewBlocks(): List<Position> {
        val spawned = mutableListOf<Position>()
        
        for (col in 0 until width) {
            for (row in 0 until height) {
                val pos = Position(row, col)
                val cell = getCell(pos)
                if (cell != null && cell.type == CellType.NORMAL && cell.block == null) {
                    val block = Block(color = rng.nextBlockColor())
                    setBlock(pos, block)
                    spawned.add(pos)
                }
            }
        }
        
        return spawned
    }
    
    fun setupSpecialCells(configs: List<SpecialCellConfig>) {
        for (config in configs) {
            val pos = Position(config.row, config.col)
            if (isValidPosition(pos)) {
                val currentCell = getCell(pos)!!
                val newCell = currentCell.copy(
                    type = config.type,
                    durability = config.durability,
                    requiredColor = config.requiredColor
                )
                setCell(pos, newCell)
            }
        }
    }
    
    fun damageAdjacentCells(pos: Position, color: BlockColor?): List<Position> {
        val damaged = mutableListOf<Position>()
        for (adjPos in pos.getAdjacent()) {
            val cell = getCell(adjPos) ?: continue
            if (cell.type != CellType.NORMAL && cell.durability > 0) {
                val newCell = cell.takeDamage(color)
                setCell(adjPos, newCell)
                if (newCell.type == CellType.NORMAL || newCell.durability < cell.durability) {
                    damaged.add(adjPos)
                }
            }
        }
        return damaged
    }
    
    fun getSpecialCellsRemaining(): Int {
        return getAllCells().count { it.type != CellType.NORMAL && it.durability > 0 }
    }
    
    fun findAllSpecials(): List<Pair<Position, SpecialType>> {
        val specials = mutableListOf<Pair<Position, SpecialType>>()
        for (pos in getAllPositions()) {
            val block = getBlock(pos)
            if (block != null && block.isSpecial()) {
                specials.add(pos to block.specialType)
            }
        }
        return specials
    }
    
    fun findNearestSpecial(from: Position): Position? {
        val specials = findAllSpecials().filter { it.first != from }
        if (specials.isEmpty()) return null
        
        return specials.minByOrNull { (pos, _) ->
            kotlin.math.abs(pos.row - from.row) + kotlin.math.abs(pos.col - from.col)
        }?.first
    }
    
    fun getRandomPosition(): Position {
        val validPositions = getAllPositions().filter { 
            getCell(it)?.type == CellType.NORMAL && getCell(it)?.block != null 
        }
        return if (validPositions.isNotEmpty()) {
            validPositions[rng.nextInt(validPositions.size)]
        } else {
            Position(height / 2, width / 2)
        }
    }
    
    fun getRandomPositionExcluding(excluded: Set<Position>): Position {
        val validPositions = getAllPositions().filter { 
            it !in excluded && getCell(it)?.type == CellType.NORMAL && getCell(it)?.block != null 
        }
        return if (validPositions.isNotEmpty()) {
            validPositions[rng.nextInt(validPositions.size)]
        } else {
            Position(height / 2, width / 2)
        }
    }
    
    fun copy(): Board {
        val newBoard = Board(width, height, rng.fork())
        for (row in 0 until height) {
            for (col in 0 until width) {
                newBoard.cells[row][col] = cells[row][col].copy()
            }
        }
        return newBoard
    }
    
    override fun toString(): String {
        val sb = StringBuilder()
        for (row in 0 until height) {
            for (col in 0 until width) {
                sb.append(cells[row][col].getDisplayEmoji())
            }
            sb.append("\n")
        }
        return sb.toString()
    }
}
