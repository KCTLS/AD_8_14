package com.example.adproject

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.adproject.api.ApiClient
import com.example.adproject.model.Result
import com.example.adproject.model.UploadQuestionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import retrofit2.Response

class UploadQuestionActivity : AppCompatActivity() {

    private val api by lazy { ApiClient.api }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var etQuestion: EditText
    private lateinit var etImage: EditText
    private lateinit var choicesContainer: LinearLayout
    private lateinit var btnAddChoice: View
    private lateinit var btnSubmit: View
    private lateinit var progress: View

    private lateinit var tvGrade: TextView
    private lateinit var tvSubject: TextView
    private lateinit var tvCategory: TextView
    private lateinit var tvTopic: TextView

    private var selectedGrade: String? = null
    private var selectedSubject: String? = null
    private var selectedCategory: String? = null
    private var selectedTopic: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload_question)

        // 处理状态栏遮挡（根布局 id 要是 activity_upload_question.xml 的根 id，比如 @+id/main）
        findViewById<View>(R.id.main)?.let { root ->
            ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
                insets
            }
        }

        // 顶部返回
        findViewById<View>(R.id.topAppBar)?.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // 绑定控件
        etQuestion = findViewById(R.id.etQuestion)
        etImage = findViewById(R.id.etImage)
        choicesContainer = findViewById(R.id.choicesContainer)
        btnAddChoice = findViewById(R.id.btnAddChoice)
        btnSubmit = findViewById(R.id.btnSubmit)
        progress = findViewById(R.id.progress)

        tvGrade = findViewById(R.id.tvGrade)
        tvSubject = findViewById(R.id.tvSubject)
        tvCategory = findViewById(R.id.tvCategory)
        tvTopic = findViewById(R.id.tvTopic)

        // 默认两行选项
        addChoiceRow()
        addChoiceRow()

        // 默认筛选：取 FacetDefaults 第一项
        selectedGrade = FacetDefaults.grade.first()
        tvGrade.text = selectedGrade

        selectedSubject = FacetDefaults.subject.first()
        tvSubject.text = selectedSubject

        selectedCategory = FacetDefaults.category.first()
        tvCategory.text = selectedCategory

        selectedTopic = FacetDefaults.topic.first()
        tvTopic.text = selectedTopic

        // 点击选择
        tvGrade.setOnClickListener {
            pickOne("Select Grade", FacetDefaults.grade, selectedGrade) {
                selectedGrade = it; tvGrade.text = it
            }
        }
        tvSubject.setOnClickListener {
            pickOne("Select Subject", FacetDefaults.subject, selectedSubject) {
                selectedSubject = it; tvSubject.text = it
            }
        }
        tvCategory.setOnClickListener {
            pickOne("Select Category", FacetDefaults.category, selectedCategory) {
                selectedCategory = it; tvCategory.text = it
            }
        }
        tvTopic.setOnClickListener {
            pickOne("Select Topic", FacetDefaults.topic, selectedTopic) {
                selectedTopic = it; tvTopic.text = it
            }
        }

        btnAddChoice.setOnClickListener { addChoiceRow() }
        btnSubmit.setOnClickListener { submit() }
    }

    /** 增加一行可编辑选项（需要有 res/layout/item_choice_row.xml） */
    private fun addChoiceRow(prefill: String = "") {
        val row = layoutInflater.inflate(R.layout.item_choice_row, choicesContainer, false)
        val et = row.findViewById<EditText>(R.id.etChoice)
        val rb = row.findViewById<RadioButton>(R.id.rbCorrect)
        val del = row.findViewById<ImageButton>(R.id.btnRemove)

        et.setText(prefill)

        // 单选互斥
        rb.setOnClickListener {
            for (i in 0 until choicesContainer.childCount) {
                val other = choicesContainer.getChildAt(i)
                    .findViewById<RadioButton>(R.id.rbCorrect)
                if (other != rb) other.isChecked = false
            }
        }

        // 删除（至少保留 2 项）
        del.setOnClickListener {
            if (choicesContainer.childCount <= 2) {
                toast("至少需要两个选项")
            } else {
                choicesContainer.removeView(row)
            }
        }

        choicesContainer.addView(row)
    }

    /** 收集所有非空选项 + 找到被勾选为正确答案的下标 */
    private fun collectChoices(): Pair<List<String>, Int> {
        val list = mutableListOf<String>()
        var answerIndex = -1
        for (i in 0 until choicesContainer.childCount) {
            val child = choicesContainer.getChildAt(i)
            val text = child.findViewById<EditText>(R.id.etChoice)
                .text?.toString()?.trim().orEmpty()
            val checked = child.findViewById<RadioButton>(R.id.rbCorrect).isChecked
            if (text.isNotEmpty()) {
                val idx = list.size
                list += text
                if (checked) answerIndex = idx
            }
        }
        return list to answerIndex
    }

    /** 提交：options 发 List<String>；answer 发 0/1/2 下标（Int） */
    private fun submit() {
        val question = etQuestion.text.toString().trim()
        if (question.isEmpty()) { toast("Question cannot be empty"); return }

        val (choices, answerIndex) = collectChoices()
        if (choices.size < 2) { toast("Please enter at least two options."); return }
        if (answerIndex !in choices.indices) { toast("Please select a correct answer."); return }

        val body = UploadQuestionRequest(
            question = question,
            subject  = selectedSubject ?: FacetDefaults.subject.first(),
            category = selectedCategory ?: FacetDefaults.category.first(),
            topic    = selectedTopic ?: FacetDefaults.topic.first(),
            grade    = selectedGrade ?: FacetDefaults.grade.first(),
            image    = etImage.text.toString().trim().ifEmpty { null },
            options  = choices,
            answer   = answerIndex        // 发 0-based 下标
        )

        Log.d("UploadQuestion", "payload options=$choices answerIndex=$answerIndex")

        progress.visibility = View.VISIBLE
        btnSubmit.isEnabled = false

        scope.launch {
            try {
                val resp: Response<Result<String>> = api.uploadQuestion(body)
                Log.d("UploadQuestion", "HTTP ${resp.code()} url=${resp.raw().request.url}")
                val r = resp.body()
                if (resp.isSuccessful && r?.code == 1) {
                    toast(r.data ?: "Upload successfully")
                    finish()
                } else {
                    toast(r?.msg ?: "Upload failed（${resp.code()}）")
                }
            } catch (e: Exception) {
                Log.e("UploadQuestion", "exception", e)
                toast("network error：${e.message}")
            } finally {
                progress.visibility = View.GONE
                btnSubmit.isEnabled = true
            }
        }
    }

    /** 选择器：默认选中当前值 */
    private fun pickOne(
        title: String,
        options: List<String>,
        current: String?,
        onPicked: (String) -> Unit
    ) {
        var picked = options.indexOf(current).coerceAtLeast(-1)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(options.toTypedArray(), picked) { _, which -> picked = which }
            .setPositiveButton("Apply") { d, _ ->
                val value = if (picked in options.indices) options[picked] else options.first()
                onPicked(value); d.dismiss()
            }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
