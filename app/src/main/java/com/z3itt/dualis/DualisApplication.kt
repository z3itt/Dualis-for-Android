package com.z3itt.dualis

import android.app.Application
import android.content.res.Configuration
import com.z3itt.dualis.audio.DualStemPlayer
import com.z3itt.dualis.audio.PlaybackHub
import com.z3itt.dualis.data.repo.LibraryRepository
import com.z3itt.dualis.domain.queue.WorkQueue
import com.z3itt.dualis.download.LinkBackend
import com.z3itt.dualis.download.LocalFileBackend
import com.z3itt.dualis.ml.StemSeparator
import java.io.File

class DualisApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        applyStoredNightMode()
        container = AppContainer(this)
    }

    fun isThemeDark(): Boolean =
        getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_THEME_DARK, false)

    fun persistThemeDark(dark: Boolean) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_THEME_DARK, dark).apply()
    }

    private fun applyStoredNightMode() {
        val newNight = if (isThemeDark()) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        val config = resources.configuration
        if (config.uiMode and Configuration.UI_MODE_NIGHT_MASK == newNight) return
        val next = Configuration(config)
        next.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or newNight
        @Suppress("DEPRECATION")
        resources.updateConfiguration(next, resources.displayMetrics)
    }

    companion object {
        private const val PREFS = "dualis"
        private const val PREF_THEME_DARK = "theme_dark"
    }
}

class AppContainer(app: DualisApplication) {
    val repo = LibraryRepository(app)
    val workQueue = WorkQueue()
    val player = DualStemPlayer(app)
    val playback = PlaybackHub()
    val separator = StemSeparator(app.applicationInfo.nativeLibraryDir, File(app.cacheDir, "qnn"))
    val localFiles = LocalFileBackend(app)
    val linkBackend = LinkBackend()
}
