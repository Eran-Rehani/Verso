package app.verso.reader.data.db

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
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
    val anchorOffsetPx: Int = 0,
    @ColumnInfo(defaultValue = "'AVAILABLE'") val availability: String = "AVAILABLE",
    @ColumnInfo(defaultValue = "''") val payloadPath: String = "",
    @ColumnInfo(defaultValue = "''") val sourcePath: String = "",
    @ColumnInfo(defaultValue = "0") val hasOriginal: Boolean = false,
    @ColumnInfo(defaultValue = "0") val hasResources: Boolean = false,
    @ColumnInfo(defaultValue = "''") val locationJson: String = "",
    @ColumnInfo(defaultValue = "''") val pdfLocationJson: String = "",
    @ColumnInfo(defaultValue = "'REFLOW'") val readingMode: String = "REFLOW"
) {
    val progress: Float
        get() = if (blockCount <= 1) {
            0f
        } else {
            (anchorBlock.toFloat() / (blockCount - 1)).coerceIn(0f, 1f)
        }
}
