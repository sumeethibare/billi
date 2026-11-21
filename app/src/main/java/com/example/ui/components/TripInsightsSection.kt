package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.CurrencyFormatter
import com.example.ui.theme.MoneyBodyStyle
import com.example.ui.theme.MoneyTitleStyle
import com.example.ui.theme.NegativeRust
import com.example.ui.theme.PositiveSage
import com.example.ui.viewmodel.TripDetailContent

@Composable
fun TripInsightsTabContent(
    content: TripDetailContent,
    canManageBudget: Boolean,
    onEditBudgetClick: () -> Unit,
    onOpenTripWrapped: () -> Unit = {}
) {
    val feedback = LocalBilliFeedback.current
    val insights = content.insights
    val summary = content.summary

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 680.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 0. INTERACTIVE "TRIP WRAPPED" STORY LAUNCHER ---
        if (content.bills.isNotEmpty()) {
            item(key = "trip_wrapped_banner") {
                BilliStaggeredItem(index = 0) {
                    Surface(
                        onClick = {
                            feedback?.celebrateDoublePulse()
                            onOpenTripWrapped()
                        },
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .billiPressScale()
                            .testTag("open_trip_wrapped_banner")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF0E6E52), Color(0xFF5B2E91))
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color(0xFFFFE082)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Play Trip Wrapped",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "See The Ambani, Chai Sponsor & shareable story awards",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Launch Trip Wrapped",
                                            tint = Color(0xFF0E6E52)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 1. TRIP BUDGET TRACKER ---
        item(key = "budget_card") {
            BilliStaggeredItem(index = 0) {
                BilliMorphCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trip_budget_card")
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Trip Budget", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = if (insights.budgetCents > 0L) {
                                            "Target: ${CurrencyFormatter.format(insights.budgetCents)}"
                                        } else {
                                            "No budget limit set yet"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (canManageBudget) {
                                OutlinedButton(
                                    onClick = onEditBudgetClick,
                                    modifier = Modifier.testTag("edit_budget_button")
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (insights.budgetCents > 0L) "Edit" else "Set")
                                }
                            }
                        }

                        if (insights.budgetCents > 0L) {
                            Spacer(Modifier.height(14.dp))
                            val overBudget = insights.totalSpendCents > insights.budgetCents
                            val remaining = insights.budgetCents - insights.totalSpendCents
                            val animatedFill by animateFloatAsState(
                                targetValue = insights.budgetProgress.coerceIn(0f, 1f),
                                animationSpec = BilliMotion.snappySpring(),
                                label = "insights_budget_bar"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "Spent so far",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = CurrencyFormatter.format(insights.totalSpendCents),
                                        style = MoneyTitleStyle,
                                        color = if (overBudget) NegativeRust else MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (overBudget) {
                                            Icon(
                                                imageVector = Icons.Rounded.WarningAmber,
                                                contentDescription = null,
                                                tint = NegativeRust,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(3.dp))
                                        }
                                        Text(
                                            text = if (overBudget) "Exceeded by" else "Remaining",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (overBudget) NegativeRust else PositiveSage
                                        )
                                    }
                                    Text(
                                        text = CurrencyFormatter.format(kotlin.math.abs(remaining)),
                                        style = MoneyBodyStyle.copy(fontWeight = FontWeight.Bold),
                                        color = if (overBudget) NegativeRust else PositiveSage
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { animatedFill },
                                color = if (overBudget) NegativeRust else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${(insights.budgetProgress * 100).toInt()}% of trip budget used",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (canManageBudget) {
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = onEditBudgetClick,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Set INR Trip Budget")
                            }
                        }
                    }
                }
            }
        }

        // --- 2. KEY INSIGHT METRICS ---
        item(key = "insight_metrics") {
            BilliStaggeredItem(index = 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InsightMiniStatCard(
                        label = "Daily Avg",
                        value = CurrencyFormatter.formatCompact(insights.averagePerDayCents),
                        sub = "${insights.dailySpends.size} active days",
                        modifier = Modifier.weight(1f)
                    )
                    InsightMiniStatCard(
                        label = "Avg / Bill",
                        value = CurrencyFormatter.formatCompact(insights.averagePerBillCents),
                        sub = "${content.bills.size} bills",
                        modifier = Modifier.weight(1f)
                    )
                    InsightMiniStatCard(
                        label = "Top Payer",
                        value = insights.topSpenderName?.take(10) ?: "—",
                        sub = if (insights.topSpenderCents > 0L) {
                            CurrencyFormatter.formatCompact(insights.topSpenderCents)
                        } else "No spend",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (content.bills.isEmpty()) {
            item(key = "empty_insights") {
                BilliMorphCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Insights,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("No spending data yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Add expenses to see animated category breakdowns, daily spend trends, and member comparisons.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // --- 3. CATEGORY BREAKDOWN DONUT CHART ---
            item(key = "category_donut") {
                BilliStaggeredItem(index = 2) {
                    BilliMorphCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Spending by Category", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(14.dp))

                            val sweepAnim = remember(insights.categoryBreakdown) { Animatable(0f) }
                            LaunchedEffect(insights.categoryBreakdown) {
                                sweepAnim.snapTo(0f)
                                sweepAnim.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(132.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val strokeWidth = 22.dp.toPx()
                                        val diameter = size.minDimension - strokeWidth
                                        val topLeft = Offset(
                                            (size.width - diameter) / 2f,
                                            (size.height - diameter) / 2f
                                        )
                                        val arcSize = Size(diameter, diameter)
                                        drawArc(
                                            color = trackColor,
                                            startAngle = 0f,
                                            sweepAngle = 360f,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcSize,
                                            style = Stroke(width = strokeWidth)
                                        )
                                        var startAngle = -90f
                                        insights.categoryBreakdown.forEach { cat ->
                                            val sweep = (cat.percentage * 360f) * sweepAnim.value
                                            if (sweep > 0.5f) {
                                                drawArc(
                                                    color = categoryAccentColor(cat.category),
                                                    startAngle = startAngle,
                                                    sweepAngle = (sweep - 2f).coerceAtLeast(1f),
                                                    useCenter = false,
                                                    topLeft = topLeft,
                                                    size = arcSize,
                                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                                )
                                            }
                                            startAngle += sweep
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Total",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = CurrencyFormatter.formatCompact(insights.totalSpendCents),
                                            style = MoneyBodyStyle.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    insights.categoryBreakdown.forEach { cat ->
                                        val accent = categoryAccentColor(cat.category)
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(10.dp)
                                                            .clip(CircleShape)
                                                            .background(accent)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = cat.category.label,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Text(
                                                    text = "${(cat.percentage * 100).toInt()}%",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = "${CurrencyFormatter.format(cat.amountCents)} • ${cat.billCount} ${if (cat.billCount == 1) "bill" else "bills"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- 4. DAILY SPEND TREND BAR CHART ---
            if (insights.dailySpends.isNotEmpty()) {
                item(key = "daily_trend") {
                    BilliStaggeredItem(index = 3) {
                        BilliMorphCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text("Day-by-Day Spend Trend", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Chronological spending across trip days",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(14.dp))
                                val maxDayCents = insights.dailySpends.maxOf { it.amountCents }.coerceAtLeast(1L)

                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    insights.dailySpends.forEach { day ->
                                        val ratio by animateFloatAsState(
                                            targetValue = (day.amountCents.toFloat() / maxDayCents.toFloat()).coerceIn(0.05f, 1f),
                                            animationSpec = tween(550, easing = FastOutSlowInEasing),
                                            label = "day_bar_${day.dayKey}"
                                        )
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Day ${day.dayNumber} • ${day.shortLabel}",
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                                Text(
                                                    text = CurrencyFormatter.format(day.amountCents),
                                                    style = MoneyBodyStyle.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(10.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(ratio)
                                                        .height(10.dp)
                                                        .clip(RoundedCornerShape(50))
                                                        .background(MaterialTheme.colorScheme.primary)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- 5. MEMBER PAID VS SHARE COMPARISON ---
            item(key = "member_comparison") {
                BilliStaggeredItem(index = 4) {
                    BilliMorphCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Paid vs Fair Share", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            val maxMemberAmt = summary.memberBalances
                                .maxOfOrNull { maxOf(it.totalPaidCents, it.totalShareCents) }
                                ?.coerceAtLeast(1L) ?: 1L

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                summary.memberBalances.forEach { mb ->
                                    val paidFraction by animateFloatAsState(
                                        targetValue = (mb.totalPaidCents.toFloat() / maxMemberAmt).coerceIn(0f, 1f),
                                        animationSpec = tween(550),
                                        label = "paid_${mb.memberId}"
                                    )
                                    val shareFraction by animateFloatAsState(
                                        targetValue = (mb.totalShareCents.toFloat() / maxMemberAmt).coerceIn(0f, 1f),
                                        animationSpec = tween(550),
                                        label = "share_${mb.memberId}"
                                    )
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(mb.displayName, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                text = "Paid ${CurrencyFormatter.formatCompact(mb.totalPaidCents)} • Share ${CurrencyFormatter.formatCompact(mb.totalShareCents)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(Modifier.height(5.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(paidFraction)
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(PositiveSage)
                                            )
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(shareFraction)
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(MaterialTheme.colorScheme.secondary)
                                            )
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
}

@Composable
private fun InsightMiniStatCard(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    BilliMorphCard(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MoneyBodyStyle.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
