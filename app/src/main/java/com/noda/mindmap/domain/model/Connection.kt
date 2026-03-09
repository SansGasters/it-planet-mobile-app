package com.noda.mindmap.domain.model

data class Connection(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    var strength: Float = 1f,
    val isDirected: Boolean = false
)
