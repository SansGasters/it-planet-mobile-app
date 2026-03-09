package com.noda.mindmap.data.db

import androidx.room.*
import com.noda.mindmap.data.model.ConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM connections")
    fun getAllConnections(): Flow<List<ConnectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: ConnectionEntity)

    @Delete
    suspend fun deleteConnection(connection: ConnectionEntity)

    @Query("UPDATE connections SET strength = MIN(strength + 0.1, 5.0) WHERE (fromNodeId = :a AND toNodeId = :b) OR (fromNodeId = :b AND toNodeId = :a)")
    suspend fun incrementStrength(a: String, b: String)

    @Query("DELETE FROM connections WHERE fromNodeId = :nodeId OR toNodeId = :nodeId")
    suspend fun deleteConnectionsForNode(nodeId: String)

    @Query("SELECT * FROM connections WHERE fromNodeId = :nodeId OR toNodeId = :nodeId")
    fun getConnectionsForNode(nodeId: String): Flow<List<ConnectionEntity>>
}
