package com.browser.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TabAdapter(
    private val tabs: List<BrowserTab>,
    private var activeIndex: Int,
    private val onTabClick: (Int) -> Unit,
    private val onTabClose: (Int) -> Unit
) : RecyclerView.Adapter<TabAdapter.TabVH>() {

    inner class TabVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView    = view.findViewById(R.id.tvTabTitle)
        val btnClose: ImageButton = view.findViewById(R.id.btnTabClose)
    }

    fun setActive(index: Int) {
        val old = activeIndex
        activeIndex = index
        notifyItemChanged(old)
        notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false)
        return TabVH(v)
    }

    override fun onBindViewHolder(holder: TabVH, position: Int) {
        val tab = tabs[position]
        holder.tvTitle.text = tab.title.take(16).ifEmpty { "Nueva pestaña" }

        val isActive = position == activeIndex
        holder.itemView.setBackgroundResource(
            if (isActive) R.drawable.bg_tab_active else R.drawable.bg_tab_inactive
        )
        holder.tvTitle.setTextColor(
            if (isActive) 0xFF00ffc3.toInt() else 0xFF7a8899.toInt()
        )

        holder.itemView.setOnClickListener { onTabClick(position) }
        holder.btnClose.setOnClickListener { onTabClose(position) }
    }

    override fun getItemCount() = tabs.size
}
