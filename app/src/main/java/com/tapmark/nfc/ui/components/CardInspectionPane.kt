package com.tapmark.nfc.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tapmark.nfc.model.ScannedCard
import com.tapmark.nfc.ui.theme.CardSurface
import com.tapmark.nfc.ui.theme.CopperSoftTint
import com.tapmark.nfc.ui.theme.CopperTerracotta
import com.tapmark.nfc.ui.theme.DividerHairline
import com.tapmark.nfc.ui.theme.MutedInk
import com.tapmark.nfc.ui.theme.PrimaryInk
import com.tapmark.nfc.ui.theme.RecessedSurface
import com.tapmark.nfc.ui.theme.SecondaryGraphite

private val SAMPLE_PREVIEW_CHIP_LABELS = listOf(
    "BPI",
    "BDO",
    "GCash",
    "Maya",
    "GoTyme",
    "Transit",
    "Tag"
)

@Composable
fun CardInspectionPane(
    card: ScannedCard,
    onUpdateLabel: (String?) -> Unit,
    onFeedbackMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNewScan: (() -> Unit)? = null,
    sampleIndicatorText: String? = null,
    onNextSample: (() -> Unit)? = null,
    sampleIndex: Int = 0,
    sampleCount: Int = 0,
    onSelectSample: ((Int) -> Unit)? = null,
    initialFlipped: Boolean = false
) {
    val context = LocalContext.current
    var revealPan by remember(card.id) { mutableStateOf(false) }
    var isCardFlipped by remember(card.id, initialFlipped) { mutableStateOf(initialFlipped) }
    var showRenameDialog by remember(card.id) { mutableStateOf(false) }

    val actionsReveal = remember(card.id) { Animatable(0f) }
    LaunchedEffect(card.id) {
        actionsReveal.snapTo(0f)
        actionsReveal.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (card.isSample && onSelectSample != null && sampleCount > 0) {
            SampleCardSwitcherRow(
                sampleIndex = sampleIndex,
                sampleCount = sampleCount,
                onSelectSample = onSelectSample
            )
        }

        PhysicalCardView(
            card = card,
            revealPan = revealPan,
            onToggleRevealPan = { revealPan = !revealPan },
            initialFlipped = initialFlipped,
            isFlipped = isCardFlipped,
            onToggleFlip = { isCardFlipped = !isCardFlipped }
        )

        if (card.emvInfo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isCardFlipped) {
                        "Card back · EMVCo CVV2 & chip security"
                    } else {
                        "Drag card to tilt · Flip for EMV chip back"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isCardFlipped) CopperSoftTint else CardSurface,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isCardFlipped) CopperTerracotta.copy(alpha = 0.35f) else DividerHairline
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { isCardFlipped = !isCardFlipped }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        CardFlipVectorIcon(
                            tint = CopperTerracotta,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Front / Back",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isCardFlipped) CopperTerracotta else PrimaryInk
                        )
                    }
                }
            }
        }

        // Compact tactile action row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val p = actionsReveal.value
                    alpha = p
                    translationY = (1f - p) * 10.dp.toPx()
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { showRenameDialog = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DividerHairline),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CardSurface,
                    contentColor = PrimaryInk
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = SecondaryGraphite
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Label",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = {
                    copyTextToClipboard(
                        context = context,
                        label = card.customLabel ?: card.cardTitle,
                        text = buildCardSummaryText(card)
                    )
                    onFeedbackMessage("Card summary copied")
                },
                modifier = Modifier.weight(1.25f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DividerHairline),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CardSurface,
                    contentColor = PrimaryInk
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = SecondaryGraphite
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Copy Summary",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onNewScan != null) {
                Button(
                    onClick = onNewScan,
                    modifier = Modifier.weight(1.1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CopperTerracotta,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "New Scan",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (card.isSample && onNextSample != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sampleIndicatorText ?: "Sample preview card",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryGraphite
                )
                TextButton(
                    onClick = onNextSample,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Next sample",
                        style = MaterialTheme.typography.labelLarge,
                        color = CopperTerracotta
                    )
                }
            }
        }

        CardDetailLedger(
            card = card,
            revealPan = revealPan,
            onFeedbackMessage = onFeedbackMessage
        )
    }

    if (showRenameDialog) {
        RenameLabelDialog(
            initialLabel = card.customLabel.orEmpty(),
            cardTitle = card.cardTitle,
            onDismiss = { showRenameDialog = false },
            onSave = { newLabel ->
                showRenameDialog = false
                onUpdateLabel(newLabel)
            }
        )
    }
}

@Composable
private fun SampleCardSwitcherRow(
    sampleIndex: Int,
    sampleCount: Int,
    onSelectSample: (Int) -> Unit
) {
    val count = sampleCount.coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (idx in 0 until count) {
            val selected = idx == sampleIndex
            val label = SAMPLE_PREVIEW_CHIP_LABELS.getOrElse(idx) { "Sample ${idx + 1}" }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (selected) CopperTerracotta else CardSurface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) CopperTerracotta else DividerHairline
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { onSelectSample(idx) }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (selected) Color.White else PrimaryInk,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun RenameLabelDialog(
    initialLabel: String,
    cardTitle: String,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit
) {
    var text by remember(initialLabel) { mutableStateOf(initialLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = PrimaryInk,
        textContentColor = SecondaryGraphite,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                text = "Card Label",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Add a personal nickname for $cardTitle. Leave blank to clear.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryGraphite
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 36) text = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "e.g. Office Badge, Commute Pass",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryGraphite.copy(alpha = 0.6f)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CopperTerracotta,
                        unfocusedBorderColor = DividerHairline,
                        focusedContainerColor = RecessedSurface.copy(alpha = 0.45f),
                        unfocusedContainerColor = RecessedSurface.copy(alpha = 0.45f),
                        cursorColor = CopperTerracotta,
                        focusedTextColor = PrimaryInk,
                        unfocusedTextColor = PrimaryInk
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = text.trim()
                    onSave(trimmed.ifEmpty { null })
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CopperTerracotta,
                    contentColor = Color.White
                )
            ) {
                Text("Save", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (initialLabel.isNotBlank()) {
                    TextButton(
                        onClick = { onSave(null) }
                    ) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelLarge,
                            color = SecondaryGraphite
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = SecondaryGraphite
                    )
                }
            }
        }
    )
}
