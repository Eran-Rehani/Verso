package app.verso.reader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val format: String,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val blockCount: Int = 0,
    val anchorBlock: Int = 0,
    val anchorOffsetPx: Int = 0
) {
    val progress: Float
        get() = if (blockCount <= 1) {
            0f
        } else {
            (anchorBlock.toFloat() / (blockCount - 1)).coerceIn(0f, 1f)
        }
}
