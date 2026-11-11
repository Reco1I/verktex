package com.reco1l.verktex.database

import androidx.room.Room
import androidx.room.RoomDatabase

// Room's builders take the type of the database, but use it only to find its implementation when no
// factory is given, so any database type will do here.
@Suppress("UNCHECKED_CAST")
internal actual fun <T : RoomDatabase> databaseBuilder(path: String?, factory: () -> T): RoomDatabase.Builder<T> =
    if (path == null) {
        Room.inMemoryDatabaseBuilder<RoomDatabase>(factory)
    } else {
        Room.databaseBuilder<RoomDatabase>(path, factory)
    } as RoomDatabase.Builder<T>
