package dev.gotlou.bettertrophies

import android.content.Context
import android.webkit.MimeTypeMap
import java.io.File
import java.util.Locale

class CaptureMediaStore(
    context: Context,
) {
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

    private companion object {
        const val ROOT_DIRECTORY_NAME = "capture-media-cache"
    }
}
