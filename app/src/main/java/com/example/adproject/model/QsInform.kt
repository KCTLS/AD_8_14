package com.example.adproject.model

data class QsInform(
    val id: Int,
    val image: String?, // 图片URL或Base64编码的字符串
    val question: String,
    val choices: List<String>, // 后端返回的JSON数组会自动解析为List
    val answer: Int,
    val hint: String?,
    val task: String?,
    val grade: String,
    val subject: String,
    val topic: String,
    val category: String,
    val skill: String?,
    val lecture: String?,
    val solution: String?
)