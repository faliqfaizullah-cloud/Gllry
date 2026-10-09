package com.gllry.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

/** Teaches Coil to show a frame of each video as its thumbnail. */
class GllryApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).components { add(VideoFrameDecoder.Factory()) }.build()
}
