package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SyncConnectionState
import com.example.data.model.SyncStatusUi
import com.example.ui.theme.OfflineAmber
import com.example.ui.theme.OfflineAmberBg
import com.example.ui.theme.OnlinePine
import com.example.ui.theme.OnlinePineBg

@Composable
fun SyncStatusPill(
    syncStatus: SyncStatusUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visual = when (syncStatus.state) {
        SyncConnectionState.ONLINE_SYNCED -> SyncPillStyle(
            icon = Icons.Rounded.CloudDone,
            label = "Online",
            bg = OnlinePineBg,
            fg = OnlinePine,
            desc = "Online and synced to cloud",
            pulse = false
        )
        SyncConnectionState.SYNCING -> SyncPillStyle(
            icon = Icons.Rounded.CloudSync,
            label = "Syncing…",
            bg = MaterialTheme.colorScheme.primaryContainer,
            fg = MaterialTheme.colorScheme.onPrimaryContainer,
            desc = "Syncing ${syncStatus.pendingWritesCount} pending changes",
            pulse = true
        )
        SyncConnectionState.OFFLINE_QUEUED -> SyncPillStyle(
            icon = Icons.Rounded.CloudUpload,
            label = "Offline • ${syncStatus.pendingWritesCount}",
            bg = OfflineAmberBg,
            fg = OfflineAmber,
            desc = "Offline with ${syncStatus.pendingWritesCount} items queued for background sync",
            pulse = true
        )
        SyncConnectionState.OFFLINE_SYNCED -> SyncPillStyle(
            icon = Icons.Rounded.CloudOff,
            label = "Offline",
            bg = OfflineAmberBg,
            fg = OfflineAmber,
            desc = "Offline mode using cached ledger",
            pulse = false
        )
    }

    val animatedBg by animateColorAsState(visual.bg, tween(280), label = "sync_bg")
    val animatedFg by animateColorAsState(visual.fg, tween(280), label = "sync_fg")

    val infiniteTransition = rememberInfiniteTransition(label = "sync_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse_alpha"
    )

    Surface(
        shape = RoundedCornerShape(50),
        color = animatedBg,
        border = BorderStroke(1.dp, animatedFg.copy(alpha = 0.24f)),
        modifier = modifier
            .heightIn(min = 34.dp)
            .animateContentSize(BilliMotion.snappySpring())
            .billiPressScale(0.95f)
            .testTag("sync_status_pill")
            .semantics { contentDescription = visual.desc }
            .clickable(onClick = onClick)
    ) {
        AnimatedContent(
            targetState = visual,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.92f)) togetherWith
                    (fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.92f))
            },
            label = "sync_pill_anim"
        ) { target ->
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = target.icon,
                    contentDescription = null,
                    tint = animatedFg,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { alpha = if (target.pulse) pulseAlpha else 1f }
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = target.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = animatedFg
                )
            }
        }
    }
}

private data class SyncPillStyle(
    val icon: ImageVector,
    val label: String,
    val bg: Color,
    val fg: Color,
    val desc: String,
    val pulse: Boolean
)
