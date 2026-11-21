package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.SettlementRecord
import com.example.data.model.SettlementTransfer
import com.example.domain.CurrencyFormatter
import com.example.ui.theme.MoneyBodyStyle
import com.example.ui.theme.MoneyTitleStyle
import com.example.ui.theme.NegativeRust
import com.example.ui.theme.PositiveSage
import com.example.ui.viewmodel.TripDetailContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripSettlementsTabContent(
    content: TripDetailContent,
    currentUserId: String,
    canEdit: Boolean,
    exportingPdf: Boolean,
    onMarkTransferPaid: (SettlementTransfer) -> Unit,
    onDeleteSettlement: (SettlementRecord) -> Unit,
    onEndTripClick: () -> Unit,
    onExportPdfOnly: () -> Unit
) {
    val feedback = LocalBilliFeedback.current
    val summary = content.summary
    val trip = content.trip
    val isPrimaryCreator = trip.canManageTripAdmin(currentUserId)
    val memberNames = remember(trip.members) {
        trip.members.associate { it.memberId to it.displayName }
    }
    val dateFmt = remember { SimpleDateFormat("MMM d • h:mm a", Locale.US) }

    // Smart Debt Simplification (Graph Reduction) toggle — ON by default
    var simplifyDebts by rememberSaveable(trip.id) { mutableStateOf(true) }
    val activeTransfers = if (simplifyDebts) summary.simplifiedTransfers else summary.rawPairwiseTransfers
    val rawCount = summary.rawPairwiseTransfers.size
    val simplifiedCount = summary.simplifiedTransfers.size
    val transfersSaved = (rawCount - simplifiedCount).coerceAtLeast(0)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 680.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- SMART DEBT SIMPLIFICATION (GRAPH REDUCTION) TOGGLE CARD ---
        item(key = "graph_reduction_toggle") {
            BilliMorphCard(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("graph_reduction_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoGraph,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Smart Debt Simplification",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (simplifyDebts) {
                                        "Graph Reduction merges chains (A→B→C becomes A→C)"
                                    } else {
                                        "Showing raw bill-by-bill pairwise balances"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = simplifyDebts,
                            onCheckedChange = {
                                simplifyDebts = it
                                feedback?.celebrateDoublePulse()
                            },
                            modifier = Modifier.testTag("simplify_debts_switch")
                        )
                    }

                    if (rawCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (simplifyDebts && transfersSaved > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            }
                        ) {
                            Text(
                                text = when {
                                    simplifyDebts && transfersSaved > 0 ->
                                        "Merged $rawCount transfers → $simplifiedCount direct ${if (simplifiedCount == 1) "payment" else "payments"} (saved $transfersSaved)"
                                    simplifyDebts ->
                                        "Optimal: $simplifiedCount minimum ${if (simplifiedCount == 1) "transfer" else "transfers"}"
                                    else ->
                                        "Raw view: $rawCount pairwise ${if (rawCount == 1) "transfer" else "transfers"}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (simplifyDebts && transfersSaved > 0) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- TRANSFERS LIST (ANIMATED MORPH BETWEEN RAW & GRAPH-REDUCED) ---
        item(key = "transfers_animated_section") {
            AnimatedContent(
                targetState = simplifyDebts to activeTransfers,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "debt_graph_morph"
            ) { (isSimplified, transfers) ->
                if (transfers.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Everyone is completely settled up — ₹0.00 remaining.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (isSimplified) "Direct Simplified Transfers" else "Raw Pairwise Debts",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        transfers.forEach { transfer ->
                            BilliMorphCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settlement_transfer_${transfer.fromMemberId}_${transfer.toMemberId}")
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = transfer.fromName,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = NegativeRust
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                                contentDescription = "pays",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = transfer.toName,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = PositiveSage
                                            )
                                        }
                                        Text(
                                            text = CurrencyFormatter.format(transfer.amountCents),
                                            style = MoneyTitleStyle,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    AnimatedVisibility(visible = canEdit) {
                                        Column {
                                            Spacer(Modifier.height(10.dp))
                                            FilledTonalButton(
                                                onClick = {
                                                    feedback?.celebrateDoublePulse()
                                                    onMarkTransferPaid(transfer)
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .billiPressScale()
                                                    .testTag("mark_paid_${transfer.fromMemberId}_${transfer.toMemberId}")
                                            ) {
                                                Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Record Payment")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- RECORDED SETTLEMENT PAYMENTS HISTORY (WITH PER-CARD SYNC BADGES) ---
        if (content.settlements.isNotEmpty()) {
            item(key = "recorded_header") {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recorded Payments (${content.settlements.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = CurrencyFormatter.format(summary.totalSettledCents),
                        style = MoneyBodyStyle.copy(fontWeight = FontWeight.Bold),
                        color = PositiveSage
                    )
                }
            }

            items(content.settlements, key = { it.id }) { record ->
                val fromName = memberNames[record.fromMemberId] ?: "Member"
                val toName = memberNames[record.toMemberId] ?: "Member"
                val isPendingSync = record.id in content.pendingSettlementIds
                BilliMorphCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recorded_settlement_${record.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPendingSync) Icons.Rounded.CloudUpload else Icons.Rounded.CheckCircle,
                            contentDescription = if (isPendingSync) "Pending cloud sync" else "Synced",
                            tint = if (isPendingSync) MaterialTheme.colorScheme.tertiary else PositiveSage,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "$fromName paid $toName",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                if (isPendingSync) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer
                                    ) {
                                        Text(
                                            text = "Queued",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            val dateStr = if (record.settledAtMillis > 0L) {
                                dateFmt.format(Date(record.settledAtMillis))
                            } else ""
                            val sub = listOf(dateStr, record.note).filter { it.isNotBlank() }.joinToString(" • ")
                            if (sub.isNotBlank()) {
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = CurrencyFormatter.format(record.amountCents),
                            style = MoneyBodyStyle.copy(fontWeight = FontWeight.Bold),
                            color = PositiveSage
                        )
                        if (canEdit) {
                            IconButton(
                                onClick = {
                                    feedback?.tick()
                                    onDeleteSettlement(record)
                                },
                                modifier = Modifier.testTag("delete_settlement_${record.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Undo settlement",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- MEMBER STANDINGS ---
        item(key = "standings_header") {
            Spacer(Modifier.height(6.dp))
            Text("Member Balances", style = MaterialTheme.typography.titleMedium)
        }

        item(key = "standings_card") {
            BilliMorphCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    summary.memberBalances.forEachIndexed { idx, mb ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mb.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Paid ${CurrencyFormatter.format(mb.totalPaidCents)} • Share ${CurrencyFormatter.format(mb.totalShareCents)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = CurrencyFormatter.format(mb.netBalanceCents, showPlusSign = true),
                                style = MoneyBodyStyle.copy(fontWeight = FontWeight.Bold),
                                color = when {
                                    mb.netBalanceCents > 0L -> PositiveSage
                                    mb.netBalanceCents < 0L -> NegativeRust
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (idx < summary.memberBalances.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }

        // --- ARCHIVAL PDF & END TRIP ---
        item(key = "archival_pdf_card") {
            Spacer(Modifier.height(6.dp))
            BilliMorphCard(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Archival Trip PDF", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "Exports trip summary, budget status, simplified settlements, recorded payments, itemized INR bills with locations & multi-payer splits, and attached receipt images into one PDF.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isPrimaryCreator) {
                        Button(
                            onClick = {
                                feedback?.tick()
                                onEndTripClick()
                            },
                            enabled = !exportingPdf,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .billiPressScale()
                                .testTag("end_trip_archive_button")
                        ) {
                            Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("End Trip & Export Archival PDF")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            feedback?.tick()
                            onExportPdfOnly()
                        },
                        enabled = !exportingPdf,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("export_pdf_snapshot_button")
                    ) {
                        if (exportingPdf) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Building PDF…")
                        } else {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (trip.isEnded()) "Download Archival PDF" else "Export Snapshot PDF")
                        }
                    }
                }
            }
        }
    }
}
