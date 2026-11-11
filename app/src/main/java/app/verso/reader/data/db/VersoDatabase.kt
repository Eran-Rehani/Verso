package app.verso.reader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BookEntity::class, LibraryMigration::class], version = 2, exportSchema = true)
abstract class VersoDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN availability TEXT NOT NULL DEFAULT 'AVAILABLE'")
                for (column in listOf("payloadPath", "sourcePath", "locationJson", "pdfLocationJson")) {
                    db.execSQL("ALTER TABLE books ADD COLUMN $column TEXT NOT NULL DEFAULT ''")
                }
                for (column in listOf("hasOriginal", "hasResources")) {
                    db.execSQL("ALTER TABLE books ADD COLUMN $column INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL("ALTER TABLE books ADD COLUMN readingMode TEXT NOT NULL DEFAULT 'REFLOW'")
                db.execSQL("CREATE TABLE IF NOT EXISTS library_migrations (name TEXT NOT NULL PRIMARY KEY, complete INTEGER NOT NULL)")
            }
        }
        @Volatile
        private var INSTANCE: VersoDatabase? = null

        fun getInstance(context: Context): VersoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VersoDatabase::class.java,
                    "verso_library.db"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
