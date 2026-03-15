package com.noda.mindmap.domain.model

import android.graphics.Color

data class Node(
    val id: String,
    var text: String,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var color: Int = Color.parseColor("#6C63FF"),
    var textColor: Int = Color.WHITE,
    var radius: Float = 60f,
    var isFrozen: Boolean = false,
    var pulsePhase: Float = 0f,
    var notes: String = ""
)
