package com.z3itt.dualis

import android.app.Application
import com.z3itt.dualis.audio.DualStemPlayer
import com.z3itt.dualis.data.repo.LibraryRepository
import com.z3itt.dualis.domain.queue.WorkQueue
import com.z3itt.dualis.download.LinkBackend
import com.z3itt.dualis.download.LocalFileBackend
import com.z3itt.dualis.ml.StemSeparator
import com.z3itt.dualis.service.JobForegroundService

class DualisApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        JobForegroundService.start(this)
    }
}

class AppContainer(app: DualisApplication) {
    val repo = LibraryRepository(app)
    val workQueue = WorkQueue()
    val player = DualStemPlayer(app)
    val separator = StemSeparator()
    val localFiles = LocalFileBackend(app)
    val linkBackend = LinkBackend()
}
