package com.zalith.launcher.ui

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zalith.launcher.R
import com.zalith.launcher.multiplayer.accounts.Account
import com.zalith.launcher.multiplayer.accounts.AccountRepository
import com.zalith.launcher.multiplayer.accounts.MicrosoftAuth
import java.io.File

/**
 * Contas: criar offline (instantâneo, UUID oficial), entrar com
 * Microsoft por device code, trocar com toque e remover com toque longo.
 */
class AccountsFragment : Fragment(R.layout.fragment_accounts) {

    private data class Row(val uuid: String, val name: String, val type: String, val active: Boolean)

    private var repo: AccountRepository? = null
    private val adapter = AccountAdapter()
    private val ui = Handler(Looper.getMainLooper())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repo = AccountRepository(File(requireContext().filesDir, "zl3"))

        view.findViewById<RecyclerView>(R.id.accList).apply {
            layoutManager = LinearLayoutManager(context)
            itemAnimator = null
            adapter = this@AccountsFragment.adapter
        }

        view.findViewById<View>(R.id.btnAddOffline).setOnClickListener { showOfflineDialog() }
        view.findViewById<View>(R.id.btnMicrosoft).setOnClickListener { showMicrosoftDialog() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val r = repo ?: return
        Thread {
            val active = r.active()?.uuid
            val rows = r.list().map { a ->
                Row(a.uuid, a.username,
                    if (a is Account.Microsoft) "MICROSOFT" else "OFFLINE",
                    a.uuid == active)
            }
            ui.post { adapter.submit(rows) }
        }.start()
    }

    private fun showOfflineDialog() {
        val r = repo ?: return
        val input = EditText(context).apply {
            hint = "nome da conta"
            setSingleLine()
            setTextColor(0xFFE8E0DC.toInt())
            setHintTextColor(0xFF9A8F8A.toInt())
            typeface = Typeface.MONOSPACE
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Nova conta offline")
            .setMessage("UUID derivado do nome pela regra oficial — whitelist casa certinho.")
            .setView(input)
            .setPositiveButton("criar") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(context, "nome vazio", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Thread {
                    r.add(Account.Offline(name))
                    ui.post { refresh() }
                }.start()
            }
            .setNegativeButton("cancelar", null)
            .show()
    }

    private fun showMicrosoftDialog() {
        val r = repo ?: return
        val msg = TextView(context).apply {
            text = "pedindo código de dispositivo…"
            setTextColor(0xFFE8E0DC.toInt())
            setPadding(48, 32, 48, 32)
            typeface = Typeface.MONOSPACE
            textSize = 13f
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Entrar com Microsoft")
            .setView(msg)
            .setNegativeButton("fechar", null)
            .show()
        Thread {
            try {
                if (MicrosoftAuth.clientId.startsWith("PREENCHER")) {
                    ui.post { msg.text = "configure o clientId em MicrosoftAuth (docs/FASE1-MULTIPLAYER.md)" }
                    return@Thread
                }
                val dc = MicrosoftAuth.requestDeviceCode()
                ui.post { msg.text = "digite o código " + dc.userCode + " em " + dc.verificationUri }
                val ms = MicrosoftAuth.awaitApproval(dc)
                val mc = MicrosoftAuth.loginWithXbox(ms.accessToken)
                r.add(Account.Microsoft(
                    mc.username, mc.uuid, mc.accessToken, ms.refreshToken,
                    System.currentTimeMillis() + mc.expiresInSeconds * 1000L,
                    mc.xuid, mc.skinUrl))
                ui.post {
                    refresh()
                    dialog.dismiss()
                }
            } catch (e: Exception) {
                ui.post { msg.text = "falhou: " + e.message }
            }
        }.start()
    }

    private inner class AccountAdapter : RecyclerView.Adapter<AccountAdapter.VH>() {

        private var rows = emptyList<Row>()

        fun submit(next: List<Row>) {
            val old = rows
            rows = next
            DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = old.size
                override fun getNewListSize() = next.size
                override fun areItemsTheSame(a: Int, b: Int) = old[a].uuid == next[b].uuid
                override fun areContentsTheSame(a: Int, b: Int) = old[a] == next[b]
            }).dispatchUpdatesTo(this)
        }

        override fun getItemCount() = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_account, parent, false))

        override fun onBindViewHolder(h: VH, pos: Int) {
            val r = rows[pos]
            h.name.text = r.name
            h.type.text = r.type
            h.dot.setTextColor(if (r.active) 0xFFFF5245.toInt() else 0xFF5C5451.toInt())
            h.itemView.setOnClickListener {
                Thread {
                    repo?.setActive(r.uuid)
                    ui.post { refresh() }
                }.start()
            }
            h.itemView.setOnLongClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("remover conta?")
                    .setMessage(r.name)
                    .setPositiveButton("remover") { _, _ ->
                        Thread {
                            repo?.remove(r.uuid)
                            ui.post { refresh() }
                        }.start()
                    }
                    .setNegativeButton("manter", null)
                    .show()
                true
            }
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvAccName)
            val type: TextView = v.findViewById(R.id.tvAccType)
            val dot: TextView = v.findViewById(R.id.tvAccDot)
        }
    }
}
