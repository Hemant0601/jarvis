package com.jarvis.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jarvis.ui.capture.CaptureScreen
import com.jarvis.ui.chat.ChatScreen
import com.jarvis.ui.graph.GraphScreen
import com.jarvis.ui.settings.SettingsScreen

sealed class Dest(val route: String, val label: String) {
    data object Graph : Dest("graph", "Brain")
    data object Capture : Dest("capture", "Capture")
    data object Settings : Dest("settings", "Settings")
    data object Chat : Dest("chat?nodeId={nodeId}", "Chat") {
        fun withNode(nodeId: String?) = "chat?nodeId=${nodeId ?: ""}"
    }
}

private val bottomDests = listOf(Dest.Graph, Dest.Capture, Dest.Settings)

@Composable
fun JarvisApp(startInCapture: Boolean = false) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomDests.forEach { dest ->
                    val selected = current == dest.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(dest.route) {
                                popUpTo(Dest.Graph.route)
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = when (dest) {
                                    Dest.Graph -> Icons.Rounded.Hub
                                    Dest.Capture -> Icons.Rounded.Mic
                                    Dest.Settings -> Icons.Rounded.Settings
                                    else -> Icons.Rounded.Hub
                                },
                                contentDescription = dest.label
                            )
                        },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (startInCapture) Dest.Capture.route else Dest.Graph.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Dest.Graph.route) {
                GraphScreen(
                    onOpenNode = { nodeId -> nav.navigate(Dest.Chat.withNode(nodeId)) }
                )
            }
            composable(Dest.Capture.route) {
                CaptureScreen(
                    onFinished = { nav.navigate(Dest.Graph.route) }
                )
            }
            composable(Dest.Settings.route) { SettingsScreen() }
            composable(Dest.Chat.route) { backStack ->
                val nodeId = backStack.arguments?.getString("nodeId").takeIf { !it.isNullOrEmpty() }
                ChatScreen(seedNodeId = nodeId, onBack = { nav.popBackStack() })
            }
        }
    }
}
