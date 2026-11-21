package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
import kotlinx.coroutines.delay

/**
 * Global Low-End Device & Battery-Saver animation flag.
 * When true, Compose springs and staggered reveals simplify to snappy lightweight transitions.
 */
val LocalReduceMotion = compositionLocalOf { false }

/**
 * Shared spring physics and smooth morph transitions for Billi's UI components and screens.
 */
object BilliMotion {
    fun <T> snappySpring(reduceMotion: Boolean = false): FiniteAnimationSpec<T> =
        if (reduceMotion) tween(90) else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )

    fun <T> bouncySpring(reduceMotion: Boolean = false): FiniteAnimationSpec<T> =
        if (reduceMotion) tween(90) else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )

    fun <T> gentleSpec(durationMs: Int = 320, reduceMotion: Boolean = false): AnimationSpec<T> =
        if (reduceMotion) snap() else tween(durationMs)

    fun screenEnterForward(reduceMotion: Boolean): EnterTransition =
        if (reduceMotion) fadeIn(tween(110))
        else fadeIn(tween(260)) +
            slideInHorizontally(tween(300)) { fullWidth -> fullWidth / 10 } +
            scaleIn(tween(280), initialScale = 0.97f)

    fun screenExitForward(reduceMotion: Boolean): ExitTransition =
        if (reduceMotion) fadeOut(tween(90))
        else fadeOut(tween(200)) +
            slideOutHorizontally(tween(280)) { fullWidth -> -fullWidth / 14 } +
            scaleOut(tween(260), targetScale = 0.98f)

    fun screenPopEnter(reduceMotion: Boolean): EnterTransition =
        if (reduceMotion) fadeIn(tween(110))
        else fadeIn(tween(240)) +
            slideInHorizontally(tween(280)) { fullWidth -> -fullWidth / 12 } +
            scaleIn(tween(260), initialScale = 0.98f)

    fun screenPopExit(reduceMotion: Boolean): ExitTransition =
        if (reduceMotion) fadeOut(tween(90))
        else fadeOut(tween(200)) +
            slideOutHorizontally(tween(260)) { fullWidth -> fullWidth / 10 } +
            scaleOut(tween(240), targetScale = 0.97f)

    val screenEnterForward: EnterTransition get() = screenEnterForward(false)
    val screenExitForward: ExitTransition get() = screenExitForward(false)
    val screenPopEnter: EnterTransition get() = screenPopEnter(false)
    val screenPopExit: ExitTransition get() = screenPopExit(false)
}

fun Modifier.billiPressScale(
    pressedScale: Float = 0.975f,
    interactionSource: MutableInteractionSource? = null
): Modifier = composed {
    val reduceMotion = LocalReduceMotion.current
    if (reduceMotion) return@composed this
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = BilliMotion.bouncySpring(false),
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
    content: @Composable () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (!reduceMotion && pressed && onClick != null) 0.98f else 1f,
        animationSpec = BilliMotion.bouncySpring(reduceMotion),
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
            .animateContentSize(animationSpec = BilliMotion.snappySpring(false))
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

@Composable
fun BilliStaggeredItem(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    if (reduceMotion) {
        content()
        return
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(8) * 35L))
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(260)) + slideInVertically(tween(280)) { it / 5 }
    ) {
        content()
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
