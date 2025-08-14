package com.example.adproject.model

data class UploadQuestionDTO(
    val answer: String,      // "1"
    val category: String,    // "other"
    val grade: String,       // "other"
    val image: String,       // 可为空字符串
    val options: List<Int>,  // [1,2,3]
    val question: String,    // "123"
    val subject: String,     // "other"
    val topic: String        // "other"
)
