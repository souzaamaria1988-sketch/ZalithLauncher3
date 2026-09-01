package com.zalith.launcher.perf

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/**
 * Cache comum de shaderpacks: o zip é guardado uma única vez por
 * conteúdo (hash sha1) e ativado por cópia no shaderpacks/ de cada
 * instância. Cota com eviction LRU — passou do limite, os packs menos
 * usados saem do cache (o que está ativo na instância não é tocado).
 */
class ShaderCache(
    private val root: File,
    private val quotaBytes: Long = 2L * 1024 * 1024 * 1024
) {

    class PackEntry(
        val instanceId: String,
        val name: String,
        val hash: String,
        var lastUsed: Long
    )

    private val objectsDir = File(root, "objects")
    private val metaFile = File(root, "meta.json")
    private val entries = ArrayList<PackEntry>()

    init { load() }

    /** Guarda o pack (validando o zip) e devolve o hash do conteúdo. */
    fun install(instanceId: String, name: String, zipFile: File): String {
        ZipFile(zipFile).use { zip ->
            var hasShaders = false
            val e = zip.entries()
            while (e.hasMoreElements()) {
                if (e.nextElement().name.contains("shaders/")) {
                    hasShaders = true
                    break
                }
            }
            if (!hasShaders) {
                throw IllegalArgumentException("não parece um shaderpack (sem shaders/): " + name)
            }
        }
        val hash = sha1Of(zipFile)
            ?: throw IllegalArgumentException("não consegui ler o zip: " + name)
        val dest = File(objectsDir, hash + ".zip")
        if (!dest.exists() || dest.length() != zipFile.length()) {
            dest.parentFile?.mkdirs()
            zipFile.copyTo(dest, overwrite = true)
        }
        entries.removeAll { it.instanceId == instanceId && it.name == name }
        entries.add(PackEntry(instanceId, name, hash, System.currentTimeMillis()))
        persist()
        enforceQuota()
        return hash
    }

    /** Copia o pack para o shaderpacks/ da instância e marca como usado. */
    fun activate(instanceId: String, name: String, gameDir: File): Boolean {
        val entry = entries.firstOrNull { it.instanceId == instanceId && it.name == name }
            ?: return false
        val src = File(objectsDir, entry.hash + ".zip")
        if (!src.exists()) return false
        entry.lastUsed = System.currentTimeMillis()
        persist()
        val dest = File(gameDir, "shaderpacks/" + name + ".zip")
        dest.parentFile?.mkdirs()
        src.copyTo(dest, overwrite = true)
        return true
    }

    fun list(instanceId: String): List<PackEntry> =
        entries.filter { it.instanceId == instanceId }

    fun remove(instanceId: String, name: String) {
        entries.removeAll { it.instanceId == instanceId && it.name == name }
        persist()
        enforceQuota()
    }

    fun totalBytes(): Long =
        objectsDir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun enforceQuota() {
        var total = totalBytes()
        if (total <= quotaBytes) {
            sweepOrphans()
            return
        }
        val order = entries.sortedBy { it.lastUsed }
        var i = 0
        while (total > quotaBytes && i < order.size) {
            val e = order[i]
            val f = File(objectsDir, e.hash + ".zip")
            if (f.exists()) {
                total -= f.length()
                f.delete()
            }
            entries.remove(e)
            i++
        }
        persist()
        sweepOrphans()
    }

    private fun sweepOrphans() {
        val referenced = entries.map { it.hash + ".zip" }.toHashSet()
        objectsDir.listFiles()?.forEach { f ->
            if (f.isFile && !referenced.contains(f.name)) f.delete()
        }
    }

    private fun load() {
        entries.clear()
        if (!metaFile.exists()) return
        try {
            val arr = JSONObject(metaFile.readText(Charsets.UTF_8)).optJSONArray("packs") ?: return
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                entries.add(PackEntry(
                    o.getString("instance"),
                    o.getString("name"),
                    o.getString("hash"),
                    o.optLong("lastUsed", 0L)
                ))
            }
        } catch (e: Exception) {
            entries.clear()
        }
    }

    private fun persist() {
        root.mkdirs()
        val arr = JSONArray()
        for (e in entries) {
            arr.put(JSONObject()
                .put("instance", e.instanceId)
                .put("name", e.name)
                .put("hash", e.hash)
                .put("lastUsed", e.lastUsed))
        }
        metaFile.writeText(JSONObject().put("packs", arr).toString(2), Charsets.UTF_8)
    }

    private fun sha1Of(file: File): String? {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-1")
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
