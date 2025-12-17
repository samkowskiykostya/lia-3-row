package com.match3.game.domain.model

data class Block(
    val color: BlockColor,
    val specialType: SpecialType = SpecialType.NONE,
    val id: Long = System.nanoTime()
) {
    fun getEmoji(): String {
        return when {
            specialType != SpecialType.NONE -> specialType.emoji
            else -> color.emoji
        }
    }
    
    fun withSpecial(type: SpecialType): Block = copy(specialType = type)
    
    fun isSpecial(): Boolean = specialType != SpecialType.NONE
    
    fun isRocket(): Boolean = specialType.isRocket()
    
    fun isDisco(): Boolean = specialType == SpecialType.DISCO_BALL
    
    fun isPropeller(): Boolean = specialType == SpecialType.PROPELLER
    
    fun isBomb(): Boolean = specialType == SpecialType.BOMB
}
