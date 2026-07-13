package com.windscore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.windscore.core.model.Score
import com.windscore.ui.components.PillButtonStyle
import com.windscore.ui.theme.WindScoreColors
import com.windscore.ui.theme.WindScoreRounded
import com.windscore.ui.theme.WindScoreSpacing
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import windscore.composeapp.generated.resources.Res
import windscore.composeapp.generated.resources.arrow_left_thin
import windscore.composeapp.generated.resources.bug_thin
import windscore.composeapp.generated.resources.clock_thin
import windscore.composeapp.generated.resources.folder_open_thin
import windscore.composeapp.generated.resources.gear_six_thin
import windscore.composeapp.generated.resources.music_notes_thin
import windscore.composeapp.generated.resources.pencil_simple_thin
import windscore.composeapp.generated.resources.trash_thin

private const val FEEDBACK_URL = "https://github.com/ECeternalcat/WindScore/issues"

@Composable
fun WelcomeScreen(
    isCompact: Boolean,
    recentScores: List<Score>,
    onScoreClick: (Score) -> Unit,
    onRename: (Score, String) -> Unit,
    onDelete: (Score) -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // 弹窗状态
    var pendingDelete by remember { mutableStateOf<Score?>(null) }
    var renamingScore by remember { mutableStateOf<Score?>(null) }
    var renameInput by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { focusManager.clearFocus() },
            ),
    ) {
        WelcomeTopBar()
        if (showSettings) {
            SettingsScreen(onBack = { showSettings = false }, modifier = Modifier.weight(1f))
        } else if (isCompact) {
            ScoreListBody(
                recentScores = recentScores,
                onScoreClick = onScoreClick,
                onShowSettings = { showSettings = true },
                onRename = { renamingScore = it; renameInput = it.title },
                onDelete = { pendingDelete = it },
                modifier = Modifier.weight(1f),
            )
        } else {
            DesktopWelcomeBody(
                recentScores = recentScores,
                onPickFile = onScoreClick,
                onShowSettings = { showSettings = true },
                onRename = { renamingScore = it; renameInput = it.title },
                onDelete = { pendingDelete = it },
                modifier = Modifier.weight(1f),
            )
        }
    }

    // 删除确认弹窗
    pendingDelete?.let { score ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除乐谱") },
            text = { Text("即将删除「${score.title}」，是否确定？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(score)
                    pendingDelete = null
                }) { Text("确认删除", color = WindScoreColors.accentMagenta) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    // 重命名弹窗
    renamingScore?.let { score ->
        val keyboard = LocalSoftwareKeyboardController.current
        AlertDialog(
            onDismissRequest = { renamingScore = null; keyboard?.hide() },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                        .onKeyEvent {
                            if (it.key == Key.Enter) {
                                val trimmed = renameInput.trim()
                                if (trimmed.isNotEmpty()) {
                                    onRename(score, trimmed)
                                    renamingScore = null
                                    keyboard?.hide()
                                }
                                true
                            } else false
                        },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = renameInput.trim()
                    if (trimmed.isNotEmpty()) {
                        onRename(score, trimmed)
                        renamingScore = null
                        keyboard?.hide()
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renamingScore = null; keyboard?.hide() }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun WelcomeTopBar() {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = WindScoreSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("WindScore", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700))
        Spacer(Modifier.width(WindScoreSpacing.sm))
        Text("MusicXML播放器", style = MaterialTheme.typography.bodySmall, color = WindScoreColors.ink)
    }
}

@Composable
private fun DesktopWelcomeBody(
    recentScores: List<Score>,
    onPickFile: (Score) -> Unit,
    onShowSettings: () -> Unit,
    onRename: (Score) -> Unit,
    onDelete: (Score) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxSize().padding(WindScoreSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.lg),
    ) {
        ActionCard(
            onPickFile = onPickFile,
            onShowSettings = onShowSettings,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        RecentScoresPanel(
            scores = recentScores,
            onScoreClick = onPickFile,
            onRename = onRename,
            onDelete = onDelete,
            modifier = Modifier.weight(2f).fillMaxHeight(),
        )
    }
}

@Composable
private fun ActionCard(
    onPickFile: (Score) -> Unit,
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(WindScoreRounded.lg)),
        color = WindScoreColors.blockLime,
        shape = RoundedCornerShape(WindScoreRounded.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val openFile = rememberScoreFileOpener(onPickFile)
            ActionButton(icon = Res.drawable.folder_open_thin, label = "打开文件", onClick = openFile)
            Spacer(Modifier.width(WindScoreSpacing.xxl))
            ActionButton(icon = Res.drawable.gear_six_thin, label = "设置", onClick = onShowSettings)
        }
    }
}

@Composable
private fun ActionButton(icon: DrawableResource, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(WindScoreRounded.lg))
                .background(WindScoreColors.canvas).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), label, Modifier.size(36.dp), WindScoreColors.ink)
        }
        Spacer(Modifier.height(WindScoreSpacing.sm))
        Text(label, style = MaterialTheme.typography.titleMedium, color = WindScoreColors.ink)
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(WindScoreSpacing.xxl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(WindScoreRounded.full)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(Res.drawable.arrow_left_thin), "返回", Modifier.size(22.dp), WindScoreColors.ink)
            }
            Spacer(Modifier.width(WindScoreSpacing.sm))
            Text("设置", style = MaterialTheme.typography.headlineMedium, color = WindScoreColors.ink)
        }
        Spacer(Modifier.height(WindScoreSpacing.xl))
        Surface(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(WindScoreRounded.lg)).clickable { openUrl(FEEDBACK_URL) },
            color = WindScoreColors.surfaceSoft,
            shape = RoundedCornerShape(WindScoreRounded.lg),
        ) {
            Row(modifier = Modifier.padding(WindScoreSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(WindScoreRounded.md)).background(WindScoreColors.blockPink),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(Res.drawable.bug_thin), null, Modifier.size(22.dp), WindScoreColors.ink)
                }
                Spacer(Modifier.width(WindScoreSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text("意见反馈", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W500), color = WindScoreColors.ink)
                    Text("在 GitHub Issues 中提交问题或建议", style = MaterialTheme.typography.bodySmall, color = WindScoreColors.ink)
                }
            }
        }
    }
}

// region 最近乐谱 + 搜索

@Composable
private fun RecentScoresPanel(
    scores: List<Score>,
    onScoreClick: (Score) -> Unit,
    onRename: (Score) -> Unit,
    onDelete: (Score) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val filtered = remember(searchQuery, scores) {
        if (searchQuery.isBlank()) scores
        else scores.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }
    // 搜索框聚焦且无内容 → 空白提示；有内容但无匹配 → 空白提示；否则正常列表
    val showEmptyHint = searchFocused && searchQuery.isBlank() || (searchQuery.isNotBlank() && filtered.isEmpty())

    Surface(
        modifier = modifier.clip(RoundedCornerShape(WindScoreRounded.lg)),
        color = WindScoreColors.surfaceSoft,
        shape = RoundedCornerShape(WindScoreRounded.lg),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(WindScoreSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(Res.drawable.clock_thin), null, Modifier.size(20.dp), WindScoreColors.ink)
                Spacer(Modifier.width(WindScoreSpacing.xs))
                Text("最近打开的乐谱", style = MaterialTheme.typography.titleMedium, color = WindScoreColors.ink)
            }
            Spacer(Modifier.height(WindScoreSpacing.md))
            SearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onFocusChange = { searchFocused = it },
            )
            Spacer(Modifier.height(WindScoreSpacing.sm))
            if (scores.isEmpty() && !searchFocused) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无最近记录", style = MaterialTheme.typography.bodyMedium, color = WindScoreColors.ink)
                }
            } else if (showEmptyHint) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有匹配的文件", style = MaterialTheme.typography.bodyMedium, color = WindScoreColors.ink)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm),
                ) {
                    items(filtered, key = { it.title + it.totalTicks }) { score ->
                        ScoreListItem(
                            score = score,
                            onClick = { onScoreClick(score) },
                            onRename = { onRename(score) },
                            onDelete = { onDelete(score) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreListBody(
    recentScores: List<Score>,
    onScoreClick: (Score) -> Unit,
    onShowSettings: () -> Unit,
    onRename: (Score) -> Unit,
    onDelete: (Score) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val filtered = remember(searchQuery, recentScores) {
        if (searchQuery.isBlank()) recentScores
        else recentScores.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }
    val showEmptyHint = searchFocused && searchQuery.isBlank() || (searchQuery.isNotBlank() && filtered.isEmpty())

    Column(modifier = modifier.fillMaxSize().padding(WindScoreSpacing.lg)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm),
        ) {
            ScoreFilePickerButton(onPicked = onScoreClick, style = PillButtonStyle.PRIMARY)
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(WindScoreRounded.full))
                    .background(WindScoreColors.surfaceSoft).clickable(onClick = onShowSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(Res.drawable.gear_six_thin), "设置", Modifier.size(22.dp), WindScoreColors.ink)
            }
        }
        Spacer(Modifier.height(WindScoreSpacing.lg))
        SearchField(searchQuery, { searchQuery = it }, { searchFocused = it })
        Spacer(Modifier.height(WindScoreSpacing.sm))
        Text("乐谱库", style = MaterialTheme.typography.headlineLarge, color = WindScoreColors.ink)
        Spacer(Modifier.height(WindScoreSpacing.sm))
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(WindScoreRounded.lg)),
            color = WindScoreColors.surfaceSoft,
            shape = RoundedCornerShape(WindScoreRounded.lg),
        ) {
            if (recentScores.isEmpty() && !searchFocused) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("点击上方按钮加载第一首乐谱", style = MaterialTheme.typography.bodyMedium, color = WindScoreColors.ink)
                }
            } else if (showEmptyHint) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有匹配的文件", style = MaterialTheme.typography.bodyMedium, color = WindScoreColors.ink)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(WindScoreSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm),
                ) {
                    items(filtered, key = { it.title + it.totalTicks }) { score ->
                        ScoreListItem(
                            score = score,
                            onClick = { onScoreClick(score) },
                            onRename = { onRename(score) },
                            onDelete = { onDelete(score) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) },
        placeholder = { Text("搜索乐谱…", style = MaterialTheme.typography.bodyMedium, color = WindScoreColors.ink.copy(alpha = 0.4f)) },
        singleLine = true,
        shape = RoundedCornerShape(WindScoreRounded.md),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = WindScoreColors.canvas,
            unfocusedContainerColor = WindScoreColors.canvas,
            cursorColor = WindScoreColors.ink,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = WindScoreColors.ink,
            unfocusedTextColor = WindScoreColors.ink,
        ),
    )
}

// endregion

@Composable
private fun ScoreListItem(
    score: Score,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(WindScoreRounded.md)).clickable(onClick = onClick),
        color = WindScoreColors.canvas,
        shape = RoundedCornerShape(WindScoreRounded.md),
    ) {
        Row(modifier = Modifier.padding(WindScoreSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(WindScoreRounded.md)).background(WindScoreColors.blockLilac),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(Res.drawable.music_notes_thin), null, Modifier.size(24.dp), WindScoreColors.ink)
            }
            Spacer(Modifier.width(WindScoreSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    score.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W500),
                    color = WindScoreColors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${score.notes.size} 音符 · ${score.tempo} BPM", style = MaterialTheme.typography.bodySmall, color = WindScoreColors.ink)
            }
            // 重命名
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(WindScoreRounded.full))
                    .clickable(onClick = onRename),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(Res.drawable.pencil_simple_thin), "重命名", Modifier.size(18.dp), WindScoreColors.ink.copy(alpha = 0.6f))
            }
            Spacer(Modifier.width(WindScoreSpacing.xxs))
            // 删除
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(WindScoreRounded.full))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(Res.drawable.trash_thin), "删除", Modifier.size(18.dp), WindScoreColors.ink.copy(alpha = 0.6f))
            }
        }
    }
}
