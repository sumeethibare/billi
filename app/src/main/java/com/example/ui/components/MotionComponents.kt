package com.example.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.BillCategory
import com.example.ui.theme.ChartActivity
import com.example.ui.theme.ChartFood
import com.example.ui.theme.ChartOther
import com.example.ui.theme.ChartShopping
import com.example.ui.theme.ChartStay
import com.example.ui.theme.ChartTransport
import kotlinx.coroutines.CancellationException

/**
 * Global Low-End Device & Battery-Saver animation flag.
 * When true, Compose springs, shimmers, and screen transitions simplify to lightweight transitions.
 */
val LocalReduceMotion = compositionLocalOf { false }

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

/**
 * Shared spring physics and crisp Material 3 Shared-Axis transitions for Billi.
 * Avoids scale collisions with card press feedback and strictly honors [LocalReduceMotion].
 */
object BilliMotion {
    fun <T> snappySpring(reduceMotion: Boolean = false): FiniteAnimationSpec<T> =
        if (reduceMotion) tween(80) else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )

    fun <T> bouncySpring(reduceMotion: Boolean = false): FiniteAnimationSpec<T> =
        if (reduceMotion) tween(80) else spring(
            dampingRatio = 0.74f,
            stiffness = Spring.StiffnessMediumLow
        )

    fun <T> gentleSpec(durationMs: Int = 280, reduceMotion: Boolean = false): AnimationSpec<T> =
        if (reduceMotion) snap() else tween(durationMs, easing = EmphasizedDecelerate)

    // Crisp Material 3 Shared-Axis X (no scale clipping or ghost stutter)
    fun screenEnterForward(reduceMotion: Boolean): EnterTransition =
        if (reduceMotion) fadeIn(tween(90))
        else fadeIn(tween(220, delayMillis = 35, easing = EmphasizedDecelerate)) +
            slideInHorizontally(tween(280, easing = EmphasizedDecelerate)) { fullWidth ->
                (fullWidth * 0.08f).toInt()
            }

    fun screenExitForward(reduceMotion: Boolean): ExitTransition =
        if (reduceMotion) fadeOut(tween(80))
        else fadeOut(tween(150, easing = FastOutLinearInEasing)) +
            slideOutHorizontally(tween(220, easing = EmphasizedAccelerate)) { fullWidth ->
                -(fullWidth * 0.05f).toInt()
            }

    fun screenPopEnter(reduceMotion: Boolean): EnterTransition =
        if (reduceMotion) fadeIn(tween(90))
        else fadeIn(tween(210, delayMillis = 25, easing = EmphasizedDecelerate)) +
            slideInHorizontally(tween(260, easing = EmphasizedDecelerate)) { fullWidth ->
                -(fullWidth * 0.06f).toInt()
            }

    fun screenPopExit(reduceMotion: Boolean): ExitTransition =
        if (reduceMotion) fadeOut(tween(80))
        else fadeOut(tween(150, easing = FastOutLinearInEasing)) +
            slideOutHorizontally(tween(220, easing = EmphasizedAccelerate)) { fullWidth ->
                (fullWidth * 0.08f).toInt()
            }

    // Crisp Material 3 Shared-Axis Y for full-screen overlays (Editor, QR, Trip Wrapped, Receipt Viewer)
    fun overlayEnter(reduceMotion: Boolean): EnterTransition =
        if (reduceMotion) fadeIn(tween(90))
        else fadeIn(tween(220, easing = EmphasizedDecelerate)) +
            slideInVertically(tween(280, easing = EmphasizedDecelerate)) { it / 14 }

    fun overlayExit(reduceMotion: Boolean): ExitTransition =
        if (reduceMotion) fadeOut(tween(80))
        else fadeOut(tween(170, easing = FastOutLinearInEasing)) +
            slideOutVertically(tween(210, easing = EmphasizedAccelerate)) { it / 16 }

    val screenEnterForward: EnterTransition get() = screenEnterForward(false)
    val screenExitForward: ExitTransition get() = screenExitForward(false)
    val screenPopEnter: EnterTransition get() = screenPopEnter(false)
    val screenPopExit: ExitTransition get() = screenPopExit(false)
}

fun Modifier.billiPressScale(
    pressedScale: Float = 0.985f,
    interactionSource: MutableInteractionSource? = null
): Modifier = composed {
    val reduceMotion = LocalReduceMotion.current
    if (reduceMotion) return@composed this
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = BilliMotion.snappySpring(false),
        label = "press_scale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun BilliMorphCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(20.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    tonalElevation: Dp = 1.dp,
    animateSize: Boolean = false,
    content: @Composable () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (!reduceMotion && pressed && onClick != null) 0.988f else 1f,
        animationSpec = BilliMotion.snappySpring(reduceMotion),
        label = "morph_card_scale"
    )

    val clickableMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.material3.ripple(),
            onClick = onClick
        )
    } else {
        Modifier
    }

    val motionModifier = if (reduceMotion) {
        modifier.then(clickableMod)
    } else {
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (animateSize) Modifier.animateContentSize(BilliMotion.snappySpring(false)) else Modifier)
            .then(clickableMod)
    }

    Surface(
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = tonalElevation,
        modifier = motionModifier,
        content = content
    )
}

/**
 * Flicker-free list wrapper (no destructive visibility reset on LazyColumn scroll or back-navigation).
 */
@Composable
fun BilliStaggeredItem(
    @Suppress("UNUSED_PARAMETER") index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
    }
}

/**
 * Full-screen animated overlay container with live Android 14+ Predictive Back scale & corner preview.
 * Preserves the underlying screen's scroll position instead of unmounting it via early returns.
 */
@Composable
fun BilliPredictiveOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    var backProgress by remember(visible) { mutableFloatStateOf(0f) }
    var swipeEdge by remember(visible) { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    if (visible) {
        PredictiveBackHandler(enabled = true) { progressFlow ->
            try {
                progressFlow.collect { event ->
                    backProgress = event.progress
                    swipeEdge = event.swipeEdge
                }
                backProgress = 0f
                onDismiss()
            } catch (e: CancellationException) {
                backProgress = 0f
                throw e
            }
        }
    }

    val smoothProgress by animateFloatAsState(
        targetValue = if (reduceMotion) 0f else backProgress,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "predictive_back_progress"
    )

    AnimatedVisibility(
        visible = visible,
        enter = BilliMotion.overlayEnter(reduceMotion),
        exit = BilliMotion.overlayExit(reduceMotion),
        modifier = modifier.fillMaxSize()
    ) {
        val cornerRadius = (smoothProgress * 28f).dp
        val scale = 1f - (smoothProgress * 0.08f)
        val shiftX = if (swipeEdge == BackEventCompat.EDGE_LEFT) smoothProgress * 36f else -smoothProgress * 36f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = shiftX
                }
                .clip(RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.background)
        ) {
            content()
        }
    }
}

// --- LIVE SHIMMER SKELETONS ---

@Composable
fun BilliShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    val reduceMotion = LocalReduceMotion.current
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerLowest

    if (reduceMotion) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(base)
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "billi_shimmer")
    val translate by transition.animateFloat(
        initialValue = -320f,
        targetValue = 960f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )

    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate, 0f),
        end = Offset(translate + 280f, 160f)
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

@Composable
fun DashboardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 680.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Overall Net Balance Hero Skeleton
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                BilliShimmerBox(Modifier.width(140.dp).height(14.dp))
                Spacer(Modifier.height(10.dp))
                BilliShimmerBox(Modifier.width(190.dp).height(30.dp))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BilliShimmerBox(Modifier.weight(1f).height(26.dp), RoundedCornerShape(10.dp))
                    BilliShimmerBox(Modifier.weight(1f).height(26.dp), RoundedCornerShape(10.dp))
                }
            }
        }
        // Search + Filter pills skeleton
        BilliShimmerBox(Modifier.fillMaxWidth().height(48.dp), RoundedCornerShape(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) {
                BilliShimmerBox(Modifier.width(72.dp).height(30.dp), RoundedCornerShape(50))
            }
        }
        // Trip Card skeletons
        repeat(3) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BilliShimmerBox(Modifier.width(160.dp).height(20.dp))
                        BilliShimmerBox(Modifier.width(78.dp).height(22.dp), RoundedCornerShape(50))
                    }
                    Spacer(Modifier.height(10.dp))
                    BilliShimmerBox(Modifier.width(210.dp).height(14.dp))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BilliShimmerBox(Modifier.width(86.dp).height(24.dp), RoundedCornerShape(8.dp))
                        BilliShimmerBox(Modifier.width(96.dp).height(24.dp), RoundedCornerShape(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TripDetailSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 700.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary Hero Card skeleton
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BilliShimmerBox(Modifier.width(110.dp).height(14.dp))
                    BilliShimmerBox(Modifier.width(84.dp).height(22.dp), RoundedCornerShape(50))
                }
                Spacer(Modifier.height(12.dp))
                BilliShimmerBox(Modifier.width(180.dp).height(34.dp))
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BilliShimmerBox(Modifier.weight(1f).height(42.dp), RoundedCornerShape(12.dp))
                    BilliShimmerBox(Modifier.weight(1f).height(42.dp), RoundedCornerShape(12.dp))
                }
            }
        }
        // Search bar skeleton
        BilliShimmerBox(Modifier.fillMaxWidth().height(48.dp), RoundedCornerShape(16.dp))
        // Day header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BilliShimmerBox(Modifier.width(120.dp).height(16.dp))
            BilliShimmerBox(Modifier.width(70.dp).height(18.dp), RoundedCornerShape(50))
        }
        // Bill rows skeleton
        repeat(4) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BilliShimmerBox(Modifier.size(40.dp), CircleShape)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        BilliShimmerBox(Modifier.width(140.dp).height(16.dp))
                        Spacer(Modifier.height(6.dp))
                        BilliShimmerBox(Modifier.width(185.dp).height(12.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    BilliShimmerBox(Modifier.width(64.dp).height(20.dp))
                }
            }
        }
    }
}

fun categoryAccentColor(category: BillCategory): Color = when (category) {
    BillCategory.FOOD -> ChartFood
    BillCategory.STAY -> ChartStay
    BillCategory.TRANSPORT -> ChartTransport
    BillCategory.ACTIVITY -> ChartActivity
    BillCategory.SHOPPING -> ChartShopping
    BillCategory.OTHER -> ChartOther
}
