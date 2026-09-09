package com.z3itt.dualis

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import com.z3itt.dualis.ui.DualisViewModel
import com.z3itt.dualis.ui.screens.DualisScreen
import com.z3itt.dualis.ui.theme.DualisTheme

class MainActivity : ComponentActivity() {
    private val vm: DualisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        handleIntent(intent)
        setContent {
            val state by vm.state.collectAsState()
            DualisTheme(darkTheme = state.themeDark) {
                DualisScreen(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
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
