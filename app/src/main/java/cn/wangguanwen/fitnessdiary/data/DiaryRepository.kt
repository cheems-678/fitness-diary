package cn.wangguanwen.fitnessdiary.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

data class DraftPhoto(val id: String, val file: File)

class DiaryRepository(val context: Context, val database: DiaryDatabase) {
    val mediaDirectory = File(context.filesDir, "photos").apply { mkdirs() }
    private val draftDirectory = File(context.cacheDir, "draft-photos").apply { mkdirs() }
    internal val mutex = Mutex()
    val entries = database.diaryDao().observeEntries()

    fun photoFile(photo: DiaryPhoto) = File(mediaDirectory, photo.fileName)

    fun importPhoto(uri: Uri): DraftPhoto {
        val id = UUID.randomUUID().toString()
        val target = File(draftDirectory, "$id.img")
        try {
            context.contentResolver.openInputStream(uri)?.use { source ->
                target.outputStream().use { output -> copyLimited(source, output, MAX_PHOTO_BYTES) }
            } ?: error("无法读取照片，请重新选择")
            requireImage(target)
            return DraftPhoto(id, target)
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    fun discardDrafts(photos: List<DraftPhoto>) {
        photos.filter { it.file.parentFile == draftDirectory }.forEach { it.file.delete() }
    }

    suspend fun save(date: String, weight: Int?, note: String, photos: List<DraftPhoto>) = mutex.withLock {
        require(java.time.LocalDate.parse(date).toString() == date) { "日期格式错误" }
        require(weight == null || weight > 0) { "体重必须大于零" }
        require(weight != null || note.isNotBlank() || photos.isNotEmpty()) { "请填写体重、感想或添加照片" }
        require(photos.map { it.id }.distinct().size == photos.size) { "照片重复" }
        val newFiles = mutableListOf<File>()
        try {
            val entities = photos.mapIndexed { index, photo ->
                require(photo.file.isFile) { "照片文件不存在，请重新选择" }
                val target = if (photo.file.parentFile == mediaDirectory) photo.file else {
                    File(mediaDirectory, "${UUID.randomUUID()}.img").also {
                        newFiles += it
                        photo.file.copyTo(it)
                    }
                }
                DiaryPhoto(photo.id, date, target.name, index)
            }
            database.withTransaction {
                database.diaryDao().upsertEntry(DiaryEntry(date, weight, note))
                database.diaryDao().removePhotos(date)
                database.diaryDao().insertPhotos(entities)
            }
        } catch (error: Exception) {
            newFiles.forEach { it.delete() }
            throw error
        }
        discardDrafts(photos)
        removeUnreferencedMedia()
    }

    suspend fun delete(date: String) = mutex.withLock {
        database.diaryDao().removeEntry(date)
        removeUnreferencedMedia()
    }

    internal suspend fun removeUnreferencedMedia() {
        // 清理发生在数据库提交之后；失败不能把已成功的保存/恢复误报为失败。
        runCatching {
            val referenced = database.diaryDao().allEntries().flatMap { it.photos }.map { it.fileName }.toSet()
            mediaDirectory.listFiles()?.filter { it.isFile && it.name !in referenced }?.forEach { it.delete() }
        }
    }

    companion object { const val MAX_PHOTO_BYTES = 100L * 1024 * 1024 }
}

internal fun copyLimited(source: java.io.InputStream, output: java.io.OutputStream, maximum: Long): Long {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val count = source.read(buffer)
        if (count < 0) break
        total += count
        require(total <= maximum) { "文件过大，超出支持范围" }
        output.write(buffer, 0, count)
    }
    return total
}

internal fun requireImage(file: File) {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    require(options.outWidth > 0 && options.outHeight > 0) { "照片无法识别，请选择 JPG、PNG、WebP 等系统支持的图片" }
}
