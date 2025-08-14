package com.example.adproject

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.example.adproject.model.QsInform

// QuestionAdapter.kt
class QuestionAdapter(context: Context, private val questions: MutableList<QsInform>) :
    ArrayAdapter<QsInform>(context, 0, questions) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var itemView = convertView
        if (itemView == null) {
            itemView = LayoutInflater.from(context).inflate(R.layout.list_item_question, parent, false)
        }

        val currentQuestion = getItem(position)

        val questionText = itemView?.findViewById<TextView>(R.id.questionText)
        val questionImage = itemView?.findViewById<ImageView>(R.id.questionImage)

        // 从 QsInform 对象中获取数据
        questionText?.text = currentQuestion?.question

        val base64Data = currentQuestion?.image

        if (base64Data.isNullOrEmpty()) {
            // 情况 ①：image 为空 → 显示占位图
            questionImage?.setImageResource(R.drawable.placeholder_image)
        } else {
            try {
                // 情况 ②：有值，尝试 Base64 解码
                val pureBase64 = base64Data.substringAfter(",") // 去掉前缀 if 存在
                val decodedBytes = android.util.Base64.decode(pureBase64, android.util.Base64.DEFAULT)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

                if (bitmap != null) {
                    questionImage?.setImageBitmap(bitmap)
                } else {
                    // 情况 ③：解析失败 → 显示错误图
                    questionImage?.setImageResource(R.drawable.error_image)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 解码失败 → 显示错误图
                questionImage?.setImageResource(R.drawable.error_image)
            }
        }

        return itemView!!
    }

    // 在 QuestionAdapter 里加一个公开方法
    fun getData(): List<QsInform> = questions  // questions 是你内部维护的列表变量名

    fun updateData(newData: List<QsInform>) {
        questions.clear()
        questions.addAll(newData)
        notifyDataSetChanged()
    }

    fun addItems(newData: List<QsInform>) {
        questions.addAll(newData)
        notifyDataSetChanged()
    }
}