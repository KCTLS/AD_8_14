package com.example.adproject

import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.adproject.api.ApiClient
import com.example.adproject.api.ApiService
import com.example.adproject.model.AnnouncementItem
import com.example.adproject.model.Result
import com.example.adproject.model.StudentClass
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.util.GregorianCalendar

class AnnouncementActivity : AppCompatActivity() {

    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var listView: ListView
    private lateinit var emptyState: View
    private lateinit var emptyText: android.widget.TextView
    private lateinit var toolbar: MaterialToolbar

    private lateinit var adapter: AnnouncementAdapter

    private var selectedClassId: Int? = null
    private var joinedClasses: List<StudentClass> = emptyList()
    private var classNameMap: Map<Int, String> = emptyMap()

    private val api: ApiService by lazy { ApiClient.api }

    // 防止同一条在请求过程中被重复点击
    private val markingIds = mutableSetOf<Int>()

    private var tipToast: Toast? = null
    private fun showToast(msg: String) {
        tipToast?.cancel()
        tipToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT)
        tipToast?.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_announcement)

        toolbar = findViewById(R.id.topAppBar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.menu.clear()
        toolbar.inflateMenu(R.menu.menu_announcement_filter)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_filter -> { showFilterDialog(); true }
                else -> false
            }
        }

        swipe = findViewById(R.id.swipeRefresh)
        listView = findViewById(R.id.announcementList)
        emptyState = findViewById(R.id.emptyState)
        emptyText = emptyState.findViewById(R.id.emptyText)

        adapter = AnnouncementAdapter(this).apply {
            setOnItemClick { ann ->
                // TODO: 如果有详情页，这里打开
            }
            setOnMarkRead { ann ->
                val id = ann.announcementId ?: return@setOnMarkRead
                // 去重：同一条请求未结束前不重复触发
                if (!markingIds.add(id)) return@setOnMarkRead

                // 打接口 -> 刷新
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        try { api.checkAnnouncement(id) } catch (_: Exception) {}
                    }
                    markingIds.remove(id)
                    reloadAnnouncements() // 强刷列表，红点根据服务端 status 消失
                }
            }
        }
        listView.adapter = adapter

        swipe.setOnRefreshListener { reloadAnnouncements() }

        lifecycleScope.launch {
            loadMyClasses()
            reloadAnnouncements()
        }
    }

    /** 拉我加入的班级 */
    private suspend fun loadMyClasses() {
        try {
            val resp = withContext(Dispatchers.IO) { api.viewClass() }
            if (resp.isSuccessful && resp.body()?.code == 1) {
                joinedClasses = resp.body()?.data?.list.orEmpty()
                classNameMap = joinedClasses.associate { it.classId to it.className }
            } else {
                checkAuthAndToast(resp.body()?.code, resp.body()?.msg)
                joinedClasses = emptyList()
                classNameMap = emptyMap()
            }
        } catch (_: Exception) {
            joinedClasses = emptyList()
            classNameMap = emptyMap()
        }
    }

    /** 下拉/筛选后刷新 */
    private fun reloadAnnouncements() {
        swipe.isRefreshing = true
        lifecycleScope.launch {
            try {
                val data = when (val cid = selectedClassId) {
                    null -> fetchAllAnnouncements()
                    else -> fetchOneClassAnnouncements(cid)
                }
                applyData(data)
            } catch (e: Exception) {
                showToast("加载失败：${e.message}")
                applyData(emptyList())
            } finally {
                swipe.isRefreshing = false
            }
        }
    }

    /** 全部班级并发拉取，按时间倒序 */
    private suspend fun fetchAllAnnouncements(): List<AnnouncementItem> = withContext(Dispatchers.IO) {
        if (joinedClasses.isEmpty()) return@withContext emptyList<AnnouncementItem>()
        val jobs = joinedClasses.map { cls ->
            async {
                try {
                    val r = api.selectAnnouncement(cls.classId)
                    if (r.isSuccessful && r.body()?.code == 1) {
                        r.body()?.data?.list.orEmpty()
                            .map { it.copy(classId = cls.classId, className = it.className ?: cls.className) }
                    } else {
                        checkAuthAndToast(r.body()?.code, r.body()?.msg)
                        emptyList()
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
        jobs.awaitAll().flatten().sortedByDescending { toMillis(it.createTime) }
    }

    /** 单个班级 */
    private suspend fun fetchOneClassAnnouncements(classId: Int): List<AnnouncementItem> = withContext(Dispatchers.IO) {
        val r = api.selectAnnouncement(classId)
        if (r.isSuccessful && r.body()?.code == 1) {
            r.body()?.data?.list.orEmpty()
                .map { it.copy(classId = classId, className = it.className ?: classNameMap[classId]) }
                .sortedByDescending { toMillis(it.createTime) }
        } else {
            checkAuthAndToast(r.body()?.code, r.body()?.msg)
            emptyList()
        }
    }

    /** 应用到 UI */
    private fun applyData(list: List<AnnouncementItem>) {
        adapter.setItems(list)
        val empty = list.isEmpty()
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        listView.visibility = if (empty) View.GONE else View.VISIBLE
        emptyText.text = if (selectedClassId == null) "暂无任何通知" else "当前班级无通知"
    }

    /** 筛选 */
    private fun showFilterDialog() {
        val names = mutableListOf("全部消息") + joinedClasses.map { it.className }
        val checked = when (val cid = selectedClassId) {
            null -> 0
            else -> (joinedClasses.indexOfFirst { it.classId == cid }.takeIf { it >= 0 } ?: -1) + 1
        }

        AlertDialog.Builder(this)
            .setTitle("筛选班级")
            .setSingleChoiceItems(names.toTypedArray(), checked) { d, which ->
                selectedClassId = if (which == 0) null else joinedClasses[which - 1].classId
                d.dismiss()
                reloadAnnouncements()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 提示未登录 */
    private fun checkAuthAndToast(code: Int?, msg: String?) {
        if (code == 0) showToast(msg ?: "未登录或会话已失效")
    }

    /** [yyyy,MM,dd,HH,mm] -> millis */
    private fun toMillis(arr: List<Int>?): Long {
        if (arr == null || arr.size < 5) return 0L
        return try {
            val cal = GregorianCalendar(arr[0], arr[1] - 1, arr[2], arr[3], arr[4])
            cal.timeInMillis
        } catch (_: Exception) { 0L }
    }
}
