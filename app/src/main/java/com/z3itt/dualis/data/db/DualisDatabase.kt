package com.z3itt.dualis.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TrackEntity::class, PlaylistEntity::class, SettingEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class DualisDatabase : RoomDatabase() {
    abstract fun dao(): DualisDao
}
