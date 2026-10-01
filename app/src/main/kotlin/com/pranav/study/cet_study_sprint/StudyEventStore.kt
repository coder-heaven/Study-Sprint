package com.pranav.study.cet_study_sprint

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow

internal data class StudyTotals(
    val focusedMinutes: Long = 0,
    val sessions: Int = 0,
    val tasks: Int = 0,
    val questions: Int = 0,
    val correct: Int = 0,
    val dailyMinutes: List<Long> = List(7) { 0 }
)

/** Historical events are separate from legacy preference counters; unknown old durations are never invented. */
internal data class ChapterNote(val body: String = "", val pdfName: String = "", val pdfFile: String = "")

internal data class PendingLeaderboardEvent(val id: String, val uid: String, val delta: LeaderboardDelta)

internal class StudyEventStore(context: Context) : SQLiteOpenHelper(context, "study_history.db", null, 4) {
    private val appContext = context.applicationContext
    val revision = MutableStateFlow(0)
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, at_ms INTEGER NOT NULL, duration_ms INTEGER NOT NULL DEFAULT 0, subject TEXT NOT NULL DEFAULT '', attempted INTEGER NOT NULL DEFAULT 0, correct INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX events_by_time ON events(at_ms)")
        db.execSQL("CREATE TABLE notes (day TEXT PRIMARY KEY, body TEXT NOT NULL)")
        db.execSQL("CREATE TABLE chapter_notes (chapter_key TEXT PRIMARY KEY, body TEXT NOT NULL DEFAULT '', pdf_name TEXT NOT NULL DEFAULT '', pdf_file TEXT NOT NULL DEFAULT '')")
        createLeaderboardTables(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("CREATE TABLE IF NOT EXISTS notes (day TEXT PRIMARY KEY, body TEXT NOT NULL)")
        if (oldVersion < 3) db.execSQL("CREATE TABLE IF NOT EXISTS chapter_notes (chapter_key TEXT PRIMARY KEY, body TEXT NOT NULL DEFAULT '', pdf_name TEXT NOT NULL DEFAULT '', pdf_file TEXT NOT NULL DEFAULT '')")
        if (oldVersion < 4) createLeaderboardTables(db)
    }
    private fun createLeaderboardTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE leaderboard_outbox (event_id TEXT NOT NULL, uid TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(uid, event_id))")
        db.execSQL("CREATE TABLE quiz_completions (attempt_id TEXT PRIMARY KEY)")
    }
    fun chapterNote(key: String): ChapterNote = readableDatabase.rawQuery(
        "SELECT body, pdf_name, pdf_file FROM chapter_notes WHERE chapter_key = ?", arrayOf(key)
    ).use { if (it.moveToFirst()) ChapterNote(it.getString(0), it.getString(1), it.getString(2)) else ChapterNote() }
    fun saveChapterNote(key: String, note: ChapterNote) {
        val row = ContentValues().apply {
            put("chapter_key", key); put("body", note.body)
            put("pdf_name", note.pdfName); put("pdf_file", note.pdfFile)
        }
        writableDatabase.insertWithOnConflict("chapter_notes", null, row, SQLiteDatabase.CONFLICT_REPLACE)
        revision.value++
    }
    fun saveNote(day: String, body: String) {
        val row = ContentValues().apply { put("day", day); put("body", body) }
        writableDatabase.insertWithOnConflict("notes", null, row, SQLiteDatabase.CONFLICT_REPLACE)
        revision.value++
    }
    fun note(day: String): String = readableDatabase.rawQuery("SELECT body FROM notes WHERE day = ?", arrayOf(day)).use {
        if (it.moveToFirst()) it.getString(0) else ""
    }
    fun notes(): List<Pair<String, String>> = readableDatabase.rawQuery("SELECT day, body FROM notes ORDER BY day DESC", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) }
    }
    fun recordFocus(durationMs: Long, subject: String) = insert("focus", durationMs, subject, 0, 0)
    fun recordTask() = insert("task", 0, "", 0, 0)
    fun recordPractice(attempted: Int, correct: Int) = insert("practice", 0, "", attempted, correct)
    fun recordQuiz(attemptId: String, canonicalSet: String, attempted: Int, correct: Int) {
        val delta = LeaderboardScoring.quiz(attempted, correct) ?: return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val completion = ContentValues().apply { put("attempt_id", attemptId) }
            if (db.insertWithOnConflict("quiz_completions", null, completion, SQLiteDatabase.CONFLICT_IGNORE) != -1L) {
                insertRow(db, "practice", 0, "", attempted, correct)
                enqueue(db, LeaderboardScoring.quizEventId(canonicalSet, System.currentTimeMillis()), delta)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        revision.value++
    }
    private fun enqueue(db: SQLiteDatabase, id: String, delta: LeaderboardDelta) {
        val prefs = appContext.getSharedPreferences("study_sprint", Context.MODE_PRIVATE)
        val uid = prefs.getString("leaderboard_uid", null) ?: return
        if (!PrivacyConsent.has(prefs) || !prefs.getBoolean("leaderboard_enabled", false) ||
            runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }.getOrNull() != uid) return
        val payload = org.json.JSONObject().apply {
            put("focusMinutes", delta.focusMinutes); put("quizAttempts", delta.quizAttempts)
            put("quizWins", delta.quizWins); put("quizCorrect", delta.quizCorrect); put("quizQuestions", delta.quizQuestions)
        }
        val row = ContentValues().apply { put("event_id", id); put("uid", uid); put("payload", payload.toString()) }
        db.insertWithOnConflict("leaderboard_outbox", null, row, SQLiteDatabase.CONFLICT_IGNORE)
    }
    fun pendingLeaderboardEvents(uid: String): List<PendingLeaderboardEvent> = readableDatabase.rawQuery(
        "SELECT event_id, payload FROM leaderboard_outbox WHERE uid = ? ORDER BY rowid LIMIT 100", arrayOf(uid)
    ).use { cursor -> buildList {
        while (cursor.moveToNext()) {
            val item = org.json.JSONObject(cursor.getString(1))
            add(PendingLeaderboardEvent(cursor.getString(0), uid, LeaderboardDelta(item.getLong("focusMinutes"),
                item.getLong("quizAttempts"), item.getLong("quizWins"), item.getLong("quizCorrect"), item.getLong("quizQuestions"))))
        }
    } }
    fun acknowledgeLeaderboardEvent(uid: String, id: String) {
        writableDatabase.delete("leaderboard_outbox", "uid = ? AND event_id = ?", arrayOf(uid, id))
    }
    fun discardLeaderboardEvents(uid: String) {
        writableDatabase.delete("leaderboard_outbox", "uid = ?", arrayOf(uid))
    }
    fun recordLimitEvent(kind: String, pkg: String) {
        require(kind in setOf("limit_reached", "blocked", "bypass"))
        insert(kind, 0, pkg, 0, 0)
    }
    fun limitCounts(days: Int): Map<String, Int> {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, -(days - 1))
        }
        return readableDatabase.rawQuery("SELECT kind, COUNT(*) FROM events WHERE at_ms >= ? AND kind IN ('limit_reached','blocked','bypass') GROUP BY kind", arrayOf(calendar.timeInMillis.toString())).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getInt(1)) }
        }
    }
    private fun insert(kind: String, durationMs: Long, subject: String, attempted: Int, correct: Int) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            insertRow(db, kind, durationMs, subject, attempted, correct)
            if (kind == "focus") LeaderboardScoring.focus(durationMs)?.let {
                enqueue(db, "focus_${java.util.UUID.randomUUID()}", it)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        revision.value++
    }
    private fun insertRow(db: SQLiteDatabase, kind: String, durationMs: Long, subject: String, attempted: Int, correct: Int) {
        val row = ContentValues().apply {
            put("kind", kind); put("at_ms", System.currentTimeMillis())
            put("duration_ms", durationMs); put("subject", subject)
            put("attempted", attempted); put("correct", correct)
        }
        db.insertOrThrow("events", null, row)
    }
    fun subjectMinutes(days: Int): Map<String, Long> {
        val since = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, -(days - 1))
        }.timeInMillis
        return readableDatabase.rawQuery("SELECT subject, SUM(duration_ms) FROM events WHERE kind = 'focus' AND at_ms >= ? GROUP BY subject", arrayOf(since.toString())).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0).ifBlank { "General" }, cursor.getLong(1) / 60000) }
        }
    }
    fun streak(): Int {
        val days = mutableSetOf<java.time.LocalDate>()
        readableDatabase.rawQuery("SELECT at_ms FROM events WHERE (kind = 'focus' AND duration_ms >= 300000) OR kind IN ('task', 'practice')", null).use { cursor ->
            while (cursor.moveToNext()) days += java.time.Instant.ofEpochMilli(cursor.getLong(0)).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        }
        var date = java.time.LocalDate.now()
        if (date !in days) date = date.minusDays(1)
        var count = 0
        while (date in days) { count++; date = date.minusDays(1) }
        return count
    }
    fun totals(days: Int): StudyTotals {
        val now = java.util.Calendar.getInstance()
        now.set(java.util.Calendar.HOUR_OF_DAY, 0)
        now.set(java.util.Calendar.MINUTE, 0)
        now.set(java.util.Calendar.SECOND, 0)
        now.set(java.util.Calendar.MILLISECOND, 0)
        now.add(java.util.Calendar.DAY_OF_YEAR, -(days - 1))
        val start = now.timeInMillis
        var minutes = 0L; var sessions = 0; var tasks = 0; var questions = 0; var correct = 0
        val daily = LongArray(7)
        readableDatabase.rawQuery(
            "SELECT kind, at_ms, duration_ms, attempted, correct FROM events WHERE at_ms >= ? ORDER BY at_ms",
            arrayOf(start.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                when (cursor.getString(0)) {
                    "focus" -> {
                        val value = cursor.getLong(2) / 60000
                        minutes += value; sessions++
                        val day = java.time.Instant.ofEpochMilli(cursor.getLong(1)).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        val offset = java.time.temporal.ChronoUnit.DAYS.between(day, java.time.LocalDate.now()).toInt()
                        if (offset in 0..6) daily[6 - offset] += value
                    }
                    "task" -> tasks++
                    "practice" -> { questions += cursor.getInt(3); correct += cursor.getInt(4) }
                }
            }
        }
        return StudyTotals(minutes, sessions, tasks, questions, correct, daily.toList())
    }
}

internal object StudyData {
    private var store: StudyEventStore? = null
    @Synchronized fun events(context: Context): StudyEventStore =
        store ?: StudyEventStore(context.applicationContext).also { store = it }
}
