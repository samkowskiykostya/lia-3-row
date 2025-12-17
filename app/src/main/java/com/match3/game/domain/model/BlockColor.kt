package com.match3.game.domain.model

enum class BlockColor(val emoji: String, val displayName: String) {
    RED("🟥", "Red"),
    BLUE("🟦", "Blue"),
    GREEN("🟩", "Green"),
    YELLOW("🟨", "Yellow");
    
    companion object {
        fun random(seed: Long, index: Int): BlockColor {
            val rng = java.util.Random(seed + index)
            return entries[rng.nextInt(entries.size)]
        }
        
        fun fromOrdinal(ordinal: Int): BlockColor = entries[ordinal % entries.size]
    }
}
