package com.zalith.launcher.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.zalith.launcher.R

/**
 * Casca única: 3 fragments escondidos/mostrados — trocar de aba nunca
 * recria tela (uma das causas clássicas de travamento).
 */
class MainActivity : AppCompatActivity() {

    private val frags = arrayOfNulls<Fragment>(3)
    private lateinit var tabs: Array<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        UiPerf.start()

        tabs = arrayOf(
            findViewById(R.id.tab0),
            findViewById(R.id.tab1),
            findViewById(R.id.tab2)
        )
        for (i in tabs.indices) tabs[i].setOnClickListener { select(i) }
        select(0)
    }

    private fun select(index: Int) {
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()
        for (i in 0..2) {
            var f = fm.findFragmentByTag("f" + i)
            if (f == null && i == index) {
                f = when (i) {
                    0 -> HomeFragment()
                    1 -> AccountsFragment()
                    else -> LogsFragment()
                }
                frags[i] = f
                tx.add(R.id.container, f, "f" + i)
            } else if (f != null) {
                frags[i] = f
            }
            if (i == index) tx.show(frags[i]!!) else if (frags[i] != null) tx.hide(frags[i]!!)
            tabs[i].setTextColor(if (i == index) 0xFFFF5245.toInt() else 0xFF9A8F8A.toInt())
        }
        tx.commitAllowingStateLoss()
    }
}
