package app.criol.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Entrega as capas em cache para a tela inicial. O widget manda só o endereço da imagem
 * (como o Booming Music faz) e o launcher carrega a capa em resolução cheia, sem passar
 * bitmaps grandes pelo sistema. Só leitura, e só arquivos da pasta `art/`.
 */
class WidgetArtProvider : ContentProvider() {
    override fun onCreate() = true

    override fun getType(uri: Uri) = "image/jpeg"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Somente leitura")
        val ctx = context ?: throw FileNotFoundException(uri.toString())
        val name = uri.lastPathSegment?.takeIf { NAME.matches(it) } ?: throw FileNotFoundException(uri.toString())
        val file = File(artDir(ctx), name)
        if (!file.isFile) throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private val NAME = Regex("[A-Za-z0-9_-]+\\.jpg")

        private fun artDir(context: Context) = File(context.filesDir, "art")

        /** Endereço da capa para o widget, ou null se o arquivo não for uma capa em cache. */
        fun uriFor(context: Context, path: String?): Uri? {
            val file = path?.let(::File) ?: return null
            if (!NAME.matches(file.name) || !file.isFile) return null
            if (file.parentFile?.canonicalPath != artDir(context).canonicalPath) return null
            return Uri.Builder()
                .scheme("content")
                .authority("${context.packageName}.widgetart")
                .appendPath(file.name)
                // Muda quando a capa é refeita, para o launcher não reaproveitar a antiga.
                .appendQueryParameter("v", file.lastModified().toString())
                .build()
        }
    }
}
