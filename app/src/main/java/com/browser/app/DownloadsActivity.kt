package com.browser.app

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class DownloadEntry(
    val path: String,
    val name: String,
    val size: Long,
    val timestamp: Long
)

class DownloadsActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnDelete: ImageView
    private var selectMode = false
    private val selected = mutableSetOf<String>()
    private var items = mutableListOf<DownloadEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_downloads)

        supportActionBar?.apply {
            title = "📥 Descargas"
            setDisplayHomeAsUpEnabled(true)
        }

        rv = findViewById(R.id.recyclerView)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnDelete = findViewById(R.id.btnDeleteMode)
        rv.layoutManager = LinearLayoutManager(this)

        btnDelete.setOnClickListener {
            if (selectMode && selected.isNotEmpty()) {
                confirmDelete()
            } else {
                selectMode = !selectMode
                selected.clear()
                refreshUI()
            }
        }

        loadDownloads()
    }

    private fun loadDownloads() {
        val entries = StorageManager.getDownloads(this).mapNotNull { entry ->
            val f = File(entry.path)
            if (f.exists()) DownloadEntry(f.absolutePath, f.name, f.length(), entry.timestamp)
            else null
        }.sortedByDescending { it.timestamp }

        items = entries.toMutableList()
        refreshUI()
    }

    private fun refreshUI() {
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        rv.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE

        btnDelete.setImageResource(
            if (selectMode) android.R.drawable.ic_menu_close_clear_cancel
            else android.R.drawable.ic_menu_delete
        )

        rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class VH(v: View) : RecyclerView.ViewHolder(v) {
                val tvTitle: TextView = v.findViewById(R.id.tvTitle)
                val tvSub: TextView = v.findViewById(R.id.tvSubtitle)
                val checkbox: CheckBox = v.findViewById(R.id.checkbox)
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
                val v = layoutInflater.inflate(R.layout.item_download, parent, false)
                return VH(v)
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val h = holder as VH
                val item = items[position]
                h.tvTitle.text = item.name
                h.tvSub.text = "${formatSize(item.size)}  •  ${formatDate(item.timestamp)}"

                h.checkbox.visibility = if (selectMode) View.VISIBLE else View.GONE
                h.checkbox.setOnCheckedChangeListener(null)
                h.checkbox.isChecked = selected.contains(item.path)
                h.checkbox.setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(item.path) else selected.remove(item.path)
                }

                h.itemView.setOnClickListener {
                    if (selectMode) {
                        h.checkbox.isChecked = !h.checkbox.isChecked
                    } else {
                        openFile(item)
                    }
                }
            }

            override fun getItemCount() = items.size
        }
    }

    private fun openFile(item: DownloadEntry) {
        try {
            val file = File(item.path)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.provider", file
            )
            val mime = contentResolver.getType(uri)
                ?: android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension) ?: "*/*"

            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Eliminar archivos")
            .setMessage("¿Eliminar ${selected.size} archivo(s) seleccionados? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                var deleted = 0
                selected.forEach { path ->
                    val f = File(path)
                    if (f.exists() && f.delete()) {
                        deleted++
                        StorageManager.removeDownload(this, path)
                    }
                }
                Toast.makeText(this, "🗑️ $deleted archivo(s) eliminados", Toast.LENGTH_SHORT).show()
                selectMode = false
                selected.clear()
                loadDownloads()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun formatDate(ts: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ts))

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
