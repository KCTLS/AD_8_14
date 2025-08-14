package com.example.adproject

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.adproject.model.RecommendedPractice
import java.io.ByteArrayInputStream

class RecommendedAdapter(
    private val data: MutableList<RecommendedPractice>,
    private val onStart: (RecommendedPractice) -> Unit
) : RecyclerView.Adapter<RecommendedAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        // 去掉 icon
        val title: TextView = v.findViewById(R.id.title)
        val subtitle: TextView = v.findViewById(R.id.subtitle)
        val badge: TextView = v.findViewById(R.id.badge)
        val thumb: ImageView = v.findViewById(R.id.thumb)
        val startBtn: Button = v.findViewById(R.id.startBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_recommend, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = data[position]
        h.title.text = item.title
        h.subtitle.text = "${item.subject ?: "—"} · ${item.grade ?: "—"} · ${item.questions} questions"
        h.badge.text = item.difficulty ?: "Medium"

        val b64 = item.imageBase64
        if (!b64.isNullOrBlank()) {
            try {
                val bytes = Base64.decode(b64.substringAfter(","), Base64.DEFAULT)
                val input = ByteArrayInputStream(bytes)
                val bm = BitmapFactory.decodeStream(input)
                h.thumb.setImageBitmap(bm)
            } catch (_: Exception) {
                h.thumb.setImageResource(android.R.color.darker_gray)
            }
        } else {
            h.thumb.setImageResource(android.R.color.darker_gray)
        }

        h.startBtn.setOnClickListener { onStart(item) }
        h.itemView.setOnClickListener { onStart(item) } // 整卡可点
    }

    override fun getItemCount(): Int = data.size

    fun setItems(newItems: List<RecommendedPractice>) {
        data.clear()
        data.addAll(newItems.distinctBy { it.id })
        notifyDataSetChanged()
    }

    fun addItem(item: RecommendedPractice) {
        val i = data.indexOfFirst { it.id == item.id }
        if (i >= 0) {
            data[i] = item
            notifyItemChanged(i)
        } else {
            data.add(item)
            notifyItemInserted(data.size - 1)
        }
    }

    fun clear() {
        data.clear()
        notifyDataSetChanged()
    }

    fun updateItem(id: Int, block: (RecommendedPractice) -> Unit) {
        val i = data.indexOfFirst { it.id == id }
        if (i >= 0) {
            block(data[i])
            notifyItemChanged(i)
        }
    }
}
