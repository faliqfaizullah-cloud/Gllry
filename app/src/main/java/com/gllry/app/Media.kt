package com.gllry.app

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

const val ALL = "__all"
const val ARCHIVE = "__archive"
const val VIDEOS = "__videos"

data class Photo(
    val id: Long, val album: String, val added: Long,
    val isVideo: Boolean = false, val duration: Long = 0L
) {
    /** unique across photos AND videos (their ids live in separate tables) */
    val key: String get() = if (isVideo) "v$id" else "i$id"
    /** photos keep the plain id so older archived photos stay archived */
    val archiveKey: String get() = if (isVideo) "v$id" else id.toString()
    val uri: Uri
        get() = ContentUris.withAppendedId(
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
        )
}

data class Album(val key: String, val title: String, val photos: List<Photo>)

object Store {
    private fun p(c: Context) = c.getSharedPreferences("gllry", Context.MODE_PRIVATE)
    fun archived(c: Context): Set<String> = p(c).getStringSet("archived", emptySet()) ?: emptySet()
    fun saveArchived(c: Context, s: Set<String>) = p(c).edit().putStringSet("archived", HashSet(s)).apply()

    fun queryPhotos(c: Context): List<Photo> {
        val out = ArrayList<Photo>()
        runCatching {
            c.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED),
                null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cur ->
                while (cur.moveToNext()) out += Photo(cur.getLong(0), cur.getString(1) ?: "Other", cur.getLong(2))
            }
        }
        runCatching {
            c.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Video.Media._ID, MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.DURATION
                ),
                null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cur ->
                while (cur.moveToNext()) out += Photo(cur.getLong(0), cur.getString(1) ?: "Videos", cur.getLong(2), true, cur.getLong(3))
            }
        }
        return out.sortedByDescending { it.added }
    }
}

class GalleryVM(app: Application) : AndroidViewModel(app) {
    var all by mutableStateOf(emptyList<Photo>()); private set
    var archived by mutableStateOf(Store.archived(app)); private set

    fun refresh() {
        all = Store.queryPhotos(getApplication())
        archived = Store.archived(getApplication())
    }

    fun setArchived(p: Photo, on: Boolean) {
        archived = if (on) archived + p.archiveKey else archived - p.archiveKey
        Store.saveArchived(getApplication(), archived)
        DateWidget.updateAll(getApplication())
    }

    fun photosFor(key: String): List<Photo> = when (key) {
        ALL -> all.filter { it.archiveKey !in archived }
        ARCHIVE -> all.filter { it.archiveKey in archived }
        VIDEOS -> all.filter { it.isVideo && it.archiveKey !in archived }
        else -> all.filter { it.album == key && it.archiveKey !in archived }
    }

    fun albums(): List<Album> {
        val live = photosFor(ALL)
        val vids = photosFor(VIDEOS)
        val arch = photosFor(ARCHIVE)
        val buckets = live.groupBy { it.album }
            .map { Album(it.key, it.key, it.value) }
            .sortedByDescending { it.photos.size }
        return buildList {
            add(Album(ALL, "All", live))
            if (vids.isNotEmpty()) add(Album(VIDEOS, "Videos", vids))
            if (arch.isNotEmpty()) add(Album(ARCHIVE, "Archive", arch))
            addAll(buckets)
        }
    }
}
