package dev.gotlou.bettertrophies

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.util.Locale

class CaptureMediaStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val contentResolver = context.contentResolver
    private val rootDirectory = File(context.filesDir, ROOT_DIRECTORY_NAME).apply { mkdirs() }

    fun persistThumbnail(
        accountKey: String,
        captureId: String,
        sourceUrl: String?,
        contentType: String?,
        bytes: ByteArray,
    ): String {
        return writeBytes(
            accountKey = accountKey,
            captureId = captureId,
            variant = "thumb",
            sourceName = sourceUrl,
            contentType = contentType,
            bytes = bytes,
        )
    }

    fun persistPrimaryAsset(
        accountKey: String,
        captureId: String,
        fileName: String?,
        contentType: String?,
        bytes: ByteArray,
    ): String {
        return writeBytes(
            accountKey = accountKey,
            captureId = captureId,
            variant = "full",
            sourceName = fileName,
            contentType = contentType,
            bytes = bytes,
        )
    }

    fun resolve(relativePath: String?): File? {
        if (relativePath.isNullOrBlank()) {
            return null
        }
        return File(relativePath)
    }

    fun clearAccount(accountKey: String) {
        resolveAccountDirectory(accountKey).deleteRecursively()
    }

    fun shareUriFor(capture: CaptureEntry): Uri? {
        capture.localPrimaryAssetGalleryUri
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
            ?.let { return it }

        val file = resolve(capture.localPrimaryAssetPath)
            ?.takeIf(File::exists)
            ?: return null
        return BetterTrophiesFileProvider.uriFor(context = appContext, file = file)
    }

    fun savePrimaryAssetToGallery(capture: CaptureEntry): String? {
        capture.localPrimaryAssetGalleryUri
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val file = resolve(capture.localPrimaryAssetPath)
            ?.takeIf(File::exists)
            ?: return null
        val uri = saveToGallery(
            captureId = capture.ugcId,
            sourceName = capture.localPrimaryAssetFileName ?: file.name,
            contentType = capture.localPrimaryAssetContentType,
            bytes = file.readBytes(),
        )
        return uri?.toString()
    }

    private fun writeBytes(
        accountKey: String,
        captureId: String,
        variant: String,
        sourceName: String?,
        contentType: String?,
        bytes: ByteArray,
    ): String {
        val accountDirectory = resolveAccountDirectory(accountKey).apply { mkdirs() }
        val extension = inferExtension(sourceName, contentType)
        val fileName = "$captureId-$variant.$extension"
        val file = File(accountDirectory, fileName)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private fun resolveAccountDirectory(accountKey: String): File {
        return File(rootDirectory, accountKey)
    }

    private fun saveToGallery(
        captureId: String,
        sourceName: String?,
        contentType: String?,
        bytes: ByteArray,
    ): Uri? {
        val extension = inferExtension(sourceName, contentType)
        val normalizedContentType = inferContentType(contentType, extension)
        val fileName = "$captureId.$extension"
        val contentUri = when {
            normalizedContentType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            normalizedContentType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Files.getContentUri("external")
        }
        val existingUri = findExistingUri(
            collectionUri = contentUri,
            fileName = fileName,
        )
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, normalizedContentType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val targetUri = existingUri?.also { contentResolver.update(it, values, null, null) }
            ?: contentResolver.insert(contentUri, values)
            ?: return null

        try {
            contentResolver.openOutputStream(targetUri, "w")?.use { output ->
                output.write(bytes)
            } ?: return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.update(
                    targetUri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            return targetUri
        } catch (error: Throwable) {
            if (existingUri == null) {
                contentResolver.delete(targetUri, null, null)
            }
            throw error
        }
    }

    private fun findExistingUri(
        collectionUri: Uri,
        fileName: String,
    ): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(fileName, RELATIVE_PATH)
        contentResolver.query(collectionUri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return null
            }
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
            return ContentUris.withAppendedId(collectionUri, id)
        }
        return null
    }

    private fun inferExtension(sourceName: String?, contentType: String?): String {
        val fromMime = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.US)
            ?.let { mimeType -> MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) }
            ?.ifBlank { null }
        if (fromMime != null) {
            return fromMime
        }

        val path = sourceName.orEmpty().substringBefore('?').substringBefore('#')
        val extension = path.substringAfterLast('.', "")
            .trim()
            .lowercase(Locale.US)
        return extension.ifBlank { "bin" }
    }

    private fun inferContentType(contentType: String?, extension: String): String {
        val normalizedContentType = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.US)
            ?.ifBlank { null }
        if (normalizedContentType != null) {
            return normalizedContentType
        }
        return MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension)
            ?.lowercase(Locale.US)
            ?: "application/octet-stream"
    }

    private companion object {
        const val ROOT_DIRECTORY_NAME = "capture-media-cache"
        val RELATIVE_PATH = "${Environment.DIRECTORY_PICTURES}/BetterTrophies"
    }
}
