package app.verso.reader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE availability != 'DELETED' ORDER BY lastOpenedAt DESC, id")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books")
    suspend fun getAllNow(): List<BookEntity>

    @Query("SELECT complete FROM library_migrations WHERE name = 'legacy-v2'")
    suspend fun migrationComplete(): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markMigration(migration: LibraryMigration)

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getBookById(id: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("UPDATE books SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun updateLastOpened(id: String, timestamp: Long)

    @Query("UPDATE books SET anchorBlock = :block, anchorOffsetPx = :offsetPx WHERE id = :id")
    suspend fun updateReadingPosition(id: String, block: Int, offsetPx: Int)

    @Query("UPDATE books SET availability = :status WHERE id = :id")
    suspend fun setAvailability(id: String, status: String)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: String)

    @Query("SELECT COUNT(*) FROM books")
    suspend fun getBookCount(): Int

    // --- Bookmarks ---
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY blockIndex ASC, createdAt DESC")
    fun getBookmarksForBook(bookId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY blockIndex ASC, createdAt DESC")
    suspend fun getBookmarksForBookNow(bookId: String): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND blockIndex = :blockIndex LIMIT 1")
    suspend fun getBookmarkAtBlock(bookId: String, blockIndex: Int): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("UPDATE bookmarks SET label = :label WHERE id = :id")
    suspend fun updateBookmarkLabel(id: String, label: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: String)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId AND blockIndex = :blockIndex")
    suspend fun deleteBookmarkAtBlock(bookId: String, blockIndex: Int)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteBookmarksForBook(bookId: String)
}
