package com.example.adproject

import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.adproject.model.ClassAssignmentItem
import java.util.Calendar
import java.time.LocalDateTime

class AssignmentAdapter(
    private val onClick: (ClassAssignmentItem) -> Unit
) : RecyclerView.Adapter<AssignmentAdapter.VH>() {

    private val items = mutableListOf<ClassAssignmentItem>()

    fun submit(data: List<ClassAssignmentItem>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_assignment, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], onClick)
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvTitle = v.findViewById<TextView>(R.id.tvTitle)
        private val tvMeta  = v.findViewById<TextView>(R.id.tvMeta)
        private val dot     = v.findViewById<View>(R.id.dotStatus)

        fun bind(item: ClassAssignmentItem, onClick: (ClassAssignmentItem) -> Unit) {
            tvTitle.text = item.assignmentName

            // —— 状态判定
            val nowMs = System.currentTimeMillis()
            val expired = item.isExpired(nowMs)
            val completed = (item.whetherFinish == 1) || !item.finishTime.isNullOrEmpty()

            // “进行中”：未完成、未过期 且 本地已答过至少一题
            val localProgress = AssignmentProgressStore.get(itemView.context, item.assignmentId)
            val doing = !completed && !expired && localProgress.answers.isNotEmpty()

            val status = when {
                expired   -> Status.EXPIRED
                completed -> Status.COMPLETED
                doing     -> Status.DOING
                else      -> Status.NEW
            }

            // —— Meta 文案
            val dueText = item.expireTime.toDateTimeText()
            val finishText = item.finishTime.toDateTimeText()
            val statusText = when (status) {
                Status.EXPIRED   -> "Expired"
                Status.COMPLETED -> if (finishText.isNotEmpty()) "Completed · $finishText" else "Completed"
                Status.DOING     -> "In progress"
                Status.NEW       -> "Not started"
            }
            tvMeta.text = if (dueText.isNotEmpty()) "Due $dueText · $statusText" else statusText

            // —— UI: 圆点 + 背景 + 点击行为
            when (status) {
                Status.EXPIRED -> {
                    dot.setBackgroundResource(R.drawable.bg_dot_gray)
                    // 灰底（需要你在 drawable 放一个 bg_card_expired.xml）
                    itemView.setBackgroundResource(R.drawable.bg_card_expired)
                    itemView.alpha = 0.8f
                    itemView.isEnabled = false
                    itemView.setOnClickListener {
                        Toast.makeText(it.context, "已过期", Toast.LENGTH_SHORT).show()
                    }
                }
                Status.COMPLETED -> {
                    dot.setBackgroundResource(R.drawable.bg_dot_green)
                    // 绿底（需要 drawable/bg_card_completed.xml）
                    itemView.setBackgroundResource(R.drawable.bg_card_completed)
                    itemView.alpha = 1f
                    itemView.isEnabled = false
                    itemView.setOnClickListener {
                        Toast.makeText(it.context, "已提交", Toast.LENGTH_SHORT).show()
                    }
                }
                Status.DOING -> {
                    dot.setBackgroundResource(R.drawable.bg_dot_orange)
                    itemView.setBackgroundResource(R.drawable.bg_card_normal)
                    itemView.alpha = 1f
                    itemView.isEnabled = true
                    itemView.setOnClickListener { onClick(item) }
                }
                Status.NEW -> {
                    dot.setBackgroundResource(R.drawable.bg_dot_red)
                    itemView.setBackgroundResource(R.drawable.bg_card_normal)
                    itemView.alpha = 1f
                    itemView.isEnabled = true
                    itemView.setOnClickListener { onClick(item) }
                }
            }
        }

        private enum class Status { EXPIRED, COMPLETED, DOING, NEW }
    }
}

/** —— 工具扩展 —— */

// 是否过期（以本地时区为准）
private fun ClassAssignmentItem.isExpired(nowMs: Long): Boolean {
    val due = this.expireTime.toEpochMillis() ?: return false
    return nowMs > due
}

// [yyyy,MM,dd,HH,mm,ss?] -> epoch millis（Calendar 月份需 -1）
private fun List<Int>?.toEpochMillis(): Long? {
    if (this == null || this.isEmpty()) return null
    val y   = this.getOrNull(0) ?: return null
    val m   = this.getOrNull(1) ?: 1
    val d   = this.getOrNull(2) ?: 1
    val h   = this.getOrNull(3) ?: 0
    val mi  = this.getOrNull(4) ?: 0
    val s   = this.getOrNull(5) ?: 0
    return Calendar.getInstance().apply {
        set(Calendar.MILLISECOND, 0)
        set(y, m - 1, d, h, mi, s)
    }.timeInMillis
}

// [yyyy,MM,dd,HH,mm,ss?] -> "yyyy-MM-dd HH:mm"
private fun List<Int>?.toDateTimeText(): String {
    if (this == null || this.isEmpty()) return ""
    val y   = this.getOrNull(0) ?: return ""
    val m   = this.getOrNull(1) ?: 1
    val d   = this.getOrNull(2) ?: 1
    val h   = this.getOrNull(3) ?: 0
    val mi  = this.getOrNull(4) ?: 0
    return String.format("%04d-%02d-%02d %02d:%02d", y, m, d, h, mi)
}
