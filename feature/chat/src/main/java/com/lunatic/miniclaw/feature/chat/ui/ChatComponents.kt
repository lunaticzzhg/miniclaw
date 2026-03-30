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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lunatic.miniclaw.domain.chat.model.MessageRole
import com.lunatic.miniclaw.feature.chat.presentation.ChatIntent
import com.lunatic.miniclaw.feature.chat.presentation.ChatMessageItemUiModel

@Composable
internal fun ChatMessagesPanel(
    modifier: Modifier = Modifier,
    messages: List<ChatMessageItemUiModel>,
    listState: LazyListState,
    onRetryUserMessage: (String) -> Unit,
    onRetryAssistantMessage: (String) -> Unit,
    onJumpToBottom: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .fillMaxWidth()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatMessageItem(
                    message = message,
                    onRetry = {
                        if (message.role == MessageRole.USER) {
                            onRetryUserMessage(message.id)
                        } else {
                            onRetryAssistantMessage(message.id)
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
                onClick = onJumpToBottom
            ) {
                Text(text = "↓")
            }
        }
    }
}

@Composable
internal fun ChatInputBar(
    inputText: String,
    canSend: Boolean,
    canStop: Boolean,
    onInputChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    onStopClicked: () -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = inputText,
        onValueChange = onInputChanged,
        minLines = 1,
        maxLines = 4,
        label = { Text(text = "输入消息") },
        trailingIcon = {
            if (canStop) {
                TextButton(onClick = onStopClicked) {
                    Text(text = "停止")
                }
            } else {
                TextButton(enabled = canSend, onClick = onSendClicked) {
                    Text(text = "发送")
                }
            }
        }
    )
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

internal fun LazyListState.isNearBottom(): Boolean {
    val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return true
    val totalCount = layoutInfo.totalItemsCount
    if (totalCount == 0) return true
    return lastVisibleIndex >= totalCount - 2
}
