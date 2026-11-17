package com.lifttracker.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.lifttracker.app.ui.LiftTrackerRoot
import com.lifttracker.app.viewmodel.LiftTrackerViewModel
import com.lifttracker.app.viewmodel.MainTab
import com.lifttracker.app.viewmodel.SettingsSection

class MainActivity : ComponentActivity() {

    private val viewModel: LiftTrackerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.initializeWithContext(applicationContext)
        handleDeepLinkIntent(intent)

        setContent {
            LiftTrackerRoot(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    /** A routine JSON or workout CSV file opened with, or shared to, the app. */
    private fun handleImportIntent(intent: Intent?): Boolean {
        intent ?: return false
        val uri = when (intent.action) {
            Intent.ACTION_SEND -> @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            Intent.ACTION_VIEW -> intent.data?.takeIf { it.scheme == "content" || it.scheme == "file" }
            else -> null
        } ?: return false
        viewModel.importSharedFile(uri)
        // Do not import the same file again when the activity is recreated.
        intent.action = Intent.ACTION_MAIN
        intent.data = null
        intent.removeExtra(Intent.EXTRA_STREAM)
        return true
    }

    private fun openWorkoutDestination() {
        viewModel.selectedTab = MainTab.WORKOUT
        if (viewModel.isWorkoutActive) {
            viewModel.openActiveWorkoutLayer()
        }
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        if (handleImportIntent(intent)) return
        if (intent?.getBooleanExtra(EXTRA_OPEN_WORKOUT, false) == true) {
            intent.removeExtra(EXTRA_OPEN_WORKOUT)
            openWorkoutDestination()
            return
        }
        val data = intent?.data ?: return
        val path = data.path ?: return
        when {
            path.startsWith("/workout") -> openWorkoutDestination()
            path.startsWith("/routine") || path.startsWith("/folder") || path.startsWith("/program") -> {
                viewModel.selectedTab = MainTab.ROUTINES
            }
            path.startsWith("/user") -> viewModel.openSettingsLayer(SettingsSection.HEALTH_CONNECT)
            path.startsWith("/settings") -> viewModel.openSettingsLayer()
        }
    }

    companion object {
        const val EXTRA_OPEN_WORKOUT = "com.lifttracker.app.extra.OPEN_WORKOUT"
    }
}
