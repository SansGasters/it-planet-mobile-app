package com.noda.mindmap.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.noda.mindmap.data.model.ConnectionEntity
import com.noda.mindmap.data.model.NodeEntity

@Database(entities = [NodeEntity::class, ConnectionEntity::class], version = 3)
abstract class NodaDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun connectionDao(): ConnectionDao
}