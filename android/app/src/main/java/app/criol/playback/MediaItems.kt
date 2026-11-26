package app.criol.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.criol.data.Library
import app.criol.data.Song
import java.io.File

/** Item completo, montado dentro do serviço (sem passar pelo Binder). */
fun Song.toPlayableMediaItem(library: Library): MediaItem {
    val art = library.artPath(albumId)?.let { Uri.fromFile(File(it)) }
    val fileUri = Uri.parse(uri)
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(fileUri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(fileUri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setAlbumArtist(albumArtist.ifBlank { null })
                .setArtworkUri(art)
                .setTrackNumber(track.takeIf { it > 0 })
                .setDiscNumber(disc.takeIf { it > 0 })
                .setReleaseYear(year.takeIf { it > 0 })
                .setGenre(genre.ifBlank { null })
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build(),
        )
        .build()
}
