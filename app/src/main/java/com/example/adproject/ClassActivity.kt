package com.example.adproject

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.adproject.api.ApiClient
import com.example.adproject.model.StudentClass
import com.example.adproject.model.ViewClassResponse
import kotlinx.coroutines.*
import java.io.IOException

class ClassActivity : AppCompatActivity() {

    // --- 网络：统一用 ApiClient 单例 ---
    private val api by lazy { ApiClient.api }

    // --- 协程 ---
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 防抖，避免短时间重复拉取
    @Volatile private var isLoading = false

    // --- UI ---
    private lateinit var homeworkListView: ListView
    private lateinit var adapter: ClassListAdapter
    private lateinit var leaveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_class)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // ✅ 设置标题：先用本地缓存名字
        val titleView = findViewById<TextView>(R.id.classTitle)
        val cachedName = UserSession.name(this) ?: "Your"
        titleView.text = "${toPossessive(cachedName)} Class"

        // ✅ 后台异步从服务端同步名字（后端就绪后会更新）
        uiScope.launch {
            val latest = UserSession.syncNameFromServer(this@ClassActivity)
            if (!latest.isNullOrBlank()) {
                titleView.text = "${toPossessive(latest)} Class"
            }
        }

        // 顶部三个按钮
        findViewById<Button>(R.id.announcementButton).setOnClickListener {
            startActivity(Intent(this, AnnouncementActivity::class.java))
        }
        findViewById<Button>(R.id.quizButton).setOnClickListener {
            // ✅ 改为跳转到上传题目页
            startActivity(Intent(this, UploadQuestionActivity::class.java))
        }
        leaveButton = findViewById(R.id.leaveButton)
        leaveButton.setOnClickListener {
            startActivity(Intent(this, LeaveClassActivity::class.java))
        }

        // 底部导航
        val exerciseButton = findViewById<Button>(R.id.exerciseButton)
        val dashboardButton = findViewById<Button>(R.id.dashboardButton)
        val classButton = findViewById<Button>(R.id.classButton)
        val homeButton = findViewById<Button>(R.id.homeButton)
        setSelectedButton(classButton)

        exerciseButton.setOnClickListener {
            setSelectedButton(exerciseButton)
            startActivity(Intent(this, ExerciseActivity::class.java))
        }
        dashboardButton.setOnClickListener {
            setSelectedButton(dashboardButton)
            startActivity(Intent(this, DashboardActivity::class.java))
        }
        classButton.setOnClickListener { setSelectedButton(classButton) }
        homeButton.setOnClickListener {
            setSelectedButton(homeButton)
            startActivity(Intent(this, HomeActivity::class.java))
        }

        // 列表
        homeworkListView = findViewById(R.id.homeworkListView)

        // 顶部固定“Join Class” 头部卡片（addHeaderView 必须在 setAdapter 之前）
        val header = layoutInflater.inflate(R.layout.header_join_class, homeworkListView, false)
        homeworkListView.addHeaderView(header, null, false)
        header.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnGoJoin)
            .setOnClickListener {
                // 不用回传，回到本页时 onResume 会自动刷新
                startActivity(Intent(this, JoinClassActivity::class.java))
            }

        adapter = ClassListAdapter(mutableListOf())
        homeworkListView.adapter = adapter

        homeworkListView.setOnItemClickListener { _, _, position, _ ->
            val realPos = position - homeworkListView.headerViewsCount
            if (realPos !in 0 until adapter.items.size) return@setOnItemClickListener
            val item = adapter.items[realPos]
            val it = Intent(this, ClassAssignmentActivity::class.java)
                .putExtra("classId", item.classId)
                .putExtra("className", item.className)
            startActivity(it)
        }

        // 注意：不在 onCreate 里主动拉取，避免与 onResume 重复
        // loadMyClasses()
    }

    override fun onResume() {
        super.onResume()
        loadMyClasses()  // 每次回到这个页面都重刷
    }

    private fun loadMyClasses() {
        if (isLoading) return
        isLoading = true

        uiScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { api.viewClass() }
                if (resp.isSuccessful) {
                    val body: ViewClassResponse? = resp.body()
                    if (body?.code == 1) {
                        val list = body.data?.list ?: emptyList()
                        adapter.replace(list)
                    } else {
                        Toast.makeText(this@ClassActivity, body?.msg ?: "Failed to load classes", Toast.LENGTH_SHORT).show()
                        adapter.replace(emptyList())
                    }
                } else {
                    Toast.makeText(this@ClassActivity, "Network error：${resp.code()}", Toast.LENGTH_SHORT).show()
                    adapter.replace(emptyList())
                }
            } catch (e: IOException) {
                Toast.makeText(this@ClassActivity, "Connection failed", Toast.LENGTH_SHORT).show()
                adapter.replace(emptyList())
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@ClassActivity, "Unknown error：${e.message}", Toast.LENGTH_SHORT).show()
                adapter.replace(emptyList())
            } finally {
                isLoading = false
            }
        }
    }

    private fun setSelectedButton(selectedButton: Button) {
        findViewById<Button>(R.id.exerciseButton).isSelected = false
        findViewById<Button>(R.id.dashboardButton).isSelected = false
        findViewById<Button>(R.id.classButton).isSelected = false
        findViewById<Button>(R.id.homeButton).isSelected = false
        selectedButton.isSelected = true
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
    }

    // ====== 适配器：复用 row_homework.xml 的两行样式 ======
    inner class ClassListAdapter(val items: MutableList<StudentClass>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = items[position].classId.toLong()

        fun replace(newItems: List<StudentClass>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@ClassActivity)
                .inflate(R.layout.row_homework, parent, false)

            val item = items[position]
            val starIcon = view.findViewById<ImageView>(R.id.starIcon)
            val subjectText = view.findViewById<TextView>(R.id.subjectText)
            val dueText = view.findViewById<TextView>(R.id.dueText)

            subjectText.text = item.className
            dueText.text = item.description

            // 星标本地切换（demo）
            val taggedKey = "class_fav_${item.classId}"
            val isFav = view.getTag(taggedKey.hashCode()) as? Boolean ?: false
            starIcon.setImageResource(if (isFav) R.drawable.star_yellow else R.drawable.star_black)
            starIcon.setOnClickListener {
                val nowFav = !(view.getTag(taggedKey.hashCode()) as? Boolean ?: false)
                view.setTag(taggedKey.hashCode(), nowFav)
                starIcon.setImageResource(if (nowFav) R.drawable.star_yellow else R.drawable.star_black)
            }

            return view
        }
    }

    private fun toPossessive(nameRaw: String): String {
        val name = nameRaw.trim()
        if (name.isEmpty() || name.equals("your", ignoreCase = true)) return "Your"
        // 结尾是 s 的情况用 Chris'，其余用 Lewis's
        return if (name.last().lowercaseChar() == 's') "$name'" else "$name's"
    }

}
