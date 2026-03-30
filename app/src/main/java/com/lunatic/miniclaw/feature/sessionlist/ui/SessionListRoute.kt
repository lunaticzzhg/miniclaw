package com.lunatic.miniclaw.feature.sessionlist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SessionListRoute(
    onSessionClicked: (String) -> Unit,
    onCreateSessionClicked: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = "MiniClaw") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "会话列表（骨架页）")
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSessionClicked("demo-session") }
            ) {
                Text(text = "进入示例会话")
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onCreateSessionClicked
            ) {
                Text(text = "新建会话")
            }
        }
    }
}
