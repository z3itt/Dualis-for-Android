package com.z3itt.dualis.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.z3itt.dualis.MainActivity
import com.z3itt.dualis.R
import com.z3itt.dualis.audio.AudioDecoder
import com.z3itt.dualis.data.repo.LibraryRepository
import com.z3itt.dualis.domain.ingest.SpotifyQuery
import com.z3itt.dualis.domain.model.JobEvent
import com.z3itt.dualis.domain.model.Track
import com.z3itt.dualis.domain.model.TrackStatus
import com.z3itt.dualis.domain.queue.WorkItem
import com.z3itt.dualis.domain.queue.WorkQueue
import com.z3itt.dualis.download.DownloadException
import com.z3itt.dualis.ml.InferErrors
import com.z3itt.dualis.ml.LoadedModel
import com.z3itt.dualis.ml.ModelCatalog
import com.z3itt.dualis.ml.ModelDownloader
import com.z3itt.dualis.ml.StemSeparator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.io.File

class JobForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var worker: Job? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        worker = scope.launch { runLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground("Starting", 0)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val queue = (application as com.z3itt.dualis.DualisApplication).container.workQueue
        if (queue.isIdle()) {
            stopWhenIdle()
        }
    }

    override fun onDestroy() {
        worker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runLoop() {
        val app = application as com.z3itt.dualis.DualisApplication
        val queue = app.container.workQueue
        val repo = app.container.repo
        resumePending(repo, queue)
        while (scope.isActive) {
            val item = queue.pop()
            if (item == null) {
                if (queue.isIdle()) {
                    stopWhenIdle()
                    return
                }
                delay(250)
                continue
            }
            try {
                runJob(app, item)
            } catch (err: CancellationException) {
                throw err
            } catch (_: JobDroppedException) {
                File(repo.tracksDir(), item.trackId).deleteRecursively()
            } catch (err: OutOfMemoryError) {
                fail(
                    repo,
                    item.trackId,
                    "Not enough memory to separate this track. Try a shorter song or Kim Vocal 2.",
                )
            } catch (err: Exception) {
                fail(repo, item.trackId, err.message ?: err.toString())
            } finally {
                queue.finish(item.trackId)
            }
        }
    }

    private suspend fun resumePending(repo: LibraryRepository, queue: WorkQueue) {
        repo.pendingTracks().forEach { track ->
            val query = SpotifyQuery.resolveDownloadQuery(
                track.sourceKind,
                track.title,
                track.artist,
                track.sourceUrl,
                track.ytdlpQuery,
            )
            queue.enqueue(WorkItem(track.id, query), false)
        }
    }

    private suspend fun runJob(app: com.z3itt.dualis.DualisApplication, item: WorkItem) {
        val repo = app.container.repo
        val queue = app.container.workQueue
        abortIfDropped(queue, repo, item.trackId)
        var track = repo.getTrack(item.trackId) ?: return
        if (track.status == TrackStatus.READY && track.vocalsPath != null) return
        if (track.sourcePath.isNullOrBlank()) {
            val existing = File(repo.trackDir(track.id), "source.m4a")
            if (existing.isFile && existing.length() > 64L) {
                track = track.copy(sourcePath = existing.absolutePath, updatedAt = now())
                repo.upsert(track)
            } else {
                download(app, track, item.query)
                abortIfDropped(queue, repo, item.trackId)
                track = repo.getTrack(item.trackId) ?: return
            }
        }
        separate(app, track)
    }

    private suspend fun download(app: com.z3itt.dualis.DualisApplication, track: Track, rawQuery: String) {
        val repo = app.container.repo
        val queue = app.container.workQueue
        abortIfDropped(queue, repo, track.id)
        update(repo, track.copy(status = TrackStatus.DOWNLOADING, error = null, updatedAt = now()))
        emit(
            JobEvent(track.id, "download", 0.02f, "Starting download", TrackStatus.DOWNLOADING.raw(), null),
        )
        startInForeground("Downloading ${track.title}", 5)
        val query = rawQuery.ifBlank {
            SpotifyQuery.resolveDownloadQuery(
                track.sourceKind,
                track.title,
                track.artist,
                track.sourceUrl,
                track.ytdlpQuery,
            )
        }
        val dest = File(repo.trackDir(track.id), "source.m4a")
        try {
            app.container.linkBackend.download(query, dest) { progress, message ->
                abortIfCancelled(queue, track.id)
                emit(JobEvent(track.id, "download", progress, message, TrackStatus.DOWNLOADING.raw(), null))
                startInForeground(message, (progress * 100).toInt())
            }
        } catch (err: CancellationException) {
            throw err
        } catch (err: JobDroppedException) {
            throw err
        } catch (err: Exception) {
            throw DownloadException(err.message ?: "Download failed", err)
        }
        abortIfDropped(queue, repo, track.id)
        val cover = File(repo.trackDir(track.id), "cover.jpg")
        val decoder = AudioDecoder(this)
        if (!cover.isFile || cover.length() < 64L) {
            decoder.extractCover(dest.absolutePath, cover)
        }
        if (!cover.isFile || cover.length() < 64L) {
            app.container.linkBackend.saveCover(
                cover,
                app.container.linkBackend.coverUrls(track.sourceUrl, track.ytdlpQuery),
            )
        }
        update(
            repo,
            track.copy(
                sourcePath = dest.absolutePath,
                coverPath = cover.takeIf { it.isFile && it.length() > 64L }?.absolutePath ?: track.coverPath,
                status = TrackStatus.DOWNLOADED,
                updatedAt = now(),
            ),
        )
    }

    private suspend fun separate(app: com.z3itt.dualis.DualisApplication, start: Track) {
        val repo = app.container.repo
        val queue = app.container.workQueue
        abortIfDropped(queue, repo, start.id)
        var track = start.copy(status = TrackStatus.SEPARATING, error = null, updatedAt = now())
        update(repo, track)
        emit(JobEvent(track.id, "decode", 0.0f, "Preparing model", TrackStatus.SEPARATING.raw(), null))
        val spec = ModelCatalog.require(repo.selectedModelId())
        val downloader = ModelDownloader()
        val modelFile = withContext(Dispatchers.IO) {
            downloader.ensureModel(repo.modelsDir(), spec) { progress, message ->
                abortIfCancelled(queue, track.id)
                emit(JobEvent(track.id, "decode", progress, message, TrackStatus.SEPARATING.raw(), null))
                startInForeground(message, (progress * 100).toInt())
            }
        }
        val source = track.sourcePath ?: error("Track has no downloaded audio yet")
        val separator = app.container.separator
        val decoder = AudioDecoder(this)
        emit(JobEvent(track.id, "decode", 0.02f, "Decoding audio", TrackStatus.SEPARATING.raw(), null))
        startInForeground("Decoding ${track.title}", 10)
        System.gc()
        val decoded = withContext(Dispatchers.IO) { decoder.decodePath(source) }
        if (track.coverPath == null) {
            val cover = File(repo.trackDir(track.id), "cover.jpg")
            if (!cover.isFile || cover.length() < 64L) {
                decoder.extractCover(source, cover)
            }
            if (!cover.isFile || cover.length() < 64L) {
                app.container.linkBackend.saveCover(
                    cover,
                    app.container.linkBackend.coverUrls(track.sourceUrl, track.ytdlpQuery),
                )
            }
            if (cover.isFile && cover.length() > 64L) {
                track = track.copy(coverPath = cover.absolutePath)
            }
        }
        var preferAccel = !InferErrors.shouldFallbackToCpu(track.error.orEmpty())
        var model: LoadedModel? = null
        try {
            val result = try {
                model = withContext(Dispatchers.IO) { separator.loadModel(modelFile, spec, preferAccel) }
                abortIfDropped(queue, repo, track.id)
                runChunks(separator, model!!, decoded, repo, queue, track)
            } catch (err: Throwable) {
                if (err is CancellationException || err is JobDroppedException) throw err
                if (!preferAccel || !InferErrors.shouldFallbackToCpu(err)) throw err
                emit(
                    JobEvent(
                        track.id,
                        "infer",
                        0.1f,
                        InferErrors.cpuFallbackMessage(err),
                        TrackStatus.SEPARATING.raw(),
                        null,
                    ),
                )
                model?.let { separator.close(it) }
                model = null
                System.gc()
                preferAccel = false
                model = withContext(Dispatchers.IO) { separator.loadModel(modelFile, spec, preferAccel = false) }
                abortIfDropped(queue, repo, track.id)
                runChunks(separator, model!!, decoded, repo, queue, track)
            }
            abortIfDropped(queue, repo, track.id)
            update(
                repo,
                track.copy(
                    vocalsPath = result.vocalsPath,
                    instrumentalPath = result.instrumentalPath,
                    durationMs = result.durationMs,
                    sampleRate = result.sampleRate.toLong(),
                    status = TrackStatus.READY,
                    error = null,
                    updatedAt = now(),
                    peaks = File(result.peaksPath).takeIf { it.exists() }?.readText()
                        ?.removePrefix("[")?.removeSuffix("]")
                        ?.split(",")
                        ?.mapNotNull { it.trim().toFloatOrNull() },
                ),
            )
            emit(
                JobEvent(
                    track.id,
                    "export",
                    1f,
                    "Ready on ${result.executionProvider} · 32-bit float WAV",
                    TrackStatus.READY.raw(),
                    0f,
                ),
            )
            startInForeground("Ready: ${track.title}", 100)
        } finally {
            model?.let { separator.close(it) }
        }
    }

    private suspend fun runChunks(
        separator: StemSeparator,
        model: LoadedModel,
        decoded: com.z3itt.dualis.audio.DecodedAudio,
        repo: LibraryRepository,
        queue: WorkQueue,
        track: Track,
    ) = withContext(Dispatchers.Default) {
        separator.separateFile(model, decoded, repo.trackDir(track.id)) { progress, message, eta ->
            abortIfCancelled(queue, track.id)
            val stage = when {
                message.contains("decod", true) -> "decode"
                message.contains("reconstr", true) || message.contains("stems ready", true) -> "export"
                else -> "infer"
            }
            emit(JobEvent(track.id, stage, progress, message, TrackStatus.SEPARATING.raw(), eta))
            startInForeground(message, (progress * 100).toInt())
        }
    }

    private fun abortIfCancelled(queue: WorkQueue, trackId: String) {
        if (queue.isCancelled(trackId)) throw JobDroppedException()
    }

    private suspend fun abortIfDropped(queue: WorkQueue, repo: LibraryRepository, trackId: String) {
        abortIfCancelled(queue, trackId)
        if (repo.getTrack(trackId) == null) {
            queue.cancel(trackId)
            throw JobDroppedException()
        }
    }

    private suspend fun fail(repo: LibraryRepository, trackId: String, message: String) {
        val queue = (application as com.z3itt.dualis.DualisApplication).container.workQueue
        if (queue.isCancelled(trackId)) {
            runCatching { repo.deleteTrack(trackId) }
            return
        }
        val track = repo.getTrack(trackId)
        val failed = track?.copy(
            status = TrackStatus.ERROR,
            error = message,
            sourcePath = null,
            vocalsPath = null,
            instrumentalPath = null,
            coverPath = null,
            updatedAt = now(),
        )
        emit(
            JobEvent(
                trackId,
                "infer",
                0f,
                message,
                TrackStatus.ERROR.raw(),
                etaSeconds = null,
                failedTrack = failed,
            ),
        )
        startInForeground("Failed: ${track?.title ?: "track"}", 0)
        val deleted = runCatching { repo.deleteTrack(trackId) }.isSuccess
        if (!deleted && failed != null) {
            runCatching { repo.upsert(failed) }
        }
    }

    private suspend fun update(repo: LibraryRepository, track: Track) {
        val queue = (application as com.z3itt.dualis.DualisApplication).container.workQueue
        abortIfDropped(queue, repo, track.id)
        repo.upsert(track)
    }

    private fun emit(event: JobEvent) {
        events.tryEmit(event)
    }

    private fun now() = System.currentTimeMillis() / 1000

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_jobs), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun stopWhenIdle() {
        val stop = {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            stop()
        } else {
            main.post(stop)
        }
    }

    private fun startInForeground(text: String, progress: Int) {
        val notification = buildNotification(text, progress)
        val apply = {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            apply()
        } else {
            main.post(apply)
        }
    }

    private fun buildNotification(text: String, progress: Int): Notification {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.job_notification_title))
            .setContentText(text)
            .setContentIntent(launch)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .build()
    }

    companion object {
        const val CHANNEL = "dualis_jobs"
        const val NOTIF_ID = 42
        val events: MutableSharedFlow<JobEvent> = MutableSharedFlow(extraBufferCapacity = 64)
        fun bus(): SharedFlow<JobEvent> = events

        fun start(context: Context) {
            val intent = Intent(context, JobForegroundService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (_: IllegalStateException) {
                context.startService(intent)
            } catch (_: SecurityException) {
                context.startService(intent)
            }
        }
    }
}

private class JobDroppedException : Exception("Track deleted")
