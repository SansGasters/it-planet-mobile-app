package com.noda.mindmap.presentation.canvas

import com.noda.mindmap.domain.model.Connection
import com.noda.mindmap.domain.model.Node
import kotlin.math.*

class PhysicsEngine {

    companion object {
        private const val REPULSION = 12000f
        private const val SPRING_STRENGTH = 0.018f
        private const val SPRING_LENGTH = 280f
        private const val DAMPING = 0.92f
        private const val MAX_SPEED = 28f
        private const val MIN_DISTANCE = 90f
        private const val CENTER_GRAVITY = 0.0003f
        private const val COLLISION_RESTITUTION = 0.6f
    }

    fun applyFling(node: Node, vx: Float, vy: Float) {
        if (node.isFrozen) return
        node.vx += vx * 0.4f
        node.vy += vy * 0.4f
        // Cap fling speed
        val speed = sqrt(node.vx * node.vx + node.vy * node.vy)
        if (speed > MAX_SPEED * 1.5f) {
            node.vx = node.vx / speed * MAX_SPEED * 1.5f
            node.vy = node.vy / speed * MAX_SPEED * 1.5f
        }
    }

    fun pushFromPoint(nodes: List<Node>, x: Float, y: Float, radius: Float, force: Float) {
        for (node in nodes) {
            if (node.isFrozen) continue
            val dx = node.x - x
            val dy = node.y - y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < radius && dist > 1f) {
                val pushStrength = (1f - dist / radius) * force
                node.vx += dx / dist * pushStrength
                node.vy += dy / dist * pushStrength
            }
        }
    }

    fun step(nodes: List<Node>, connections: List<Connection>) {
        nodes.forEach { node ->
            node.pulsePhase = (node.pulsePhase + 0.04f) % (2 * PI.toFloat())
        }

        if (nodes.size < 2) return

        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                applyRepulsion(nodes[i], nodes[j])
            }
        }

        for (conn in connections) {
            val from = nodes.find { it.id == conn.fromNodeId } ?: continue
            val to = nodes.find { it.id == conn.toNodeId } ?: continue
            applySpring(from, to)
        }

        for (node in nodes) {
            if (!node.isFrozen) {
                node.vx -= node.x * CENTER_GRAVITY
                node.vy -= node.y * CENTER_GRAVITY
            }
        }

        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                applyCollision(nodes[i], nodes[j])
            }
        }

        for (node in nodes) {
            if (node.isFrozen) continue

            node.vx *= DAMPING
            node.vy *= DAMPING

            val speed = sqrt(node.vx * node.vx + node.vy * node.vy)
            if (speed > MAX_SPEED) {
                node.vx = node.vx / speed * MAX_SPEED
                node.vy = node.vy / speed * MAX_SPEED
            }

            node.x += node.vx
            node.y += node.vy
        }
    }

    private fun applyRepulsion(a: Node, b: Node) {
        if (a.isFrozen && b.isFrozen) return
        val dx = b.x - a.x
        val dy = b.y - a.y
        val distSq = dx * dx + dy * dy
        val dist = max(sqrt(distSq), MIN_DISTANCE)
        val force = REPULSION / (dist * dist)
        val fx = force * dx / dist
        val fy = force * dy / dist
        if (!a.isFrozen) { a.vx -= fx; a.vy -= fy }
        if (!b.isFrozen) { b.vx += fx; b.vy += fy }
    }

    private fun applySpring(a: Node, b: Node) {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dist = max(sqrt(dx * dx + dy * dy), 1f)
        val stretch = dist - SPRING_LENGTH
        val force = SPRING_STRENGTH * stretch
        val fx = force * dx / dist
        val fy = force * dy / dist
        if (!a.isFrozen) { a.vx += fx; a.vy += fy }
        if (!b.isFrozen) { b.vx -= fx; b.vy -= fy }
    }

    private fun applyCollision(a: Node, b: Node) {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dist = sqrt(dx * dx + dy * dy)
        val minDist = a.radius + b.radius - 10f  // slight overlap allowed

        if (dist < minDist && dist > 0.1f) {
            // Overlap resolution
            val overlap = minDist - dist
            val nx = dx / dist
            val ny = dy / dist

            if (!a.isFrozen && !b.isFrozen) {
                a.x -= nx * overlap * 0.5f
                a.y -= ny * overlap * 0.5f
                b.x += nx * overlap * 0.5f
                b.y += ny * overlap * 0.5f
            } else if (!a.isFrozen) {
                a.x -= nx * overlap
                a.y -= ny * overlap
            } else if (!b.isFrozen) {
                b.x += nx * overlap
                b.y += ny * overlap
            }

            val relVx = b.vx - a.vx
            val relVy = b.vy - a.vy
            val dotProduct = relVx * nx + relVy * ny

            if (dotProduct < 0) {
                val impulse = dotProduct * COLLISION_RESTITUTION
                if (!a.isFrozen) { a.vx += impulse * nx; a.vy += impulse * ny }
                if (!b.isFrozen) { b.vx -= impulse * nx; b.vy -= impulse * ny }
            }
        }
    }

    fun checkMerge(nodes: List<Node>): Pair<Node, Node>? {
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val dx = nodes[i].x - nodes[j].x
                val dy = nodes[i].y - nodes[j].y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < nodes[i].radius * 0.7f) {
                    return Pair(nodes[i], nodes[j])
                }
            }
        }
        return null
    }
}
