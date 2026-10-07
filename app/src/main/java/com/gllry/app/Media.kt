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

data class Photo(val id: Long, val album: String, val added: Long) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
}

data class Album(val key: String, val title: String, val photos: List<Photo>)

object Store {
    private fun p(c: Context) = c.getSharedPreferences("gllry", Context.MODE_PRIVATE)
    fun archived(c: Context): Set<String> = p(c).getStringSet("archived", emptySet()) ?: emptySet()
    fun saveArchived(c: Context, s: Set<String>) = p(c).edit().putStringSet("archived", HashSet(s)).apply()

    fun queryPhotos(c: Context): List<Photo> {
        val out = ArrayList<Photo>()
        val proj = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )
        runCatching {
            c.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cur ->
                while (cur.moveToNext()) {
                    out += Photo(cur.getLong(0), cur.getString(1) ?: "Other", cur.getLong(2))
                }
            }
        }
        return out
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
        archived = if (on) archived + p.id.toString() else archived - p.id.toString()
        Store.saveArchived(getApplication(), archived)
        DateWidget.updateAll(getApplication())
    }

    fun photosFor(key: String): List<Photo> = when (key) {
        ALL -> all.filter { it.id.toString() !in archived }
        ARCHIVE -> all.filter { it.id.toString() in archived }
        else -> all.filter { it.album == key && it.id.toString() !in archived }
    }

    fun albums(): List<Album> {
        val live = photosFor(ALL)
        val arch = photosFor(ARCHIVE)
        val buckets = live.groupBy { it.album }
            .map { Album(it.key, it.key, it.value) }
            .sortedByDescending { it.photos.size }
        return buildList {
            add(Album(ALL, "All", live))
            if (arch.isNotEmpty()) add(Album(ARCHIVE, "Archive", arch))
            addAll(buckets)
        }
    }
}
