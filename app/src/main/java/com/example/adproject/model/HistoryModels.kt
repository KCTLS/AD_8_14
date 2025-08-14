package com.example.adproject.model

data class HistoryRecord(
    val questionId: Int,
    val isCorrect: Int // 0/1
)

data class HistoryData(
    val records: List<HistoryRecord>
)
