package com.example.adproject.model

data class SelectClassDetailResponse(
    val code: Int,
    val msg: String?,
    val data: ClassDetailData?
)

data class ClassDetailData(
    val classId: Int,
    val className: String,
    val list: List<ClassAssignmentItem>
)

data class ClassAssignmentItem(
    val assignmentId: Int,
    val assignmentName: String,
    val expireTime: List<Int>?,   // [yyyy, MM, dd, HH, mm, ss]
    val whetherFinish: Int,       // 0/1
    val finishTime: List<Int>?    // 同上，可能为 null
)
