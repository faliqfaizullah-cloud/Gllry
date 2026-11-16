package com.gllry.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache

class GllryApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        AppFonts.init(this)
    }

    /** Teaches Coil to show a frame of each video as its thumbnail. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(220)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.28).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(256L * 1024 * 1024).build() }
            .build()
}
