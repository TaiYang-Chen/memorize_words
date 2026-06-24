package com.chen.memorizewords.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class DestructiveRoomDatabaseFactory(
    private val databaseName: String = NewArchitectureDatabase.NAME,
    private val onCreateOrOpen: (SupportSQLiteDatabase) -> Unit = {},
    private val migrations: Array<Migration> = emptyArray()
) {
    fun <T : RoomDatabase> build(
        context: Context,
        databaseClass: Class<T>,
        configure: RoomDatabase.Builder<T>.() -> RoomDatabase.Builder<T> = { this }
    ): T {
        return Room.databaseBuilder(
            context,
            databaseClass,
            databaseName
        )
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        onCreateOrOpen(db)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        onCreateOrOpen(db)
                    }
                }
            )
            .addMigrations(*migrations)
            .configure()
            .build()
    }
}
