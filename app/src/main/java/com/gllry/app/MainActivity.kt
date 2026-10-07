package com.gllry.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
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

    private fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = has(permName)
        if (granted) { vm.refresh(); DateWidget.updateAll(this) }
    }
    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.refresh(); DateWidget.updateAll(this)
    }

    private fun askPermissions() =
        permLauncher.launch(arrayOf(permName, Manifest.permission.ACCESS_MEDIA_LOCATION))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        granted = has(permName)
        if (granted) vm.refresh()
        // also asks for photo-location access (shown in Details) if it isn't granted yet
        if (!granted || !has(Manifest.permission.ACCESS_MEDIA_LOCATION)) askPermissions()

        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                GllryApp(
                    vm = vm,
                    hasPermission = granted,
                    onRequest = { askPermissions() },
                    onDelete = { p ->
                        runCatching {
                            val pi = MediaStore.createDeleteRequest(contentResolver, listOf(p.uri))
                            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (granted) vm.refresh()
    }
}
