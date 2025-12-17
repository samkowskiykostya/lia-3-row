package com.match3.game.ui.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateInterpolator
import com.match3.game.domain.engine.Enemy
import com.match3.game.domain.engine.Gate
import com.match3.game.domain.engine.TowerDefenseEngine
import kotlin.math.min

class EnemyFieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var enemies: List<Enemy> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var gate: Gate? = null
        set(value) {
            field = value
            invalidate()
        }

    private var cellSize: Float = 0f
    private var fieldOffsetX: Float = 0f
    private var fieldOffsetY: Float = 0f

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#4A4A6A")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }

    private val gatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val hpBarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
    }

    private val hpBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4444")
    }

    // Projectile animations
    private val projectiles = mutableListOf<Projectile>()

    data class Projectile(
        val col: Int,
        var y: Float,
        var alpha: Float = 1f
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateDimensions()
    }

    private fun calculateDimensions() {
        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom

        // Enemy field is 4 rows + 1 gate row
        val totalRows = TowerDefenseEngine.ENEMY_FIELD_HEIGHT + 1
        val cols = TowerDefenseEngine.FIELD_WIDTH

        cellSize = min(
            availableWidth.toFloat() / cols,
            availableHeight.toFloat() / totalRows
        )

        val fieldWidth = cellSize * cols
        val fieldHeight = cellSize * totalRows

        fieldOffsetX = (width - fieldWidth) / 2
        fieldOffsetY = (height - fieldHeight) / 2

        textPaint.textSize = cellSize * 0.5f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background
        canvas.drawColor(Color.parseColor("#0A0A1A"))

        // Draw enemy field grid
        for (row in 0 until TowerDefenseEngine.ENEMY_FIELD_HEIGHT) {
            for (col in 0 until TowerDefenseEngine.FIELD_WIDTH) {
                drawEnemyCell(canvas, row, col)
            }
        }

        // Draw gate row
        drawGateRow(canvas)

        // Draw enemies
        for (enemy in enemies.filter { it.hp > 0 }) {
            drawEnemy(canvas, enemy)
        }

        // Draw projectiles
        for (projectile in projectiles) {
            drawProjectile(canvas, projectile)
        }
    }

    private fun drawEnemyCell(canvas: Canvas, row: Int, col: Int) {
        val x = fieldOffsetX + col * cellSize
        val y = fieldOffsetY + row * cellSize

        cellPaint.color = Color.parseColor("#1A1A2E")
        val padding = cellSize * 0.05f
        canvas.drawRoundRect(
            x + padding, y + padding,
            x + cellSize - padding, y + cellSize - padding,
            cellSize * 0.1f, cellSize * 0.1f, cellPaint
        )

        canvas.drawRoundRect(
            x + padding, y + padding,
            x + cellSize - padding, y + cellSize - padding,
            cellSize * 0.1f, cellSize * 0.1f, borderPaint
        )
    }

    private fun drawGateRow(canvas: Canvas) {
        val gateRow = TowerDefenseEngine.ENEMY_FIELD_HEIGHT
        val y = fieldOffsetY + gateRow * cellSize

        val g = gate ?: return

        // Draw gate background
        val healthPercent = g.durability.toFloat() / g.maxDurability

        val gateColor = when {
            healthPercent > 0.5f -> Color.parseColor("#4CAF50")
            healthPercent > 0.25f -> Color.parseColor("#FF9800")
            else -> Color.parseColor("#F44336")
        }

        gatePaint.color = gateColor

        for (col in 0 until TowerDefenseEngine.FIELD_WIDTH) {
            val x = fieldOffsetX + col * cellSize
            val padding = cellSize * 0.05f

            canvas.drawRoundRect(
                x + padding, y + padding,
                x + cellSize - padding, y + cellSize - padding,
                cellSize * 0.1f, cellSize * 0.1f, gatePaint
            )

            // Draw gate emoji in center cells
            if (col == TowerDefenseEngine.FIELD_WIDTH / 2) {
                canvas.drawText(
                    "🚪",
                    x + cellSize / 2,
                    y + cellSize / 2 + textPaint.textSize / 3,
                    textPaint
                )
            }
        }

        // Draw gate health bar
        val barWidth = cellSize * TowerDefenseEngine.FIELD_WIDTH * 0.8f
        val barHeight = cellSize * 0.15f
        val barX = fieldOffsetX + (cellSize * TowerDefenseEngine.FIELD_WIDTH - barWidth) / 2
        val barY = y + cellSize - barHeight - cellSize * 0.1f

        canvas.drawRoundRect(barX, barY, barX + barWidth, barY + barHeight, 4f, 4f, hpBarBgPaint)

        hpBarPaint.color = gateColor
        canvas.drawRoundRect(barX, barY, barX + barWidth * healthPercent, barY + barHeight, 4f, 4f, hpBarPaint)

        // Draw durability text
        val durabilityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = cellSize * 0.25f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "${g.durability}/${g.maxDurability}",
            fieldOffsetX + cellSize * TowerDefenseEngine.FIELD_WIDTH / 2,
            barY - 4f,
            durabilityPaint
        )
    }

    private fun drawEnemy(canvas: Canvas, enemy: Enemy) {
        val x = fieldOffsetX + enemy.col * cellSize
        val y = fieldOffsetY + enemy.row * cellSize

        // Draw enemy emoji
        canvas.drawText(
            enemy.getEmoji(),
            x + cellSize / 2,
            y + cellSize / 2 + textPaint.textSize / 3,
            textPaint
        )

        // Draw HP bar
        val barWidth = cellSize * 0.8f
        val barHeight = cellSize * 0.1f
        val barX = x + (cellSize - barWidth) / 2
        val barY = y + cellSize * 0.05f

        canvas.drawRoundRect(barX, barY, barX + barWidth, barY + barHeight, 2f, 2f, hpBarBgPaint)

        val healthPercent = enemy.hp.toFloat() / enemy.maxHp
        hpBarPaint.color = when {
            healthPercent > 0.5f -> Color.parseColor("#4CAF50")
            healthPercent > 0.25f -> Color.parseColor("#FF9800")
            else -> Color.parseColor("#F44336")
        }
        canvas.drawRoundRect(barX, barY, barX + barWidth * healthPercent, barY + barHeight, 2f, 2f, hpBarPaint)

        // Draw HP text
        val hpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = cellSize * 0.2f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "${enemy.hp}",
            x + cellSize / 2,
            barY + barHeight + cellSize * 0.15f,
            hpPaint
        )
    }

    private fun drawProjectile(canvas: Canvas, projectile: Projectile) {
        val x = fieldOffsetX + projectile.col * cellSize + cellSize / 2

        val projectilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFD700")
            alpha = (projectile.alpha * 255).toInt()
        }

        canvas.drawCircle(x, projectile.y, cellSize * 0.15f, projectilePaint)

        // Draw trail
        val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFA500")
            alpha = (projectile.alpha * 128).toInt()
        }
        canvas.drawLine(x, projectile.y, x, projectile.y + cellSize * 0.3f, trailPaint)
    }

    fun animateProjectile(col: Int, onComplete: () -> Unit) {
        val startY = height.toFloat()
        val endY = fieldOffsetY

        val projectile = Projectile(col, startY)
        projectiles.add(projectile)

        ValueAnimator.ofFloat(startY, endY).apply {
            addUpdateListener { animator ->
                projectile.y = animator.animatedValue as Float
                projectile.alpha = 1f - (startY - projectile.y) / (startY - endY) * 0.5f
                invalidate()
            }
            duration = 300
            interpolator = AccelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    projectiles.remove(projectile)
                    onComplete()
                }
            })
            start()
        }
    }

    fun animateEnemyDamage(enemyId: Int) {
        // Flash effect handled by redraw
        invalidate()
    }

    fun animateGateDamage() {
        // Shake effect
        val originalX = translationX
        ValueAnimator.ofFloat(0f, 10f, -10f, 5f, -5f, 0f).apply {
            addUpdateListener { animator ->
                translationX = originalX + animator.animatedValue as Float
            }
            duration = 200
            start()
        }
    }
}
