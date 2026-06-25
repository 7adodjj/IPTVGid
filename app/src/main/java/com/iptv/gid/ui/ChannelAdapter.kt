package com.iptv.gid.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.iptv.gid.R
import com.iptv.gid.data.Channel
import com.iptv.gid.data.EpgRepository
import com.iptv.gid.data.FavoritesRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChannelAdapter(
    private val onClick: (Channel) -> Unit,
    private val onFavoriteToggle: (Channel) -> Unit
) : ListAdapter<Channel, ChannelAdapter.VH>(DIFF) {

    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Channel>() {
            override fun areItemsTheSame(a: Channel, b: Channel) = a.url == b.url
            override fun areContentsTheSame(a: Channel, b: Channel) = a == b
        }

        // Запускаем VLC с флагом полного экрана
        fun openInVlc(channel: Channel, view: View) {
            val ctx = view.context
            try {
                // Пробуем запустить через VLC intent с полным экраном
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setPackage("org.videolan.vlc")
                    setDataAndType(Uri.parse(channel.url), "video/*")
                    putExtra("from_start", true)
                    putExtra("title", channel.name)
                    // Флаги полноэкранного режима VLC
                    putExtra("force_fullscreen", true)
                }
                ctx.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // VLC не установлен — предлагаем установить
                try {
                    val marketIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=org.videolan.vlc")
                    )
                    ctx.startActivity(marketIntent)
                    Toast.makeText(ctx, "Установите VLC для воспроизведения", Toast.LENGTH_LONG).show()
                } catch (e2: Exception) {
                    // Нет маркета — открываем в браузере
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=org.videolan.vlc")
                    )
                    ctx.startActivity(browserIntent)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val channel = getItem(position)
        holder.bind(channel)
        holder.itemView.setOnClickListener { onClick(channel) }
        holder.ivPlay.setOnClickListener { openInVlc(channel, it) }
        holder.ivFavorite.setOnClickListener {
            onFavoriteToggle(channel)
            notifyItemChanged(position)
        }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivLogo: ImageView     = view.findViewById(R.id.ivLogo)
        val tvName: TextView      = view.findViewById(R.id.tvChannelName)
        val tvNow: TextView       = view.findViewById(R.id.tvNowPlaying)
        val pbEpg: ProgressBar    = view.findViewById(R.id.pbEpg)
        val ivPlay: ImageView     = view.findViewById(R.id.ivPlay)
        val ivFavorite: ImageView = view.findViewById(R.id.ivFavorite)

        fun bind(channel: Channel) {
            tvName.text = channel.name

            // Звёздочка избранного
            ivFavorite.setImageResource(
                if (FavoritesRepository.isFavorite(channel))
                    android.R.drawable.btn_star_big_on
                else
                    android.R.drawable.btn_star_big_off
            )

            // Логотип через Glide
            if (channel.logoUrl.isNotEmpty()) {
                Glide.with(ivLogo.context)
                    .load(channel.logoUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_tv)
                    .error(R.drawable.ic_tv)
                    .into(ivLogo)
            } else {
                ivLogo.setImageResource(R.drawable.ic_tv)
            }

            // EPG прогресс
            val now = System.currentTimeMillis()
            val programs = EpgRepository.getProgramsForChannelAll(channel)
            val current = programs.firstOrNull { it.startTime <= now && it.endTime > now }

            if (current != null && current.startTime > 0 && current.endTime > 0) {
                val s = fmt.format(Date(current.startTime))
                val e = fmt.format(Date(current.endTime))
                tvNow.text = "$s–$e  ${current.title}"
                tvNow.visibility = View.VISIBLE
                val total = (current.endTime - current.startTime).toFloat()
                val elapsed = (now - current.startTime).toFloat()
                pbEpg.progress = ((elapsed / total) * 100).toInt().coerceIn(0, 100)
                pbEpg.visibility = View.VISIBLE
            } else {
                tvNow.visibility = View.GONE
                pbEpg.visibility = View.GONE
            }
        }
    }
}
