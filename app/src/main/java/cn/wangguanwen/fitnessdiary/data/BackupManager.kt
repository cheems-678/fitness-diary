package cn.wangguanwen.fitnessdiary.data

import android.net.Uri
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import androidx.room.withTransaction

data class RestoreRecord(val entry: DiaryEntry, val photos: List<RestorePhoto>)
data class RestorePhoto(val id: String, val file: File, val position: Int)
data class PendingRestore(val directory: File, val records: List<RestoreRecord>) {
    val photoCount get() = records.sumOf { it.photos.size }
    fun discard() { directory.deleteRecursively() }
}

class BackupManager(private val repository: DiaryRepository) {
    suspend fun export(uri: Uri) {
        repository.context.contentResolver.openOutputStream(uri, "wt")?.use { exportTo(it) }
            ?: error("无法写入该位置，请重新选择")
    }

    suspend fun exportTo(output: OutputStream) = repository.mutex.withLock {
        val entries = repository.database.diaryDao().allEntries()
        val records = JSONArray()
        entries.forEach { record ->
            val photos = JSONArray()
            record.orderedPhotos.forEach { photo ->
                val file = repository.photoFile(photo)
                require(file.isFile) { "${record.entry.date} 的照片丢失，备份未完成" }
                photos.put(JSONObject().put("id", photo.id).put("path", "photos/${photo.id}.img")
                    .put("size", file.length()).put("sha256", sha256(file)))
            }
            records.put(JSONObject().put("date", record.entry.date)
                .put("weightHundredths", record.entry.weightHundredths ?: JSONObject.NULL)
                .put("note", record.entry.note).put("photos", photos))
        }
        val manifest = JSONObject().put("format", FORMAT).put("version", 1)
            .put("createdAt", Instant.now().toString()).put("records", records)
        val manifestBytes = manifest.toString().toByteArray(Charsets.UTF_8)
        require(manifestBytes.size <= MAX_MANIFEST_BYTES) { "记录数量超出备份支持范围" }
        var totalBytes = manifestBytes.size.toLong()
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifestBytes)
            zip.closeEntry()
            entries.forEach { record -> record.orderedPhotos.forEach { photo ->
                val file = repository.photoFile(photo)
                require(file.length() <= DiaryRepository.MAX_PHOTO_BYTES) { "单张照片过大" }
                totalBytes += file.length()
                require(totalBytes <= MAX_BACKUP_BYTES) { "备份大于 2 GB，请分阶段保管记录" }
                zip.putNextEntry(ZipEntry("photos/${photo.id}.img"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            } }
        }
    }

    fun prepare(uri: Uri): PendingRestore = repository.context.contentResolver.openInputStream(uri)?.use { prepareFrom(it) }
        ?: error("无法读取备份文件")

    fun prepareFrom(input: InputStream): PendingRestore {
        val directory = File(repository.context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val archive = File(directory, "backup.zip")
            archive.outputStream().use { copyLimited(input, it, MAX_BACKUP_BYTES) }
            val records = mutableListOf<RestoreRecord>()
            ZipFile(archive).use { zip ->
                val zipEntries = zip.entries().toList()
                require(zipEntries.size <= 100_001) { "备份文件数量超出支持范围" }
                val names = zipEntries.map { it.name }
                require(names.distinct().size == names.size) { "备份含重复文件" }
                require(names.all { it == "manifest.json" || PHOTO_PATH.matches(it) }) { "备份包含非法文件路径" }
                val manifestEntry = zip.getEntry("manifest.json") ?: error("未找到备份清单")
                val manifest = zip.getInputStream(manifestEntry).use { stream ->
                    val bytes = java.io.ByteArrayOutputStream()
                    copyLimited(stream, bytes, MAX_MANIFEST_BYTES.toLong())
                    JSONObject(bytes.toString("UTF-8"))
                }
                val version = manifest.get("version")
                require(manifest.getString("format") == FORMAT && version is Number && version.toString() == "1") { "不支持该备份格式或版本" }
                val array = manifest.getJSONArray("records")
                require(array.length() <= 100_000) { "记录数量超出支持范围" }
                val dates = mutableSetOf<String>()
                val photoIds = mutableSetOf<String>()
                val expectedNames = mutableSetOf("manifest.json")
                var totalBytes = 0L
                for (index in 0 until array.length()) {
                    val record = array.getJSONObject(index)
                    val date = record.getString("date")
                    require(LocalDate.parse(date).toString() == date && dates.add(date)) { "备份日期错误或重复" }
                    val weight = if (record.isNull("weightHundredths")) null else {
                        val raw = record.get("weightHundredths")
                        require(raw is Number && raw.toString().matches(Regex("[0-9]+"))) { "备份体重格式错误" }
                        raw.toString().toLong().also { require(it in 1..Int.MAX_VALUE.toLong()) { "备份体重超出范围" } }.toInt()
                    }
                    val noteValue = record.get("note")
                    require(noteValue is String) { "备份感想格式错误" }
                    val photosArray = record.getJSONArray("photos")
                    val photos = mutableListOf<RestorePhoto>()
                    for (photoIndex in 0 until photosArray.length()) {
                        val metadata = photosArray.getJSONObject(photoIndex)
                        val id = metadata.getString("id")
                        require(UUID.fromString(id).toString() == id && photoIds.add(id)) { "备份照片标识错误或重复" }
                        val path = metadata.getString("path")
                        require(path == "photos/$id.img" && expectedNames.add(path)) { "备份照片路径错误" }
                        val entry = zip.getEntry(path) ?: error("备份缺少照片：$date")
                        val file = File(directory, "$id.img")
                        val written = zip.getInputStream(entry).use { source -> file.outputStream().use { target ->
                            copyLimited(source, target, DiaryRepository.MAX_PHOTO_BYTES)
                        } }
                        totalBytes += written
                        require(totalBytes <= MAX_BACKUP_BYTES) { "备份解压后过大" }
                        require(written == metadata.getLong("size") && sha256(file) == metadata.getString("sha256")) { "备份照片校验失败：$date" }
                        requireImage(file)
                        photos += RestorePhoto(id, file, photoIndex)
                    }
                    require(weight != null || noteValue.isNotBlank() || photos.isNotEmpty()) { "备份包含空记录" }
                    records += RestoreRecord(DiaryEntry(date, weight, noteValue), photos)
                }
                require(expectedNames == names.toSet()) { "备份存在未登记的文件" }
            }
            return PendingRestore(directory, records)
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
    }

    suspend fun restore(pending: PendingRestore) = repository.mutex.withLock {
        val newFiles = mutableListOf<File>()
        try {
            // 使用新文件名预先准备全部照片；数据库失败回滚时原照片仍保持完好。
            val entities = pending.records.map { record ->
                record.entry to record.photos.map { photo ->
                    val target = File(repository.mediaDirectory, "${UUID.randomUUID()}.img")
                    newFiles += target
                    photo.file.copyTo(target)
                    DiaryPhoto(photo.id, record.entry.date, target.name, photo.position)
                }
            }
            repository.database.withTransaction {
                val dao = repository.database.diaryDao()
                dao.removeAllEntries()
                entities.forEach { (entry, photos) -> dao.upsertEntry(entry); dao.insertPhotos(photos) }
            }
        } catch (error: Exception) {
            newFiles.forEach { it.delete() }
            throw error
        }
        repository.removeUnreferencedMedia()
        pending.discard()
    }

    companion object {
        private const val FORMAT = "fitness-diary-backup"
        private const val MAX_MANIFEST_BYTES = 8 * 1024 * 1024
        private const val MAX_BACKUP_BYTES = 2L * 1024 * 1024 * 1024
        private val PHOTO_PATH = Regex("photos/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.img")
        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
