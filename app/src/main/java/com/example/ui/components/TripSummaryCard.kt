package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.SupervisorAccount
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Trip
import com.example.data.model.TripLedgerSummary
import com.example.domain.CurrencyFormatter
import com.example.ui.theme.MoneyBodyStyle
import com.example.ui.theme.MoneyDisplayStyle
import com.example.ui.theme.NegativeRust
import com.example.ui.theme.NegativeRustBg
import com.example.ui.theme.PositiveSage
import com.example.ui.theme.PositiveSageBg
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripSummaryCard(
    trip: Trip,
    summary: TripLedgerSummary,
    currentUserId: String,
    onSelectMyMember: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isCreator = trip.isCreator(currentUserId)
    val isCoHost = trip.isCoHost(currentUserId)
    val isEnded = trip.isEnded()
    val userBalance = summary.currentUserBalance
    var showMemberSwitcher by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(BilliMotion.snappySpring())
            .testTag("trip_summary_card"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoleLockBadge(
                    isCreator = isCreator,
                    isCoHost = isCoHost,
                    isEnded = isEnded
                )

                Text(
                    text = "${summary.billCount} ${if (summary.billCount == 1) "bill" else "bills"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(14.dp))

            if (userBalance != null && !showMemberSwitcher) {
                val net = userBalance.netBalanceCents
                val (statusLabel, badgeBg, badgeFg, heroColor) = when {
                    net > 0L -> Quad(
                        "You are owed",
                        PositiveSageBg,
                        PositiveSage,
                        PositiveSage
                    )
                    net < 0L -> Quad(
                        "You owe",
                        NegativeRustBg,
                        NegativeRust,
                        NegativeRust
                    )
                    else -> Quad(
                        "All settled up",
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        MaterialTheme.colorScheme.primary
                    )
                }

                val animatedHeroColor by animateColorAsState(
                    targetValue = heroColor,
                    animationSpec = tween(280),
                    label = "hero_balance_color"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = badgeBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (net == 0L) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = badgeFg,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = "$statusLabel • ${userBalance.displayName}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = badgeFg
                            )
                        }
                    }

                    if (!isCreator) {
                        TextButton(
                            onClick = { showMemberSwitcher = true },
                            modifier = Modifier.testTag("switch_linked_member_button")
                        ) {
                            Text("Switch", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                AnimatedContent(
                    targetState = net,
                    transitionSpec = {
                        (fadeIn(tween(240)) + slideInVertically { it / 3 }) togetherWith
                            (fadeOut(tween(180)) + slideOutVertically { -it / 3 })
                    },
                    label = "hero_balance_anim"
                ) { animatedNet ->
                    Text(
                        text = CurrencyFormatter.format(abs(animatedNet)),
                        style = MoneyDisplayStyle,
                        color = animatedHeroColor,
                        modifier = Modifier.testTag("summary_user_balance_amount")
                    )
                }

                AnimatedVisibility(visible = summary.currentUserTransfers.isNotEmpty()) {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        val first = summary.currentUserTransfers.firstOrNull()
                        if (first != null) {
                            val transferHint = if (first.fromMemberId == userBalance.memberId) {
                                "Next transfer: Pay ${first.toName} ${CurrencyFormatter.format(first.amountCents)}"
                            } else {
                                "Next transfer: Receive ${CurrencyFormatter.format(first.amountCents)} from ${first.fromName}"
                            }
                            val extraCount = summary.currentUserTransfers.size - 1
                            Text(
                                text = if (extraCount > 0) "$transferHint (+$extraCount more)" else transferHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (trip.budgetCents > 0L) {
                    Spacer(Modifier.height(12.dp))
                    val rawRatio = (summary.totalTripSpendCents.toFloat() / trip.budgetCents.toFloat())
                    val animatedProgress by animateFloatAsState(
                        targetValue = rawRatio.coerceIn(0f, 1f),
                        animationSpec = BilliMotion.snappySpring(),
                        label = "summary_budget_progress"
                    )
                    val isOver = summary.totalTripSpendCents > trip.budgetCents
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isOver) "Over budget" else "Budget used",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOver) NegativeRust else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(rawRatio * 100).toInt()}% of ${CurrencyFormatter.formatCompact(trip.budgetCents)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isOver) NegativeRust else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        color = if (isOver) NegativeRust else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SummaryMetricColumn(
                        label = "You paid",
                        value = CurrencyFormatter.format(userBalance.totalPaidCents)
                    )
                    SummaryMetricColumn(
                        label = "Your share",
                        value = CurrencyFormatter.format(userBalance.totalShareCents)
                    )
                    SummaryMetricColumn(
                        label = "Trip total",
                        value = CurrencyFormatter.format(summary.totalTripSpendCents),
                        alignment = Alignment.End
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.PersonSearch,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Who are you on this trip?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Tap your name to calculate what you owe or are owed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    trip.members.forEach { member ->
                        AssistChip(
                            onClick = {
                                onSelectMyMember(member.memberId)
                                showMemberSwitcher = false
                            },
                            label = { Text(member.displayName) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.testTag("claim_member_${member.memberId}")
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Trip Total",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.format(summary.totalTripSpendCents),
                        style = MoneyBodyStyle.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricColumn(
    label: String,
    value: String,
    alignment: Alignment.Horizontal = Alignment.Start
) {
    Column(horizontalAlignment = alignment) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MoneyBodyStyle.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun RoleLockBadge(
    isCreator: Boolean,
    isEnded: Boolean,
    modifier: Modifier = Modifier,
    isCoHost: Boolean = false
) {
    val (icon, label, bg, fg) = when {
        isEnded -> Quad(
            Icons.Rounded.Archive,
            "Archived • Read-Only",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        isCreator -> Quad(
            Icons.Rounded.VerifiedUser,
            "Creator • Admin",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        isCoHost -> Quad(
            Icons.Rounded.SupervisorAccount,
            "Co-Host • Can Edit",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        else -> Quad(
            Icons.Rounded.Lock,
            "View-Only • Locked",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
    }

    val animatedBg by animateColorAsState(targetValue = bg, animationSpec = tween(250), label = "role_bg")
    val animatedFg by animateColorAsState(targetValue = fg, animationSpec = tween(250), label = "role_fg")

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(animatedBg)
            .animateContentSize(BilliMotion.snappySpring())
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("role_lock_badge")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = animatedFg,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = animatedFg
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
