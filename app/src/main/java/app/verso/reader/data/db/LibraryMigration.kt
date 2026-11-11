package app.verso.reader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_migrations")
data class LibraryMigration(@PrimaryKey val name: String, val complete: Boolean)
