package com.jarvis.ui.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.ui.theme.LocalJarvisGradient

@Composable
fun ChatScreen(
    seedNodeId: String?,
    onBack: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(seedNodeId) { if (seedNodeId != null) viewModel.seedFromNode(seedNodeId) }
    LaunchedEffect(Unit) { viewModel.refreshSetupState() }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.toggleLiveVoice() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalJarvisGradient.current.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalJarvisGradient.current.accentGlow),
        )
        Column(Modifier.fillMaxSize()) {
            ChatTopBar(
                title = state.title,
                useCloud = state.useCloud,
                onToggleCloud = viewModel::setUseCloud,
                onBack = onBack,
            )

            if (state.needsSetup && onOpenSettings != null) {
                SetupCard(onOpenSettings = onOpenSettings, onDismiss = viewModel::dismissSetup)
            }

            if (state.liveMode) {
                LiveVoiceBanner(
                    speaking = state.speaking,
                    listening = state.listening,
                    partial = state.voicePartial,
                    amplitude = state.voiceAmplitude,
                )
            }

            if (state.messages.isEmpty() && !state.liveMode) {
                EmptyState(Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.messages, key = { it.id }) { msg -> MessageBubble(msg) }
                    if (state.thinking) { item { ThinkingBubble() } }
                }
            }

            Composer(
                input = state.input,
                liveMode = state.liveMode,
                listening = state.listening,
                speaking = state.speaking,
                thinking = state.thinking,
                error = state.errorMessage,
                onInputChanged = viewModel::onInputChanged,
                onSend = viewModel::send,
                onToggleLive = {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) viewModel.toggleLiveVoice()
                    else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
            )
        }
    }
}

@Composable
private fun ChatTopBar(
    title: String,
    useCloud: Boolean,
    onToggleCloud: (Boolean) -> Unit,
    onBack: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = if (onBack == null) 4.dp else 0.dp)
                .size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        ModeToggle(useCloud = useCloud, onToggle = onToggleCloud)
    }
}

/**
 * Compact tappable pill showing Local vs Cloud. Replaces the earlier
 * Surface-with-Switch that clipped off the screen on narrower widths.
 */
@Composable
private fun ModeToggle(useCloud: Boolean, onToggle: (Boolean) -> Unit) {
    val bg = if (useCloud) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
    val fg = if (useCloud) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        onClick = { onToggle(!useCloud) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (useCloud) Icons.Rounded.CloudQueue else Icons.Rounded.PhoneAndroid,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                if (useCloud) "Cloud" else "Local",
                color = fg,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SetupCard(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Finish setting up Jarvis",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.size(6.dp))
            Text(
                "Pick a brain before chatting: download Gemma 3 1B for fully on-device " +
                    "answers, or paste an OpenRouter key to use the cloud.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onOpenSettings) { Text("Open Settings") }
                TextButton(onClick = onDismiss) {
                    Text("Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LiveVoiceBanner(
    speaking: Boolean,
    listening: Boolean,
    partial: String,
    amplitude: Float,
) {
    val status = when {
        speaking -> "Jarvis is speaking…"
        listening -> "Listening — speak when ready"
        else -> "Connecting…"
    }
    val transition = rememberInfiniteTransition(label = "live")
    val glow by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "live-glow",
    )
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f + glow * 0.08f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VoiceWaveform(
                    amplitude = if (speaking) 0.6f else amplitude,
                    listening = listening || speaking,
                    tall = true,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    status,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (partial.isNotBlank()) {
                Spacer(Modifier.size(8.dp))
                Text(
                    "\u201C$partial\u201D",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "empty")
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "empty-pulse",
    )
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.35f * pulse), Color.Transparent),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.size(24.dp))
        Text(
            "Hi, I'm Jarvis.",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            "Tell me something worth remembering,\nor ask me anything I've stored.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MessageBubble(msg: ChatMessageUi) {
    val alignment = if (msg.fromUser) Alignment.CenterEnd else Alignment.CenterStart
    Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (msg.fromUser) Alignment.End else Alignment.Start,
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (msg.fromUser) 20.dp else 4.dp,
                    bottomEnd = if (msg.fromUser) 4.dp else 20.dp,
                ),
                color = if (msg.fromUser) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    msg.text,
                    color = if (msg.fromUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            if (msg.memoryCites.isNotEmpty()) {
                Spacer(Modifier.size(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) {
                    msg.memoryCites.take(3).forEach { cite ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f),
                        ) {
                            Text(
                                cite,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    val transition = rememberInfiniteTransition(label = "think")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "think-alpha",
    )
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(3) { i ->
                    val phase by rememberInfiniteTransition(label = "d$i").animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            tween(900, delayMillis = i * 180),
                            RepeatMode.Reverse,
                        ),
                        label = "dot$i",
                    )
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor.copy(alpha = phase * alpha)),
                    )
                }
            }
        }
    }
}

@Composable
private fun Composer(
    input: String,
    liveMode: Boolean,
    listening: Boolean,
    speaking: Boolean,
    thinking: Boolean,
    error: String?,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onToggleLive: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 6.dp),
    ) {
        error?.let {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveMicButton(
                    listening = liveMode || listening || speaking,
                    onClick = onToggleLive,
                )
                Spacer(Modifier.size(8.dp))
                if (liveMode) {
                    Text(
                        text = when {
                            speaking -> "Jarvis is speaking."
                            listening -> "Listening. Tap stop to exit."
                            else -> "Voice mode. Tap stop to exit."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 12.dp),
                    )
                } else {
                    BasicTextField(
                        value = input,
                        onValueChange = onInputChanged,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 5,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 10.dp),
                        decorationBox = { inner ->
                            if (input.isEmpty()) {
                                Text(
                                    "Ask or tell Jarvis…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 15.sp,
                                )
                            }
                            inner()
                        },
                    )
                    Spacer(Modifier.size(4.dp))
                    SendButton(
                        enabled = input.isNotBlank() && !thinking,
                        onClick = onSend,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceWaveform(amplitude: Float, listening: Boolean, tall: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "wave")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900)),
        label = "wave-drift",
    )
    val animatedAmp by animateFloatAsState(
        targetValue = if (listening) amplitude.coerceAtLeast(0.12f) else 0f,
        label = "voice-amp",
    )
    val barColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier.size(
            width = if (tall) 56.dp else 40.dp,
            height = if (tall) 36.dp else 22.dp,
        ),
    ) {
        val bars = if (tall) 7 else 5
        val w = size.width / (bars * 2 - 1)
        repeat(bars) { i ->
            val phase = (drift + i * 0.2f) % 1f
            val h = size.height * (0.2f + 0.8f * animatedAmp * (0.5f + 0.5f * kotlin.math.sin(phase * Math.PI * 2).toFloat()))
            val x = i * 2f * w
            val top = (size.height - h) / 2f
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, top),
                size = androidx.compose.ui.geometry.Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun LiveMicButton(listening: Boolean, onClick: () -> Unit) {
    val color = if (listening) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = color),
        modifier = Modifier.size(44.dp),
    ) {
        Icon(
            imageVector = if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic,
            contentDescription = if (listening) "Stop voice" else "Start live voice",
            tint = if (listening) MaterialTheme.colorScheme.onError
            else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val bg = if (enabled) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = bg,
            disabledContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
        ),
        modifier = Modifier.size(44.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.Send,
            contentDescription = "Send",
            tint = if (enabled) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
    }
}
