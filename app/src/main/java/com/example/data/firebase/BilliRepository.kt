package com.example.data.firebase

import android.content.Context
import android.graphics.Bitmap
import com.example.R
import com.example.data.model.Bill
import com.example.data.model.BillCategory
import com.example.data.model.BillComment
import com.example.data.model.BillPayer
import com.example.data.model.BillReaction
import com.example.data.model.BillReceipt
import com.example.data.model.DEFAULT_CURRENCY_CODE
import com.example.data.model.ReactionType
import com.example.data.model.SettlementRecord
import com.example.data.model.SplitMode
import com.example.data.model.SyncedTripUser
import com.example.data.model.Trip
import com.example.data.model.TripMember
import com.example.data.model.TripStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserTripLink
import com.example.domain.ReceiptImageProcessor
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.UUID

data class SnapshotPayload<T>(
    val data: T,
    val pendingWritesCount: Int,
    val isFromCache: Boolean,
    val pendingDocIds: Set<String> = emptySet()
)

/**
 * Firestore & offline-cache repository for Billi.
 * Strictly initializes Firestore with R.string.firestore_database_id and persistent disk caching
 * so trips, multi-payer bills, soft-delete trash bin, settlements, and receipts work offline
 * with per-card sync badges and auto-sync when reconnected.
 */
class BilliRepository(
    private val db: FirebaseFirestore,
    private val appContext: Context,
    private val auth: FirebaseAuth = Firebase.auth
) {
    companion object {
        @Volatile
        private var configuredDb: FirebaseFirestore? = null

        fun getFirestoreInstance(context: Context): FirebaseFirestore {
            return configuredDb ?: synchronized(this) {
                configuredDb ?: run {
                    val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
                    val instance = FirebaseFirestore.getInstance(databaseId)
                    try {
                        instance.firestoreSettings = firestoreSettings {
                            setLocalCacheSettings(
                                PersistentCacheSettings.newBuilder()
                                    .setSizeBytes(100L * 1024L * 1024L)
                                    .build()
                            )
                        }
                    } catch (_: IllegalStateException) {
                    }
                    instance.also { configuredDb = it }
                }
            }
        }
    }

    constructor(context: Context) : this(
        db = getFirestoreInstance(context),
        appContext = context.applicationContext
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun generateJoinCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return (1..8).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    private fun generateSafeId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(16)}"

    // --- ONE-SHOT SNAPSHOT HELPER (FOR FLOATING BUBBLE / WIDGET) ---

    suspend fun loadTripSnapshot(tripId: String): Trip? {
        if (tripId.isBlank()) return null
        return try {
            requireUserId()
            val snap = db.collection("trips").document(tripId).get().await()
            if (snap.exists()) {
                snap.toObject(Trip::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    // --- OBSERVE STREAMS ---

    fun observeTrips(userId: String): Flow<SnapshotPayload<List<Trip>>> {
        val path = "trips"
        return db.collection("trips")
            .whereArrayContains("memberUids", userId)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                val trips = snapshot.documents
                    .mapNotNull { it.toObject(Trip::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                    .sortedByDescending { it.updatedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L }
                val pendingIds = snapshot.documents
                    .filter { it.metadata.hasPendingWrites() }
                    .map { it.id }
                    .toSet()
                SnapshotPayload(trips, pendingIds.size, snapshot.metadata.isFromCache, pendingIds)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeTrip(tripId: String, userId: String): Flow<SnapshotPayload<Trip?>> {
        val path = "trips/$tripId"
        return db.collection("trips").document(tripId)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                val trip = if (snapshot.exists()) {
                    snapshot.toObject(Trip::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
                val hasPending = snapshot.metadata.hasPendingWrites()
                SnapshotPayload(
                    data = trip,
                    pendingWritesCount = if (hasPending) 1 else 0,
                    isFromCache = snapshot.metadata.isFromCache,
                    pendingDocIds = if (hasPending) setOf(tripId) else emptySet()
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                }
                throw error
            }
    }

    fun observeBills(tripId: String, userId: String): Flow<SnapshotPayload<List<Bill>>> {
        val path = "trips/$tripId/bills"
        return db.collection("trips").document(tripId).collection("bills")
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                val bills = snapshot.documents
                    .mapNotNull { it.toObject(Bill::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                    .sortedByDescending { it.billDateMillis }
                val pendingIds = snapshot.documents
                    .filter { it.metadata.hasPendingWrites() }
                    .map { it.id }
                    .toSet()
                SnapshotPayload(bills, pendingIds.size, snapshot.metadata.isFromCache, pendingIds)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeSettlements(tripId: String, userId: String): Flow<SnapshotPayload<List<SettlementRecord>>> {
        val path = "trips/$tripId/settlements"
        return db.collection("trips").document(tripId).collection("settlements")
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                val records = snapshot.documents
                    .mapNotNull {
                        it.toObject(SettlementRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }
                    .sortedByDescending { it.settledAtMillis }
                val pendingIds = snapshot.documents
                    .filter { it.metadata.hasPendingWrites() }
                    .map { it.id }
                    .toSet()
                SnapshotPayload(records, pendingIds.size, snapshot.metadata.isFromCache, pendingIds)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeSyncedUsers(tripId: String, userId: String): Flow<List<SyncedTripUser>> {
        val path = "trips/$tripId/syncedUsers"
        return db.collection("trips").document(tripId).collection("syncedUsers")
            .snapshots()
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull {
                        it.toObject(SyncedTripUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }
                    .sortedBy { it.displayName.lowercase() }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                emit(emptyList())
            }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                }
                throw error
            }
    }

    suspend fun ensureUserProfile(
        displayName: String,
        photoUrl: String?,
        isOnline: Boolean
    ): Result<Unit> {
        var docPath = "users/unknown"
        return try {
            val uid = requireUserId()
            docPath = "users/$uid"
            val docRef = db.collection("users").document(uid)
            val cleanName = displayName.trim().ifBlank { "Traveler" }.take(80)
            val cleanPhoto = photoUrl?.trim()?.take(500)?.takeIf { it.isNotEmpty() }

            val existing = try {
                docRef.get().await()
            } catch (_: Exception) {
                null
            }

            if (existing != null && existing.exists()) {
                val updatePayload = buildMap<String, Any> {
                    put("displayName", cleanName)
                    if (cleanPhoto != null) put("photoUrl", cleanPhoto)
                    put("defaultCurrencyCode", DEFAULT_CURRENCY_CODE)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                val task = docRef.update(updatePayload)
                if (isOnline) task.await()
            } else {
                val createPayload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", cleanName)
                    if (cleanPhoto != null) put("photoUrl", cleanPhoto)
                    put("defaultCurrencyCode", DEFAULT_CURRENCY_CODE)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                val task = docRef.set(createPayload)
                if (isOnline) task.await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertSyncedTripUser(
        tripId: String,
        displayName: String,
        photoUrl: String?,
        linkedMemberId: String?,
        isOnline: Boolean
    ): Result<Unit> {
        var docPath = "trips/$tripId/syncedUsers/unknown"
        return try {
            val uid = requireUserId()
            docPath = "trips/$tripId/syncedUsers/$uid"
            val cleanName = displayName.trim().ifBlank { "Traveler" }.take(80)
            val cleanPhoto = photoUrl?.trim()?.take(500)?.takeIf { it.isNotEmpty() }
            val payload = buildMap<String, Any> {
                put("userId", uid)
                put("tripId", tripId)
                put("displayName", cleanName)
                if (cleanPhoto != null) put("photoUrl", cleanPhoto)
                if (!linkedMemberId.isNullOrBlank()) put("linkedMemberId", linkedMemberId.take(128))
                put("updatedAt", FieldValue.serverTimestamp())
            }
            val task = db.collection("trips").document(tripId)
                .collection("syncedUsers").document(uid)
                .set(payload)
            if (isOnline) task.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeUserTripLink(userId: String, tripId: String): Flow<String?> {
        val path = "users/$userId/tripLinks/$tripId"
        return db.collection("users").document(userId)
            .collection("tripLinks").document(tripId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserTripLink::class.java)?.linkedMemberId
                } else null
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                }
                throw error
            }
    }

    fun observeAllUserTripLinks(userId: String): Flow<Map<String, String>> {
        val path = "users/$userId/tripLinks"
        return db.collection("users").document(userId)
            .collection("tripLinks")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val link = doc.toObject(UserTripLink::class.java)
                    val tripId = link?.tripId?.ifBlank { doc.id } ?: doc.id
                    val memberId = link?.linkedMemberId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    tripId to memberId
                }.toMap()
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                emit(emptyMap())
            }
    }

    // --- BILL COLLABORATION (CREATOR-LOCK SAFE COMMENTS & REACTIONS) ---

    fun observeBillComments(tripId: String, billId: String, userId: String): Flow<List<BillComment>> {
        val path = "trips/$tripId/bills/$billId/comments"
        return db.collection("trips").document(tripId)
            .collection("bills").document(billId)
            .collection("comments")
            .snapshots()
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull {
                        it.toObject(BillComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }
                    .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                emit(emptyList())
            }
    }

    suspend fun addBillComment(
        tripId: String,
        billId: String,
        authorName: String,
        text: String,
        isOnline: Boolean
    ): Result<String> {
        val commentId = generateSafeId("cmt")
        val path = "trips/$tripId/bills/$billId/comments/$commentId"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(280)
            require(cleanText.isNotEmpty()) { "Comment cannot be empty." }
            val cleanAuthor = authorName.trim().ifBlank { "Traveler" }.take(80)
            val payload = mapOf(
                "tripId" to tripId,
                "billId" to billId,
                "userId" to uid,
                "authorName" to cleanAuthor,
                "text" to cleanText,
                "createdAt" to FieldValue.serverTimestamp()
            )
            val task = db.collection("trips").document(tripId)
                .collection("bills").document(billId)
                .collection("comments").document(commentId)
                .set(payload)
            if (isOnline) task.await()
            Result.success(commentId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteBillComment(
        tripId: String,
        billId: String,
        commentId: String,
        isOnline: Boolean
    ): Result<Unit> {
        val path = "trips/$tripId/bills/$billId/comments/$commentId"
        return try {
            requireUserId()
            val task = db.collection("trips").document(tripId)
                .collection("bills").document(billId)
                .collection("comments").document(commentId)
                .delete()
            if (isOnline) task.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeBillReactions(tripId: String, billId: String, userId: String): Flow<List<BillReaction>> {
        val path = "trips/$tripId/bills/$billId/reactions"
        return db.collection("trips").document(tripId)
            .collection("bills").document(billId)
            .collection("reactions")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(BillReaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                emit(emptyList())
            }
    }

    suspend fun toggleBillReaction(
        tripId: String,
        billId: String,
        authorName: String,
        reactionType: ReactionType,
        removeExisting: Boolean,
        isOnline: Boolean
    ): Result<Unit> {
        var path = "trips/$tripId/bills/$billId/reactions/unknown"
        return try {
            val uid = requireUserId()
            path = "trips/$tripId/bills/$billId/reactions/$uid"
            val docRef = db.collection("trips").document(tripId)
                .collection("bills").document(billId)
                .collection("reactions").document(uid)
            val task = if (removeExisting) {
                docRef.delete()
            } else {
                val cleanAuthor = authorName.trim().ifBlank { "Traveler" }.take(80)
                docRef.set(
                    mapOf(
                        "tripId" to tripId,
                        "billId" to billId,
                        "userId" to uid,
                        "authorName" to cleanAuthor,
                        "emoji" to reactionType.wireValue,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            if (isOnline) task.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- TRIP MUTATIONS ---

    suspend fun createTrip(
        title: String,
        budgetCents: Long = 0L,
        creatorDisplayName: String,
        otherMemberNames: List<String>,
        isOnline: Boolean
    ): Result<String> {
        val cleanTitle = title.trim().take(100)
        val cleanCreatorName = creatorDisplayName.trim().ifBlank { "Me" }.take(80)
        val tripId = generateSafeId("trip")
        val docPath = "trips/$tripId"
        return try {
            val uid = requireUserId()
            require(cleanTitle.isNotEmpty()) { "Trip name is required." }
            val safeBudget = budgetCents.coerceIn(0L, 1_000_000_000L)

            val creatorMemberId = generateSafeId("mem")
            val members = mutableListOf(
                TripMember(
                    memberId = creatorMemberId,
                    displayName = cleanCreatorName,
                    linkedUid = uid
                )
            )
            otherMemberNames
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(49)
                .forEach { name ->
                    members.add(
                        TripMember(
                            memberId = generateSafeId("mem"),
                            displayName = name.take(60),
                            linkedUid = ""
                        )
                    )
                }

            val payload = mapOf(
                "creatorUid" to uid,
                "creatorName" to cleanCreatorName,
                "title" to cleanTitle,
                "currencyCode" to DEFAULT_CURRENCY_CODE,
                "budgetCents" to safeBudget,
                "status" to TripStatus.ACTIVE.wireValue,
                "joinCode" to generateJoinCode(),
                "memberUids" to listOf(uid),
                "coHostUids" to emptyList<String>(),
                "members" to members.map { it.toMap() },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val writeTask = db.collection("trips").document(tripId).set(payload)
            if (isOnline) writeTask.await()
            upsertSyncedTripUser(
                tripId = tripId,
                displayName = cleanCreatorName,
                photoUrl = auth.currentUser?.photoUrl?.toString(),
                linkedMemberId = creatorMemberId,
                isOnline = isOnline
            )
            Result.success(tripId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTripBudget(trip: Trip, budgetCents: Long, isOnline: Boolean): Result<Unit> {
        val docPath = "trips/${trip.id}"
        return try {
            val uid = requireUserId()
            require(trip.canManageTripAdmin(uid)) { "Only the trip creator can update the trip budget." }
            val safeBudget = budgetCents.coerceIn(0L, 1_000_000_000L)
            val task = db.collection("trips").document(trip.id).update(
                mapOf(
                    "budgetCents" to safeBudget,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (isOnline) task.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTripCoHosts(
        trip: Trip,
        coHostUids: List<String>,
        isOnline: Boolean
    ): Result<Unit> {
        val docPath = "trips/${trip.id}"
        return try {
            val uid = requireUserId()
            require(trip.canManageTripAdmin(uid)) { "Only the primary creator can manage co-hosts." }
            val sanitized = coHostUids
                .filter { it.isNotBlank() && it != trip.creatorUid && it in trip.memberUids }
                .distinct()
                .take(20)
            val task = db.collection("trips").document(trip.id).update(
                mapOf(
                    "coHostUids" to sanitized,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (isOnline) task.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun transferTripOwnership(
        trip: Trip,
        newCreatorUid: String,
        newCreatorName: String,
        keepSelfAsCoHost: Boolean,
        isOnline: Boolean
    ): Result<Unit> {
        val docPath = "trips/${trip.id}"
        return try {
            val uid = requireUserId()
            require(trip.canManageTripAdmin(uid)) { "Only the current trip creator can transfer ownership." }
            require(newCreatorUid in trip.memberUids && newCreatorUid != uid) {
                "Select another signed-in trip member to transfer ownership to."
            }
            val cleanName = newCreatorName.trim().ifBlank { "Organizer" }.take(80)
            val nextCoHosts = buildList {
                addAll(trip.coHostUids.filter { it != newCreatorUid && it != uid })
                if (keepSelfAsCoHost) add(uid)
            }.distinct().take(20)

            val task = db.collection("trips").document(trip.id).update(
                mapOf(
                    "creatorUid" to newCreatorUid,
                    "creatorName" to cleanName,
                    "coHostUids" to nextCoHosts,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (isOnline) task.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addTripMember(
        trip: Trip,
        memberName: String,
        isOnline: Boolean
    ): Result<Unit> {
        val docPath = "trips/${trip.id}"
        return try {
            val uid = requireUserId()
            require(trip.canManageTripAdmin(uid)) { "Only the trip creator can edit members on an active trip." }
            val cleanName = memberName.trim().take(60)
            require(cleanName.isNotEmpty()) { "Member name cannot be empty." }
            require(trip.members.size < 50) { "Maximum 50 members reached." }

            val updatedMembers = trip.members + TripMember(
                memberId = generateSafeId("mem"),
                displayName = cleanName,
                linkedUid = ""
            )
            val task = db.collection("trips").document(trip.id).update(
                mapOf(
                    "members" to updatedMembers.map { it.toMap() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (isOnline) task.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun removeTripMember(
        trip: Trip,
        memberId: String,
        bills: List<Bill>,
        isOnline: Boolean
    ): Result<Unit> {
        val docPath = "trips/${trip.id}"
        return try {
            val uid = requireUserId()
            require(trip.canManageTripAdmin(uid)) { "Only the trip creator can manage members." }
            val target = trip.members.firstOrNull { it.memberId == memberId }
                ?: throw IllegalArgumentException("Member not found.")
            require(target.linkedUid != trip.creatorUid) { "The trip creator cannot be removed." }
            val referencedInBill = bills.filter { !it.deleted }.any { bill ->
                bill.paidByMemberId == memberId ||
                    bill.effectivePayers().any { it.memberId == memberId } ||
                    memberId in bill.participantIds ||
                    bill.splits.containsKey(memberId)
            }
            require(!referencedInBill) {
                "Cannot remove ${target.displayName} while they are part of an existing bill."
            }
            val updatedMembers = trip.members.filterNot { it.memberId == memberId }
            require(updatedMembers.isNotEmpty()) { "A trip must have at least one member." }

            val task = db.collection("trips").document(trip.id).update(
                mapOf(
                    "members" to updatedMembers.map { it.toMap() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (isOnline) task.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun joinTripViaQr(tripId: String, joinCode: String): Result<String> {
        val docPath = "trips/$tripId"
        return try {
            val uid = requireUserId()
            val cleanCode = joinCode.trim().uppercase()
            require(tripId.isNotBlank() && cleanCode.length in 6..20) { "Invalid trip QR code." }

            val docRef = db.collection("trips").document(tripId)
            val existingRead = try {
                docRef.get().await()
            } catch (_: Exception) {
                null
            }
            if (existingRead == null || !existingRead.exists()) {
                docRef.update(
                    mapOf(
                        "memberUids" to FieldValue.arrayUnion(uid),
                        "lastJoinCodeAttempt" to cleanCode,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val user = auth.currentUser
            val name = user?.displayName?.takeIf { it.isNotBlank() }
                ?: user?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                ?: "Traveler"
            upsertSyncedTripUser(
                tripId = tripId,
                displayName = name,
                photoUrl = user?.photoUrl?.toString(),
                linkedMemberId = null,
                isOnline = true
            )
            Result.success(tripId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(
                IllegalStateException(
                    "Could not join trip. Verify the QR code is valid and the trip is still active.",
                    e
                )
            )
        }
    }

    suspend fun endTrip(trip: Trip, isOnline: Boolean): Result<Unit> {
        val docPath = "trips/${trip.id}"
        return try {
            val uid = requireUserId()
            require(trip.canManageTripAdmin(uid)) { "Only the primary trip creator can end an active trip." }
            val task = db.collection("trips").document(trip.id).update(
                mapOf(
                    "status" to TripStatus.ENDED.wireValue,
                    "endedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (isOnline) task.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setUserLinkedMember(tripId: String, memberId: String, isOnline: Boolean): Result<Unit> {
        var docPath = "users/unknown/tripLinks/$tripId"
        return try {
            val uid = requireUserId()
            docPath = "users/$uid/tripLinks/$tripId"
            val payload = mapOf(
                "tripId" to tripId,
                "linkedMemberId" to memberId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val task = db.collection("users").document(uid)
                .collection("tripLinks").document(tripId)
                .set(payload)
            if (isOnline) task.await()

            val user = auth.currentUser
            val name = user?.displayName?.takeIf { it.isNotBlank() }
                ?: user?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                ?: "Traveler"
            upsertSyncedTripUser(
                tripId = tripId,
                displayName = name,
                photoUrl = user?.photoUrl?.toString(),
                linkedMemberId = memberId,
                isOnline = isOnline
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    // --- SETTLEMENT RECORDING ---

    suspend fun recordSettlement(
        trip: Trip,
        fromMemberId: String,
        toMemberId: String,
        amountCents: Long,
        note: String,
        isOnline: Boolean
    ): Result<String> {
        val settlementId = generateSafeId("settle")
        val docPath = "trips/${trip.id}/settlements/$settlementId"
        return try {
            val uid = requireUserId()
            require(trip.canEdit(uid)) { "Only the trip creator or co-host can record settlements." }
            require(fromMemberId != toMemberId) { "Payer and recipient must be different members." }
            require(amountCents in 1L..1_000_000_000L) { "Settlement amount must be greater than zero." }

            val payload = mapOf(
                "tripId" to trip.id,
                "creatorUid" to uid,
                "fromMemberId" to fromMemberId,
                "toMemberId" to toMemberId,
                "amountCents" to amountCents,
                "note" to note.trim().take(160),
                "settledAtMillis" to System.currentTimeMillis(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val task = db.collection("trips").document(trip.id)
                .collection("settlements").document(settlementId)
                .set(payload)
            if (isOnline) task.await()
            Result.success(settlementId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSettlement(
        trip: Trip,
        settlementId: String,
        isOnline: Boolean
    ): Result<Unit> {
        val docPath = "trips/${trip.id}/settlements/$settlementId"
        return try {
            val uid = requireUserId()
            require(trip.canEdit(uid)) { "Only the trip creator or co-host can remove recorded settlements." }
            val task = db.collection("trips").document(trip.id)
                .collection("settlements").document(settlementId)
                .delete()
            if (isOnline) task.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- BILL & RECEIPT MUTATIONS (Multi-Payer, Soft-Delete Trash Bin & Creator/Co-Host Lock) ---

    suspend fun saveBill(
        trip: Trip,
        existingBill: Bill?,
        title: String,
        category: BillCategory,
        amountCents: Long,
        locationName: String,
        notes: String = "",
        paidByMemberId: String,
        payers: List<BillPayer> = emptyList(),
        splitMode: SplitMode,
        participantIds: List<String>,
        splits: Map<String, Long>,
        splitPercentages: Map<String, Long>,
        billDateMillis: Long,
        newReceiptBase64: String?,
        removeExistingReceipt: Boolean,
        isOnline: Boolean
    ): Result<String> {
        val billId = existingBill?.id?.ifBlank { null } ?: generateSafeId("bill")
        val billPath = "trips/${trip.id}/bills/$billId"
        return try {
            val uid = requireUserId()
            require(trip.canEdit(uid)) { "Creator Lock: only the creator or a co-host can add or edit bills." }
            val cleanTitle = title.trim().take(120)
            require(cleanTitle.isNotEmpty()) { "Bill title is required." }
            require(amountCents in 1L..1_000_000_000L) { "Amount must be greater than zero." }
            require(participantIds.isNotEmpty()) { "Select at least one participant." }
            require(splits.values.sum() == amountCents) { "Split amounts must equal the bill total." }

            val cleanPayers = payers
                .filter { it.memberId.isNotBlank() && it.amountCents > 0L }
                .take(20)
            val primaryPayerId = cleanPayers.maxByOrNull { it.amountCents }?.memberId
                ?: paidByMemberId
            require(primaryPayerId.isNotBlank()) { "Select at least one payer." }
            if (cleanPayers.isNotEmpty()) {
                require(cleanPayers.sumOf { it.amountCents } == amountCents) {
                    "Split payment amounts must equal the bill total."
                }
            }

            val cleanLocation = locationName.trim().take(160)
            val cleanNotes = notes.trim().take(500)
            val hadReceipt = existingBill?.receiptAttached == true
            val finalReceiptAttached = when {
                !newReceiptBase64.isNullOrBlank() -> true
                removeExistingReceipt -> false
                else -> hadReceipt
            }

            if (!newReceiptBase64.isNullOrBlank()) {
                ReceiptImageProcessor.saveBase64ToLocalCache(appContext, billId, newReceiptBase64)
            } else if (removeExistingReceipt) {
                ReceiptImageProcessor.deleteLocalCache(appContext, billId)
            }

            val billRef = db.collection("trips").document(trip.id).collection("bills").document(billId)
            val receiptRef = db.collection("trips").document(trip.id).collection("receipts").document(billId)

            val batch = db.batch()
            if (existingBill == null) {
                val createPayload = mapOf(
                    "tripId" to trip.id,
                    "creatorUid" to uid,
                    "title" to cleanTitle,
                    "category" to category.wireValue,
                    "amountCents" to amountCents,
                    "locationName" to cleanLocation,
                    "notes" to cleanNotes,
                    "paidByMemberId" to primaryPayerId,
                    "payers" to cleanPayers.map { it.toMap() },
                    "splitMode" to splitMode.wireValue,
                    "participantIds" to participantIds.distinct(),
                    "splits" to splits,
                    "splitPercentages" to if (splitMode == SplitMode.PERCENTAGE) splitPercentages else emptyMap<String, Long>(),
                    "receiptAttached" to finalReceiptAttached,
                    "billDateMillis" to billDateMillis,
                    "deleted" to false,
                    "deletedAtMillis" to 0L,
                    "deletedByUid" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(billRef, createPayload)
            } else {
                val updatePayload = mapOf(
                    "title" to cleanTitle,
                    "category" to category.wireValue,
                    "amountCents" to amountCents,
                    "locationName" to cleanLocation,
                    "notes" to cleanNotes,
                    "paidByMemberId" to primaryPayerId,
                    "payers" to cleanPayers.map { it.toMap() },
                    "splitMode" to splitMode.wireValue,
                    "participantIds" to participantIds.distinct(),
                    "splits" to splits,
                    "splitPercentages" to if (splitMode == SplitMode.PERCENTAGE) splitPercentages else emptyMap<String, Long>(),
                    "receiptAttached" to finalReceiptAttached,
                    "billDateMillis" to billDateMillis,
                    "deleted" to false,
                    "deletedAtMillis" to 0L,
                    "deletedByUid" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.update(billRef, updatePayload)
            }

            if (!newReceiptBase64.isNullOrBlank()) {
                if (hadReceipt) {
                    batch.update(
                        receiptRef,
                        mapOf(
                            "imageBase64" to newReceiptBase64,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    batch.set(
                        receiptRef,
                        mapOf(
                            "tripId" to trip.id,
                            "billId" to billId,
                            "creatorUid" to uid,
                            "imageBase64" to newReceiptBase64,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            } else if (removeExistingReceipt && hadReceipt) {
                batch.delete(receiptRef)
            }

            val commitTask = batch.commit()
            if (isOnline) commitTask.await()
            Result.success(billId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existingBill == null) OperationType.CREATE else OperationType.UPDATE, billPath)
            Result.failure(e)
        }
    }

    /**
     * Soft-deletes a bill into the Creator/Co-Host recovery Trash Bin so accidental deletes
     * can be undone via the 5-second Snackbar or restored before the trip is permanently locked.
     */
    suspend fun softDeleteBill(trip: Trip, bill: Bill, isOnline: Boolean): Result<Unit> {
        val billPath = "trips/${trip.id}/bills/${bill.id}"
        return try {
            val uid = requireUserId()
            require(trip.canEdit(uid)) { "Creator Lock: only the creator or a co-host can delete bills." }
            val task = db.collection("trips").document(trip.id)
                .collection("bills").document(bill.id)
                .update(
                    mapOf(
                        "deleted" to true,
                        "deletedAtMillis" to System.currentTimeMillis(),
                        "deletedByUid" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            if (isOnline) task.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, billPath)
            Result.failure(e)
        }
    }

    suspend fun restoreBill(trip: Trip, bill: Bill, isOnline: Boolean): Result<Unit> {
        val billPath = "trips/${trip.id}/bills/${bill.id}"
        return try {
            val uid = requireUserId()
            require(trip.canEdit(uid)) { "Creator Lock: only the creator or a co-host can restore bills." }
            val task = db.collection("trips").document(trip.id)
                .collection("bills").document(bill.id)
                .update(
                    mapOf(
                        "deleted" to false,
                        "deletedAtMillis" to 0L,
                        "deletedByUid" to "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            if (isOnline) task.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, billPath)
            Result.failure(e)
        }
    }

    suspend fun deleteBill(trip: Trip, bill: Bill, isOnline: Boolean): Result<Unit> {
        val billPath = "trips/${trip.id}/bills/${bill.id}"
        return try {
            val uid = requireUserId()
            require(trip.canEdit(uid)) { "Creator Lock: only the creator or a co-host can permanently delete bills." }
            ReceiptImageProcessor.deleteLocalCache(appContext, bill.id)
            val batch = db.batch()
            batch.delete(db.collection("trips").document(trip.id).collection("bills").document(bill.id))
            if (bill.receiptAttached) {
                batch.delete(db.collection("trips").document(trip.id).collection("receipts").document(bill.id))
            }
            val task = batch.commit()
            if (isOnline) task.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, billPath)
            Result.failure(e)
        }
    }

    suspend fun loadReceiptBitmap(tripId: String, billId: String): Bitmap? {
        ReceiptImageProcessor.loadBitmapFromLocalCache(appContext, billId)?.let { return it }
        val docPath = "trips/$tripId/receipts/$billId"
        return try {
            requireUserId()
            val snapshot = db.collection("trips").document(tripId)
                .collection("receipts").document(billId)
                .get()
                .await()
            val receipt = snapshot.toObject(BillReceipt::class.java) ?: return null
            if (receipt.imageBase64.isBlank()) return null
            ReceiptImageProcessor.saveBase64ToLocalCache(appContext, billId, receipt.imageBase64)
            ReceiptImageProcessor.decodeBase64ToBitmap(receipt.imageBase64)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            null
        }
    }
}
