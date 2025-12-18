package com.match3.game.domain.model

data class Match(
    val positions: Set<Position>,
    val color: BlockColor,
    val type: MatchType,
    val swappedPosition: Position? = null // Position where user swiped TO (for user-initiated matches)
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
    
    /**
     * Get position where special block should be created.
     * Rules:
     * - If match was caused by user swap: create at the swapped-to position
     * - If match was caused by falling blocks: create at bottommost, leftmost position
     */
    fun getCreationPosition(): Position {
        // If user swiped to a position in this match, create special there
        if (swappedPosition != null && positions.contains(swappedPosition)) {
            return swappedPosition
        }
        
        // For cascade matches: bottommost row first, then leftmost column
        return positions.maxWithOrNull(compareBy({ it.row }, { -it.col })) ?: positions.first()
    }
}
