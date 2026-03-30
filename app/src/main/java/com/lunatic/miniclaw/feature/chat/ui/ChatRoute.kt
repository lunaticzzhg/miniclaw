package com.lunatic.miniclaw.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lunatic.miniclaw.feature.chat.presentation.ChatViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ChatRoute(
    sessionId: String,
    onBackClicked: () -> Unit
) {
    val viewModel: ChatViewModel = koinViewModel(
        key = "chat-$sessionId",
        parameters = { parametersOf(sessionId) }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = uiState.title) },
                navigationIcon = {
                    Button(onClick = onBackClicked) {
                        Text(text = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    val content = if (message.content.isBlank()) "..." else message.content
                    Text(text = "${message.role}: $content")
                    if (message.statusText != null) {
                        Text(text = message.statusText)
                    }
                }
            }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.inputText,
                onValueChange = viewModel::onInputChanged,
                label = { Text(text = "输入消息") }
            )
            if (uiState.canStop) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = viewModel::onStopClicked
                ) {
                    Text(text = "停止")
                }
            } else {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.canSend,
                    onClick = viewModel::onSendClicked
                ) {
                    Text(text = "发送")
                }
            }
        }
    }
}
