package cn.wangguanwen.fitnessdiary

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cn.wangguanwen.fitnessdiary.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var database: DiaryDatabase
    private lateinit var repository: DiaryRepository
    private lateinit var backups: BackupManager

    @Before fun setup() {
        val files = temporary.newFolder("files")
        val cache = temporary.newFolder("cache")
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getFilesDir() = files
            override fun getCacheDir() = cache
        }
        database = Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java).allowMainThreadQueries().build()
        repository = DiaryRepository(context, database)
        backups = BackupManager(repository)
    }

    @After fun teardown() { database.close() }

    @Test fun weightKeepsExactHundredthsAndRejectsInvalidInput() {
        assertEquals(6525, WeightFormat.parse("65.25"))
        assertEquals(6510, WeightFormat.parse("65.1"))
        assertEquals(6500, WeightFormat.parse("65"))
        assertEquals("65.00", WeightFormat.display(6500))
        assertEquals("−0.25", WeightFormat.change(-25))
        assertNull(WeightFormat.parse(" "))
        listOf("0", "0.00", "-65", "65.251", "NaN", "1e2", "99999999999999").forEach { input ->
            assertThrows(IllegalArgumentException::class.java) { WeightFormat.parse(input) }
        }
    }

    @Test fun chartUsesRealDatesAndDoesNotInventMissingWeights() {
        val records = listOf(
            EntryWithPhotos(DiaryEntry("2026-11-20", 6525, ""), emptyList()),
            EntryWithPhotos(DiaryEntry("2026-11-19", null, "只写感想"), emptyList()),
            EntryWithPhotos(DiaryEntry("2026-11-14", 6600, ""), emptyList()),
            EntryWithPhotos(DiaryEntry("2026-11-13", 6625, ""), emptyList()),
        )
        assertEquals(listOf("2026-11-14", "2026-11-20"), records.weightEntries(7, LocalDate.of(2026, 11, 20)).map { it.date })
        assertEquals(3, records.weightEntries(null, LocalDate.of(2026, 11, 20)).size)
        assertTrue(emptyList<EntryWithPhotos>().weightEntries(30, LocalDate.now()).isEmpty())
    }

    @Test fun sameDayUpdatesOneEntryAndAllowsTextOnly() = runBlocking {
        repository.save("2026-11-20", 6525, "第一次", emptyList())
        repository.save("2026-11-20", null, "只记录感想\n支持换行", emptyList())
        val records = database.diaryDao().allEntries()
        assertEquals(1, records.size)
        assertNull(records.single().entry.weightHundredths)
        assertEquals("只记录感想\n支持换行", records.single().entry.note)
    }

    @Test fun importedPhotoSurvivesSourceRemovalAndBackupRoundTrips() = runBlocking {
        val source = makePhoto()
        val photo = repository.importPhoto(Uri.fromFile(source))
        repository.save("2026-11-20", 6525, "今天练腿\n状态不错", listOf(photo))
        source.delete()
        val before = database.diaryDao().allEntries().single()
        val imageBytes = repository.photoFile(before.photos.single()).readBytes()
        assertTrue(imageBytes.isNotEmpty())
        val output = ByteArrayOutputStream()
        backups.exportTo(output)
        repository.save("2026-11-19", null, "稍后会被替换", emptyList())
        val pending = backups.prepareFrom(ByteArrayInputStream(output.toByteArray()))
        assertEquals(1, pending.records.size)
        assertEquals(1, pending.photoCount)
        backups.restore(pending)
        val after = database.diaryDao().allEntries().single()
        assertEquals(before.entry, after.entry)
        assertArrayEquals(imageBytes, repository.photoFile(after.photos.single()).readBytes())
    }

    @Test fun corruptPhotoAndIllegalPathAreRejectedWithoutChangingRecords() = runBlocking {
        val source = makePhoto()
        repository.save("2026-11-20", 6525, "保留我", listOf(repository.importPhoto(Uri.fromFile(source))))
        val output = ByteArrayOutputStream(); backups.exportTo(output)
        val original = output.toByteArray()
        val corrupt = rewriteZip(original) { name, bytes -> if (name.startsWith("photos/")) byteArrayOf(1, 2, 3) else bytes }
        assertThrows(Exception::class.java) { backups.prepareFrom(ByteArrayInputStream(corrupt)) }
        val illegal = ByteArrayOutputStream()
        ZipOutputStream(illegal).use { zip -> zip.putNextEntry(ZipEntry("../escape.img")); zip.write(byteArrayOf(1)); zip.closeEntry() }
        assertThrows(Exception::class.java) { backups.prepareFrom(ByteArrayInputStream(illegal.toByteArray())) }
        assertEquals("保留我", database.diaryDao().allEntries().single().entry.note)
        assertTrue(repository.photoFile(database.diaryDao().allEntries().single().photos.single()).exists())
    }

    @Test fun missingPhotoAndFutureBackupVersionAreRejected() = runBlocking {
        repository.save("2026-11-20", 6500, "", listOf(repository.importPhoto(Uri.fromFile(makePhoto()))))
        val output = ByteArrayOutputStream(); backups.exportTo(output)
        val missing = rewriteZip(output.toByteArray()) { name, bytes -> if (name.startsWith("photos/")) null else bytes }
        assertThrows(Exception::class.java) { backups.prepareFrom(ByteArrayInputStream(missing)) }
        listOf<Number>(99, 1.5).forEach { version ->
            val future = rewriteZip(output.toByteArray()) { name, bytes ->
                if (name == "manifest.json") JSONObject(String(bytes, Charsets.UTF_8)).put("version", version).toString().toByteArray() else bytes
            }
            assertThrows(Exception::class.java) { backups.prepareFrom(ByteArrayInputStream(future)) }
        }
        assertEquals(1, database.diaryDao().allEntries().size)
    }

    @Test fun failedRestoreKeepsPreviousDatabaseAndPhotos() = runBlocking {
        repository.save("2026-11-20", 6500, "原记录", listOf(repository.importPhoto(Uri.fromFile(makePhoto()))))
        val original = database.diaryDao().allEntries().single()
        val output = ByteArrayOutputStream(); backups.exportTo(output)
        val pending = backups.prepareFrom(ByteArrayInputStream(output.toByteArray()))
        pending.records.single().photos.single().file.delete()
        assertThrows(Exception::class.java) { runBlocking { backups.restore(pending) } }
        assertEquals(original.entry, database.diaryDao().allEntries().single().entry)
        assertTrue(repository.photoFile(original.photos.single()).exists())
    }

    @Test fun emptyBackupCanBeExportedAndValidated() = runBlocking {
        val output = ByteArrayOutputStream(); backups.exportTo(output)
        val pending = backups.prepareFrom(ByteArrayInputStream(output.toByteArray()))
        assertEquals(0, pending.records.size)
        assertEquals(0, pending.photoCount)
        backups.restore(pending)
        assertTrue(database.diaryDao().allEntries().isEmpty())
    }

    private fun makePhoto(): File {
        val target = temporary.newFile("source-${java.util.UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(8, 12, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return target
    }

    private fun rewriteZip(bytes: ByteArray, transform: (String, ByteArray) -> ByteArray?): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { target ->
            ZipInputStream(ByteArrayInputStream(bytes)).use { source ->
                while (true) {
                    val entry = source.nextEntry ?: break
                    val changed = transform(entry.name, source.readBytes())
                    if (changed != null) { target.putNextEntry(ZipEntry(entry.name)); target.write(changed); target.closeEntry() }
                    source.closeEntry()
                }
            }
        }
        return output.toByteArray()
    }
}
