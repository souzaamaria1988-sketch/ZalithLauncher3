package com.zalith.launcher.perf

/**
 * Escolha automática das flags de JVM conforme o aparelho — o coração
 * da fase de otimização. Presets:
 *
 *  ECONOMY      aparelho fraco: SerialGC, heap enxuto
 *  BALANCED     padrão: G1GC com pausas curtas
 *  PERFORMANCE  aparelho forte: G1GC agressivo (flags no estilo Aikar,
 *               adaptadas para cliente com mods)
 *  ZGC          experimental: só com JRE que inclua ZGC e heap grande
 *
 * As flags saem prontas para entrar no topo da lista de jvmArgs do
 * LaunchArguments (integração quando as branchs se encontrarem).
 */
class JvmFlags(private val profile: DeviceProfile) {

    enum class Preset(val id: String, val label: String) {
        ECONOMY("economy", "aparelho fraco — serial gc, heap enxuto"),
        BALANCED("balanced", "padrão — g1gc com pausas curtas"),
        PERFORMANCE("performance", "aparelho forte — g1gc agressivo"),
        ZGC("zgc", "experimental — heap grande, pausas mínimas")
    }

    enum class Gc { SERIAL, G1, ZGC }

    class Recommendation(
        val preset: Preset,
        val gc: Gc,
        val heapMb: Int,
        val flags: List<String>,
        val explanation: Map<String, String>
    )

    fun autoPreset(): Preset = when (profile.tier) {
        DeviceProfile.Tier.LOW -> Preset.ECONOMY
        DeviceProfile.Tier.MID -> Preset.BALANCED
        DeviceProfile.Tier.HIGH -> Preset.PERFORMANCE
    }

    /**
     * Heap pela RAM + mods:
     *  LOW -> 1024MB · MID -> 2048MB · HIGH -> 4096MB (mais que isso
     *  em Android só piora o sistema, não o FPS)
     *  boost de mods: +128MB a cada 8 mods, teto +768MB
     *  economy nunca passa de 1024; os demais, de 60% da RAM total.
     */
    fun heapMb(preset: Preset, modsCount: Int = 0): Int {
        val base = when (preset) {
            Preset.ECONOMY -> 1024
            Preset.BALANCED -> 2048
            else -> 4096
        }
        val boost = if (modsCount > 0) (modsCount / 8) * 128 else 0
        val cap = if (preset == Preset.ECONOMY) 1024 else profile.safeGameRamMb
        return (base + boost.coerceAtMost(768)).coerceAtMost(cap)
    }

    fun recommend(
        preset: Preset = autoPreset(),
        heapOverrideMb: Int = 0,
        modsCount: Int = 0
    ): Recommendation {
        val heap = if (heapOverrideMb > 0) {
            heapOverrideMb.coerceAtMost(profile.safeGameRamMb)
        } else {
            heapMb(preset, modsCount)
        }
        val gc = when (preset) {
            Preset.ECONOMY -> Gc.SERIAL
            Preset.ZGC -> Gc.ZGC
            else -> Gc.G1
        }

        val flags = ArrayList<String>()
        val why = LinkedHashMap<String, String>()

        fun add(flag: String, reason: String) {
            flags.add(flag)
            why[flag] = reason
        }

        add("-Xms" + (heap / 2).coerceAtLeast(512) + "m",
            "heap inicial = metade do teto: menos reagrandecimento no início")
        add("-Xmx" + heap + "m",
            "teto de heap pela RAM do aparelho" +
            (if (modsCount > 0) " (+" + (modsCount / 8) * 128 + "MB de mods)" else ""))
        add("-XX:+UnlockExperimentalVMOptions", "libera as flags experimentais abaixo")
        add("-XX:+DisableExplicitGC", "System.gc() chamado por mod não congela o jogo")
        add("-XX:+PerfDisableSharedMem",
            "desliga escrita de perf data em memória compartilhada: pausas menores")

        when (gc) {
            Gc.SERIAL -> {
                add("-XX:+UseSerialGC", "serial gc: menor pegada de RAM em aparelho fraco")
                add("-XX:NewRatio=3", "new gen proporcional ao heap apertado")
                add("-XX:MaxMetaspaceSize=256m", "teto de metaspace: classes de mods não estouram")
            }
            Gc.G1 -> {
                add("-XX:+UseG1GC", "g1: melhor custo/benefício com heap grande")
                add("-XX:MaxGCPauseMillis=40", "meta de pausa curta: sem engasgo visível")
                add("-XX:+ParallelRefProcEnabled", "referências em paralelo: mods criam muitas")
                add("-XX:+UseStringDeduplication", "strings duplicadas de mods somem do heap")
                add("-XX:G1NewSizePercent=" + (if (preset == Preset.PERFORMANCE) 30 else 20),
                    "new gen mínimo garantido: gcs jovens curtas")
                add("-XX:G1ReservePercent=" + (if (preset == Preset.PERFORMANCE) 20 else 15),
                    "reserva contra promoção surpresa (to-space exhausted)")
                if (heap >= 3072) {
                    add("-XX:G1HeapRegionSize=16m", "regiões de 16m: heap >3GB sem desperdício")
                }
                add("-XX:MaxMetaspaceSize=" + (if (preset == Preset.PERFORMANCE) 512 else 384) + "m",
                    "metaspace folgado para os mods, com teto")
            }
            Gc.ZGC -> {
                add("-XX:+UseZGC", "zgc: pausas sub-milissegundo (experimental no Android)")
                add("-XX:SoftMaxHeapSize=" + (heap - heap / 8) + "m",
                    "zgc estica até o teto só sob pressão real")
                add("-XX:MaxMetaspaceSize=512m", "teto de metaspace para os mods")
            }
        }

        flags.add("-Dfile.encoding=UTF-8")
        why["-Dfile.encoding=UTF-8"] = "teclado e logs consistentes entre aparelhos"

        return Recommendation(preset, gc, heap, flags, why)
    }
}
