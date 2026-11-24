package eu.kanade.tachiyomi.util

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.restore.kotatsu.KotatsuBackupConverter
import java.io.InputStream
import okio.buffer
import okio.gzip
import okio.source

object BackupUtil {
    private const val GZIP_MAGIC = 0x1f8b
    private const val ZIP_MAGIC = 0x504b

    /**
     * Decode a backup file, which can be a gzipped protobuf, a plain protobuf or a Kotatsu /
     * Kotatsu Redo zip of JSON sections.
     */
    fun decodeBackup(context: Context, uri: Uri): Backup {
        return context.contentResolver.openInputStream(uri)!!.use { decodeBackup(context, it) }
    }

    fun decodeBackup(context: Context, stream: InputStream): Backup {
        val source = stream.source().buffer()
        val peeked = source.peek()
        peeked.require(2)
        val magic = peeked.readShort().toInt() and 0xffff

        if (magic == ZIP_MAGIC) {
            return KotatsuBackupConverter.create().convert(source.inputStream())
        }

        val backupString = if (magic == GZIP_MAGIC) {
            source.gzip().buffer()
        } else {
            source
        }

        return backupString.use {
            BackupCreator(context).parser.decodeFromByteArray(Backup.serializer(), it.readByteArray())
        }
    }
}
