package com.noda.mindmap.di

import android.content.Context
import androidx.room.Room
import com.noda.mindmap.data.db.NodaDatabase
import com.noda.mindmap.data.repository.NodeRepositoryImpl
import com.noda.mindmap.domain.repository.NodeRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): NodaDatabase =
        Room.databaseBuilder(ctx, NodaDatabase::class.java, "noda_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideNodeDao(db: NodaDatabase) = db.nodeDao()

    @Provides
    fun provideConnectionDao(db: NodaDatabase) = db.connectionDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindNodeRepository(impl: NodeRepositoryImpl): NodeRepository
}
