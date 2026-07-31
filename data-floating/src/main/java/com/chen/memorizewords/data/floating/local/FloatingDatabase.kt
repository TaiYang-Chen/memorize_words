package com.chen.memorizewords.data.floating.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingRuntimeSessionDao
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingRuntimeSessionEntity
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingWordDisplayRecordDao
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingWordDisplayRecordEntity
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingWordDisplayWordEntity

@Database(
    entities = [
        FloatingWordDisplayRecordEntity::class,
        FloatingWordDisplayWordEntity::class,
        FloatingRuntimeSessionEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class FloatingDatabase : RoomDatabase() {
    abstract fun floatingWordDisplayRecordDao(): FloatingWordDisplayRecordDao
    abstract fun floatingRuntimeSessionDao(): FloatingRuntimeSessionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS floating_runtime_session (
                        singleton_id INTEGER NOT NULL PRIMARY KEY,
                        session_id TEXT NOT NULL,
                        revision INTEGER NOT NULL,
                        phase TEXT NOT NULL,
                        source TEXT NOT NULL,
                        target_pack_id TEXT,
                        progress INTEGER NOT NULL,
                        error_code TEXT,
                        start_deadline_at_ms INTEGER,
                        last_heartbeat_at_ms INTEGER,
                        config_version INTEGER NOT NULL,
                        created_at_ms INTEGER NOT NULL,
                        updated_at_ms INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
