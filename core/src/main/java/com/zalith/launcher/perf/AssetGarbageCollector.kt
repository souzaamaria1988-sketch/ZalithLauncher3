package com.zalith.launcher.perf

import org.json.JSONObject
import java.io.File

/**
 * Coletor de lixo do repositório de assets. Objetos são endereçados
 * por sha1 e compartilhados entre instâncias; quando nenhuma
 * instância referencia mais um hash (versão desinstalada), o arquivo
 * vira órfão — este coletor devolve o espaço para o aparelho.
 */
class AssetGarbageCollector(private val objectsDir: File) {

    class Report(val scanned: Int, val deleted: Int, val freedBytes: Long) {
        override fun toString(): String =
            scanned.toString() + " objetos · " + deleted + " órfãos · " +
                    (freedBytes / 1024 / 1024) + "MB liberados"
    }

    fun collect(referencedHashes: Set<String>, dryRun: Boolean = false): Report {
        var scanned = 0
        var deleted = 0
        var freed = 0L
        val prefixes = objectsDir.listFiles { f -> f.isDirectory } ?: return Report(0, 0, 0)
        for (prefix in prefixes) {
            val files = prefix.listFiles() ?: continue
            for (f in files) {
                if (!f.isFile) continue
                scanned++
                if (!referencedHashes.contains(f.name)) {
                    freed += f.length()
                    deleted++
                    if (!dryRun) f.delete()
                }
            }
        }
        if (!dryRun) {
            for (p in prefixes) {
                if (p.listFiles()?.isEmpty() == true) p.delete()
            }
        }
        return Report(scanned, deleted, freed)
    }

    companion object {
        /** Todos os hashes que um índice de assets referencia. */
        fun referencedFromIndex(indexJson: JSONObject): Set<String> {
            val out = HashSet<String>()
            val objs = indexJson.optJSONObject("objects") ?: return out
            val keys = objs.keys()
            while (keys.hasNext()) {
                val o = objs.optJSONObject(keys.next()) ?: continue
                val hash = o.optString("hash")
                if (hash.isNotEmpty()) out.add(hash)
            }
            return out
        }
    }
}
