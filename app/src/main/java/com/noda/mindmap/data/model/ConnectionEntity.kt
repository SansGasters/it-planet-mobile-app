package com.noda.mindmap.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connections")
data class ConnectionEntity(
    @PrimaryKey val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val strength: Float = 1f,
    val isDirected: Boolean = false
)
