package com.lunatic.miniclaw.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lunatic.miniclaw.domain.chat.model.MessageRole
import com.lunatic.miniclaw.feature.chat.presentation.ChatIntent
import com.lunatic.miniclaw.feature.chat.presentation.ChatMessageItemUiModel
import com.lunatic.miniclaw.feature.chat.presentation.ChatViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ChatRoute(
    sessionId: String
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = uiState.title) }
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        ChatMessageItem(
                            message = message,
                            onRetry = {
                                if (message.role == MessageRole.USER) {
                                    viewModel.onIntent(
                                        ChatIntent.RetryUserMessageClicked(message.id)
                                    )
                                } else {
                                    viewModel.onIntent(
                                        ChatIntent.RetryAssistantMessageClicked(message.id)
                                    )
                                }
                            }
                        )
                    }
                }

                if (!listState.isNearBottom()) {
                    FloatingActionButton(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        shape = CircleShape,
                        onClick = {
                            shouldFollowBottom = true
                            if (uiState.messages.isNotEmpty()) {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(uiState.messages.lastIndex)
                                }
                            }
                        }
                    ) {
                        Text(text = "↓")
                    }
                }
            }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.inputText,
                onValueChange = { viewModel.onIntent(ChatIntent.InputChanged(it)) },
                minLines = 1,
                maxLines = 4,
                label = { Text(text = "输入消息") },
                trailingIcon = {
                    if (uiState.canStop) {
                        TextButton(onClick = { viewModel.onIntent(ChatIntent.StopClicked) }) {
                            Text(text = "停止")
                        }
                    } else {
                        TextButton(
                            enabled = uiState.canSend,
                            onClick = { viewModel.onIntent(ChatIntent.SendClicked) }
                        ) {
                            Text(text = "发送")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessageItemUiModel,
    onRetry: () -> Unit
) {
    if (message.role == MessageRole.USER) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = if (message.content.isBlank()) "..." else message.content,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (message.statusText != null) {
                        Text(
                            text = message.statusText,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (message.showRetry) {
                        Button(onClick = onRetry) {
                            Text(text = "重试")
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 52.dp)
        ) {
            Text(text = if (message.content.isBlank()) "..." else message.content)
            if (message.statusText != null) {
                Text(text = message.statusText)
            }
            if (message.showRetry) {
                Button(onClick = onRetry) {
                    Text(text = "重试")
                }
            }
        }
    }
}

private fun LazyListState.isNearBottom(): Boolean {
    val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return true
    val totalCount = layoutInfo.totalItemsCount
    if (totalCount == 0) return true
    return lastVisibleIndex >= totalCount - 2
}
