package com.tapmark.nfc.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tapmark.nfc.model.CardVisualStyle
import com.tapmark.nfc.model.ScannedCard
import com.tapmark.nfc.ui.components.CardInspectionPane
import com.tapmark.nfc.ui.components.RenameLabelDialog
import com.tapmark.nfc.ui.components.formatRelativeTimestamp
import com.tapmark.nfc.ui.components.formatShortUid
import com.tapmark.nfc.ui.components.toPalette
import com.tapmark.nfc.ui.theme.CardSurface
import com.tapmark.nfc.ui.theme.CopperSoftTint
import com.tapmark.nfc.ui.theme.CopperTerracotta
import com.tapmark.nfc.ui.theme.DividerHairline
import com.tapmark.nfc.ui.theme.MutedInk
import com.tapmark.nfc.ui.theme.PrimaryInk
import com.tapmark.nfc.ui.theme.RecessedSurface
import com.tapmark.nfc.ui.theme.SecondaryGraphite
import com.tapmark.nfc.ui.theme.StatusCrimson
import com.tapmark.nfc.ui.theme.TapmarkMonoCaption
import com.tapmark.nfc.ui.theme.TapmarkSectionOverline

@Composable
fun HistoryScreen(
    cards: List<ScannedCard>,
    onUpdateLabel: (String, String?) -> Unit,
    onDeleteCard: (String) -> Unit,
    onClearAll: () -> Unit,
    onGoToScanner: () -> Unit,
    onPreviewSample: () -> Unit,
    onFeedbackMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCardId by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<ScannedCard?>(null) }
    var deleteTarget by remember { mutableStateOf<ScannedCard?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    val selectedCard = cards.firstOrNull { it.id == selectedCardId }

    BackHandler(enabled = selectedCard != null) {
        selectedCardId = null
    }

    AnimatedContent(
        targetState = selectedCard,
        transitionSpec = {
            if (targetState != null) {
                (fadeIn(tween(240)) + slideInHorizontally(tween(260)) { it / 5 })
                    .togetherWith(
                        fadeOut(tween(180)) + slideOutHorizontally(tween(240)) { -it / 6 }
                    )
            } else {
                (fadeIn(tween(240)) + slideInHorizontally(tween(260)) { -it / 6 })
                    .togetherWith(
                        fadeOut(tween(180)) + slideOutHorizontally(tween(240)) { it / 5 }
                    )
            }
        },
        contentKey = { it?.id ?: "history_list" },
        label = "history_detail_transition",
        modifier = modifier.fillMaxSize()
    ) { card ->
        if (card == null) {
            HistoryListContent(
                cards = cards,
                onSelectCard = { selectedCardId = it.id },
                onRequestRename = { renameTarget = it },
                onRequestDelete = { deleteTarget = it },
                onRequestClearAll = { showClearAllDialog = true },
                onGoToScanner = onGoToScanner,
                onPreviewSample = onPreviewSample
            )
        } else {
            HistoryDetailContent(
                card = card,
                onBack = { selectedCardId = null },
                onRequestDelete = { deleteTarget = card },
                onUpdateLabel = { newLabel -> onUpdateLabel(card.id, newLabel) },
                onFeedbackMessage = onFeedbackMessage
            )
        }
    }

    renameTarget?.let { target ->
        RenameLabelDialog(
            initialLabel = target.customLabel.orEmpty(),
            cardTitle = target.cardTitle,
            onDismiss = { renameTarget = null },
            onSave = { newLabel ->
                renameTarget = null
                onUpdateLabel(target.id, newLabel)
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = CardSurface,
            titleContentColor = PrimaryInk,
            textContentColor = SecondaryGraphite,
            shape = RoundedCornerShape(18.dp),
            title = {
                Text("Remove Saved Card?", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                val displayName = target.customLabel?.takeIf { it.isNotBlank() }
                    ?: target.emvInfo?.bankName?.takeIf { it.isNotBlank() }
                    ?: target.cardTitle
                Text(
                    text = "\"$displayName\" will be removed from saved scan history.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idToDelete = target.id
                        deleteTarget = null
                        if (selectedCardId == idToDelete) {
                            selectedCardId = null
                        }
                        onDeleteCard(idToDelete)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusCrimson,
                        contentColor = Color.White
                    )
                ) {
                    Text("Delete", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge, color = SecondaryGraphite)
                }
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            containerColor = CardSurface,
            titleContentColor = PrimaryInk,
            textContentColor = SecondaryGraphite,
            shape = RoundedCornerShape(18.dp),
            title = {
                Text("Clear Saved History?", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text(
                    text = "All ${cards.size} saved card records will be permanently removed from this device.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllDialog = false
                        selectedCardId = null
                        onClearAll()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusCrimson,
                        contentColor = Color.White
                    )
                ) {
                    Text("Clear All", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge, color = SecondaryGraphite)
                }
            }
        )
    }
}

@Composable
private fun HistoryListContent(
    cards: List<ScannedCard>,
    onSelectCard: (ScannedCard) -> Unit,
    onRequestRename: (ScannedCard) -> Unit,
    onRequestDelete: (ScannedCard) -> Unit,
    onRequestClearAll: () -> Unit,
    onGoToScanner: () -> Unit,
    onPreviewSample: () -> Unit
) {
    if (cards.isEmpty()) {
        HistoryEmptyState(
            onGoToScanner = onGoToScanner,
            onPreviewSample = onPreviewSample
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 8.dp,
            bottom = 122.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "history_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SAVED CARDS",
                        style = TapmarkSectionOverline,
                        color = SecondaryGraphite
                    )
                    Text(
                        text = "${cards.size} ${if (cards.size == 1) "entry" else "entries"} stored locally",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedInk
                    )
                }

                TextButton(
                    onClick = onRequestClearAll,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Clear All",
                        style = MaterialTheme.typography.labelLarge,
                        color = StatusCrimson
                    )
                }
            }
        }

        items(
            items = cards,
            key = { it.id }
        ) { card ->
            HistoryCardItem(
                card = card,
                onClick = { onSelectCard(card) },
                onRename = { onRequestRename(card) },
                onDelete = { onRequestDelete(card) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
private fun HistoryCardItem(
    card: ScannedCard,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bankOrCardTitle = card.emvInfo?.bankName?.takeIf { it.isNotBlank() } ?: card.cardTitle
    val primaryTitle = card.customLabel?.takeIf { it.isNotBlank() } ?: bankOrCardTitle
    val subtitle = buildString {
        if (!card.customLabel.isNullOrBlank()) {
            append(bankOrCardTitle)
            append(" · ")
        }
        val emv = card.emvInfo
        if (emv != null) {
            val typeParts = listOfNotNull(
                emv.network.takeIf { it.isNotBlank() },
                emv.cardType?.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (typeParts.isNotBlank()) {
                append(typeParts)
            } else {
                append(card.chipGuess)
            }
            if (emv.transactionLogs.isNotEmpty()) {
                append(" · ${emv.transactionLogs.size} tx")
            }
        } else {
            append(card.chipGuess)
        }
    }

    val identifierText = card.emvInfo?.panMasked?.let { masked ->
        val digits = masked.takeLast(4)
        "•••• $digits"
    } ?: formatShortUid(card.uidHex)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, DividerHairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniCardSwatch(visualStyle = card.visualStyle)

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = primaryTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryGraphite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = identifierText,
                        style = TapmarkMonoCaption,
                        color = PrimaryInk
                    )
                    Text(
                        text = "·",
                        style = TapmarkMonoCaption,
                        color = MutedInk
                    )
                    Text(
                        text = formatRelativeTimestamp(card.scannedAtMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedInk
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onRename,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Rename card",
                        tint = SecondaryGraphite,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete card",
                        tint = MutedInk,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniCardSwatch(
    visualStyle: CardVisualStyle,
    modifier: Modifier = Modifier
) {
    val palette = visualStyle.toPalette()

    Box(
        modifier = modifier
            .size(width = 48.dp, height = 32.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(palette.baseColor)
            .border(1.dp, palette.borderColor, RoundedCornerShape(7.dp))
            .padding(6.dp)
    ) {
        // Tiny metallic chip detail
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(width = 9.dp, height = 7.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(palette.chipGoldTint)
        )
    }
}

@Composable
private fun HistoryDetailContent(
    card: ScannedCard,
    onBack: () -> Unit,
    onRequestDelete: () -> Unit,
    onUpdateLabel: (String?) -> Unit,
    onFeedbackMessage: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 122.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back to saved cards",
                    tint = PrimaryInk,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Saved Cards",
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryInk
                )
            }

            TextButton(
                onClick = onRequestDelete,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = StatusCrimson,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Delete",
                    style = MaterialTheme.typography.labelLarge,
                    color = StatusCrimson
                )
            }
        }

        CardInspectionPane(
            card = card,
            onUpdateLabel = onUpdateLabel,
            onFeedbackMessage = onFeedbackMessage
        )
    }
}

@Composable
private fun HistoryEmptyState(
    onGoToScanner: () -> Unit,
    onPreviewSample: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .padding(bottom = 122.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, DividerHairline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EmptyArchivalCardEmblem()

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "No saved scans yet",
                    style = MaterialTheme.typography.titleLarge,
                    color = PrimaryInk,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Contactless cards and NFC tags you tap are archived here automatically for offline inspection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryGraphite,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onGoToScanner,
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CopperTerracotta,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("Open Scanner", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = onPreviewSample,
                        shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(1.dp, DividerHairline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = RecessedSurface,
                            contentColor = PrimaryInk
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text("Sample Card", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyArchivalCardEmblem() {
    Canvas(modifier = Modifier.size(width = 88.dp, height = 56.dp)) {
        val corner = 9.dp.toPx()
        drawRoundRect(
            color = RecessedSurface,
            cornerRadius = CornerRadius(corner, corner)
        )
        drawRoundRect(
            color = DividerHairline,
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = 1.2.dp.toPx())
        )

        val inset = 7.dp.toPx()
        drawRoundRect(
            color = CopperSoftTint,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2f, size.height - inset * 2f),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
        )
        drawRoundRect(
            color = CopperTerracotta.copy(alpha = 0.72f),
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2f, size.height - inset * 2f),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
            style = Stroke(width = 1.3.dp.toPx())
        )

        // Small chip rectangle inside emblem
        drawRoundRect(
            color = CopperTerracotta,
            topLeft = Offset(size.width * 0.20f, size.height * 0.36f),
            size = Size(13.dp.toPx(), 10.dp.toPx()),
            cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
        )
    }
}
