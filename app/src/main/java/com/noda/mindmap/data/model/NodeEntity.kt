package com.noda.mindmap.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nodes")
data class NodeEntity(
    @PrimaryKey val id: String,
    val text: String,
    val x: Float,
    val y: Float,
    val colorHex: String = "#6C63FF",
    val textColorHex: String = "#FFFFFF",
    val radius: Float = 60f,
    val isFrozen: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
