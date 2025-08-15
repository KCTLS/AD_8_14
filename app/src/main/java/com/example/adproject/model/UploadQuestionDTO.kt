// model/UploadQuestionRequest.kt
package com.example.adproject.model

// 用于发给服务端的 payload（键名对齐后端）
data class UploadQuestionRequest(
    val question: String,
    val subject: String,
    val category: String,
    val topic: String,
    val grade: String,
    val image: String? = null,
    val options: List<String>,  // 和后端的字段对齐
    val answer: Int            // 和后端的类型对齐
)

