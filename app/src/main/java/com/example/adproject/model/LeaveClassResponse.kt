package com.example.adproject.model

data class LeaveClassResponse(
    val code: Int,
    val msg: String?
    // 如果后端还会返回别的字段，再加；不需要就保持精简，避免 Gson 类型不匹配
)