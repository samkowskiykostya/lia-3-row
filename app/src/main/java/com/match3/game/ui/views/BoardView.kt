package com.match3.game.ui.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.OvershootInterpolator
import com.match3.game.domain.engine.Board
import com.match3.game.domain.model.*
import kotlin.math.abs
import kotlin.math.min

class BoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface BoardInteractionListener {
        fun onSwap(pos1: Position, pos2: Position)
        fun onTap(pos: Position)
    }

    var listener: BoardInteractionListener? = null
    var board: Board? = null
        set(value) {
            field = value
            invalidate()
        }

    private var cellSize: Float = 0f
    private var boardOffsetX: Float = 0f
    private var boardOffsetY: Float = 0f

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val cellBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#3A3A6A")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#FFD700")
    }

    // Touch handling
    private var touchStartX: Float = 0f
    private var touchStartY: Float = 0f
    private var selectedPos: Position? = null
    private var isDragging: Boolean = false
    private var dragOffsetX: Float = 0f
    private var dragOffsetY: Float = 0f
    private var dragTargetPos: Position? = null
    private val swipeThreshold = 30f  // Reduced for more responsive dragging
    
    // Callback for swap attempt result
    var onSwapAttempt: ((pos1: Position, pos2: Position, onResult: (Boolean) -> Unit) -> Unit)? = null

    // Animations
    private val animatingBlocks = mutableMapOf<Position, BlockAnimation>()
    private val scorePopups = mutableListOf<ScorePopup>()
    private val explosions = mutableListOf<Explosion>()
    private val lightnings = mutableListOf<Lightning>()
    
    // Highlight state
    private val highlightedPositions = mutableSetOf<Position>()
    private var highlightColor: Int = Color.WHITE
    
    // Special forming state
    private val formingPositions = mutableSetOf<Position>()
    private var formingColor: Int = Color.WHITE
    private var formingAlpha: Float = 0f
    private var formingCreationPos: Position? = null

    data class BlockAnimation(
        var offsetX: Float = 0f,
        var offsetY: Float = 0f,
        var scale: Float = 1f,
        var alpha: Float = 1f,
        var rotation: Float = 0f
    )

    data class ScorePopup(
        var x: Float,
        var y: Float,
        val text: String,
        var alpha: Float = 1f,
        var offsetY: Float = 0f
    )

    data class Explosion(
        var x: Float,
        var y: Float,
        var radius: Float = 0f,
        var alpha: Float = 1f
    )
    
    data class Lightning(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        var progress: Float = 0f,
        var alpha: Float = 1f,
        val color: Int = Color.parseColor("#FFA500") // Orange
    )

    private val gradientColors = intArrayOf(
        Color.parseColor("#1A1A2E"),
        Color.parseColor("#16213E"),
        Color.parseColor("#0F3460")
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateDimensions()
    }

    private fun calculateDimensions() {
        val b = board ?: return
        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom

        cellSize = min(
            availableWidth.toFloat() / b.width,
            availableHeight.toFloat() / b.height
        )

        val boardWidth = cellSize * b.width
        val boardHeight = cellSize * b.height

        boardOffsetX = (width - boardWidth) / 2
        boardOffsetY = (height - boardHeight) / 2

        textPaint.textSize = cellSize * 0.6f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val b = board ?: return

        // Draw background gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                gradientColors, null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Draw cells
        for (row in 0 until b.height) {
            for (col in 0 until b.width) {
                val pos = Position(row, col)
                drawCell(canvas, pos)
            }
        }
        
        // Draw highlight overlay for matched positions
        for (pos in highlightedPositions) {
            val x = boardOffsetX + pos.col * cellSize
            val y = boardOffsetY + pos.row * cellSize
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = highlightColor
                alpha = 100
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(
                x + 2, y + 2, x + cellSize - 2, y + cellSize - 2,
                cellSize * 0.15f, cellSize * 0.15f, paint
            )
        }
        
        // Draw special forming effect
        if (formingPositions.isNotEmpty() && formingAlpha > 0) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = formingColor
                alpha = (formingAlpha * 180).toInt()
                style = Paint.Style.FILL
            }
            for (pos in formingPositions) {
                val x = boardOffsetX + pos.col * cellSize
                val y = boardOffsetY + pos.row * cellSize
                canvas.drawRoundRect(
                    x + 2, y + 2, x + cellSize - 2, y + cellSize - 2,
                    cellSize * 0.15f, cellSize * 0.15f, paint
                )
            }
        }

        // Draw lightning bolts
        for (lightning in lightnings) {
            drawLightning(canvas, lightning)
        }
        
        // Draw explosions
        for (explosion in explosions) {
            drawExplosion(canvas, explosion)
        }

        // Draw score popups
        for (popup in scorePopups) {
            drawScorePopup(canvas, popup)
        }

        // Draw selection highlight
        selectedPos?.let { pos ->
            val x = boardOffsetX + pos.col * cellSize
            val y = boardOffsetY + pos.row * cellSize
            canvas.drawRoundRect(
                x + 2, y + 2, x + cellSize - 2, y + cellSize - 2,
                cellSize * 0.15f, cellSize * 0.15f, highlightPaint
            )
        }
    }

    private fun drawCell(canvas: Canvas, pos: Position) {
        val b = board ?: return
        val cell = b.getCell(pos) ?: return

        val animation = animatingBlocks[pos]
        val offsetX = animation?.offsetX ?: 0f
        val offsetY = animation?.offsetY ?: 0f
        val scale = animation?.scale ?: 1f
        val alpha = animation?.alpha ?: 1f

        val baseX = boardOffsetX + pos.col * cellSize + offsetX
        val baseY = boardOffsetY + pos.row * cellSize + offsetY

        // Draw cell background
        val cellColor = when (cell.type) {
            CellType.NORMAL -> Color.parseColor("#2A2A4A")
            CellType.FROZEN, CellType.FROZEN_ZONE -> Color.parseColor("#4A6A8A")
            CellType.BOX -> Color.parseColor("#8B4513")
            CellType.COLOR_BOX -> getColorForBlock(cell.requiredColor)
        }

        cellPaint.color = cellColor
        cellPaint.alpha = (alpha * 255).toInt()

        val padding = cellSize * 0.05f
        canvas.drawRoundRect(
            baseX + padding, baseY + padding,
            baseX + cellSize - padding, baseY + cellSize - padding,
            cellSize * 0.15f, cellSize * 0.15f, cellPaint
        )

        // Draw cell border
        canvas.drawRoundRect(
            baseX + padding, baseY + padding,
            baseX + cellSize - padding, baseY + cellSize - padding,
            cellSize * 0.15f, cellSize * 0.15f, cellBorderPaint
        )

        // Draw block or cell content
        val emoji = cell.getDisplayEmoji()
        if (emoji.isNotEmpty()) {
            canvas.save()

            val centerX = baseX + cellSize / 2
            val centerY = baseY + cellSize / 2

            canvas.scale(scale, scale, centerX, centerY)

            textPaint.alpha = (alpha * 255).toInt()
            canvas.drawText(
                emoji,
                centerX,
                centerY + textPaint.textSize / 3,
                textPaint
            )

            canvas.restore()
        }

        // Draw durability indicator for special cells
        if (cell.type != CellType.NORMAL && cell.durability > 1) {
            val durabilityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = cellSize * 0.25f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                cell.durability.toString(),
                baseX + cellSize - cellSize * 0.2f,
                baseY + cellSize - cellSize * 0.1f,
                durabilityPaint
            )
        }
    }

    private fun getColorForBlock(color: BlockColor?): Int {
        return when (color) {
            BlockColor.RED -> Color.parseColor("#FF4444")
            BlockColor.BLUE -> Color.parseColor("#4488FF")
            BlockColor.GREEN -> Color.parseColor("#44FF44")
            BlockColor.YELLOW -> Color.parseColor("#FFFF44")
            null -> Color.parseColor("#888888")
        }
    }

    private fun drawLightning(canvas: Canvas, lightning: Lightning) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = cellSize * 0.15f
            color = lightning.color
            alpha = (lightning.alpha * 255).toInt()
            strokeCap = Paint.Cap.ROUND
        }
        
        // Draw jagged lightning bolt
        val path = android.graphics.Path()
        path.moveTo(lightning.startX, lightning.startY)
        
        val dx = lightning.endX - lightning.startX
        val dy = lightning.endY - lightning.startY
        val segments = 5
        
        for (i in 1..segments) {
            val t = i.toFloat() / segments * lightning.progress
            val x = lightning.startX + dx * t
            val y = lightning.startY + dy * t
            
            // Add random offset for jagged effect
            val offset = cellSize * 0.2f * kotlin.math.sin(i * 2.5f)
            val perpX = -dy / kotlin.math.sqrt(dx * dx + dy * dy) * offset
            val perpY = dx / kotlin.math.sqrt(dx * dx + dy * dy) * offset
            
            path.lineTo(x + perpX, y + perpY)
        }
        
        canvas.drawPath(path, paint)
        
        // Draw glow effect
        paint.strokeWidth = cellSize * 0.3f
        paint.alpha = (lightning.alpha * 100).toInt()
        canvas.drawPath(path, paint)
    }
    
    private fun drawExplosion(canvas: Canvas, explosion: Explosion) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            color = Color.parseColor("#FFD700")
            alpha = (explosion.alpha * 255).toInt()
        }
        canvas.drawCircle(explosion.x, explosion.y, explosion.radius, paint)
    }

    private fun drawScorePopup(canvas: Canvas, popup: ScorePopup) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFD700")
            textSize = cellSize * 0.4f
            textAlign = Paint.Align.CENTER
            alpha = (popup.alpha * 255).toInt()
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }
        canvas.drawText(popup.text, popup.x, popup.y + popup.offsetY, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                selectedPos = getTouchedPosition(event.x, event.y)
                isDragging = false
                dragOffsetX = 0f
                dragOffsetY = 0f
                dragTargetPos = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val pos = selectedPos ?: return true
                val deltaX = event.x - touchStartX
                val deltaY = event.y - touchStartY
                
                // Start dragging if threshold exceeded
                if (!isDragging && (abs(deltaX) > swipeThreshold || abs(deltaY) > swipeThreshold)) {
                    isDragging = true
                }
                
                if (isDragging) {
                    // Determine drag direction (lock to one axis)
                    if (abs(deltaX) > abs(deltaY)) {
                        // Horizontal drag - clamp to one cell
                        dragOffsetX = deltaX.coerceIn(-cellSize, cellSize)
                        dragOffsetY = 0f
                        dragTargetPos = if (dragOffsetX > cellSize * 0.3f) {
                            Position(pos.row, pos.col + 1)
                        } else if (dragOffsetX < -cellSize * 0.3f) {
                            Position(pos.row, pos.col - 1)
                        } else null
                    } else {
                        // Vertical drag - clamp to one cell
                        dragOffsetX = 0f
                        dragOffsetY = deltaY.coerceIn(-cellSize, cellSize)
                        dragTargetPos = if (dragOffsetY > cellSize * 0.3f) {
                            Position(pos.row + 1, pos.col)
                        } else if (dragOffsetY < -cellSize * 0.3f) {
                            Position(pos.row - 1, pos.col)
                        } else null
                    }
                    
                    // Update animation for dragged block
                    animatingBlocks[pos] = BlockAnimation(offsetX = dragOffsetX, offsetY = dragOffsetY)
                    
                    // Also move target block in opposite direction
                    dragTargetPos?.let { target ->
                        if (board?.isValidPosition(target) == true) {
                            animatingBlocks[target] = BlockAnimation(offsetX = -dragOffsetX, offsetY = -dragOffsetY)
                        }
                    }
                    
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val pos = selectedPos
                
                if (pos != null && isDragging && dragTargetPos != null) {
                    val target = dragTargetPos!!
                    
                    // Check if target is valid
                    if (board?.isValidPosition(target) == true) {
                        // Try the swap - use callback to check if valid
                        if (onSwapAttempt != null) {
                            // Animate to final position first
                            val finalOffsetX = (target.col - pos.col) * cellSize
                            val finalOffsetY = (target.row - pos.row) * cellSize
                            
                            // Check if dragged block is special
                            val draggedBlock = board?.getBlock(pos)
                            val isDraggedSpecial = draggedBlock?.isSpecial() == true
                            
                            animatingBlocks[pos] = BlockAnimation(offsetX = dragOffsetX, offsetY = dragOffsetY)
                            
                            // Only animate target if dragged block is NOT special
                            if (!isDraggedSpecial) {
                                animatingBlocks[target] = BlockAnimation(offsetX = -dragOffsetX, offsetY = -dragOffsetY)
                            }
                            
                            // Complete the swap animation
                            animateSwapCompletion(pos, target, dragOffsetX, dragOffsetY, isDraggedSpecial) { 
                                // After animation, notify listener
                                listener?.onSwap(pos, target)
                            }
                        } else {
                            listener?.onSwap(pos, target)
                            resetDragState()
                        }
                    } else {
                        // Invalid target - snap back
                        animateSnapBack(pos, dragTargetPos)
                    }
                } else if (pos != null && !isDragging) {
                    // Tap detected
                    listener?.onTap(pos)
                    resetDragState()
                } else {
                    resetDragState()
                }
                
                selectedPos = null
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                selectedPos?.let { pos ->
                    animateSnapBack(pos, dragTargetPos)
                }
                selectedPos = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    private fun resetDragState() {
        isDragging = false
        dragOffsetX = 0f
        dragOffsetY = 0f
        dragTargetPos = null
        animatingBlocks.clear()
        invalidate()
    }
    
    private fun animateSwapCompletion(pos: Position, target: Position, currentOffsetX: Float, currentOffsetY: Float, isDraggedSpecial: Boolean, onComplete: () -> Unit) {
        val finalOffsetX = (target.col - pos.col) * cellSize
        val finalOffsetY = (target.row - pos.row) * cellSize
        
        ValueAnimator.ofFloat(0f, 1f).apply {
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                val offsetX = currentOffsetX + (finalOffsetX - currentOffsetX) * progress
                val offsetY = currentOffsetY + (finalOffsetY - currentOffsetY) * progress
                animatingBlocks[pos] = BlockAnimation(offsetX = offsetX, offsetY = offsetY)
                
                // Only animate target if dragged block is not special
                if (!isDraggedSpecial) {
                    animatingBlocks[target] = BlockAnimation(offsetX = -offsetX, offsetY = -offsetY)
                }
                invalidate()
            }
            duration = 100
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animatingBlocks.remove(pos)
                    animatingBlocks.remove(target)
                    isDragging = false
                    dragTargetPos = null
                    onComplete()
                }
            })
            start()
        }
    }
    
    private fun animateSnapBack(pos: Position, target: Position?) {
        val startOffsetX = dragOffsetX
        val startOffsetY = dragOffsetY
        
        ValueAnimator.ofFloat(1f, 0f).apply {
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                animatingBlocks[pos] = BlockAnimation(offsetX = startOffsetX * progress, offsetY = startOffsetY * progress)
                target?.let {
                    if (board?.isValidPosition(it) == true) {
                        animatingBlocks[it] = BlockAnimation(offsetX = -startOffsetX * progress, offsetY = -startOffsetY * progress)
                    }
                }
                invalidate()
            }
            duration = 150
            interpolator = OvershootInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    resetDragState()
                }
            })
            start()
        }
    }
    
    /** Called when a swap was invalid - animate blocks back to original positions */
    fun animateInvalidSwap(pos1: Position, pos2: Position) {
        // Blocks are already swapped visually, animate them back
        val deltaCol = (pos2.col - pos1.col) * cellSize
        val deltaRow = (pos2.row - pos1.row) * cellSize
        
        animatingBlocks[pos1] = BlockAnimation(offsetX = deltaCol, offsetY = deltaRow)
        animatingBlocks[pos2] = BlockAnimation(offsetX = -deltaCol, offsetY = -deltaRow)
        
        ValueAnimator.ofFloat(1f, 0f).apply {
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                animatingBlocks[pos1]?.offsetX = deltaCol * progress
                animatingBlocks[pos1]?.offsetY = deltaRow * progress
                animatingBlocks[pos2]?.offsetX = -deltaCol * progress
                animatingBlocks[pos2]?.offsetY = -deltaRow * progress
                invalidate()
            }
            duration = 150
            interpolator = OvershootInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animatingBlocks.remove(pos1)
                    animatingBlocks.remove(pos2)
                }
            })
            start()
        }
    }

    private fun getTouchedPosition(x: Float, y: Float): Position? {
        val b = board ?: return null

        val col = ((x - boardOffsetX) / cellSize).toInt()
        val row = ((y - boardOffsetY) / cellSize).toInt()

        return if (row in 0 until b.height && col in 0 until b.width) {
            Position(row, col)
        } else null
    }

    // Animation methods
    fun animateSwap(pos1: Position, pos2: Position, onComplete: () -> Unit) {
        val deltaCol = (pos2.col - pos1.col) * cellSize
        val deltaRow = (pos2.row - pos1.row) * cellSize

        animatingBlocks[pos1] = BlockAnimation()
        animatingBlocks[pos2] = BlockAnimation()

        val animator1X = ObjectAnimator.ofFloat(0f, deltaCol).apply {
            addUpdateListener {
                animatingBlocks[pos1]?.offsetX = it.animatedValue as Float
                invalidate()
            }
        }
        val animator1Y = ObjectAnimator.ofFloat(0f, deltaRow).apply {
            addUpdateListener {
                animatingBlocks[pos1]?.offsetY = it.animatedValue as Float
            }
        }
        val animator2X = ObjectAnimator.ofFloat(0f, -deltaCol).apply {
            addUpdateListener {
                animatingBlocks[pos2]?.offsetX = it.animatedValue as Float
            }
        }
        val animator2Y = ObjectAnimator.ofFloat(0f, -deltaRow).apply {
            addUpdateListener {
                animatingBlocks[pos2]?.offsetY = it.animatedValue as Float
            }
        }

        AnimatorSet().apply {
            playTogether(animator1X, animator1Y, animator2X, animator2Y)
            duration = 150
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animatingBlocks.remove(pos1)
                    animatingBlocks.remove(pos2)
                    onComplete()
                }
            })
            start()
        }
    }

    fun animateDestroy(positions: Set<Position>, onComplete: () -> Unit) {
        if (positions.isEmpty()) {
            onComplete()
            return
        }

        for (pos in positions) {
            animatingBlocks[pos] = BlockAnimation()

            // Add explosion effect
            val x = boardOffsetX + pos.col * cellSize + cellSize / 2
            val y = boardOffsetY + pos.row * cellSize + cellSize / 2
            explosions.add(Explosion(x, y))
        }

        val scaleAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                for (pos in positions) {
                    animatingBlocks[pos]?.scale = scale
                    animatingBlocks[pos]?.alpha = scale
                }
                invalidate()
            }
        }

        val explosionAnimator = ValueAnimator.ofFloat(0f, cellSize).apply {
            addUpdateListener { animator ->
                val radius = animator.animatedValue as Float
                val alpha = 1f - (radius / cellSize)
                for (explosion in explosions) {
                    explosion.radius = radius
                    explosion.alpha = alpha
                }
            }
        }

        AnimatorSet().apply {
            playTogether(scaleAnimator, explosionAnimator)
            duration = 200
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    for (pos in positions) {
                        animatingBlocks.remove(pos)
                    }
                    explosions.clear()
                    onComplete()
                }
            })
            start()
        }
    }

    fun animateFall(movements: List<Pair<Position, Position>>, onComplete: () -> Unit) {
        if (movements.isEmpty()) {
            onComplete()
            return
        }

        for ((from, to) in movements) {
            val deltaRow = (from.row - to.row) * cellSize
            animatingBlocks[to] = BlockAnimation(offsetY = -deltaRow)
        }

        ValueAnimator.ofFloat(1f, 0f).apply {
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                for ((from, to) in movements) {
                    val deltaRow = (from.row - to.row) * cellSize
                    animatingBlocks[to]?.offsetY = -deltaRow * progress
                }
                invalidate()
            }
            duration = 150
            interpolator = BounceInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    for ((_, to) in movements) {
                        animatingBlocks.remove(to)
                    }
                    onComplete()
                }
            })
            start()
        }
    }
    
    /** Animate blocks falling DOWN (from top to bottom) - board already updated */
    fun animateFallDown(movements: List<Pair<Position, Position>>, onComplete: () -> Unit) {
        if (movements.isEmpty()) {
            onComplete()
            return
        }

        // Blocks are now at their final positions (to), animate them coming FROM above (from)
        for ((from, to) in movements) {
            // Block fell from 'from' to 'to', so it needs to animate from above
            val deltaRow = (from.row - to.row) * cellSize  // Negative because from.row < to.row (falling down)
            animatingBlocks[to] = BlockAnimation(offsetY = deltaRow)  // Start above final position
        }

        ValueAnimator.ofFloat(1f, 0f).apply {
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                for ((from, to) in movements) {
                    val deltaRow = (from.row - to.row) * cellSize
                    animatingBlocks[to]?.offsetY = deltaRow * progress
                }
                invalidate()
            }
            duration = 150
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    for ((_, to) in movements) {
                        animatingBlocks.remove(to)
                    }
                    onComplete()
                }
            })
            start()
        }
    }
    
    /** Quick blink animation for matched blocks - turn gray briefly */
    fun animateMatchedBlink(positions: Set<Position>, onComplete: () -> Unit) {
        if (positions.isEmpty()) {
            onComplete()
            return
        }
        
        highlightedPositions.clear()
        highlightedPositions.addAll(positions)
        highlightColor = Color.WHITE
        
        // Single white flash
        ValueAnimator.ofFloat(0f, 1f).apply {
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                highlightColor = Color.argb(
                    (value * 255).toInt(),
                    255, 255, 255
                )
                invalidate()
            }
            duration = 150
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    highlightedPositions.clear()
                    onComplete()
                }
            })
            start()
        }
    }

    fun animateSpawn(positions: List<Position>, onComplete: () -> Unit) {
        if (positions.isEmpty()) {
            onComplete()
            return
        }

        for (pos in positions) {
            animatingBlocks[pos] = BlockAnimation(scale = 0f, offsetY = -cellSize)
        }

        ValueAnimator.ofFloat(0f, 1f).apply {
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                for (pos in positions) {
                    animatingBlocks[pos]?.scale = progress
                    animatingBlocks[pos]?.offsetY = -cellSize * (1f - progress)
                }
                invalidate()
            }
            duration = 200
            interpolator = OvershootInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    for (pos in positions) {
                        animatingBlocks.remove(pos)
                    }
                    onComplete()
                }
            })
            start()
        }
    }

    fun animateSpecialCreated(pos: Position, onComplete: () -> Unit) {
        animatingBlocks[pos] = BlockAnimation(scale = 0f)

        ValueAnimator.ofFloat(0f, 1.2f, 1f).apply {
            addUpdateListener { animator ->
                animatingBlocks[pos]?.scale = animator.animatedValue as Float
                invalidate()
            }
            duration = 300
            interpolator = OvershootInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animatingBlocks.remove(pos)
                    onComplete()
                }
            })
            start()
        }
    }

    fun showScorePopup(pos: Position, score: Int, multiplier: Float) {
        val x = boardOffsetX + pos.col * cellSize + cellSize / 2
        val y = boardOffsetY + pos.row * cellSize + cellSize / 2

        val text = if (multiplier > 1f) "+$score ×${String.format("%.1f", multiplier)}" else "+$score"
        val popup = ScorePopup(x, y, text)
        scorePopups.add(popup)

        ValueAnimator.ofFloat(0f, -cellSize * 1.5f).apply {
            addUpdateListener { animator ->
                popup.offsetY = animator.animatedValue as Float
                popup.alpha = 1f - (abs(popup.offsetY) / (cellSize * 1.5f))
                invalidate()
            }
            duration = 800
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    scorePopups.remove(popup)
                }
            })
            start()
        }
    }

    fun animateRocket(pos: Position, isHorizontal: Boolean, onComplete: () -> Unit) {
        val b = board ?: run {
            onComplete()
            return
        }
        
        val centerX = boardOffsetX + pos.col * cellSize + cellSize / 2
        val centerY = boardOffsetY + pos.row * cellSize + cellSize / 2
        
        // Create lightning bolts along the row/column
        val tempLightnings = mutableListOf<Lightning>()
        
        if (isHorizontal) {
            // Horizontal lightning across the row
            val startX = boardOffsetX
            val endX = boardOffsetX + b.width * cellSize
            val lightning = Lightning(startX, centerY, endX, centerY, color = Color.parseColor("#FFA500")) // Orange
            tempLightnings.add(lightning)
            lightnings.add(lightning)
        } else {
            // Vertical lightning down the column
            val startY = boardOffsetY
            val endY = boardOffsetY + b.height * cellSize
            val lightning = Lightning(centerX, startY, centerX, endY, color = Color.parseColor("#FFA500")) // Orange
            tempLightnings.add(lightning)
            lightnings.add(lightning)
        }

        ValueAnimator.ofFloat(0f, 1f).apply {
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                for (lightning in tempLightnings) {
                    lightning.progress = progress
                    lightning.alpha = 1f - progress * 0.7f
                }
                invalidate()
            }
            duration = 150
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    for (lightning in tempLightnings) {
                        lightnings.remove(lightning)
                    }
                    onComplete()
                }
            })
            start()
        }
    }

    fun animatePropeller(from: Position, to: Position, onComplete: () -> Unit) {
        val startX = boardOffsetX + from.col * cellSize + cellSize / 2
        val startY = boardOffsetY + from.row * cellSize + cellSize / 2
        val endX = boardOffsetX + to.col * cellSize + cellSize / 2
        val endY = boardOffsetY + to.row * cellSize + cellSize / 2

        val lightning = Lightning(startX, startY, endX, endY, color = Color.parseColor("#00BFFF")) // Blue
        lightnings.add(lightning)

        ValueAnimator.ofFloat(0f, 1f).apply {
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                lightning.progress = progress
                lightning.alpha = 1f - progress * 0.5f
                invalidate()
            }
            duration = 200
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    lightnings.remove(lightning)
                    onComplete()
                }
            })
            start()
        }
    }

    fun animatePropellerCarrying(from: Position, to: Position, carryingType: SpecialType, onComplete: () -> Unit) {
        val startX = boardOffsetX + from.col * cellSize + cellSize / 2
        val startY = boardOffsetY + from.row * cellSize + cellSize / 2
        val endX = boardOffsetX + to.col * cellSize + cellSize / 2
        val endY = boardOffsetY + to.row * cellSize + cellSize / 2

        val lightning = Lightning(startX, startY, endX, endY, color = Color.parseColor("#00BFFF")) // Blue
        lightnings.add(lightning)

        ValueAnimator.ofFloat(0f, 1f).apply {
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                lightning.progress = progress
                lightning.alpha = 1f - progress * 0.5f
                invalidate()
            }
            duration = 200
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    lightnings.remove(lightning)
                    onComplete()
                }
            })
            start()
        }
    }
    
    fun highlightPositions(positions: Set<Position>, color: BlockColor) {
        highlightedPositions.clear()
        highlightedPositions.addAll(positions)
        highlightColor = getColorForBlock(color)
        invalidate()
        
        // Clear highlight after a short delay
        postDelayed({
            highlightedPositions.clear()
            invalidate()
        }, 150)
    }
    
    fun animateSpecialForming(positions: Set<Position>, type: SpecialType, creationPos: Position, onComplete: () -> Unit) {
        // Blink the forming blocks with special color
        val specialColor = when (type) {
            SpecialType.ROCKET_HORIZONTAL, SpecialType.ROCKET_VERTICAL -> Color.parseColor("#FF6600")
            SpecialType.BOMB -> Color.parseColor("#FF0000")
            SpecialType.PROPELLER -> Color.parseColor("#00FFFF")
            SpecialType.DISCO_BALL -> Color.parseColor("#FF00FF")
            else -> Color.WHITE
        }
        
        formingPositions.clear()
        formingPositions.addAll(positions)
        formingColor = specialColor
        formingCreationPos = creationPos
        
        // Blink animation
        ValueAnimator.ofFloat(0f, 1f, 0f, 1f, 0f).apply {
            addUpdateListener { animator ->
                formingAlpha = animator.animatedValue as Float
                invalidate()
            }
            duration = 400
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    formingPositions.clear()
                    formingAlpha = 0f
                    onComplete()
                }
            })
            start()
        }
    }
    
    fun animateExplosion(center: Position, positions: Set<Position>, onComplete: () -> Unit) {
        val centerX = boardOffsetX + center.col * cellSize + cellSize / 2
        val centerY = boardOffsetY + center.row * cellSize + cellSize / 2
        
        // Add central explosion
        explosions.add(Explosion(centerX, centerY))
        
        // Animate destruction of all positions
        for (pos in positions) {
            animatingBlocks[pos] = BlockAnimation()
        }
        
        val explosionAnimator = ValueAnimator.ofFloat(0f, cellSize * 2).apply {
            addUpdateListener { animator ->
                val radius = animator.animatedValue as Float
                val alpha = 1f - (radius / (cellSize * 2))
                for (explosion in explosions) {
                    explosion.radius = radius
                    explosion.alpha = alpha
                }
                for (pos in positions) {
                    animatingBlocks[pos]?.scale = 1f - (radius / (cellSize * 2))
                    animatingBlocks[pos]?.alpha = alpha
                }
                invalidate()
            }
        }
        
        explosionAnimator.duration = 350
        explosionAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                for (pos in positions) {
                    animatingBlocks.remove(pos)
                }
                explosions.clear()
                onComplete()
            }
        })
        explosionAnimator.start()
    }
    
    fun animateDisco(center: Position, color: BlockColor, positions: Set<Position>, onComplete: () -> Unit) {
        val discoColor = getColorForBlock(color)
        
        // Flash all positions with the disco color
        for (pos in positions) {
            animatingBlocks[pos] = BlockAnimation()
        }
        
        ValueAnimator.ofFloat(1f, 0f).apply {
            addUpdateListener { animator ->
                val alpha = animator.animatedValue as Float
                for (pos in positions) {
                    animatingBlocks[pos]?.alpha = alpha
                    animatingBlocks[pos]?.scale = alpha
                }
                invalidate()
            }
            duration = 400
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    for (pos in positions) {
                        animatingBlocks.remove(pos)
                    }
                    onComplete()
                }
            })
            start()
        }
    }
    
    fun showComboText(type1: SpecialType, type2: SpecialType, position: Position) {
        val emoji1 = type1.emoji
        val emoji2 = type2.emoji
        val x = boardOffsetX + position.col * cellSize + cellSize / 2
        val y = boardOffsetY + position.row * cellSize + cellSize / 2
        
        val popup = ScorePopup(x, y, "$emoji1+$emoji2")
        scorePopups.add(popup)
        
        ValueAnimator.ofFloat(0f, -cellSize * 2f).apply {
            addUpdateListener { animator ->
                popup.offsetY = animator.animatedValue as Float
                popup.alpha = 1f - (abs(popup.offsetY) / (cellSize * 2f))
                invalidate()
            }
            duration = 1000
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    scorePopups.remove(popup)
                }
            })
            start()
        }
    }
}
