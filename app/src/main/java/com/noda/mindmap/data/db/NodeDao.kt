package com.noda.mindmap.data.db

import androidx.room.*
import com.noda.mindmap.data.model.NodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes ORDER BY createdAt")
    fun getAllNodes(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes WHERE text LIKE '%' || :query || '%' ORDER BY createdAt")
    fun searchNodes(query: String): Flow<List<NodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: NodeEntity)

    @Update
    suspend fun updateNode(node: NodeEntity)

    @Delete
    suspend fun deleteNode(node: NodeEntity)

    @Query("SELECT * FROM nodes WHERE id = :id")
    suspend fun getNodeById(id: String): NodeEntity?
}