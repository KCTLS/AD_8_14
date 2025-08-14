package com.example.adproject.model

import com.google.gson.JsonElement

data class JoinClassResponse(
    val code: Int,
    val msg: String?,
    val data: JsonElement? = null // 现在后端是 null；以后若返回班级信息可直接用
)
