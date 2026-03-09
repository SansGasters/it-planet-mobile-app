package com.noda.mindmap.presentation.canvas

import android.graphics.*
import com.noda.mindmap.domain.model.Connection
import com.noda.mindmap.domain.model.Node
import kotlin.math.*

class NodeRenderer {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL)
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        letterSpacing = 0.03f
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.argb(200, 255, 220, 80)
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }

    private val path = Path()

    // Highlighted node IDs (from search)
    var highlightedNodeIds: Set<String> = emptySet()

    fun drawConnection(canvas: Canvas, from: Node, to: Node, connection: Connection) {
        val alpha = (120 + min(connection.strength * 25f, 100f)).toInt().coerceIn(0, 255)
        val baseColor = blendColors(from.color, to.color, 0.5f)

        val midX = (from.x + to.x) / 2f
        val midY = (from.y + to.y) / 2f + 25f

        // Glow
        linePaint.color = baseColor
        linePaint.alpha = (alpha * 0.25f).toInt()
        linePaint.strokeWidth = (3f + connection.strength * 2f) * 2.5f
        linePaint.maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        path.reset(); path.moveTo(from.x, from.y); path.quadTo(midX, midY, to.x, to.y)
        canvas.drawPath(path, linePaint)

        // Sharp line
        linePaint.alpha = alpha
        linePaint.strokeWidth = 1.5f + connection.strength * 1.2f
        linePaint.maskFilter = null
        canvas.drawPath(path, linePaint)

        drawLineParticles(canvas, from, to, midX, midY, baseColor, alpha, connection)
        if (connection.isDirected) drawArrow(canvas, from, to, baseColor, alpha)
    }

    private fun drawLineParticles(canvas: Canvas, from: Node, to: Node, midX: Float, midY: Float,
                                   color: Int, alpha: Int, connection: Connection) {
        val t = (System.currentTimeMillis() % 3000L) / 3000f
        for (i in 0..2) {
            val pt = (t + i / 3f) % 1f
            val bx = bezier(from.x, midX, to.x, pt)
            val by = bezier(from.y, midY, to.y, pt)
            val size = 2.5f + connection.strength * 0.5f
            particlePaint.color = color
            particlePaint.alpha = (alpha * (0.3f + (1f - abs(pt - 0.5f) * 1.8f).coerceAtLeast(0f))).toInt().coerceIn(0,255)
            particlePaint.maskFilter = BlurMaskFilter(size * 2, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(bx, by, size, particlePaint)
        }
        particlePaint.maskFilter = null
    }

    private fun bezier(p0: Float, p1: Float, p2: Float, t: Float): Float {
        val inv = 1f - t
        return inv * inv * p0 + 2f * inv * t * p1 + t * t * p2
    }

    fun drawNode(canvas: Canvas, node: Node, isSelected: Boolean = false) {
        val pulse = sin(node.pulsePhase) * 3f
        val breathe = 1f + sin(node.pulsePhase * 0.7f) * 0.015f
        val r = (node.radius + pulse) * breathe

        val cr = Color.red(node.color)
        val cg = Color.green(node.color)
        val cb = Color.blue(node.color)

        // Search highlight ring
        if (node.id in highlightedNodeIds) {
            canvas.drawCircle(node.x, node.y, r + 10f + sin(node.pulsePhase * 3f) * 3f, highlightPaint)
        }

        // Outer glow
        glowPaint.color = Color.argb((35 + sin(node.pulsePhase) * 12).toInt().coerceIn(0, 255), cr, cg, cb)
        canvas.drawCircle(node.x, node.y, r * 2.0f, glowPaint)

        // Mid glow ring
        fillPaint.shader = RadialGradient(node.x, node.y, r * 1.55f,
            intArrayOf(Color.argb(0,cr,cg,cb), Color.argb(55,cr,cg,cb), Color.argb(0,cr,cg,cb)),
            floatArrayOf(0.5f, 0.75f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(node.x, node.y, r * 1.55f, fillPaint)

        // Main body
        fillPaint.shader = RadialGradient(
            node.x - r * 0.25f, node.y - r * 0.3f, r * 1.1f,
            intArrayOf(lightenColor(node.color, 0.6f), node.color,
                darkenColor(node.color, 0.5f), darkenColor(node.color, 0.7f)),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(node.x, node.y, r, fillPaint)

        // Top highlight
        fillPaint.shader = RadialGradient(
            node.x - r * 0.3f, node.y - r * 0.4f, r * 0.7f,
            intArrayOf(Color.argb(85,255,255,255), Color.argb(25,255,255,255), Color.argb(0,255,255,255)),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(node.x, node.y, r, fillPaint)

        // Bottom rim
        fillPaint.shader = RadialGradient(
            node.x + r * 0.2f, node.y + r * 0.5f, r * 0.6f,
            intArrayOf(Color.argb(45,255,255,255), Color.argb(0,255,255,255)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(node.x, node.y, r, fillPaint)
        fillPaint.shader = null

        // Rim stroke
        rimPaint.shader = SweepGradient(node.x, node.y,
            intArrayOf(Color.argb(0,255,255,255), Color.argb(110,255,255,255),
                Color.argb(35,cr,cg,cb), Color.argb(0,255,255,255)), null)
        canvas.drawCircle(node.x, node.y, r, rimPaint)
        rimPaint.shader = null

        // Selection ring
        if (isSelected) {
            val selAlpha = (180 + sin(node.pulsePhase * 2f) * 40).toInt()
            selectedPaint.color = Color.WHITE
            selectedPaint.alpha = selAlpha
            selectedPaint.maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(node.x, node.y, r + 7f + sin(node.pulsePhase) * 2f, selectedPaint)
            selectedPaint.maskFilter = null
        }

        // Text with custom color
        if (node.text.isNotEmpty()) drawNodeText(canvas, node, r)

        // Frozen icon
        if (node.isFrozen) {
            textPaint.textSize = r * 0.32f
            textPaint.color = Color.WHITE
            textPaint.alpha = 160
            canvas.drawText("❄", node.x, node.y - r * 0.72f, textPaint)
        }

        // Velocity trail
        val speed = sqrt(node.vx * node.vx + node.vy * node.vy)
        if (speed > 4f) {
            val trailAlpha = ((speed - 4f) / 12f * 70f).toInt().coerceIn(0, 70)
            fillPaint.shader = RadialGradient(
                node.x - node.vx * 2.5f, node.y - node.vy * 2.5f, r * 0.9f,
                intArrayOf(Color.argb(trailAlpha, cr, cg, cb), Color.TRANSPARENT),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(node.x - node.vx * 2.5f, node.y - node.vy * 2.5f, r * 0.9f, fillPaint)
            fillPaint.shader = null
        }
    }

    private fun drawNodeText(canvas: Canvas, node: Node, r: Float) {
        textPaint.textSize = when {
            node.text.length > 25 -> r * 0.26f
            node.text.length > 15 -> r * 0.30f
            node.text.length > 8  -> r * 0.34f
            else                  -> r * 0.38f
        }
        textPaint.color = node.textColor
        textPaint.alpha = 235
        textPaint.setShadowLayer(4f, 0f, 1f, Color.argb(100, 0, 0, 0))

        val words = node.text.split(" ")
        val lines = wrapText(words, r * 1.55f, textPaint)
        val lineHeight = textPaint.textSize * 1.35f
        val startY = node.y - (lines.size - 1) * lineHeight / 2f + textPaint.textSize * 0.85f
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, node.x, startY + i * lineHeight, textPaint)
        }
        textPaint.clearShadowLayer()
    }

    private fun drawArrow(canvas: Canvas, from: Node, to: Node, color: Int, alpha: Int) {
        val dx = to.x - from.x; val dy = to.y - from.y
        val angle = atan2(dy, dx)
        val tipX = to.x - cos(angle) * to.radius
        val tipY = to.y - sin(angle) * to.radius
        arrowPaint.color = color; arrowPaint.alpha = alpha
        path.reset()
        path.moveTo(tipX, tipY)
        path.lineTo(tipX - 18f * cos(angle - 0.38f), tipY - 18f * sin(angle - 0.38f))
        path.lineTo(tipX - 18f * cos(angle + 0.38f), tipY - 18f * sin(angle + 0.38f))
        path.close()
        canvas.drawPath(path, arrowPaint)
    }

    private fun wrapText(words: List<String>, maxWidth: Float, paint: Paint): List<String> {
        val lines = mutableListOf<String>(); var current = ""
        for (word in words) {
            val test = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(test) <= maxWidth) current = test
            else { if (current.isNotEmpty()) lines.add(current); current = word }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines.take(3)
    }

    private fun lightenColor(color: Int, factor: Float) = Color.rgb(
        min(255f, Color.red(color) + (255 - Color.red(color)) * factor).toInt(),
        min(255f, Color.green(color) + (255 - Color.green(color)) * factor).toInt(),
        min(255f, Color.blue(color) + (255 - Color.blue(color)) * factor).toInt()
    )
    private fun darkenColor(color: Int, factor: Float) = Color.rgb(
        (Color.red(color) * (1 - factor)).toInt(),
        (Color.green(color) * (1 - factor)).toInt(),
        (Color.blue(color) * (1 - factor)).toInt()
    )
    private fun blendColors(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1 - ratio
        return Color.rgb(
            (Color.red(c1) * inv + Color.red(c2) * ratio).toInt(),
            (Color.green(c1) * inv + Color.green(c2) * ratio).toInt(),
            (Color.blue(c1) * inv + Color.blue(c2) * ratio).toInt()
        )
    }
}
