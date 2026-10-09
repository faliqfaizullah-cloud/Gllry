package com.gllry.app

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private val vm: GalleryVM by viewModels()
    private var granted by mutableStateOf(false)

    private val permName
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private val allPerms: Array<String>
        get() = buildList {
            add(permName)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }.toTypedArray()

    private fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    private fun hasAll() = allPerms.all { has(it) }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = has(permName)
        if (granted) { vm.refresh(); DateWidget.updateAll(this) }
    }
    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.refresh(); DateWidget.updateAll(this)
    }

    // ---- import photos / videos from anywhere and SAVE a copy inside Gllry --------------------
    private val picker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isNotEmpty()) {
            Toast.makeText(this, "Saving ${uris.size} item(s) to Gllry…", Toast.LENGTH_SHORT).show()
            Thread {
                var n = 0
                uris.forEach { runCatching { importMedia(it, n++) } }
                runOnUiThread { vm.refresh(); DateWidget.updateAll(this) }
            }.start()
        }
    }

    private fun importMedia(src: Uri, n: Int) {
        val mime = contentResolver.getType(src) ?: "image/jpeg"
        val video = mime.startsWith("video/")
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: if (video) "mp4" else "jpg"
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Gllry_${System.currentTimeMillis()}_$n.$ext")
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (video) "Movies/Gllry" else "Pictures/Gllry")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val col = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val dst = contentResolver.insert(col, v) ?: return
        contentResolver.openInputStream(src)?.use { i -> contentResolver.openOutputStream(dst)?.use { o -> i.copyTo(o) } }
        v.clear(); v.put(MediaStore.MediaColumns.IS_PENDING, 0)
        contentResolver.update(dst, v, null, null)
    }

    // ---- record a video straight into Movies/Gllry -----------------------------------------------
    private var pendingRecord: Uri? = null
    private val recorder = registerForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val u = pendingRecord; pendingRecord = null
        if (u != null) {
            if (ok) { vm.refresh() } else runCatching { contentResolver.delete(u, null, null) }
        }
    }

    private fun record() {
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Gllry_${System.currentTimeMillis()}.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Gllry")
        }
        val u = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v) ?: return
        pendingRecord = u
        runCatching { recorder.launch(u) }.onFailure {
            pendingRecord = null
            runCatching { contentResolver.delete(u, null, null) }
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        granted = has(permName)
        if (granted) vm.refresh()
        if (!hasAll()) permLauncher.launch(allPerms)

        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                GllryApp(
                    vm = vm,
                    hasPermission = granted,
                    onRequest = { permLauncher.launch(allPerms) },
                    onDelete = { p ->
                        runCatching {
                            val pi = MediaStore.createDeleteRequest(contentResolver, listOf(p.uri))
                            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                        }
                    },
                    onImport = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    onRecord = { record() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (granted) vm.refresh()
    }
}
