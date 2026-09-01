package com.zalith.launcher.perf

import java.io.File

/**
 * Analisa o logs/latest.log da instância: níveis, pausas de GC (com
 * -Xlog:gc nas flags da fase 3) e marcadores de lag conhecidos.
 * A UI usa isso no painel de diagnóstico.
 */
class GameLogAnalyzer(private val gameDir: File) {

    class Digest(
        val lines: Int,
        val warnings: Int,
        val errors: Int,
        val fatals: Int,
        val gcPauses: Int,
        val gcTotalMs: Double,
        val lagMarkers: List<String>
    )

    fun analyzeLatest(): Digest? {
        val log = File(gameDir, "logs/latest.log")
        if (!log.exists()) return null
        return analyze(log)
    }

    fun analyze(log: File): Digest {
        var lines = 0
        var warn = 0
        var err = 0
        var fatal = 0
        var pauses = 0
        var gcMs = 0.0
        val lag = ArrayList<String>()
        try {
            log.bufferedReader().useLines { seq ->
                for (line in seq) {
                    lines++
                    if (line.contains(" FATAL ")) fatal++
                    else if (line.contains(" ERROR ")) err++
                    else if (line.contains(" WARN ")) warn++
                    if (line.startsWith("[gc") || line.contains("Pause ")) {
                        val ms = extractGcMs(line)
                        if (ms != null) {
                            pauses++
                            gcMs += ms
                        }
                    }
                    val marker = lagMarker(line)
                    if (marker != null && lag.size < 20) lag.add(marker)
                }
            }
        } catch (e: Exception) {
            // log parcial durante a execução: mostra o que leu
        }
        return Digest(lines, warn, err, fatal, pauses, gcMs, lag)
    }

    /** Formato -Xlog:gc: "… 512M->128M(1024M) 3.456ms" -> 3.456 */
    private fun extractGcMs(line: String): Double? {
        val idx = line.lastIndexOf("ms")
        if (idx < 2) return null
        var start = idx - 1
        while (start >= 0 && !line[start].isWhitespace()) start--
        return line.substring(start + 1, idx).toDoubleOrNull()
    }

    private fun lagMarker(line: String): String? = when {
        line.contains("Can't keep up!") -> "servidor local atrasou (tick longo)"
        line.contains("Stitching") && line.contains("took") -> "stitching de texturas demorou"
        line.contains("Saving chunks") && line.contains("took") -> "save de chunks demorou"
        else -> null
    }
}
