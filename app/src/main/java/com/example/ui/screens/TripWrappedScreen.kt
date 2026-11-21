package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.model.Bill
import com.example.data.model.SettlementRecord
import com.example.data.model.Trip
import com.example.domain.TripWrappedCardData
import com.example.domain.TripWrappedEngine
import com.example.ui.components.LocalBilliFeedback
import com.example.ui.components.LocalReduceMotion
import kotlinx.coroutines.launch

/**
 * Interactive "Trip Wrapped" Instagram Story–style recap screen.
 * Swipe through animated Indian squad awards and share any card as a crisp PNG to WhatsApp.
 */
@Composable
fun TripWrappedScreen(
    trip: Trip,
    bills: List<Bill>,
    settlements: List<SettlementRecord>,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val feedback = LocalBilliFeedback.current
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()

    val cards = remember(trip, bills, settlements) {
        TripWrappedEngine.generateStoryCards(trip, bills, settlements)
    }
    val pagerState = rememberPagerState(pageCount = { cards.size.coerceAtLeast(1) })
    var exportingShare by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        feedback?.celebrateDoublePulse()
    }

    val currentCard = cards.getOrNull(pagerState.currentPage)
    val bgBrush = if (currentCard != null) {
        Brush.verticalGradient(
            listOf(
                Color(currentCard.gradientStartHex),
                Color(currentCard.gradientEndHex)
            )
        )
    } else {
        Brush.verticalGradient(listOf(Color(0xFF0E6E52), Color(0xFF1F4037)))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("trip_wrapped_screen")
    ) {
        // Top segmented progress bar + close button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                cards.forEachIndexed { index, _ ->
                    val filled = index <= pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (filled) Color.White else Color.White.copy(alpha = 0.28f)
                            )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFFFE082),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Billi Trip Wrapped",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = {
                        feedback?.tick()
                        onClose()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .testTag("wrapped_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Trip Wrapped",
                        tint = Color.White
                    )
                }
            }
        }

        // Swipeable story pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 72.dp, bottom = 92.dp)
        ) { page ->
            val card = cards[page]
            WrappedStoryPageContent(
                card = card,
                isActive = pagerState.currentPage == page,
                reduceMotion = reduceMotion
            )
        }

        // Bottom share action bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Swipe for next award →",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val cardToShare = currentCard ?: return@Button
                    feedback?.celebrateDoublePulse()
                    scope.launch {
                        exportingShare = true
                        runCatching {
                            val file = TripWrappedEngine.renderCardToPngFile(
                                context = context,
                                card = cardToShare,
                                tripTitle = trip.title
                            )
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "${cardToShare.title}: ${cardToShare.heroValue} — ${cardToShare.subtitle} (via Billi)"
                                )
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(shareIntent, "Share Trip Wrapped card")
                            )
                        }
                        exportingShare = false
                    }
                },
                enabled = !exportingShare,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF14211D)
                ),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("wrapped_share_button")
            ) {
                if (exportingShare) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share Story", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WrappedStoryPageContent(
    card: TripWrappedCardData,
    isActive: Boolean,
    reduceMotion: Boolean
) {
    val popScale = remember(card.id) { Animatable(if (reduceMotion) 1f else 0.88f) }
    LaunchedEffect(isActive, reduceMotion) {
        if (reduceMotion) {
            popScale.snapTo(1f)
        } else if (isActive) {
            popScale.snapTo(0.88f)
            popScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .scale(popScale.value),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color.White.copy(alpha = 0.16f)
        ) {
            Text(
                text = card.badge,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFFFE082),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
            )
        }

        Spacer(Modifier.height(22.dp))

        Text(
            text = card.title,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(26.dp))

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.Black.copy(alpha = 0.22f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = card.heroValue,
                style = MaterialTheme.typography.displayMedium,
                color = Color(0xFFFFE082),
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = card.subtitle,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.94f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(18.dp))

        Text(
            text = card.footnote,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.72f),
            textAlign = TextAlign.Center
        )
    }
}
