package com.match3.game.domain.model

enum class CellType(val emoji: String, val displayName: String) {
    NORMAL("", "Normal"),
    FROZEN("❄️", "Frozen"),
    FROZEN_ZONE("❄️", "Frozen Zone"),
    BOX("📦", "Box"),
    COLOR_BOX("🎨", "Color Box");
}
