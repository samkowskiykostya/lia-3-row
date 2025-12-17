package com.match3.game.domain.model

data class Position(val row: Int, val col: Int) {
    fun isAdjacent(other: Position): Boolean {
        val rowDiff = kotlin.math.abs(row - other.row)
        val colDiff = kotlin.math.abs(col - other.col)
        return (rowDiff == 1 && colDiff == 0) || (rowDiff == 0 && colDiff == 1)
    }
    
    fun getAdjacent(): List<Position> = listOf(
        Position(row - 1, col),
        Position(row + 1, col),
        Position(row, col - 1),
        Position(row, col + 1)
    )
    
    fun getCross(): List<Position> = getAdjacent()
    
    fun get3x3Area(): List<Position> {
        val positions = mutableListOf<Position>()
        for (r in -1..1) {
            for (c in -1..1) {
                positions.add(Position(row + r, col + c))
            }
        }
        return positions
    }
    
    fun get5x5Area(): List<Position> {
        val positions = mutableListOf<Position>()
        for (r in -2..2) {
            for (c in -2..2) {
                positions.add(Position(row + r, col + c))
            }
        }
        return positions
    }
    
    override fun toString(): String = "($row,$col)"
}
