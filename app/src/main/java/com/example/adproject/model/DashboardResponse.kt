package com.example.adproject.model

data class DashboardResponse(
    val code: Int,
    val msg: String?,
    val data: DashboardData?
)

data class DashboardData(
    val accuracyRates: List<Float>?
)