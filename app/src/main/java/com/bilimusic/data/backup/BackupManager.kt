package com.bilimusic.data.backup

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.bilimusic.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(private val context: Context) {

    private val tag = "BackupManager"

    companion object {
        private const val DB_FILE = "bilimusic.db"
        private const val DS_FILE = "bilimusic_settings.preferences_pb"
        private const val MANIFEST = "manifest.json"
        private const val Z_DB = "database/$DB_FILE"
        private const val Z_DS = "datastore/$DS_FILE"
        private const val Z_DL = "downloads/"
    }

    suspend fun backupData(uri: Uri) = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "bilimusic_backup_tmp.zip")
        tmp.delete()

        try {
            AppDatabase.getInstance(context).query("PRAGMA wal_checkpoint(FULL)", null).use { }
        } catch (e: Exception) { Log.w(tag, "checkpoint: ${e.message}") }

        try {
            ZipOutputStream(FileOutputStream(tmp)).use { zos ->
                addFile(zos, context.getDatabasePath(DB_FILE), Z_DB)
                addFile(zos, File(context.filesDir, "datastore/$DS_FILE"), Z_DS)

                val dao = AppDatabase.getInstance(context).musicDao()
                val done = dao.getAllDownloadTasks().first()
                    .filter { t -> t.status.name == "COMPLETED" && !t.localFilePath.isNullOrBlank() }
                if (done.isNotEmpty()) {
                    val manifest = JSONObject().apply { done.forEach { put(it.id, it.localFilePath) } }
                    zos.putNextEntry(ZipEntry(MANIFEST))
                    zos.write(manifest.toString().toByteArray())
                    zos.closeEntry()
                    val used = mutableSetOf<String>()
                    for (t in done) {
                        val f = File(t.localFilePath)
                        if (!f.exists()) continue
                        var name = "${Z_DL}${f.name}"
                        var c = 1
                        while (!used.add(name)) { name = "${Z_DL}${f.nameWithoutExtension}_$c.${f.extension}"; c++ }
                        addFile(zos, f, name)
                    }
                }
            }
            context.contentResolver.openOutputStream(uri)?.use { out ->
                tmp.inputStream().use { ins -> ins.copyTo(out) }
            } ?: throw Exception("无法写入目标文件")
            Log.i(tag, "backup -> $uri (${tmp.length()} bytes)")
        } finally {
            tmp.delete()
        }
    }

    suspend fun restoreData(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(DB_FILE)
        val dsFile = File(context.filesDir, "datastore/$DS_FILE")
        val dbBak = File(context.cacheDir, "_db_bak")
        val dsBak = File(context.cacheDir, "_ds_bak")
        var dbOk = false
        var dsOk = false

        try {
            AppDatabase.getInstance(context).close()
            if (dbFile.exists()) dbFile.copyTo(dbBak, overwrite = true)
            if (dsFile.exists()) dsFile.copyTo(dsBak, overwrite = true)

            context.contentResolver.openInputStream(uri).use { ins ->
                ZipInputStream(ins!!).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            Z_DB -> {
                                FileOutputStream(dbFile).use { it.write(zis.readBytes()) }
                                dbOk = true
                            }
                            Z_DS -> {
                                dsFile.parentFile?.mkdirs()
                                FileOutputStream(dsFile).use { it.write(zis.readBytes()) }
                                dsOk = true
                            }
                        }
                        if (entry.name.startsWith(Z_DL)) {
                            val fname = entry.name.substringAfterLast('/')
                            val dest = File("${Environment.DIRECTORY_MUSIC}/BiliMusic").apply { mkdirs() }
                            FileOutputStream(File(dest, fname)).use { it.write(zis.readBytes()) }
                        }
                        entry = zis.nextEntry
                    }
                }
            }

            if (dbOk && dsOk) {
                dbBak.delete()
                dsBak.delete()
                Log.i(tag, "restore OK")
                RestoreResult.Ok
            } else {
                if (!dbOk && dbBak.exists()) { dbBak.copyTo(dbFile, overwrite = true); dbBak.delete() }
                if (!dsOk && dsBak.exists()) { dsBak.copyTo(dsFile, overwrite = true); dsBak.delete() }
                RestoreResult.Error("备份文件不完整，已回滚")
            }
        } catch (ex: Exception) {
            Log.e(tag, "restore failed", ex)
            if (!dbOk && dbBak.exists()) { dbBak.copyTo(dbFile, overwrite = true); dbBak.delete() }
            if (!dsOk && dsBak.exists()) { dsBak.copyTo(dsFile, overwrite = true); dsBak.delete() }
            RestoreResult.Error("恢复失败: ${ex.localizedMessage ?: "未知错误"}")
        }
    }

    private fun addFile(zos: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists()) return
        zos.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyTo(zos) }
        zos.closeEntry()
    }

    sealed class RestoreResult {
        object Ok : RestoreResult()
        data class Error(val message: String) : RestoreResult()
    }
}
