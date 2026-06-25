package com.iptv.gid.ui

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.iptv.gid.R
import com.iptv.gid.data.EpgProgram
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EpgAdapter : RecyclerView.Adapter<EpgAdapter.VH>() {

    private val items = mutableListOf<EpgProgram>()
    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submitList(list: List<EpgProgram>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_epg, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        val now = System.currentTimeMillis()
        val isNow = p.startTime <= now && p.endTime > now

        val start = if (p.startTime > 0) fmt.format(Date(p.startTime)) else "--:--"
        val end   = if (p.endTime > 0)   fmt.format(Date(p.endTime))   else "--:--"
        holder.tvTime.text  = "$start–$end"
        holder.tvTitle.text = p.title

        // Бейдж "СЕЙЧАС"
        if (isNow) {
            holder.tvNowBadge.visibility = View.VISIBLE
            holder.tvTitle.setTypeface(null, Typeface.BOLD)
        } else {
            holder.tvNowBadge.visibility = View.GONE
            holder.tvTitle.setTypeface(null, Typeface.NORMAL)
        }

        // Описание
        if (p.description.isNotEmpty()) {
            holder.tvDesc.text = p.description
            holder.tvDesc.visibility = View.VISIBLE
        } else {
            holder.tvDesc.visibility = View.GONE
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvNowBadge: TextView = view.findViewById(R.id.tvNowBadge)
        val tvTime: TextView     = view.findViewById(R.id.tvEpgTime)
        val tvTitle: TextView    = view.findViewById(R.id.tvEpgTitle)
        val tvDesc: TextView     = view.findViewById(R.id.tvEpgDesc)
    }
}
