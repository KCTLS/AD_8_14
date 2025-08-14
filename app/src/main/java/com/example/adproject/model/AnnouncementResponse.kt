// model/AnnouncementResponse.kt
package com.example.adproject.model

import com.google.gson.annotations.SerializedName

data class AnnouncementResponse(
    val code: Int,
    val msg: String?,
    val data: AnnouncementList?
)

data class AnnouncementList(
    val list: List<AnnouncementItem>
)

data class AnnouncementItem(
    // 兼容后端字段名是 id 或 announcementId 的两种情况
    @SerializedName(value = "announcementId", alternate = ["id"])
    val announcementId: Int? = null,

    val title: String,
    val content: String,

    // 你现在的代码里用的是 [yyyy, M, d, HH, mm] 数组；保持不变
    val createTime: List<Int>,

    val classId: Int? = null,        // 按需你在前端补上的
    val className: String? = null,   // 按需你在前端补上的

    // 0=未读, 1=已读（后端返回即可；若后端暂时没给，可为空）
    val status: Int? = null
)

// 小工具：把 createTime 转成可读字符串
fun AnnouncementItem.displayTime(): String {
    if (createTime.size < 5) return ""
    val y = createTime[0]
    val m = createTime[1]
    val d = createTime[2]
    val hh = createTime[3]
    val mm = createTime[4]
    return String.format("%04d-%02d-%02d %02d:%02d", y, m, d, hh, mm)
}
