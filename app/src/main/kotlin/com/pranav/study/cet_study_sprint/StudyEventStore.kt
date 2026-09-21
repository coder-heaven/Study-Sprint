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
internal class StudyEventStore(context: Context) : SQLiteOpenHelper(context, "study_history.db", null, 2) {
    val revision = MutableStateFlow(0)
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, at_ms INTEGER NOT NULL, duration_ms INTEGER NOT NULL DEFAULT 0, subject TEXT NOT NULL DEFAULT '', attempted INTEGER NOT NULL DEFAULT 0, correct INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX events_by_time ON events(at_ms)")
        db.execSQL("CREATE TABLE notes (day TEXT PRIMARY KEY, body TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("CREATE TABLE IF NOT EXISTS notes (day TEXT PRIMARY KEY, body TEXT NOT NULL)")
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
        val row = ContentValues().apply {
            put("kind", kind); put("at_ms", System.currentTimeMillis())
            put("duration_ms", durationMs); put("subject", subject)
            put("attempted", attempted); put("correct", correct)
        }
        writableDatabase.insert("events", null, row)
        revision.value++
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
