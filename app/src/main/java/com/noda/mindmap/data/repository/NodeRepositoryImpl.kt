package com.noda.mindmap.data.repository

import android.graphics.Color
import com.noda.mindmap.data.db.ConnectionDao
import com.noda.mindmap.data.db.NodeDao
import com.noda.mindmap.data.model.ConnectionEntity
import com.noda.mindmap.data.model.NodeEntity
import com.noda.mindmap.domain.model.Connection
import com.noda.mindmap.domain.model.Node
import com.noda.mindmap.domain.repository.NodeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class NodeRepositoryImpl @Inject constructor(
    private val nodeDao: NodeDao,
    private val connectionDao: ConnectionDao
) : NodeRepository {

    override fun getNodes(): Flow<List<Node>> =
        nodeDao.getAllNodes().map { list -> list.map { it.toDomain() } }

    override fun getConnections(): Flow<List<Connection>> =
        connectionDao.getAllConnections().map { list -> list.map { it.toDomain() } }

    override fun searchNodes(query: String): Flow<List<Node>> =
        nodeDao.searchNodes(query).map { list -> list.map { it.toDomain() } }

    override suspend fun saveNode(node: Node) = nodeDao.insertNode(node.toEntity())
    override suspend fun updateNode(node: Node) = nodeDao.insertNode(node.toEntity())

    override suspend fun deleteNode(nodeId: String) {
        connectionDao.deleteConnectionsForNode(nodeId)
        nodeDao.deleteNode(NodeEntity(nodeId, "", 0f, 0f))
    }

    override suspend fun addConnection(connection: Connection) =
        connectionDao.insertConnection(connection.toEntity())

    override suspend fun deleteConnection(connection: Connection) =
        connectionDao.deleteConnection(connection.toEntity())

    override suspend fun strengthenConnection(fromId: String, toId: String) =
        connectionDao.incrementStrength(fromId, toId)

    private fun NodeEntity.toDomain() = Node(
        id = id, text = text, x = x, y = y,
        color = tryParseColor(colorHex, "#6C63FF"),
        textColor = tryParseColor(textColorHex, "#FFFFFF"),
        radius = radius.coerceIn(30f, 150f),
        isFrozen = isFrozen
    )

    private fun Node.toEntity() = NodeEntity(
        id = id, text = text, x = x, y = y,
        colorHex = colorToHex(color),
        textColorHex = colorToHex(textColor),
        radius = radius,
        isFrozen = isFrozen
    )

    private fun ConnectionEntity.toDomain() = Connection(
        id = id, fromNodeId = fromNodeId, toNodeId = toNodeId,
        strength = strength, isDirected = isDirected
    )

    private fun Connection.toEntity() = ConnectionEntity(
        id = id, fromNodeId = fromNodeId, toNodeId = toNodeId,
        strength = strength, isDirected = isDirected
    )

    private fun tryParseColor(hex: String, fallback: String): Int =
        try { Color.parseColor(hex) } catch (e: Exception) { Color.parseColor(fallback) }

    private fun colorToHex(color: Int): String =
        String.format("#%06X", 0xFFFFFF and color)
}
