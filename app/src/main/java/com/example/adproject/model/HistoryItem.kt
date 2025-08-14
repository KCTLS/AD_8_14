package com.example.adproject.model

data class HistoryItem(
    val questionId: Int,
    val isCorrect: Boolean,
    var title: String = "Question #$questionId",
    var imageBase64: String? = null
)
