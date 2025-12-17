package com.match3.game.domain.engine

import com.match3.game.domain.model.*

class MatchFinder(private val board: Board) {
    
    fun findAllMatches(): List<Match> {
        val matches = mutableListOf<Match>()
        val processed = mutableSetOf<Position>()
        
        // Find horizontal matches
        for (row in 0 until board.height) {
            var col = 0
            while (col < board.width) {
                val startPos = Position(row, col)
                val block = board.getBlock(startPos)
                
                if (block == null || block.isSpecial()) {
                    col++
                    continue
                }
                
                val color = block.color
                val positions = mutableSetOf(startPos)
                
                var nextCol = col + 1
                while (nextCol < board.width) {
                    val nextPos = Position(row, nextCol)
                    val nextBlock = board.getBlock(nextPos)
                    if (nextBlock?.color == color && !nextBlock.isSpecial()) {
                        positions.add(nextPos)
                        nextCol++
                    } else {
                        break
                    }
                }
                
                if (positions.size >= 3) {
                    val matchType = when (positions.size) {
                        3 -> Match.MatchType.LINE_3
                        4 -> Match.MatchType.LINE_4
                        else -> Match.MatchType.LINE_5
                    }
                    matches.add(Match(positions, color, matchType))
                    processed.addAll(positions)
                }
                
                col = nextCol
            }
        }
        
        // Find vertical matches
        for (col in 0 until board.width) {
            var row = 0
            while (row < board.height) {
                val startPos = Position(row, col)
                val block = board.getBlock(startPos)
                
                if (block == null || block.isSpecial()) {
                    row++
                    continue
                }
                
                val color = block.color
                val positions = mutableSetOf(startPos)
                
                var nextRow = row + 1
                while (nextRow < board.height) {
                    val nextPos = Position(nextRow, col)
                    val nextBlock = board.getBlock(nextPos)
                    if (nextBlock?.color == color && !nextBlock.isSpecial()) {
                        positions.add(nextPos)
                        nextRow++
                    } else {
                        break
                    }
                }
                
                if (positions.size >= 3) {
                    val matchType = when (positions.size) {
                        3 -> Match.MatchType.LINE_3
                        4 -> Match.MatchType.LINE_4
                        else -> Match.MatchType.LINE_5
                    }
                    matches.add(Match(positions, color, matchType))
                    processed.addAll(positions)
                }
                
                row = nextRow
            }
        }
        
        // Find 2x2 squares
        for (row in 0 until board.height - 1) {
            for (col in 0 until board.width - 1) {
                val positions = listOf(
                    Position(row, col),
                    Position(row, col + 1),
                    Position(row + 1, col),
                    Position(row + 1, col + 1)
                )
                
                val blocks = positions.mapNotNull { board.getBlock(it) }
                if (blocks.size == 4 && 
                    blocks.all { !it.isSpecial() } &&
                    blocks.all { it.color == blocks[0].color }) {
                    
                    val posSet = positions.toSet()
                    // Only add if not already part of a larger match
                    if (!matches.any { it.positions.containsAll(posSet) }) {
                        matches.add(Match(posSet, blocks[0].color, Match.MatchType.SQUARE_2X2))
                    }
                }
            }
        }
        
        // Find T-shapes and L-shapes
        val tAndLMatches = findTAndLShapes()
        matches.addAll(tAndLMatches)
        
        return mergeOverlappingMatches(matches)
    }
    
    private fun findTAndLShapes(): List<Match> {
        val matches = mutableListOf<Match>()
        
        for (row in 0 until board.height) {
            for (col in 0 until board.width) {
                val centerPos = Position(row, col)
                val centerBlock = board.getBlock(centerPos) ?: continue
                if (centerBlock.isSpecial()) continue
                
                val color = centerBlock.color
                
                // Check T-shapes (4 orientations)
                val tPatterns = listOf(
                    // T pointing down
                    listOf(Position(row, col-1), Position(row, col), Position(row, col+1), Position(row+1, col)),
                    // T pointing up
                    listOf(Position(row, col-1), Position(row, col), Position(row, col+1), Position(row-1, col)),
                    // T pointing right
                    listOf(Position(row-1, col), Position(row, col), Position(row+1, col), Position(row, col+1)),
                    // T pointing left
                    listOf(Position(row-1, col), Position(row, col), Position(row+1, col), Position(row, col-1))
                )
                
                for (pattern in tPatterns) {
                    if (pattern.all { pos ->
                        board.isValidPosition(pos) && 
                        board.getBlock(pos)?.color == color &&
                        board.getBlock(pos)?.isSpecial() != true
                    }) {
                        matches.add(Match(pattern.toSet(), color, Match.MatchType.T_SHAPE))
                    }
                }
                
                // Check L-shapes (4 orientations)
                val lPatterns = listOf(
                    // L normal
                    listOf(Position(row, col), Position(row+1, col), Position(row+2, col), Position(row+2, col+1), Position(row+2, col+2)),
                    // L rotated 90
                    listOf(Position(row, col), Position(row, col+1), Position(row, col+2), Position(row+1, col), Position(row+2, col)),
                    // L rotated 180
                    listOf(Position(row, col), Position(row, col+1), Position(row, col+2), Position(row+1, col+2), Position(row+2, col+2)),
                    // L rotated 270
                    listOf(Position(row, col+2), Position(row+1, col+2), Position(row+2, col), Position(row+2, col+1), Position(row+2, col+2))
                )
                
                for (pattern in lPatterns) {
                    if (pattern.all { pos ->
                        board.isValidPosition(pos) && 
                        board.getBlock(pos)?.color == color &&
                        board.getBlock(pos)?.isSpecial() != true
                    }) {
                        matches.add(Match(pattern.toSet(), color, Match.MatchType.L_SHAPE))
                    }
                }
            }
        }
        
        return matches
    }
    
    private fun mergeOverlappingMatches(matches: List<Match>): List<Match> {
        if (matches.isEmpty()) return matches
        
        // Sort by priority: larger/special-creating matches first
        val sorted = matches.sortedByDescending { 
            when (it.type) {
                Match.MatchType.LINE_5 -> 100
                Match.MatchType.T_SHAPE, Match.MatchType.L_SHAPE -> 80
                Match.MatchType.SQUARE_2X2 -> 70
                Match.MatchType.LINE_4 -> 60
                Match.MatchType.LINE_3 -> 30
            }
        }
        
        val result = mutableListOf<Match>()
        val usedPositions = mutableSetOf<Position>()
        
        for (match in sorted) {
            // Check if any position in this match is already used
            val overlapping = match.positions.any { it in usedPositions }
            
            if (!overlapping) {
                result.add(match)
                usedPositions.addAll(match.positions)
            } else {
                // For overlapping matches, we might still want to include them
                // if they create specials and the overlap is partial
                val nonOverlapping = match.positions.filter { it !in usedPositions }
                if (nonOverlapping.size >= 3 && match.type != Match.MatchType.LINE_3) {
                    // Keep the match but mark positions as used
                    result.add(match)
                    usedPositions.addAll(match.positions)
                }
            }
        }
        
        return result
    }
    
    fun hasAnyMatches(): Boolean {
        return findAllMatches().isNotEmpty()
    }
    
    fun findMatchesAt(pos: Position): List<Match> {
        return findAllMatches().filter { pos in it.positions }
    }
}
