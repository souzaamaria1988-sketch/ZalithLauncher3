package com.zalith.launcher.perf

import org.json.JSONArray
import org.json.JSONObject

/**
 * Relatório consolidado do painel de diagnóstico: aparelho, flags
 * recomendadas (com explicação), última sessão, FPS e digest do log.
 * A UI chama text() para tela e json() para persistir/exportar.
 */
class PerfReport(
    private val profile: DeviceProfile,
    private val recommendation: JvmFlags.Recommendation
) {

    fun text(
        lastStats: LaunchStats.Summary? = null,
        fps: FpsProbe.Snapshot? = null,
        log: GameLogAnalyzer.Digest? = null
    ): String {
        val sb = StringBuilder()
        sb.append("aparelho  ").append(profile.summary).appendLine()
        sb.append("preset    ").append(recommendation.preset.id)
            .append(" (").append(recommendation.preset.label).append(")").appendLine()
        sb.append("gc        ").append(recommendation.gc).appendLine()
        sb.append("heap      ").append(recommendation.heapMb)
            .append("MB · teto de 60% da RAM respeitado").appendLine()
        sb.append("flags     ").append(recommendation.flags.size)
            .append(" automáticas").appendLine()
        lastStats?.let { s ->
            sb.append("sessões   ").append(s.launches)
                .append(" · última ").append(fmt(s.lastTotalMs))
                .append(" · média ").append(fmt(s.avgTotalMs)).appendLine()
        }
        if (fps != null) {
            sb.append("fps       ").append(fps.fps)
                .append(" · frame ").append(fps.frameMs).append("ms")
                .append(" · heap ").append(fps.heapUsedMb)
                .append("/").append(fps.heapMaxMb).append("MB").appendLine()
        } else {
            sb.append("fps       sonda sem dados (agente java pendente na fase 0)").appendLine()
        }
        if (log != null) {
            sb.append("log       ").append(log.warnings).append(" warns · ")
                .append(log.errors).append(" erros · gc ")
                .append(log.gcPauses).append(" pausas (")
                .append(log.gcTotalMs).append("ms)").appendLine()
            if (log.lagMarkers.isNotEmpty()) {
                sb.append("lag       ").append(log.lagMarkers.size)
                    .append(" marcadores no último log").appendLine()
            }
        }
        return sb.toString()
    }

    fun json(
        lastStats: LaunchStats.Summary? = null,
        fps: FpsProbe.Snapshot? = null,
        log: GameLogAnalyzer.Digest? = null
    ): JSONObject {
        val o = JSONObject()
            .put("device", profile.summary)
            .put("tier", profile.tier.name)
            .put("preset", recommendation.preset.id)
            .put("gc", recommendation.gc.name)
            .put("heapMb", recommendation.heapMb)
            .put("flags", JSONArray(recommendation.flags))
        lastStats?.let { s ->
            o.put("launches", s.launches)
                .put("lastLaunchMs", s.lastTotalMs)
                .put("avgLaunchMs", s.avgTotalMs)
        }
        fps?.let { p ->
            o.put("fps", p.fps).put("frameMs", p.frameMs)
                .put("heapUsedMb", p.heapUsedMb).put("heapMaxMb", p.heapMaxMb)
        }
        log?.let { d ->
            o.put("warnings", d.warnings).put("errors", d.errors)
                .put("gcPauses", d.gcPauses).put("gcTotalMs", d.gcTotalMs)
        }
        return o
    }

    private fun fmt(v: Long): String =
        if (v >= 1000) String.format("%.1fs", v / 1000.0) else v.toString() + "ms"
}
