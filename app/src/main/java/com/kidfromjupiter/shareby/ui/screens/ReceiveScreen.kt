package com.kidfromjupiter.shareby.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import com.kidfromjupiter.shareby.ui.components.SquigglyLinearProgress
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kidfromjupiter.shareby.model.Endpoint
import com.kidfromjupiter.shareby.model.NearbyShareState
import com.kidfromjupiter.shareby.model.TransferDirection
import com.kidfromjupiter.shareby.model.TransferItem
import com.kidfromjupiter.shareby.model.TransferStatus
import com.kidfromjupiter.shareby.ui.components.ConnectionState
import com.kidfromjupiter.shareby.ui.components.Device
import com.kidfromjupiter.shareby.ui.components.DeviceItem
import com.kidfromjupiter.shareby.ui.components.DeviceType

@Composable
fun ReceiveScreen(
    state: NearbyShareState,
    onConnect: (Endpoint) -> Unit,
    onStartReceiving: () -> Unit,
    onStopAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deviceStates = remember { mutableStateMapOf<String, Pair<ConnectionState, Float>>() }
    

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
                        text = "Receive Files",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (state.isAdvertising) {
                            "Visible to nearby devices"
                        } else {
                            "Not visible to others"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Button(
                    onClick = {
                        if (state.isAdvertising) onStopAll() else onStartReceiving()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isAdvertising) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = if (state.isAdvertising) "Visible" else "Hidden",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // Status card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (state.isAdvertising) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                tonalElevation = if (state.isAdvertising) 0.dp else 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(80.dp)
                    ) {
                        // Pulsing rings for active state
                        if (state.isAdvertising) {
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse1")
                            val scale1 by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.5f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1500, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "scale1"
                            )
                            val alpha1 by infiniteTransition.animateFloat(
                                initialValue = 0.5f,
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1500, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "alpha1"
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .scale(scale1)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = alpha1))
                            )
                            
                            val scale2 by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.5f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1500, easing = LinearEasing, delayMillis = 500),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "scale2"
                            )
                            val alpha2 by infiniteTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1500, easing = LinearEasing, delayMillis = 500),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "alpha2"
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .scale(scale2)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha2))
                            )
                        }
                        
                        // Center icon
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.isAdvertising) {
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.tertiary,
                                                MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        )
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (state.isAdvertising) Icons.Default.Radio else Icons.Default.Wifi,
                                contentDescription = if (state.isAdvertising) "Broadcasting" else "Offline",
                                modifier = Modifier.size(32.dp),
                                tint = if (state.isAdvertising) {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = if (state.isAdvertising) state.displayName else "Start Receiving",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = if (state.isAdvertising) {
                            "Other devices can send files to you"
                        } else {
                            "Make yourself visible to receive files"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Incoming transfers section
        val incomingTransfers = state.transfers.values.filter { it.direction == TransferDirection.INCOMING }
        if (incomingTransfers.isNotEmpty()) {
            Text(
                text = "INCOMING TRANSFERS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                incomingTransfers.forEach { transfer ->
                    IncomingTransferCard(transfer = transfer)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

    }
}

@Composable
private fun IncomingTransferCard(transfer: TransferItem) {
    val progress = if (transfer.totalBytes > 0) {
        (transfer.transferredBytes.toFloat() / transfer.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val progressPercent = (progress * 100).toInt()

    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transfer.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "From: ${transfer.endpointName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = when (transfer.status) {
                        TransferStatus.SUCCESS -> "Done"
                        TransferStatus.FAILURE -> "Failed"
                        TransferStatus.CANCELED -> "Canceled"
                        TransferStatus.IN_PROGRESS -> "$progressPercent%"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when (transfer.status) {
                        TransferStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary
                        TransferStatus.FAILURE, TransferStatus.CANCELED -> MaterialTheme.colorScheme.error
                        TransferStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            SquigglyLinearProgress(
                progress = when (transfer.status) {
                    TransferStatus.IN_PROGRESS -> progress
                    else -> 1f
                },
                enabled = transfer.status == TransferStatus.IN_PROGRESS,
                modifier = Modifier.fillMaxWidth().height(12.dp),
                color = when (transfer.status) {
                    TransferStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary
                    TransferStatus.FAILURE, TransferStatus.CANCELED -> MaterialTheme.colorScheme.error
                    TransferStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}
