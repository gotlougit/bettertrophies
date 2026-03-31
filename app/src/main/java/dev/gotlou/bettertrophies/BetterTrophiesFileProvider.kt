package dev.gotlou.bettertrophies

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File

class BetterTrophiesFileProvider : FileProvider(R.xml.file_paths) {
    companion object {
        fun uriFor(context: Context, file: File) = getUriForFile(context, authority(context), file)

        fun authority(context: Context): String = "${context.packageName}.fileprovider"
    }
}
