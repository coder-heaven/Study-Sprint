package com.pranav.study.cet_study_sprint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun <T> Task<T>.awaitLeaderboardTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

internal object LeaderboardPhotos {
    suspend fun bitmap(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE)
        prefs.getString("profile_photo", null)?.let { path ->
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()?.let { return@withContext it }
        }
        val url = prefs.getString("account_photo_url", null) ?: return@withContext null
        if (!url.startsWith("https://")) return@withContext null
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5000; connection.readTimeout = 5000
                val bytes = connection.inputStream.use { it.readBytesUpTo(1_048_576) }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val options = BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 192).coerceAtLeast(1) }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } finally { connection.disconnect() }
        }.getOrNull()
    }
    private fun java.io.InputStream.readBytesUpTo(limit: Int): ByteArray {
        val result = ByteArrayOutputStream(); val buffer = ByteArray(4096)
        while (true) {
            val size = read(buffer); if (size < 0) break
            require(result.size() + size <= limit) { "Profile photo is too large" }
            result.write(buffer, 0, size)
        }
        return result.toByteArray()
    }
    suspend fun encoded(context: Context): String = withContext(Dispatchers.IO) {
        val source = bitmap(context) ?: return@withContext ""
        val side = minOf(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val thumbnail = Bitmap.createScaledBitmap(square, 72, 72, true)
        val bytes = ByteArrayOutputStream().also { thumbnail.compress(Bitmap.CompressFormat.JPEG, 65, it) }.toByteArray()
        Base64.encodeToString(bytes, Base64.NO_WRAP).takeIf { it.length <= 12000 }.orEmpty()
    }
}

internal data class LeaderboardStudent(
    val uid: String, val name: String, val photo: String, val accountType: String,
    val focusMinutes: Long, val quizAttempts: Long, val quizWins: Long,
    val quizCorrect: Long, val quizQuestions: Long
)

internal class LeaderboardRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    val syncMessage = MutableStateFlow<String?>(null)
    private val auth get() = FirebaseAuth.getInstance()
    private val firestore get() = FirebaseFirestore.getInstance()
    private val students get() = firestore.collection("leaderboardStudents")
    private var publishedProfile: String? = null

    suspend fun join() = mutex.withLock {
        check(PrivacyConsent.has(prefs)) { "Please review the privacy notice before sharing your profile." }
        hidePendingProfile()
        val user = auth.currentUser ?: auth.signInAnonymously().awaitLeaderboardTask().user
            ?: error("Could not create a local leaderboard profile.")
        prefs.edit().putString("leaderboard_uid", user.uid).commit()
        publishProfile(user.uid, visible = true)
        if (!PrivacyConsent.has(prefs) || prefs.getBoolean("leaderboard_opted_out", false)) return@withLock
        prefs.edit().putBoolean("leaderboard_opted_out", false)
            .putBoolean("leaderboard_enabled", true).putString("leaderboard_uid", user.uid).apply()
        syncMessage.value = null
    }

    suspend fun leave() {
        PrivacyConsent.revoke(prefs)
        mutex.withLock { hidePendingProfile() }
    }

    private suspend fun hidePendingProfile() {
        if (!prefs.getBoolean(PrivacyConsent.PENDING, false)) return
        val uid = prefs.getString("leaderboard_uid", null)
        if (uid != null) {
            check(auth.currentUser?.uid == uid) { "Reconnect with the original account to remove the old public profile." }
            // Wait for the server: don't claim a public profile was hidden while offline.
            val reference = students.document(uid)
            firestore.runTransaction { transaction ->
                if (transaction.get(reference).exists()) transaction.update(reference,
                    mapOf("visible" to false, "name" to "Student", "photo" to "", "updatedAt" to FieldValue.serverTimestamp()))
            }.awaitLeaderboardTask()
            withContext(Dispatchers.IO) { StudyData.events(context).discardLeaderboardEvents(uid) }
        }
        prefs.edit().putBoolean(PrivacyConsent.PENDING, false).remove("leaderboard_uid").apply()
        publishedProfile = null
    }

    private suspend fun publishProfile(uid: String, visible: Boolean) {
        check(PrivacyConsent.has(prefs) && !prefs.getBoolean("leaderboard_opted_out", false))
        val name = prefs.getString("profile_name", "Student").orEmpty().trim().take(40).ifBlank { "Student" }
        val type = if (auth.currentUser?.isAnonymous == true) "local" else "google"
        val profileKey = "$uid|$name|${prefs.getString("profile_photo", "")}|${prefs.getString("account_photo_url", "")}|$type|$visible"
        if (publishedProfile == profileKey) return
        val photo = LeaderboardPhotos.encoded(context)
        check(PrivacyConsent.has(prefs) && !prefs.getBoolean("leaderboard_opted_out", false))
        val reference = students.document(uid)
        firestore.runTransaction { transaction ->
            val previous = transaction.get(reference)
            val data = mutableMapOf<String, Any>("name" to name, "photo" to photo, "accountType" to type,
                "visible" to visible, "updatedAt" to FieldValue.serverTimestamp())
            if (!previous.exists()) {
                data.putAll(mapOf("focusMinutes" to 0L, "quizAttempts" to 0L, "quizWins" to 0L,
                    "quizCorrect" to 0L, "quizQuestions" to 0L, "lastEventId" to ""))
                transaction.set(reference, data)
            } else transaction.update(reference, data)
        }.awaitLeaderboardTask()
        publishedProfile = profileKey
    }

    suspend fun sync() = mutex.withLock {
        // Older versions shared automatically. Stop publishing until explicit consent,
        // and remove the old public name/photo when the original account reconnects.
        if (!PrivacyConsent.has(prefs) && prefs.getBoolean("leaderboard_enabled", false)) PrivacyConsent.revoke(prefs)
        hidePendingProfile()
        if (!LeaderboardParticipation.shouldConnect(
                prefs.getBoolean("onboarding_v3", false),
                prefs.getBoolean("leaderboard_opted_out", false), prefs.getInt(PrivacyConsent.KEY, 0))) return@withLock
        val user = auth.currentUser ?: auth.signInAnonymously().awaitLeaderboardTask().user
            ?: error("Could not connect your student profile.")
        val uid = user.uid
        // Store the authenticated identity before the network write so activity queues
        // locally even if the initial profile publication is temporarily offline.
        prefs.edit().putBoolean("leaderboard_enabled", true).putString("leaderboard_uid", uid).apply()
        publishProfile(uid, visible = true)
        val outbox = withContext(Dispatchers.IO) { StudyData.events(context).pendingLeaderboardEvents(uid) }
        outbox.forEach { item ->
            if (!PrivacyConsent.has(prefs) || prefs.getBoolean("leaderboard_opted_out", false)) return@withLock
            val entry = students.document(uid)
            val event = entry.collection("events").document(item.id)
            firestore.runTransaction { transaction ->
                val recorded = transaction.get(event)
                val previous = transaction.get(entry)
                if (!recorded.exists()) {
                    check(previous.exists()) { "Join the leaderboard before uploading results." }
                    val delta = item.delta
                    val values = mapOf("focusMinutes" to delta.focusMinutes, "quizAttempts" to delta.quizAttempts,
                        "quizWins" to delta.quizWins, "quizCorrect" to delta.quizCorrect, "quizQuestions" to delta.quizQuestions)
                    transaction.set(event, values + ("createdAt" to FieldValue.serverTimestamp()))
                    val totals = values.mapValues { (key, value) -> (previous.getLong(key) ?: 0L) + value }
                    transaction.update(entry, totals + mapOf("lastEventId" to item.id, "updatedAt" to FieldValue.serverTimestamp()))
                }
            }.awaitLeaderboardTask()
            withContext(Dispatchers.IO) { StudyData.events(context).acknowledgeLeaderboardEvent(uid, item.id) }
        }
        syncMessage.value = null
    }

    suspend fun runSyncLoop() {
        while (true) {
            try { sync() } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                syncMessage.value = message(error)
            }
            delay(15_000)
        }
    }

    fun listen(metric: String, onResult: (List<LeaderboardStudent>, Boolean, String?) -> Unit): ListenerRegistration {
        val ranked = students.whereEqualTo("visible", true).orderBy(metric, Query.Direction.DESCENDING)
        val query = if (metric == "quizWins") ranked.orderBy("quizAttempts", Query.Direction.DESCENDING) else ranked
        return query.limit(100).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) onResult(emptyList(), true, message(error))
                else if (snapshot != null) onResult(snapshot.documents.map { document ->
                    LeaderboardStudent(document.id, document.getString("name") ?: "Student", document.getString("photo").orEmpty(),
                        document.getString("accountType") ?: "local", document.getLong("focusMinutes") ?: 0,
                        document.getLong("quizAttempts") ?: 0, document.getLong("quizWins") ?: 0,
                        document.getLong("quizCorrect") ?: 0, document.getLong("quizQuestions") ?: 0)
                }.filter { if (metric == "focusMinutes") it.focusMinutes > 0 else it.quizAttempts > 0 }, snapshot.metadata.isFromCache, null)
            }
    }

    fun message(error: Throwable): String = when {
        prefs.getBoolean(PrivacyConsent.PENDING, false) ->
            "Public profile removal is pending. Reconnect with the original account to complete it."
        error is com.google.firebase.auth.FirebaseAuthException && error.errorCode == "ERROR_OPERATION_NOT_ALLOWED" ->
            "Local leaderboard sign-in is not enabled yet. The app owner needs to enable Anonymous sign-in in Firebase."
        error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Leaderboard access is not enabled yet. The app owner needs to publish the Firebase leaderboard rules."
        error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            "The leaderboard needs its Firebase database indexes. Please contact the app owner."
        error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
            "Offline. Your new results are saved on this phone and will upload when you reconnect."
        else -> "Could not connect to the leaderboard. Check your connection and try again."
    }
}

internal object Leaderboards {
    private var instance: LeaderboardRepository? = null
    @Synchronized fun repository(context: Context): LeaderboardRepository = instance
        ?: LeaderboardRepository(context.applicationContext).also { instance = it }
}
