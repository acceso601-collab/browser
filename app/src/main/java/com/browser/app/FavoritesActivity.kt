package com.browser.app

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FavoritesActivity : AppCompatActivity() {

    companion object {
        const val RESULT_URL = "url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        supportActionBar?.apply {
            title = "⭐ Favoritos"
            setDisplayHomeAsUpEnabled(true)
        }

        val rv = findViewById<RecyclerView>(R.id.recyclerView)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)
        val btnAction = findViewById<TextView>(R.id.btnAction)

        btnAction.visibility = View.GONE

        rv.layoutManager = LinearLayoutManager(this)
        loadList(rv, tvEmpty)
    }

    private fun loadList(rv: RecyclerView, tvEmpty: TextView) {
        val items = StorageManager.getFavorites(this)
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        rv.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        tvEmpty.text = "No hay favoritos\n\nUsa el menú ≡ para añadir páginas"

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
                h.tvSub.text = item.url
                h.btnDel.setImageResource(android.R.drawable.ic_menu_delete)
                h.btnDel.setOnClickListener {
                    StorageManager.removeFavorite(this@FavoritesActivity, item.url)
                    loadList(rv, tvEmpty)
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
