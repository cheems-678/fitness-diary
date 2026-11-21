package cn.wangguanwen.fitnessdiary.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

@Entity(tableName = "entries")
data class DiaryEntry(
    @PrimaryKey val date: String,
    val weightHundredths: Int?,
    val note: String,
)

@Entity(
    tableName = "photos",
    foreignKeys = [ForeignKey(entity = DiaryEntry::class, parentColumns = ["date"], childColumns = ["entryDate"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entryDate"), Index(value = ["fileName"], unique = true)],
)
data class DiaryPhoto(
    @PrimaryKey val id: String,
    val entryDate: String,
    val fileName: String,
    val position: Int,
)

data class EntryWithPhotos(
    @Embedded val entry: DiaryEntry,
    @Relation(parentColumn = "date", entityColumn = "entryDate") val photos: List<DiaryPhoto>,
) {
    val orderedPhotos get() = photos.sortedBy { it.position }
}

@Dao
interface DiaryDao {
    @Transaction @Query("SELECT * FROM entries ORDER BY date DESC")
    fun observeEntries(): Flow<List<EntryWithPhotos>>

    @Transaction @Query("SELECT * FROM entries ORDER BY date ASC")
    suspend fun allEntries(): List<EntryWithPhotos>

    @Upsert suspend fun upsertEntry(entry: DiaryEntry)
    @Insert suspend fun insertPhotos(photos: List<DiaryPhoto>)
    @Query("DELETE FROM photos WHERE entryDate = :date") suspend fun removePhotos(date: String)
    @Query("DELETE FROM entries WHERE date = :date") suspend fun removeEntry(date: String)
    @Query("DELETE FROM entries") suspend fun removeAllEntries()
}

@Database(entities = [DiaryEntry::class, DiaryPhoto::class], version = 1, exportSchema = false)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
}

object WeightFormat {
    fun parse(input: String): Int? {
        val text = input.trim()
        if (text.isEmpty()) return null
        require(Regex("[0-9]+(?:\\.[0-9]{1,2})?").matches(text)) { "体重请输入正数，最多保留两位小数" }
        val value = try { BigDecimal(text).movePointRight(2).intValueExact() }
        catch (_: ArithmeticException) { throw IllegalArgumentException("体重数值过大，请检查输入") }
        require(value > 0) { "体重必须大于 0 kg" }
        return value
    }

    fun display(value: Int): String = String.format(Locale.ROOT, "%d.%02d", value / 100, value % 100)

    fun change(value: Int): String = when {
        value > 0 -> "+${display(value)}"
        value < 0 -> "−${display(-value)}"
        else -> "0.00"
    }
}

fun List<EntryWithPhotos>.weightEntries(days: Int?, today: LocalDate): List<DiaryEntry> {
    val start = days?.let { today.minusDays(it.toLong() - 1) }
    return map { it.entry }.filter {
        val date = LocalDate.parse(it.date)
        it.weightHundredths != null && !date.isAfter(today) && (start == null || !date.isBefore(start))
    }.sortedBy { it.date }
}
