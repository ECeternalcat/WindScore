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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.windscore.core.model.AppConfig
import com.windscore.core.model.ThemeMode
import com.windscore.ViewMode
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.windscore.core.model.Score
import com.windscore.ui.components.PillButtonStyle
import com.windscore.ui.components.PillToggle
import com.windscore.ui.i18n.AppLanguage
import com.windscore.ui.i18n.AppStrings
import com.windscore.ui.i18n.strings
import com.windscore.ui.theme.WindScoreColors
import com.windscore.ui.theme.WindScoreRounded
import com.windscore.ui.theme.WindScoreSpacing
import com.windscore.ui.theme.WindScoreTheme
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
    onLanguageChange: (AppLanguage) -> Unit = {},
    config: AppConfig = com.windscore.core.loadAppConfig(),
    onConfigChange: (AppConfig) -> Unit = {},
) {
    var showSettings by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val s = strings()

    // 弹窗状态
    var pendingDelete by remember { mutableStateOf<Score?>(null) }
    var renamingScore by remember { mutableStateOf<Score?>(null) }
    var renameInput by remember { mutableStateOf("") }

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkTheme = when (config.themeMode) {
        ThemeMode.FOLLOW_SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    Column(
        Modifier.fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { focusManager.clearFocus() },
            ),
    ) {
        WelcomeTopBar()
        if (isCompact) {
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

    SettingsDialog(
        visible = showSettings,
        onDismiss = { showSettings = false }
    ) {
        WindScoreTheme(darkTheme = isDarkTheme) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = WindScoreColors.canvas,
            ) {
                SettingsScreen(
                    onBack = { showSettings = false },
                    onLanguageChange = onLanguageChange,
                    config = config,
                    onConfigChange = onConfigChange
                )
            }
        }
    }

    // 删除确认弹窗
    pendingDelete?.let { score ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(s.deleteScore) },
            text = { Text(s.confirmDelete(score.title)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(score)
                    pendingDelete = null
                }) { Text(s.confirmDeleteAction, color = WindScoreColors.accentMagenta) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(s.cancel) }
            },
        )
    }

    // 重命名弹窗
    renamingScore?.let { score ->
        val keyboard = LocalSoftwareKeyboardController.current
        AlertDialog(
            onDismissRequest = { renamingScore = null; keyboard?.hide() },
            title = { Text(s.rename) },
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
                }) { Text(s.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { renamingScore = null; keyboard?.hide() }) { Text(s.cancel) }
            },
        )
    }
}

@Composable
private fun WelcomeTopBar() {
    val s = strings()
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = WindScoreSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("WindScore", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700))
        Spacer(Modifier.width(WindScoreSpacing.sm))
        Text(s.appSubtitle, style = MaterialTheme.typography.bodySmall, color = WindScoreColors.ink)
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
    val s = strings()
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
            ActionButton(icon = Res.drawable.folder_open_thin, label = s.openFile, onClick = openFile)
            Spacer(Modifier.width(WindScoreSpacing.xxl))
            ActionButton(icon = Res.drawable.gear_six_thin, label = s.settings, onClick = onShowSettings)
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
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onLanguageChange: (AppLanguage) -> Unit = {},
    config: AppConfig = com.windscore.core.loadAppConfig(),
    onConfigChange: (AppConfig) -> Unit = {}
) {
    val s = strings()
    val isZh = AppStrings.language == AppLanguage.CHINESE
    val isJa = AppStrings.language == AppLanguage.JAPANESE

    val themeLabel = if (isZh) "界面主题" else if (isJa) "UIテーマ" else "UI Theme"
    val followSystemLabel = if (isZh) "跟随系统" else if (isJa) "システム" else "System"
    val lightLabel = if (isZh) "浅色" else if (isJa) "ライト" else "Light"
    val darkLabel = if (isZh) "深色" else if (isJa) "ダーク" else "Dark"

    val defaultViewLabel = if (isZh) "默认视图" else if (isJa) "デフォルトビュー" else "Default View"
    val viewRollLabel = s.pianoRoll
    val viewSheetKbLabel = s.readSheet
    val viewSheetFullLabel = s.viewSheet

    val defaultPreBeatLabel = if (isZh) "默认预备拍" else if (isJa) "デフォルトカウント" else "Default Pre-beat"

    Column(modifier = modifier.fillMaxSize().padding(WindScoreSpacing.xxl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(WindScoreRounded.full)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(Res.drawable.arrow_left_thin), s.back, Modifier.size(22.dp), WindScoreColors.ink)
            }
            Spacer(Modifier.width(WindScoreSpacing.sm))
            Text(s.settings, style = MaterialTheme.typography.headlineMedium, color = WindScoreColors.ink)
        }
        Spacer(Modifier.height(WindScoreSpacing.xl))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.md)
        ) {
            // 语言 / Language
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(WindScoreRounded.lg)),
                color = WindScoreColors.surfaceSoft,
                shape = RoundedCornerShape(WindScoreRounded.lg),
            ) {
                Column(modifier = Modifier.padding(WindScoreSpacing.lg), verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(WindScoreRounded.md)).background(WindScoreColors.blockLilac),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Aあ", style = MaterialTheme.typography.titleMedium, color = WindScoreColors.ink)
                        }
                        Spacer(Modifier.width(WindScoreSpacing.md))
                        Text(s.language, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W500), color = WindScoreColors.ink)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
                        AppLanguage.entries.forEach { lang ->
                            PillToggle(
                                selected = AppStrings.language == lang,
                                onClick = { onLanguageChange(lang) },
                                label = lang.nativeName,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // 界面主题 / UI Theme
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(WindScoreRounded.lg)),
                color = WindScoreColors.surfaceSoft,
                shape = RoundedCornerShape(WindScoreRounded.lg),
            ) {
                Column(modifier = Modifier.padding(WindScoreSpacing.lg), verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(WindScoreRounded.md)).background(WindScoreColors.blockMint),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("🌓", style = MaterialTheme.typography.titleMedium, color = WindScoreColors.ink)
                        }
                        Spacer(Modifier.width(WindScoreSpacing.md))
                        Text(themeLabel, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W500), color = WindScoreColors.ink)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
                        listOf(
                            ThemeMode.FOLLOW_SYSTEM to followSystemLabel,
                            ThemeMode.LIGHT to lightLabel,
                            ThemeMode.DARK to darkLabel
                        ).forEach { (mode, label) ->
                            PillToggle(
                                selected = config.themeMode == mode,
                                onClick = { onConfigChange(config.copy(themeMode = mode)) },
                                label = label,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // 默认视图 / Default View Mode
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(WindScoreRounded.lg)),
                color = WindScoreColors.surfaceSoft,
                shape = RoundedCornerShape(WindScoreRounded.lg),
            ) {
                Column(modifier = Modifier.padding(WindScoreSpacing.lg), verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(WindScoreRounded.md)).background(WindScoreColors.blockLime),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(Res.drawable.music_notes_thin), null, Modifier.size(20.dp), WindScoreColors.ink)
                        }
                        Spacer(Modifier.width(WindScoreSpacing.md))
                        Text(defaultViewLabel, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W500), color = WindScoreColors.ink)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
                        listOf(
                            ViewMode.ROLL to viewRollLabel,
                            ViewMode.SHEET_KEYBOARD to viewSheetKbLabel,
                            ViewMode.SHEET_FULL to viewSheetFullLabel
                        ).forEach { (mode, label) ->
                            PillToggle(
                                selected = config.defaultViewMode == mode,
                                onClick = { onConfigChange(config.copy(defaultViewMode = mode)) },
                                label = label,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // 默认预备拍秒数 / Default Pre-beat
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(WindScoreRounded.lg)),
                color = WindScoreColors.surfaceSoft,
                shape = RoundedCornerShape(WindScoreRounded.lg),
            ) {
                Column(modifier = Modifier.padding(WindScoreSpacing.lg), verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(WindScoreRounded.md)).background(WindScoreColors.blockCoral),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(Res.drawable.clock_thin), null, Modifier.size(20.dp), WindScoreColors.ink)
                        }
                        Spacer(Modifier.width(WindScoreSpacing.md))
                        Text(defaultPreBeatLabel, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W500), color = WindScoreColors.ink)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
                        listOf(0, 1, 2, 3, 4, 5).forEach { seconds ->
                            PillToggle(
                                selected = config.defaultCountdownSeconds == seconds,
                                onClick = { onConfigChange(config.copy(defaultCountdownSeconds = seconds)) },
                                label = "${seconds}s",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // 反馈 / Feedback
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
                        Text(s.feedback, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W500), color = WindScoreColors.ink)
                        Text(s.feedbackDesc, style = MaterialTheme.typography.bodySmall, color = WindScoreColors.ink)
                    }
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
    val s = strings()

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
                Text(s.recentScores, style = MaterialTheme.typography.titleMedium, color = WindScoreColors.ink)
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
                    Text(s.noRecent, style = MaterialTheme.typography.bodyMedium, color = WindScoreColors.ink)
                }
            } else if (showEmptyHint) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.noMatch, style = MaterialTheme.typography.bodyMedium, color = WindScoreColors.ink)
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
    val s = strings()

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
                Icon(painterResource(Res.drawable.gear_six_thin), s.settings, Modifier.size(22.dp), WindScoreColors.ink)
            }
        }
        Spacer(Modifier.height(WindScoreSpacing.lg))
        SearchField(searchQuery, { searchQuery = it }, { searchFocused = it })
        Spacer(Modifier.height(WindScoreSpacing.sm))
        Text(s.scoreLibrary, style = MaterialTheme.typography.headlineLarge, color = WindScoreColors.ink)
        Spacer(Modifier.height(WindScoreSpacing.sm))
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(WindScoreRounded.lg)),
            color = WindScoreColors.surfaceSoft,
            shape = RoundedCornerShape(WindScoreRounded.lg),
        ) {
            if (recentScores.isEmpty() && !searchFocused) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.loadFirstScore, style = MaterialTheme.typography.bodyMedium, color = WindScoreColors.ink)
                }
            } else if (showEmptyHint) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.noMatch, style = MaterialTheme.typography.bodyMedium, color = WindScoreColors.ink)
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
    val s = strings()
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) },
        placeholder = { Text(s.searchScores, style = MaterialTheme.typography.bodyMedium, color = WindScoreColors.ink.copy(alpha = 0.4f)) },
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
    val s = strings()
    val displayTitle = score.title.ifBlank { s.untitledScore }
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
                    displayTitle,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W500),
                    color = WindScoreColors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(s.noteCountAndBpm(score.notes.size, score.tempo), style = MaterialTheme.typography.bodySmall, color = WindScoreColors.ink)
            }
            // 重命名
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(WindScoreRounded.full))
                    .clickable(onClick = onRename),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(Res.drawable.pencil_simple_thin), s.rename, Modifier.size(18.dp), WindScoreColors.ink.copy(alpha = 0.6f))
            }
            Spacer(Modifier.width(WindScoreSpacing.xxs))
            // 删除
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(WindScoreRounded.full))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(Res.drawable.trash_thin), s.deleteScore, Modifier.size(18.dp), WindScoreColors.ink.copy(alpha = 0.6f))
            }
        }
    }
}
