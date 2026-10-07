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

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        if (it) { vm.refresh(); DateWidget.updateAll(this) }
    }
    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.refresh(); DateWidget.updateAll(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Full screen, edge to edge, bars hidden (swipe from edge to peek)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        granted = ContextCompat.checkSelfPermission(this, permName) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.refresh() else permLauncher.launch(permName)

        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                GllryApp(
                    vm = vm,
                    hasPermission = granted,
                    onRequest = { permLauncher.launch(permName) },
                    onDelete = { p ->
                        // Android asks the user to confirm; photo disappears once confirmed.
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
