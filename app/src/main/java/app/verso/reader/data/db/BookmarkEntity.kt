package app.verso.reader.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["bookId"])]
)
data class BookmarkEntity(
    @PrimaryKey
    val id: String,
    val bookId: String,
    val blockIndex: Int,
    val offsetPx: Int = 0,
    val pageNumber: Int = 1,
    val totalPages: Int = 1,
    val sectionTitle: String = "",
    val snippet: String = "",
    val label: String = "",
    val progressFraction: Float = 0f,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun idFor(bookId: String, blockIndex: Int): String = "$bookId:$blockIndex"
    }
}
