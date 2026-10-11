package com.example.mediasearch.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Where downloaded files go: Download/Media Search/<movie folder>/ in shared storage, so any video
 * player or file manager can see them. Files are hidden ("pending") until fully written.
 */
class DownloadStore(private val context: Context) {

    class Target(val stream: OutputStream, internal val uri: Uri?, internal val file: File?)

    fun create(folder: String, name: String): Target {
        val safeFolder = folder.replace('/', '_')
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(name))
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$LOCATION_NAME/$safeFolder")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Couldn't create $name in Downloads.")
            val stream = resolver.openOutputStream(uri, "w")
                ?: throw IOException("Couldn't open $name for writing.")
            Target(stream.buffered(1 shl 20), uri, null)
        } else {
            // Android 8–9: app-specific Downloads folder (no storage permission needed).
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), safeFolder)
            dir.mkdirs()
            val file = File(dir, name)
            Target(FileOutputStream(file).buffered(1 shl 20), null, file)
        }
    }

    /** Makes a finished file visible to other apps. */
    fun complete(t: Target) {
        if (t.uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            context.contentResolver.update(t.uri, values, null, null)
        }
    }

    /** Removes a partial file. */
    fun discard(t: Target) {
        runCatching { t.stream.close() }
        if (t.uri != null) runCatching { context.contentResolver.delete(t.uri, null, null) }
        t.file?.delete()
    }

    companion object {
        const val LOCATION_NAME = "Media Search"

        private val videoExtensions = setOf("mkv", "mp4", "m4v", "avi", "mov", "wmv", "webm", "ts", "m2ts", "mpg", "mpeg", "flv")
        private val subtitleExtensions = setOf("srt", "ass", "ssa", "sub", "idx", "vtt")

        fun extension(name: String) = name.substringAfterLast('.', "").lowercase()
        fun isVideo(name: String) = extension(name) in videoExtensions
        fun isSubtitle(name: String) = extension(name) in subtitleExtensions

        fun mimeFor(name: String): String {
            val ext = extension(name)
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: when {
                    ext == "mkv" -> "video/x-matroska"
                    ext in videoExtensions -> "video/*"
                    else -> "application/octet-stream"
                }
        }
    }
}
