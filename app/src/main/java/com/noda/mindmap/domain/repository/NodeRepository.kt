package com.noda.mindmap.domain.repository

import com.noda.mindmap.domain.model.Connection
import com.noda.mindmap.domain.model.Node
import kotlinx.coroutines.flow.Flow

interface NodeRepository {
    fun getNodes(): Flow<List<Node>>
    fun getConnections(): Flow<List<Connection>>
    suspend fun saveNode(node: Node)
    suspend fun updateNode(node: Node)
    suspend fun deleteNode(nodeId: String)
    suspend fun addConnection(connection: Connection)
    suspend fun deleteConnection(connection: Connection)
    suspend fun strengthenConnection(fromId: String, toId: String)
    fun searchNodes(query: String): Flow<List<Node>>
}
