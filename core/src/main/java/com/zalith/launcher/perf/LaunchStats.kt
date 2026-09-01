package com.zalith.launcher.perf

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Métricas por inicialização de jogo: quanto tempo levou resolver,
 * baixar, verificar e lançar. Histórico de 30 sessões por instância
 * em stats.json — a UI mostra última, média e melhor.
 */
class LaunchStats(private val baseDir: File) {

    class Timings(
        val resolveMs: Long,
        val downloadMs: Long,
        val verifyMs: Long,
        val launchMs: Long,
        val totalMs: Long,
        val skippedFiles: Int = 0,
        val hashedFiles: Int = 0,
        val modsCount: Int = 0,
        val heapMb: Int = 0
    )

    class Summary(
        val launches: Int,
        val avgTotalMs: Long,
        val lastTotalMs: Long,
        val bestTotalMs: Long,
        val avgDownloadMs: Long
    )

    private val file = File(baseDir, "stats.json")
    private val history = HashMap<String, MutableList<Timings>>()

    init { load() }

    fun record(instanceId: String, timings: Timings) {
        val list = history.getOrPut(instanceId) { ArrayList() }
        list.add(timings)
        while (list.size > CAP) list.removeAt(0)
        persist()
    }

    fun summary(instanceId: String): Summary? {
        val list = history[instanceId] ?: return null
        if (list.isEmpty()) return null
        return Summary(
            list.size,
            list.map { it.totalMs }.average().toLong(),
            list.last().totalMs,
            list.map { it.totalMs }.minOrNull() ?: 0L,
            list.map { it.downloadMs }.average().toLong()
        )
    }

    private fun load() {
        history.clear()
        if (!file.exists()) return
        try {
            val arr = JSONObject(file.readText(Charsets.UTF_8)).optJSONArray("stats") ?: return
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("instance")
                val ts = o.getJSONArray("timings")
                val list = history.getOrPut(id) { ArrayList() }
                for (j in 0 until ts.length()) {
                    val t = ts.getJSONObject(j)
                    list.add(Timings(
                        t.optLong("resolveMs"),
                        t.optLong("downloadMs"),
                        t.optLong("verifyMs"),
                        t.optLong("launchMs"),
                        t.optLong("totalMs"),
                        t.optInt("skippedFiles"),
                        t.optInt("hashedFiles"),
                        t.optInt("modsCount"),
                        t.optInt("heapMb")
                    ))
                }
            }
        } catch (e: Exception) {
            history.clear()
        }
    }

    private fun persist() {
        baseDir.mkdirs()
        val arr = JSONArray()
        for ((id, list) in history) {
            val ts = JSONArray()
            for (t in list) {
                ts.put(JSONObject()
                    .put("resolveMs", t.resolveMs)
                    .put("downloadMs", t.downloadMs)
                    .put("verifyMs", t.verifyMs)
                    .put("launchMs", t.launchMs)
                    .put("totalMs", t.totalMs)
                    .put("skippedFiles", t.skippedFiles)
                    .put("hashedFiles", t.hashedFiles)
                    .put("modsCount", t.modsCount)
                    .put("heapMb", t.heapMb))
            }
            arr.put(JSONObject().put("instance", id).put("timings", ts))
        }
        file.writeText(JSONObject().put("stats", arr).toString(2), Charsets.UTF_8)
    }

    companion object { private const val CAP = 30 }
}
