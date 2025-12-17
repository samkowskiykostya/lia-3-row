package com.match3.game.domain.engine

import com.match3.game.domain.model.BlockColor
import java.util.Random

class SeededRandom(seed: Long) {
    private val random = Random(seed)
    private var callCount = 0L
    
    fun nextBlockColor(): BlockColor {
        callCount++
        return BlockColor.entries[random.nextInt(BlockColor.entries.size)]
    }
    
    fun nextInt(bound: Int): Int {
        callCount++
        return random.nextInt(bound)
    }
    
    fun nextFloat(): Float {
        callCount++
        return random.nextFloat()
    }
    
    fun nextBoolean(): Boolean {
        callCount++
        return random.nextBoolean()
    }
    
    fun getCallCount(): Long = callCount
    
    fun fork(): SeededRandom {
        return SeededRandom(random.nextLong())
    }
}
