package com.kidfromjupiter.shareby.model

import android.net.Uri

data class Endpoint(
    val id: String,
    val name: String,
)

enum class TransferDirection {
    OUTGOING,
    INCOMING,
}

enum class TransferStatus {
    IN_PROGRESS,
    SUCCESS,
    FAILURE,
    CANCELED,
}

data class TransferItem(
    val payloadId: Long,
    val endpointName: String,
    val fileName: String,
    val totalBytes: Long,
    val transferredBytes: Long = 0,
    val direction: TransferDirection,
    val status: TransferStatus = TransferStatus.IN_PROGRESS,
)

sealed class OutgoingShareContent {
    data class File(
        val uri: Uri,
        val fileName: String,
        val fileSize: Long,
        val mimeType: String?
    ) : OutgoingShareContent()

    data class Text(
        val value: String,
    ) : OutgoingShareContent()
}

data class NearbyShareState(
    val isAdvertising: Boolean = false,
    val isDiscovering: Boolean = false,
    val discoveredEndpoints: List<Endpoint> = emptyList(),
    val connectedEndpoint: Endpoint? = null,
    val pendingOutgoing: OutgoingShareContent? = null,
    val transfers: Map<Long, TransferItem> = emptyMap(),
    val receivedTexts: List<String> = emptyList(),
    val displayName: String = "",
    val lastError: String? = null,
)
