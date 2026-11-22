package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

enum class BilliToastTone {
    SUCCESS,
    INFO,
    WARNING,
    ERROR
}

data class BilliToastMessage(
    val id: Long,
    val message: String,
    val tone: BilliToastTone = BilliToastTone.INFO,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val durationMillis: Long = DEFAULT_DURATION_MS
) {
    companion object {
        const val DEFAULT_DURATION_MS = 2600L
        const val ACTIONABLE_DURATION_MS = 5000L
    }
}

@Stable
class BilliToastController(
    private val scope: CoroutineScope
) {
    private val idCounter = AtomicLong(0L)
    private var dismissJob: Job? = null

    private val _currentToast = MutableStateFlow<BilliToastMessage?>(null)
    val currentToast: StateFlow<BilliToastMessage?> = _currentToast.asStateFlow()

    fun show(
        message: String,
        tone: BilliToastTone? = null,
        actionLabel: String? = null,
        durationMillis: Long? = null,
        onAction: (() -> Unit)? = null
    ) {
        val cleanMessage = message.trim()
        if (cleanMessage.isEmpty()) return

        val resolvedTone = tone ?: inferToastTone(cleanMessage)
        val resolvedDuration = durationMillis ?: if (actionLabel != null) {
            BilliToastMessage.ACTIONABLE_DURATION_MS
        } else {
            BilliToastMessage.DEFAULT_DURATION_MS
        }
        val nextId = idCounter.incrementAndGet()
        val toast = BilliToastMessage(
            id = nextId,
            message = cleanMessage,
            tone = resolvedTone,
            actionLabel = actionLabel,
            onAction = onAction,
            durationMillis = resolvedDuration
        )

        dismissJob?.cancel()
        _currentToast.value = toast
        dismissJob = scope.launch {
            delay(resolvedDuration)
            if (_currentToast.value?.id == nextId) {
                _currentToast.value = null
            }
        }
    }

    fun dismiss(toastId: Long? = null) {
        if (toastId == null || _currentToast.value?.id == toastId) {
            dismissJob?.cancel()
            _currentToast.value = null
        }
    }

    companion object {
        internal fun inferToastTone(message: String): BilliToastTone {
            val lower = message.lowercase()
            return when {
                lower.contains("failed") ||
                    lower.contains("could not") ||
                    lower.contains("unable") ||
                    lower.contains("cannot") ||
                    lower.contains("error") ||
                    lower.contains("denied") ||
                    lower.contains("mismatch") ||
                    lower.contains("invalid") ||
                    lower.contains("no published") -> BilliToastTone.ERROR

                lower.contains("deleted") ||
                    lower.contains("removed") ||
                    lower.contains("enable \"") ||
                    lower.contains("allow \"") ||
                    lower.contains("rate limit") -> BilliToastTone.WARNING

                lower.contains("added") ||
                    lower.contains("updated") ||
                    lower.contains("saved") ||
                    lower.contains("recorded") ||
                    lower.contains("restored") ||
                    lower.contains("copied") ||
                    lower.contains("pinned") ||
                    lower.contains("linked") ||
                    lower.contains("promoted") ||
                    lower.contains("transferred") ||
                    lower.contains("joined") ||
                    lower.contains("created") ||
                    lower.contains("latest") -> BilliToastTone.SUCCESS

                else -> BilliToastTone.INFO
            }
        }
    }
}

val LocalBilliToastController = staticCompositionLocalOf<BilliToastController?> { null }

/**
 * App-wide floating pill toast overlay descending from the top safe area (below status bar
 * and display cutout). Supports semantic icons, inline Undo actions, Reduce Motion fallback,
 * and swipe-up-to-dismiss with haptic feedback.
 */
@Composable
fun BilliTopToastHost(
    controller: BilliToastController,
    modifier: Modifier = Modifier
) {
    val toast by controller.currentToast.collectAsStateWithLifecycle()
    val reduceMotion = LocalReduceMotion.current
    val feedback = LocalBilliFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedContent(
            targetState = toast,
            contentKey = { it?.id ?: -1L },
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(tween(150)) togetherWith fadeOut(tween(120))
                } else {
                    (slideInVertically(
                        animationSpec = spring(
                            dampingRatio = 0.78f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        initialOffsetY = { -it - 48 }
                    ) + fadeIn(tween(180)) + scaleIn(
                        initialScale = 0.94f,
                        animationSpec = tween(180)
                    )) togetherWith (slideOutVertically(
                        animationSpec = tween(170),
                        targetOffsetY = { -it - 36 }
                    ) + fadeOut(tween(140)) + scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(140)
                    ))
                }
            },
            label = "billi_top_toast_transition"
        ) { current ->
            if (current != null) {
                BilliFloatingToastPill(
                    toast = current,
                    reduceMotion = reduceMotion,
                    onDismiss = {
                        feedback?.tick()
                        controller.dismiss(current.id)
                    },
                    onActionClick = {
                        val action = current.onAction
                        controller.dismiss(current.id)
                        feedback?.celebrateDoublePulse()
                        action?.invoke()
                    }
                )
            }
        }
    }
}

@Composable
private fun BilliFloatingToastPill(
    toast: BilliToastMessage,
    reduceMotion: Boolean,
    onDismiss: () -> Unit,
    onActionClick: () -> Unit
) {
    val density = LocalDensity.current
    val dismissThresholdPx = remember(density) { with(density) { 36.dp.toPx() } }
    var rawDragOffsetY by remember(toast.id) { mutableFloatStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(
        targetValue = rawDragOffsetY,
        animationSpec = if (reduceMotion) tween(0) else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "toast_drag_offset"
    )

    val (badgeContainer, badgeContent, leadingIcon) = resolveToastVisuals(toast)
    val shape = RoundedCornerShape(28.dp)

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        modifier = Modifier
            .widthIn(min = 180.dp, max = 460.dp)
            .offset { IntOffset(0, animatedOffsetY.roundToInt()) }
            .semantics { liveRegion = LiveRegionMode.Polite }
            .pointerInput(toast.id) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        val next = rawDragOffsetY + dragAmount
                        // Allow free upward swipe, slight rubber-band resistance downward
                        rawDragOffsetY = if (next > 0f) next * 0.2f else next
                    },
                    onDragEnd = {
                        if (rawDragOffsetY <= -dismissThresholdPx) {
                            onDismiss()
                        } else {
                            rawDragOffsetY = 0f
                        }
                    },
                    onDragCancel = {
                        rawDragOffsetY = 0f
                    }
                )
            }
            .testTag("billi_top_toast")
    ) {
        Row(
            modifier = Modifier
                .padding(
                    start = 10.dp,
                    end = if (toast.actionLabel != null) 8.dp else 16.dp,
                    top = 8.dp,
                    bottom = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(badgeContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = badgeContent,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = toast.message,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            if (toast.actionLabel != null && toast.onAction != null) {
                Spacer(Modifier.width(2.dp))
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onActionClick)
                        .testTag("billi_top_toast_action"),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            text = toast.actionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun resolveToastVisuals(toast: BilliToastMessage): Triple<Color, Color, ImageVector> {
    val isDeleteAction = toast.actionLabel != null &&
        toast.message.contains("Deleted", ignoreCase = true)
    return when (toast.tone) {
        BilliToastTone.SUCCESS -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Rounded.CheckCircle
        )
        BilliToastTone.WARNING -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            if (isDeleteAction) Icons.Rounded.DeleteOutline else Icons.Rounded.WarningAmber
        )
        BilliToastTone.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Rounded.ErrorOutline
        )
        BilliToastTone.INFO -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Rounded.Info
        )
    }
}
