package com.example.adproject

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.adproject.api.ApiClient
import com.example.adproject.model.Result
import com.example.adproject.model.UploadQuestionDTO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response

class UploadQuestionActivity : AppCompatActivity() {

    private lateinit var etQuestion: EditText
    private lateinit var etOptions: EditText
    private lateinit var etAnswer: EditText
    private lateinit var etImage: EditText
    private lateinit var btnSubmit: Button
    private lateinit var progress: ProgressBar

    private lateinit var btnGrade: Button
    private lateinit var btnSubject: Button
    private lateinit var btnCategory: Button
    private lateinit var btnTopic: Button

    // 当前选择（null 表示 “other”）
    private var selectedGrade: String? = null
    private var selectedSubject: String? = null
    private var selectedCategory: String? = null
    private var selectedTopic: String? = null

    // 当前候选（会按其余条件动态收紧）
    private var currentSubjectOptions  = FacetDefaults.subject.toMutableList()
    private var currentCategoryOptions = FacetDefaults.category.toMutableList()
    private var currentTopicOptions    = FacetDefaults.topic.toMutableList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_upload_question)

        val root = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, bars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        etQuestion = findViewById(R.id.etQuestion)
        etOptions  = findViewById(R.id.etOptions)
        etAnswer   = findViewById(R.id.etAnswer)
        etImage    = findViewById(R.id.etImage)
        btnSubmit  = findViewById(R.id.btnSubmit)
        progress   = findViewById(R.id.progress)

        btnGrade   = findViewById(R.id.btnGrade)
        btnSubject = findViewById(R.id.btnSubject)
        btnCategory= findViewById(R.id.btnCategory)
        btnTopic   = findViewById(R.id.btnTopic)

        // 绑定四个按钮
        btnGrade.setOnClickListener {
            val options = FacetDefaults.grade
            showSingleChoiceDialog("Select Grade", options, selectedGrade) { chosen ->
                selectedGrade = chosen
                btnGrade.text = chosen ?: "Grade（默认 other）"
                rebuildFacetOptionsFromServer()
            }
        }
        btnSubject.setOnClickListener {
            lifecycleScope.launch {
                val options = fetchFacetOptions("subject")
                showSingleChoiceDialog("Select Subject",
                    options.ifEmpty { currentSubjectOptions }, selectedSubject
                ) { chosen ->
                    selectedSubject = chosen
                    btnSubject.text = chosen ?: "Subject（默认 other）"
                    rebuildFacetOptionsFromServer()
                }
            }
        }
        btnCategory.setOnClickListener {
            lifecycleScope.launch {
                val options = fetchFacetOptions("category")
                showSingleChoiceDialog("Select Category",
                    options.ifEmpty { currentCategoryOptions }, selectedCategory
                ) { chosen ->
                    selectedCategory = chosen
                    btnCategory.text = chosen ?: "Category（默认 other）"
                    rebuildFacetOptionsFromServer()
                }
            }
        }
        btnTopic.setOnClickListener {
            lifecycleScope.launch {
                val options = fetchFacetOptions("topic")
                showSingleChoiceDialog("Select Topic",
                    options.ifEmpty { currentTopicOptions }, selectedTopic
                ) { chosen ->
                    selectedTopic = chosen
                    btnTopic.text = chosen ?: "Topic（默认 other）"
                }
            }
        }

        btnSubmit.setOnClickListener { submit() }
    }

    // 和练习页一样：忽略当前 facet，按其余条件抓 1~3 页作为候选
    private suspend fun fetchFacetOptions(facet: String): List<String> = withContext(Dispatchers.IO) {
        val api = ApiClient.api
        val result = linkedSetOf<String>()
        var page = 1
        val maxPages = 3
        while (page <= maxPages) {
            val resp = api.viewQuestion(
                keyword = "",
                questionName = "",
                grade     = if (facet == "grade") ""    else (selectedGrade ?: ""),
                subject   = if (facet == "subject") ""  else (selectedSubject ?: ""),
                category  = if (facet == "category") "" else (selectedCategory ?: ""),
                topic     = if (facet == "topic") ""    else (selectedTopic ?: ""),
                page = page,
                questionIndex = -1
            )
            if (!resp.isSuccessful) break
            val items = resp.body()?.data?.items ?: emptyList()
            if (items.isEmpty()) break
            for (q in items) {
                val v = when (facet) {
                    "grade"    -> q.grade
                    "subject"  -> q.subject
                    "category" -> q.category
                    "topic"    -> q.topic
                    else -> null
                }?.trim()
                if (!v.isNullOrEmpty()) result += v
            }
            page++
        }
        result.toList().sorted()
    }

    private fun showSingleChoiceDialog(
        title: String,
        options: List<String>,
        current: String?,
        onPicked: (String?) -> Unit
    ) {
        val display = options.toTypedArray()
        var chosenIndex = if (current != null) options.indexOf(current) else -1

        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(display, chosenIndex) { _, which -> chosenIndex = which }
            .setPositiveButton("Apply") { d, _ ->
                onPicked(if (chosenIndex in options.indices) options[chosenIndex] else null)
                d.dismiss()
            }
            .setNegativeButton("Clear") { d, _ ->
                onPicked(null); d.dismiss()
            }
            .setNeutralButton("Cancel") { d, _ -> d.dismiss() }
            .show()
    }

    /** 选择变化后，用当前选择从服务器数据“收紧”候选集（与练习页一致） */
    private fun rebuildFacetOptionsFromServer() {
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                val resp = ApiClient.api.viewQuestion(
                    keyword = "",
                    questionName = "",
                    grade = selectedGrade ?: "",
                    subject = selectedSubject ?: "",
                    topic = selectedTopic ?: "",
                    category = selectedCategory ?: "",
                    page = 1,
                    questionIndex = -1
                )
                if (resp.isSuccessful) resp.body()?.data?.items.orEmpty() else emptyList()
            }
            val subjects = data.mapNotNull { it.subject?.trim() }.filter { it.isNotEmpty() }.toSet()
            val categories = data.mapNotNull { it.category?.trim() }.filter { it.isNotEmpty() }.toSet()
            val topics = data.mapNotNull { it.topic?.trim() }.filter { it.isNotEmpty() }.toSet()

            if (subjects.isNotEmpty())   currentSubjectOptions  = subjects.sorted().toMutableList()
            if (categories.isNotEmpty()) currentCategoryOptions = categories.sorted().toMutableList()
            if (topics.isNotEmpty())     currentTopicOptions    = topics.sorted().toMutableList()

            if (selectedSubject != null && selectedSubject !in currentSubjectOptions) {
                selectedSubject = null; btnSubject.text = "Subject（默认 other）"
            }
            if (selectedCategory != null && selectedCategory !in currentCategoryOptions) {
                selectedCategory = null; btnCategory.text = "Category（默认 other）"
            }
            if (selectedTopic != null && selectedTopic !in currentTopicOptions) {
                selectedTopic = null; btnTopic.text = "Topic（默认 other）"
            }
        }
    }

    private fun submit() {
        val question = etQuestion.text.toString().trim()
        val optionsTxt = etOptions.text.toString().trim()
        val answer = etAnswer.text.toString().trim()

        if (question.isEmpty()) { toast("Question 不能为空"); return }
        if (optionsTxt.isEmpty()) { toast("Options 不能为空"); return }
        if (answer.isEmpty()) { toast("Answer 不能为空"); return }

        val options: List<Int> = try {
            optionsTxt.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }.map { it.toInt() }
        } catch (_: Exception) {
            toast("Options 格式错误，例如：1,2,3"); return
        }

        val body = UploadQuestionDTO(
            answer   = answer,
            category = selectedCategory ?: "other",
            grade    = selectedGrade ?: "other",
            image    = etImage.text.toString(),
            options  = options,
            question = question,
            subject  = selectedSubject ?: "other",
            topic    = selectedTopic ?: "other"
        )

        progress.visibility = View.VISIBLE
        btnSubmit.isEnabled = false

        lifecycleScope.launch {
            try {
                val resp: Response<Result<Any?>> = ApiClient.api.uploadQuestion(body)
                Log.d("UploadQuestion", "HTTP ${resp.code()} url=${resp.raw().request.url}")
                if (resp.isSuccessful) {
                    val r = resp.body()
                    Log.d("UploadQuestion", "body=$r")
                    if (r?.code == 1) {
                        toast("上传成功")
                        finish()
                    } else {
                        toast("上传失败：" + (r?.msg ?: "未知错误"))
                    }
                } else {
                    toast("HTTP ${resp.code()}")
                }
            } catch (e: Exception) {
                Log.e("UploadQuestion", "exception", e)
                toast("请求异常：${e.javaClass.simpleName}: ${e.message}")
            } finally {
                progress.visibility = View.GONE
                btnSubmit.isEnabled = true
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
