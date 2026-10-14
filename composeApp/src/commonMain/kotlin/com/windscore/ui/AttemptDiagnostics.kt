package com.windscore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.windscore.core.practice.PracticeAttempt
import com.windscore.core.collection.AttemptExport
import com.windscore.ui.i18n.strings
import com.windscore.ui.components.CircularIconButton
import com.windscore.ui.theme.windScoreColors
import com.windscore.ui.theme.WindScoreRounded
import com.windscore.ui.theme.WindScoreSpacing
import org.jetbrains.compose.resources.painterResource
import windscore.composeapp.generated.resources.Res
import windscore.composeapp.generated.resources.arrow_left_thin

@Composable
fun AttemptDiagnosticsContent(
    scoreId: String,
    loadAttempts: suspend (String) -> List<PracticeAttempt>,
    onDismiss: () -> Unit,
    appVersion: String,
    onExportBusyChange: (Boolean) -> Unit,
) {
    val s = strings()
    var attempts by remember(scoreId) { mutableStateOf<List<PracticeAttempt>?>(null) }
    var loadError by remember(scoreId) { mutableStateOf(false) }
    var selectedIds by remember(scoreId) { mutableStateOf<Set<String>>(emptySet()) }
    var includeRaw by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(scoreId) {
        attempts = null
        loadError = false
        try { attempts = loadAttempts(scoreId) } catch (_: Exception) { loadError = true }
    }
    val chosen = attempts.orEmpty().filter { it.attemptId in selectedIds }
    // One exact byte snapshot is used for both preview and save. Selection/options are locked while the
    // native chooser is open, so exported data cannot silently drift away from the reviewed preview.
    val preview = remember(chosen, includeRaw, appVersion) {
        if (chosen.isEmpty()) "" else AttemptExport.serialize(chosen, includeRaw, appVersion)
    }
    val previewBytes = remember(preview) { preview.encodeToByteArray() }

    Column(Modifier.fillMaxSize().background(windScoreColors().canvas)) {
        Row(Modifier.fillMaxWidth().padding(WindScoreSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            CircularIconButton(onClick = { if (!saving) onDismiss() }, enabled = !saving) {
                Icon(painterResource(Res.drawable.arrow_left_thin), s.back, Modifier.size(20.dp))
            }
            Spacer(Modifier.size(WindScoreSpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(s.collectionTitle, style = MaterialTheme.typography.headlineSmall, color = windScoreColors().ink)
                Text(s.collectionPrivateRecording, style = MaterialTheme.typography.bodySmall, color = windScoreColors().ink.copy(alpha = .65f))
            }
        }
        when {
            attempts == null && !loadError -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            loadError -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(s.collectionLoadError, color = windScoreColors().ink)
            }
            attempts.orEmpty().isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(s.collectionEmpty, color = windScoreColors().ink.copy(alpha = .65f))
            }
            else -> {
                val available = attempts.orEmpty()
                Text(s.collectionSelect, Modifier.padding(horizontal = WindScoreSpacing.md), style = MaterialTheme.typography.labelLarge, color = windScoreColors().ink)
                LazyColumn(
                    Modifier.fillMaxWidth().weight(.8f),
                    contentPadding = PaddingValues(horizontal = WindScoreSpacing.md, vertical = WindScoreSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs),
                ) {
                    items(available, key = { it.attemptId }) { attempt ->
                        val c = attempt.context
                        Surface(color = windScoreColors().surfaceSoft, shape = RoundedCornerShape(WindScoreRounded.md)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = WindScoreSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = attempt.attemptId in selectedIds,
                                    enabled = !saving,
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked) selectedIds + attempt.attemptId else selectedIds - attempt.attemptId
                                        status = null
                                    },
                                )
                                Column(Modifier.weight(1f).padding(vertical = WindScoreSpacing.xs)) {
                                    Text("${c.attemptType} · ${c.practiceMode} · ${attempt.endReason ?: "ACTIVE"}", color = windScoreColors().ink, style = MaterialTheme.typography.bodyMedium)
                                    Text("${attempt.events.size} events · ${attempt.durationMicros?.let { "${it / 1_000_000}s" } ?: s.collectionDurationUnknown} · tick ${attempt.context.startTick ?: "?"} → ${attempt.reachedTick ?: "?"}", color = windScoreColors().ink.copy(alpha = .62f), style = MaterialTheme.typography.bodySmall)
                                    Text("${s.collectionLocalId}: …${attempt.attemptId.takeLast(8)} · ${s.collectionSession}: …${attempt.sessionId.takeLast(6)}", color = windScoreColors().ink.copy(alpha = .45f), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().weight(1.2f).padding(horizontal = WindScoreSpacing.md).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(includeRaw, enabled = !saving, onCheckedChange = { includeRaw = it; status = null })
                        Text(s.collectionRawOption, color = windScoreColors().ink, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(if (includeRaw) s.collectionRawWarning else s.collectionSummaryWarning, color = windScoreColors().ink.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall)
                    Text(s.collectionPreview, color = windScoreColors().ink, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        if (chosen.isEmpty()) s.collectionNothingSelected else preview,
                        modifier = Modifier.fillMaxWidth().background(windScoreColors().surfaceSoft, RoundedCornerShape(WindScoreRounded.sm)).padding(WindScoreSpacing.sm),
                        color = windScoreColors().ink.copy(alpha = .8f), style = MaterialTheme.typography.labelSmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm)) {
                        AttemptExportSaveButton(
                            bytes = previewBytes,
                            filename = AttemptExport.suggestedFilename(),
                            label = if (saving) s.collectionSaving else s.collectionSave,
                            enabled = chosen.isNotEmpty() && !saving,
                            onSavingChanged = { saving = it; onExportBusyChange(it) },
                            onResult = { result -> status = when (result) {
                                AttemptExportSaveResult.SAVED -> s.collectionSaved
                                AttemptExportSaveResult.CANCELLED -> s.collectionCancelled
                                AttemptExportSaveResult.FAILED -> s.collectionSaveError
                            } },
                        )
                        status?.let { Text(it, color = windScoreColors().ink, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}
