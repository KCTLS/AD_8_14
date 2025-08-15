package com.example.adproject

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        val root: View       = v.findViewById(R.id.rootClickable) // 整卡可点（带水波纹）
        val title: TextView  = v.findViewById(R.id.txtTitle)
        val thumb: ImageView = v.findViewById(R.id.thumb)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_recommend, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = data[position]

        // 标题
        h.title.text = item.title

        // 缩略图：base64 -> Bitmap；失败/为空用占位图
        val placeholder = R.drawable.placeholder_image
        val b64 = item.imageBase64
        if (!b64.isNullOrBlank()) {
            try {
                val pure = b64.substringAfter(",", b64) // 兼容 dataURI 或纯 base64
                val bytes = Base64.decode(pure, Base64.DEFAULT)
                ByteArrayInputStream(bytes).use { input ->
                    val bm = BitmapFactory.decodeStream(input)
                    if (bm != null) h.thumb.setImageBitmap(bm) else h.thumb.setImageResource(placeholder)
                }
            } catch (_: Exception) {
                h.thumb.setImageResource(placeholder)
            }
        } else {
            h.thumb.setImageResource(placeholder)
        }

        // 整卡点击
        h.root.setOnClickListener { onStart(item) }
    }

    override fun getItemCount(): Int = data.size

    fun setItems(newItems: List<RecommendedPractice>) {
        data.clear()
        data.addAll(newItems.distinctBy { it.id })
        notifyDataSetChanged()
    }

    fun addItem(item: RecommendedPractice) {
        val i = data.indexOfFirst { it.id == item.id }
        if (i >= 0) { data[i] = item; notifyItemChanged(i) }
        else { data.add(item); notifyItemInserted(data.size - 1) }
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
