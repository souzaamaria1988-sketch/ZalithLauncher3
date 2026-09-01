package com.zalith.launcher.perf

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Verificação incremental.
 *
 * O Downloader já evita re-baixar o que está íntegro — mas conferir
 * integridade custa um SHA-1 por arquivo, e uma instalação tem
 * milhares deles (assets, libs). Este módulo grava size + mtime de
 * cada arquivo já conferido: enquanto os dois baterem, o hash é
 * pulado. Re-hash só quando o arquivo muda de verdade.
 *
 * Ganho típico: verificação de ~3 mil arquivos cai de segundos
 * (hash em tudo) para milissegundos (stat em tudo).
 */
class IncrementalVerifier(private val stateFile: File) {

    class Task(val file: File, val sha1: String?, val expectedSize: Long = -1L)

    class Verdict(
        val checked: Int,
        val ok: Int,
        val failed: List<Task>,
        val hashesSkipped: Int,
        val hashesComputed: Int,
        val hashTimeMs: Long
    ) {
        val allOk: Boolean get() = failed.isEmpty()
    }

    /** caminho absoluto -> [size, mtime] */
    private val records = HashMap<String, LongArray>()

    fun load() {
        records.clear()
        if (!stateFile.exists()) return
        try {
            val files = JSONObject(stateFile.readText(Charsets.UTF_8)).optJSONObject("files") ?: return
            val keys = files.keys()
            while (keys.hasNext()) {
                val path = keys.next()
                val arr = files.optJSONArray(path) ?: continue
                if (arr.length() == 2) {
                    records[path] = longArrayOf(arr.optLong(0), arr.optLong(1))
                }
            }
        } catch (e: Exception) {
            records.clear()
        }
    }

    fun commit() {
        stateFile.parentFile?.mkdirs()
        val files = JSONObject()
        for ((path, sm) in records) {
            files.put(path, JSONArray().put(sm[0]).put(sm[1]))
        }
        stateFile.writeText(
            JSONObject().put("files", files).put("savedAt", System.currentTimeMillis()).toString(),
            Charsets.UTF_8
        )
    }

    /**
     * Verifica a lista. repair=true apaga o que está corrompido para o
     * Downloader re-baixar na próxima passada.
     */
    fun verify(tasks: List<Task>, repair: Boolean = false): Verdict {
        val failed = ArrayList<Task>()
        var ok = 0
        var skipped = 0
        var computed = 0
        val t0 = System.currentTimeMillis()
        for (t in tasks) {
            val f = t.file
            if (!f.exists()) {
                failed.add(t)
                continue
            }
            if (t.sha1 == null) {
                if (t.expectedSize > 0 && f.length() != t.expectedSize) {
                    failed.add(t)
                    if (repair) f.delete()
                } else {
                    ok++
                }
                continue
            }
            val rec = records[f.absolutePath]
            if (rec != null && rec[0] == f.length() && rec[1] == f.lastModified()) {
                ok++
                skipped++
                continue
            }
            computed++
            val hash = sha1Of(f)
            if (hash == t.sha1) {
                records[f.absolutePath] = longArrayOf(f.length(), f.lastModified())
                ok++
            } else {
                failed.add(t)
                records.remove(f.absolutePath)
                if (repair) f.delete()
            }
        }
        return Verdict(tasks.size, ok, failed, skipped, computed, System.currentTimeMillis() - t0)
    }

    fun sha1Of(file: File): String? {
        if (!file.exists()) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-1")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
            val out = StringBuilder(40)
            for (b in digest.digest()) {
                out.append(Integer.toHexString((b.toInt() and 0xFF) + 0x100).substring(1))
            }
            out.toString()
        } catch (e: Exception) {
            null
        }
    }
}
