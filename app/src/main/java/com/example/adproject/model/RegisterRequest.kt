package com.example.adproject.model

data class RegisterRequest(
    val address: String,
    val email: String,
    val gender: String,      // "male"/"female"/"other"（按你后端定义）
    val group: String,
    val name: String,
    val password: String,
    val phone: String,
    val signature: String,
    val tags: List<String> = emptyList(),
    val title: String
)
