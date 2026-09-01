package com.zalith.launcher.perf

import java.io.File

/**
 * Perfil do aparelho: RAM total e disponível, núcleos e arquitetura.
 * É a base de toda decisão automática da fase 3.
 *
 * Lê /proc/meminfo direto (Android é Linux) — sem depender da API do
 * Android, o módulo compila e roda em qualquer JVM para teste.
 */
class DeviceProfile(
    val totalRamMb: Int,
    val availableRamMb: Int,
    val cores: Int,
    val arch: String
) {

    enum class Tier { LOW, MID, HIGH }

    /** Faixa por RAM total: <4GB LOW · 4-6GB MID · >=8GB HIGH. */
    val tier: Tier = when {
        totalRamMb >= 8192 -> Tier.HIGH
        totalRamMb >= 4096 -> Tier.MID
        else -> Tier.LOW
    }

    /** Teto de RAM que o jogo pode pedir: 60% do total, nunca abaixo de 768MB. */
    val safeGameRamMb: Int
        get() = (totalRamMb * 60 / 100).coerceAtLeast(768)

    val summary: String
        get() = tier.name + " · " + totalRamMb + "MB total · " +
                availableRamMb + "MB livres · " + cores + " núcleos · " + arch

    companion object {
        /** Detecção real no aparelho; valores conservadores se algo falhar. */
        fun detect(): DeviceProfile {
            var total = 3072
            var available = total / 2
            try {
                val meminfo = File("/proc/meminfo")
                if (meminfo.exists()) {
                    for (line in meminfo.readLines()) {
                        val kb = kbValue(line) ?: continue
                        when {
                            line.startsWith("MemTotal:") -> total = (kb / 1024).toInt()
                            line.startsWith("MemAvailable:") -> available = (kb / 1024).toInt()
                        }
                    }
                }
            } catch (e: Exception) {
                // mantém os valores conservadores
            }
            if (available <= 0 || available > total) available = total / 2
            return DeviceProfile(
                total,
                available,
                Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
                System.getProperty("os.arch") ?: "unknown"
            )
        }

        /** "MemTotal:       3906324 kB" -> 3906324L */
        private fun kbValue(line: String): Long? {
            val parts = line.split(' ').filter { it.isNotEmpty() }
            if (parts.size < 2) return null
            val n = parts[1].toLongOrNull() ?: return null
            return if (n > 0) n else null
        }
    }
}
