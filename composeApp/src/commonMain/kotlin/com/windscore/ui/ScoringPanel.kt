package com.windscore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.windscore.ViewMode
import com.windscore.core.engine.ScoreSession
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.ScoreRecord
import com.windscore.ui.components.CircularIconButton
import com.windscore.ui.components.PillButton
import com.windscore.ui.components.PillButtonStyle
import com.windscore.ui.components.PillToggle
import com.windscore.ui.i18n.strings
import com.windscore.ui.theme.windScoreColors
import com.windscore.ui.theme.WindScoreRounded
import com.windscore.ui.theme.WindScoreSpacing
import org.jetbrains.compose.resources.painterResource
import windscore.composeapp.generated.resources.Res
import windscore.composeapp.generated.resources.book_open_text_thin
import windscore.composeapp.generated.resources.clock_thin
import windscore.composeapp.generated.resources.music_notes_thin
import windscore.composeapp.generated.resources.pencil_simple_thin
import windscore.composeapp.generated.resources.piano_keys_thin


/**
 * 评分设置面板：用户选择评分范围（单/双手）与评分视图，点击"开始评分"进入评分。
 * 视图选择直接写入全局 [viewMode]（主舞台实时预览），手别选择为本地态、仅在开始时应用。
 */
@Composable
fun ScoringSetupPanel(
    scoreHasSheet: Boolean,
    viewMode: ViewMode,
    onViewChange: (ViewMode) -> Unit,
    onStartScoring: (PracticeMode) -> Unit,
    history: List<ScoreRecord>,
    onShowHistory: () -> Unit,
    onShowAttemptDiagnostics: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var scoringHand by remember { mutableStateOf(PracticeMode.BOTH) }
    val s = strings()

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(WindScoreRounded.lg))
            .background(windScoreColors().canvas)
            .border(1.dp, windScoreColors().hairline, RoundedCornerShape(WindScoreRounded.lg))
            .padding(WindScoreSpacing.md),
        verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
            Icon(
                painter = painterResource(Res.drawable.pencil_simple_thin),
                contentDescription = s.scoring,
                modifier = Modifier.size(18.dp),
                tint = windScoreColors().ink,
            )
            Text(
                s.scoringSettings,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                color = windScoreColors().ink,
            )
        }

        // 评分范围
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, windScoreColors().hairlineSoft, RoundedCornerShape(WindScoreRounded.md))
                .padding(WindScoreSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs),
        ) {
            Text(s.scoringRange, style = MaterialTheme.typography.labelSmall, color = windScoreColors().ink.copy(alpha = 0.5f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs),
            ) {
                PillToggle(scoringHand == PracticeMode.BOTH, { scoringHand = PracticeMode.BOTH }, s.bothHands, modifier = Modifier.weight(1f))
                PillToggle(scoringHand == PracticeMode.RIGHT_ONLY, { scoringHand = PracticeMode.RIGHT_ONLY }, s.rightHand, modifier = Modifier.weight(1f))
                PillToggle(scoringHand == PracticeMode.LEFT_ONLY, { scoringHand = PracticeMode.LEFT_ONLY }, s.leftHand, modifier = Modifier.weight(1f))
            }
            Text(
                s.scoringRangeHint,
                style = MaterialTheme.typography.bodySmall,
                color = windScoreColors().ink.copy(alpha = 0.5f),
            )
        }

        // 评分视图
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, windScoreColors().hairlineSoft, RoundedCornerShape(WindScoreRounded.md))
                .padding(WindScoreSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs),
        ) {
            Text(s.scoringView, style = MaterialTheme.typography.labelSmall, color = windScoreColors().ink.copy(alpha = 0.5f))
            Row(horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
                ViewModeButton(ViewMode.ROLL, viewMode == ViewMode.ROLL) { onViewChange(ViewMode.ROLL) }
                if (scoreHasSheet) {
                    ViewModeButton(ViewMode.SHEET_KEYBOARD, viewMode == ViewMode.SHEET_KEYBOARD) { onViewChange(ViewMode.SHEET_KEYBOARD) }
                    ViewModeButton(ViewMode.SHEET_FULL, viewMode == ViewMode.SHEET_FULL) { onViewChange(ViewMode.SHEET_FULL) }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        PillButton(
            style = PillButtonStyle.PRIMARY,
            onClick = { onStartScoring(scoringHand) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(s.startScoring, fontWeight = FontWeight.W600)
        }

        Spacer(Modifier.height(WindScoreSpacing.sm))

        // 评分历史入口
        Surface(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(WindScoreRounded.md)).clickable(onClick = onShowHistory),
            color = windScoreColors().surfaceSoft,
            shape = RoundedCornerShape(WindScoreRounded.md),
        ) {
            Row(
                modifier = Modifier.padding(WindScoreSpacing.md).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.clock_thin),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = windScoreColors().ink.copy(alpha = 0.6f),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        s.scoreHistory,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W500),
                        color = windScoreColors().ink,
                    )
                    val subtitle = if (history.isEmpty()) {
                        s.noHistory
                    } else {
                        val best = history.maxOf { it.percent }
                        "${s.bestLabel} $best% · ${s.timesLabel(history.size)}"
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = windScoreColors().ink.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(WindScoreRounded.md)).clickable(onClick = onShowAttemptDiagnostics),
            color = windScoreColors().surfaceSoft,
            shape = RoundedCornerShape(WindScoreRounded.md),
        ) {
            Row(
                modifier = Modifier.padding(WindScoreSpacing.sm).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.music_notes_thin),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = windScoreColors().ink.copy(alpha = 0.4f),
                )
                Text(
                    s.collectionTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = windScoreColors().ink.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/**
 * 评分进行中面板：仅提示"请开始演奏"+ 中性进度，加"退出评分"。
 * 过程绝不展示打叉/飘红/扣分（文档 4.3）。
 */
@Composable
fun ScoringActivePanel(
    session: ScoreSession,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val ss = session.state.collectAsState().value
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(WindScoreRounded.lg))
            .background(windScoreColors().canvas)
            .border(1.dp, windScoreColors().hairline, RoundedCornerShape(WindScoreRounded.lg))
            .padding(WindScoreSpacing.md),
        verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
            Icon(
                painter = painterResource(Res.drawable.music_notes_thin),
                contentDescription = s.scoringActiveLabel,
                modifier = Modifier.size(18.dp),
                tint = windScoreColors().ink,
            )
            Text(
                s.scoringActive,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                color = windScoreColors().ink,
            )
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    s.pleaseStart,
                    style = MaterialTheme.typography.headlineSmall,
                    color = windScoreColors().ink,
                )
                Spacer(Modifier.height(WindScoreSpacing.sm))
                Text(
                    s.scoringProgress(ss.progress, ss.groupCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = windScoreColors().ink.copy(alpha = 0.5f),
                )
            }
        }

        PillButton(
            style = PillButtonStyle.SECONDARY,
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(s.exitScoring, fontWeight = FontWeight.W600)
        }
    }
}

/** 评分完成时侧栏的占位（结果详情由主舞台 [ScoringResultOverlay] 展示）。 */
@Composable
fun ScoringDonePanel(modifier: Modifier = Modifier) {
    val s = strings()
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(WindScoreRounded.lg))
            .background(windScoreColors().canvas)
            .border(1.dp, windScoreColors().hairline, RoundedCornerShape(WindScoreRounded.lg))
            .padding(WindScoreSpacing.md),
        verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
            Icon(
                painter = painterResource(Res.drawable.pencil_simple_thin),
                contentDescription = s.scoringDone,
                modifier = Modifier.size(18.dp),
                tint = windScoreColors().ink.copy(alpha = 0.5f),
            )
            Text(
                s.scoringDone,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                color = windScoreColors().ink,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                s.scoringDoneHint,
                style = MaterialTheme.typography.bodyMedium,
                color = windScoreColors().ink.copy(alpha = 0.5f),
            )
        }
    }
}

/**
 * 评分结算覆盖层：覆盖主舞台，居中显示最终准度分数 + "回到练习"。
 * 不含过程性惩罚反馈；与练习结算页风格一致（blockCream + pastel 分数色）。
 */
@Composable
fun ScoringResultOverlay(
    session: ScoreSession,
    onReturnToPractice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val percent = session.scorePercent()
    val clean = session.cleanHitCount()
    val total = session.totalActiveNotes()
    val wrong = session.wrongPressCount()

    Surface(
        modifier = modifier.padding(WindScoreSpacing.lg),
        color = windScoreColors().blockCream,
        shape = RoundedCornerShape(WindScoreRounded.lg),
    ) {
        Column(
            modifier = Modifier.padding(WindScoreSpacing.xxl).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                s.performanceComplete,
                style = MaterialTheme.typography.headlineLarge,
                color = windScoreColors().ink,
            )
            Spacer(Modifier.height(WindScoreSpacing.md))
            Text(
                "$percent%",
                fontSize = 64.sp,
                fontWeight = FontWeight.W300,
                color = if (percent >= 80) windScoreColors().scoreClean else windScoreColors().scoreStuck,
                letterSpacing = (-0.96).sp,
            )
            Text(
                s.cleanHitsLine(clean, total, wrong),
                style = MaterialTheme.typography.bodyMedium,
                color = windScoreColors().ink.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(WindScoreSpacing.xl))
            PillButton(style = PillButtonStyle.PRIMARY, onClick = onReturnToPractice) {
                Text(s.returnToPractice, fontWeight = FontWeight.W600)
            }
        }
    }
}
