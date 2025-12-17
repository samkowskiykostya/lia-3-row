package com.match3.game.domain.model

data class Cell(
    val row: Int,
    val col: Int,
    val type: CellType = CellType.NORMAL,
    val durability: Int = 0,
    val zoneId: Int = -1,
    val requiredColor: BlockColor? = null,
    var block: Block? = null
) {
    fun isEmpty(): Boolean = block == null && type == CellType.NORMAL
    
    fun hasBlock(): Boolean = block != null
    
    fun isBlocked(): Boolean = type != CellType.NORMAL && durability > 0
    
    fun canSwap(): Boolean = type == CellType.NORMAL && block != null
    
    fun takeDamage(damageColor: BlockColor? = null): Cell {
        if (type == CellType.NORMAL) return this
        
        return when (type) {
            CellType.FROZEN, CellType.FROZEN_ZONE -> {
                if (durability > 1) copy(durability = durability - 1)
                else copy(type = CellType.NORMAL, durability = 0)
            }
            CellType.BOX -> {
                if (durability > 1) copy(durability = durability - 1)
                else copy(type = CellType.NORMAL, durability = 0)
            }
            CellType.COLOR_BOX -> {
                if (damageColor == requiredColor) {
                    if (durability > 1) copy(durability = durability - 1)
                    else copy(type = CellType.NORMAL, durability = 0, requiredColor = null)
                } else this
            }
            else -> this
        }
    }
    
    fun getDisplayEmoji(): String {
        return when {
            type != CellType.NORMAL && durability > 0 -> {
                when (type) {
                    CellType.COLOR_BOX -> "${type.emoji}${requiredColor?.emoji ?: ""}"
                    else -> type.emoji + if (durability > 1) "$durability" else ""
                }
            }
            block != null -> block!!.getEmoji()
            else -> "⬜"
        }
    }
}
