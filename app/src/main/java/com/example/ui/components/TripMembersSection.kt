package com.example.ui.components

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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.SupervisorAccount
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.SyncedTripUser
import com.example.ui.viewmodel.TripDetailContent

@Composable
fun TripMembersTabContent(
    content: TripDetailContent,
    currentUserId: String,
    isBusy: Boolean,
    onAddMember: (String, () -> Unit) -> Unit,
    onRemoveMember: (String) -> Unit,
    onToggleCoHost: (uid: String, name: String) -> Unit,
    onRequestOwnershipTransfer: (SyncedTripUser) -> Unit,
    onCopiedMessage: (String) -> Unit
) {
    val trip = content.trip
    val isPrimaryCreator = trip.canManageTripAdmin(currentUserId)
    var newMemberName by remember { mutableStateOf("") }

    // Combine syncedUsers directory with any memberUids that haven't written a directory doc yet
    val syncedDirectory = remember(trip.memberUids, trip.creatorUid, trip.creatorName, content.syncedUsers) {
        val byUid = content.syncedUsers.associateBy { it.userId }.toMutableMap()
        if (!byUid.containsKey(trip.creatorUid)) {
            byUid[trip.creatorUid] = SyncedTripUser(
                userId = trip.creatorUid,
                tripId = trip.id,
                displayName = trip.creatorName
            )
        }
        trip.memberUids.map { uid ->
            byUid[uid] ?: SyncedTripUser(
                userId = uid,
                tripId = trip.id,
                displayName = "Billi User (${uid.take(5)})"
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 680.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. QR SYNC CARD ---
        item(key = "qr_share_card") {
            BilliStaggeredItem(index = 0) {
                TripQrShareCard(
                    trip = trip,
                    onCopiedMessage = onCopiedMessage
                )
            }
        }

        // --- 2. CO-HOSTS & OWNERSHIP TRANSFER ---
        item(key = "cohost_admin_card") {
            BilliStaggeredItem(index = 1) {
                BilliMorphCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Synced Billi Users & Permissions", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (isPrimaryCreator) {
                                        "Promote synced companions to Co-Host or transfer primary Creator ownership."
                                    } else {
                                        "Creator & Co-Hosts can edit bills; other synced users have view-only access."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        syncedDirectory.forEachIndexed { idx, user ->
                            val userIsCreator = user.userId == trip.creatorUid
                            val userIsCoHost = user.userId in trip.coHostUids
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!user.photoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(user.photoUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = user.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = user.displayName.firstOrNull()?.uppercase() ?: "?",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (user.userId == currentUserId) {
                                                "${user.displayName} (You)"
                                            } else user.displayName,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = when {
                                                userIsCreator -> "Primary Creator • Full Control"
                                                userIsCoHost -> "Co-Host • Can add & edit bills"
                                                else -> "Viewer • Read-only access"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (userIsCreator || userIsCoHost) {
                                                MaterialTheme.colorScheme.primary
                                            } else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (isPrimaryCreator && !userIsCreator) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Co-Host",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Switch(
                                                checked = userIsCoHost,
                                                onCheckedChange = {
                                                    onToggleCoHost(user.userId, user.displayName)
                                                },
                                                enabled = !isBusy,
                                                modifier = Modifier.testTag("cohost_switch_${user.userId}")
                                            )
                                        }
                                    }
                                }

                                if (isPrimaryCreator && !userIsCreator) {
                                    OutlinedButton(
                                        onClick = { onRequestOwnershipTransfer(user) },
                                        enabled = !isBusy,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("transfer_ownership_${user.userId}")
                                    ) {
                                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Transfer Ownership to ${user.displayName}")
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }

                                if (idx < syncedDirectory.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }

                        if (syncedDirectory.size <= 1) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SupervisorAccount,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Have friends scan the QR code above to appear here for Co-Host permissions or Ownership Transfer.",
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

        // --- 3. PARTICIPANT ROSTER ---
        item(key = "roster_header") {
            Text(
                text = "Bill Split Roster (${trip.members.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        if (isPrimaryCreator) {
            item(key = "add_member_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newMemberName,
                        onValueChange = { if (it.length <= 50) newMemberName = it },
                        placeholder = { Text("Add companion for bill splitting") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_member_input")
                    )
                    Button(
                        onClick = {
                            val clean = newMemberName.trim()
                            if (clean.isNotEmpty()) {
                                onAddMember(clean) { newMemberName = "" }
                            }
                        },
                        enabled = newMemberName.trim().isNotEmpty() && !isBusy,
                        modifier = Modifier
                            .height(54.dp)
                            .billiPressScale()
                            .testTag("add_member_confirm_button")
                    ) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = "Add companion")
                    }
                }
            }
        }

        item(key = "roster_list") {
            BilliMorphCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    trip.members.forEachIndexed { idx, member ->
                        val isCreatorMember = member.linkedUid == trip.creatorUid
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = member.displayName.firstOrNull()?.uppercase() ?: "?",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(member.displayName, style = MaterialTheme.typography.titleSmall)
                                    if (isCreatorMember) {
                                        Text(
                                            text = "Trip Organizer",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            if (isPrimaryCreator && !isCreatorMember) {
                                IconButton(
                                    onClick = { onRemoveMember(member.memberId) },
                                    modifier = Modifier.testTag("remove_member_${member.memberId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Remove ${member.displayName}",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        if (idx < trip.members.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}
