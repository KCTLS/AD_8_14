// 仅展示与改动相关的完整文件（其余保持一致）

package com.example.adproject

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
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
import com.example.adproject.model.RecommendedPractice
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecommendedActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var adapter: RecommendedAdapter

    private val seenIds = mutableSetOf<Int>()
    private var isLoading = false
    private val maxRetry = 4
    private val delayMs = 800L

    private val api by lazy { ApiClient.api }

    // ✅ 记录推荐题目的顺序（用于 Prev/Next）
    private val idOrder = mutableListOf<Int>()

    // 用 Activity Result 接收返回
    private val questionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "已返回推荐列表", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_recommend)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom)
            insets
        }
        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        recycler = findViewById(R.id.recycler)
        progress = findViewById(R.id.progressBar)
        errorText = findViewById(R.id.errorText)

        recycler.layoutManager = LinearLayoutManager(this)
        adapter = RecommendedAdapter(mutableListOf()) { item ->
            // ✅ 启动做题页时，携带推荐题目的 id 列表和当前题 id
            val itn = Intent(this, QuestionHost::class.java).apply {
                putExtra("questionId", item.id)
                putExtra("id_list", idOrder.toIntArray())
                putExtra("practice_title", item.title)
            }
            questionLauncher.launch(itn)
        }
        recycler.adapter = adapter

        // 底部导航（保留）
        findViewById<Button>(R.id.exerciseButton).setOnClickListener {
            startActivity(Intent(this, ExerciseActivity::class.java))
        }
        findViewById<Button>(R.id.dashboardButton).apply {
            isSelected = true
            setOnClickListener { startActivity(Intent(this@RecommendedActivity, DashboardActivity::class.java)) }
        }
        findViewById<Button>(R.id.classButton).setOnClickListener {
            startActivity(Intent(this, ClassActivity::class.java))
        }
        findViewById<Button>(R.id.homeButton).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
        }

        seenIds.clear()
        idOrder.clear()          // ✅ 每次刷新重置顺序表
        loadRecommendations()
    }

    private fun loadRecommendations() {
        if (isLoading) return
        isLoading = true
        showLoading(true)
        errorText.visibility = View.GONE

        lifecycleScope.launch {
            val trigOk = withContext(Dispatchers.IO) {
                try {
                    val r = api.triggerRecommend()
                    r.isSuccessful && r.body()?.get("code")?.asInt == 1
                } catch (_: Exception) { false }
            }
            if (!trigOk) return@launch fail("触发推荐失败")

            val ids = fetchRecommendIdsWithRetry()
            if (ids.isEmpty()) return@launch fail("当前没有可用的推荐题目")

            adapter.clear()
            idOrder.clear()
            idOrder.addAll(ids)       // ✅ 记录顺序
            fetchQuestionOneByOne(ids, 0)
        }
    }

    private suspend fun fetchRecommendIdsWithRetry(): List<Int> {
        repeat(maxRetry) {
            val ids = withContext(Dispatchers.IO) {
                try {
                    val r = api.getRecommendIds()
                    if (!r.isSuccessful) return@withContext emptyList<Int>()
                    val root = r.body()
                    if (root?.get("code")?.asInt != 1) return@withContext emptyList<Int>()
                    extractIds(root)
                } catch (_: Exception) { emptyList() }
            }
            val dedup = ids.distinct().filter { seenIds.add(it) }
            if (dedup.isNotEmpty()) return dedup
            delay(delayMs)
        }
        return emptyList()
    }

    private fun extractIds(root: JsonObject?): List<Int> {
        val data = root?.getAsJsonObject("data") ?: return emptyList()
        val arr: JsonArray? = when {
            data.has("questionIds") -> data.getAsJsonArray("questionIds")
            data.has("ids") -> data.getAsJsonArray("ids")
            else -> null
        }
        val list = mutableListOf<Int>()
        if (arr != null) for (e in arr) list += e.asInt
        return list
    }

    private fun fetchQuestionOneByOne(ids: List<Int>, index: Int) {
        if (index >= ids.size) {
            showLoading(false)
            isLoading = false
            Toast.makeText(this, "已获取到 ${adapter.itemCount} 条推荐", Toast.LENGTH_SHORT).show()
            return
        }
        val id = ids[index]
        lifecycleScope.launch {
            val item: RecommendedPractice? = withContext(Dispatchers.IO) {
                try {
                    val resp = api.getQuestionById(id)
                    val dto = resp.body()?.data ?: return@withContext null
                    val title = dto.question ?: "Question #$id"
                    val b64 = dto.image
                    RecommendedPractice(
                        id = id,
                        title = title,
                        subject = "—",
                        grade = "—",
                        questions = 10,
                        difficulty = "Medium",
                        imageBase64 = b64
                    )
                } catch (_: Exception) {
                    RecommendedPractice(
                        id = id,
                        title = "Question #$id",
                        subject = "—",
                        grade = "—",
                        questions = 10,
                        difficulty = "Medium",
                        imageBase64 = null
                    )
                }
            }
            item?.let { adapter.addItem(it) }
            fetchQuestionOneByOne(ids, index + 1)
        }
    }

    private fun showLoading(show: Boolean) {
        progress.visibility = if (show) View.VISIBLE else View.GONE
        recycler.alpha = if (show) 0.4f else 1f
    }

    private fun fail(msg: String) {
        isLoading = false
        showLoading(false)
        errorText.text = msg
        errorText.visibility = View.VISIBLE
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
