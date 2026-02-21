package com.kidfromjupiter.shareby.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kidfromjupiter.shareby.model.Endpoint
import com.kidfromjupiter.shareby.model.NearbyShareState
import com.kidfromjupiter.shareby.ui.components.BottomNavigation
import com.kidfromjupiter.shareby.ui.components.NavDestination
import com.kidfromjupiter.shareby.ui.screens.ReceiveScreen
import com.kidfromjupiter.shareby.ui.screens.SendScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharebyApp(
    state: NearbyShareState,
    onConnect: (Endpoint) -> Unit,
    onRetryDiscovery: () -> Unit,
    onCancelShare: () -> Unit,
    onPickFile: () -> Unit,
    onSaveDisplayName: (String) -> Unit,
    onStartReceiving: () -> Unit,
    onStopAll: () -> Unit,
    onClearError: () -> Unit,
) {
    var currentDestination by remember { 
        mutableStateOf(
            if (state.pendingOutgoing != null) NavDestination.SEND else NavDestination.RECEIVE
        )
    }
    var showSettings by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.lastError) {
        val error = state.lastError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        onClearError()
    }

    // Switch to Send screen when sharing content
    LaunchedEffect(state.pendingOutgoing) {
        if (state.pendingOutgoing != null) {
            currentDestination = NavDestination.SEND
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomNavigation(
                currentDestination = currentDestination,
                onDestinationSelected = { currentDestination = it }
            )
        }
    ) { innerPadding ->
        when (currentDestination) {
            NavDestination.SEND -> {
                SendScreen(
                    state = state,
                    pendingShare = state.pendingOutgoing,
                    onConnect = onConnect,
                    onRetryDiscovery = onRetryDiscovery,
                    onPickFile = onPickFile,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            NavDestination.RECEIVE -> {
                ReceiveScreen(
                    state = state,
                    onConnect = onConnect,
                    onStartReceiving = onStartReceiving,
                    onStopAll = onStopAll,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }

    if (showSettings) {
        DisplayNameDialog(
            initialValue = state.displayName,
            onDismiss = { showSettings = false },
            onSave = {
                onSaveDisplayName(it)
                showSettings = false
            },
        )
    }
}

@Composable
private fun DisplayNameDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Display name") },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Visible to nearby devices") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
