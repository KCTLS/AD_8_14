package com.example.adproject

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.adproject.api.ApiClient
import com.example.adproject.model.StudentClass
import com.example.adproject.model.ViewClassResponse
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LeaveClassActivity : AppCompatActivity() {

    private lateinit var toolbar: com.google.android.material.appbar.MaterialToolbar
    private lateinit var swipe: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var recycler: androidx.recyclerview.widget.RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var progress: View

    private val api by lazy { ApiClient.api }
    private val adapter by lazy { JoinedClassAdapter(onLeaveClick = ::onLeaveClicked) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_leave_class)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        toolbar = findViewById(R.id.topAppBar)
        swipe = findViewById(R.id.swipe)
        recycler = findViewById(R.id.recycler)
        emptyView = findViewById(R.id.emptyView)
        progress = findViewById(R.id.progress)

        // 顶部返回
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // 列表
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // 下拉刷新
        swipe.setOnRefreshListener { loadJoinedClasses() }

        // 首次加载
        loadJoinedClasses()
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun loadJoinedClasses() {
        setLoading(true)
        lifecycleScope.launch {
            val list: List<StudentClass> = withContext(Dispatchers.IO) {
                try {
                    val resp = api.viewClass() // <-- 对应你的 ApiService
                    val body: ViewClassResponse? = resp.body()
                    if (resp.isSuccessful && body?.code == 1) {
                        body.data?.list.orEmpty()
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            }

            setLoading(false)
            swipe.isRefreshing = false

            adapter.submit(list)
            emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun onLeaveClicked(item: StudentClass) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Leave ${item.className}?")
            .setMessage("You will stop receiving announcements from this class.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Leave") { _, _ -> doLeave(item) }
            .show()
    }

    private fun doLeave(item: StudentClass) {
        lifecycleScope.launch {
            val (ok, tip) = withContext(Dispatchers.IO) {
                try {
                    // Api 需要 Long，这里把 Int 转成 Long
                    val resp = api.leaveClass(item.classId.toLong())
                    val body = resp.body()
                    if (resp.isSuccessful && body != null) {
                        val success = body.code == 1 || body.code == 2
                        val msg = body.msg ?: if (body.code == 2) "You already left this class" else "Left the class"
                        success to msg
                    } else {
                        false to "Network error"
                    }
                } catch (e: Exception) {
                    false to (e.message ?: "Request failed")
                }
            }

            Toast.makeText(this@LeaveClassActivity, tip, Toast.LENGTH_SHORT).show()
            if (ok) {
                adapter.remove(item.classId) // Int
                emptyView.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
                setResult(RESULT_OK)
            }
        }
    }
}
