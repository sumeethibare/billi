package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.domain.AppUpdateDownloadState
import com.example.domain.GitHubReleaseInfo
import com.example.domain.GitHubReleaseUpdater

/**
 * Material 3 Bottom Sheet for GitHub Release In-App Updates (`sumeethibare/billi`).
 * Displays parsed Markdown release notes, live APK download progress, and requests
 * REQUEST_INSTALL_PACKAGES ONLY right when the user taps "Install Update".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateBottomSheet(
    release: GitHubReleaseInfo,
    downloadState: AppUpdateDownloadState,
    onStartDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val feedback = LocalBilliFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var needsInstallPermissionHint by remember { mutableStateOf(false) }
    var pendingInstallFile by remember { mutableStateOf<java.io.File?>(null) }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val apkFile = pendingInstallFile
        if (apkFile != null && GitHubReleaseUpdater.canRequestPackageInstalls(context)) {
            needsInstallPermissionHint = false
            pendingInstallFile = null
            feedback?.celebrateDoublePulse()
            runCatching {
                context.startActivity(
                    GitHubReleaseUpdater.createInstallApkIntent(context, apkFile)
                )
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (release.isNewerThanCurrent) {
                            Icons.Rounded.SystemUpdate
                        } else {
                            Icons.Rounded.CheckCircle
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (release.isNewerThanCurrent) {
                            "New Billi Update Available"
                        } else {
                            "Billi is Up to Date"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${release.tagName} • Current v${BuildConfig.VERSION_NAME} • ${
                            GitHubReleaseUpdater.formatFileSize(release.apkSizeBytes)
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Parsed Markdown release notes card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NewReleases,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = release.releaseTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    ReleaseNotesMarkdownView(release.releaseNotesMarkdown)
                }
            }

            if (needsInstallPermissionHint) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Allow \"Install unknown apps\" for Billi in Settings, then return and tap Install Update again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = downloadState,
                label = "update_download_state"
            ) { state ->
                when (state) {
                    is AppUpdateDownloadState.Idle -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("update_later_button")
                            ) {
                                Text(if (release.isNewerThanCurrent) "Later" else "Close")
                            }
                            if (release.canDownloadInApp && release.isNewerThanCurrent) {
                                Button(
                                    onClick = {
                                        feedback?.tick()
                                        onStartDownload()
                                    },
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .height(48.dp)
                                        .testTag("update_download_button")
                                ) {
                                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Download APK")
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .height(48.dp)
                                        .testTag("update_github_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("View on GitHub")
                                }
                            }
                        }
                    }

                    is AppUpdateDownloadState.Downloading -> {
                        val animProgress by animateFloatAsState(
                            targetValue = state.progress,
                            animationSpec = tween(160),
                            label = "apk_progress"
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Downloading ${release.apkFileName}…",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = if (state.totalBytes > 0L) {
                                        "${(state.progress * 100).toInt()}%"
                                    } else {
                                        GitHubReleaseUpdater.formatFileSize(state.downloadedBytes)
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (state.totalBytes > 0L) {
                                LinearProgressIndicator(
                                    progress = { animProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                )
                            }
                        }
                    }

                    is AppUpdateDownloadState.ReadyToInstall -> {
                        LaunchedEffect(state.apkFile.absolutePath, state.checksumVerified) {
                            if (state.checksumVerified) {
                                feedback?.celebrateDoublePulse()
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (state.checksumVerified) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("update_sha256_verified_badge")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.VerifiedUser,
                                            contentDescription = "SHA-256 checksum verified",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "SHA-256 checksum verified — ready to install",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            Button(
                                onClick = {
                                    feedback?.celebrateDoublePulse()
                                    if (!GitHubReleaseUpdater.canRequestPackageInstalls(context)) {
                                        needsInstallPermissionHint = true
                                        pendingInstallFile = state.apkFile
                                        runCatching {
                                            installPermissionLauncher.launch(
                                                GitHubReleaseUpdater.createUnknownSourcesSettingsIntent(context)
                                            )
                                        }
                                    } else {
                                        needsInstallPermissionHint = false
                                        pendingInstallFile = null
                                        runCatching {
                                            context.startActivity(
                                                GitHubReleaseUpdater.createInstallApkIntent(context, state.apkFile)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("install_update_button")
                            ) {
                                Icon(Icons.Rounded.InstallMobile, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Install Update")
                            }
                        }
                    }

                    is AppUpdateDownloadState.Error -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Close") }
                                Button(
                                    onClick = onStartDownload,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Retry") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseNotesMarkdownView(markdown: String) {
    val lines = remember(markdown) {
        markdown
            .replace("\r\n", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(18)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { rawLine ->
            when {
                rawLine.startsWith("### ") || rawLine.startsWith("## ") || rawLine.startsWith("# ") -> {
                    Text(
                        text = rawLine.trimStart('#').trim(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                rawLine.startsWith("- ") || rawLine.startsWith("* ") -> {
                    val clean = compactHashDisplay(rawLine.drop(2).replace("**", "").replace("`", ""))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = clean,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    Text(
                        text = compactHashDisplay(rawLine.replace("**", "").replace("`", "")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private val LongHexTokenRegex = Regex("""\b([0-9a-fA-F]{40,64})\b""")

private fun compactHashDisplay(line: String): String {
    return LongHexTokenRegex.replace(line) { match ->
        val hex = match.value
        if (hex.length >= 24) "${hex.take(12)}…${hex.takeLast(8)}" else hex
    }
}
