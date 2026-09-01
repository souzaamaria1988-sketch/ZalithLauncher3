package com.zalith.launcher.ui

import android.content.Context
import android.view.Choreographer

/**
 * Sonda de desempenho da própria interface: o Choreographer conta os
 * frames e o HUD da tela inicial mostra fps + frames engolidos.
 * Se a UI travar, você vê na hora — nada de achismo.
 */
object UiPerf {

    @Volatile private var started = false
    private var lastNs = 0L
    private val deltas = ArrayList<Long>()

    @Volatile var jankFrames = 0
        private set

    fun start() {
        if (started) return
        started = true
        Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
            override fun doFrame(timeNs: Long) {
                if (lastNs != 0L) {
                    val dt = timeNs - lastNs
                    synchronized(deltas) {
                        deltas.add(dt)
                        if (deltas.size > 120) deltas.removeAt(0)
                    }
                    if (dt > 32_000_000L) jankFrames++
                }
                lastNs = timeNs
                Choreographer.getInstance().postFrameCallback(this)
            }
        })
    }

    fun fps(): Int {
        synchronized(deltas) {
            if (deltas.isEmpty()) return 0
            val avg = deltas.sum() / deltas.size
            return if (avg > 0) (1_000_000_000L / avg).toInt() else 0
        }
    }

    fun stats(): String = "UI " + fps() + "fps · " + jankFrames + " frames engolidos"
}

/**
 * Modo turbo — ligado por padrão (otimização máxima). Na integração
 * de launch, força o preset PERFORMANCE do JvmFlags (fase 3) e mantém
 * o HUD de jank ativo.
 */
object TurboMode {

    private const val PREFS = "zl3"

    fun isEnabled(c: Context): Boolean =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("turbo", true)

    fun setEnabled(c: Context, on: Boolean) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("turbo", on).apply()
    }
}
