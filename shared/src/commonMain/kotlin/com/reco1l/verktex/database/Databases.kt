package com.reco1l.verktex.database

import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.reco1l.verktex.Platform
import okio.Path

/**
 * Opens Room databases the same way on every platform: over the bundled SQLite, built from the
 * [RoomDatabaseConstructor] Room generates for the database class (`@ConstructedBy`).
 *
 * The application declares its database with Room and runs Room's compiler (KSP) for each of its
 * targets; the engine provides Room's runtime and the driver.
 */
object Databases {

    /**
     * Opens the database file at [file], creating its folder if needed.
     *
     * @param configure Configures the rest of the builder, e.g. its migrations or a destructive
     * fallback, before it's built.
     */
    fun <T : RoomDatabase> open(
        constructor: RoomDatabaseConstructor<T>,
        file: Path,
        configure: RoomDatabase.Builder<T>.() -> Unit = {}
    ): T {
        file.parent?.let { Platform.fileSystem.createDirectories(it) }
        return build(databaseBuilder(file.toString(), constructor::initialize), configure)
    }

    /**
     * Opens the database `<name>.db` kept in the application's
     * [data directory][com.reco1l.verktex.PlatformStorage.dataDirectory].
     */
    fun <T : RoomDatabase> open(
        constructor: RoomDatabaseConstructor<T>,
        name: String,
        configure: RoomDatabase.Builder<T>.() -> Unit = {}
    ): T = open(constructor, Platform.storage.dataDirectory / "$name.db", configure)

    /**
     * Opens an empty database kept in memory, which is lost once closed, e.g. for tests.
     */
    fun <T : RoomDatabase> openInMemory(
        constructor: RoomDatabaseConstructor<T>,
        configure: RoomDatabase.Builder<T>.() -> Unit = {}
    ): T = build(databaseBuilder(null, constructor::initialize), configure)


    private fun <T : RoomDatabase> build(builder: RoomDatabase.Builder<T>, configure: RoomDatabase.Builder<T>.() -> Unit): T =
        builder.setDriver(BundledSQLiteDriver()).apply(configure).build()
}

/**
 * Creates the platform's builder of a database at the file [path], or kept in memory if it's null.
 */
internal expect fun <T : RoomDatabase> databaseBuilder(path: String?, factory: () -> T): RoomDatabase.Builder<T>
