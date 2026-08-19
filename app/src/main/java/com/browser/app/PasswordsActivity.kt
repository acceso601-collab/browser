package com.browser.app

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PasswordsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        supportActionBar?.apply {
            title = "🔑 Contraseñas guardadas"
            setDisplayHomeAsUpEnabled(true)
        }

        val rv = findViewById<RecyclerView>(R.id.recyclerView)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)
        val btnAction = findViewById<TextView>(R.id.btnAction)

        btnAction.visibility = View.GONE
        tvEmpty.text = "No hay contraseñas guardadas\n\nSe guardan automáticamente al iniciar sesión"

        rv.layoutManager = LinearLayoutManager(this)
        loadList(rv, tvEmpty)
    }

    private fun loadList(rv: RecyclerView, tvEmpty: TextView) {
        val items = PasswordManager.getAll(this)
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
                h.tvTitle.text = item.domain
                h.tvSub.text = "👤 ${item.username}  •  🔒 ••••••••"
                h.btnDel.setImageResource(android.R.drawable.ic_menu_delete)
                h.btnDel.setOnClickListener {
                    AlertDialog.Builder(this@PasswordsActivity)
                        .setTitle("Eliminar")
                        .setMessage("¿Olvidar esta contraseña guardada para ${item.domain}?")
                        .setPositiveButton("Eliminar") { _, _ ->
                            PasswordManager.remove(this@PasswordsActivity, item.domain, item.username)
                            loadList(rv, tvEmpty)
                            Toast.makeText(this@PasswordsActivity, "Eliminada", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
                h.itemView.setOnClickListener { }
            }

            override fun getItemCount() = items.size
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
