package com.match3.game.domain.progression

import com.match3.game.domain.model.*

object LevelGenerator {
    
    fun generateLevel(levelNumber: Int): LevelConfig {
        val seed = levelNumber * 12345L
        val blockIndex = (levelNumber - 1) / 20 // Which block of 20 levels
        val levelInBlock = (levelNumber - 1) % 20
        
        return when {
            // Every 5th level in a block is Tower Defense
            levelInBlock == 4 || levelInBlock == 9 || levelInBlock == 14 || levelInBlock == 19 -> {
                generateTowerDefenseLevel(levelNumber, blockIndex, seed)
            }
            // Every 3rd level (except TD) is Clear mode
            levelInBlock % 3 == 2 -> {
                generateClearLevel(levelNumber, blockIndex, seed)
            }
            // Rest are Score mode
            else -> {
                generateScoreLevel(levelNumber, blockIndex, seed)
            }
        }
    }
    
    private fun generateScoreLevel(levelNumber: Int, blockIndex: Int, seed: Long): LevelConfig {
        val baseScore = 50 + blockIndex * 30
        val targetScore = baseScore + (levelNumber % 20) * 10
        val maxTurns = 20 + blockIndex * 2
        
        return LevelConfig.createScoreLevel(
            levelNumber = levelNumber,
            targetScore = targetScore,
            maxTurns = maxTurns,
            seed = seed
        )
    }
    
    private fun generateClearLevel(levelNumber: Int, blockIndex: Int, seed: Long): LevelConfig {
        val maxTurns = 25 + blockIndex * 3
        val specialCells = mutableListOf<SpecialCellConfig>()
        val frozenZones = mutableListOf<FrozenZoneConfig>()
        
        val rng = java.util.Random(seed)
        
        // Add frozen cells
        val frozenCount = 5 + blockIndex * 2 + (levelNumber % 5)
        for (i in 0 until frozenCount) {
            val row = rng.nextInt(10) + 2 // Avoid top rows
            val col = rng.nextInt(9)
            val durability = 1 + rng.nextInt(minOf(4, 1 + blockIndex))
            specialCells.add(SpecialCellConfig(row, col, CellType.FROZEN, durability))
        }
        
        // Add boxes in later blocks
        if (blockIndex >= 1) {
            val boxCount = 1 + blockIndex / 2
            for (i in 0 until boxCount) {
                val row = rng.nextInt(8) + 2
                val col = rng.nextInt(7) + 1
                specialCells.add(SpecialCellConfig(row, col, CellType.BOX, 2))
            }
        }
        
        // Add color boxes in even later blocks
        if (blockIndex >= 2) {
            val colorBoxCount = blockIndex / 2
            for (i in 0 until colorBoxCount) {
                val row = rng.nextInt(8) + 2
                val col = rng.nextInt(9)
                val color = BlockColor.entries[rng.nextInt(BlockColor.entries.size)]
                specialCells.add(SpecialCellConfig(row, col, CellType.COLOR_BOX, 3, color))
            }
        }
        
        // Add frozen zones in block 2+
        if (blockIndex >= 1 && levelNumber % 4 == 0) {
            val zonePositions = mutableListOf<Position>()
            val startRow = rng.nextInt(6) + 3
            val startCol = rng.nextInt(6) + 1
            
            for (r in 0..1) {
                for (c in 0..2) {
                    zonePositions.add(Position(startRow + r, startCol + c))
                    specialCells.add(SpecialCellConfig(
                        startRow + r, startCol + c,
                        CellType.FROZEN_ZONE, 1, null
                    ))
                }
            }
            
            frozenZones.add(FrozenZoneConfig(
                zoneId = 1,
                positions = zonePositions,
                threshold = 6 + blockIndex
            ))
        }
        
        return LevelConfig.createClearLevel(
            levelNumber = levelNumber,
            maxTurns = maxTurns,
            specialCells = specialCells,
            frozenZones = frozenZones,
            seed = seed
        )
    }
    
    private fun generateTowerDefenseLevel(levelNumber: Int, blockIndex: Int, seed: Long): LevelConfig {
        val maxTurns = 30 + blockIndex * 5
        val enemies = mutableListOf<EnemyConfig>()
        
        val rng = java.util.Random(seed)
        
        // Wave 1: Basic enemies
        val wave1Count = 3 + blockIndex
        for (i in 0 until wave1Count) {
            enemies.add(EnemyConfig(
                type = EnemyType.BASIC,
                spawnTurn = 1 + i * 2,
                spawnCol = rng.nextInt(9),
                hp = 2 + blockIndex
            ))
        }
        
        // Wave 2: Mix of basic and controllers
        if (blockIndex >= 1) {
            val wave2Start = wave1Count * 2 + 3
            val wave2Count = 2 + blockIndex
            for (i in 0 until wave2Count) {
                val type = if (i % 3 == 0) EnemyType.CONTROLLER else EnemyType.BASIC
                enemies.add(EnemyConfig(
                    type = type,
                    spawnTurn = wave2Start + i * 2,
                    spawnCol = rng.nextInt(9),
                    hp = if (type == EnemyType.CONTROLLER) 4 + blockIndex else 3 + blockIndex
                ))
            }
        }
        
        // Wave 3: Spawners in later blocks
        if (blockIndex >= 2) {
            val wave3Start = maxTurns - 10
            enemies.add(EnemyConfig(
                type = EnemyType.SPAWNER,
                spawnTurn = wave3Start,
                spawnCol = 4,
                hp = 6 + blockIndex * 2
            ))
        }
        
        // Boss every 20 levels
        if (levelNumber % 20 == 0) {
            enemies.add(EnemyConfig(
                type = EnemyType.BOSS,
                spawnTurn = maxTurns - 5,
                spawnCol = 4,
                hp = 15 + blockIndex * 5
            ))
        }
        
        return LevelConfig.createTowerDefenseLevel(
            levelNumber = levelNumber,
            maxTurns = maxTurns,
            enemies = enemies,
            seed = seed
        )
    }
    
    fun getLevelDescription(config: LevelConfig): String {
        return when (config.mode) {
            GameMode.SCORE_ACCUMULATION -> "Score ${config.targetScore} points in ${config.maxTurns} turns"
            GameMode.CLEAR_SPECIAL_CELLS -> "Clear all obstacles in ${config.maxTurns} turns"
            GameMode.TOWER_DEFENSE -> "Defend the gate for ${config.maxTurns} turns"
        }
    }
    
    fun getLevelEmoji(config: LevelConfig): String {
        return when (config.mode) {
            GameMode.SCORE_ACCUMULATION -> "🎯"
            GameMode.CLEAR_SPECIAL_CELLS -> "❄️"
            GameMode.TOWER_DEFENSE -> "⚔️"
        }
    }
}
