package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Choreographs the signature Billi post-sign-in morph:
 * The signed-in user's Google profile avatar starts centered in the hero loading orb,
 * glides seamlessly along a spring curve into the top-right Account avatar slot,
 * fires a soft tactile thud upon docking, and drives the Dashboard's smooth
 * top-to-bottom fade-in & blur-to-sharp reveal.
 */
@Composable
fun SignInAvatarMorphOverlay(
    photoUrl: String?,
    displayName: String,
    onRevealProgress: (Float) -> Unit,
    onFinished: () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    val feedback = LocalBilliFeedback.current
    val density = LocalDensity.current

    val travelProgress = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }
    val revealAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            onRevealProgress(1f)
            feedback?.softThud()
            onFinished()
            return@LaunchedEffect
        }
        delay(120)
        // Glide avatar to top-right while simultaneously unveiling the Dashboard
        val travelJob = launch {
            travelProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = 190f
                )
            )
        }
        val revealJob = launch {
            delay(180)
            revealAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing)
            ) {
                onRevealProgress(value)
            }
        }
        travelJob.join()
        feedback?.softThud()
        revealJob.join()
        overlayAlpha.animateTo(0f, tween(220))
        onFinished()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .alpha(overlayAlpha.value)
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val startSizeDp = 78.dp
        val endSizeDp = 38.dp
        val p = travelProgress.value.coerceIn(0f, 1f)
        val currentSizeDp = startSizeDp + (endSizeDp - startSizeDp) * p
        val currentSizePx = with(density) { currentSizeDp.toPx() }

        val startX = (widthPx - currentSizePx) / 2f
        val startY = (heightPx - currentSizePx) * 0.56f
        val endX = widthPx - with(density) { 54.dp.toPx() }
        val endY = with(density) { 46.dp.toPx() }

        val curX = startX + (endX - startX) * p
        val curY = startY + (endY - startY) * p

        Box(
            modifier = Modifier
                .offset { IntOffset(curX.roundToInt(), curY.roundToInt()) }
                .size(currentSizeDp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(
                    width = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Signing in as $displayName",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(currentSizeDp * 0.55f)
                )
            }
        }
    }
}

/**
 * 2-Step Interactive First-Time Walkthrough Spotlight Coachmark.
 * Step 0: Points directly to "+ New Trip" (bottom-end FAB).
 * Step 1: Points directly to "Scan QR" (top-right scanner icon).
 */
@Composable
fun SpotlightWalkthroughOverlay(
    onComplete: () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    val feedback = LocalBilliFeedback.current
    var step by remember { mutableIntStateOf(0) }

    val pulseScale = if (reduceMotion) {
        1f
    } else {
        val infinite = rememberInfiniteTransition(label = "spotlight_pulse")
        val anim by infinite.animateFloat(
            initialValue = 1f,
            targetValue = 1.14f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_ring"
        )
        anim
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .testTag("spotlight_walkthrough_overlay")
    ) {
        // Pulsing visual spotlight halo positioned at the real control
        if (step == 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 14.dp, bottom = 14.dp)
                    .size(64.dp)
                    .scale(pulseScale)
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(22.dp)
                    )
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(22.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 6.dp, end = 54.dp)
                    .size(54.dp)
                    .scale(pulseScale)
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    )
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.QrCodeScanner,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Coachmark explanation card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .align(if (step == 0) Alignment.BottomCenter else Alignment.TopCenter)
                .then(
                    if (step == 0) {
                        Modifier
                            .navigationBarsPadding()
                            .padding(bottom = 96.dp, start = 20.dp, end = 20.dp)
                    } else {
                        Modifier
                            .statusBarsPadding()
                            .padding(top = 74.dp, start = 20.dp, end = 20.dp)
                    }
                )
                .fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = step,
                label = "coachmark_step"
            ) { currentStep ->
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "QUICK TOUR • STEP ${currentStep + 1} OF 2",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (currentStep == 0) {
                            "Start a Trip in Seconds"
                        } else {
                            "Join a Squad via QR Code"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (currentStep == 0) {
                            "Tap + New Trip below to create an INR group ledger with Creator Lock, Smart Receipt Scan, Split Payments, and offline sync."
                        } else {
                            "Already travelling with friends? Tap Scan QR at the top right to sync their live ledger directly to your phone."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                feedback?.tick()
                                onComplete()
                            },
                            modifier = Modifier.testTag("walkthrough_skip_button")
                        ) {
                            Text("Skip")
                        }
                        Button(
                            onClick = {
                                if (currentStep == 0) {
                                    feedback?.tick()
                                    step = 1
                                } else {
                                    feedback?.celebrateDoublePulse()
                                    onComplete()
                                }
                            },
                            modifier = Modifier.testTag("walkthrough_next_button")
                        ) {
                            Text(if (currentStep == 0) "Next" else "Got It")
                        }
                    }
                }
            }
        }
    }
}
