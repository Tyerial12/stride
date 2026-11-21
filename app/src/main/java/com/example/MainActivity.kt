package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firebase.SolstrideFirestoreRepository
import com.example.data.firebase.safeAuthStateFlow
import com.example.data.firebase.safeCurrentUser
import com.example.data.local.SolstrideLocalDatabase
import com.example.data.local.SolstridePreferences
import com.example.data.local.SolstrideSettings
import com.example.hardware.ConnectivityObserver
import com.example.hardware.HapticFeedbackManager
import com.example.hardware.StepTrackerManager
import com.example.ui.SolstrideAppShell
import com.example.ui.SolstrideViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.theme.SolstrideDisplayMode
import com.example.ui.theme.SolstrideTheme
import com.example.wear.GalaxyWatchBridge
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.hardware.StepBackgroundSyncScheduler.ensureScheduled(applicationContext)
        setContent {
            SolstrideRoot()
        }
    }
}

@Composable
private fun SolstrideRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember(context) { SolstridePreferences(context.applicationContext) }
    val settings by preferences.settingsFlow.collectAsStateWithLifecycle(initialValue = SolstrideSettings())
    val currentUser by safeAuthStateFlow(context).collectAsStateWithLifecycle(initialValue = safeCurrentUser(context))

    SolstrideTheme(displayMode = settings.displayMode) {
        val user = currentUser
        if (user == null) {
            AuthScreen(
                displayMode = settings.displayMode,
                onToggleDisplayMode = {
                    val next = if (settings.displayMode == SolstrideDisplayMode.SUNLIT_PARCHMENT) {
                        SolstrideDisplayMode.OBSIDIAN_OLED
                    } else {
                        SolstrideDisplayMode.SUNLIT_PARCHMENT
                    }
                    scope.launch { preferences.setDisplayMode(next) }
                },
                onAuthSuccess = {
                    // authStateFlow reacts immediately when FirebaseAuth updates currentUser
                }
            )
        } else {
            val uid = user.uid
            key(uid) {
                val viewModel: SolstrideViewModel = viewModel(
                    key = uid,
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY]) {
                                "APPLICATION_KEY missing from CreationExtras"
                            }
                            val databaseId = app.getString(R.string.firestore_database_id)
                            val firestore = FirebaseFirestore.getInstance(databaseId)
                            val repository = SolstrideFirestoreRepository(firestore)
                            val localDb = SolstrideLocalDatabase.getInstance(app)
                            val prefs = SolstridePreferences(app)
                            val haptics = HapticFeedbackManager(app)
                            val watchBridge = GalaxyWatchBridge(app)
                            val healthConnectReader = com.example.wear.HealthConnectStepReader(app, prefs)
                            val connectivity = ConnectivityObserver(app)
                            lateinit var vmRef: SolstrideViewModel
                            val stepTracker = StepTrackerManager(
                                context = app,
                                onBaselineUpdated = { baseline ->
                                    vmRef.onHardwareBaselineUpdated(baseline)
                                },
                                onWatchToggleBroadcast = { watchConnected, watchName ->
                                    vmRef.onWatchConnectionBroadcastReceived(watchConnected, watchName)
                                },
                                onStepDelta = { delta ->
                                    vmRef.onHardwareStepsReceived(delta)
                                }
                            )
                            vmRef = SolstrideViewModel(
                                currentUserId = uid,
                                initialDisplayName = user.displayName,
                                repository = repository,
                                offlineSyncDao = localDb.offlineSyncDao(),
                                preferences = prefs,
                                stepTracker = stepTracker,
                                haptics = haptics,
                                watchBridge = watchBridge,
                                healthConnectReader = healthConnectReader,
                                connectivityObserver = connectivity
                            )
                            vmRef
                        }
                    }
                )
                SolstrideAppShell(
                    currentUserId = uid,
                    viewModel = viewModel
                )
            }
        }
    }
}
