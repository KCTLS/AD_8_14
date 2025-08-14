package com.example.adproject.model

data class ViewClassResponse(
    val code: Int,
    val msg: String?,
    val data: ClassListData?
)

data class ClassListData(
    val list: List<StudentClass>
)

data class StudentClass(
    val classId: Int,
    val className: String,
    val description: String
)
