package com.example.adproject.model

data class RecommendedPractice(
    val id: Int,
    var title: String,
    var subject: String? = "—",
    var grade: String? = "—",
    var questions: Int = 10,
    var difficulty: String? = "Medium",
    var imageBase64: String? = null   // 新增：题目图片（Base64）
)
