package com.example.adproject.model

data class QsResultDTO<T>(
    val data: Data<T>?,
    val errorMessage: String?
) {
    data class Data<U>(
        val items: List<U>?,
        val totalCount: Int,
        val page: Int,
        val pageSize: Int,
        val errorMessage: String?
    )
}