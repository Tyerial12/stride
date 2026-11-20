package com.example.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.SolstridePreferences
import com.example.data.local.SolstrideSettings
import com.example.ui.components.SolstrideCard
import com.example.ui.theme.SolstrideTheme

/**
 * Dedicated Health Connect Privacy Policy & Permissions Rationale Activity.
 * Launched by Android Health Connect when users inspect Solstride's data usage
 * via ACTION_SHOW_PERMISSIONS_RATIONALE (Android 13-) or VIEW_PERMISSION_USAGE (Android 14+).
 */
class HealthConnectRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val preferences = remember(context) { SolstridePreferences(context.applicationContext) }
            val settings by preferences.settingsFlow.collectAsStateWithLifecycle(initialValue = SolstrideSettings())

            SolstrideTheme(displayMode = settings.displayMode) {
                HealthConnectRationaleScreen(onClose = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthConnectRationaleScreen(onClose: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Solstride Health Connect Privacy",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SolstrideCard(strongBorder = true) {
                Text(
                    text = "How Solstride Uses Health Connect",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Solstride is a step-powered tactical RPG. Connecting Health Connect allows Solstride to import real-world walking steps from your paired smartwatch or fitness app (such as Samsung Health / Galaxy Watch, Pixel Watch / Fitbit, Garmin Connect, or Google Fit) directly into your game progression.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RationaleItemCard(
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                title = "Steps Read Access (READ_STEPS)",
                description = "Solstride reads only your step counts so walking charges Solar Stride Energy, builds Encounter Progress, advances Daily & Weekly Quests, and updates your Weekly Leaderboard steps."
            )

            RationaleItemCard(
                icon = Icons.Filled.Watch,
                title = "Background Step Sync (Optional)",
                description = "If granted, background step access lets Solstride keep your watch steps synced even while the app is closed so your hero never misses steps from a walk."
            )

            RationaleItemCard(
                icon = Icons.Filled.Lock,
                title = "Privacy & Control",
                description = "Solstride does not read heart rate, sleep, location, or medical records from Health Connect. Step totals are saved solely to your private authenticated Solstride RPG profile, never sold or shared for advertising, and you can revoke access anytime in Android Health Connect settings."
            )

            RationaleItemCard(
                icon = Icons.Filled.Shield,
                title = "Smart Deduplication",
                description = "When Solstride's built-in phone pedometer is active, phone-only records in Health Connect are filtered automatically to prevent double-counting."
            )

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("health_connect_rationale_close_button")
            ) {
                Text("Back to Health Connect")
            }
        }
    }
}

@Composable
private fun RationaleItemCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    SolstrideCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
