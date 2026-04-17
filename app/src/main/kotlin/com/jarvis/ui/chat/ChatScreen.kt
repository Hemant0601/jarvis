package com.jarvis.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    seedNodeId: String?,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(seedNodeId) {
        if (seedNodeId != null) viewModel.seedFromNode(seedNodeId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconToggleButton(
                        checked = state.useCloud,
                        onCheckedChange = { viewModel.setUseCloud(it) },
                    ) {
                        Icon(
                            imageVector = if (state.useCloud) Icons.Rounded.CloudQueue else Icons.Rounded.PhoneAndroid,
                            contentDescription = if (state.useCloud) "Using OpenRouter" else "Using on-device Gemma"
                        )
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                reverseLayout = false,
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    MessageBubble(msg)
                }
                if (state.thinking) {
                    item { Text("…", modifier = Modifier.padding(12.dp)) }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = viewModel::onInputChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask your brain…") },
                    shape = RoundedCornerShape(24.dp),
                )
                Spacer(Modifier.size(8.dp))
                IconButton(
                    onClick = viewModel::send,
                    enabled = state.input.isNotBlank() && !state.thinking,
                ) {
                    Icon(Icons.Rounded.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessageUi) {
    val alignment = if (msg.fromUser) Alignment.CenterEnd else Alignment.CenterStart
    val color = if (msg.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (msg.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(
            color = color,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            Text(
                msg.text,
                color = textColor,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}
