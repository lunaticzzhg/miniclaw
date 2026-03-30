package com.lunatic.miniclaw.feature.sessionlist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lunatic.miniclaw.feature.sessionlist.presentation.SessionListEffect
import com.lunatic.miniclaw.feature.sessionlist.presentation.SessionListIntent
import com.lunatic.miniclaw.feature.sessionlist.presentation.SessionListViewModel
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SessionListRoute(
    onOpenChat: (String) -> Unit
) {
    val viewModel: SessionListViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is SessionListEffect.NavigateToChat -> onOpenChat(effect.sessionId)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = "MiniClaw") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                onClick = { viewModel.onIntent(SessionListIntent.CreateSessionClicked) }
            ) {
                Text(text = "新建会话")
            }

            if (uiState.sessions.isEmpty()) {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = if (uiState.isLoading) "加载中..." else "还没有会话，开始第一段对话"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.sessions, key = { it.id }) { session ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenChat(session.id) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = session.title)
                                Text(text = session.preview)
                                Text(text = session.updatedAtText)
                            }
                        }
                    }
                }
            }
        }
    }
}
