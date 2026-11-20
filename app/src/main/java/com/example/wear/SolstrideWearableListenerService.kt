package com.example.wear

import android.util.Log
import com.example.data.local.PendingStepBatchEntity
import com.example.data.local.SolstrideLocalDatabase
import com.example.data.model.StepSource
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

class SolstrideWearableListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val dao = SolstrideLocalDatabase.getInstance(applicationContext).offlineSyncDao()
        val activeUid = Firebase.auth.currentUser?.uid ?: "__WATCH_UNBOUND__"

        val pendingBatches = mutableListOf<PendingStepBatchEntity>()
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val path = event.dataItem.uri.path ?: continue
            if (path == WearDataPaths.STEPS_BATCH) {
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                val steps = map.getLong(WearDataPaths.KEY_STEPS_DELTA, 0L)
                if (steps > 0L) {
                    val batchId = map.getString(WearDataPaths.KEY_BATCH_ID)
                        ?.takeIf { it.isNotBlank() }
                        ?: "watch_${UUID.randomUUID()}"
                    val recordedAt = map.getLong(WearDataPaths.KEY_TIMESTAMP, System.currentTimeMillis())
                    pendingBatches.add(
                        PendingStepBatchEntity(
                            batchId = batchId,
                            userId = activeUid,
                            source = StepSource.GALAXY_WATCH.name,
                            stepDelta = steps,
                            recordedAtMs = recordedAt,
                            synced = false
                        )
                    )
                }
            }
        }

        if (pendingBatches.isNotEmpty()) {
            serviceScope.launch {
                pendingBatches.forEach { dao.insertBatch(it) }
                Log.d("SolstrideWearService", "Queued ${pendingBatches.size} step batch(es) from Galaxy Watch")
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == WearDataPaths.STEPS_BATCH) {
            val rawSteps = String(messageEvent.data, Charsets.UTF_8).trim().toLongOrNull() ?: return
            if (rawSteps <= 0L) return
            val dao = SolstrideLocalDatabase.getInstance(applicationContext).offlineSyncDao()
            val activeUid = Firebase.auth.currentUser?.uid ?: "__WATCH_UNBOUND__"
            serviceScope.launch {
                dao.insertBatch(
                    PendingStepBatchEntity(
                        batchId = "watch_msg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
                        userId = activeUid,
                        source = StepSource.GALAXY_WATCH.name,
                        stepDelta = rawSteps,
                        recordedAtMs = System.currentTimeMillis(),
                        synced = false
                    )
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
