package com.example.adproject.model

data class SelectAssignmentResponse(
    val code: Int, val msg: String?, val data: AssignmentQList?
)
data class AssignmentQList(val list: List<AssignmentQuestion>)
data class AssignmentQuestion(
    val id: Int,
    val image: String?,          // base64，可空
    val question: String,
    val choices: List<String>,
    val answer: Int              // 正确答案索引（先不用）
)