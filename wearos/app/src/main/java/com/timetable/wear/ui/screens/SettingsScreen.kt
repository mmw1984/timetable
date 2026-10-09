package com.timetable.wear.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.timetable.wear.data.local.DenseLayoutMode

@Composable
fun SettingsScreen(
    onEditUrl: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val denseMode by viewModel.denseMode.collectAsStateWithLifecycle()
    val mergeConsecutive by viewModel.mergeConsecutive.collectAsStateWithLifecycle()
    val transformationSpec = rememberTransformationSpec()
    val columnState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = columnState) { contentPadding ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text("設定")
                }
            }
            item {
                SourceCard(
                    url = urlInput,
                    onEdit = onEditUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec)
                )
            }
            item {
                Button(
                    onClick = viewModel::refresh,
                    enabled = !isRefreshing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                    icon = {
                        if (isRefreshing) {
                            CircularProgressIndicator(progress = { 0.4f }, modifier = Modifier.padding(4.dp))
                        }
                    }
                ) {
                    Text(if (isRefreshing) "更新中" else "手動更新", maxLines = 1)
                }
            }
            message?.let { status ->
                item {
                    StatusCard(
                        message = status,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding),
                        transformation = SurfaceTransformation(transformationSpec)
                    )
                }
            }
            item {
                Button(
                    onClick = viewModel::cycleDenseMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text("版面：${DenseLayoutMode.displayText(denseMode)}", maxLines = 1)
                }
            }
            item {
                Button(
                    onClick = { viewModel.setMergeConsecutive(!mergeConsecutive) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(if (mergeConsecutive) "連堂合併：開" else "連堂合併：關", maxLines = 1)
                }
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text("時間表 Wear OS", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "v1.3",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun UrlEditorScreen(
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    ScreenScaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("資料來源", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = urlInput,
                onValueChange = viewModel::updateUrl,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("HTTPS URL") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.saveUrl() }),
                enabled = !isRefreshing
            )
            Button(
                onClick = viewModel::saveUrl,
                enabled = !isRefreshing && urlInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                icon = {
                    if (isRefreshing) {
                        CircularProgressIndicator(progress = { 0.4f }, modifier = Modifier.padding(4.dp))
                    }
                }
            ) {
                Text(if (isRefreshing) "驗證並更新" else "儲存並更新", maxLines = 1)
            }
            message?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Clip
                )
            }
            Button(onClick = onDismiss, enabled = !isRefreshing, modifier = Modifier.fillMaxWidth()) {
                Text("完成")
            }
        }
    }
}

@Composable
private fun SourceCard(url: String, onEdit: () -> Unit, modifier: Modifier, transformation: SurfaceTransformation) {
    Card(modifier = modifier, transformation = transformation) {
        Text("資料來源", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            text = url.ifBlank { "尚未設定" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Clip
        )
        Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Text("編輯 URL", maxLines = 1)
        }
    }
}

@Composable
private fun StatusCard(message: String, modifier: Modifier, transformation: SurfaceTransformation) {
    Card(modifier = modifier, transformation = transformation) {
        Text("更新狀態", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(message, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Clip)
    }
}
