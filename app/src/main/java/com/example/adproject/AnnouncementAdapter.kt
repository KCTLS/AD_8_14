package com.example.adproject

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.example.adproject.model.AnnouncementItem
import com.example.adproject.model.displayTime

class AnnouncementAdapter(
    private val ctx: Context,
    private val data: MutableList<AnnouncementItem> = mutableListOf()
) : BaseAdapter() {

    private var onItemClick: ((AnnouncementItem) -> Unit)? = null
    private var onMarkRead: ((AnnouncementItem) -> Unit)? = null

    fun setOnItemClick(block: (AnnouncementItem) -> Unit) { onItemClick = block }
    fun setOnMarkRead(block: (AnnouncementItem) -> Unit) { onMarkRead = block }

    fun setItems(list: List<AnnouncementItem>) {
        data.clear()
        data.addAll(list)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = data.size
    override fun getItem(position: Int): AnnouncementItem = data[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val v = convertView ?: LayoutInflater.from(ctx)
            .inflate(R.layout.row_announcement, parent, false)

        val item = getItem(position)

        v.findViewById<TextView>(R.id.tvTitle).text = item.title
        v.findViewById<TextView>(R.id.tvSnippet).text = item.content
        v.findViewById<TextView>(R.id.tvTime).text = item.displayTime()

        v.findViewById<TextView?>(R.id.tvAuthor)?.let { tvAuthor ->
            val clsName = item.className
            if (!clsName.isNullOrBlank()) {
                tvAuthor.text = clsName
                tvAuthor.visibility = View.VISIBLE
            } else {
                tvAuthor.visibility = View.GONE
            }
        }

        // 红点：仅根据后端 status 判断（0 未读 / 1 已读）
        val dot = v.findViewById<View?>(R.id.dotUnread)
        dot?.visibility = if ((item.status ?: 0) == 1) View.GONE else View.VISIBLE

        // 点击：交给外层去“标记已读并刷新”
        v.setOnClickListener {
            onItemClick?.invoke(item)
            onMarkRead?.invoke(item)
        }

        return v
    }
}
