package com.match3.game.domain.progression

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.match3.game.domain.model.*

/**
 * Loads level configurations from JSON files in assets.
 * 
 * Level file format (levels.json):
 * {
 *   "levels": [
 *     {
 *       "levelNumber": 1,
 *       "mode": "SCORE_ACCUMULATION",
 *       "boardWidth": 7,
 *       "boardHeight": 10,
 *       "maxTurns": 20,
 *       "targetScore": 60,
 *       "numColors": 4,
 *       "specialCells": [
 *         {"row": 3, "col": 2, "type": "FROZEN", "durability": 2},
 *         {"row": 4, "col": 3, "type": "COLOR_BOX", "durability": 3, "requiredColor": "RED"}
 *       ],
 *       "initialBoard": [
 *         "R B G Y R B G",
 *         "B G Y R B G Y",
 *         "..."
 *       ],
 *       "enemies": [
 *         {"type": "BASIC", "spawnTurn": 1, "spawnCol": 3, "hp": 5}
 *       ]
 *     }
 *   ]
 * }
 * 
 * Cell notation in initialBoard:
 * - "R", "B", "G", "Y" = Regular blocks (Red, Blue, Green, Yellow)
 * - "❄️2" = Frozen cell with durability 2
 * - "📦3" = Box with durability 3
 * - "🎨R2" = Color box requiring Red, durability 2
 * - "🚀H" = Horizontal rocket
 * - "🚀V" = Vertical rocket
 * - "💣" = Bomb
 * - "🌀" = Propeller
 * - "🪩" = Disco ball
 * - "." = Empty/normal cell
 */
object LevelLoader {
    
    private val gson = Gson()
    private var cachedLevels: Map<Int, LevelFileData>? = null
    
    data class LevelFileData(
        val levelNumber: Int,
        val mode: String,
        val boardWidth: Int,
        val boardHeight: Int,
        val maxTurns: Int,
        val targetScore: Int = 0,
        val numColors: Int = 4,
        val specialCells: List<SpecialCellFileData>? = null,
        val frozenZones: List<FrozenZoneFileData>? = null,
        val enemies: List<EnemyFileData>? = null,
        val initialBoard: List<String>? = null,
        val seed: Long? = null
    )
    
    data class SpecialCellFileData(
        val row: Int,
        val col: Int,
        val type: String,
        val durability: Int,
        val requiredColor: String? = null
    )
    
    data class FrozenZoneFileData(
        val zoneId: Int,
        val positions: List<PositionFileData>,
        val threshold: Int
    )
    
    data class PositionFileData(
        val row: Int,
        val col: Int
    )
    
    data class EnemyFileData(
        val type: String,
        val spawnTurn: Int,
        val spawnCol: Int,
        val hp: Int
    )
    
    data class LevelsFile(
        val levels: List<LevelFileData>
    )
    
    fun loadLevels(context: Context): Map<Int, LevelFileData> {
        if (cachedLevels != null) return cachedLevels!!
        
        return try {
            val json = context.assets.open("levels.json").bufferedReader().use { it.readText() }
            val levelsFile = gson.fromJson(json, LevelsFile::class.java)
            cachedLevels = levelsFile.levels.associateBy { it.levelNumber }
            cachedLevels!!
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    fun getLevelConfig(context: Context, levelNumber: Int): LevelConfig? {
        val levels = loadLevels(context)
        val data = levels[levelNumber] ?: return null
        
        return convertToLevelConfig(data)
    }
    
    fun hasCustomLevel(context: Context, levelNumber: Int): Boolean {
        return loadLevels(context).containsKey(levelNumber)
    }
    
    private fun convertToLevelConfig(data: LevelFileData): LevelConfig {
        val mode = GameMode.valueOf(data.mode)
        
        val specialCells = data.specialCells?.map { cell ->
            SpecialCellConfig(
                row = cell.row,
                col = cell.col,
                type = CellType.valueOf(cell.type),
                durability = cell.durability,
                requiredColor = cell.requiredColor?.let { BlockColor.valueOf(it) }
            )
        } ?: emptyList()
        
        val frozenZones = data.frozenZones?.map { zone ->
            FrozenZoneConfig(
                zoneId = zone.zoneId,
                positions = zone.positions.map { Position(it.row, it.col) },
                threshold = zone.threshold
            )
        } ?: emptyList()
        
        val enemies = data.enemies?.map { enemy ->
            EnemyConfig(
                type = EnemyType.valueOf(enemy.type),
                spawnTurn = enemy.spawnTurn,
                spawnCol = enemy.spawnCol,
                hp = enemy.hp
            )
        } ?: emptyList()
        
        return LevelConfig(
            levelNumber = data.levelNumber,
            mode = mode,
            boardWidth = data.boardWidth,
            boardHeight = data.boardHeight,
            maxTurns = data.maxTurns,
            targetScore = data.targetScore,
            numColors = data.numColors,
            specialCells = specialCells,
            frozenZones = frozenZones,
            enemies = enemies,
            initialBoard = data.initialBoard,
            seed = data.seed ?: (data.levelNumber * 12345L)
        )
    }
    
    /**
     * Parse initial board string notation to cell/block data
     */
    fun parseInitialBoard(boardLines: List<String>): List<List<CellData>> {
        return boardLines.map { line ->
            line.split(" ").filter { it.isNotBlank() }.map { token ->
                parseCellToken(token)
            }
        }
    }
    
    data class CellData(
        val cellType: CellType = CellType.NORMAL,
        val durability: Int = 1,
        val requiredColor: BlockColor? = null,
        val blockColor: BlockColor? = null,
        val specialType: SpecialType = SpecialType.NONE
    )
    
    private fun parseCellToken(token: String): CellData {
        return when {
            token == "." -> CellData()
            token == "R" -> CellData(blockColor = BlockColor.RED)
            token == "B" -> CellData(blockColor = BlockColor.BLUE)
            token == "G" -> CellData(blockColor = BlockColor.GREEN)
            token == "Y" -> CellData(blockColor = BlockColor.YELLOW)
            token.startsWith("❄️") -> {
                val durability = token.removePrefix("❄️").toIntOrNull() ?: 1
                CellData(cellType = CellType.FROZEN, durability = durability)
            }
            token.startsWith("📦") -> {
                val durability = token.removePrefix("📦").toIntOrNull() ?: 2
                CellData(cellType = CellType.BOX, durability = durability)
            }
            token.startsWith("🎨") -> {
                val rest = token.removePrefix("🎨")
                val color = when {
                    rest.startsWith("R") -> BlockColor.RED
                    rest.startsWith("B") -> BlockColor.BLUE
                    rest.startsWith("G") -> BlockColor.GREEN
                    rest.startsWith("Y") -> BlockColor.YELLOW
                    else -> BlockColor.RED
                }
                val durability = rest.drop(1).toIntOrNull() ?: 2
                CellData(cellType = CellType.COLOR_BOX, durability = durability, requiredColor = color)
            }
            token == "🚀H" -> CellData(specialType = SpecialType.ROCKET_HORIZONTAL)
            token == "🚀V" -> CellData(specialType = SpecialType.ROCKET_VERTICAL)
            token == "💣" -> CellData(specialType = SpecialType.BOMB)
            token == "🌀" -> CellData(specialType = SpecialType.PROPELLER)
            token == "🪩" -> CellData(specialType = SpecialType.DISCO_BALL)
            else -> CellData()
        }
    }
}
