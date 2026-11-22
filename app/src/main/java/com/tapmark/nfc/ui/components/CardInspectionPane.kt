package com.tapmark.nfc.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tapmark.nfc.model.ScannedCard
import com.tapmark.nfc.ui.theme.CardSurface
import com.tapmark.nfc.ui.theme.CopperTerracotta
import com.tapmark.nfc.ui.theme.DividerHairline
import com.tapmark.nfc.ui.theme.PrimaryInk
import com.tapmark.nfc.ui.theme.RecessedSurface
import com.tapmark.nfc.ui.theme.SecondaryGraphite

@Composable
fun CardInspectionPane(
    card: ScannedCard,
    onUpdateLabel: (String?) -> Unit,
    onFeedbackMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNewScan: (() -> Unit)? = null,
    sampleIndicatorText: String? = null,
    onNextSample: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var revealPan by remember(card.id) { mutableStateOf(false) }
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PhysicalCardView(
            card = card,
            revealPan = revealPan,
            onToggleRevealPan = { revealPan = !revealPan }
        )

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
