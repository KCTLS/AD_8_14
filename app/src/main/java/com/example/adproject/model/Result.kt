package com.example.adproject.model

data class Result<T>(
    val code: Int,
    val msg: String?,
    val data: T?
)
