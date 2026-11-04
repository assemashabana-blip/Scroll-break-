package com.assem.scrollbreak

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Screen dump files in the app's private storage (shared out via FileProvider). */
object DumpStore {
    private val _files = MutableStateFlow<List<File>>(emptyList())
    val files: StateFlow<List<File>> = _files.asStateFlow()

    fun dir(context: Context): File =
        File(context.applicationContext.filesDir, "dumps").apply { mkdirs() }

    fun newFile(context: Context): File {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return File(dir(context), "dump-$ts.txt")
    }

    fun refresh(context: Context) {
        _files.value = dir(context).listFiles { f -> f.extension == "txt" }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
    }

    fun delete(context: Context, file: File) {
        file.delete()
        refresh(context)
    }

    fun deleteAll(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
        refresh(context)
    }
}
