package com.zalith.launcher.perf

import org.json.JSONObject
import java.io.File

/**
 * Sonda de FPS: contrato com o lado Java do runtime (fase 0). Um
 * agente empacotado com a ponte JNI escreve zl3-perf.json no game
 * dir a cada poucos segundos; este módulo lê o último valor.
 * Enquanto a fase 0 não entrega o agente, read() devolve null e o
 * diagnóstico mostra só o que tem.
 */
class FpsProbe(private val gameDir: File) {

    class Snapshot(
        val timestamp: Long,
        val fps: Int,
        val frameMs: Double,
        val heapUsedMb: Int,
        val heapMaxMb: Int,
        val renderer: String?
    )

    fun read(): Snapshot? {
        val f = File(gameDir, "zl3-perf.json")
        if (!f.exists()) return null
        return try {
            val o = JSONObject(f.readText(Charsets.UTF_8))
            Snapshot(
                o.optLong("t", 0L),
                o.optInt("fps", -1),
                o.optDouble("frameMs", -1.0),
                o.optInt("heapUsedMb", -1),
                o.optInt("heapMaxMb", -1),
                o.optString("renderer").takeIf { it.isNotEmpty() }
            )
        } catch (e: Exception) {
            null
        }
    }
}
