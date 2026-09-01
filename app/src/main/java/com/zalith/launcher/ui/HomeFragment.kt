package com.zalith.launcher.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zalith.launcher.R
import com.zalith.launcher.instances.InstanceManager
import com.zalith.launcher.multiplayer.accounts.AccountRepository
import com.zalith.launcher.play.PlayTimeTracker
import java.io.File

/**
 * Tela inicial: conta ativa, tempo de jogo (hoje/total), HUD de jank,
 * turbo, instâncias e iniciar rápido. Toda a IO em thread de fundo.
 */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private data class Row(
        val id: String, val name: String, val sub: String,
        val time: String, val ago: String
    )

    private var repo: AccountRepository? = null
    private var instances: InstanceManager? = null
    private var playtime: PlayTimeTracker? = null
    private var lastInstanceId: String? = null

    private val adapter = InstanceAdapter()
    private val ui = Handler(Looper.getMainLooper())

    private val hud = object : Runnable {
        override fun run() {
            view?.findViewById<TextView>(R.id.jankHud)?.text = UiPerf.stats()
            ui.postDelayed(this, 1000)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val base = File(requireContext().filesDir, "zl3")
        repo = AccountRepository(base)
        instances = InstanceManager(base)
        playtime = PlayTimeTracker(base)

        view.findViewById<RecyclerView>(R.id.instList).apply {
            layoutManager = LinearLayoutManager(context)
            itemAnimator = null
            adapter = this@HomeFragment.adapter
        }

        view.findViewById<Switch>(R.id.turbo).apply {
            isChecked = TurboMode.isEnabled(requireContext())
            setOnCheckedChangeListener { _, on -> TurboMode.setEnabled(requireContext(), on) }
        }

        view.findViewById<View>(R.id.quickLaunch).setOnClickListener {
            if (lastInstanceId == null) {
                Toast.makeText(context, "sem instância jogada ainda", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context,
                    "runtime pendente: ponte JNI + JREs vêm na próxima entrega",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        ui.post(hud)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(hud)
    }

    private fun refresh() {
        val r = repo ?: return
        val im = instances ?: return
        val pt = playtime ?: return
        Thread {
            val account = r.active()
            val today = pt.todayAll()
            val total = pt.totalAll()
            val list = im.list().map { inst ->
                Row(
                    inst.id,
                    inst.name,
                    inst.vanillaVersion + " · " + inst.loaderType.id,
                    fmtDur(pt.totalOf(inst.id)),
                    fmtAgo(pt.lastPlayed(inst.id))
                )
            }
            val last = im.list().filter { it.lastPlayed > 0 }.maxByOrNull { it.lastPlayed }
            ui.post {
                val v = view ?: return@post
                v.findViewById<TextView>(R.id.accName).text =
                    account?.username ?: "sem conta — crie em CONTAS"
                v.findViewById<TextView>(R.id.ptToday).text = fmtDur(today)
                v.findViewById<TextView>(R.id.ptTotal).text = fmtDur(total)
                lastInstanceId = last?.id
                v.findViewById<TextView>(R.id.quickLaunch).text =
                    if (last != null) "▶ INICIAR · " + last.name else "▶ INICIAR"
                adapter.submit(list)
            }
        }.start()
    }

    private fun fmtDur(ms: Long): String {
        val m = ms / 60000L
        return if (m < 60) m.toString() + "m" else (m / 60).toString() + "h " + (m % 60).toString() + "m"
    }

    private fun fmtAgo(ts: Long): String {
        if (ts <= 0L) return "nunca jogada"
        val h = (System.currentTimeMillis() - ts) / 3600000L
        return when {
            h < 1L -> "agora há pouco"
            h < 24L -> "há " + h + "h"
            h < 48L -> "ontem"
            else -> "há " + (h / 24L) + "d"
        }
    }

    private inner class InstanceAdapter : RecyclerView.Adapter<InstanceAdapter.VH>() {

        private var rows = emptyList<Row>()

        fun submit(next: List<Row>) {
            val old = rows
            rows = next
            DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = old.size
                override fun getNewListSize() = next.size
                override fun areItemsTheSame(a: Int, b: Int) = old[a].id == next[b].id
                override fun areContentsTheSame(a: Int, b: Int) = old[a] == next[b]
            }).dispatchUpdatesTo(this)
        }

        override fun getItemCount() = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_instance, parent, false))

        override fun onBindViewHolder(h: VH, pos: Int) {
            val r = rows[pos]
            h.name.text = r.name
            h.sub.text = r.sub
            h.meta.text = r.time + " · " + r.ago
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvName)
            val sub: TextView = v.findViewById(R.id.tvSub)
            val meta: TextView = v.findViewById(R.id.tvMeta)
        }
    }
}
