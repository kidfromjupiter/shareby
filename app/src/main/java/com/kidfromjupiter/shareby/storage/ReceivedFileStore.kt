package com.kidfromjupiter.shareby.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream

class ReceivedFileStore(private val context: Context) {

    data class SavedFile(
        val name: String,
        val uri: Uri,
    )

    fun saveIncomingFile(sourceUri: Uri, preferredName: String, mimeTypeHint: String? = null): SavedFile? {
        val cleanBaseName = preferredName.ifBlank { "received_file" }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStore(sourceUri, cleanBaseName, mimeTypeHint)
        } else {
            saveToLegacyDownloads(sourceUri, cleanBaseName)
        }
    }

    private fun saveToMediaStore(sourceUri: Uri, preferredName: String, mimeTypeHint: String?): SavedFile? {
        val resolver = context.contentResolver
        val targetName = uniqueMediaStoreName(preferredName)
        val mimeType = mimeTypeHint ?: mimeFromName(targetName) ?: "application/octet-stream"
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, targetName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val targetUri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openInputStream(sourceUri).use { input ->
                resolver.openOutputStream(targetUri).use { output ->
                    if (input == null || output == null) {
                        resolver.delete(targetUri, null, null)
                        return null
                    }
                    input.copyTo(output)
                }
            }
            resolver.update(
                targetUri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null,
            )
            SavedFile(targetName, targetUri)
        } catch (_: Exception) {
            resolver.delete(targetUri, null, null)
            null
        }
    }

    private fun saveToLegacyDownloads(sourceUri: Uri, preferredName: String): SavedFile? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
            return null
        }

        val targetFile = uniqueLegacyFile(downloadsDir, preferredName)
        return try {
            context.contentResolver.openInputStream(sourceUri).use { input ->
                FileOutputStream(targetFile).use { output ->
                    if (input == null) {
                        return null
                    }
                    input.copyTo(output)
                }
            }
            SavedFile(targetFile.name, Uri.fromFile(targetFile))
        } catch (_: Exception) {
            null
        }
    }

    private fun uniqueMediaStoreName(preferredName: String): String {
        var candidate = preferredName
        var index = 1
        while (mediaStoreNameExists(candidate)) {
            candidate = withSuffix(preferredName, index)
            index += 1
        }
        return candidate
    }

    private fun mediaStoreNameExists(name: String): Boolean {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }

        resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(name),
            null,
        )?.use { cursor ->
            return cursor.moveToFirst()
        }
        return false
    }

    private fun uniqueLegacyFile(downloadsDir: File, preferredName: String): File {
        var candidate = File(downloadsDir, preferredName)
        var index = 1
        while (candidate.exists()) {
            candidate = File(downloadsDir, withSuffix(preferredName, index))
            index += 1
        }
        return candidate
    }

    private fun withSuffix(fileName: String, index: Int): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0 && dot < fileName.length - 1) {
            val stem = fileName.substring(0, dot)
            val ext = fileName.substring(dot)
            "$stem ($index)$ext"
        } else {
            "$fileName ($index)"
        }
    }

    private fun mimeFromName(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension.isBlank()) {
            return null
        }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }
}
