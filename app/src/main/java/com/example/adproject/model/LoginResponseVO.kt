// model/LoginResultVO.kt
package com.example.adproject.model

data class LoginResultVO(
    val status: String? = null,            // "ok" / "error"
    val type: String? = null,              // "student"/"account"
    val currentAuthority: String? = null,  // "student"/"guest"
    val userId: Long? = null,
    val userName: String? = null,
    val token: String? = null,
    val message: String? = null,           // 后端可能用 message
    val msg: String? = null                // 有些后端用 msg
)
