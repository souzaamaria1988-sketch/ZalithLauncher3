package com.zalith.launcher.logs

import java.io.File
import java.io.RandomAccessFile

/**
 * Cauda do latest.log: acha o log mais recente entre as instâncias e
 * repassa as linhas novas a cada 500ms. Detecção de rotação inclusa
 * (log encolheu = recomeça do zero). Zero dependências.
 */
class LogTail(private val instancesRoot: File) {

    @Volatile private var running = false

    fun stop() {
        running = false
    }

    fun start(onLine: (String) -> Unit) {
        stop()
        running = true
        Thread({
            var current: File? = null
            var pos = 0L
            while (running) {
                try {
                    val log = newestLog()
                    if (log != current) {
                        current = log
                        pos = 0L
                    }
                    if (current != null && current.exists()) {
                        val len = current.length()
                        if (len < pos) pos = 0L
                        if (len > pos) {
                            RandomAccessFile(current, "r").use { raf ->
                                raf.seek(pos)
                                val buf = ByteArray((len - pos).toInt())
                                raf.readFully(buf)
                                val text = String(buf, Charsets.UTF_8)
                                val lastNl = text.lastIndexOf(10.toChar())
                                if (lastNl >= 0) {
                                    for (line in text.substring(0, lastNl).split(10.toChar())) {
                                        if (line.isNotBlank()) onLine(line.trim())
                                    }
                                    pos += lastNl + 1
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // log girou no meio da leitura: recomeça no próximo ciclo
                }
                Thread.sleep(500)
            }
        }, "zl3-logtail").apply { isDaemon = true }.start()
    }

    fun newestLog(): File? {
        var best: File? = null
        var bestTime = 0L
        val dirs = instancesRoot.listFiles { f -> f.isDirectory } ?: return null
        for (d in dirs) {
            val f = File(d, ".minecraft/logs/latest.log")
            if (f.exists() && f.lastModified() > bestTime) {
                bestTime = f.lastModified()
                best = f
            }
        }
        return best
    }

    companion object {
        fun levelOf(line: String): String = when {
            line.contains(" FATAL ") || line.contains(" ERROR ") -> "E"
            line.contains(" WARN ") -> "W"
            else -> "I"
        }
    }
}
