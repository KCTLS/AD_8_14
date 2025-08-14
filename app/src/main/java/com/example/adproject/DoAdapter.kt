package com.example.adproject

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.adproject.model.AssignmentQuestion

class DoAdapter(
    private val onChoose: (qid: Int, choice: Int) -> Unit
) : RecyclerView.Adapter<DoAdapter.VH>() {
    private val items = mutableListOf<AssignmentQuestion>()
    private var aid: Int = -1

    fun submit(list: List<AssignmentQuestion>, assignmentId: Int) {
        items.clear(); items.addAll(list); this.aid = assignmentId; notifyDataSetChanged()
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int): VH {
        val v = LayoutInflater.from(p.context).inflate(R.layout.item_question_do, p, false)
        return VH(v)
    }
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(items[pos], aid, onChoose)

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvQ = v.findViewById<TextView>(R.id.tvQ)
        private val iv = v.findViewById<ImageView>(R.id.iv)
        private val group = v.findViewById<RadioGroup>(R.id.group)

        fun bind(q: AssignmentQuestion, aid: Int, onChoose: (Int, Int) -> Unit) {
            tvQ.text = q.question

            // 图片（base64）可选显示
            if (!q.image.isNullOrBlank()) {
                iv.visibility = View.VISIBLE
                iv.setImageBitmap(decodeBase64Bitmap(q.image))
            } else iv.visibility = View.GONE

            group.removeAllViews()
            q.choices.forEachIndexed { idx, text ->
                val rb = RadioButton(itemView.context).apply {
                    layoutParams = RadioGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    this.text = text
                    id = View.generateViewId()
                }
                group.addView(rb)
            }

            // 回显
            val p = AssignmentProgressStore.get(itemView.context, aid)
            val chosen = p.answers[q.id]
            if (chosen != null && chosen in 0 until group.childCount) {
                (group.getChildAt(chosen) as? RadioButton)?.isChecked = true
            } else group.clearCheck()

            group.setOnCheckedChangeListener { _, checkedId ->
                val idx = (0 until group.childCount).indexOfFirst { group.getChildAt(it).id == checkedId }
                if (idx >= 0) onChoose(q.id, idx)
            }
        }

        private fun decodeBase64Bitmap(base64: String): android.graphics.Bitmap? = try {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) { null }
    }
}
