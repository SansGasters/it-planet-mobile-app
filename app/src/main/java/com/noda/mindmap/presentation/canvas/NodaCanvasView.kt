package com.noda.mindmap.presentation.canvas

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import com.noda.mindmap.domain.model.Connection
import com.noda.mindmap.domain.model.Node
import kotlinx.coroutines.*
import java.util.UUID
import kotlin.math.*

class NodaCanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val nodes = mutableListOf<Node>()
    private val connections = mutableListOf<Connection>()
    private var selectedNode: Node? = null

    // Connection mode: long press sets sourceNode, next tap on another node creates connection
    private var connectionSourceNode: Node? = null

    private var cameraX = 0f
    private var cameraY = 0f
    private var cameraScale = 1f
    private var lastFocusX = 0f
    private var lastFocusY = 0f

    private val physics = PhysicsEngine()
    private val renderer = NodeRenderer()

    private val bgPaint = Paint().apply { color = Color.parseColor("#060610") }
    private val nebulaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(120f, BlurMaskFilter.Blur.NORMAL)
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL
    }

    // Paint for drawing the "pending connection" line
    private val pendingLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(160, 108, 99, 255)
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
        maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
    }
    private val pendingDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(200, 108, 99, 255)
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }

    private var stars: List<FloatArray> = emptyList()
    private val nebulae = mutableListOf<FloatArray>()
    private var mergeDialogShown = false

    private var draggedNode: Node? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var velocityTracker: VelocityTracker? = null
    private var isSpreadGesture = false
    private var spreadTriggered = false
    private var pointer1Start = Pair(0f, 0f)
    private var pointer2Start = Pair(0f, 0f)

    // Track current touch position for the pending connection line
    private var currentTouchWorldX = 0f
    private var currentTouchWorldY = 0f

    private var lastDoubleTapX = 0f
    private var lastDoubleTapY = 0f
    private var doubleTapPulse = 0f

    private val animScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var onNodeCreated: ((Node) -> Unit)? = null
    var onNodeSelected: ((Node?) -> Unit)? = null
    var onNodeDoubleTapped: ((Node) -> Unit)? = null
    var onConnectionCreated: ((Connection) -> Unit)? = null
    var onConnectionDeleted: ((Connection) -> Unit)? = null
    var onNodesMergeRequested: ((Node, Node) -> Unit)? = null
    var onNodeLongPressed: ((Node) -> Unit)? = null
    var onConnectionModeChanged: ((Boolean) -> Unit)? = null  // true = entered, false = cancelled

    init { startAnimationLoop() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (stars.isEmpty()) { stars = generateStars(w, h); generateNebulae(w, h) }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    fun setNodes(newNodes: List<Node>) {
        val newIds = newNodes.map { it.id }.toSet()
        nodes.removeIf { it.id !in newIds }
        val existingIds = nodes.map { it.id }.toSet()
        newNodes.filter { it.id !in existingIds }.forEach { nodes.add(it) }
        newNodes.forEach { updated ->
            val existing = nodes.find { it.id == updated.id } ?: return@forEach
            existing.text = updated.text
            existing.color = updated.color
            existing.textColor = updated.textColor
            existing.radius = updated.radius
            existing.isFrozen = updated.isFrozen
        }
        selectedNode?.let { sel -> selectedNode = nodes.find { it.id == sel.id } }
    }

    fun setConnections(newConnections: List<Connection>) {
        connections.clear(); connections.addAll(newConnections)
    }

    fun removeNode(nodeId: String) {
        if (draggedNode?.id == nodeId) draggedNode = null
        if (connectionSourceNode?.id == nodeId) cancelConnectionMode()
        nodes.removeIf { it.id == nodeId }
        connections.removeIf { it.fromNodeId == nodeId || it.toNodeId == nodeId }
        if (selectedNode?.id == nodeId) selectedNode = null
        invalidate()
    }

    fun updateNodeColor(nodeId: String, color: Int) { nodes.find { it.id == nodeId }?.color = color; invalidate() }
    fun updateNodeTextColor(nodeId: String, color: Int) { nodes.find { it.id == nodeId }?.textColor = color; invalidate() }
    fun updateNodeRadius(nodeId: String, radius: Float) { nodes.find { it.id == nodeId }?.radius = radius.coerceIn(30f, 150f); invalidate() }
    fun toggleFreezeNode(nodeId: String) { nodes.find { it.id == nodeId }?.let { it.isFrozen = !it.isFrozen }; invalidate() }
    fun updateNodeText(nodeId: String, text: String) { nodes.find { it.id == nodeId }?.text = text; invalidate() }
    fun setHighlightedNodes(ids: Set<String>) { renderer.highlightedNodeIds = ids; invalidate() }

    fun focusOnNode(nodeId: String) {
        val node = nodes.find { it.id == nodeId } ?: return
        cameraX = -node.x * cameraScale
        cameraY = -node.y * cameraScale
        invalidate()
    }

    fun resetMergeDialog() { mergeDialogShown = false }

    fun cancelConnectionMode() {
        connectionSourceNode = null
        onConnectionModeChanged?.invoke(false)
        invalidate()
    }

    // Delete connection between two nodes
    fun deleteConnectionBetween(fromId: String, toId: String) {
        val conn = connections.find {
            (it.fromNodeId == fromId && it.toNodeId == toId) ||
            (it.fromNodeId == toId && it.toNodeId == fromId)
        } ?: return
        connections.remove(conn)
        onConnectionDeleted?.invoke(conn)
        invalidate()
    }

    fun getConnectionsForNode(nodeId: String): List<Connection> =
        connections.filter { it.fromNodeId == nodeId || it.toNodeId == nodeId }

    fun isInConnectionMode() = connectionSourceNode != null

    // ─── Drawing ──────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawNebulae(canvas)
        drawStars(canvas)
        if (doubleTapPulse > 0f) drawTapRipple(canvas)

        canvas.save()
        canvas.translate(width / 2f + cameraX, height / 2f + cameraY)
        canvas.scale(cameraScale, cameraScale)

        for (conn in connections) {
            val from = nodes.find { it.id == conn.fromNodeId } ?: continue
            val to = nodes.find { it.id == conn.toNodeId } ?: continue
            renderer.drawConnection(canvas, from, to, conn)
        }

        // Draw pending connection line from source to current touch
        val src = connectionSourceNode
        if (src != null) {
            drawPendingConnection(canvas, src)
        }

        for (node in nodes) {
            val isSource = node.id == connectionSourceNode?.id
            renderer.drawNode(canvas, node, node.id == selectedNode?.id, isSource)
        }

        canvas.restore()
    }

    private fun drawPendingConnection(canvas: Canvas, src: Node) {
        val tx = currentTouchWorldX
        val ty = currentTouchWorldY

        // Dashed line from source to finger
        canvas.drawLine(src.x, src.y, tx, ty, pendingLinePaint)

        // Pulsing dot at finger position
        val pulse = 0.5f + 0.5f * sin(System.currentTimeMillis() * 0.005f)
        pendingDotPaint.alpha = (140 + (pulse * 80).toInt())
        canvas.drawCircle(tx, ty, 12f + pulse * 4f, pendingDotPaint)
    }

    private fun drawNebulae(canvas: Canvas) {
        for (n in nebulae) {
            nebulaPaint.color = Color.argb(n[6].toInt(), n[3].toInt(), n[4].toInt(), n[5].toInt())
            canvas.drawCircle(n[0], n[1], n[2], nebulaPaint)
        }
    }

    private fun drawStars(canvas: Canvas) {
        for (star in stars) {
            val twinkle = 0.7f + sin(star[4] + System.currentTimeMillis() * 0.001f * star[5]) * 0.3f
            starPaint.alpha = (star[3] * twinkle).toInt().coerceIn(0, 255)
            canvas.drawCircle(star[0], star[1], star[2], starPaint)
        }
    }

    private fun drawTapRipple(canvas: Canvas) {
        val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f
            color = Color.argb((doubleTapPulse * 110).toInt(), 108, 99, 255)
            maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(lastDoubleTapX, lastDoubleTapY, (1f - doubleTapPulse) * 140f, rp)
        doubleTapPulse -= 0.04f
        if (doubleTapPulse < 0f) doubleTapPulse = 0f
    }

    private fun generateStars(w: Int, h: Int) = (0..200).map {
        floatArrayOf((Math.random()*w).toFloat(), (Math.random()*h).toFloat(),
            (Math.random()*1.8+0.2).toFloat(), (Math.random()*180+40).toFloat(),
            (Math.random()*6.28f).toFloat(), (Math.random()*0.5f+0.3f).toFloat())
    }

    private fun generateNebulae(w: Int, h: Int) {
        val colors = listOf(Triple(60,50,140), Triple(20,80,120), Triple(80,30,100), Triple(20,100,80))
        repeat(4) { i ->
            val c = colors[i]
            nebulae.add(floatArrayOf((Math.random()*w).toFloat(), (Math.random()*h).toFloat(),
                (Math.random()*200+150).toFloat(), c.first.toFloat(), c.second.toFloat(),
                c.third.toFloat(), (Math.random()*22+8).toFloat()))
        }
    }

    // ─── Animation Loop ───────────────────────────────────────────────────────

    private fun startAnimationLoop() {
        animScope.launch {
            while (isActive) {
                physics.step(nodes, connections)
                if (!mergeDialogShown) {
                    physics.checkMerge(nodes)?.let {
                        mergeDialogShown = true
                        onNodesMergeRequested?.invoke(it.first, it.second)
                    }
                }
                invalidate()
                delay(16L)
            }
        }
    }

    // ─── Gestures ─────────────────────────────────────────────────────────────

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
                lastFocusX = d.focusX; lastFocusY = d.focusY; return true
            }
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val oldScale = cameraScale
                val newScale = (cameraScale * d.scaleFactor).coerceIn(0.15f, 4f)
                val focusX = d.focusX; val focusY = d.focusY
                val worldX = (focusX - width / 2f - cameraX) / oldScale
                val worldY = (focusY - height / 2f - cameraY) / oldScale
                cameraScale = newScale
                cameraX = focusX - width / 2f - worldX * newScale
                cameraY = focusY - height / 2f - worldY * newScale
                cameraX += focusX - lastFocusX
                cameraY += focusY - lastFocusY
                lastFocusX = focusX; lastFocusY = focusY
                lastTouchX = focusX; lastTouchY = focusY
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val node = findNodeAtScreen(e.x, e.y)
                if (node != null) {
                    if (connectionSourceNode != null) {
                        cancelConnectionMode()
                    } else {
                        onNodeDoubleTapped?.invoke(node)
                    }
                } else {
                    if (connectionSourceNode != null) {
                        cancelConnectionMode()
                    } else {
                        val (wx, wy) = screenToWorld(e.x, e.y)
                        createNewNode(wx, wy)
                        physics.pushFromPoint(nodes, wx, wy, 200f, 8f)
                        lastDoubleTapX = e.x; lastDoubleTapY = e.y; doubleTapPulse = 1f
                    }
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val node = findNodeAtScreen(e.x, e.y)
                val src = connectionSourceNode

                if (src != null) {
                    // We're in connection mode
                    if (node != null && node.id != src.id) {
                        // Tap on different node → create connection
                        createConnectionBetween(src.id, node.id)
                        cancelConnectionMode()
                    } else if (node == null) {
                        // Tap on empty space → cancel
                        cancelConnectionMode()
                    }
                    // Tap on source node itself → stay in mode
                } else {
                    selectedNode = node
                    onNodeSelected?.invoke(node)
                    if (node != null) {
                        val (wx, wy) = screenToWorld(e.x, e.y)
                        physics.pushFromPoint(nodes.filter { it.id != node.id }, wx, wy, 150f, 3f)
                    }
                }
                invalidate()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val node = findNodeAtScreen(e.x, e.y)
                if (node != null) {
                    if (connectionSourceNode == null) {
                        // Enter connection mode
                        connectionSourceNode = node
                        selectedNode = node
                        val (wx, wy) = screenToWorld(e.x, e.y)
                        currentTouchWorldX = wx
                        currentTouchWorldY = wy
                        onConnectionModeChanged?.invoke(true)
                        invalidate()
                    } else {
                        // Second long press → open context menu
                        selectedNode = node
                        onNodeLongPressed?.invoke(node)
                    }
                }
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (connectionSourceNode == null) {
                    draggedNode?.let { physics.applyFling(it, vx / 60f, vy / 60f); draggedNode = null }
                }
                return true
            }
        })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        // Track touch position for pending connection line
        if (connectionSourceNode != null && event.pointerCount == 1) {
            val (wx, wy) = screenToWorld(event.x, event.y)
            currentTouchWorldX = wx
            currentTouchWorldY = wy
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x; lastTouchY = event.y
                draggedNode = if (connectionSourceNode == null) findNodeAtScreen(event.x, event.y) else null
                isSpreadGesture = false; spreadTriggered = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    // Two fingers cancels connection mode
                    if (connectionSourceNode != null) cancelConnectionMode()
                    pointer1Start = Pair(event.getX(0), event.getY(0))
                    pointer2Start = Pair(event.getX(1), event.getY(1))
                    isSpreadGesture = true; spreadTriggered = false; draggedNode = null
                    lastTouchX = (event.getX(0) + event.getX(1)) / 2f
                    lastTouchY = (event.getY(0) + event.getY(1)) / 2f
                }
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                if (scaleDetector.isInProgress) return true
                val dx = event.x - lastTouchX; val dy = event.y - lastTouchY
                if (event.pointerCount == 2 && isSpreadGesture && !spreadTriggered) {
                    val midX = (event.getX(0)+event.getX(1))/2f
                    val midY = (event.getY(0)+event.getY(1))/2f
                    val spread = dist(event.getX(0),event.getY(0),event.getX(1),event.getY(1))
                    val init = dist(pointer1Start.first,pointer1Start.second,pointer2Start.first,pointer2Start.second)
                    if (spread - init > 80f) {
                        val (wx,wy) = screenToWorld(midX, midY)
                        createNewNode(wx, wy); spreadTriggered = true; isSpreadGesture = false
                    }
                } else if (event.pointerCount == 1) {
                    if (draggedNode != null && connectionSourceNode == null) {
                        draggedNode!!.x += dx/cameraScale; draggedNode!!.y += dy/cameraScale
                        draggedNode!!.vx = dx/cameraScale*0.5f; draggedNode!!.vy = dy/cameraScale*0.5f
                    } else if (connectionSourceNode == null) {
                        cameraX += dx; cameraY += dy
                    }
                    // In connection mode: only update the pending line (already done above)
                }
                lastTouchX = event.x; lastTouchY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.apply {
                    computeCurrentVelocity(1000)
                    if (connectionSourceNode == null) {
                        draggedNode?.let { node ->
                            val speed = sqrt(xVelocity*xVelocity + yVelocity*yVelocity)
                            if (speed > 200f) physics.applyFling(node, xVelocity/60f, yVelocity/60f)
                        }
                    }
                    recycle()
                }
                velocityTracker = null; isSpreadGesture = false; spreadTriggered = false; draggedNode = null
            }
            MotionEvent.ACTION_POINTER_UP -> {
                isSpreadGesture = false; draggedNode = null
                val liftedIndex = event.actionIndex
                val remainingIndex = if (liftedIndex == 0) 1 else 0
                if (remainingIndex < event.pointerCount) {
                    lastTouchX = event.getX(remainingIndex)
                    lastTouchY = event.getY(remainingIndex)
                }
            }
        }
        return true
    }

    private fun createNewNode(x: Float, y: Float) {
        val node = Node(id = UUID.randomUUID().toString(), text = "", x = x, y = y,
            vx = ((-2)..2).random().toFloat(), vy = ((-2)..2).random().toFloat())
        nodes.add(node); selectedNode = node
        onNodeCreated?.invoke(node); invalidate()
    }

    fun createConnectionBetween(fromId: String, toId: String) {
        if (connections.any {
                (it.fromNodeId == fromId && it.toNodeId == toId) ||
                (it.fromNodeId == toId && it.toNodeId == fromId)
            }) return
        val conn = Connection(id = UUID.randomUUID().toString(), fromNodeId = fromId, toNodeId = toId)
        connections.add(conn); onConnectionCreated?.invoke(conn); invalidate()
    }

    private fun findNodeAtScreen(sx: Float, sy: Float): Node? {
        val (wx,wy) = screenToWorld(sx,sy)
        return nodes.find { sqrt((it.x-wx).pow(2)+(it.y-wy).pow(2)) <= it.radius+16f }
    }
    private fun screenToWorld(sx: Float, sy: Float) =
        Pair((sx-width/2f-cameraX)/cameraScale, (sy-height/2f-cameraY)/cameraScale)
    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float) =
        sqrt((x2-x1).pow(2)+(y2-y1).pow(2))

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        velocityTracker?.recycle(); animScope.cancel()
    }
}
