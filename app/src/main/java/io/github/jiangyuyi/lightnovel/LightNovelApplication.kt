package io.github.jiangyuyi.lightnovel

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import io.github.jiangyuyi.lightnovel.core.cache.CachedDataSource
import io.github.jiangyuyi.lightnovel.core.cache.SqliteCacheStore
import io.github.jiangyuyi.lightnovel.core.data.LightNovelRepository
import io.github.jiangyuyi.lightnovel.core.network.CronetImageFetcher
import io.github.jiangyuyi.lightnovel.core.network.LightNovelApi
import io.github.jiangyuyi.lightnovel.core.preferences.ReaderPreferencesStore
import io.github.jiangyuyi.lightnovel.core.session.SessionStore

class LightNovelApplication : Application(), ImageLoaderFactory {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(true)
        .components { add(CronetImageFetcher.Factory(container.api)) }
        .build()
}

class AppContainer(application: Application) {
    val sessionStore = SessionStore(application)
    val readerPreferences = ReaderPreferencesStore(application)
    val api = LightNovelApi(application)
    private val cacheStore = SqliteCacheStore(application)
    private val cachedDataSource = CachedDataSource(cacheStore)
    val repository = LightNovelRepository(api, sessionStore, cachedDataSource)
}
