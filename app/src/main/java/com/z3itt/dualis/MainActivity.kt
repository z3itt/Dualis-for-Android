package com.z3itt.dualis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.z3itt.dualis.service.JobForegroundService
import com.z3itt.dualis.ui.DualisViewModel
import com.z3itt.dualis.ui.screens.DualisScreen
import com.z3itt.dualis.ui.screens.DualisSplash
import com.z3itt.dualis.ui.theme.DualisTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: DualisViewModel by viewModels()

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        resumeJobsIfNeeded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestNotificationsThenResumeJobs()
        handleIntent(intent)
        setContent {
            val state by vm.state.collectAsState()
            var showSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(900)
                showSplash = false
            }
            DualisTheme(darkTheme = state.themeDark) {
                Crossfade(
                    targetState = showSplash,
                    animationSpec = tween(280),
                    label = "dualisSplash",
                ) { splash ->
                    if (splash) {
                        DualisSplash(dark = state.themeDark)
                    } else {
                        DualisScreen(vm)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun requestNotificationsThenResumeJobs() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            resumeJobsIfNeeded()
        }
    }

    private fun resumeJobsIfNeeded() {
        lifecycleScope.launch {
            val pending = (application as DualisApplication).container.repo.pendingTracks()
            if (pending.isNotEmpty()) {
                JobForegroundService.start(this@MainActivity)
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val stream = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                when {
                    stream != null -> vm.ingestLocalUris(listOf(stream))
                    !text.isNullOrBlank() -> vm.ingestSharedText(text)
                }
            }
            Intent.ACTION_VIEW -> {
                intent.dataString?.let { vm.ingestSharedText(it) }
            }
        }
    }
}
