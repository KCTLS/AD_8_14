package com.example.adproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.adproject.api.ApiClient
import com.example.adproject.model.ClassAssignmentItem
import com.example.adproject.model.SelectClassDetailResponse
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class ClassAssignmentActivity : AppCompatActivity() {

    private val api by lazy { ApiClient.api }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var rv: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var progress: View

    private val adapter by lazy { AssignmentAdapter(::onAssignmentClick) }

    private var classId: Int = -1
    private var className: String = ""

    // 做题页返回后刷新列表
    private val doLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        loadAssignments()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_class_assignment)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, v.paddingBottom)
            insets
        }

        classId = intent.getIntExtra("classId", -1)
        className = intent.getStringExtra("className") ?: ""

        toolbar = findViewById(R.id.topAppBar)
        rv = findViewById(R.id.rvAssignments)
        emptyView = findViewById(R.id.emptyView)
        progress = findViewById(R.id.progress)

        toolbar.title = if (className.isNotBlank()) "$className Assignments" else "Assignments"
        toolbar.setNavigationOnClickListener { finish() }

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        loadAssignments()
    }

    override fun onResume() {
        super.onResume()
        // 返回本页时确保列表状态最新（如过期、已完成）
        loadAssignments()
    }

    private fun setLoading(b: Boolean) {
        progress.visibility = if (b) View.VISIBLE else View.GONE
    }

    private fun loadAssignments() {
        if (classId <= 0) {
            Toast.makeText(this, "缺少 classId", Toast.LENGTH_SHORT).show()
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            val (name, rawList) = withContext(Dispatchers.IO) {
                try {
                    val resp = api.selectClass(classId) // GET("selectClass")
                    val body: SelectClassDetailResponse? = resp.body()
                    if (resp.isSuccessful && body?.code == 1) {
                        val data = body.data
                        Pair(data?.className.orEmpty(), data?.list.orEmpty())
                    } else Pair("", emptyList())
                } catch (e: Exception) {
                    Pair("", emptyList())
                }
            }
            setLoading(false)

            if (name.isNotBlank()) toolbar.title = "$name Assignments"

            // —— 过期排到最下，其余保持后端顺序（老师发布顺序）
            val nowMs = System.currentTimeMillis()
            val (notExpired, expired) = rawList.partition { !it.isExpired(nowMs) }
            val sorted = notExpired + expired

            adapter.submit(sorted)
            emptyView.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun onAssignmentClick(item: ClassAssignmentItem) {
        // 进入做题页，完成/返回后在 doLauncher 回调和 onResume 里刷新
        doLauncher.launch(
            Intent(this, AssignmentDoActivity::class.java)
                .putExtra("assignmentId", item.assignmentId)
                .putExtra("assignmentName", item.assignmentName)
        )
    }
}

/** —— 工具扩展 —— */

// 判断该作业是否过期（以本地时区为准）
private fun ClassAssignmentItem.isExpired(nowMs: Long): Boolean {
    val due = this.expireTime.toEpochMillis() ?: return false
    return nowMs > due
}

// [yyyy,MM,dd,HH,mm,ss?] -> epoch millis（Calendar 月份需 -1）
private fun List<Int>?.toEpochMillis(): Long? {
    if (this == null || this.isEmpty()) return null
    val y   = this.getOrNull(0) ?: return null
    val m   = this.getOrNull(1) ?: 1
    val d   = this.getOrNull(2) ?: 1
    val h   = this.getOrNull(3) ?: 0
    val mi  = this.getOrNull(4) ?: 0
    val s   = this.getOrNull(5) ?: 0
    return Calendar.getInstance().apply {
        set(Calendar.MILLISECOND, 0)
        set(y, m - 1, d, h, mi, s)
    }.timeInMillis
}
