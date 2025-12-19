package com.match3.game.domain.model

enum class SpecialType(val emoji: String, val displayName: String) {
    NONE("", "Normal"),
    ROCKET_HORIZONTAL("➡️", "Horizontal Rocket"),  // Horizontal arrow
    ROCKET_VERTICAL("⬆️", "Vertical Rocket"),      // Vertical arrow
    DISCO_BALL("🪩", "Disco Ball"),
    PROPELLER("🌀", "Propeller"),
    BOMB("💣", "Bomb");
    
    fun isRocket(): Boolean = this == ROCKET_HORIZONTAL || this == ROCKET_VERTICAL
}
