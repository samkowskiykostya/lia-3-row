package com.match3.game.domain.model

data class Match(
    val positions: Set<Position>,
    val color: BlockColor,
    val type: MatchType
) {
    enum class MatchType {
        LINE_3,      // 3 in a row - just clear
        LINE_4,      // 4 in a row - creates rocket
        LINE_5,      // 5 in a row - creates disco ball
        SQUARE_2X2,  // 2x2 square - creates propeller
        T_SHAPE,     // T-shape - creates bomb
        L_SHAPE      // L-shape - creates bomb
    }
    
    fun getSpecialType(isHorizontal: Boolean): SpecialType {
        return when (type) {
            MatchType.LINE_4 -> if (isHorizontal) SpecialType.ROCKET_HORIZONTAL else SpecialType.ROCKET_VERTICAL
            MatchType.LINE_5 -> SpecialType.DISCO_BALL
            MatchType.SQUARE_2X2 -> SpecialType.PROPELLER
            MatchType.T_SHAPE, MatchType.L_SHAPE -> SpecialType.BOMB
            else -> SpecialType.NONE
        }
    }
    
    fun getCreationPosition(): Position {
        // Return the center-most position for special creation
        val sortedByRow = positions.sortedBy { it.row }
        val sortedByCol = positions.sortedBy { it.col }
        val midRow = sortedByRow[sortedByRow.size / 2].row
        val midCol = sortedByCol[sortedByCol.size / 2].col
        return positions.minByOrNull { 
            kotlin.math.abs(it.row - midRow) + kotlin.math.abs(it.col - midCol) 
        } ?: positions.first()
    }
}
