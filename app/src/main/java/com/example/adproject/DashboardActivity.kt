package com.example.adproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.adproject.api.ApiClient
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.components.AxisBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class DashboardActivity : AppCompatActivity() {

    private lateinit var chartView: LineChart
    private lateinit var last7Days: TextView
    private var accuracyRates: List<Float> = emptyList()

    private val api by lazy { ApiClient.api }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val exerciseButton = findViewById<Button>(R.id.exerciseButton)
        val dashboardButton = findViewById<Button>(R.id.dashboardButton)
        val classButton = findViewById<Button>(R.id.classButton)
        val homeButton = findViewById<Button>(R.id.homeButton)

        setSelectedButton(dashboardButton)

        exerciseButton.setOnClickListener {
            setSelectedButton(exerciseButton)
            startActivity(Intent(this, ExerciseActivity::class.java))
        }
        dashboardButton.setOnClickListener { setSelectedButton(dashboardButton) }
        classButton.setOnClickListener {
            setSelectedButton(classButton)
            startActivity(Intent(this, ClassActivity::class.java))
        }
        homeButton.setOnClickListener {
            setSelectedButton(homeButton)
            startActivity(Intent(this, HomeActivity::class.java))
        }

        findViewById<Button>(R.id.answerHistoryButton).setOnClickListener {
            startActivity(Intent(this, AnswerHistoryActivity::class.java))
        }
        findViewById<Button>(R.id.recommendedPracticeButton).setOnClickListener {
            startActivity(Intent(this, RecommendedActivity::class.java))
        }

        chartView = findViewById(R.id.chartView)
        setupChart()

        last7Days = findViewById(R.id.last7Days)
        last7Days.setOnClickListener { showDayOptions() }

        fetchAccuracyRates()
    }

    private fun setupChart() {
        chartView.description.isEnabled = false

        chartView.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setDrawGridLines(true)
            // 轴范围在 updateChartForDays 里动态设；这里不固定最大值
            axisMinimum = 1f
        }

        chartView.axisRight.isEnabled = false
        chartView.axisLeft.apply {
            axisMinimum = 0f
            axisMaximum = 100f
            granularity = 10f
        }

        chartView.setTouchEnabled(true)
        chartView.isDragEnabled = true
        chartView.setScaleEnabled(false)
    }

    /** x 从 1..n，与轴标签完全一致，避免偏移 */
    private fun updateChartForDays(days: Int) {
        if (accuracyRates.isEmpty()) {
            Toast.makeText(this, "暂无图表数据", Toast.LENGTH_SHORT).show()
            chartView.clear()
            return
        }

        val count = days.coerceAtMost(accuracyRates.size)

        // 如果后端是从早到晚排序，保留左侧前 n 天；若从晚到早，请改成 takeLast(count)
        val slice = accuracyRates.take(count)

        Log.d("ChartUpdate", "展示前 $count 天数据: $slice")

        val entries = slice.mapIndexed { idx, v ->
            // x 用 1..n；如果后端已是 0~100，请去掉 *100f
            Entry((idx + 1).toFloat(), v * 100f)
        }

        val dataSet = LineDataSet(entries, "Accuracy").apply {
            lineWidth = 2f
            circleRadius = 4f
            setDrawValues(true)
            setDrawCircles(true)
        }

        chartView.data = LineData(dataSet)

        chartView.xAxis.apply {
            val n = slice.size
            axisMinimum = 1f
            axisMaximum = n.toFloat()
            granularity = 1f
            setLabelCount(n, true)
            // 只显示整数 1..n
            valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    val iv = value.roundToInt()
                    return if (iv in 1..n) iv.toString() else ""
                }
            }
        }

        chartView.notifyDataSetChanged()
        chartView.invalidate()
    }

    private fun showDayOptions() {
        val options = arrayOf("Last 3 Days", "Last 5 Days", "Last 7 Days")
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Select Duration")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> { last7Days.text = "Last 3 Days ▼"; updateChartForDays(3) }
                1 -> { last7Days.text = "Last 5 Days ▼"; updateChartForDays(5) }
                2 -> { last7Days.text = "Last 7 Days ▼"; updateChartForDays(7) }
            }
        }
        builder.show()
    }

    private fun fetchAccuracyRates() {
        lifecycleScope.launch {
            val (ok, msg, data) = withContext(Dispatchers.IO) {
                try {
                    val resp = api.dashboard()
                    if (!resp.isSuccessful) {
                        Triple(false, "网络错误: ${resp.code()}", emptyList<Float>())
                    } else {
                        val body = resp.body()
                        val listAny = body?.data?.accuracyRates ?: emptyList()
                        val rates = listAny.map {
                            when (it) {
                                is Number -> it.toFloat()
                                else -> it.toString().toFloatOrNull() ?: 0f
                            }
                        }
                        val message = body?.msg ?: "OK"
                        val success = (body?.code == 1)
                        Triple(success, message, rates)
                    }
                } catch (e: Exception) {
                    Triple(false, e.message ?: "请求失败", emptyList<Float>())
                }
            }

            if (!ok) {
                Toast.makeText(this@DashboardActivity, msg, Toast.LENGTH_SHORT).show()
                accuracyRates = listOf(0.6f, 0.7f, 0.3f, 0.9f, 0.5f, 0.8f, 1.0f)
            } else {
                accuracyRates = data
                if (accuracyRates.isEmpty()) {
                    Toast.makeText(this@DashboardActivity, "暂无图表数据", Toast.LENGTH_SHORT).show()
                }
            }
            updateChartForDays(7)
        }
    }

    private fun setSelectedButton(selectedButton: Button) {
        findViewById<Button>(R.id.exerciseButton).isSelected = false
        findViewById<Button>(R.id.dashboardButton).isSelected = false
        findViewById<Button>(R.id.classButton).isSelected = false
        findViewById<Button>(R.id.homeButton).isSelected = false
        selectedButton.isSelected = true
    }
}
