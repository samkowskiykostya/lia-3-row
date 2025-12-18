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
    private val swipeThreshold = 50f

    // Animations
    private val animatingBlocks = mutableMapOf<Position, BlockAnimation>()
    private val scorePopups = mutableListOf<ScorePopup>()
    private val explosions = mutableListOf<Explosion>()

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
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val pos = getTouchedPosition(touchStartX, touchStartY)
                if (pos != null) {
                    val deltaX = event.x - touchStartX
                    val deltaY = event.y - touchStartY

                    if (abs(deltaX) > swipeThreshold || abs(deltaY) > swipeThreshold) {
                        // Swipe detected
                        val targetPos = when {
                            abs(deltaX) > abs(deltaY) -> {
                                if (deltaX > 0) Position(pos.row, pos.col + 1)
                                else Position(pos.row, pos.col - 1)
                            }
                            else -> {
                                if (deltaY > 0) Position(pos.row + 1, pos.col)
                                else Position(pos.row - 1, pos.col)
                            }
                        }
                        listener?.onSwap(pos, targetPos)
                    } else {
                        // Tap detected
                        listener?.onTap(pos)
                    }
                }
                selectedPos = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                selectedPos = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
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
        // Simple flash effect for now
        val positions = mutableSetOf<Position>()
        val b = board ?: run {
            onComplete()
            return
        }

        if (isHorizontal) {
            for (col in 0 until b.width) {
                positions.add(Position(pos.row, col))
            }
        } else {
            for (row in 0 until b.height) {
                positions.add(Position(row, pos.col))
            }
        }

        animateDestroy(positions, onComplete)
    }

    fun animatePropeller(from: Position, to: Position, onComplete: () -> Unit) {
        animatingBlocks[from] = BlockAnimation()

        val startX = boardOffsetX + from.col * cellSize
        val startY = boardOffsetY + from.row * cellSize
        val endX = boardOffsetX + to.col * cellSize
        val endY = boardOffsetY + to.row * cellSize

        ValueAnimator.ofFloat(0f, 1f).apply {
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                animatingBlocks[from]?.offsetX = (endX - startX) * progress
                animatingBlocks[from]?.offsetY = (endY - startY) * progress
                animatingBlocks[from]?.rotation = progress * 720f
                invalidate()
            }
            duration = 600
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animatingBlocks.remove(from)
                    onComplete()
                }
            })
            start()
        }
    }

    fun animatePropellerCarrying(from: Position, to: Position, carryingType: SpecialType, onComplete: () -> Unit) {
        // Animate propeller flying while carrying another special block
        animatingBlocks[from] = BlockAnimation()

        val startX = boardOffsetX + from.col * cellSize
        val startY = boardOffsetY + from.row * cellSize
        val endX = boardOffsetX + to.col * cellSize
        val endY = boardOffsetY + to.row * cellSize

        // Draw the carried item emoji during animation
        val carriedEmoji = when {
            carryingType.isRocket() -> "🚀"
            carryingType == SpecialType.BOMB -> "💣"
            else -> "🌀"
        }

        ValueAnimator.ofFloat(0f, 1f).apply {
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                animatingBlocks[from]?.offsetX = (endX - startX) * progress
                animatingBlocks[from]?.offsetY = (endY - startY) * progress
                animatingBlocks[from]?.rotation = progress * 720f
                invalidate()
            }
            duration = 600
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animatingBlocks.remove(from)
                    // Add explosion at destination
                    val x = endX + cellSize / 2
                    val y = endY + cellSize / 2
                    explosions.add(Explosion(x, y))
                    onComplete()
                }
            })
            start()
        }
    }
}
