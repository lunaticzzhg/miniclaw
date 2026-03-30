package com.lunatic.miniclaw.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lunatic.miniclaw.feature.chat.presentation.ChatEffect
import com.lunatic.miniclaw.feature.chat.presentation.ChatIntent
import com.lunatic.miniclaw.feature.chat.presentation.ChatViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ChatRoute(
    sessionId: String,
    onNavigateToModelConfig: (String) -> Unit
) {
    val viewModel: ChatViewModel = koinViewModel(
        key = "chat-$sessionId",
        parameters = { parametersOf(sessionId) }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var shouldFollowBottom by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isNearBottom() }.collectLatest { isNearBottom ->
            shouldFollowBottom = isNearBottom
        }
    }

    LaunchedEffect(uiState.messages) {
        if (shouldFollowBottom && uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ChatEffect.NavigateToModelConfig -> {
                    onNavigateToModelConfig(effect.sessionId)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = uiState.title) },
                actions = {
                    TextButton(onClick = { viewModel.onIntent(ChatIntent.ModelSwitcherClicked) }) {
                        val suffix = uiState.availabilityText?.let { "($it)" }.orEmpty()
                        Text(text = "${uiState.currentProviderLabel}$suffix")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ChatMessagesPanel(
                modifier = Modifier.weight(1f),
                messages = uiState.messages,
                listState = listState,
                onRetryUserMessage = { messageId ->
                    viewModel.onIntent(ChatIntent.RetryUserMessageClicked(messageId))
                },
                onRetryAssistantMessage = { messageId ->
                    viewModel.onIntent(ChatIntent.RetryAssistantMessageClicked(messageId))
                },
                onJumpToBottom = {
                    shouldFollowBottom = true
                    if (uiState.messages.isNotEmpty()) {
                        coroutineScope.launch {
                            listState.animateScrollToItem(uiState.messages.lastIndex)
                        }
                    }
                }
            )

            ChatInputBar(
                inputText = uiState.inputText,
                canSend = uiState.canSend,
                canStop = uiState.canStop,
                onInputChanged = { viewModel.onIntent(ChatIntent.InputChanged(it)) },
                onSendClicked = { viewModel.onIntent(ChatIntent.SendClicked) },
                onStopClicked = { viewModel.onIntent(ChatIntent.StopClicked) }
            )
        }
    }
}
