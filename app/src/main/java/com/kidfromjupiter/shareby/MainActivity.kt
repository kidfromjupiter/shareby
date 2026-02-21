package com.kidfromjupiter.shareby

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import com.kidfromjupiter.shareby.model.OutgoingShareContent
import com.kidfromjupiter.shareby.nearby.NearbyShareManager
import com.kidfromjupiter.shareby.notifications.TransferNotificationManager
import com.kidfromjupiter.shareby.prefs.UserPrefs
import com.kidfromjupiter.shareby.storage.ReceivedFileStore
import com.kidfromjupiter.shareby.ui.SharebyApp
import com.kidfromjupiter.shareby.ui.theme.SharebyTheme

class MainActivity : ComponentActivity() {
    private lateinit var nearbyManager: NearbyShareManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        if (nearbyManager.state.value.pendingOutgoing != null) {
            nearbyManager.retryPendingDiscovery()
        } else {
            nearbyManager.startAdvertisingIfNeeded()
        }
    }

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val (name, size) = queryFileNameAndSize(uri)
        val mimeType = contentResolver.getType(uri)
        nearbyManager.beginShare(
            OutgoingShareContent.File(
                uri = uri,
                fileName = name ?: "shared_file",
                fileSize = size ?: 0L,
                mimeType = mimeType,
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = UserPrefs(this)
        val notificationManager = TransferNotificationManager(this)
        notificationManager.createChannel()

        nearbyManager = NearbyShareManager(
            context = this,
            userPrefs = prefs,
            notificationManager = notificationManager,
            receivedFileStore = ReceivedFileStore(this),
        )

        requestRuntimePermissions()
        handleShareIntent(intent)
        nearbyManager.startAdvertisingIfNeeded()

        setContent {
            val state by nearbyManager.state.collectAsState()
            SharebyTheme {
                SharebyApp(
                    state = state,
                    onConnect = { nearbyManager.connectToEndpoint(it) },
                    onRetryDiscovery = { nearbyManager.retryPendingDiscovery() },
                    onCancelShare = { nearbyManager.cancelShareFlow() },
                    onPickFile = { pickFileLauncher.launch("*/*") },
                    onSaveDisplayName = { nearbyManager.updateDisplayName(it) },
                    onStartReceiving = { nearbyManager.startAdvertisingIfNeeded() },
                    onStopAll = { nearbyManager.stopAll() },
                    onClearError = { nearbyManager.clearError() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        nearbyManager.shutdown()
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_ADVERTISE
            permissions += Manifest.permission.BLUETOOTH_CONNECT
            permissions += Manifest.permission.BLUETOOTH_SCAN
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES
            permissions += Manifest.permission.READ_MEDIA_IMAGES
            permissions += Manifest.permission.READ_MEDIA_VIDEO
            permissions += Manifest.permission.READ_MEDIA_AUDIO
            permissions += Manifest.permission.POST_NOTIFICATIONS
        } else {
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }

        val missing = permissions.distinct().filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            if (nearbyManager.state.value.pendingOutgoing != null) {
                nearbyManager.retryPendingDiscovery()
            } else {
                nearbyManager.startAdvertisingIfNeeded()
            }
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_SEND) {
            return
        }
        parseOutgoingShare(intent)?.let(nearbyManager::beginShare)
    }

    private fun parseOutgoingShare(intent: Intent): OutgoingShareContent? {
        val sharedUri = readStreamUri(intent)
        if (sharedUri != null) {
            val (name, size) = queryFileNameAndSize(sharedUri)
            return OutgoingShareContent.File(
                uri = sharedUri,
                fileName = name ?: "shared_file",
                fileSize = size ?: 0L,
                mimeType = intent.type,
            )
        }

        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        if (!text.isNullOrBlank()) {
            return OutgoingShareContent.Text(text)
        }

        return null
    }

    private fun readStreamUri(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun queryFileNameAndSize(uri: Uri): Pair<String?, Long?> {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return null to null
                }
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else null
                return name to size
            }
        return null to null
    }
}
