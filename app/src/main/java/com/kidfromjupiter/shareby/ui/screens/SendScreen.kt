package com.kidfromjupiter.shareby.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kidfromjupiter.shareby.model.Endpoint
import com.kidfromjupiter.shareby.model.NearbyShareState
import com.kidfromjupiter.shareby.model.OutgoingShareContent
import com.kidfromjupiter.shareby.model.TransferDirection
import com.kidfromjupiter.shareby.model.TransferItem
import com.kidfromjupiter.shareby.model.TransferStatus
import com.kidfromjupiter.shareby.ui.components.ConnectionState
import com.kidfromjupiter.shareby.ui.components.Device
import com.kidfromjupiter.shareby.ui.components.DeviceItem
import com.kidfromjupiter.shareby.ui.components.DeviceType
import kotlinx.coroutines.delay

@Composable
fun SendScreen(
    state: NearbyShareState,
    pendingShare: OutgoingShareContent?,
    onConnect: (Endpoint) -> Unit,
    onRetryDiscovery: () -> Unit,
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deviceStates = remember { mutableStateMapOf<String, Pair<ConnectionState, Float>>() }

    // Fresh state for every new share session
    LaunchedEffect(state.pendingOutgoing) {
        deviceStates.clear()
    }

    // Flip any CONNECTING device to FAILED when an error is reported
    LaunchedEffect(state.lastError) {
        if (state.lastError != null) {
            deviceStates.keys.toList().forEach { id ->
                if (deviceStates[id]?.first == ConnectionState.CONNECTING) {
                    deviceStates[id] = ConnectionState.FAILED to 0f
                }
            }
        }
    }
    
    // Convert Endpoints to Devices for display
    val devices = remember(state.discoveredEndpoints) {
        state.discoveredEndpoints.map { endpoint ->
            Device(
                id = endpoint.id,
                name = endpoint.name,
                type = DeviceType.values().random(), // In real app, get from endpoint metadata
                color = generateColorForDevice(endpoint.id)
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Send Files",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (state.isDiscovering) {
                            "Scanning for devices..."
                        } else {
                            "${devices.size} device${if (devices.size != 1) "s" else ""} nearby"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                IconButton(
                    onClick = onRetryDiscovery,
                    enabled = !state.isDiscovering
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "refresh")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = if (state.isDiscovering) 360f else 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "rotation"
                    )
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // File selection area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPickFile() },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.FileUpload,
                        contentDescription = "Upload",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (pendingShare != null) {
                        Text(
                            text = when (pendingShare) {
                                is OutgoingShareContent.File -> pendingShare.fileName
                                is OutgoingShareContent.Text -> "Text content ready"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Tap a device below to send",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "Select files to share",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Tap to choose files or folders",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Completed transfers section
        val completedTransfers = state.transfers.values.filter {
            it.direction == TransferDirection.OUTGOING && it.status == TransferStatus.SUCCESS
        }
        if (completedTransfers.isNotEmpty()) {
            Text(
                text = "COMPLETED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp,
            ) {
                Column {
                    completedTransfers.forEachIndexed { index, transfer ->
                        CompletedTransferRow(transfer = transfer)
                        if (index < completedTransfers.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Devices section header
        Text(
            text = "AVAILABLE DEVICES",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Devices grid
        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .border(4.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Scanning for nearby devices...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                val outgoingTransfer = state.transfers.values.firstOrNull {
                    it.direction == TransferDirection.OUTGOING
                }
                items(devices, key = { it.id }) { device ->
                    val (connectionState, progress) = when {
                        state.connectedEndpoint?.id == device.id && outgoingTransfer != null -> {
                            val p = if (outgoingTransfer.totalBytes > 0)
                                (outgoingTransfer.transferredBytes.toFloat() / outgoingTransfer.totalBytes).coerceIn(0f, 1f)
                            else 0f
                            when (outgoingTransfer.status) {
                                TransferStatus.IN_PROGRESS -> ConnectionState.TRANSFERRING to p
                                TransferStatus.SUCCESS -> ConnectionState.COMPLETE to 1f
                                else -> ConnectionState.IDLE to 0f
                            }
                        }
                        else -> deviceStates[device.id] ?: (ConnectionState.IDLE to 0f)
                    }
                    
                    DeviceItem(
                        device = device,
                        connectionState = connectionState,
                        progress = progress,
                        onTap = { tappedDevice ->
                            // Find corresponding endpoint and connect
                            state.discoveredEndpoints.find { it.id == tappedDevice.id }?.let { endpoint ->
                                onConnect(endpoint)
                                // Simulate connection flow
                                deviceStates[device.id] = ConnectionState.CONNECTING to 0f
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedTransferRow(transfer: TransferItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = "Sent",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transfer.fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "To: ${transfer.endpointName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "Sent",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun generateColorForDevice(id: String): Color {
    val colors = listOf(
        Color(0xFF3B82F6), // Blue
        Color(0xFF8B5CF6), // Purple
        Color(0xFFEC4899), // Pink
        Color(0xFFF59E0B), // Amber
        Color(0xFF10B981), // Green
        Color(0xFF06B6D4), // Cyan
        Color(0xFF6366F1), // Indigo
        Color(0xFFEF4444), // Red
    )
    return colors[id.hashCode().mod(colors.size)]
}
