package com.match3.game.domain.model

data class LevelConfig(
    val levelNumber: Int,
    val mode: GameMode,
    val boardWidth: Int,
    val boardHeight: Int,
    val maxTurns: Int,
    val targetScore: Int = 0,
    val numColors: Int = 4, // Number of block colors to use
    val specialCells: List<SpecialCellConfig> = emptyList(),
    val frozenZones: List<FrozenZoneConfig> = emptyList(),
    val enemies: List<EnemyConfig> = emptyList(),
    val initialBoard: List<String>? = null, // Optional initial board layout
    val seed: Long = System.currentTimeMillis()
) {
    companion object {
        // First 40 levels use 7x10 board
        private fun getBoardSize(levelNumber: Int): Pair<Int, Int> {
            return if (levelNumber <= 40) 7 to 10 else 9 to 12
        }
        
        fun createScoreLevel(
            levelNumber: Int, 
            targetScore: Int, 
            maxTurns: Int, 
            seed: Long,
            numColors: Int = 4,
            initialBoard: List<String>? = null
        ): LevelConfig {
            val (width, height) = getBoardSize(levelNumber)
            return LevelConfig(
                levelNumber = levelNumber,
                mode = GameMode.SCORE_ACCUMULATION,
                boardWidth = width,
                boardHeight = height,
                maxTurns = maxTurns,
                targetScore = targetScore,
                numColors = numColors,
                initialBoard = initialBoard,
                seed = seed
            )
        }
        
        fun createClearLevel(
            levelNumber: Int, 
            maxTurns: Int, 
            specialCells: List<SpecialCellConfig>,
            frozenZones: List<FrozenZoneConfig> = emptyList(),
            seed: Long,
            numColors: Int = 4,
            initialBoard: List<String>? = null
        ): LevelConfig {
            val (width, height) = getBoardSize(levelNumber)
            return LevelConfig(
                levelNumber = levelNumber,
                mode = GameMode.CLEAR_SPECIAL_CELLS,
                boardWidth = width,
                boardHeight = height,
                maxTurns = maxTurns,
                numColors = numColors,
                specialCells = specialCells,
                frozenZones = frozenZones,
                initialBoard = initialBoard,
                seed = seed
            )
        }
        
        fun createTowerDefenseLevel(
            levelNumber: Int,
            maxTurns: Int,
            enemies: List<EnemyConfig>,
            seed: Long,
            numColors: Int = 4,
            initialBoard: List<String>? = null
        ): LevelConfig {
            val (width, height) = getBoardSize(levelNumber)
            // TD mode uses smaller player board
            return LevelConfig(
                levelNumber = levelNumber,
                mode = GameMode.TOWER_DEFENSE,
                boardWidth = if (levelNumber <= 40) 7 else 9,
                boardHeight = if (levelNumber <= 40) 8 else 8,
                maxTurns = maxTurns,
                numColors = numColors,
                enemies = enemies,
                initialBoard = initialBoard,
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
