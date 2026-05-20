package com.browser.app

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HistoryActivity : AppCompatActivity() {

    companion object {
        const val RESULT_URL = "url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        supportActionBar?.apply {
            title = "📚 Historial"
            setDisplayHomeAsUpEnabled(true)
        }

        val rv = findViewById<RecyclerView>(R.id.recyclerView)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)
        val btnAction = findViewById<TextView>(R.id.btnAction)

        btnAction.text = "🗑️ Borrar todo"
        btnAction.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Borrar historial")
                .setMessage("¿Eliminar todo el historial?")
                .setPositiveButton("Borrar") { _, _ ->
                    StorageManager.clearHistory(this)
                    loadList(rv, tvEmpty)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        rv.layoutManager = LinearLayoutManager(this)
        loadList(rv, tvEmpty)
    }

    private fun loadList(rv: RecyclerView, tvEmpty: TextView) {
        val items = StorageManager.getHistory(this)
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        rv.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE

        rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class VH(v: View) : RecyclerView.ViewHolder(v) {
                val tvTitle: TextView = v.findViewById(R.id.tvTitle)
                val tvSub: TextView = v.findViewById(R.id.tvSubtitle)
                val btnDel: ImageButton = v.findViewById(R.id.btnItemAction)
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
                val v = layoutInflater.inflate(R.layout.item_list, parent, false)
                return VH(v)
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val h = holder as VH
                val item = items[position]
                h.tvTitle.text = item.title
                h.tvSub.text = "${StorageManager.formatDate(item.timestamp)}  •  ${item.url}"
                h.btnDel.setImageResource(android.R.drawable.ic_menu_delete)
                h.btnDel.setOnClickListener {
                    val intent = Intent().apply { putExtra(RESULT_URL, item.url) }
                    setResult(RESULT_OK, intent)
                    finish()
                }
                h.itemView.setOnClickListener {
                    val intent = Intent().apply { putExtra(RESULT_URL, item.url) }
                    setResult(RESULT_OK, intent)
                    finish()
                }
            }

            override fun getItemCount() = items.size
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
