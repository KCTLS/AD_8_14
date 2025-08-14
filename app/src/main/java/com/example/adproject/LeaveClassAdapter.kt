package com.example.adproject

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.adproject.model.StudentClass

class JoinedClassAdapter(
    private val onLeaveClick: (StudentClass) -> Unit
) : RecyclerView.Adapter<JoinedClassAdapter.VH>() {

    private val items = mutableListOf<StudentClass>()

    fun submit(list: List<StudentClass>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun remove(classId: Int) {
        val idx = items.indexOfFirst { it.classId == classId }
        if (idx >= 0) {
            items.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_joined_class, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], onLeaveClick)
    }

    override fun getItemCount() = items.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvName = v.findViewById<TextView>(R.id.tvName)
        private val tvDesc = v.findViewById<TextView>(R.id.tvDesc)
        private val btnLeave = v.findViewById<Button>(R.id.btnLeave)

        fun bind(item: StudentClass, onLeaveClick: (StudentClass) -> Unit) {
            tvName.text = item.className
            tvDesc.text = item.description
            btnLeave.setOnClickListener { onLeaveClick(item) }
        }
    }
}
