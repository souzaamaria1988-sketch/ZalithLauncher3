package com.zalith.launcher.play

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * Tempo de jogo por instância: sessões com início e fim persistidas
 * em playtime.json. A tela inicial mostra hoje, total e por instância.
 */
class PlayTimeTracker(private val baseDir: File) {

    class Session(val instanceId: String, val start: Long, val end: Long)

    private val file = File(baseDir, "playtime.json")
    private val sessions = ArrayList<Session>()

    init { load() }

    @Synchronized
    fun startSession(instanceId: String) {
        if (sessions.any { it.instanceId == instanceId && it.end == 0L }) return
        sessions.add(Session(instanceId, System.currentTimeMillis(), 0L))
        persist()
    }

    @Synchronized
    fun endSession(instanceId: String) {
        val i = sessions.indexOfLast { it.instanceId == instanceId && it.end == 0L }
        if (i < 0) return
        val s = sessions.removeAt(i)
        sessions.add(Session(s.instanceId, s.start, System.currentTimeMillis()))
        persist()
    }

    @Synchronized
    fun totalOf(instanceId: String): Long =
        sessions.asSequence()
            .filter { it.instanceId == instanceId && it.end > it.start }
            .sumOf { it.end - it.start }

    @Synchronized
    fun totalAll(): Long =
        sessions.asSequence()
            .filter { it.end > it.start }
            .sumOf { it.end - it.start }

    @Synchronized
    fun todayAll(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        val now = System.currentTimeMillis()
        return sessions.asSequence()
            .filter { it.start >= startOfDay }
            .sumOf { (if (it.end > it.start) it.end else now) - it.start }
    }

    @Synchronized
    fun lastPlayed(instanceId: String): Long =
        sessions.filter { it.instanceId == instanceId }
            .maxOfOrNull { if (it.end > 0) it.end else it.start } ?: 0L

    private fun load() {
        if (!file.exists()) return
        try {
            val arr = JSONObject(file.readText()).optJSONArray("sessions") ?: return
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                sessions.add(Session(o.getString("id"), o.getLong("start"), o.optLong("end", 0L)))
            }
        } catch (e: Exception) {
            sessions.clear()
        }
    }

    private fun persist() {
        baseDir.mkdirs()
        val arr = JSONArray()
        for (s in sessions) {
            arr.put(JSONObject()
                .put("id", s.instanceId)
                .put("start", s.start)
                .put("end", s.end))
        }
        file.writeText(JSONObject().put("sessions", arr).toString())
    }
}
