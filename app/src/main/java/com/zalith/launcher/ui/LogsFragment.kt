package com.zalith.launcher.ui

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zalith.launcher.R
import com.zalith.launcher.logs.LogTail
import java.io.File

/**
 * Log do Minecraft ao vivo: cauda do latest.log mais recente entre as
 * instâncias, filtros de nível e autoscroll que pausa quando você
 * sobe para ler. Buffer limitado a 2000 linhas — sem travar.
 */
class LogsFragment : Fragment(R.layout.fragment_logs) {

    private class Line(val text: String, val level: String)

    private val all = ArrayList<Line>()
    private val shown = ArrayList<Line>()
    private var filter = "A"
    private var auto = true
    private val adapter = LineAdapter()
    private val ui = Handler(Looper.getMainLooper())
    private var tail: LogTail? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rec = view.findViewById<RecyclerView>(R.id.logList)
        rec.layoutManager = LinearLayoutManager(context)
        rec.itemAnimator = null
        rec.adapter = adapter
        rec.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                auto = !rv.canScrollVertically(1)
            }
        })
        view.findViewById<TextView>(R.id.fAll).setOnClickListener { setFilter("A") }
        view.findViewById<TextView>(R.id.fWarn).setOnClickListener { setFilter("W") }
        view.findViewById<TextView>(R.id.fErr).setOnClickListener { setFilter("E") }
    }

    override fun onStart() {
        super.onStart()
        tail = LogTail(File(requireContext().filesDir, "zl3/instances"))
        tail?.start { line -> ui.post { add(line) } }
    }

    override fun onStop() {
        tail?.stop()
        tail = null
        super.onStop()
    }

    private fun setFilter(f: String) {
        filter = f
        view?.findViewById<TextView>(R.id.fAll)?.setTextColor(if (f == "A") RED else DIM)
        view?.findViewById<TextView>(R.id.fWarn)?.setTextColor(if (f == "W") RED else DIM)
        view?.findViewById<TextView>(R.id.fErr)?.setTextColor(if (f == "E") RED else DIM)
        rebuild()
    }

    private fun add(raw: String) {
        val line = Line(raw, LogTail.levelOf(raw))
        all.add(line)
        if (all.size > 2000) {
            all.subList(0, all.size - 2000).clear()
            rebuild()
            return
        }
        if (match(line)) {
            shown.add(line)
            adapter.notifyItemInserted(shown.size - 1)
            scrollIfAuto()
        }
    }

    private fun match(l: Line): Boolean = when (filter) {
        "W" -> l.level != "I"
        "E" -> l.level == "E"
        else -> true
    }

    private fun rebuild() {
        shown.clear()
        shown.addAll(all.filter { match(it) })
        adapter.notifyDataSetChanged()
        scrollIfAuto()
    }

    private fun scrollIfAuto() {
        if (auto && shown.isNotEmpty()) {
            view?.findViewById<RecyclerView>(R.id.logList)?.scrollToPosition(shown.size - 1)
        }
    }

    private inner class LineAdapter : RecyclerView.Adapter<LineAdapter.VH>() {

        override fun getItemCount() = shown.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context).apply {
                typeface = Typeface.MONOSPACE
                textSize = 11f
                setPadding(24, 4, 24, 4)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            return VH(tv)
        }

        override fun onBindViewHolder(h: VH, pos: Int) {
            val l = shown[pos]
            h.tv.text = l.text
            h.tv.setTextColor(when (l.level) {
                "E" -> RED
                "W" -> AMBER
                else -> GRAY
            })
        }

        inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)
    }

    companion object {
        private val RED = 0xFFFF5245.toInt()
        private val AMBER = 0xFFD4A45C.toInt()
        private val GRAY = 0xFF9A8F8A.toInt()
        private val DIM = 0xFF7C726D.toInt()
    }
}
