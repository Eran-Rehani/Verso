package app.verso.reader.data.repository

import app.verso.reader.data.db.BookDao
import app.verso.reader.data.db.BookEntity
import kotlinx.coroutines.flow.Flow

class BookRepository(private val bookDao: BookDao) {
    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()

    suspend fun getBook(id: String): BookEntity? = bookDao.getBookById(id)

    suspend fun saveOrUpdate(
        id: String,
        title: String,
        format: String,
        blockCount: Int,
        anchorBlock: Int = 0,
        anchorOffsetPx: Int = 0
    ) {
        val existing = bookDao.getBookById(id)
        val now = System.currentTimeMillis()
        val entity = if (existing != null) {
            existing.copy(
                title = title,
                format = format,
                blockCount = blockCount,
                lastOpenedAt = now
            )
        } else {
            BookEntity(
                id = id,
                title = title,
                format = format,
                addedAt = now,
                lastOpenedAt = now,
                blockCount = blockCount,
                anchorBlock = anchorBlock,
                anchorOffsetPx = anchorOffsetPx
            )
        }
        bookDao.insertBook(entity)
    }

    suspend fun touch(id: String) {
        bookDao.updateLastOpened(id, System.currentTimeMillis())
    }

    suspend fun updateAnchor(id: String, block: Int, offsetPx: Int) {
        bookDao.updateReadingPosition(id, block, offsetPx, System.currentTimeMillis())
    }

    suspend fun delete(id: String) {
        bookDao.deleteBookById(id)
    }

    suspend fun count(): Int = bookDao.getBookCount()
}
