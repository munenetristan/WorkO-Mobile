// app/src/main/java/com/towmech/app/MainActivity.kt
package com.towmech.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.towmech.app.navigation.AppNavGraph
import com.towmech.app.notifications.FcmTokenSync
import com.towmech.app.notifications.NotificationChannels
import com.towmech.app.ui.theme.TowMechTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // ✅ Try sync again after permission response
            CoroutineScope(Dispatchers.IO).launch {
                FcmTokenSync.syncIfLoggedIn(this@MainActivity)
            }
        }

    private var notifOpen by mutableStateOf<String?>(null)
    private var notifJobId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationChannels.create(this)
        askNotificationPermission()

        readNotificationExtras(intent)

        CoroutineScope(Dispatchers.IO).launch {
            FcmTokenSync.syncIfLoggedIn(this@MainActivity)
        }

        setContent {
            TowMechTheme {
                AppNavGraph(
                    notificationOpen = notifOpen,
                    notificationJobId = notifJobId
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readNotificationExtras(intent)
    }

    private fun readNotificationExtras(intent: Intent?) {
        if (intent == null) return

        val open = intent.getStringExtra("open")
        val jobId = intent.getStringExtra("jobId")

        if (!open.isNullOrBlank()) notifOpen = open
        if (!jobId.isNullOrBlank()) notifJobId = jobId
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )

            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}