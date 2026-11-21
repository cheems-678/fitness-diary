package cn.wangguanwen.fitnessdiary

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import cn.wangguanwen.fitnessdiary.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorDraft(
    val date: String,
    val weight: String,
    val note: String,
    val photos: List<DraftPhoto>,
    val originalWeight: String = weight,
    val originalNote: String = note,
    val originalPhotoIds: List<String> = photos.map { it.id },
) {
    val dirty get() = weight != originalWeight || note != originalNote || photos.map { it.id } != originalPhotoIds
}

class DiaryViewModel(application: Application) : AndroidViewModel(application) {
    val repository = DiaryRepository(application, Room.databaseBuilder(application, DiaryDatabase::class.java, "fitness-diary.db").build())
    private val backups = BackupManager(repository)
    var records by mutableStateOf<List<EntryWithPhotos>>(emptyList()); private set
    var loaded by mutableStateOf(false); private set
    var loadError by mutableStateOf<String?>(null); private set
    private var readJob: Job? = null
    var busy by mutableStateOf(false); private set
    var busyLabel by mutableStateOf(""); private set
    var editor by mutableStateOf<EditorDraft?>(null); private set
    var editorError by mutableStateOf<String?>(null); private set
    var confirmClose by mutableStateOf(false); private set
    var pendingRestore by mutableStateOf<PendingRestore?>(null); private set
    private val draftPool = mutableListOf<DraftPhoto>()
    private val notifications = Channel<String>(Channel.BUFFERED)
    val messages = notifications.receiveAsFlow()

    init { retryLoading() }

    fun retryLoading() {
        readJob?.cancel()
        loadError = null
        readJob = viewModelScope.launch {
            try { repository.entries.collect { records = it; loaded = true } }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { loadError = "记录读取失败，请检查可用空间后重试。原数据不会自动清空。" }
        }
    }

    fun openEditor(date: String) {
        if (busy || !loaded) return
        val record = records.find { it.entry.date == date }
        editor = EditorDraft(date, record?.entry?.weightHundredths?.let(WeightFormat::display).orEmpty(),
            record?.entry?.note.orEmpty(), record?.orderedPhotos?.map { DraftPhoto(it.id, repository.photoFile(it)) }.orEmpty())
        editorError = null
    }

    fun updateWeight(value: String) { editor = editor?.copy(weight = value); editorError = null }
    fun updateNote(value: String) { editor = editor?.copy(note = value) }
    fun removePhoto(id: String) { editor = editor?.let { it.copy(photos = it.photos.filterNot { photo -> photo.id == id }) } }

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty() || editor == null) return
        operation("正在保存照片…") {
            val imported = mutableListOf<DraftPhoto>()
            try {
                withContext(Dispatchers.IO) { uris.forEach { imported += repository.importPhoto(it) } }
                draftPool += imported
                editor = editor?.let { it.copy(photos = it.photos + imported) }
            } catch (error: Exception) {
                withContext(Dispatchers.IO) { repository.discardDrafts(imported) }
                throw error
            }
        }
    }

    fun save() {
        val current = editor ?: return
        val weight = try { WeightFormat.parse(current.weight) }
        catch (error: IllegalArgumentException) { editorError = error.message; return }
        if (weight == null && current.note.isBlank() && current.photos.isEmpty()) {
            editorError = "请填写体重、感想或添加照片"; return
        }
        operation("正在保存记录…") {
            withContext(Dispatchers.IO) { repository.save(current.date, weight, current.note, current.photos); repository.discardDrafts(draftPool.toList()) }
            draftPool.clear()
            editor = null
            notifications.send("记录已保存")
        }
    }

    fun requestClose() { if (editor?.dirty == true) confirmClose = true else closeEditor() }
    fun keepEditing() { confirmClose = false }
    fun closeEditor() {
        val discarded = draftPool.toList()
        draftPool.clear()
        viewModelScope.launch(Dispatchers.IO) { repository.discardDrafts(discarded) }
        editor = null; confirmClose = false; editorError = null
    }

    fun delete(date: String) = operation("正在删除记录…") {
        withContext(Dispatchers.IO) { repository.delete(date) }
        notifications.send("记录已删除，相册原图不受影响")
    }

    fun export(uri: Uri) = operation("正在导出记录和照片…") {
        withContext(Dispatchers.IO) { backups.export(uri) }
        notifications.send("完整备份已导出")
    }

    fun prepareRestore(uri: Uri) = operation("正在校验备份和照片…") {
        val prepared = withContext(Dispatchers.IO) { backups.prepare(uri) }
        pendingRestore?.let { previous -> withContext(Dispatchers.IO) { previous.discard() } }
        pendingRestore = prepared
    }

    fun cancelRestore() {
        val previous = pendingRestore ?: return
        pendingRestore = null
        viewModelScope.launch(Dispatchers.IO) { previous.discard() }
    }

    fun restore() {
        val prepared = pendingRestore ?: return
        operation("正在恢复完整备份…") {
            withContext(Dispatchers.IO) { backups.restore(prepared) }
            pendingRestore = null
            notifications.send("记录和照片已恢复")
        }
    }

    private fun operation(label: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true; busyLabel = label
        viewModelScope.launch {
            try { action() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { notifications.send(friendlyError(error)) }
            finally { busy = false; busyLabel = "" }
        }
    }

    private fun friendlyError(error: Exception): String = when (error) {
        is java.io.IOException -> "文件读写失败，请检查可用空间、文件或选择的位置"
        is org.json.JSONException, is java.time.format.DateTimeParseException -> "备份内容格式不正确，现有记录未被替换"
        else -> error.message ?: "操作未完成，请重试"
    }
}
