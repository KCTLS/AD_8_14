package com.example.adproject

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.adproject.api.ApiClient
import com.example.adproject.model.AssignmentQuestion
import com.example.adproject.model.SelectAssignmentResponse
import com.example.adproject.model.SelectQuestionDTO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.appbar.MaterialToolbar
import java.math.BigDecimal
import java.math.RoundingMode

class AssignmentDoActivity : AppCompatActivity() {

    private val api by lazy { ApiClient.api }

    private var assignmentId = -1
    private var titleName = ""

    private lateinit var toolbar: MaterialToolbar
    private lateinit var rv: RecyclerView
    private lateinit var tvProgress: TextView
    private lateinit var btnFinish: Button
    private lateinit var progress: View

    private val adapter by lazy { DoAdapter(::onSelected) }
    private var total = 0
    private var questions: List<AssignmentQuestion> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_assignment_do)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, v.paddingBottom)
            ins
        }

        assignmentId = intent.getIntExtra("assignmentId", -1)
        titleName = intent.getStringExtra("assignmentName") ?: ""

        toolbar = findViewById(R.id.topAppBar)
        rv = findViewById(R.id.rv)
        tvProgress = findViewById(R.id.tvProgress)
        btnFinish = findViewById(R.id.btnFinish)
        progress = findViewById(R.id.progress)

        toolbar.title = if (titleName.isNotBlank()) titleName else "Assignment"
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        btnFinish.setOnClickListener { lifecycleScope.launch { submitAssignment() } }

        loadQuestions()
    }

    private fun setLoading(b: Boolean) {
        progress.visibility = if (b) View.VISIBLE else View.GONE
    }

    private fun loadQuestions() {
        if (assignmentId <= 0) {
            Toast.makeText(this, "Missing assignmentId", Toast.LENGTH_SHORT).show()
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            questions = withContext(Dispatchers.IO) {
                try {
                    val resp = api.selectAssignment(assignmentId) // Response<SelectAssignmentResponse>
                    val body: SelectAssignmentResponse? = resp.body()
                    if (resp.isSuccessful && body?.code == 1) {
                        body.data?.list.orEmpty()
                    } else {
                        Log.w("AssignmentDo", "selectAssignment fail: http=${resp.code()} code=${body?.code} msg=${body?.msg}")
                        emptyList()
                    }
                } catch (e: Exception) {
                    Log.e("AssignmentDo", "selectAssignment error", e)
                    emptyList()
                }
            }
            setLoading(false)

            adapter.submit(questions, assignmentId)
            total = questions.size
            refreshProgress()
        }
    }

    private fun onSelected(qid: Int, choice: Int) {
        AssignmentProgressStore.setAnswer(this, assignmentId, qid, choice)
        refreshProgress()
    }

    private fun refreshProgress() {
        val answered = AssignmentProgressStore.get(this, assignmentId).answers.size
        tvProgress.text = "$answered/$total"
    }

    /** 点击提交按钮时调用 */
    private suspend fun submitAssignment() {
        val progressState = AssignmentProgressStore.get(this, assignmentId)
        if (progressState.answers.size != total) {
            Toast.makeText(this, "Some questions are unanswered", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        // 并发获取每题正确答案并判定正确与否
        val results = coroutineScope {
            questions.map { q ->
                async(Dispatchers.IO) {
                    try {
                        val resp = api.getQuestionById(q.id) // Response<Result<SelectQuestionDTO>>
                        val body = resp.body()
                        if (resp.isSuccessful && body?.code == 1 && body.data != null) {
                            val dto: SelectQuestionDTO = body.data!!
                            val raw = dto.answer
                            val max = dto.choices.size
                            val correctIndex = if (raw in 1..max) raw - 1 else raw // 兼容 1-based/0-based
                            val my = progressState.answers[q.id]
                            (my != null && my == correctIndex)
                        } else {
                            Log.w("AssignmentDo", "getQuestionById fail: id=${q.id} http=${resp.code()} code=${body?.code} msg=${body?.msg}")
                            false
                        }
                    } catch (e: Exception) {
                        Log.e("AssignmentDo", "getQuestionById error id=${q.id}", e)
                        false
                    }
                }
            }.awaitAll()
        }

        val correctCount = results.count { it }
        val accuracy = if (total > 0) (correctCount.toDouble() / total) * 100.0 else 0.0
        // 四舍五入到 2 位小数（不受 Locale 影响）
        val accuracyRounded = BigDecimal(accuracy).setScale(2, RoundingMode.HALF_UP).toDouble()

        // 提交完成信息（POST）——接口直接返回 Result<String>
        val ok = withContext(Dispatchers.IO) {
            try {
                val r = api.finishAssignment(assignmentId, 1, accuracyRounded) // Result<String>
                if (r.code == 1) {
                    true
                } else {
                    Log.w("AssignmentDo", "finishAssignment business fail: code=${r.code}, msg=${r.msg}, data=${r.data}")
                    false
                }
            } catch (e: Exception) {
                Log.e("AssignmentDo", "finishAssignment error", e)
                false
            }
        }

        setLoading(false)

        if (ok) {
            AssignmentProgressStore.setCompleted(this, assignmentId, true)
            Toast.makeText(this, "Submission successful, accuracy: $accuracyRounded%", Toast.LENGTH_LONG).show()
            setResult(RESULT_OK)
            finish()
        } else {
            Toast.makeText(this, "Submission failed, please try again later", Toast.LENGTH_SHORT).show()
        }
    }
}