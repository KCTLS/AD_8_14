package com.example.adproject.api

import com.example.adproject.model.AnnouncementResponse
import com.example.adproject.model.AnswerResponse
import com.example.adproject.model.DashboardResponse
import com.example.adproject.model.JoinClassResponse
import com.example.adproject.model.LeaveClassResponse
import com.example.adproject.model.LoginRequest
import com.example.adproject.model.LoginResultVO
import com.example.adproject.model.QsInform
import com.example.adproject.model.QsResultDTO
import com.example.adproject.model.RegisterRequest
import com.example.adproject.model.Result
import com.example.adproject.model.SelectAssignmentResponse
import com.example.adproject.model.SelectQuestionDTO
import com.example.adproject.model.UploadQuestionDTO   // ✅
import com.example.adproject.model.ViewClassResponse
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

interface ApiService {

    /** POST /student/login ，JSON 请求体（@RequestBody LoginDTO） */
    @POST("login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResultVO>

    // 注册（@RequestBody RegisterRequest）
    @POST("register")
    suspend fun register(@Body body: RegisterRequest): Response<Result<Unit>>

    // ====== 题库 ======
    @GET("viewQuestion")
    suspend fun viewQuestion(
        @Query("keyword") keyword: String = "",
        @Query("questionName") questionName: String = "",
        @Query("grade") grade: String = "",
        @Query("subject") subject: String = "",
        @Query("topic") topic: String = "",
        @Query("category") category: String = "",
        @Query("page") page: Int,
        @Query("questionIndex") questionIndex: Int = -1
    ): Response<QsResultDTO<QsInform>>

    @GET("doquestion")
    suspend fun getQuestionById(@Query("id") id: Int): Response<Result<SelectQuestionDTO>>

    // 可选：原始 JSON
    @GET("doquestion")
    suspend fun getQuestionDetailRaw(@Query("id") id: Int): Response<JsonObject>

    @GET("answerQuestion")
    suspend fun answerQuestion(
        @Query("id") id: Int,
        @Query("correct") correct: Int,
        @Query("param") param: Int
    ): Response<AnswerResponse>

    // ====== Dashboard ======
    @GET("dashboard")
    suspend fun dashboard(): Response<DashboardResponse>

    // ====== 推荐 ======
    @PUT("recommend")
    suspend fun triggerRecommend(): Response<JsonObject>

    @GET("getRecommend")
    suspend fun getRecommendIds(): Response<JsonObject>

    // ====== 班级 & 公告 ======
    @GET("viewClass")
    suspend fun viewClass(): Response<ViewClassResponse>

    @GET("selectAnnouncement")
    suspend fun selectAnnouncement(@Query("classId") classId: Int): Response<AnnouncementResponse>

    @POST("checkAnnouncement")
    suspend fun checkAnnouncement(@Query("announcementId") announcementId: Int): Response<Result<Unit>>

    @POST("joinClass")
    suspend fun joinClass(
        @Query("accessType") accessType: String, // "byName" / "byLink"
        @Query("key") key: String                // name 或 token
    ): Response<JoinClassResponse>

    @POST("leaveClass")
    suspend fun leaveClass(@Query("classId") classId: Long): Response<LeaveClassResponse>

    @GET("selectClass")
    suspend fun selectClass(
        @Query("classId") classId: Int
    ): Response<com.example.adproject.model.SelectClassDetailResponse>

    @GET("selectAssignment")
    suspend fun selectAssignment(@Query("assignmentId") assignmentId: Int)
            : Response<SelectAssignmentResponse>

    // 完成作业
    @POST("finishAssignment")
    suspend fun finishAssignment(
        @Query("assignmentId") assignmentId: Int,
        @Query("whether") whether: Int,
        @Query("accuracy") accuracy: Double ): Result<String> // 👈 改这里

    // ====== 题目上传（返回 data 为 null，改为 Any? 以兼容） ======
    @POST("upload-question")
    suspend fun uploadQuestion(@Body body: UploadQuestionDTO): Response<Result<Any?>>
}
