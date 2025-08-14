package com.example.adproject.model

data class AnswerRecord(
    val questionId: Int,
    val isCorrect: Boolean,
    var title: String? = null,
    var imageBase64: String? = null
)
