package com.kidfromjupiter.shareby.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class NavDestination {
    SEND,
    RECEIVE
}

@Composable
fun BottomNavigation(
    currentDestination: NavDestination,
    onDestinationSelected: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        NavigationBarItem(
            selected = currentDestination == NavDestination.SEND,
            onClick = { onDestinationSelected(NavDestination.SEND) },
            icon = {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = { Text("Send") }
        )
        NavigationBarItem(
            selected = currentDestination == NavDestination.RECEIVE,
            onClick = { onDestinationSelected(NavDestination.RECEIVE) },
            icon = {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Receive",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = { Text("Receive") }
        )
    }
}
