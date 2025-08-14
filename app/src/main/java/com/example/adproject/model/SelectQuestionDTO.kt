package com.example.adproject.model

data class SelectQuestionDTO(
    val id: Int,
    val image: String?, // 如果是 base64 字符串
    val question: String,
    val choices: List<String>,
    val answer: Int
)
