package com.lunatic.miniclaw.feature.modelconfig.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lunatic.miniclaw.feature.modelconfig.presentation.ModelConfigEffect
import com.lunatic.miniclaw.feature.modelconfig.presentation.ModelConfigIntent
import com.lunatic.miniclaw.feature.modelconfig.presentation.ModelConfigViewModel
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigRoute(
    onNavigateBack: () -> Unit
) {
    val viewModel: ModelConfigViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onIntent(ModelConfigIntent.ScreenStarted)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                ModelConfigEffect.NavigateBack -> onNavigateBack()
                is ModelConfigEffect.ShowToast -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型配置") },
                navigationIcon = {
                    TextButton(onClick = { viewModel.onIntent(ModelConfigIntent.BackToChatClicked) }) {
                        Text("返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.selectedProvider.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Provider") }
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.modelName,
                onValueChange = { viewModel.onIntent(ModelConfigIntent.ModelNameChanged(it)) },
                label = { Text("模型名称") },
                singleLine = true
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.apiKeyInput,
                onValueChange = { viewModel.onIntent(ModelConfigIntent.ApiKeyChanged(it)) },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.baseUrlInput,
                onValueChange = { viewModel.onIntent(ModelConfigIntent.BaseUrlChanged(it)) },
                label = { Text("Base URL (可选)") },
                singleLine = true
            )

            uiState.availabilityText?.let { Text(text = it) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    enabled = uiState.canSave && !uiState.isSaving,
                    onClick = { viewModel.onIntent(ModelConfigIntent.SaveClicked) }
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    }
                    Text("保存")
                }

                Button(
                    enabled = uiState.canSave && !uiState.isValidating,
                    onClick = { viewModel.onIntent(ModelConfigIntent.ValidateClicked) }
                ) {
                    if (uiState.isValidating) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    }
                    Text("保存并测试")
                }
            }
        }
    }
}
