package com.kidfromjupiter.shareby.nearby

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionType
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.kidfromjupiter.shareby.model.Endpoint
import com.kidfromjupiter.shareby.model.NearbyShareState
import com.kidfromjupiter.shareby.model.OutgoingShareContent
import com.kidfromjupiter.shareby.model.TransferDirection
import com.kidfromjupiter.shareby.model.TransferItem
import com.kidfromjupiter.shareby.model.TransferStatus
import com.kidfromjupiter.shareby.notifications.TransferNotificationManager
import com.kidfromjupiter.shareby.prefs.UserPrefs
import com.kidfromjupiter.shareby.storage.ReceivedFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.ArrayDeque

class NearbyShareManager(
    context: Context,
    private val userPrefs: UserPrefs,
    private val notificationManager: TransferNotificationManager,
    private val receivedFileStore: ReceivedFileStore,
) {
    private val appContext = context.applicationContext
    private val connectionsClient = Nearby.getConnectionsClient(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(NearbyShareState(displayName = userPrefs.getDisplayName()))
    val state: StateFlow<NearbyShareState> = _state.asStateFlow()

    private val pendingIncomingNames = ArrayDeque<String>()
    private val incomingFilePayloads = mutableMapOf<Long, Payload>()

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { setError("Failed to auto-accept connection: ${it.message}") }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (!result.status.isSuccess) {
                setError("Connection failed: ${result.status.statusMessage}")
                return
            }

            val endpoint = _state.value.discoveredEndpoints.find { it.id == endpointId }
                ?: Endpoint(endpointId, "Nearby Device")

            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()

            _state.update {
                it.copy(
                    connectedEndpoint = endpoint,
                    isAdvertising = false,
                    isDiscovering = false,
                )
            }

            sendPendingOutgoingIfPresent()
        }

        override fun onDisconnected(endpointId: String) {
            _state.update { it.copy(connectedEndpoint = null) }
            if (_state.value.pendingOutgoing == null) {
                startAdvertisingIfNeeded()
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val endpoint = Endpoint(endpointId, info.endpointName)
            _state.update { current ->
                if (current.discoveredEndpoints.any { it.id == endpointId }) {
                    current
                } else {
                    current.copy(discoveredEndpoints = current.discoveredEndpoints + endpoint)
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _state.update { current ->
                current.copy(discoveredEndpoints = current.discoveredEndpoints.filterNot { it.id == endpointId })
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> handleIncomingBytes(payload)
                Payload.Type.FILE -> handleIncomingFile(payload)
                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val transfer = _state.value.transfers[update.payloadId] ?: return

            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    val updated = transfer.copy(transferredBytes = update.bytesTransferred)
                    _state.update { it.copy(transfers = it.transfers + (update.payloadId to updated)) }
                    notificationManager.showProgress(
                        payloadId = update.payloadId,
                        fileName = updated.fileName,
                        direction = updated.direction,
                        transferredBytes = update.bytesTransferred,
                        totalBytes = update.totalBytes,
                    )
                }

                PayloadTransferUpdate.Status.SUCCESS -> {
                    if (transfer.direction == TransferDirection.OUTGOING) {
                        val completed = transfer.copy(
                            status = TransferStatus.SUCCESS,
                            transferredBytes = if (update.totalBytes > 0) update.totalBytes else update.bytesTransferred,
                        )
                        _state.update { it.copy(transfers = it.transfers + (update.payloadId to completed)) }
                        notificationManager.showCompleted(update.payloadId, completed.fileName, completed.direction)
                        scheduleTransferRemoval(update.payloadId, completed.direction)
                        clearPendingOutgoing()
                        disconnectAfterSuccessfulTransfer()
                    } else {
                        finalizeIncomingFile(update.payloadId, transfer)
                    }
                }

                PayloadTransferUpdate.Status.FAILURE -> {
                    val failed = transfer.copy(status = TransferStatus.FAILURE)
                    _state.update { it.copy(transfers = it.transfers + (update.payloadId to failed)) }
                    notificationManager.showFailed(update.payloadId, failed.fileName, failed.direction)
                    scheduleTransferRemoval(update.payloadId, failed.direction)
                }

                PayloadTransferUpdate.Status.CANCELED -> {
                    val canceled = transfer.copy(status = TransferStatus.CANCELED)
                    _state.update { it.copy(transfers = it.transfers + (update.payloadId to canceled)) }
                    notificationManager.showFailed(update.payloadId, canceled.fileName, canceled.direction)
                    scheduleTransferRemoval(update.payloadId, canceled.direction)
                }
            }
        }
    }

    fun updateDisplayName(displayName: String) {
        val trimmed = displayName.trim()
        if (trimmed.isBlank()) {
            return
        }
        userPrefs.setDisplayName(trimmed)
        _state.update { it.copy(displayName = trimmed) }

        if (_state.value.isAdvertising) {
            connectionsClient.stopAdvertising()
            _state.update { it.copy(isAdvertising = false) }
            startAdvertisingIfNeeded()
        }
    }

    fun clearError() {
        _state.update { it.copy(lastError = null) }
    }

    fun beginShare(content: OutgoingShareContent) {
        _state.update {
            it.copy(
                pendingOutgoing = content,
                discoveredEndpoints = emptyList(),
                connectedEndpoint = null,
                lastError = null,
            )
        }
        connectionsClient.stopAdvertising()
        _state.update { it.copy(isAdvertising = false) }
        startDiscovery()
    }

    fun retryPendingDiscovery() {
        if (_state.value.pendingOutgoing == null) {
            return
        }
        connectionsClient.stopDiscovery()
        _state.update { it.copy(isDiscovering = false, discoveredEndpoints = emptyList()) }
        startDiscovery()
    }

    fun cancelShareFlow() {
        clearPendingOutgoing()
        connectionsClient.stopDiscovery()
        _state.update { it.copy(isDiscovering = false, discoveredEndpoints = emptyList()) }
        startAdvertisingIfNeeded()
    }

    fun connectToEndpoint(endpoint: Endpoint) {
        val displayName = _state.value.displayName
        connectionsClient.requestConnection(displayName, endpoint.id, connectionLifecycleCallback)
            .addOnFailureListener { setError("Failed to request connection: ${it.message}") }
    }

    fun startAdvertisingIfNeeded() {
        if (_state.value.pendingOutgoing != null || _state.value.connectedEndpoint != null || _state.value.isAdvertising) {
            return
        }

        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .setConnectionType(ConnectionType.DISRUPTIVE)
            .setDisruptiveUpgrade(true)
            .build()

        connectionsClient.startAdvertising(
            _state.value.displayName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options,
        )
            .addOnSuccessListener { _state.update { it.copy(isAdvertising = true) } }
            .addOnFailureListener { setError("Failed to start advertising: ${it.message}") }
    }

    fun stopAll() {
        connectionsClient.stopAllEndpoints()
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        _state.update {
            it.copy(
                isAdvertising = false,
                isDiscovering = false,
                discoveredEndpoints = emptyList(),
                connectedEndpoint = null,
                pendingOutgoing = null,
            )
        }
    }

    fun shutdown() {
        stopAll()
        scope.cancel()
    }

    private fun startDiscovery() {
        if (_state.value.isDiscovering) return

        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()

        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener {
                _state.update { it.copy(isDiscovering = true, discoveredEndpoints = emptyList()) }
            }
            .addOnFailureListener { setError("Failed to start discovery: ${it.message}") }
    }

    private fun sendPendingOutgoingIfPresent() {
        val endpointId = _state.value.connectedEndpoint?.id ?: return
        val outgoing = _state.value.pendingOutgoing ?: return

        when (outgoing) {
            is OutgoingShareContent.Text -> sendOutgoingText(endpointId, outgoing)
            is OutgoingShareContent.File -> sendOutgoingFile(endpointId, outgoing)
        }
    }

    private fun sendOutgoingText(endpointId: String, content: OutgoingShareContent.Text) {
        val payload = Payload.fromBytes("$TEXT_PREFIX${content.value}".toByteArray())
        connectionsClient.sendPayload(endpointId, payload)
            .addOnSuccessListener {
                clearPendingOutgoing()
                disconnectAfterSuccessfulTransfer()
            }
            .addOnFailureListener { setError("Failed to send text: ${it.message}") }
    }

    private fun sendOutgoingFile(endpointId: String, content: OutgoingShareContent.File) {
        val parcelFileDescriptor = appContext.contentResolver.openFileDescriptor(content.uri, "r")
        if (parcelFileDescriptor == null) {
            setError("Unable to open shared file")
            return
        }

        val filePayload = Payload.fromFile(parcelFileDescriptor)
        val metadataPayload = Payload.fromBytes("$FILE_PREFIX${content.fileName}".toByteArray())

        val transfer = TransferItem(
            payloadId = filePayload.id,
            endpointName = _state.value.connectedEndpoint?.name ?: "Nearby Device",
            fileName = content.fileName,
            totalBytes = content.fileSize,
            direction = TransferDirection.OUTGOING,
        )

        _state.update { it.copy(transfers = it.transfers + (filePayload.id to transfer)) }
        notificationManager.showProgress(filePayload.id, transfer.fileName, transfer.direction, 0, transfer.totalBytes)

        connectionsClient.sendPayload(endpointId, metadataPayload)
            .addOnSuccessListener {
                connectionsClient.sendPayload(endpointId, filePayload)
                    .addOnFailureListener {
                        val failed = transfer.copy(status = TransferStatus.FAILURE)
                        _state.update { it.copy(transfers = it.transfers + (filePayload.id to failed)) }
                        notificationManager.showFailed(filePayload.id, transfer.fileName, transfer.direction)
                        setError("Failed to send file payload: ${it.message}")
                    }
            }
            .addOnFailureListener {
                val failed = transfer.copy(status = TransferStatus.FAILURE)
                _state.update { it.copy(transfers = it.transfers + (filePayload.id to failed)) }
                notificationManager.showFailed(filePayload.id, transfer.fileName, transfer.direction)
                setError("Failed to send file metadata: ${it.message}")
            }
    }

    private fun handleIncomingBytes(payload: Payload) {
        val text = payload.asBytes()?.toString(Charsets.UTF_8) ?: return

        when {
            text.startsWith(FILE_PREFIX) -> {
                val fileName = text.removePrefix(FILE_PREFIX).ifBlank { "received_file" }
                pendingIncomingNames.addLast(fileName)
            }

            text.startsWith(TEXT_PREFIX) -> {
                val message = text.removePrefix(TEXT_PREFIX)
                _state.update { it.copy(receivedTexts = it.receivedTexts + message) }
                disconnectAfterSuccessfulTransfer()
            }

            else -> {
                _state.update { it.copy(receivedTexts = it.receivedTexts + text) }
                disconnectAfterSuccessfulTransfer()
            }
        }
    }

    private fun handleIncomingFile(payload: Payload) {
        incomingFilePayloads[payload.id] = payload

        val fileName = if (pendingIncomingNames.isNotEmpty()) {
            pendingIncomingNames.removeFirst()
        } else {
            "received_${payload.id}"
        }

        val transfer = TransferItem(
            payloadId = payload.id,
            endpointName = _state.value.connectedEndpoint?.name ?: "Nearby Device",
            fileName = fileName,
            totalBytes = payload.asFile()?.size ?: 0,
            direction = TransferDirection.INCOMING,
        )

        _state.update { it.copy(transfers = it.transfers + (payload.id to transfer)) }
        notificationManager.showProgress(payload.id, transfer.fileName, transfer.direction, 0, transfer.totalBytes)
    }

    private fun finalizeIncomingFile(payloadId: Long, transfer: TransferItem) {
        scope.launch {
            val payload = incomingFilePayloads.remove(payloadId)
            val sourceUri = payload?.asFile()?.asUri()
            if (sourceUri == null) {
                markIncomingFailure(payloadId, transfer, "Incoming file URI missing")
                return@launch
            }

            val saved = receivedFileStore.saveIncomingFile(sourceUri, transfer.fileName)
            if (saved == null) {
                markIncomingFailure(payloadId, transfer, "Failed to save incoming file")
                return@launch
            }

            val completed = transfer.copy(
                fileName = saved.name,
                transferredBytes = transfer.totalBytes,
                status = TransferStatus.SUCCESS,
            )
            _state.update { it.copy(transfers = it.transfers + (payloadId to completed)) }
            notificationManager.showCompleted(payloadId, completed.fileName, completed.direction)
            scheduleTransferRemoval(payloadId, completed.direction)
            disconnectAfterSuccessfulTransfer()
        }
    }

    private fun markIncomingFailure(payloadId: Long, transfer: TransferItem, message: String) {
        val failed = transfer.copy(status = TransferStatus.FAILURE)
        _state.update { it.copy(transfers = it.transfers + (payloadId to failed), lastError = message) }
        notificationManager.showFailed(payloadId, failed.fileName, failed.direction)
        scheduleTransferRemoval(payloadId, failed.direction)
    }

    private fun scheduleTransferRemoval(payloadId: Long, direction: TransferDirection) {
        // Enforce max completed per direction — evict oldest beyond limit immediately
        val completed = _state.value.transfers.values
            .filter { it.direction == direction && it.status != TransferStatus.IN_PROGRESS }
        if (completed.size > MAX_COMPLETED_TRANSFERS) {
            val evict = completed.take(completed.size - MAX_COMPLETED_TRANSFERS).map { it.payloadId }.toSet()
            _state.update { it.copy(transfers = it.transfers - evict) }
        }
        scope.launch {
            delay(TRANSFER_TIMEOUT_MS)
            _state.update { it.copy(transfers = it.transfers - payloadId) }
        }
    }

    private fun clearPendingOutgoing() {        _state.update { it.copy(pendingOutgoing = null, isDiscovering = false, discoveredEndpoints = emptyList()) }
        connectionsClient.stopDiscovery()
    }

    private fun disconnectAfterSuccessfulTransfer() {
        val endpointId = _state.value.connectedEndpoint?.id
        scope.launch {
            delay(DISCONNECT_DELAY_MS)
            if (endpointId != null) {
                connectionsClient.disconnectFromEndpoint(endpointId)
            }
            _state.update { it.copy(connectedEndpoint = null) }
            // onDisconnected will fire and call startAdvertisingIfNeeded()
        }
    }

    private fun setError(message: String) {
        _state.update { it.copy(lastError = message) }
    }

    companion object {
        const val SERVICE_ID = "com.nearby.qml.tray"
        private const val FILE_PREFIX = "FILE:"
        private const val TEXT_PREFIX = "TEXT:"
        private const val DISCONNECT_DELAY_MS = 3_000L
        private const val TRANSFER_TIMEOUT_MS = 5_000L
        private const val MAX_COMPLETED_TRANSFERS = 2
    }
}
