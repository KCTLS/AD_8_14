package com.example.adproject

import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.adproject.model.AnswerRecord

class AnswerHistoryAdapter(
    private val all: MutableList<AnswerRecord>,
    private val onClick: (AnswerRecord) -> Unit   // item 点击回调
) : RecyclerView.Adapter<AnswerHistoryAdapter.VH>() {

    private val current = mutableListOf<AnswerRecord>()

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView? = v.findViewById(R.id.icon)
        val title: TextView = v.findViewById(R.id.txtTitle)
        val status: TextView = v.findViewById(R.id.txtStatus)
        val thumb: ImageView = v.findViewById(R.id.thumb)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_answer_history, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val record = current[position]
        val ctx = h.itemView.context

        // 标题
        h.title.text = record.title ?: "Question #${record.questionId}"

        // 正误文本 + 颜色
        if (record.isCorrect) {
            h.status.text = "Correct"
            h.status.setTextColor(ContextCompat.getColor(ctx, android.R.color.holo_green_dark))
            h.icon?.let { iv ->
                ImageViewCompat.setImageTintList(
                    iv, ColorStateList.valueOf(
                        ContextCompat.getColor(ctx, android.R.color.holo_green_dark)
                    )
                )
                iv.contentDescription = "Correct"
            }
        } else {
            h.status.text = "Wrong"
            h.status.setTextColor(ContextCompat.getColor(ctx, android.R.color.holo_red_dark))
            h.icon?.let { iv ->
                ImageViewCompat.setImageTintList(
                    iv, ColorStateList.valueOf(
                        ContextCompat.getColor(ctx, android.R.color.holo_red_dark)
                    )
                )
                iv.contentDescription = "Wrong"
            }
        }

        // 缩略图（base64 -> Bitmap）
        val b64 = record.imageBase64
        if (!b64.isNullOrBlank()) {
            try {
                val pure = b64.substringAfter("base64,", b64)
                val bytes = Base64.decode(pure, Base64.DEFAULT)
                val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                h.thumb.setImageBitmap(bm)
            } catch (_: Exception) {
                h.thumb.setImageResource(android.R.color.darker_gray)
            }
        } else {
            h.thumb.setImageResource(android.R.color.darker_gray)
        }

        // ✅ 绑定点击，传递当前 AnswerRecord（而不是 View）
        h.itemView.setOnClickListener { onClick(record) }
        h.thumb.setOnClickListener { onClick(record) }
    }

    override fun getItemCount(): Int = current.size

    fun submitList(list: List<AnswerRecord>) {
        current.clear()
        current.addAll(list)
        notifyDataSetChanged()
    }

    fun updateTitleAndImage(questionId: Int, title: String?, imageBase64: String?) {
        // 更新当前显示列表
        val i = current.indexOfFirst { it.questionId == questionId }
        if (i >= 0) {
            if (title != null) current[i].title = title
            if (imageBase64 != null) current[i].imageBase64 = imageBase64
            notifyItemChanged(i)
        }
        // 同步到全量
        val j = all.indexOfFirst { it.questionId == questionId }
        if (j >= 0) {
            if (title != null) all[j].title = title
            if (imageBase64 != null) all[j].imageBase64 = imageBase64
        }
    }
}
