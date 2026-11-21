package com.derek.capflix

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.derek.capflix.data.Jellyfin
import com.derek.capflix.data.Session

class CapFlixApp : Application(), SingletonImageLoader.Factory {
    lateinit var session: Session
        private set
    lateinit var api: Jellyfin
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        session = Session(this)
        api = Jellyfin(session)
    }

    // Posters load through the same signed-in client, and are cached on disk
    // so rows scroll smoothly on the second visit.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { api.http })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images"))
                    .maxSizeBytes(400L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()

    companion object {
        lateinit var instance: CapFlixApp
            private set
        val api get() = instance.api
        val session get() = instance.session
    }
}
