package com.match3.game.domain.model

data class LevelConfig(
    val levelNumber: Int,
    val mode: GameMode,
    val boardWidth: Int,
    val boardHeight: Int,
    val maxTurns: Int,
    val targetScore: Int = 0,
    val specialCells: List<SpecialCellConfig> = emptyList(),
    val frozenZones: List<FrozenZoneConfig> = emptyList(),
    val enemies: List<EnemyConfig> = emptyList(),
    val seed: Long = System.currentTimeMillis()
) {
    companion object {
        fun createScoreLevel(levelNumber: Int, targetScore: Int, maxTurns: Int, seed: Long): LevelConfig {
            return LevelConfig(
                levelNumber = levelNumber,
                mode = GameMode.SCORE_ACCUMULATION,
                boardWidth = 9,
                boardHeight = 12,
                maxTurns = maxTurns,
                targetScore = targetScore,
                seed = seed
            )
        }
        
        fun createClearLevel(
            levelNumber: Int, 
            maxTurns: Int, 
            specialCells: List<SpecialCellConfig>,
            frozenZones: List<FrozenZoneConfig> = emptyList(),
            seed: Long
        ): LevelConfig {
            return LevelConfig(
                levelNumber = levelNumber,
                mode = GameMode.CLEAR_SPECIAL_CELLS,
                boardWidth = 9,
                boardHeight = 12,
                maxTurns = maxTurns,
                specialCells = specialCells,
                frozenZones = frozenZones,
                seed = seed
            )
        }
        
        fun createTowerDefenseLevel(
            levelNumber: Int,
            maxTurns: Int,
            enemies: List<EnemyConfig>,
            seed: Long
        ): LevelConfig {
            return LevelConfig(
                levelNumber = levelNumber,
                mode = GameMode.TOWER_DEFENSE,
                boardWidth = 9,
                boardHeight = 8,
                maxTurns = maxTurns,
                enemies = enemies,
                seed = seed
            )
        }
    }
}

data class SpecialCellConfig(
    val row: Int,
    val col: Int,
    val type: CellType,
    val durability: Int,
    val requiredColor: BlockColor? = null
)

data class FrozenZoneConfig(
    val zoneId: Int,
    val positions: List<Position>,
    val threshold: Int
)

data class EnemyConfig(
    val type: EnemyType,
    val spawnTurn: Int,
    val spawnCol: Int,
    val hp: Int
)

enum class EnemyType(val emoji: String, val displayName: String) {
    BASIC("👾", "Basic"),
    CONTROLLER("🤖", "Controller"),
    SPAWNER("🥚", "Spawner"),
    BOSS("👹", "Boss")
}
