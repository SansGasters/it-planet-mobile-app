package com.noda.mindmap.presentation.canvas

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.noda.mindmap.domain.model.Node
import kotlin.math.sqrt

class GestureHandler(
    context: Context,
    private val callbacks: GestureCallbacks
) {
    interface GestureCallbacks {
        fun onNodeTap(node: Node?)
        fun onNodeLongPress(node: Node)
        fun onEmptySpaceDoubleTap(x: Float, y: Float)
        fun onCreateNodeGesture(x: Float, y: Float)
        fun onDragNode(node: Node, dx: Float, dy: Float)
        fun onDragCanvas(dx: Float, dy: Float)
        fun onScale(scaleFactor: Float, focusX: Float, focusY: Float)
    }

    var findNodeAt: (Float, Float) -> Node? = { _, _ -> null }

    private var draggedNode: Node? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var pointer1Start = Pair(0f, 0f)
    private var pointer2Start = Pair(0f, 0f)
    private var isSpreadGesture = false
    private var spreadTriggered = false

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                callbacks.onScale(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                val node = findNodeAt(e.x, e.y)
                if (node != null) callbacks.onNodeLongPress(node)
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val node = findNodeAt(e.x, e.y)
                if (node == null) callbacks.onEmptySpaceDoubleTap(e.x, e.y)
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val node = findNodeAt(e.x, e.y)
                callbacks.onNodeTap(node)
                return true
            }
        })

    fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                draggedNode = findNodeAt(event.x, event.y)
                isSpreadGesture = false
                spreadTriggered = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    pointer1Start = Pair(event.getX(0), event.getY(0))
                    pointer2Start = Pair(event.getX(1), event.getY(1))
                    isSpreadGesture = true
                    spreadTriggered = false
                    draggedNode = null
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) return true

                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY

                if (event.pointerCount == 2 && isSpreadGesture && !spreadTriggered) {
                    val midX = (event.getX(0) + event.getX(1)) / 2f
                    val midY = (event.getY(0) + event.getY(1)) / 2f
                    val spread = calcDistance(event.getX(0), event.getY(0), event.getX(1), event.getY(1))
                    val initialSpread = calcDistance(pointer1Start.first, pointer1Start.second, pointer2Start.first, pointer2Start.second)
                    if (spread - initialSpread > 80f) {
                        callbacks.onCreateNodeGesture(midX, midY)
                        spreadTriggered = true
                        isSpreadGesture = false
                    }
                } else if (event.pointerCount == 1) {
                    if (draggedNode != null) {
                        callbacks.onDragNode(draggedNode!!, dx, dy)
                    } else {
                        callbacks.onDragCanvas(dx, dy)
                    }
                }

                lastTouchX = event.x
                lastTouchY = event.y
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isSpreadGesture = false
                spreadTriggered = false
                draggedNode = null
            }

            MotionEvent.ACTION_POINTER_UP -> {
                isSpreadGesture = false
                draggedNode = null
            }
        }

        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun calcDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
}
